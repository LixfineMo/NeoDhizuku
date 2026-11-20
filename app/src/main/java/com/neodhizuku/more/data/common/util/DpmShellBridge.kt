package com.neodhizuku.more.data.common.util

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.IInterface

import com.neodhizuku.more.NeoLog

import kotlinx.coroutines.delay

import java.lang.reflect.Field

import rikka.shizuku.ShizukuBinderWrapper

/**
 * 以 Shizuku（shell / root）身份调用 DevicePolicyManager 的隐藏接口。
 *
 * 复用更新版 Dhizuku 的 `activateAsDeviceOwnerByShizuku` 通道：把
 * `DevicePolicyManager.mService` 临时替换为经 `ShizukuBinderWrapper` 包装的
 * `IDevicePolicyManager`，使 `forceUpdateUserSetupComplete` / `setActiveAdmin` /
 * `setDeviceOwner` 全部以 shell uid 发往 system_server，而不是应用自身 uid。
 *
 * 这是「免删账号」在部分 ROM 上能否成功的关键：DPMS 对 app uid 发起的隐藏接口
 * 调用会做额外的身份与权限校验（MANAGE_DEVICE_ADMINS / Role 等在部分厂商实现上
 * 并不随 `cmd role` 落到应用进程），shell uid 则不受这层限制。
 * 同时它不再依赖 `DEVICE_POLICY_MANAGEMENT` Role，绕开了
 * `cmd role set-bypassing-role-qualification` 在部分 ROM 上不可用或静默失败的问题。
 */
object DpmShellBridge {

    /** 执行结果，`detail` 逐行记录反射签名与调用结果，供日志框输出。 */
    data class Result(val ok: Boolean, val detail: String)

    private const val SETTING_USER_SETUP_COMPLETE = "user_setup_complete"

    /**
     * withShellDpm
     * (context: Context, action: (DevicePolicyManager) -> Unit)
     * 作用：把 DPM.mService 临时换成经 ShizukuBinderWrapper 包装的 IDevicePolicyManager 并执行 action，结束后无条件恢复原对象。
     * 备注：仅内部使用；action 抛异常时也会在 finally 中恢复
     */
    @SuppressLint("PrivateApi")
    fun withShellDpm(context: Context, action: (DevicePolicyManager) -> Unit) {
        val manager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        var field: Field? = null
        var original: IInterface? = null
        try {
            field = manager.javaClass.getDeclaredField("mService")
            field.isAccessible = true
            original = field.get(manager) as? IInterface
                ?: throw IllegalStateException("DevicePolicyManager.mService unavailable")
            val binder: IBinder = original.asBinder()
            field.set(manager, IDevicePolicyManager.Stub.asInterface(ShizukuBinderWrapper(binder)))
            action(manager)
        } finally {
            if (field != null && original != null) {
                runCatching { field.set(manager, original) }
            }
        }
    }

    /**
     * waitAccountCache
     * ()
     * 作用：等待账户缓存刷新，与更新版 Dhizuku 的 1.5 秒等待一致。
     * 备注：挂起函数；DPMS 判定「设备是否已有账户」读的是缓存
     */
    suspend fun waitAccountCache() {
        delay(1500L)
    }

    /**
     * readUserSetupComplete
     * (userId: Int)
     * 作用：经 Shizuku 读取指定用户的 user_setup_complete，读不到返回 null。
     * 备注：挂起函数；只读，不写
     */
    suspend fun readUserSetupComplete(userId: Int): String? {
        val out = ShizukuShell.exec(
            "settings --user $userId get secure $SETTING_USER_SETUP_COMPLETE 2>/dev/null"
        )
        val value = out.output.lineSequence()
            .map { it.trim() }
            .lastOrNull { it == "0" || it == "1" }
        NeoLog.i(NeoLog.ACTIVATE) { "readUserSetupComplete($userId) -> $value" }
        return value
    }

    /**
     * bypassUserSetupComplete
     * (userId: Int)
     * 作用：仅当指定用户的 user_setup_complete 为 1 时置 0，并刷新 DPM 内存态；返回是否真的改过。
     * 备注：挂起函数；返回 false 表示原本不是 1，调用方不应恢复，避免把既有状态写坏
     */
    suspend fun bypassUserSetupComplete(userId: Int, context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (readUserSetupComplete(userId) != "1") return false
        val put = ShizukuShell.exec(
            "settings --user $userId put secure $SETTING_USER_SETUP_COMPLETE 0"
        )
        if (!put.isSuccess) return false
        val now = readUserSetupComplete(userId)
        if (now != "0") return false
        refreshUserSetupComplete(context, userId)
        return true
    }

    /**
     * restoreUserSetupComplete
     * (userId: Int, context: Context)
     * 作用：把 user_setup_complete 写回 1 并刷新 DPM 内存态。
     * 备注：挂起函数；与 bypassUserSetupComplete 配对，只在确实改过时调用
     */
    suspend fun restoreUserSetupComplete(userId: Int, context: Context) {
        ShizukuShell.exec(
            "settings --user $userId put secure $SETTING_USER_SETUP_COMPLETE 1"
        )
        refreshUserSetupComplete(context, userId)
    }

    /**
     * refreshUserSetupComplete
     * (context: Context, userId: Int)
     * 作用：经 shell 身份调用 forceUpdateUserSetupComplete，把 Settings 的真实值搬进 DPMS 内存态。
     * 备注：挂起函数；int 签名不可用时回退无参签名，失败只记日志不阻断
     */
    private suspend fun refreshUserSetupComplete(context: Context, userId: Int) {
        val result = runCatching {
            withShellDpm(context) { dpm -> dpm.forceUpdateUserSetupComplete(userId) }
        }
        NeoLog.i(NeoLog.ACTIVATE) {
            "forceUpdateUserSetupComplete($userId): " +
                if (result.isSuccess) "OK" else rootCause(result.exceptionOrNull())
        }
    }

    /**
     * applyDeviceOwner
     * (context: Context, who: ComponentName, userId: Int)
     * 作用：经 shell 身份刷新向导闸、注册设备管理员并设置设备所有者，返回是否生效。
     * 备注：仅内部使用；结果以公开 API isDeviceOwnerApp 复查为准
     */
    fun applyDeviceOwner(context: Context, who: ComponentName, userId: Int): Result {
        val log = mutableListOf<String>()
        val call = runCatching {
            withShellDpm(context) { dpm ->
                val refreshed = runCatching { dpm.forceUpdateUserSetupComplete(userId) }
                log += "forceUpdateUserSetupComplete($userId): " +
                    if (refreshed.isSuccess) "OK" else rootCause(refreshed.exceptionOrNull())

                dpm.setActiveAdmin(who, true, userId)
                log += "setActiveAdmin($userId): OK"

                val accepted = dpm.setDeviceOwner(who, null, userId)
                log += "setDeviceOwner($userId): returned=$accepted"
            }
        }
        if (call.isFailure) {
            log += "shell binder 调用失败：${rootCause(call.exceptionOrNull())}"
        }
        val owner = runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isDeviceOwnerApp(who.packageName)
        }.getOrDefault(false)
        log += "isDeviceOwnerApp 复查：$owner"
        return Result(owner, log.joinToString("\n"))
    }

    /**
     * applyProfileOwner
     * (context: Context, who: ComponentName, userId: Int)
     * 作用：经 shell 身份注册设备管理员并设置资料所有者。
     * 备注：仅内部使用；供资料所有者路径复用同一通道
     */
    fun applyProfileOwner(context: Context, who: ComponentName, userId: Int): Result {
        val log = mutableListOf<String>()
        val call = runCatching {
            withShellDpm(context) { dpm ->
                dpm.setActiveAdmin(who, true, userId)
                log += "setActiveAdmin($userId): OK"
                val accepted = dpm.setProfileOwner(who, null, userId)
                log += "setProfileOwner($userId): returned=$accepted"
            }
        }
        if (call.isFailure) {
            log += "shell binder 调用失败：${rootCause(call.exceptionOrNull())}"
        }
        return Result(call.isSuccess, log.joinToString("\n"))
    }

    /**
     * DevicePolicyManager.forceUpdateUserSetupComplete
     * (userId: Int)
     * 作用：反射调用隐藏方法；优先 int 签名，失败回退无参签名。
     * 备注：仅内部使用；与更新版 Dhizuku 的双签名兜底一致
     */
    @SuppressLint("PrivateApi")
    private fun DevicePolicyManager.forceUpdateUserSetupComplete(userId: Int) {
        val clazz = this::class.java
        runCatching {
            clazz.getDeclaredMethod(
                "forceUpdateUserSetupComplete", Int::class.javaPrimitiveType!!
            ).also { it.isAccessible = true }.invoke(this, userId)
        }.onFailure {
            clazz.getDeclaredMethod("forceUpdateUserSetupComplete")
                .also { it.isAccessible = true }.invoke(this)
        }
    }

    /**
     * DevicePolicyManager.setActiveAdmin
     * (policyReceiver: ComponentName, refreshing: Boolean, userHandle: Int)
     * 作用：反射调用带 userId 的隐藏签名。
     * 备注：仅内部使用
     */
    @SuppressLint("PrivateApi")
    private fun DevicePolicyManager.setActiveAdmin(
        policyReceiver: ComponentName,
        refreshing: Boolean,
        userHandle: Int
    ) = this::class.java
        .getDeclaredMethod(
            "setActiveAdmin",
            ComponentName::class.java,
            Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        .also { it.isAccessible = true }
        .invoke(this, policyReceiver, refreshing, userHandle)

    /**
     * DevicePolicyManager.setDeviceOwner
     * (who: ComponentName, ownerName: String?, userId: Int)
     * 作用：按系统版本选择隐藏签名调用，返回系统是否接受。
     * 备注：仅内部使用；Android 14 起为两参，Android 7 起为三参
     */
    @SuppressLint("PrivateApi")
    private fun DevicePolicyManager.setDeviceOwner(
        who: ComponentName,
        ownerName: String?,
        userId: Int
    ): Boolean {
        val clazz = this::class.java
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            clazz.getDeclaredMethod("setDeviceOwner", ComponentName::class.java, Int::class.javaPrimitiveType!!)
                .also { it.isAccessible = true }.invoke(this, who, userId) as? Boolean ?: false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clazz.getDeclaredMethod(
                "setDeviceOwner",
                ComponentName::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            ).also { it.isAccessible = true }.invoke(this, who, ownerName, userId) as? Boolean ?: false
        } else {
            clazz.getDeclaredMethod("setDeviceOwner", String::class.java, String::class.java)
                .also { it.isAccessible = true }.invoke(this, who.packageName, ownerName) as? Boolean ?: false
        }
    }

    /**
     * DevicePolicyManager.setProfileOwner
     * (who: ComponentName, ownerName: String?, userId: Int)
     * 作用：按系统版本选择隐藏签名调用，返回系统是否接受。
     * 备注：仅内部使用
     */
    @SuppressLint("PrivateApi")
    private fun DevicePolicyManager.setProfileOwner(
        who: ComponentName,
        ownerName: String?,
        userId: Int
    ): Boolean {
        val clazz = this::class.java
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            clazz.getDeclaredMethod("setProfileOwner", ComponentName::class.java, Int::class.javaPrimitiveType!!)
                .also { it.isAccessible = true }.invoke(this, who, userId) as? Boolean ?: false
        } else {
            clazz.getDeclaredMethod(
                "setProfileOwner",
                ComponentName::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            ).also { it.isAccessible = true }.invoke(this, who, ownerName, userId) as? Boolean ?: false
        }
    }

    /**
     * rootCause
     * (t: Throwable?)
     * 作用：解开 InvocationTargetException，取出真正的原因与 message。
     * 备注：仅内部使用
     */
    private fun rootCause(t: Throwable?): String {
        var cur = t ?: return "(null)"
        var depth = 0
        while (cur is java.lang.reflect.InvocationTargetException && depth < 5) {
            cur = cur.targetException ?: break
            depth++
        }
        return "${cur::class.java.simpleName}: ${cur.message ?: "(无 message)"}"
    }
}
