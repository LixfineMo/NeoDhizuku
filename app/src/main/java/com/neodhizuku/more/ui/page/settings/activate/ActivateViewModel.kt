package com.neodhizuku.more.ui.page.settings.activate

import android.annotation.SuppressLint
import android.app.admin.DeviceAdminInfo
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.system.Os

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.NeoDhizuku
import com.rosan.dhizuku.api.Dhizuku
import com.neodhizuku.more.data.common.util.has
import com.neodhizuku.more.data.common.util.requireDhizukuPermissionGranted
import com.neodhizuku.more.data.common.util.requireShizukuPermissionGranted
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.ui.page.settings.SettingsRoute

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import org.xmlpull.v1.XmlPullParserException

import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException

import rikka.shizuku.ShizukuBinderWrapper

class ActivateViewModel : ViewModel(), KoinComponent {
    private val context by inject<Context>()

    private val packageManager by lazy {
        context.packageManager
    }

    private val devicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    var state by mutableStateOf(ActivateViewState())
        private set

    fun collect() {
        // 进入激活页先同步一次真实所有者状态。
        // 若上次激活成功但状态未刷新（例如 Shizuku 路径漏了 sync），
        // 重新进页面即可看到"已激活"，不必再走一遍激活流程。
        DhizukuState.sync(context)
        collectData()
    }

    /**
     * 本应用自身是否已是设备所有者 / 资料所有者。用于把「already set」这类
     * 成功态异常从失败提示里摘掉，只作用于 Shizuku 自身激活路径。
     */
    private fun selfIsOwner(): Boolean = runCatching {
        DhizukuState.sync(context)
        DhizukuState.state.isDeviceOwner || DhizukuState.state.isProfileOwner ||
            devicePolicyManager.isDeviceOwnerApp(context.packageName) ||
            devicePolicyManager.isProfileOwnerApp(context.packageName)
    }.getOrDefault(false)

    private var collectDataJob: Job? = null

    enum class OwnerType {
        Device,
        Profile
    }

    var ownerType: OwnerType? = null
        private set

    fun collectData() {
        state = state.copy(loading = true)
        collectDataJob?.cancel()
        collectDataJob = viewModelScope.launch(Dispatchers.IO) {
            // http://aospxref.com/android-14.0.0_r2/xref/packages/apps/Settings/src/com/android/settings/applications/specialaccess/deviceadmin/DeviceAdminListPreferenceController.java#271

            val flags = PackageManager.GET_META_DATA

            val data = packageManager.queryBroadcastReceivers(
                Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
                flags
            ).mapNotNull {
                if (it == null) return@mapNotNull null
                try {
                    return@mapNotNull DeviceAdminInfo(context, it)
                } catch (ignored: XmlPullParserException) {
                } catch (ignored: IOException) {
                }
                return@mapNotNull null
            }.filter {
                if (!it.isVisible) return@filter false
                !it.activityInfo.applicationInfo.flags.has(ApplicationInfo.FLAG_SYSTEM)
            }.map {
                val component = it.component
                ActivateViewData(
                    admin = it,
                    // 「设备管理员已激活」也要能被预选：免删账号流程成功注册管理员后，
                    // 若仍要求同时是设备所有者，列表会显示成未启用，诱导用户重复激活。
                    enabled = devicePolicyManager.isAdminActive(component) ||
                            devicePolicyManager.isDeviceOwnerApp(component.packageName) ||
                            devicePolicyManager.isProfileOwnerApp(component.packageName)
                )
            }.sortedBy {
                it.admin.packageName
            }
            state = state.copy(data = data, loading = false)
        }
    }

    private var activateJob: Job? = null

    fun cancel() {
        activateJob?.cancel()
        state = state.copy(status = ActivateViewState.Status.Waiting)
    }

    fun activateAsDeviceOwner(
        mode: SettingsRoute.Activate.Mode,
        comp: ComponentName
    ) {
        ownerType = OwnerType.Device
        state = state.copy(status = ActivateViewState.Status.Running)
        activateJob?.cancel()
        activateJob = viewModelScope.launch(Dispatchers.IO) {
            val exceptionOrNull = kotlin.runCatching {
                when (mode) {
                    SettingsRoute.Activate.Mode.Dhizuku -> activateByDhizuku(comp)
                    SettingsRoute.Activate.Mode.Shizuku -> activateAsDeviceOwnerByShizuku(comp)
                }
            }.exceptionOrNull().let {
                if (it is InvocationTargetException) it.targetException
                else it
            }
            if (exceptionOrNull is CancellationException) return@launch
            // setDeviceOwner 抛 "device owner is already set" 时，本应用其实已经激活成功。
            // 免删账号 / Shizuku 路径常见「激活已完成但界面未刷新」，此时若仍把它映射成
            // 「已存在设备所有者或资料所有者」，用户会以为失败并反复重试。
            val resolvedError = if (
                mode == SettingsRoute.Activate.Mode.Shizuku &&
                exceptionOrNull != null && selfIsOwner()
            ) null else exceptionOrNull
            state = state.copy(status = ActivateViewState.Status.End(error = resolvedError))
        }
    }

    fun activateAsProfileOwner(
        mode: SettingsRoute.Activate.Mode,
        comp: ComponentName
    ) {
        ownerType = OwnerType.Profile
        state = state.copy(status = ActivateViewState.Status.Running)
        activateJob?.cancel()
        activateJob = viewModelScope.launch(Dispatchers.IO) {
            val exceptionOrNull = kotlin.runCatching {
                when (mode) {
                    SettingsRoute.Activate.Mode.Dhizuku -> activateByDhizuku(comp)
                    SettingsRoute.Activate.Mode.Shizuku -> activateAsProfileOwnerByShizuku(comp)
                }
            }.exceptionOrNull().let {
                if (it is InvocationTargetException) it.targetException
                else it
            }
            if (exceptionOrNull is CancellationException) return@launch
            // setDeviceOwner 抛 "device owner is already set" 时，本应用其实已经激活成功。
            // 免删账号 / Shizuku 路径常见「激活已完成但界面未刷新」，此时若仍把它映射成
            // 「已存在设备所有者或资料所有者」，用户会以为失败并反复重试。
            val resolvedError = if (
                mode == SettingsRoute.Activate.Mode.Shizuku &&
                exceptionOrNull != null && selfIsOwner()
            ) null else exceptionOrNull
            state = state.copy(status = ActivateViewState.Status.End(error = resolvedError))
        }
    }

    @SuppressLint("NewApi")
    private suspend fun activateByDhizuku(comp: ComponentName) =
        requireDhizukuPermissionGranted {
            requireBinderWrapperDevicePolicyManager(wrapper = {
                Dhizuku.binderWrapper(it)
            }) {
                it.transferOwnership(Dhizuku.getOwnerComponent(), comp, null)
            }
            DhizukuState.sync(context)
        }

    @SuppressLint("PrivateApi")
    private suspend fun activateAsDeviceOwnerByShizuku(who: ComponentName) =
        requireShizukuPermissionGranted() {
            // 已经是设备所有者就直接返回，避免 setDeviceOwner 抛
            // IllegalStateException("Trying to set the device owner, but device owner is already set")。
            // 这种情况在"激活已成功但界面未刷新"时很常见 —— 用户会以为失败并反复重试。
            if (isAlreadyDeviceOwner(who)) {
                DhizukuState.sync(context)
                return@requireShizukuPermissionGranted
            }
            // wait for the account cache be refreshed
            if (NeoDhizuku.on(NeoDhizuku.FixAccountWait)) {
                // 轮询等待账户缓存就绪：固定延时在慢设备上不够、快设备上白等
                waitAccountsReady()
            } else {
                delay(1500)
            }
            val ownerResult = runCatching {
                if (NeoDhizuku.on(NeoDhizuku.FixReflectionDpm)) {
                    requireWrappedDevicePolicyManager(wrapper = {
                        ShizukuBinderWrapper(it)
                    }) {
                        val userId = Os.getuid() / 100000
                        it.setActiveAdminV2(who, true, userId)
                        it.setDeviceOwnerV2(who, null, userId)
                    }
                } else {
                    requireBinderWrapperDevicePolicyManager(wrapper = {
                        ShizukuBinderWrapper(it)
                    }) {
                        val userId = Os.getuid() / 100000
                        it.setActiveAdmin(who, true, userId)
                        it.setDeviceOwner(who, null, userId)
                    }
                }
            }
            // 必须同步状态，否则激活确实成功、弹窗也提示成功，
            // 但首页仍显示未激活。
            DhizukuState.sync(context)
            // 多用户 / 已存在资料所有者时，setDeviceOwner 会抛
            // "device owner or profile owner is already set"，但公开 API 可能
            // 已经显示本应用是设备所有者（管理员也确已激活）。此时按成功处理，
            // 不要再拿「已有所有者」误导用户去删一个其实不存在的东西。
            if (ownerResult.isFailure && !isAlreadyDeviceOwner(who)) {
                throw ownerResult.exceptionOrNull()!!
            }
            NeoLog.i(NeoLog.ACTIVATE) {
                if (ownerResult.isSuccess) "setDeviceOwner 调用完成（两参）"
                else "setDeviceOwner 抛错但已是设备所有者，按成功处理"
            }
        }

    @SuppressLint("PrivateApi")
    private suspend fun activateAsProfileOwnerByShizuku(who: ComponentName) =
        requireShizukuPermissionGranted() {
            if (isAlreadyProfileOwner(who)) {
                DhizukuState.sync(context)
                return@requireShizukuPermissionGranted
            }
            // wait for the account cache be refreshed
            if (NeoDhizuku.on(NeoDhizuku.FixAccountWait)) {
                waitAccountsReady()
            } else {
                delay(1500)
            }
            if (NeoDhizuku.on(NeoDhizuku.FixReflectionDpm)) {
                requireWrappedDevicePolicyManager(wrapper = {
                    ShizukuBinderWrapper(it)
                }) {
                    val userId = Os.getuid() / 100000
                    it.setActiveAdminV2(who, true, userId)
                    it.setProfileOwnerV2(who, null, userId)
                }
            } else {
                requireBinderWrapperDevicePolicyManager(wrapper = {
                    ShizukuBinderWrapper(it)
                }) {
                    val userId = Os.getuid() / 100000
                    it.setActiveAdmin(who, true, userId)
                    it.setProfileOwner(who, null, userId)
                }
            }
            DhizukuState.sync(context)
        }

    /**
     * 是否已经是指定的设备所有者。
     * 用公开 API 判定，不依赖反射；失败一律返回 false（按"还不是"处理，
     * 走正常激活流程，最坏情况是系统回报 OWNER_ALREADY_SET）。
     */
    private fun isAlreadyDeviceOwner(who: ComponentName): Boolean = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        dpm != null && dpm.isDeviceOwnerApp(who.packageName)
    }.getOrDefault(false)

    private fun isAlreadyProfileOwner(who: ComponentName): Boolean = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        dpm != null && dpm.isProfileOwnerApp(who.packageName)
    }.getOrDefault(false)

    private fun DevicePolicyManager.setActiveAdmin(
        policyReceiver: ComponentName,
        refreshing: Boolean,
        userHandle: Int
    ) = this::class.java
        .getDeclaredMethod(
            "setActiveAdmin",
            ComponentName::class.java,
            Boolean::class.java,
            Int::class.java
        )
        .also { it.isAccessible = true }
        .invoke(this, policyReceiver, refreshing, userHandle)

    private fun DevicePolicyManager.setDeviceOwner(
        who: ComponentName,
        ownerName: String?,
        userId: Int
    ) {
        val clazz = this::class.java
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            clazz.getDeclaredMethod("setDeviceOwner", ComponentName::class.java, Int::class.java)
                .also { it.isAccessible = true }.invoke(this, who, userId)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clazz.getDeclaredMethod(
                "setDeviceOwner",
                ComponentName::class.java,
                String::class.java,
                Int::class.java
            ).also { it.isAccessible = true }.invoke(this, who, ownerName, userId)
            return
        }
        clazz.getDeclaredMethod("setDeviceOwner", ComponentName::class.java, String::class.java)
            .also { it.isAccessible = true }.invoke(this, who, ownerName)
    }

    private fun DevicePolicyManager.setProfileOwner(
        who: ComponentName,
        ownerName: String?,
        userId: Int
    ) {
        val clazz = this::class.java
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            clazz.getDeclaredMethod("setProfileOwner", ComponentName::class.java, Int::class.java)
                .also { it.isAccessible = true }.invoke(this, who, userId)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clazz.getDeclaredMethod(
                "setProfileOwner",
                ComponentName::class.java,
                String::class.java,
                Int::class.java
            ).also { it.isAccessible = true }.invoke(this, who, ownerName, userId)
            return
        }
        clazz.getDeclaredMethod("setProfileOwner", ComponentName::class.java, String::class.java)
            .also { it.isAccessible = true }.invoke(this, who, ownerName)
    }

    /**
     * 轮询等待账户缓存刷新，最多 8 秒；期间可通过 [onAccountWaitProgress] 上报进度。
     * 失败不抛异常（账户数量只是 setDeviceOwner 的软性前置条件），由调用方继续尝试。
     */
    private suspend fun waitAccountsReady() {
        val manager = runCatching {
            android.accounts.AccountManager.get(context)
        }.getOrNull() ?: run {
            kotlinx.coroutines.delay(1500)
            return
        }
        // 只等账户服务可读，不要求「账户清零」。旧写法在有账户时反而空转 8 秒，
        // 把 user_setup_complete=0 的临时窗口拖过期，随后 setDeviceOwner 更容易
        // 报「已有所有者」。账户本身由系统策略判断，不该由这里死等。
        repeat(8) {
            val count = runCatching { manager.accounts.size }.getOrDefault(-1)
            if (count >= 0) return
            kotlinx.coroutines.delay(250)
        }
    }

    // 不篡改系统单例 DevicePolicyManager 的 mService 字段，
    // 而是直接把包装后的 binder 转成 IDevicePolicyManager 调用。

    private fun requireWrappedDevicePolicyManager(
        wrapper: (IBinder) -> IBinder,
        action: (IDevicePolicyManager) -> Unit
    ) {
        val binder = android.os.ServiceManager.getService(Context.DEVICE_POLICY_SERVICE)
            ?: throw IllegalStateException("device_policy service is unavailable")
        val wrapped = wrapper.invoke(binder)
        action.invoke(IDevicePolicyManager.Stub.asInterface(wrapped))
    }

    private fun IDevicePolicyManager.setActiveAdminV2(
        policyReceiver: ComponentName,
        refreshing: Boolean,
        userHandle: Int
    ) = invokeDpmCompat(
        this,
        "setActiveAdmin",
        arrayOf(policyReceiver, refreshing, userHandle)
    ) {
        setActiveAdmin(policyReceiver, refreshing, userHandle)
    }

    private fun IDevicePolicyManager.setDeviceOwnerV2(
        who: ComponentName,
        ownerName: String?,
        userId: Int
    ) {
        val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf<Any?>(who, userId)
        } else {
            arrayOf<Any?>(who, ownerName, userId)
        }
        invokeDpmCompat(this, "setDeviceOwner", args) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setDeviceOwner(who, userId, false)
                return@invokeDpmCompat
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setDeviceOwner(who, ownerName, userId, false)
                return@invokeDpmCompat
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setDeviceOwner(who, ownerName, userId)
                return@invokeDpmCompat
            }
            setDeviceOwner(who.packageName, ownerName)
        }
    }

    private fun IDevicePolicyManager.setProfileOwnerV2(
        who: ComponentName,
        ownerName: String?,
        userId: Int
    ) {
        val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf<Any?>(who, userId)
        } else {
            arrayOf<Any?>(who, ownerName, userId)
        }
        invokeDpmCompat(this, "setProfileOwner", args) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setProfileOwner(who, userId)
                return@invokeDpmCompat
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setProfileOwner(who, ownerName, userId)
                return@invokeDpmCompat
            }
            setProfileOwner(who, ownerName)
        }
    }

    /**
     * 以兼容方式调用 IDevicePolicyManager 上的隐藏方法。
     * 直接按编译期签名调用在方法签名变更的系统上会抛 NoSuchMethodError ——
     * 编译期链接的是 SDK 里的 stub，运行期解析的是设备上的真实 framework，
     * 两者不一致时调用点在运行期才崩，且是 Error 而非 Exception。
     * 策略：先按编译期签名试；仅在"签名不匹配"（NoSuchMethodError /
     * NoSuchMethodException）时，扫描同名方法、按参数位置匹配，
     * 多出来的尾部参数按类型填默认值，逐个试直到成功。
     * 业务性异常（如"设备所有者已设置"）原样抛出，交给上层错误映射处理。
     */
    private fun invokeDpmCompat(
        target: IDevicePolicyManager,
        name: String,
        args: Array<Any?>,
        preferred: () -> Unit
    ) {
        val first = runCatching(preferred)
        if (first.isSuccess) return

        val err = first.exceptionOrNull()
        val signatureMismatch = err is NoSuchMethodError || err is NoSuchMethodException
        if (!signatureMismatch) {
            throw asRuntime(err)
        }

        val candidates = target.javaClass.methods
            .filter { it.name == name }
            .sortedBy { it.parameterTypes.size }
        NeoLog.w(NeoLog.ACTIVATE) {
            "$name 编译期签名不可用，扫描到 ${candidates.size} 个候选：" +
                candidates.joinToString(" | ") { m ->
                    m.name + "(" + m.parameterTypes.joinToString(",") { it.simpleName } + ")"
                }
        }

        for (m in candidates) {
            val types = m.parameterTypes
            if (types.size < args.size) continue
            val actual = arrayOfNulls<Any?>(types.size)
            var matched = true
            for (i in types.indices) {
                if (i < args.size) {
                    if (assignable(types[i], args[i])) actual[i] = args[i]
                    else {
                        matched = false
                        break
                    }
                } else {
                    val d = defaultValueOf(types[i])
                    if (d === NO_DEFAULT) {
                        matched = false
                        break
                    }
                    actual[i] = d
                }
            }
            if (!matched) continue

            val r = runCatching { m.invoke(target, *actual) }
            if (r.isSuccess) {
                NeoLog.i(NeoLog.ACTIVATE) {
                    "$name 已用运行期签名调用：" +
                        types.joinToString(",") { it.simpleName }
                }
                return
            }
            val t = r.exceptionOrNull()
            if (t is InvocationTargetException) {
                val cause = t.targetException
                if (cause is NoSuchMethodError || cause is NoSuchMethodException) continue
                throw asRuntime(cause ?: t)
            }
            if (t is NoSuchMethodError || t is NoSuchMethodException) continue
            throw asRuntime(t)
        }
        throw NoSuchMethodError(
            "No usable signature for $name (args=${args.size}) on API ${Build.VERSION.SDK_INT}"
        )
    }

    /** 形参类型与实参是否兼容（含基本类型装箱判定） */
    private fun assignable(type: Class<*>, value: Any?): Boolean {
        if (value == null) return !type.isPrimitive
        return when (type) {
            java.lang.Boolean.TYPE -> value is Boolean
            Integer.TYPE -> value is Int
            java.lang.Long.TYPE -> value is Long
            java.lang.Float.TYPE -> value is Float
            java.lang.Double.TYPE -> value is Double
            else -> type.isAssignableFrom(value.javaClass)
        }
    }

    /** 尾部多余形参的默认值；返回 NO_DEFAULT 表示该类型无法自动填充 */
    private fun defaultValueOf(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        String::class.java -> null
        else -> NO_DEFAULT
    }

    private val NO_DEFAULT = Any()

    private fun asRuntime(t: Throwable?): RuntimeException =
        t as? RuntimeException ?: RuntimeException(t)

    @SuppressLint("PrivateApi")
    private fun requireBinderWrapperDevicePolicyManager(
        wrapper: (IBinder) -> IBinder,
        action: (DevicePolicyManager) -> Unit
    ) {
        val manager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        var field: Field? = null
        var iInterface: IInterface? = null
        try {
            field = manager.javaClass.getDeclaredField("mService")
            field.isAccessible = true
            iInterface = field.get(manager) as IInterface
            val binder = wrapper.invoke(iInterface.asBinder()!!)
            field.set(manager, IDevicePolicyManager.Stub.asInterface(binder))
            action.invoke(manager)
        } finally {
            try {
                if (field != null && iInterface != null)
                    field.set(manager, iInterface)
            } catch (ignored: Throwable) {
            }
        }
    }
}