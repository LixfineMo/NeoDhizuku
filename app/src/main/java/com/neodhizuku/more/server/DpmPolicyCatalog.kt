package com.neodhizuku.more.server

import android.content.Context

/**
 * 可被第三方调用的系统接口（设备策略接口）清单。
 * 与 [ApiPolicyCatalog] 的分工：
 * - [ApiPolicyCatalog] 是 Dhizuku 自身的 17 个 AIDL 事务（读版本、执行命令…），
 *   这些一律放行、不暴露给用户配置，只由授权与拒绝服务管；
 * - 这里是被远程调用的系统接口本身 —— 也就是第三方拿到授权后**实际能做的事**
 *   （禁用摄像头、锁屏、隐藏应用、清除数据、擦除设备…）。
 * 清单来源：设备策略接口的 AIDL 定义，方法名与运行时反射出的
 * [DpmApiCatalog] 事务码对照表**同名**，因此服务端在
 * `onRemoteTransact` 里解析出方法名后可直接命中这里的配置项。
 * 名称与说明走 strings（key 为 `dpm_<方法名>` / `dpm_<方法名>_d`），
 * 由 [DpmPolicyStrings] 静态直引；取不到时回退方法名，绝不会把资源键显示给用户。
 */
object DpmPolicyCatalog {

    /** 分组，界面按此分节 */
    enum class Group(val key: String) {
        LOCK("dpm_group_lock"),
        APP("dpm_group_app"),
        PERM("dpm_group_perm"),
        RESTRICT("dpm_group_restrict"),
        STORAGE("dpm_group_storage"),
        USER("dpm_group_user"),
        SYSTEM("dpm_group_system")
    }

    /**
     * 一个可单独配置的系统接口。
     * @param id 设备策略接口的方法名，同时是配置键
     * @param group 分组
     * @param dangerous 高危：默认不勾选
     */
    data class DpmDef(
        val id: String,
        val group: Group,
        val dangerous: Boolean = false
    ) {
        /** 未配置时的默认取值：高危一律不允许 */
        val defaultAllowed: Boolean get() = !dangerous

        val nameKey: String get() = "dpm_$id"
        val descKey: String get() = "dpm_${id}_d"
    }

    val ALL: List<DpmDef> = listOf(
        // —— 锁屏与密码 ——
        DpmDef("lockNow", Group.LOCK),
        DpmDef("lockNowWithOptions", Group.LOCK),
        DpmDef("resetPassword", Group.LOCK, dangerous = true),
        DpmDef("setPasswordQuality", Group.LOCK),
        DpmDef("getPasswordQuality", Group.LOCK),
        DpmDef("setPasswordMinimumLength", Group.LOCK),
        DpmDef("getPasswordMinimumLength", Group.LOCK),
        DpmDef("setPasswordMinimumUpperCase", Group.LOCK),
        DpmDef("setPasswordMinimumLowerCase", Group.LOCK),
        DpmDef("setPasswordMinimumNumeric", Group.LOCK),
        DpmDef("setPasswordMinimumSymbols", Group.LOCK),
        DpmDef("setPasswordMinimumNonLetter", Group.LOCK),
        DpmDef("setPasswordExpirationTimeout", Group.LOCK),
        DpmDef("setMaximumTimeToLock", Group.LOCK),
        DpmDef("getMaximumTimeToLock", Group.LOCK),
        DpmDef("setMaximumFailedPasswordsForWipe", Group.LOCK, dangerous = true),
        DpmDef("getMaximumFailedPasswordsForWipe", Group.LOCK),
        DpmDef("setKeyguardDisabledFeatures", Group.LOCK),
        DpmDef("getKeyguardDisabledFeatures", Group.LOCK),
        DpmDef("setRequiredStrongAuthTimeout", Group.LOCK),
        DpmDef("requestKeyguardDismiss", Group.LOCK),

        // —— 应用管理 ——
        DpmDef("setApplicationHidden", Group.APP),
        DpmDef("isApplicationHidden", Group.APP),
        DpmDef("setApplicationRestrictions", Group.APP),
        DpmDef("getApplicationRestrictions", Group.APP),
        DpmDef("setApplicationSuspended", Group.APP),
        DpmDef("isApplicationSuspended", Group.APP),
        DpmDef("setUninstallBlocked", Group.APP),
        DpmDef("isUninstallBlocked", Group.APP),
        DpmDef("enableSystemApp", Group.APP),
        DpmDef("installSystemUpdate", Group.APP, dangerous = true),
        DpmDef("clearApplicationUserData", Group.APP, dangerous = true),
        DpmDef("installExistingPackage", Group.APP),
        DpmDef("setKeepUninstalledPackages", Group.APP),
        DpmDef("getKeepUninstalledPackages", Group.APP),

        // —— 权限与账户 ——
        DpmDef("setPermissionGrantState", Group.PERM),
        DpmDef("getPermissionGrantState", Group.PERM),
        DpmDef("setPermissionPolicy", Group.PERM),
        DpmDef("getPermissionPolicy", Group.PERM),
        DpmDef("setPermittedInputMethods", Group.PERM),
        DpmDef("getPermittedInputMethods", Group.PERM),
        DpmDef("setAccountManagementDisabled", Group.PERM),
        DpmDef("getAccountTypesWithManagementDisabled", Group.PERM),

        // —— 设备限制 ——
        DpmDef("setCameraDisabled", Group.RESTRICT),
        DpmDef("getCameraDisabled", Group.RESTRICT),
        DpmDef("setScreenCaptureDisabled", Group.RESTRICT),
        DpmDef("getScreenCaptureDisabled", Group.RESTRICT),
        DpmDef("setStatusBarDisabled", Group.RESTRICT),
        DpmDef("setAutoTimeRequired", Group.RESTRICT),
        DpmDef("addUserRestriction", Group.RESTRICT),
        DpmDef("clearUserRestriction", Group.RESTRICT),
        DpmDef("setUserRestriction", Group.RESTRICT),
        DpmDef("getUserRestrictions", Group.RESTRICT),
        DpmDef("setNetworkLoggingEnabled", Group.RESTRICT),
        DpmDef("retrieveNetworkLogs", Group.RESTRICT),

        // —— 存储与加密 ——
        DpmDef("setStorageEncryption", Group.STORAGE, dangerous = true),
        DpmDef("getStorageEncryption", Group.STORAGE),
        DpmDef("getStorageEncryptionStatus", Group.STORAGE),
        DpmDef("installCaCert", Group.STORAGE, dangerous = true),
        DpmDef("uninstallCaCert", Group.STORAGE, dangerous = true),
        DpmDef("getInstalledCaCerts", Group.STORAGE),

        // —— 用户与资料 ——
        DpmDef("createAndManageUser", Group.USER, dangerous = true),
        DpmDef("removeUser", Group.USER, dangerous = true),
        DpmDef("switchUser", Group.USER, dangerous = true),
        DpmDef("setProfileName", Group.USER),
        DpmDef("setProfileEnabled", Group.USER),
        DpmDef("setProfileOwner", Group.USER, dangerous = true),
        DpmDef("setDeviceOwner", Group.USER, dangerous = true),
        DpmDef("transferOwnership", Group.USER, dangerous = true),
        DpmDef("clearDeviceOwnerApp", Group.USER, dangerous = true),
        DpmDef("setActiveAdmin", Group.USER, dangerous = true),
        DpmDef("removeActiveAdmin", Group.USER, dangerous = true),

        // —— 系统操作 ——
        DpmDef("reboot", Group.SYSTEM, dangerous = true),
        DpmDef("wipeData", Group.SYSTEM, dangerous = true),
        DpmDef("wipeDataWithReason", Group.SYSTEM, dangerous = true),
        DpmDef("setGlobalSetting", Group.SYSTEM),
        DpmDef("setSecureSetting", Group.SYSTEM),
        DpmDef("setSystemUpdatePolicy", Group.SYSTEM),
        DpmDef("getSystemUpdatePolicy", Group.SYSTEM),
        DpmDef("setOrganizationName", Group.SYSTEM),
        DpmDef("getDeviceOwnerComponent", Group.SYSTEM),
        DpmDef("isDeviceOwnerApp", Group.SYSTEM),
        DpmDef("isProfileOwnerApp", Group.SYSTEM),
        DpmDef("isAdminActive", Group.SYSTEM),
        DpmDef("getActiveAdmins", Group.SYSTEM),
        DpmDef("setDeviceOwnerLockScreenInfo", Group.SYSTEM)
    )

    private val BY_ID: Map<String, DpmDef> = ALL.associateBy { it.id }

    fun byId(id: String): DpmDef? = BY_ID[id]

    /** 未收录的方法一律放行：不以"认不出"为由拦截，避免误伤正常调用 */
    fun defaultAllowed(id: String): Boolean = BY_ID[id]?.defaultAllowed ?: true

    /** 按分组取，界面分节用；组内高危排在后 */
    fun byGroup(group: Group): List<DpmDef> =
        ALL.filter { it.group == group }.sortedBy { if (it.dangerous) 1 else 0 }

    /** 取本地化名称；取不到回退方法名（资源静态直引，release 不会被裁剪） */
    fun label(context: Context, id: String): String = DpmPolicyStrings.name(context, id)

    /** 取本地化说明；取不到回退空串 */
    fun desc(context: Context, id: String): String = DpmPolicyStrings.desc(context, id)

    /** 分组标题。group.key 本身已是 dpm_group_*，不能再加前缀 */
    fun groupLabel(context: Context, group: Group): String =
        DpmPolicyStrings.group(context, group.key)
}
