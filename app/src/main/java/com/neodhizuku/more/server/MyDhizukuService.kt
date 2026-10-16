package com.neodhizuku.more.server

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity
import com.neodhizuku.more.data.settings.repo.LogRepo

import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.rosan.dhizuku.aidl.IDhizukuClient
import com.rosan.dhizuku.aidl.IDhizukuRequestPermissionListener
import com.neodhizuku.more.data.common.util.getPackageInfoForUid
import com.neodhizuku.more.data.common.util.getSignaturesForUid
import com.neodhizuku.more.data.common.util.signature
import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.rosan.dhizuku.server_api.DhizukuService
import com.rosan.dhizuku.shared.DhizukuVariables

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 白名单模式下需要额外限制的高危接口。
 * 白名单模式对以下高危接口生效：仅白名单内的应用可调用。
 */
private val HIGH_RISK_FUNCS = setOf(
    "remote_process",
    "remote_transact",
    "set_delegated_scopes",
    "bind_user_service"
)

/**
 * 服务已停止时仍允许的操作：只做「查看」，不做任何实际动作。
 *
 * - `null` 对应 `isPermissionGranted()`，客户端靠它判断能不能用
 * - `get_delegated_scopes` 是只读查询
 *
 * 其余（remote_process / remote_transact / bind_user_service /
 * unbind_user_service / set_delegated_scopes）一律拒绝。
 */
private val READ_ONLY_FUNCS_WHEN_STOPPED = setOf<String?>(
    null,
    "get_delegated_scopes"
)

class MyDhizukuService(context: Context, admin: ComponentName?, client: IDhizukuClient?) :
    DhizukuService(context, admin, client), KoinComponent {
    private val context by inject<Context>()
    private val appRepo by inject<AppRepo>()
    private val settingsRepo by inject<SettingsRepo>()
    private val logRepo by inject<LogRepo>()

    /** 审计落库用的后台作用域，不阻塞 binder 线程 */
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getVersionName(): String =
        if (NeoDhizuku.on(NeoDhizuku.FixVersionName)) BuildConfig.VERSION_NAME
        else "$versionCode"

    private var signature: String? = null

    // ------------------------------------------------------------------
    // 拦截 requestPermission 事务（事务码 17）
    //
    // 之所以用 onTransact 而不是 override requestPermission()：
    // app 可能运行在「本地 API 源码」或「Maven 官方包」两种模式下，
    // Maven 的 IDhizuku 没有该方法，直接 override 会导致编译失败。
    // 事务码 17 在两种模式下都是空闲的（官方接口最高只用到 16），
    // 因此这里拦截既能兼容两种模式，又能在两种模式下都提供"服务端校验身份"的加固。
    // ------------------------------------------------------------------
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (NeoDhizuku.on(NeoDhizuku.HardenPermissionEntry)
            && code == RequestPermissionToken.TRANSACT_REQUEST_PERMISSION
        ) {
            val ok = runCatching { handleRequestPermission(data, reply) }
                .onFailure { NeoLog.e(NeoLog.PERM, { "requestPermission 异常" }, it) }
                .getOrDefault(false)
            if (!ok) {
                NeoLog.w(NeoLog.PERM) { "requestPermission: 处理失败，回退到默认 transact" }
            }
            return ok
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun handleRequestPermission(data: Parcel, reply: Parcel?): Boolean {
        data.enforceInterface(RequestPermissionToken.IDHIZUKU_DESCRIPTOR)
        val binder = data.readStrongBinder()
        val listener = IDhizukuRequestPermissionListener.Stub.asInterface(binder)
        val uid = Binder.getCallingUid()
        val pkg = runCatching { context.packageManager.getNameForUid(uid) }.getOrNull()
        val token = RequestPermissionToken.issue(uid, listener)

        NeoLog.i(NeoLog.PERM) {
            "requestPermission: uid=$uid pkg=$pkg " +
                "token=${token?.take(8)}… enabled=${settingsRepo.isDhizukuEnabled}"
        }

        val intent = Intent(DhizukuVariables.getActionRequestPermission(context.packageName))
            .setPackage(context.packageName)
            .putExtra(DhizukuVariables.PARAM_CLIENT_UID, uid)
            .putExtra(RequestPermissionToken.PARAM_SERVER_TOKEN, token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val started = runCatching {
            context.startActivity(intent)
            true
        }.onFailure { NeoLog.e(NeoLog.PERM, { "startActivity 失败 action=${intent.action}" }, it) }
            .getOrDefault(false)

        NeoLog.i(NeoLog.PERM) { "requestPermission: startActivity=$started" }
        if (!started) {
            RequestPermissionToken.revoke(token)
            runCatching { listener?.onRequestPermission(android.content.pm.PackageManager.PERMISSION_DENIED) }
        }
        reply?.writeNoException()
        return true
    }

    override fun checkCallingPermission(func: String?, callingUid: Int, callingPid: Int): Boolean {
        val pkg = runCatching { context.packageManager.getNameForUid(callingUid) }.getOrNull()

        // 一次性把关键状态打全，避免逐条猜测
        NeoLog.d(NeoLog.PERM) {
            "check: uid=$callingUid pkg=$pkg func=$func " +
                "owner=${DhizukuState.state.isOwner} enabled=${settingsRepo.isDhizukuEnabled} " +
                "whitelist=${settingsRepo.isWhitelistMode}"
        }

        if (!settingsRepo.isDhizukuEnabled) {
            NeoLog.w(NeoLog.PERM) { "check: 拒绝（API 已停用）uid=$callingUid pkg=$pkg" }
            return false
        }

        // 用户已停止守护服务：只保留「查看」，拒绝一切实际动作。
        // 注意 Provider 是 ContentProvider，随进程存活，停掉 DaemonService
        // 并不会让第三方断开，必须在这里显式拦截。
        if (settingsRepo.isServiceStoppedByUser && func !in READ_ONLY_FUNCS_WHEN_STOPPED) {
            NeoLog.w(NeoLog.PERM) {
                "check: 拒绝（服务已停止，仅允许查看）func=$func pkg=$pkg"
            }
            return false
        }

        // 优先走内存缓存，缓存未就绪时回退到原来的 runBlocking 查询
        val entity = if (NeoDhizuku.on(NeoDhizuku.FixPermissionMemoryCache)) {
            PermissionCache.get(callingUid)
                ?: runBlocking { appRepo.findByUID(callingUid)?.also { PermissionCache.put(it) } }
        } else {
            runBlocking { appRepo.findByUID(callingUid) }
        }
        if (entity == null) {
            NeoLog.w(NeoLog.PERM) { "check: 拒绝（无授权记录）uid=$callingUid pkg=$pkg" }
            ensureCallerVisible(callingUid, pkg)
            return false
        }

        if (!entity.allowApi || entity.blocked) {
            NeoLog.w(NeoLog.PERM) {
                "check: 拒绝（allowApi=${entity.allowApi} blocked=${entity.blocked}）pkg=$pkg"
            }
            return false
        }

        if (NeoDhizuku.on(NeoDhizuku.FixWhitelistMode)) {
            if (settingsRepo.isWhitelistMode && func in HIGH_RISK_FUNCS && !entity.allowApi) {
                NeoLog.w(NeoLog.PERM) { "check: 拒绝（白名单高危）func=$func pkg=$pkg" }
                return false
            }
        } else {
            if (settingsRepo.isWhitelistMode && !entity.allowApi) {
                NeoLog.w(NeoLog.PERM) { "check: 拒绝（白名单）pkg=$pkg" }
                return false
            }
        }

        if (NeoDhizuku.on(NeoDhizuku.FixSignatureSetCheck)) {
            // 原实现只取第一个签名者，v3 签名轮换 / 多签名场景下会误判。
            // 这里改为"当前签名集合覆盖授权时记录的签名"。
            val signatures = getSignaturesForUid(context.packageManager, callingUid)
            if (signatures.isEmpty()) {
                NeoLog.w(NeoLog.PERM) { "check: 拒绝（取不到签名）pkg=$pkg" }
                return false
            }
            val recorded: String = entity.signature
            if (recorded.isBlank()) {
                NeoLog.w(NeoLog.PERM) { "check: 拒绝（授权记录签名为空）pkg=$pkg" }
                return false
            }
            if (!signatures.contains(recorded)) {
                NeoLog.w(NeoLog.PERM) {
                    "check: 拒绝（签名不匹配）pkg=$pkg " +
                        "记录=${recorded.take(12)}… 当前=${signatures.map { it.take(12) }}"
                }
                return false
            }
            NeoLog.d(NeoLog.PERM) { "check: 通过 uid=$callingUid pkg=$pkg func=$func" }
            return true
        }

        val signature = this.signature
            ?: context.packageManager.getPackageInfoForUid(callingUid)?.signature
        if (signature == null) {
            NeoLog.w(NeoLog.PERM) { "check: 拒绝（取不到签名）pkg=$pkg" }
            return false
        }
        this.signature = signature

        if (signature != entity.signature) {
            NeoLog.w(NeoLog.PERM) { "check: 拒绝（签名不匹配）pkg=$pkg" }
            return false
        }

        NeoLog.d(NeoLog.PERM) { "check: 通过 uid=$callingUid pkg=$pkg func=$func" }
        return true
    }

    /**
     * 让「已发起调用但尚未授权」的应用出现在软件管理页。
     *
     * 有些客户端（例如 InstallerX）并不在清单里声明 Dhizuku API 权限，
     * 因此不会出现在 `getPackagesHoldingPermissions` 的候选集里；
     * 它又从未弹过授权窗（查询 `isPermissionGranted()` 被拒就走了），
     * 数据库里也就没有记录。结果是软件管理页完全没有它的卡片，
     * 用户想授权都找不到入口。
     *
     * 这里在首次被拒时补一条 `allowApi=false` 的记录，
     * 页面上就会显示一张开关为关的「未授权」卡片。
     */
    private fun ensureCallerVisible(callingUid: Int, pkg: String?) {
        if (!NeoDhizuku.on(NeoDhizuku.FixUnauthorizedCallerEntry)) return
        val signature = context.packageManager.getPackageInfoForUid(callingUid)?.signature
        if (signature.isNullOrBlank()) {
            NeoLog.w(NeoLog.PERM) { "caller entry: 取不到签名，跳过 uid=$callingUid" }
            return
        }
        auditScope.launch {
            runCatching {
                if (appRepo.findByUID(callingUid) != null) return@runCatching
                appRepo.insert(
                    AppEntity(uid = callingUid, signature = signature, allowApi = false)
                )
                NeoLog.i(NeoLog.PERM) { "caller entry: 已登记未授权应用 uid=$callingUid ($pkg)" }
            }.onFailure {
                NeoLog.e(NeoLog.PERM, { "caller entry: 登记失败 uid=$callingUid" }, it)
            }
        }
    }

    /**
     * remote_transact 的审计。
     *
     * 基类只给 remote_process 回调 onAudit，remote_transact 没有。
     * 这条路径能操作 DevicePolicyManager，是最需要留痕的，因此单独记一份。
     */
    private fun auditRemoteTransact(uid: Int, label: String, descriptor: String?, code: Int) {
        if (!NeoDhizuku.on(NeoDhizuku.UseCallLogPage)) return
        val detail = "以设备所有者身份调用 ${descriptor ?: "未知接口"}（事务码 $code）"
        AuditLogStore.record(uid, label, "remote_transact", detail)

        if (!NeoDhizuku.on(NeoDhizuku.UseCallLogPersist)) return
        auditScope.launch {
            val lines = NeoLog.snapshot(LOG_EXCERPT_LINES)
            val excerpt = lines.joinToString("\n") {
                "${formatTime(it.timestamp)} ${it.level}/${it.sub} ${it.message}"
            }
            runCatching {
                logRepo.insertAudit(
                    AuditEntity(
                        uid = uid,
                        packageName = label,
                        api = "remote_transact",
                        detail = detail,
                        logExcerpt = excerpt
                    )
                )
            }
        }
    }

    /**
     * 审计回调。
     *
     * DhizukuService 中已存在该方法，必须加 override
     * （否则 Kotlin 报 VIRTUAL_MEMBER_HIDDEN）。
     */
    override fun onAudit(callingUid: Int, api: String, detail: String) {
        if (!NeoDhizuku.on(NeoDhizuku.HardenRemoteProcessAudit)) return
        Log.i("Dhizuku", "audit uid=$callingUid api=$api detail=$detail")
        // 写入内存审计，供「调用记录」页展示
        if (!NeoDhizuku.on(NeoDhizuku.UseCallLogPage)) return
        val packageName = runCatching {
            context.packageManager.getNameForUid(callingUid)
        }.getOrNull() ?: callingUid.toString()
        AuditLogStore.record(callingUid, packageName, api, detail)

        if (!NeoDhizuku.on(NeoDhizuku.UseCallLogPersist)) return
        // 落库在 IO 协程里做：onAudit 跑在 binder 线程，不能阻塞
        auditScope.launch {
            val lines = NeoLog.snapshot(LOG_EXCERPT_LINES)
            val excerpt = lines.joinToString("\n") {
                "${formatTime(it.timestamp)} ${it.level}/${it.sub} ${it.message}"
            }
            runCatching {
                logRepo.insertAudit(
                    AuditEntity(
                        uid = callingUid,
                        packageName = packageName,
                        api = api,
                        detail = detail,
                        logExcerpt = excerpt
                    )
                )
            }
        }
    }

    /**
     * 拦截设备所有者转移。
     *
     * 第三方拿到授权后可以用 binderWrapper 包装 DevicePolicyManager，
     * 以本应用的设备所有者身份调用其任意方法。这里只拦"改变归属"的几类，
     * 查询类操作照常放行。
     */
    override fun onRemoteTransact(
        binder: IBinder?,
        code: Int,
        data: Parcel?,
        reply: Parcel?,
        flags: Int
    ): Boolean {
        // 双保险：即便调用方绕过了 checkCallingPermission，这里再拦一次
        if (settingsRepo.isServiceStoppedByUser) {
            val uid = Binder.getCallingUid()
            val label = runCatching {
                context.packageManager.getNameForUid(uid)
            }.getOrNull() ?: uid.toString()
            NeoLog.w(NeoLog.SERVER) {
                "remote_transact: 拒绝（服务已停止）uid=$uid ($label) code=$code"
            }
            throw SecurityException("Dhizuku service is stopped by user: uid=$uid")
        }

        // remote_transact 是最危险的一条路径：第三方可用 binderWrapper 包装任意
        // binder（含 DevicePolicyManager）以本应用的设备所有者身份执行。
        // 基类只在 remote_process 时回调 onAudit，这条路径此前完全没有记录，
        // 因此在这里补审计。
        val descriptor = runCatching { binder?.interfaceDescriptor }.getOrNull()
        val callerUid = Binder.getCallingUid()
        val callerLabel = runCatching {
            context.packageManager.getNameForUid(callerUid)
        }.getOrNull() ?: callerUid.toString()
        NeoLog.i(NeoLog.SERVER) {
            "remote_transact: uid=$callerUid ($callerLabel) " +
                "target=${descriptor ?: "unknown"} code=$code"
        }
        auditRemoteTransact(callerUid, callerLabel, descriptor, code)

        if (NeoDhizuku.on(NeoDhizuku.HardenOwnerTransferGuard)
            && OwnerTransferGuard.isOwnerTransfer(binder, code)
        ) {
            val uid = Binder.getCallingUid()
            val label = runCatching {
                context.packageManager.getNameForUid(uid)
            }.getOrNull() ?: uid.toString()
            val allowed = settingsRepo.isAllowOwnerTransfer
            NeoLog.w(NeoLog.SERVER) {
                "owner transfer: uid=$uid ($label) allowed=$allowed"
            }
            if (!allowed) {
                AlertNotifier.transferBlocked(context, label)
                throw SecurityException(
                    "Owner transfer is disabled by user: uid=$uid"
                )
            }
            AlertNotifier.transferAllowed(context, label)
        }
        return super.onRemoteTransact(binder, code, data, reply, flags)
    }

    private fun formatTime(timestamp: Long): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))

    private companion object {
        /** 每条调用记录附带的运行日志行数 */
        const val LOG_EXCERPT_LINES = 20
    }
}
