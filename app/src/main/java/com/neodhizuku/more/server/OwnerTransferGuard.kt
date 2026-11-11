package com.neodhizuku.more.server

import android.os.IBinder
import android.util.Log

import com.neodhizuku.more.NeoLog

/**
 * 识别「会改变设备所有者归属」的调用。
 * 第三方应用可以把 DevicePolicyManager 包装后经共享服务以本应用身份调用，
 * 这类请求是否放行由服务页的「允许第三方应用主动转移设备所有者」开关决定；
 * 本应用自身发起的转移不受该开关影响。
 * 事务码不硬编码：系统 Stub 里的方法码随 Android 版本变化，这里在运行时反射读取，
 * 取不到常量时按方法名比对，仍无法判定时保守判为转移类。
 */
object OwnerTransferGuard {

    private const val TAG = "Dhizuku"

    /** 目标 binder 的接口描述符 */
    private const val DPM_DESCRIPTOR = "android.app.admin.IDevicePolicyManager"

    private const val STUB_CLASS = "android.app.admin.IDevicePolicyManager\$Stub"

    /** 会改变设备所有者归属的方法名对应的常量字段。 */
    private val TRANSFER_FIELDS = arrayOf(
        "TRANSACTION_transferOwnership",
        "TRANSACTION_setDeviceOwner",
        "TRANSACTION_setDeviceOwner2",
        "TRANSACTION_setDeviceOwner3",
        "TRANSACTION_setProfileOwner",
        "TRANSACTION_setProfileOwner2",
        "TRANSACTION_setProfileOwner3"
    )

    /**
     * 与 [TRANSFER_FIELDS] 对应的方法名集合。
     * 只按事务码判定有 fail-open 风险：厂商改名或新增重载时常量会全部取不到。
     * 保留一份名字副本，反射能还原出方法名时按名比对依然成立。
     */
    private val TRANSFER_METHODS: Set<String> = setOf(
        "transferOwnership",
        "setDeviceOwner",
        "setDeviceOwner2",
        "setDeviceOwner3",
        "setProfileOwner",
        "setProfileOwner2",
        "setProfileOwner3"
    )

    /** 反射结果缓存：事务码 -> 是否属于转移类 */
    @Volatile
    private var cachedCodes: Set<Int>? = null

    /**
     * 判定本次 remote_transact 是否属于设备所有者转移。
     * 判定顺序：事务码命中 → 方法名命中 → 无法还原方法名时按 fail-closed 拦截。
     * 第 3 步会让守卫在反射失效期间拦下所有设备策略调用，这是刻意的取舍：
     * 所有者一旦被转移出去就不可逆，宁可临时不可用，也不能静默放行。
     */
    fun isOwnerTransfer(binder: IBinder?, code: Int): Boolean {
        val descriptor = runCatching { binder?.interfaceDescriptor }.getOrNull()
        if (descriptor != DPM_DESCRIPTOR) return false

        if (code in resolveCodes()) return true

        val name = DpmApiCatalog.methodName(code)
        if (name != null) return name in TRANSFER_METHODS

        NeoLog.w(NeoLog.SERVER) {
            "[OwnerTransfer] 无法解析事务码 $code，按 fail-closed 拦截"
        }
        return true
    }

    /**
     * 反射读取系统 Stub 类中的事务码常量。
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
