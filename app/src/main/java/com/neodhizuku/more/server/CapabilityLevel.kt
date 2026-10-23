package com.neodhizuku.more.server

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * 能力等级。
 *
 * 本应用有三种"被系统授予"的身份，能力依次递增：
 *
 * - **设备管理员**（Device Admin）：系统设置里点一下即可授予，全版本可用，能力最少
 * - **资料管理员**（Profile Owner）：受管资料内拥有完整控制权
 * - **设备所有者**（Device Owner）：整机最高控制权
 *
 * [DhizukuState.State.isOwner] 只涵盖后两者，设备管理员另由
 * [DhizukuState.State.isActive] 判断。只拥有设备管理员的用户也能开启服务
 * 并使用其中一部分能力 ——
 * 对无法成为所有者的设备（如 Android 11 且不愿清账号）尤其重要。
 */
enum class CapabilityLevel(
    /** 数值越大能力越强，用于比较 */
    val rank: Int
) {
    /** 未激活 */
    NONE(0),

    /** 设备管理员 */
    DEVICE_ADMIN(1),

    /** 资料管理员 */
    PROFILE_OWNER(2),

    /** 设备所有者 */
    DEVICE_OWNER(3);

    /** 是否为"完全激活"（拥有所有者权限） */
    val isOwner: Boolean
        get() = this == PROFILE_OWNER || this == DEVICE_OWNER

    /** 是否至少具备某种身份、可以开启服务 */
    val isActive: Boolean
        get() = this != NONE

    companion object {

        /**
         * 设备管理员可用的设备策略方法白名单。
         *
         * Device Admin 被激活后，DPM 允许调用的方法是有限且明确的，
         * 因此用白名单而不是黑名单。**未收录的方法一律按所有者要求处理** ——
         * 宁可误拒，也不能因为漏判造成权限提升。
         *
         * 注意：这里列出的是"管理员身份下系统本就允许"的方法。
         * [DevicePolicyManager.wipeData] 虽然管理员也能执行，但因不可逆
         * 且后果严重，故意不列入 —— 始终按所有者要求处理，并走二次确认。
         */
        private val ADMIN_ALLOWED_METHODS: Set<String> = setOf(
            // 锁屏
            "lockNow",
            "lockNowWithOptions",
            "setMaximumTimeToLock",
            "getMaximumTimeToLock",
            // 密码策略
            "resetPassword",
            "setPasswordQuality",
            "getPasswordQuality",
            "setPasswordMinimumLength",
            "getPasswordMinimumLength",
            "setPasswordMinimumUpperCase",
            "setPasswordMinimumLowerCase",
            "setPasswordMinimumNumeric",
            "setPasswordMinimumSymbols",
            "setPasswordMinimumNonLetter",
            "setPasswordExpirationTimeout",
            "getPasswordExpirationTimeout",
            "setPasswordHistoryLength",
            "getPasswordHistoryLength",
            "setMaximumFailedPasswordsForWipe",
            "getMaximumFailedPasswordsForWipe",
            // 锁屏功能限制
            "setKeyguardDisabledFeatures",
            "getKeyguardDisabledFeatures",
            // 设备功能开关
            "setCameraDisabled",
            "getCameraDisabled",
            // 存储加密
            "setStorageEncryption",
            "getStorageEncryption",
            "setRequireStorageCardEncryption",
            "getRequireStorageCardEncryption",
            // 查询
            "isAdminActive",
            "getActiveAdmins"
        )

        /** 判定当前等级 */
        fun resolve(
            isDeviceOwner: Boolean,
            isProfileOwner: Boolean,
            isDeviceAdmin: Boolean
        ): CapabilityLevel = when {
            isDeviceOwner -> DEVICE_OWNER
            isProfileOwner -> PROFILE_OWNER
            isDeviceAdmin -> DEVICE_ADMIN
            else -> NONE
        }

        /** 查询本应用是否为设备管理员 */
        fun isAdminActive(context: Context, admin: ComponentName): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? DevicePolicyManager ?: return false
            return runCatching { dpm.isAdminActive(admin) }.getOrDefault(false)
        }

        /**
         * 某方法所需的最低等级。
         *
         * @param method 方法名；为 null（无法识别）时按最严格要求处理
         */
        fun requiredLevel(method: String?): CapabilityLevel {
            if (method == null) return DEVICE_OWNER
            return if (method in ADMIN_ALLOWED_METHODS) DEVICE_ADMIN else DEVICE_OWNER
        }

        /** 当前等级是否足以执行该方法 */
        fun canExecute(level: CapabilityLevel, method: String?): Boolean =
            level.rank >= requiredLevel(method).rank
    }
}
