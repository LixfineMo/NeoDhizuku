package com.neodhizuku.more.server

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.data.common.util.has
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean

data object DhizukuState : KoinComponent {

    private val settingsRepo: SettingsRepo by inject()
    data class State(val isDeviceOwner: Boolean = false, val isProfileOwner: Boolean = false) {
        val isOwner = isDeviceOwner || isProfileOwner
    }

    var state by mutableStateOf(State())
        private set

    // 只读，避免被任意改写；admin 名称保持不变以兼容旧写法
    val admin = ComponentName(BuildConfig.APPLICATION_ID, DhizukuDAReceiver::class.java.name)

    /**
     * 副作用（IPC、权限授予、启停服务）统一放到这个串行 IO scope，
     * 避免在 Application.onCreate / BroadcastReceiver.onReceive 的主线程上执行。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val syncing = AtomicBoolean(false)

    fun sync(context: Context) {
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val next = State(
            isDeviceOwner = dpm.isDeviceOwnerApp(admin.packageName),
            isProfileOwner = dpm.isProfileOwnerApp(admin.packageName)
        )
        state = next

        if (NeoDhizuku.on(NeoDhizuku.FixSyncOffMainThread)) {
            // 只把"计算状态"留在调用线程，副作用全部异步串行执行
            val appContext = context.applicationContext
            if (!syncing.compareAndSet(false, true)) {
                // 已有同步在跑，仍然要刷新一次，排队执行
            }
            scope.launch {
                try {
                    onReceive(appContext, dpm, admin, next)
                } catch (t: Throwable) {
                    Log.w("Dhizuku", "sync side effects failed", t)
                } finally {
                    syncing.set(false)
                }
            }
        } else {
            onReceive(context, dpm, admin, next)
        }
    }

    private fun onReceive(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName,
        current: State = state
    ) {
        if (current.isOwner) onEnabled(context, dpm, admin)
        else onDisabled(context, dpm, admin)

        autoDaemonService(context, current)
    }

    private fun onEnabled(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        grantPermissions(context, dpm, admin)
    }

    private fun onDisabled(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
    }

    private fun grantPermissions(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        val permissions = getAllRequestedPermissions(context, admin).filter {
            it ?: return@filter false
            val permission = getPermissionInfo(context, it) ?: return@filter false
            return@filter if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                permission.protectionFlags.has(PermissionInfo.PROTECTION_DANGEROUS)
            } else {
                @Suppress("DEPRECATION")
                (permission.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
            }
        }

        permissions.forEach {
            dpm.setPermissionGrantState(
                admin,
                admin.packageName,
                it,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        }
    }

    private fun getAllRequestedPermissions(context: Context, admin: ComponentName) = try {
        val packageInfo: PackageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    admin.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    admin.packageName,
                    PackageManager.GET_PERMISSIONS
                )
            }
        packageInfo.requestedPermissions ?: emptyArray()
    } catch (_: Exception) {
        emptyArray()
    }

    private fun getPermissionInfo(context: Context, permission: String) = try {
        context.packageManager.getPermissionInfo(permission, 0)
    } catch (e: Exception) {
        null
    }

    private fun autoDaemonService(context: Context, current: State = state) {
        val intent = Intent(context, DaemonService::class.java)

        // 用户主动停止后，任何一次 sync 都不应把服务再拉起来，
        // 否则「停止服务」在下次启动 App / 收到广播时就被悄悄撤销了。
        val stoppedByUser = runCatching { settingsRepo.isServiceStoppedByUser }
            .getOrDefault(false)
        if (stoppedByUser) {
            runCatching { context.stopService(intent) }
            Log.i("Dhizuku", "autoDaemonService: 用户已停止，保持停止状态")
            return
        }

        if (NeoDhizuku.on(NeoDhizuku.FixDuplicateStartService)) {
            // 只启动一次，否则 onCreate 里的清理协程会并发跑两遍
            if (current.isOwner) {
                runCatching { context.startForegroundService(intent) }
                    .onFailure { Log.w("Dhizuku", "startForegroundService failed", it) }
            } else {
                runCatching { context.stopService(intent) }
            }
        } else {
            if (current.isOwner) {
                context.startService(intent)
                context.startForegroundService(intent)
            } else context.stopService(intent)
        }
    }
}
