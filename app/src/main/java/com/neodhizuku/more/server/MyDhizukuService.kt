package com.neodhizuku.more.server

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log

import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity
import com.neodhizuku.more.data.settings.repo.LogRepo

import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.rosan.dhizuku.aidl.IDhizukuClient
import com.rosan.dhizuku.aidl.IDhizukuRemoteProcess
import com.rosan.dhizuku.aidl.IDhizukuRequestPermissionListener
import com.rosan.dhizuku.aidl.IDhizukuUserServiceConnection
import com.neodhizuku.more.data.common.util.getPackageInfoForUid
import com.neodhizuku.more.data.common.util.getPackageNameForUid
import com.neodhizuku.more.data.common.util.getSignaturesForUid
import com.neodhizuku.more.data.common.util.signature
import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.repo.ApiPolicyRepo
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.DenyMode
import com.neodhizuku.more.data.settings.repo.InsufficientMode
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.data.settings.repo.WhitelistRepo
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
 * - `null` 对应 `isPermissionGranted()`，客户端靠它判断能不能用
 * - `get_delegated_scopes` 是只读查询
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
    private val whitelistRepo by inject<WhitelistRepo>()
    private val apiPolicyRepo by inject<ApiPolicyRepo>()

    /** 审计落库用的后台作用域，不阻塞 binder 线程 */
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 返回给客户端的版本名。
     * 默认返回上游 Dhizuku 的版本（[Variables.SERVICE_VERSION_NAME]）：部分客户端会
     * 校验版本，返回本项目的版本可能被判定为不兼容。用户可在「服务」页关闭该兼容模式，
     * 届时返回本项目自身的版本名。
     * `FixVersionName` 关闭时回退到上游行为（用版本码凑数），不读本开关。
     */
    // Dhizuku 自身的 AIDL 接口一律放行，不参与单项配置：
    // 能单独配置的是系统接口本身（见 DpmPolicyCatalog / enforceDpmPolicy）。
    override fun getVersionCode(): Int = super.getVersionCode()

    /**
     * 单项系统接口策略判定；返回 true 表示继续执行，false 表示本次调用已处理完毕。
     * 只作用于设备策略接口（[DpmPolicyCatalog] 收录的方法），
     * 未收录的方法一律放行 —— 不以"认不出"为由拦截正常调用。
     * 排除本应用自身、完全体，且首页停服时不介入（交给既有的拒绝模式处理）。
     */
    private fun enforceDpmPolicy(
        method: String?,
        uid: Int,
        pkg: String?,
        label: String,
        reply: Parcel?,
        fullAccess: Boolean
    ): Boolean {
        if (method == null) return true
        if (uid == Process.myUid()) return true
        if (fullAccess) return true
        if (runCatching { settingsRepo.isServiceStoppedByUser }.getOrDefault(false)) return true
        val def = DpmPolicyCatalog.byId(method) ?: return true
        val decision = apiPolicyRepo.decide(pkg, def.id, def.defaultAllowed)
        if (decision.allowed) return true
        val from = if (decision.fromApp) "本应用配置" else "全局配置"
        val shown = DpmPolicyStrings.name(context, def.id)
        val readable = DpmPolicyStrings.readable(context, def.id)
        val mode = runCatching { DenyMode.from(settingsRepo.denyMode) }
            .getOrDefault(DenyMode.DEFAULT)
        NeoLog.w(NeoLog.PERM) {
            "单项策略: 拒绝 pkg=$pkg 接口=$readable 依据=$from 处理=$mode"
        }
        return when (mode) {
            DenyMode.DENY_ALL_SUCCESS, DenyMode.DENY_DANGEROUS_SUCCESS -> {
                // 不真正执行，只回一个成功结果，客户端看不出这一项被拒。
                reply?.writeNoException()
                false
            }
            DenyMode.NO_ACTION -> {
                // 不回写也不抛异常，调用停在这里等待。
                false
            }
            else -> {
                runCatching { AlertNotifier.apiDenied(context, pkg, shown) }
                    .onFailure { NeoLog.w(NeoLog.PERM) { "单项策略通知发送失败：${it.message}" } }
                throw SecurityException(
                    "Device policy denied by user: $readable uid=$uid"
                )
            }
        }
    }

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

    // 「静默拒绝并返回成功」：全接口不执行
    // 该模式下第三方能正常拿到 binder（init() 成功、isPermissionGranted() 为 true），
    // 但除版本号之外的所有接口都不真正执行，只回一个成功结果 ——
    // 与「拒绝危险服务并返回成功」的区别是：后者只拦危险操作，只读查询仍返回真实值。
    // 判定口径与 checkCallingPermission 里的拒绝分支保持一致：
    // 用户已停止服务，且调用方不是本应用自身。
    private fun isSilentSuccessMode(uid: Int): Boolean {
        if (!runCatching { settingsRepo.isServiceStoppedByUser }.getOrDefault(false)) return false
        if (uid == Process.myUid()) return false
        return runCatching { DenyMode.from(settingsRepo.denyMode) }
            .getOrDefault(DenyMode.DEFAULT) == DenyMode.DENY_ALL_SUCCESS
    }

    /**
     * 查询调用方对某个权限的持有情况，仅用于诊断日志。
     * 返回 PERMISSION_GRANTED / PERMISSION_DENIED 的数值，取不到时返回 -1。
     * 要指定 pid/uid 只能用 Context#checkPermission（checkCallingPermission
     * 只有单参数版本，取的是当前调用方）。
     */
    private fun permissionState(permission: String, pid: Int, uid: Int): Int =
        runCatching { context.checkPermission(permission, pid, uid) }
            .getOrDefault(-1)

    override fun remoteProcess(
        cmd: Array<out String>?,
        env: Array<out String>?,
        dir: String?
    ): IDhizukuRemoteProcess? {
        val uid = Binder.getCallingUid()
        if (isSilentSuccessMode(uid)) {
            // 句柄有上限：客户端不 destroy 时不会释放，未授权方反复请求可耗尽 fd
            if (!NoOpRemoteProcess.available()) {
                NeoLog.w(NeoLog.SERVER) {
                    "remote_process: 空句柄已达上限，拒绝新建（疑似 fd 耗尽攻击）uid=$uid"
                }
                throw SecurityException("Too many pending no-op processes: uid=$uid")
            }
            NeoLog.w(NeoLog.SERVER) {
                "remote_process: 静默不执行（返回成功）uid=$uid cmd=${cmd?.joinToString(" ")}"
            }
            return NoOpRemoteProcess()
        }
        return super.remoteProcess(cmd, env, dir)
    }

    override fun bindUserService(
        connection: IDhizukuUserServiceConnection?,
        bundle: Bundle?
    ) {
        val uid = Binder.getCallingUid()
        if (isSilentSuccessMode(uid)) {
            NeoLog.w(NeoLog.SERVER) { "bind_user_service: 静默不执行（返回成功）uid=$uid" }
            return
        }
        super.bindUserService(connection, bundle)
    }

    override fun unbindUserService(bundle: Bundle?) {
        val uid = Binder.getCallingUid()
        if (isSilentSuccessMode(uid)) {
            NeoLog.w(NeoLog.SERVER) { "unbind_user_service: 静默不执行（返回成功）uid=$uid" }
            return
        }
        super.unbindUserService(bundle)
    }

    override fun unbindUserServiceByConnection(
        connection: IDhizukuUserServiceConnection?,
        bundle: Bundle?
    ) {
        val uid = Binder.getCallingUid()
        if (isSilentSuccessMode(uid)) {
            NeoLog.w(NeoLog.SERVER) { "unbind_user_service: 静默不执行（返回成功）uid=$uid" }
            return
        }
        super.unbindUserServiceByConnection(connection, bundle)
    }

    override fun getDelegatedScopes(packageName: String?): Array<out String>? {
        val uid = Binder.getCallingUid()
        if (isSilentSuccessMode(uid)) {
            NeoLog.w(NeoLog.SERVER) {
                "get_delegated_scopes: 静默不执行（返回空）uid=$uid pkg=$packageName"
            }
            return emptyArray()
        }
        return super.getDelegatedScopes(packageName)
    }

    override fun setDelegatedScopes(packageName: String?, scopes: Array<out String>?) {
        val uid = Binder.getCallingUid()
        if (isSilentSuccessMode(uid)) {
            NeoLog.w(NeoLog.SERVER) {
                "set_delegated_scopes: 静默不执行（返回成功）uid=$uid pkg=$packageName"
            }
            return
        }
        super.setDelegatedScopes(packageName, scopes)
    }

    /**
     * 未开启「加固授权入口」时，事务码 17 不会被 onTransact 拦截，
     * 而是由 AIDL 直接派发到这里 —— 因此静默判断要在两侧都做一遍。
     */
    override fun requestPermission(listener: IDhizukuRequestPermissionListener?) {
        val uid = Binder.getCallingUid()
        val silentResult = silentRequestPermissionResult(uid)
        if (silentResult != null) {
            NeoLog.w(NeoLog.SERVER) {
                "requestPermission: 静默回调 uid=$uid result=$silentResult"
            }
            runCatching { listener?.onRequestPermission(silentResult) }
            return
        }
        NeoLog.i(NeoLog.PERM) {
            "requestPermission: 默认派发 uid=$uid listener=${listener != null}"
        }
        super.requestPermission(listener)
    }

    /**
     * 两种静默模式下授权请求的直接结果；非静默场景返回 null（照常弹窗）。
     * 注意：静默的含义是**全程无界面**，所以连授权弹窗也不能拉起 ——
     * 否则"静默拒绝"实际上还是会弹一个框，只是点了拒绝。
     */
    private fun silentRequestPermissionResult(uid: Int): Int? {
        if (!runCatching { settingsRepo.isServiceStoppedByUser }.getOrDefault(false)) return null
        if (uid == Process.myUid()) return null
        return when (runCatching { DenyMode.from(settingsRepo.denyMode) }
            .getOrDefault(DenyMode.DEFAULT)) {
            DenyMode.DENY_ALL_FAIL -> PackageManager.PERMISSION_DENIED
            DenyMode.DENY_ALL_SUCCESS -> PackageManager.PERMISSION_GRANTED
            else -> null
        }
    }

    // 拦截 requestPermission 事务（事务码 17）
    // 之所以用 onTransact 而不是 override requestPermission()：
    // app 可能运行在「本地 API 源码」或「Maven 官方包」两种模式下，
    // Maven 的 IDhizuku 没有该方法，直接 override 会导致编译失败。
    // 事务码 17 在两种模式下都是空闲的（官方接口最高只用到 16），
    // 因此这里拦截既能兼容两种模式，又能在两种模式下都提供"服务端校验身份"的加固。
    // 但只对本项目的接口布局成立：旧版客户端（原版 API）的 17 号事务未必是
    // requestPermission，参数里也未必有 listener，强行解析只会读出垃圾。
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        // 完全体状态查询。客户端只能读，不能申请或改写。
        if (code == DhizukuVariables.TRANSACT_QUERY_FULL_ACCESS) {
            val handled = runCatching { handleQueryFullAccess(data, reply) }
                .onFailure { NeoLog.e(NeoLog.PERM, { "isFullAccessGranted 异常" }, it) }
                .getOrDefault(false)
            if (handled) return true
            return super.onTransact(code, data, reply, flags)
        }
        if (NeoDhizuku.on(NeoDhizuku.HardenPermissionEntry)
            && code == RequestPermissionToken.TRANSACT_REQUEST_PERMISSION
        ) {
            // 只有上报过新版客户端 API 的应用才走加固路径；原版 API 一律原样放行。
            val uid = Binder.getCallingUid()
            if (ClientApiVersion.of(uid) <= ClientApiVersion.LEGACY) {
                NeoLog.d(NeoLog.PERM) { "requestPermission: 旧版客户端，原样放行 uid=$uid" }
                return dispatchOrRecover(code, data, reply, flags)
            }

            // 读取位置必须在读取**之前**记录，否则还原后 super 会读到一个错位的
            // 接口头，抛出 SecurityException，直接把客户端打崩。
            val start = data.dataPosition()
            val ok = runCatching { handleRequestPermission(data, reply) }
                .onFailure { NeoLog.e(NeoLog.PERM, { "requestPermission 异常" }, it) }
                .getOrDefault(false)
            if (ok) return true

            // 加固路径没能处理时，必须**真正**把事务交回 AIDL 生成的 Stub 派发。
            // 必须交回 Stub 派发：直接 return false 会被 Proxy 视为事务失败，
            // 授权结果既没回调也不弹窗。
            if (start >= 0 && start <= data.dataSize()) {
                data.setDataPosition(start)
            }
            NeoLog.w(NeoLog.PERM) {
                "requestPermission: 加固路径未处理，交回 AIDL 默认派发 start=$start"
            }
            return dispatchOrRecover(code, data, reply, flags)
        }
        return super.onTransact(code, data, reply, flags)
    }

    /**
     * 把事务交给 AIDL 生成的 Stub 派发，并保证异常不会穿透回客户端。
     * 旧版客户端会把 SecurityException 一路抛到 Application.onCreate，
     * 表现为"应用一打开就崩"，比单纯的"无权限"更难定位，所以这里兜住。
     */
    private fun dispatchOrRecover(
        code: Int,
        data: Parcel,
        reply: Parcel?,
        flags: Int
    ): Boolean = try {
        super.onTransact(code, data, reply, flags)
    } catch (t: Throwable) {
        NeoLog.e(NeoLog.PERM, { "requestPermission: 默认派发异常 code=$code" }, t)
        runCatching { reply?.writeNoException() }
        true
    }

    private fun handleRequestPermission(data: Parcel, reply: Parcel?): Boolean {
        data.enforceInterface(RequestPermissionToken.IDHIZUKU_DESCRIPTOR)

        // 客户端未必真的写了 listener：API 版本不一致 / 事务码语义不同时，
        // readStrongBinder() 读不到 binder，会打出
        // "Attempt to read object from Parcel ... not in the object list"，
        // 并返回一个永远回调不出去的代理。授权结果就此丢失 ——
        // 服务端这边的记录明明已经是 allowApi=true，客户端却始终提示无权限。
        // 因此读不到 listener 时直接返回 false，由 onTransact 还原读取位置并
        // 交回 AIDL 默认派发（会落到下面的 requestPermission(listener) 兜底），
        // 而不是签发一个 listener 为空的 token 然后假装成功。
        if (data.dataAvail() <= 0) {
            NeoLog.w(NeoLog.PERM) {
                "requestPermission: 事务无 payload（avail=${data.dataAvail()}）"
            }
            return false
        }
        val binder = data.readStrongBinder()
        if (binder == null) {
            NeoLog.w(NeoLog.PERM) { "requestPermission: 读不到 listener" }
            return false
        }
        val listener = IDhizukuRequestPermissionListener.Stub.asInterface(binder)
        val uid = Binder.getCallingUid()
        val pkg = runCatching { context.packageManager.getNameForUid(uid) }.getOrNull()
        val token = RequestPermissionToken.issue(uid, listener)

        NeoLog.i(NeoLog.PERM) {
            "requestPermission: uid=$uid pkg=$pkg " +
                "token=${token.take(8)}… enabled=${settingsRepo.isDhizukuEnabled}"
        }

        // 「不做任何处理」模式：既不拉起授权页，也不回调结果。
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

        // 静默模式：不拉起授权页，直接回写结果。
        val silentResult = silentRequestPermissionResult(uid)
        if (silentResult != null) {
            NeoLog.i(NeoLog.PERM) {
                "requestPermission: 静默回调 uid=$uid pkg=$pkg result=$silentResult"
            }
            runCatching { listener.onRequestPermission(silentResult) }
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

        // 失败时不立刻回调"拒绝"。
        // 后台启动 Activity 限制（Android 10+）下，从后台 startActivity 可能
        // **静默失败**：既不弹窗也不抛异常。此时若回调 DENIED，客户端会认为
        // 用户拒绝了，之后不再重试 —— 表现为"永远无法授权"。
        // 保留 token，延迟确认弹窗是否真的起来；没起来就发通知，
        // 由用户点击拉起（用户点击属于用户发起，不受后台启动限制）。
        if (!started) {
            AlertNotifier.permissionRequest(context, uid, token)
        } else {
            scheduleFallbackNotify(uid, token)
        }
        reply?.writeNoException()
        return true
    }

    /** 完全体状态查询：客户端只读，不能申请或改写。 */
    private fun handleQueryFullAccess(data: Parcel, reply: Parcel?): Boolean {
        val start = data.dataPosition()
        val magic = runCatching {
            data.enforceInterface(DhizukuVariables.IDHIZUKU_DESCRIPTOR)
            if (data.dataAvail() < 4) -1 else data.readInt()
        }.getOrDefault(-1)
        if (magic != DhizukuVariables.FULL_ACCESS_QUERY_MAGIC) {
            runCatching { data.setDataPosition(start) }
            return false
        }
        val uid = Binder.getCallingUid()
        val signature = context.packageManager.getPackageInfoForUid(uid)?.signature
        val granted = FullAccessStore.isFull(uid, signature)
        reply?.writeNoException()
        reply?.writeInt(if (granted) 1 else 0)
        return true
    }

    /**
     * 兜底通知：startActivity 调用成功不代表弹窗真的出现了（BAL 静默拦截）。
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
        return try {

        // 把判定依赖的关键状态一次打全。
        // 两条权限名的持有情况一并打出：硬编码客户端只持有上游权限名，
        // 若这里看到 legacy=-1，说明它连补声明的那条都没拿到（多半是
        // protectionLevel 不是 normal，或客户端装在本应用改声明之前）。
        val permLegacy = permissionState(DhizukuVariables.OFFICIAL_PERMISSION_API, callingPid, callingUid)
        val permOwn = permissionState("${context.packageName}.permission.API", callingPid, callingUid)
        NeoLog.d(NeoLog.PERM) {
            "check: uid=$callingUid pkg=$pkg 接口=${ApiPolicyCatalog.labelOf(func)} " +
                "owner=${DhizukuState.state.isOwner} enabled=${settingsRepo.isDhizukuEnabled} " +
                "whitelist=${settingsRepo.isWhitelistMode} " +
                "permLegacy=$permLegacy permOwn=$permOwn"
        }

        if (!settingsRepo.isDhizukuEnabled) {
            NeoLog.w(NeoLog.PERM) { "check: 拒绝（API 已停用）uid=$callingUid pkg=$pkg" }
            return false
        }

        // 用户已停止守护服务：按拒绝模式处理。
        // 注意 Provider 是 ContentProvider，随进程存活，停掉 DaemonService
        // 并不会让第三方断开，必须在这里显式拦截。
        // 【必须排除本应用自身】激活流程（ActivateViewModel#activateByDhizuku）
        // 会用 DhizukuBinderWrapper 包装 DevicePolicyManager 调 transferOwnership，
        // 走的正是 remote_transact —— 若不排除自身 uid，服务处于停止状态时
        // 本应用自己激活会被自己拦掉，表现为 NOT_DEVICE_ADMIN。
        // 用户停服 / 恢复服务时同步清理擦除放行标记。
        // 放行标记是一次性且有 5 分钟时效的"敞口"，服务既然已经停了，
        // 就不该继续留着。恢复服务时重置标记，下次停服仍会清一次。
        if (settingsRepo.isServiceStoppedByUser) {
            if (wipeGrantsRevoked.compareAndSet(false, true)) {
                NeoLog.i(NeoLog.SERVER) { "check: 服务已停止，撤销全部高危一次性放行标记" }
                WipeGuard.revokeAll()
            }
        } else {
            wipeGrantsRevoked.set(false)
        }

        if (settingsRepo.isServiceStoppedByUser && callingUid != Process.myUid()) {
            val mode = DenyMode.from(settingsRepo.denyMode)
            val danger = func !in READ_ONLY_FUNCS_WHEN_STOPPED
            when (mode) {
                DenyMode.DENY_ALL_FAIL -> {
                    // 一律拒绝，不区分"危险 / 只读"：只读查询也拿不到真实值
                    NeoLog.w(NeoLog.PERM) { "check: 拒绝（静默·返回失败）接口=${ApiPolicyCatalog.labelOf(func)} pkg=$pkg" }
                    return false
                }
                DenyMode.DENY_ALL_SUCCESS -> {
                    // 一律放行，但成功结果由各接口 / onRemoteTransact 直接回写，不真正执行
                    NeoLog.w(NeoLog.PERM) { "check: 放行（静默·返回成功）接口=${ApiPolicyCatalog.labelOf(func)} pkg=$pkg" }
                    return true
                }
                DenyMode.DENY_DANGEROUS_FAIL -> {
                    if (danger) {
                        NeoLog.w(NeoLog.PERM) {
                            "check: 拒绝（服务已停止，仅允许查看）接口=${ApiPolicyCatalog.labelOf(func)} pkg=$pkg"
                        }
                        return false
                    }
                }
                DenyMode.DENY_DANGEROUS_SUCCESS -> {
                    // 危险操作也放行，但在 onRemoteTransact 处不真正执行，
                    // 只写回一个成功结果。
                    if (danger) {
                        NeoLog.w(NeoLog.PERM) {
                            "check: 放行但不执行（返回成功）接口=${ApiPolicyCatalog.labelOf(func)} pkg=$pkg"
                        }
                    }
                }
                DenyMode.NO_ACTION -> {
                    // 授权请求走 requestPermission 的挂起路径（非阻塞）；
                    // 其余危险操作按失败处理，避免长时间占用 binder 线程。
                    if (danger) {
                        NeoLog.w(NeoLog.PERM) {
                            "check: 拒绝（不做任何处理）接口=${ApiPolicyCatalog.labelOf(func)} pkg=$pkg"
                        }
                        return false
                    }
                }
            }
        }

        val callerSignature = context.packageManager.getPackageInfoForUid(callingUid)?.signature
        if (FullAccessStore.isFull(callingUid, callerSignature)) {
            NeoLog.d(NeoLog.PERM) { "check: 完全体放行 uid=$callingUid pkg=$pkg" }
            return true
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

        // 单项接口策略：逐个接口决定这个应用能用到哪一步。
        // 与拒绝服务相互独立 —— 拒绝服务是整体开关，这里是细项。
        // 但拒绝服务优先：首页停服时上面已按拒绝模式统一处理并返回，
        // 这里只在服务正常运行时生效，避免两套规则叠加出互相矛盾的结果。
        // 排除本应用自身：激活流程走 remote_transact 调 transferOwnership，
        // 若把自身也纳入单项策略，用户关闭「调用系统接口」后自己无法激活。
        // 说明：Dhizuku 自身的 AIDL 接口（读版本、执行命令、绑定用户服务…）
        // 一律放行、不参与单项配置 —— 它们由授权记录与拒绝服务管。
        // 可单独配置的是**被远程调用的系统接口本身**（禁用摄像头、锁屏…），
        // 判定在 onRemoteTransact 里做，见 enforceDpmPolicy。

        // 白名单模式：名单由用户在「共享服务 → 白名单」里手工维护。
        // 旧实现拿 entity.allowApi 当白名单 —— 那份名单完全由代码决定，
        // 用户无从干预。这里换成用户维护的名单；严格程度由实验性开关决定：
        // 关闭时只比对包名，开启时连加入时记录的签名一并比对。
        // 排除本应用自身：激活流程会用 DhizukuBinderWrapper 走 remote_transact
        // 调 transferOwnership，若把自身也算作第三方，开启白名单模式后
        // 本应用自己激活会被自己拦掉。
        if (settingsRepo.isWhitelistMode && callingUid != Process.myUid()) {
            val strictWhitelist = runCatching { settingsRepo.isExperimentalStrictWhitelist }
                .getOrDefault(false)
            val callerPkg = pkg
                ?: runCatching { context.packageManager.getPackageNameForUid(callingUid) }
                    .getOrNull()
            val signatures: Set<String> = if (strictWhitelist) {
                getSignaturesForUid(context.packageManager, callingUid)
            } else emptySet()
            val inWhitelist = callerPkg != null
                && whitelistRepo.isWhitelisted(callerPkg, signatures, strictWhitelist)

            if (NeoDhizuku.on(NeoDhizuku.FixWhitelistMode)) {
                if (func in HIGH_RISK_FUNCS && !inWhitelist) {
                    NeoLog.w(NeoLog.PERM) {
                        "check: 拒绝（白名单高危）接口=${ApiPolicyCatalog.labelOf(func)} pkg=$callerPkg strict=$strictWhitelist"
                    }
                    return false
                }
            } else {
                if (!inWhitelist) {
                    NeoLog.w(NeoLog.PERM) {
                        "check: 拒绝（白名单）pkg=$callerPkg strict=$strictWhitelist"
                    }
                    return false
                }
            }
        }

        // 「更严格的服务校验」：默认关闭。
        // 关闭时只按包名（授权记录）放行 —— 只认上游权限名
        // （com.rosan.dhizuku.permission.API）的硬编码客户端同样可用；
        // 开启后额外要求调用方持有本应用自身声明的权限，
        // 仅持有上游权限名的调用方会被拒绝。
        // 必须排除本应用自身：激活流程会用 DhizukuBinderWrapper 走 remote_transact
        // 调 transferOwnership，而本应用是权限的定义者、并未 uses-permission，
        // 不排除的话自己激活会被自己拦掉。
        val strictServiceCheck = runCatching { settingsRepo.isStrictServiceCheck }
            .getOrDefault(false)
        if (strictServiceCheck && callingUid != Process.myUid()) {
            // Context#checkCallingPermission 只有单参数版本（取当前 binder 调用方），
            // 要指定 pid/uid 必须用 checkPermission。
            val granted = runCatching {
                context.checkPermission(
                    "${context.packageName}.permission.API", callingPid, callingUid
                )
            }.getOrDefault(PackageManager.PERMISSION_DENIED)
            if (granted != PackageManager.PERMISSION_GRANTED) {
                NeoLog.w(NeoLog.PERM) {
                    "check: 拒绝（严格校验：未持有本应用权限）uid=$callingUid pkg=$pkg"
                }
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
            NeoLog.d(NeoLog.PERM) {
                "check: 通过 uid=$callingUid pkg=$pkg 接口=${ApiPolicyCatalog.labelOf(func)}"
            }
            return true
        }

        val signature = this.signature
            ?: context.packageManager.getPackageInfoForUid(callingUid)?.signature
        if (signature == null) {
            NeoLog.w(NeoLog.PERM) { "check: 拒绝（取不到签名）pkg=$pkg" }
            return false
        }
        this.signature = signature

        // 授权记录里没采集到签名（早期版本授权、或授权流程未写入）时，
        // 若一律按"签名不匹配"拒绝，用户会看到「已在服务端授权、客户端仍报无权限」
        // 且没有任何可自行修复的入口。这里用当前签名补录一次再放行。
        if (entity.signature.isBlank()) {
            val recorded = signature
            auditScope.launch {
                runCatching {
                    appRepo.upsert(entity.copy(signature = recorded, allowApi = true))
                }.onFailure {
                    NeoLog.w(NeoLog.PERM) { "check: 补录签名失败 pkg=$pkg ${it.message}" }
                }
            }
            NeoLog.i(NeoLog.PERM) { "check: 通过（补录空签名）uid=$callingUid pkg=$pkg" }
            return true
        }

        if (signature != entity.signature) {
            NeoLog.w(NeoLog.PERM) { "check: 拒绝（签名不匹配）pkg=$pkg" }
            return false
        }

        NeoLog.d(NeoLog.PERM) { "check: 通过 uid=$callingUid pkg=$pkg 接口=${ApiPolicyCatalog.labelOf(func)}" }
        return true
        } catch (t: Throwable) {
            // isPermissionGranted() 只捕获 SecurityException；服务端若抛出其它异常，
            // 会穿透成 RemoteException，客户端直接崩在调用处（表现为"无权限" toast、
            // 且不会弹出授权窗，用户无从修复）。这里统一兜底为拒绝，
            // 让它回落成正常的 SecurityException -> false -> 走授权流程。
            NeoLog.w(NeoLog.PERM) {
                "check: 异常兜底 uid=$callingUid pkg=$pkg " +
                    "${t.javaClass.simpleName}: ${t.message}"
            }
            false
        }
    }

    /**
     * 让「已发起调用但尚未授权」的应用出现在软件管理页。
     * 有些客户端（例如 InstallerX）并不在清单里声明 Dhizuku API 权限，
     * 因此不会出现在 `getPackagesHoldingPermissions` 的候选集里；
     * 它又从未弹过授权窗（查询 `isPermissionGranted()` 被拒就走了），
     * 数据库里也就没有记录。结果是软件管理页完全没有它的卡片，
     * 用户想授权都找不到入口。
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

                // Maven 上的官方 API 客户端不会发起授权弹窗，
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
        val resolved = human ?: DpmApiCatalog.describe(descriptor, code, context = context)
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
        val descriptor = runCatching { binder?.interfaceDescriptor }.getOrNull()
        // 双保险：即便调用方绕过了 checkCallingPermission，这里再拦一次。
        // 同样排除本应用自身 uid —— 激活流程要用这条路把所有者转移给自己。
        if (settingsRepo.isServiceStoppedByUser) {
            val uid = Binder.getCallingUid()
            if (uid != Process.myUid()) {
                val mode = DenyMode.from(settingsRepo.denyMode)
                if (mode == DenyMode.DENY_DANGEROUS_SUCCESS || mode == DenyMode.DENY_ALL_SUCCESS) {
                    // 假装执行成功：不真正 transact，只回一个没有异常的空结果
                    NeoLog.w(NeoLog.SERVER) {
                        "remote_transact: 放行但不执行（返回成功）uid=$uid " +
                            "接口=${DpmApiCatalog.interfaceLabel(context, descriptor, code)}"
                    }
                    reply?.writeNoException()
                    return true
                }
                val label = runCatching {
                    context.packageManager.getNameForUid(uid)
                }.getOrNull() ?: uid.toString()
                NeoLog.w(NeoLog.SERVER) {
                    "remote_transact: 拒绝（服务已停止）uid=$uid ($label) " +
                        "接口=${DpmApiCatalog.interfaceLabel(context, descriptor, code)}"
                }
                throw SecurityException("Dhizuku service is stopped by user: uid=$uid")
            }
            NeoLog.i(NeoLog.SERVER) {
                "remote_transact: 本应用自身调用，服务停止状态下放行 " +
                "接口=${DpmApiCatalog.interfaceLabel(context, descriptor, code)}"
            }
        }

        // remote_transact 是最危险的一条路径：第三方可用 binderWrapper 包装任意
        // binder（含 DevicePolicyManager）以本应用的设备所有者身份执行。
        // 基类只在 remote_process 时回调 onAudit，这条路径需要在这里补审计。
        val callerUid = Binder.getCallingUid()
        val callerSignature = context.packageManager.getPackageInfoForUid(callerUid)?.signature
        val fullAccess = FullAccessStore.isFull(callerUid, callerSignature)
        val callerLabel = runCatching {
            context.packageManager.getNameForUid(callerUid)
        }.getOrNull() ?: callerUid.toString()
        NeoLog.i(NeoLog.SERVER) {
            "remote_transact: uid=$callerUid ($callerLabel) " +
                "目标=${DpmApiCatalog.targetLabel(descriptor)} " +
                "接口=${DpmApiCatalog.interfaceLabel(context, descriptor, code)}"
        }
        // 解析方法名与一句人话描述，同时供审计、能力判定、高危拦截使用
        val method = DpmApiCatalog.methodName(code)
        val human = DpmApiCatalog.describe(descriptor, code, data, context)
        NeoLog.d(NeoLog.SERVER) {
            "remote_transact: 识别 " +
                "接口=${DpmApiCatalog.interfaceLabel(context, descriptor, code)} 描述=$human"
        }

        // 单项系统接口策略：用户在「设置 - 服务 - 单项接口配置」里的选择；完全体不参与。
        // 只对设备策略接口生效（descriptor 命中才判），且排在守卫之前 ——
        // 用户显式关掉某项时应当立即拦下，不必再走后面的守卫流程。
        if (descriptor == DpmApiCatalog.DPM_DESCRIPTOR) {
            val pkg = runCatching {
                context.packageManager.getPackagesForUid(callerUid)?.firstOrNull()
            }.getOrNull()
            if (!enforceDpmPolicy(method, callerUid, pkg, callerLabel, reply, fullAccess)) {
                auditRemoteTransact(
                    callerUid, callerLabel, descriptor, code,
                    extraDetail = "【已拦截：单项策略】$human"
                )
                return true
            }
        }

        // 守卫降级提示（只提示一次）。
        // 反射失效时 isWipe / isOwnerTransfer 都会 fail-closed，表现为
        // 设备策略调用**全部**被拦。这个行为是对的，但如果不说明原因，
        // 用户只会以为应用坏了，最后往往是关掉加固 —— 反而退回无保护状态。
        if (descriptor == DpmApiCatalog.DPM_DESCRIPTOR
            && (NeoDhizuku.on(NeoDhizuku.HardenWipeGuard)
                || NeoDhizuku.on(NeoDhizuku.HardenOwnerTransferGuard))
            && !DpmApiCatalog.isCatalogLoaded()
            && degradeNotified.compareAndSet(false, true)
        ) {
            NeoLog.w(NeoLog.SERVER) { "守卫降级：方法对照表未载入，设备策略调用将按保守策略拦截" }
            runCatching { AlertNotifier.guardDegraded(context) }
                .onFailure { NeoLog.w(NeoLog.SERVER) { "守卫降级通知发送失败：${it.message}" } }
        }

        if (NeoDhizuku.on(NeoDhizuku.HardenWipeGuard)
            && DpmApiCatalog.isWipe(binder, code)
            && !fullAccess
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
            && !fullAccess
        ) {
            val level = DhizukuState.state.level
            if (!CapabilityLevel.canExecute(level, method)) {
                val mode = InsufficientMode.from(settingsRepo.insufficientMode)
                val required = CapabilityLevel.requiredLevel(method)
                NeoLog.w(NeoLog.SERVER) {
                    "能力不足: uid=$callerUid ($callerLabel) " +
                        "接口=${DpmApiCatalog.interfaceLabel(context, descriptor, code)} " +
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
                            "${DpmApiCatalog.interfaceLabel(context, descriptor, code)} " +
                                "(requires ${required.name})"
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

            // 本应用自身的激活流程与设备身份页转移都在这里放行：
            // 用户在应用内主动发起的转移走二次确认弹窗，不受第三方开关影响。
            if (uid == Process.myUid()) {
                NeoLog.i(NeoLog.SERVER) {
                    "owner transfer: 应用内转移，直接放行 uid=$uid"
                }
            } else if (!settingsRepo.isAllowOwnerTransfer && !fullAccess) {
                // 关闭即阻止：只审计并提示去开开关，没有任何确认可以绕过。
                NeoLog.w(NeoLog.SERVER) {
                    "owner transfer: 第三方转移开关未开启，拦截 uid=$uid ($label)"
                }
                auditRemoteTransact(
                    callerUid, callerLabel, descriptor, code,
                    extraDetail = "【已拦截：第三方转移开关未开启】$human"
                )
                runCatching { AlertNotifier.transferBlocked(context, label) }
                    .onFailure {
                        NeoLog.w(NeoLog.SERVER) { "拦截通知发送失败：${it.message}" }
                    }
                throw SecurityException(
                    "Third-party owner transfer is disabled: uid=$uid"
                )
            } else {
                // 开关本身就是用户的确认：开启后第三方转移直接执行，
                // 不再弹确认、不再发警示，也不写任何一次性放行标记。
                NeoLog.i(NeoLog.SERVER) {
                    "owner transfer: 开关已开启，直接放行 uid=$uid ($label)"
                }
            }
        }

        // 正常放行同样要入账。
        // 「擦除拦截」和「能力不足」之外，正常调用同样要入账：
        // 它是最高频、也最该被看到的路径（第三方以本应用身份调用 DevicePolicyManager）。
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

        /**
         * 守卫降级通知是否已发过。
         * 进程级一次性：一次启动只提示一次，避免每次设备策略调用都弹通知。
         */
        val degradeNotified = java.util.concurrent.atomic.AtomicBoolean(false)

        /** 停服后是否已撤销过擦除放行标记（恢复服务时重置） */
        val wipeGrantsRevoked = java.util.concurrent.atomic.AtomicBoolean(false)

        /** 兜底通知的判定延迟：够弹窗起来，又不至于让用户等太久 */
        const val FALLBACK_NOTIFY_DELAY_MILLIS = 3000L
    }
}

/**
 * 静默模式下返回给调用方的空进程句柄。
 * 表现为"命令执行成功、没有任何输出、已正常退出"：
 * - 输入流 / 错误流的写端在构造时即关闭，客户端读到 EOF（空输出）
 * - 输出流的读端由后台线程丢弃，避免客户端大量写入时管道写满而阻塞
 * - exitValue / waitFor 一律返回 0，alive 为 false
 * 已知代价：客户端若不调用 destroy() 且一直持有写端，读端线程会一直阻塞。
 * 线程设为守护线程、且句柄位于本应用进程内，不会拖垮服务，仅占用少量资源。
 */
private class NoOpRemoteProcess : IDhizukuRemoteProcess.Stub() {

    private val outPipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
    private val inPipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
    private val errPipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()

    init {
        activeCount.incrementAndGet()
        // 让客户端读到空输出：写端立刻关闭，读端随即 EOF
        listOf(inPipe, errPipe).forEach { runCatching { it?.get(1)?.close() } }
        // 丢弃客户端写进输出流的内容，防止管道写满后把调用方卡住
        val readEnd = runCatching { outPipe?.get(0) }.getOrNull()
        if (readEnd != null) {
            Thread({
                runCatching {
                    val buf = ByteArray(8192)
                    java.io.FileInputStream(readEnd.fileDescriptor).use { stream ->
                        while (stream.read(buf) != -1) {
                        }
                    }
                }
            }, "noop-process-drain").apply { isDaemon = true }.start()
        }
    }

    override fun getOutputStream(): ParcelFileDescriptor? = outPipe?.get(1)
    override fun getInputStream(): ParcelFileDescriptor? = inPipe?.get(0)
    override fun getErrorStream(): ParcelFileDescriptor? = errPipe?.get(0)
    override fun exitValue(): Int = 0
    override fun destroy() = closeAll()
    override fun alive(): Boolean = false
    override fun waitFor(): Int = 0
    override fun waitForTimeout(timeout: Long, unit: String?): Boolean = true

    private fun closeAll() {
        listOf(outPipe, inPipe, errPipe).forEach { pipe ->
            runCatching { pipe?.get(0)?.close() }
            runCatching { pipe?.get(1)?.close() }
        }
        if (released.compareAndSet(false, true)) activeCount.decrementAndGet()
    }

    /** 防止 closeAll 被重复调用导致计数变成负数 */
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        /**
         * 同时存在的空句柄上限。
         * 客户端不调用 destroy() 时句柄不会释放（每个 3 对管道 + 1 个读线程），
         * 未授权的调用方反复请求就能耗尽本应用的 fd。这里设上限：
         * 达到上限时拒绝新建，宁可让这一次调用失败，也不让整个进程被拖垮。
         */
        private const val MAX_ACTIVE = 64

        private val activeCount = java.util.concurrent.atomic.AtomicInteger(0)

        fun available(): Boolean = activeCount.get() < MAX_ACTIVE
    }
}
