package com.neodhizuku.more.server

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
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
import com.neodhizuku.more.data.settings.repo.DenyMode
import com.neodhizuku.more.data.settings.repo.InsufficientMode
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.rosan.dhizuku.server_api.DhizukuService
import com.rosan.dhizuku.server_api.Variables
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

    /**
     * 返回给客户端的版本名。
     *
     * 默认返回上游 Dhizuku 的版本（[Variables.SERVICE_VERSION_NAME]）：部分客户端会
     * 校验版本，返回本项目的版本可能被判定为不兼容。用户可在「服务」页关闭该兼容模式，
     * 届时返回本项目自身的版本名。
     *
     * `FixVersionName` 关闭时回退到上游行为（用版本码凑数），不读本开关。
     */
    override fun getVersionName(): String {
        if (!NeoDhizuku.on(NeoDhizuku.FixVersionName)) return "$versionCode"
        val legacy = runCatching { settingsRepo.isUseLegacyVersion }.getOrDefault(true)
        val name = if (legacy) Variables.SERVICE_VERSION_NAME else BuildConfig.VERSION_NAME
        NeoLog.i(NeoLog.SERVER) {
            "getVersionName: legacy=$legacy -> $name (uid=${Binder.getCallingUid()})"
        }
        return name
    }

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

        // 「不做任何处理」模式：既不拉起授权页，也不回调结果。
        //
        // 客户端的 listener 永远收不到回调，于是停在等待状态 ——
        // 表现为获取授权时一直转圈。这里刻意**不阻塞**当前线程：
        // 挂起靠"不回调"实现，而不是 sleep，因此不会影响服务处理其他调用。
        if (DenyMode.from(settingsRepo.denyMode) == DenyMode.NO_ACTION) {
            NeoLog.i(NeoLog.PERM) {
                "requestPermission: 挂起（不做任何处理）uid=$uid pkg=$pkg"
            }
            reply?.writeNoException()
            return true
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

        // 不再在失败时立刻回调"拒绝"。
        //
        // 后台启动 Activity 限制（Android 10+）下，从后台 startActivity 可能
        // **静默失败**：既不弹窗也不抛异常。此时若回调 DENIED，客户端会认为
        // 用户拒绝了，之后不再重试 —— 表现为"永远无法授权"。
        //
        // 改为：保留 token，延迟确认弹窗是否真的起来；没起来就发通知，
        // 由用户点击拉起（用户点击属于用户发起，不受后台启动限制）。
        if (!started) {
            AlertNotifier.permissionRequest(context, uid, token)
        } else {
            scheduleFallbackNotify(uid, token)
        }
        reply?.writeNoException()
        return true
    }

    /**
     * 兜底通知：startActivity 调用成功不代表弹窗真的出现了（BAL 静默拦截）。
     *
     * 短暂延迟后检查 token 是否仍未被消费；未被消费说明用户根本没看到弹窗，
     * 此时补发通知。
     */
    private fun scheduleFallbackNotify(uid: Int, token: String?) {
        auditScope.launch {
            kotlinx.coroutines.delay(FALLBACK_NOTIFY_DELAY_MILLIS)
            if (!RequestPermissionToken.isPending(token)) {
                NeoLog.d(NeoLog.PERM) { "requestPermission: 弹窗已被处理，跳过兜底通知 uid=$uid" }
                return@launch
            }
            NeoLog.w(NeoLog.PERM) {
                "requestPermission: 弹窗未出现（疑似被后台启动限制拦截），补发通知 uid=$uid"
            }
            AlertNotifier.permissionRequest(context, uid, token)
        }
    }

    override fun checkCallingPermission(func: String?, callingUid: Int, callingPid: Int): Boolean {
        val pkg = runCatching { context.packageManager.getNameForUid(callingUid) }.getOrNull()

        // 把判定依赖的关键状态一次打全
        NeoLog.d(NeoLog.PERM) {
            "check: uid=$callingUid pkg=$pkg func=$func " +
                "owner=${DhizukuState.state.isOwner} enabled=${settingsRepo.isDhizukuEnabled} " +
                "whitelist=${settingsRepo.isWhitelistMode}"
        }

        if (!settingsRepo.isDhizukuEnabled) {
            NeoLog.w(NeoLog.PERM) { "check: 拒绝（API 已停用）uid=$callingUid pkg=$pkg" }
            return false
        }

        // 用户已停止守护服务：按拒绝模式处理。
        // 注意 Provider 是 ContentProvider，随进程存活，停掉 DaemonService
        // 并不会让第三方断开，必须在这里显式拦截。
        //
        // 【必须排除本应用自身】激活流程（ActivateViewModel#activateByDhizuku）
        // 会用 DhizukuBinderWrapper 包装 DevicePolicyManager 调 transferOwnership，
        // 走的正是 remote_transact —— 若不排除自身 uid，服务处于停止状态时
        // 本应用自己激活会被自己拦掉，表现为 NOT_DEVICE_ADMIN。
        if (settingsRepo.isServiceStoppedByUser && callingUid != Process.myUid()) {
            val mode = DenyMode.from(settingsRepo.denyMode)
            val danger = func !in READ_ONLY_FUNCS_WHEN_STOPPED
            when (mode) {
                DenyMode.DENY_ALL_FAIL -> {
                    NeoLog.w(NeoLog.PERM) { "check: 拒绝（完全拒绝）func=$func pkg=$pkg" }
                    return false
                }
                DenyMode.DENY_ALL_SUCCESS -> {
                    NeoLog.w(NeoLog.PERM) { "check: 放行但无服务能力 func=$func pkg=$pkg" }
                    return true
                }
                DenyMode.DENY_DANGEROUS_FAIL -> {
                    if (danger) {
                        NeoLog.w(NeoLog.PERM) {
                            "check: 拒绝（服务已停止，仅允许查看）func=$func pkg=$pkg"
                        }
                        return false
                    }
                }
                DenyMode.DENY_DANGEROUS_SUCCESS -> {
                    // 危险操作也放行，但在 onRemoteTransact 处不真正执行，
                    // 只写回一个成功结果。
                    if (danger) {
                        NeoLog.w(NeoLog.PERM) {
                            "check: 放行但不执行（返回成功）func=$func pkg=$pkg"
                        }
                    }
                }
                DenyMode.NO_ACTION -> {
                    // 授权请求走 requestPermission 的挂起路径（非阻塞）；
                    // 其余危险操作按失败处理，避免长时间占用 binder 线程。
                    if (danger) {
                        NeoLog.w(NeoLog.PERM) {
                            "check: 拒绝（不做任何处理）func=$func pkg=$pkg"
                        }
                        return false
                    }
                }
            }
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
            // 只校验第一个签名者在 v3 签名轮换 / 多签名场景下会误判，
            // 因此要求当前签名集合覆盖授权时记录的签名。
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

                // 老版本客户端（Maven 上的官方 API）压根不会发起授权弹窗，
                // 只是查一下 isPermissionGranted() 就走了 —— 既没弹窗也没入口。
                // 首次登记时补一条通知，让用户有办法点进去授权。
                if (NeoDhizuku.on(NeoDhizuku.HardenPermissionEntry)) {
                    AlertNotifier.permissionRequest(context, callingUid, null)
                }
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
    private fun auditRemoteTransact(
        uid: Int,
        label: String,
        descriptor: String?,
        code: Int,
        extraDetail: String? = null,
        human: String? = null
    ) {
        if (!NeoDhizuku.on(NeoDhizuku.UseCallLogPage)) return
        // 调用方已解析过就直接用（带实参），否则这里再解析一次
        val resolved = human ?: DpmApiCatalog.describe(descriptor, code)
        val detail = extraDetail ?: "以设备所有者身份调用：$resolved"
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
        // 双保险：即便调用方绕过了 checkCallingPermission，这里再拦一次。
        // 同样排除本应用自身 uid —— 激活流程要用这条路把所有者转移给自己。
        if (settingsRepo.isServiceStoppedByUser) {
            val uid = Binder.getCallingUid()
            if (uid != Process.myUid()) {
                val mode = DenyMode.from(settingsRepo.denyMode)
                if (mode == DenyMode.DENY_DANGEROUS_SUCCESS || mode == DenyMode.DENY_ALL_SUCCESS) {
                    // 假装执行成功：不真正 transact，只回一个没有异常的空结果
                    NeoLog.w(NeoLog.SERVER) {
                        "remote_transact: 放行但不执行（返回成功）uid=$uid code=$code"
                    }
                    reply?.writeNoException()
                    return true
                }
                val label = runCatching {
                    context.packageManager.getNameForUid(uid)
                }.getOrNull() ?: uid.toString()
                NeoLog.w(NeoLog.SERVER) {
                    "remote_transact: 拒绝（服务已停止）uid=$uid ($label) code=$code"
                }
                throw SecurityException("Dhizuku service is stopped by user: uid=$uid")
            }
            NeoLog.i(NeoLog.SERVER) {
                "remote_transact: 本应用自身调用，服务停止状态下放行 code=$code"
            }
        }

        // remote_transact 是最危险的一条路径：第三方可用 binderWrapper 包装任意
        // binder（含 DevicePolicyManager）以本应用的设备所有者身份执行。
        // 基类只在 remote_process 时回调 onAudit，这条路径需要在这里补审计。
        val descriptor = runCatching { binder?.interfaceDescriptor }.getOrNull()
        val callerUid = Binder.getCallingUid()
        val callerLabel = runCatching {
            context.packageManager.getNameForUid(callerUid)
        }.getOrNull() ?: callerUid.toString()
        NeoLog.i(NeoLog.SERVER) {
            "remote_transact: uid=$callerUid ($callerLabel) " +
                "target=${descriptor ?: "unknown"} code=$code"
        }
        // 解析方法名与一句人话描述，同时供审计、能力判定、高危拦截使用
        val method = DpmApiCatalog.methodName(code)
        val human = DpmApiCatalog.describe(descriptor, code, data)
        NeoLog.d(NeoLog.SERVER) {
            "remote_transact: 识别 method=${method ?: "未知"} 描述=$human"
        }

        if (NeoDhizuku.on(NeoDhizuku.HardenWipeGuard)
            && DpmApiCatalog.isWipe(binder, code)
        ) {
            // 擦除设备数据：不可逆操作，默认一律拦截，需用户多级确认后才放行。
            // 即便当前是设备所有者也拦截 —— 这条规则保护的是"被第三方借身份擦除"。
            val granted = WipeGuard.isGranted(callerUid)
            NeoLog.w(NeoLog.SERVER) {
                "wipe guard: uid=$callerUid ($callerLabel) granted=$granted"
            }
            if (!granted) {
                WipeGuard.noteRejected(callerUid, callerLabel)
                // 意图必须留痕：这正是"想测试又不给权限"的用户最关心的信息
                auditRemoteTransact(
                    callerUid, callerLabel, descriptor, code,
                    extraDetail = "【已拦截】$human"
                )
                runCatching { AlertNotifier.wipeBlocked(context, callerLabel) }
                    .onFailure {
                        NeoLog.w(NeoLog.SERVER) { "擦除拦截通知发送失败：${it.message}" }
                    }
                throw SecurityException(
                    "Wiping device data requires explicit user confirmation: uid=$callerUid"
                )
            }
            NeoLog.w(NeoLog.SERVER) {
                "wipe guard: 用户已确认，放行 uid=$callerUid ($callerLabel)"
            }
            WipeGuard.consume(callerUid)
        }

        // 能力分级：当前身份不足以执行该方法时，按服务策略处理
        if (NeoDhizuku.on(NeoDhizuku.UseCapabilityTier)
            && descriptor == DpmApiCatalog.DPM_DESCRIPTOR
        ) {
            val level = DhizukuState.state.level
            if (!CapabilityLevel.canExecute(level, method)) {
                val mode = InsufficientMode.from(settingsRepo.insufficientMode)
                val required = CapabilityLevel.requiredLevel(method)
                NeoLog.w(NeoLog.SERVER) {
                    "能力不足: uid=$callerUid ($callerLabel) method=$method " +
                        "当前=${level} 需要=${required} 处理=${mode} 描述=$human"
                }
                // 无论放行还是拒绝，意图都完整入账
                auditRemoteTransact(
                    callerUid, callerLabel, descriptor, code,
                    extraDetail = "【能力不足(${level}→${required})】$human"
                )
                when (mode) {
                    InsufficientMode.RETURN_FAIL -> throw SecurityException(
                        "Insufficient capability: ${level.name} cannot execute " +
                            "${method ?: "unknown"} (requires ${required.name})"
                    )
                    InsufficientMode.RETURN_SUCCESS -> {
                        // 假装执行成功：不真正 transact，只回一个没有异常的空结果
                        reply?.writeNoException()
                        return true
                    }
                    InsufficientMode.NO_ACTION -> {
                        // 挂起：不回调也不返回，客户端一直等。
                        // 不能用 sleep 阻塞 binder 线程，否则整个服务都会卡住，
                        // 这里靠"直接返回 true 且不写 reply"让对端等待。
                        return true
                    }
                }
            }
        }

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
                // 通知只是提示，失败绝不能影响拦截本身
                runCatching { AlertNotifier.transferBlocked(context, label) }
                    .onFailure {
                        NeoLog.w(NeoLog.SERVER) { "拦截通知发送失败：${it.message}" }
                    }
                throw SecurityException(
                    "Owner transfer is disabled by user: uid=$uid"
                )
            }
            runCatching { AlertNotifier.transferAllowed(context, label) }
                .onFailure {
                    NeoLog.w(NeoLog.SERVER) { "放行通知发送失败：${it.message}" }
                }
        }

        // 正常放行同样要入账。
        //
        // 「擦除拦截」和「能力不足」之外，正常调用同样要入账：
        // 它是最高频、也最该被看到的路径（第三方以本应用身份调用 DevicePolicyManager）。
        //
        // 复用上面已解析好的 human：它带实参（例如"把密码最小长度改为 6"），
        // 重新解析会丢参数。
        auditRemoteTransact(
            callerUid, callerLabel, descriptor, code,
            human = human
        )

        return super.onRemoteTransact(binder, code, data, reply, flags)
    }

    private fun formatTime(timestamp: Long): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))

    private companion object {
        /** 每条调用记录附带的运行日志行数 */
        const val LOG_EXCERPT_LINES = 20

        /** 兜底通知的判定延迟：够弹窗起来，又不至于让用户等太久 */
        const val FALLBACK_NOTIFY_DELAY_MILLIS = 3000L
    }
}
