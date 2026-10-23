package com.neodhizuku.more.server

import android.os.IBinder
import android.util.Log

import com.neodhizuku.more.NeoLog


/**
 * 设备所有者转移拦截。
 *
 * 背景：第三方应用拿到授权后，可以用 `Dhizuku.binderWrapper(dpmBinder)`
 * 把任意 binder（包括 DevicePolicyManager）包装起来，通过 `remote_transact`
 * 以本应用（设备所有者）的身份调用其任意方法，整个过程不需要用户再确认。
 * 若该应用本身不提供授权入口，一旦把所有者转移出去就再也回不来。
 *
 * 这里在服务端判定目标 binder 是否为 `IDevicePolicyManager`，
 * 并识别其中"会改变所有者归属"的事务码，按开关决定拒绝还是放行。
 *
 * 事务码不硬编码：`IDevicePolicyManager` 有数百个方法，
 * 事务码随 Android 版本变化。这里在运行时反射系统真实的 Stub 类读取，
 * 读不到（版本差异 / 方法改名）就跳过该方法，不会崩。
 */
object OwnerTransferGuard {

    private const val TAG = "Dhizuku"

    /** 目标 binder 的接口描述符 */
    private const val DPM_DESCRIPTOR = "android.app.admin.IDevicePolicyManager"

    private const val STUB_CLASS = "android.app.admin.IDevicePolicyManager\$Stub"

    /**
     * 会改变设备所有者归属的方法名。
     *
     * 只拦截这几类，DPM 的查询类操作（getDeviceOwnerComponent 等）不受影响，
     * 第三方应用读取设备策略信息的能力保持完整。
     */
    private val TRANSFER_FIELDS = arrayOf(
        "TRANSACTION_transferOwnership",
        "TRANSACTION_setDeviceOwner",
        "TRANSACTION_setDeviceOwner2",
        "TRANSACTION_setDeviceOwner3",
        "TRANSACTION_setProfileOwner",
        "TRANSACTION_setProfileOwner2",
        "TRANSACTION_setProfileOwner3"
    )

    /** 反射结果缓存：事务码 -> 是否属于转移类 */
    @Volatile
    private var cachedCodes: Set<Int>? = null

    /**
     * 判定本次 remote_transact 是否属于设备所有者转移。
     *
     * @param binder 客户端要操作的目标 binder
     * @param code   要调用的目标方法事务码
     */
    fun isOwnerTransfer(binder: IBinder?, code: Int): Boolean {
        val descriptor = runCatching { binder?.interfaceDescriptor }.getOrNull()
        if (descriptor != DPM_DESCRIPTOR) return false
        return code in resolveCodes()
    }

    /**
     * 反射读取系统 Stub 类中的事务码常量。
     *
     * 用 HiddenApiBypass 解除非 SDK 接口限制后再 Class.forName，
     * 保证能拿到系统真实实现（而不是本项目 hidden-api 的存根）。
     */
    private fun resolveCodes(): Set<Int> {
        cachedCodes?.let { return it }
        val result = LinkedHashSet<Int>()
        runCatching {
            HiddenApiExempt.once()
            val clazz = Class.forName(STUB_CLASS)
            TRANSFER_FIELDS.forEach { name ->
                val field = runCatching { clazz.getDeclaredField(name) }.getOrNull()
                    ?: return@forEach
                field.isAccessible = true
                if (field.type == Int::class.javaPrimitiveType ||
                    field.type == Integer.TYPE
                ) {
                    result.add(field.getInt(null))
                }
            }
        }.onFailure {
            Log.w(TAG, "[OwnerTransfer] 反射事务码失败：${it.javaClass.simpleName}")
        }
        NeoLog.i(NeoLog.SERVER) {
            "[OwnerTransfer] 解析到转移类事务码 ${result.size} 个"
        }
        cachedCodes = result
        return result
    }
}
