package com.neodhizuku.more.data.common.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.server.DhizukuState

/**
 * 权限委派（delegated scopes）的读写与还原。
 * 委派是一条独立的能力通道：所有者把**自己能做的有限几类操作**授权给某个普通应用，
 * 那个应用就能以所有者身份调用对应的 DPM 方法。它与本应用的授权开关是两回事 ——
 * 取消本应用里的授权不会自动撤销已经委派出去的能力。
 */
object DelegationManager {

    /** 一个可委派的作用域，含引入版本与说明用的字符串键后缀。 */
    data class ScopeDef(
        val id: String,
        val sinceApi: Int,
        val labelKey: String
    )

    /**
     * 系统定义的全部作用域。
     * 按引入版本排序；低于当前系统版本的项在界面上应置灰或隐藏，
     * 因为传下去会被 DPMS 直接拒绝。
     */
    val ALL_SCOPES: List<ScopeDef> = listOf(
        ScopeDef(DevicePolicyManager.DELEGATION_APP_RESTRICTIONS, Build.VERSION_CODES.O, "app_restrictions"),
        ScopeDef(DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL, Build.VERSION_CODES.O, "block_uninstall"),
        ScopeDef(DevicePolicyManager.DELEGATION_PERMISSION_GRANT, Build.VERSION_CODES.O, "permission_granting"),
        ScopeDef(DevicePolicyManager.DELEGATION_PACKAGE_ACCESS, Build.VERSION_CODES.O, "package_access"),
        ScopeDef(DevicePolicyManager.DELEGATION_ENABLE_SYSTEM_APP, Build.VERSION_CODES.O, "enable_system_apps"),
        ScopeDef(DevicePolicyManager.DELEGATION_CERT_INSTALL, Build.VERSION_CODES.P, "cert_install"),
        ScopeDef(DevicePolicyManager.DELEGATION_NETWORK_LOGGING, Build.VERSION_CODES.P, "network_logging"),
        ScopeDef(DevicePolicyManager.DELEGATION_SECURITY_LOGGING, Build.VERSION_CODES.P, "security_logging"),
        ScopeDef(DevicePolicyManager.DELEGATION_CERT_SELECTION, Build.VERSION_CODES.Q, "cert_selection"),
        ScopeDef(
            DevicePolicyManager.DELEGATION_INSTALL_EXISTING_PACKAGE,
            Build.VERSION_CODES.Q,
            "install_existing_package"
        )
    )

    /** 当前系统支持的子集。 */
    fun supportedScopes(): List<ScopeDef> =
        ALL_SCOPES.filter { Build.VERSION.SDK_INT >= it.sinceApi }

    /**
     * 读取某个包当前被委派的作用域；失败返回空列表。
     * 扫描时会逐包调用，失败用 d 级记录 —— 一次扫描可能碰到上百个包，
     * 每个都按 w 级落库会把运行日志刷满。真正需要排查时开完整日志即可。
     */
    fun readScopes(dpm: DevicePolicyManager, admin: ComponentName, pkg: String): List<String> =
        runCatching { dpm.getDelegatedScopes(admin, pkg) }
            .onFailure {
                NeoLog.d(NeoLog.PERM) { "委派：读取 $pkg 的委派范围失败：${it.message}" }
            }
            .getOrDefault(emptyList())

    /** 写入某个包的委派作用域。 */
    fun writeScopes(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        pkg: String,
        scopes: Collection<String>
    ): Boolean = runCatching {
        dpm.setDelegatedScopes(admin, pkg, scopes.toList())
        true
    }.onFailure {
        NeoLog.w(NeoLog.PERM) { "委派：写入 $pkg -> ${scopes.joinToString()} 失败：${it.message}" }
    }.getOrDefault(false)

    /**
     * 清除某个包的委派。
     * @param restore 是否先把**已经生效的状态**还原 —— 委派记录只是一张"允许调用"的表，
     *                隐藏、挂起、权限固定这些效果存在包状态与运行时权限状态里，
     *                不还原的话即使清了委派，设置里仍是坏的。
     */
    fun clear(context: Context, dpm: DevicePolicyManager, admin: ComponentName, pkg: String, restore: Boolean) {
        if (restore) {
            val scopes = readScopes(dpm, admin, pkg)
            if (scopes.isNotEmpty()) {
                NeoLog.i(NeoLog.PERM) { "委派清理：$pkg 当前委派 ${scopes.joinToString()}" }
            }
            if (scopes.contains(DevicePolicyManager.DELEGATION_PACKAGE_ACCESS)) {
                restoreHidden(dpm, admin, pkg)
                restoreSuspended(dpm, admin, pkg)
            }
            if (scopes.contains(DevicePolicyManager.DELEGATION_PERMISSION_GRANT)) {
                restorePermissionPolicy(context, dpm, admin, pkg)
            }
        }
        writeScopes(dpm, admin, pkg, emptyList())
    }

    private fun restoreHidden(dpm: DevicePolicyManager, admin: ComponentName, pkg: String) {
        runCatching {
            if (dpm.isApplicationHidden(admin, pkg)) {
                dpm.setApplicationHidden(admin, pkg, false)
                NeoLog.i(NeoLog.PERM) { "委派清理：已还原 $pkg 的隐藏状态" }
            }
        }.onFailure {
            NeoLog.w(NeoLog.PERM) { "委派清理：还原 $pkg 隐藏状态失败：${it.message}" }
        }
    }

    private fun restoreSuspended(dpm: DevicePolicyManager, admin: ComponentName, pkg: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && dpm.isPackageSuspended(admin, pkg)) {
                dpm.setPackagesSuspended(admin, arrayOf(pkg), false)
                NeoLog.i(NeoLog.PERM) { "委派清理：已还原 $pkg 的挂起状态" }
            }
        }.onFailure {
            NeoLog.w(NeoLog.PERM) { "委派清理：还原 $pkg 挂起状态失败：${it.message}" }
        }
    }

    /**
     * 把被策略固定的权限改回 DEFAULT。
     * 两点必须说明清楚，否则会造成误解：
     *  1. 只对**已被策略固定**的权限生效 —— 没有策略时 `getPermissionGrantState`
     *     返回 DEFAULT，用户自己授予的权限不受影响。
     *  2. 改回 DEFAULT **不会撤销**已授予的权限，只把控制权还给用户。
     *     程序化撤销做不到，这是 Android 的设计。
     */
    private fun restorePermissionPolicy(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName,
        pkg: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val requested = runCatching {
            context.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions
        }.getOrNull()
        if (requested.isNullOrEmpty()) return

        var restored = 0
        requested.forEach { permission ->
            runCatching {
                val state = dpm.getPermissionGrantState(admin, pkg, permission)
                if (state != DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT) {
                    dpm.setPermissionGrantState(
                        admin,
                        pkg,
                        permission,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                    )
                    restored++
                }
            }.onFailure {
                NeoLog.d(NeoLog.PERM) { "委派清理：$pkg/$permission 还原失败：${it.message}" }
            }
        }
        if (restored > 0) {
            NeoLog.i(NeoLog.PERM) { "委派清理：已还原 $pkg 的 $restored 项权限策略" }
        }
    }

    /**
     * 取消授权 / 删除记录时清掉某个 uid 名下所有包的委派。
     * @param uid 目标 uid；应用已卸载时反查不到包名，此时无从清理。
     */
    fun clearForUid(
        context: Context,
        uid: Int,
        restore: Boolean
    ) {
        if (!DhizukuState.state.isOwner) return
        val admin = DhizukuState.admin
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        val packages = context.packageManager.getPackagesForUid(uid)
        if (packages.isNullOrEmpty()) {
            NeoLog.w(NeoLog.PERM) {
                "委派清理：uid=$uid 取不到包名（应用可能已卸载），跳过"
            }
            return
        }
        packages.forEach { pkg ->
            clear(context, dpm, admin, pkg, restore)
        }
    }
}
