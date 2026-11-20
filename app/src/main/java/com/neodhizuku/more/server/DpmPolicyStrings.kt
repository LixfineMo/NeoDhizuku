package com.neodhizuku.more.server

import android.content.Context
import com.neodhizuku.more.R

/**
 * 系统接口名称的编译期资源映射。
 * 原先 [DpmPolicyCatalog] 用 `resources.getIdentifier("dpm_$id", ...)`
 * 动态查名，而 release 构建开启了 `isShrinkResources = true`：
 * 这 179 个字符串没有任何静态引用，会被资源压缩器当成"未使用"全部删掉，
 * 界面于是回退显示 `lockNow` / `dpm_group_lock` 这类原始键。
 * R.string 直引保证编译期可达，压缩器会保留，
 * 并配合 `res/raw/keep.xml` 双保险。分组 key 本身就是 `dpm_group_*`，
 * 绝不能再拼一次 `dpm_` 前缀。
 */
object DpmPolicyStrings {

    /**
     * nameRes
     * (id: String)
     * 作用：接口名资源；未收录返回 null
     * 备注：—
     */
    fun nameRes(id: String): Int? = when (id) {
        "lockNow" -> R.string.dpm_lockNow
        "lockNowWithOptions" -> R.string.dpm_lockNowWithOptions
        "resetPassword" -> R.string.dpm_resetPassword
        "setPasswordQuality" -> R.string.dpm_setPasswordQuality
        "getPasswordQuality" -> R.string.dpm_getPasswordQuality
        "setPasswordMinimumLength" -> R.string.dpm_setPasswordMinimumLength
        "getPasswordMinimumLength" -> R.string.dpm_getPasswordMinimumLength
        "setPasswordMinimumUpperCase" -> R.string.dpm_setPasswordMinimumUpperCase
        "setPasswordMinimumLowerCase" -> R.string.dpm_setPasswordMinimumLowerCase
        "setPasswordMinimumNumeric" -> R.string.dpm_setPasswordMinimumNumeric
        "setPasswordMinimumSymbols" -> R.string.dpm_setPasswordMinimumSymbols
        "setPasswordMinimumNonLetter" -> R.string.dpm_setPasswordMinimumNonLetter
        "setPasswordExpirationTimeout" -> R.string.dpm_setPasswordExpirationTimeout
        "setMaximumTimeToLock" -> R.string.dpm_setMaximumTimeToLock
        "getMaximumTimeToLock" -> R.string.dpm_getMaximumTimeToLock
        "setMaximumFailedPasswordsForWipe" -> R.string.dpm_setMaximumFailedPasswordsForWipe
        "getMaximumFailedPasswordsForWipe" -> R.string.dpm_getMaximumFailedPasswordsForWipe
        "setKeyguardDisabledFeatures" -> R.string.dpm_setKeyguardDisabledFeatures
        "getKeyguardDisabledFeatures" -> R.string.dpm_getKeyguardDisabledFeatures
        "setRequiredStrongAuthTimeout" -> R.string.dpm_setRequiredStrongAuthTimeout
        "requestKeyguardDismiss" -> R.string.dpm_requestKeyguardDismiss
        "setApplicationHidden" -> R.string.dpm_setApplicationHidden
        "isApplicationHidden" -> R.string.dpm_isApplicationHidden
        "setApplicationRestrictions" -> R.string.dpm_setApplicationRestrictions
        "getApplicationRestrictions" -> R.string.dpm_getApplicationRestrictions
        "setApplicationSuspended" -> R.string.dpm_setApplicationSuspended
        "isApplicationSuspended" -> R.string.dpm_isApplicationSuspended
        "setUninstallBlocked" -> R.string.dpm_setUninstallBlocked
        "isUninstallBlocked" -> R.string.dpm_isUninstallBlocked
        "enableSystemApp" -> R.string.dpm_enableSystemApp
        "installSystemUpdate" -> R.string.dpm_installSystemUpdate
        "clearApplicationUserData" -> R.string.dpm_clearApplicationUserData
        "installExistingPackage" -> R.string.dpm_installExistingPackage
        "setKeepUninstalledPackages" -> R.string.dpm_setKeepUninstalledPackages
        "getKeepUninstalledPackages" -> R.string.dpm_getKeepUninstalledPackages
        "setPermissionGrantState" -> R.string.dpm_setPermissionGrantState
        "getPermissionGrantState" -> R.string.dpm_getPermissionGrantState
        "setPermissionPolicy" -> R.string.dpm_setPermissionPolicy
        "getPermissionPolicy" -> R.string.dpm_getPermissionPolicy
        "setPermittedInputMethods" -> R.string.dpm_setPermittedInputMethods
        "getPermittedInputMethods" -> R.string.dpm_getPermittedInputMethods
        "setAccountManagementDisabled" -> R.string.dpm_setAccountManagementDisabled
        "getAccountTypesWithManagementDisabled" -> R.string.dpm_getAccountTypesWithManagementDisabled
        "setCameraDisabled" -> R.string.dpm_setCameraDisabled
        "getCameraDisabled" -> R.string.dpm_getCameraDisabled
        "setScreenCaptureDisabled" -> R.string.dpm_setScreenCaptureDisabled
        "getScreenCaptureDisabled" -> R.string.dpm_getScreenCaptureDisabled
        "setStatusBarDisabled" -> R.string.dpm_setStatusBarDisabled
        "setAutoTimeRequired" -> R.string.dpm_setAutoTimeRequired
        "addUserRestriction" -> R.string.dpm_addUserRestriction
        "clearUserRestriction" -> R.string.dpm_clearUserRestriction
        "setUserRestriction" -> R.string.dpm_setUserRestriction
        "getUserRestrictions" -> R.string.dpm_getUserRestrictions
        "setNetworkLoggingEnabled" -> R.string.dpm_setNetworkLoggingEnabled
        "retrieveNetworkLogs" -> R.string.dpm_retrieveNetworkLogs
        "setStorageEncryption" -> R.string.dpm_setStorageEncryption
        "getStorageEncryption" -> R.string.dpm_getStorageEncryption
        "getStorageEncryptionStatus" -> R.string.dpm_getStorageEncryptionStatus
        "installCaCert" -> R.string.dpm_installCaCert
        "uninstallCaCert" -> R.string.dpm_uninstallCaCert
        "getInstalledCaCerts" -> R.string.dpm_getInstalledCaCerts
        "createAndManageUser" -> R.string.dpm_createAndManageUser
        "removeUser" -> R.string.dpm_removeUser
        "switchUser" -> R.string.dpm_switchUser
        "setProfileName" -> R.string.dpm_setProfileName
        "setProfileEnabled" -> R.string.dpm_setProfileEnabled
        "setProfileOwner" -> R.string.dpm_setProfileOwner
        "setDeviceOwner" -> R.string.dpm_setDeviceOwner
        "transferOwnership" -> R.string.dpm_transferOwnership
        "clearDeviceOwnerApp" -> R.string.dpm_clearDeviceOwnerApp
        "setActiveAdmin" -> R.string.dpm_setActiveAdmin
        "removeActiveAdmin" -> R.string.dpm_removeActiveAdmin
        "reboot" -> R.string.dpm_reboot
        "wipeData" -> R.string.dpm_wipeData
        "wipeDataWithReason" -> R.string.dpm_wipeDataWithReason
        "setGlobalSetting" -> R.string.dpm_setGlobalSetting
        "setSecureSetting" -> R.string.dpm_setSecureSetting
        "setSystemUpdatePolicy" -> R.string.dpm_setSystemUpdatePolicy
        "getSystemUpdatePolicy" -> R.string.dpm_getSystemUpdatePolicy
        "setOrganizationName" -> R.string.dpm_setOrganizationName
        "getDeviceOwnerComponent" -> R.string.dpm_getDeviceOwnerComponent
        "isDeviceOwnerApp" -> R.string.dpm_isDeviceOwnerApp
        "isProfileOwnerApp" -> R.string.dpm_isProfileOwnerApp
        "isAdminActive" -> R.string.dpm_isAdminActive
        "getActiveAdmins" -> R.string.dpm_getActiveAdmins
        "setDeviceOwnerLockScreenInfo" -> R.string.dpm_setDeviceOwnerLockScreenInfo
        else -> null
    }

    /**
     * descRes
     * (id: String)
     * 作用：接口说明资源；未收录返回 null
     * 备注：—
     */
    fun descRes(id: String): Int? = when (id) {
        "lockNow" -> R.string.dpm_lockNow_d
        "lockNowWithOptions" -> R.string.dpm_lockNowWithOptions_d
        "resetPassword" -> R.string.dpm_resetPassword_d
        "setPasswordQuality" -> R.string.dpm_setPasswordQuality_d
        "getPasswordQuality" -> R.string.dpm_getPasswordQuality_d
        "setPasswordMinimumLength" -> R.string.dpm_setPasswordMinimumLength_d
        "getPasswordMinimumLength" -> R.string.dpm_getPasswordMinimumLength_d
        "setPasswordMinimumUpperCase" -> R.string.dpm_setPasswordMinimumUpperCase_d
        "setPasswordMinimumLowerCase" -> R.string.dpm_setPasswordMinimumLowerCase_d
        "setPasswordMinimumNumeric" -> R.string.dpm_setPasswordMinimumNumeric_d
        "setPasswordMinimumSymbols" -> R.string.dpm_setPasswordMinimumSymbols_d
        "setPasswordMinimumNonLetter" -> R.string.dpm_setPasswordMinimumNonLetter_d
        "setPasswordExpirationTimeout" -> R.string.dpm_setPasswordExpirationTimeout_d
        "setMaximumTimeToLock" -> R.string.dpm_setMaximumTimeToLock_d
        "getMaximumTimeToLock" -> R.string.dpm_getMaximumTimeToLock_d
        "setMaximumFailedPasswordsForWipe" -> R.string.dpm_setMaximumFailedPasswordsForWipe_d
        "getMaximumFailedPasswordsForWipe" -> R.string.dpm_getMaximumFailedPasswordsForWipe_d
        "setKeyguardDisabledFeatures" -> R.string.dpm_setKeyguardDisabledFeatures_d
        "getKeyguardDisabledFeatures" -> R.string.dpm_getKeyguardDisabledFeatures_d
        "setRequiredStrongAuthTimeout" -> R.string.dpm_setRequiredStrongAuthTimeout_d
        "requestKeyguardDismiss" -> R.string.dpm_requestKeyguardDismiss_d
        "setApplicationHidden" -> R.string.dpm_setApplicationHidden_d
        "isApplicationHidden" -> R.string.dpm_isApplicationHidden_d
        "setApplicationRestrictions" -> R.string.dpm_setApplicationRestrictions_d
        "getApplicationRestrictions" -> R.string.dpm_getApplicationRestrictions_d
        "setApplicationSuspended" -> R.string.dpm_setApplicationSuspended_d
        "isApplicationSuspended" -> R.string.dpm_isApplicationSuspended_d
        "setUninstallBlocked" -> R.string.dpm_setUninstallBlocked_d
        "isUninstallBlocked" -> R.string.dpm_isUninstallBlocked_d
        "enableSystemApp" -> R.string.dpm_enableSystemApp_d
        "installSystemUpdate" -> R.string.dpm_installSystemUpdate_d
        "clearApplicationUserData" -> R.string.dpm_clearApplicationUserData_d
        "installExistingPackage" -> R.string.dpm_installExistingPackage_d
        "setKeepUninstalledPackages" -> R.string.dpm_setKeepUninstalledPackages_d
        "getKeepUninstalledPackages" -> R.string.dpm_getKeepUninstalledPackages_d
        "setPermissionGrantState" -> R.string.dpm_setPermissionGrantState_d
        "getPermissionGrantState" -> R.string.dpm_getPermissionGrantState_d
        "setPermissionPolicy" -> R.string.dpm_setPermissionPolicy_d
        "getPermissionPolicy" -> R.string.dpm_getPermissionPolicy_d
        "setPermittedInputMethods" -> R.string.dpm_setPermittedInputMethods_d
        "getPermittedInputMethods" -> R.string.dpm_getPermittedInputMethods_d
        "setAccountManagementDisabled" -> R.string.dpm_setAccountManagementDisabled_d
        "getAccountTypesWithManagementDisabled" -> R.string.dpm_getAccountTypesWithManagementDisabled_d
        "setCameraDisabled" -> R.string.dpm_setCameraDisabled_d
        "getCameraDisabled" -> R.string.dpm_getCameraDisabled_d
        "setScreenCaptureDisabled" -> R.string.dpm_setScreenCaptureDisabled_d
        "getScreenCaptureDisabled" -> R.string.dpm_getScreenCaptureDisabled_d
        "setStatusBarDisabled" -> R.string.dpm_setStatusBarDisabled_d
        "setAutoTimeRequired" -> R.string.dpm_setAutoTimeRequired_d
        "addUserRestriction" -> R.string.dpm_addUserRestriction_d
        "clearUserRestriction" -> R.string.dpm_clearUserRestriction_d
        "setUserRestriction" -> R.string.dpm_setUserRestriction_d
        "getUserRestrictions" -> R.string.dpm_getUserRestrictions_d
        "setNetworkLoggingEnabled" -> R.string.dpm_setNetworkLoggingEnabled_d
        "retrieveNetworkLogs" -> R.string.dpm_retrieveNetworkLogs_d
        "setStorageEncryption" -> R.string.dpm_setStorageEncryption_d
        "getStorageEncryption" -> R.string.dpm_getStorageEncryption_d
        "getStorageEncryptionStatus" -> R.string.dpm_getStorageEncryptionStatus_d
        "installCaCert" -> R.string.dpm_installCaCert_d
        "uninstallCaCert" -> R.string.dpm_uninstallCaCert_d
        "getInstalledCaCerts" -> R.string.dpm_getInstalledCaCerts_d
        "createAndManageUser" -> R.string.dpm_createAndManageUser_d
        "removeUser" -> R.string.dpm_removeUser_d
        "switchUser" -> R.string.dpm_switchUser_d
        "setProfileName" -> R.string.dpm_setProfileName_d
        "setProfileEnabled" -> R.string.dpm_setProfileEnabled_d
        "setProfileOwner" -> R.string.dpm_setProfileOwner_d
        "setDeviceOwner" -> R.string.dpm_setDeviceOwner_d
        "transferOwnership" -> R.string.dpm_transferOwnership_d
        "clearDeviceOwnerApp" -> R.string.dpm_clearDeviceOwnerApp_d
        "setActiveAdmin" -> R.string.dpm_setActiveAdmin_d
        "removeActiveAdmin" -> R.string.dpm_removeActiveAdmin_d
        "reboot" -> R.string.dpm_reboot_d
        "wipeData" -> R.string.dpm_wipeData_d
        "wipeDataWithReason" -> R.string.dpm_wipeDataWithReason_d
        "setGlobalSetting" -> R.string.dpm_setGlobalSetting_d
        "setSecureSetting" -> R.string.dpm_setSecureSetting_d
        "setSystemUpdatePolicy" -> R.string.dpm_setSystemUpdatePolicy_d
        "getSystemUpdatePolicy" -> R.string.dpm_getSystemUpdatePolicy_d
        "setOrganizationName" -> R.string.dpm_setOrganizationName_d
        "getDeviceOwnerComponent" -> R.string.dpm_getDeviceOwnerComponent_d
        "isDeviceOwnerApp" -> R.string.dpm_isDeviceOwnerApp_d
        "isProfileOwnerApp" -> R.string.dpm_isProfileOwnerApp_d
        "isAdminActive" -> R.string.dpm_isAdminActive_d
        "getActiveAdmins" -> R.string.dpm_getActiveAdmins_d
        "setDeviceOwnerLockScreenInfo" -> R.string.dpm_setDeviceOwnerLockScreenInfo_d
        else -> null
    }

    /**
     * groupRes
     * (key: String)
     * 作用：分组名资源；key 已带 dpm_group_ 前缀
     * 备注：—
     */
    fun groupRes(key: String): Int? = when (key) {
        "dpm_group_lock" -> R.string.dpm_group_lock
        "dpm_group_app" -> R.string.dpm_group_app
        "dpm_group_perm" -> R.string.dpm_group_perm
        "dpm_group_restrict" -> R.string.dpm_group_restrict
        "dpm_group_storage" -> R.string.dpm_group_storage
        "dpm_group_user" -> R.string.dpm_group_user
        "dpm_group_system" -> R.string.dpm_group_system
        else -> null
    }

    /**
     * text
     * (context: Context, resId: Int?)
     * 作用：执行 text
     * 备注：仅内部使用
     */
    private fun text(context: Context, resId: Int?): String? =
        resId?.let { runCatching { context.getString(it) }.getOrNull() }

    /**
     * name
     * (context: Context, id: String)
     * 作用：人话名称；取不到时回退方法名，绝不会显示资源键
     * 备注：—
     */
    fun name(context: Context, id: String): String =
        text(context, nameRes(id)) ?: id

    /**
     * desc
     * (context: Context, id: String)
     * 作用：一句话功能说明；取不到时返回空串
     * 备注：—
     */
    fun desc(context: Context, id: String): String =
        text(context, descRes(id)) ?: ""

    /**
     * group
     * (context: Context, key: String)
     * 作用：分组标题
     * 备注：—
     */
    fun group(context: Context, key: String): String =
        text(context, groupRes(key)) ?: key

    /**
     * readable
     * (context: Context, id: String?)
     * 作用：日志用：人话在前、方法名括注在后，便于和 AIDL 对照
     * 备注：—
     */
    fun readable(context: Context, id: String?): String {
        if (id.isNullOrBlank()) return "未知接口"
        val n = text(context, nameRes(id)) ?: return id
        return if (n == id) id else "$n（$id）"
    }
}
