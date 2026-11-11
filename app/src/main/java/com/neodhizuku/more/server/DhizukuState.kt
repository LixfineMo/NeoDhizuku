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
import com.neodhizuku.more.NeoLog
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
    /**
     * 当前身份状态。
     * [isOwner] 的语义保持原样（拥有所有者权限），大量既有逻辑依赖它：
     * 服务启停、授权校验、所有者转移拦截都以它为"完全激活"的判据，
     * 把设备管理员算进去会连带影响这三条链路，因此另设 [isActive]。
     */
    data class State(
        val isDeviceOwner: Boolean = false,
        val isProfileOwner: Boolean = false,
        val isDeviceAdmin: Boolean = false
    ) {
        /** 是否拥有所有者权限（设备所有者或资料管理员），语义不变 */
        val isOwner = isDeviceOwner || isProfileOwner

        /** 是否至少具备某种身份、可以开启服务 */
        val isActive = isOwner || isDeviceAdmin

        /** 能力等级 */
        val level: CapabilityLevel
            get() = CapabilityLevel.resolve(isDeviceOwner, isProfileOwner, isDeviceAdmin)
    }

    var state by mutableStateOf(State())
        private set

    /** 调试用：直接覆盖内存中的身份状态；下一次 sync 会按系统真实值重算。 */
    fun setDebugState(deviceOwner: Boolean, profileOwner: Boolean, deviceAdmin: Boolean) {
        state = State(deviceOwner, profileOwner, deviceAdmin)
    }

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
        val isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(admin.packageName) }
            .getOrDefault(false)
        val isProfileOwner = runCatching { dpm.isProfileOwnerApp(admin.packageName) }
            .getOrDefault(false)
        val isDeviceAdmin = runCatching { dpm.isAdminActive(admin) }
            .getOrDefault(false)

        val next = State(
            isDeviceOwner = isDeviceOwner,
            isProfileOwner = isProfileOwner,
            isDeviceAdmin = isDeviceAdmin
        )
        val previous = state
        state = next

        NeoLog.i(NeoLog.DAEMON) {
            "状态: deviceOwner=$isDeviceOwner profileOwner=$isProfileOwner " +
                "deviceAdmin=$isDeviceAdmin level=${next.level} " +
                "enabled=${runCatching { settingsRepo.isDhizukuEnabled }.getOrDefault(true)} " +
                "whitelist=${runCatching { settingsRepo.isWhitelistMode }.getOrDefault(false)}"
        }
        if (previous.level != next.level) {
            NeoLog.i(NeoLog.DAEMON) {
                "能力等级变化: ${previous.level} -> ${next.level}"
            }
        }

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

    /**
     * 无条件尝试清除本应用残留的设备身份，逐项返回成败。
     * 背景：通过 `transferOwnership` 把设备所有者转给别的应用后，部分机型
     * 不会顺带卸掉本应用的设备管理员身份，于是出现"设置里显示未激活、
     * 卸载却报设备管理器阻止"的错位状态（DELETE_FAILED_DEVICE_POLICY_MANAGER）。
     * 这里**不依赖状态检测**：转移之后 [State.isDeviceAdmin] 往往已经回到
     * false（`isAdminActive` 读不到），但系统卸载检查读的是另一份记录，
     * 按状态判断会得出"没有可清理的东西"而什么都不做。因此直接调用三个
     * 清理接口，让用户在卡死状态下有自救手段。
     * 不做自动清理：激活流程是 `setActiveAdmin` 之后才 `setDeviceOwner`，
     * 中间存在"仅管理员、还不是所有者"的窗口，此时自动卸掉管理员会把
     * 正在进行的激活打断。所以只由用户手动触发。
     */
    @Suppress("DEPRECATION")
    fun forceCleanup(context: Context): Map<String, Boolean> {
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val out = LinkedHashMap<String, Boolean>()
        out["clearDeviceOwnerApp"] = runCatching {
            dpm.clearDeviceOwnerApp(admin.packageName)
            true
        }.getOrDefault(false)
        out["clearProfileOwner"] = runCatching {
            dpm.clearProfileOwner(admin)
            true
        }.getOrDefault(false)
        out["removeActiveAdmin"] = runCatching {
            dpm.removeActiveAdmin(admin)
            true
        }.getOrDefault(false)
        NeoLog.i(NeoLog.DAEMON) { "强制解除残留: $out" }
        return out
    }

    /** 供界面展示的三项原始状态，用于判断身份是否残留 */
    fun snapshot(context: Context): State {
        syncQuiet(context)
        return state
    }

    private fun syncQuiet(context: Context) = runCatching { sync(context) }

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

    /**
     * 同步拉起守护服务，供 BroadcastReceiver 在 onReceive 内调用。
     * [sync] 的副作用跑在 IO 协程上，开机这种冷启动场景下协程可能来不及执行，
     * 进程就已经被回收。接收器必须有一个同步入口把服务启动请求发出去。
     */
    fun startDaemonNow(context: Context) {
        autoDaemonService(context.applicationContext, state)
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
            // 设备管理员也算"已激活"：服务开启后第三方可以拿到 binder，
            // 能执行哪些方法由能力等级在服务侧判定（见 CapabilityLevel）
            if (current.isActive) {
                runCatching { context.startForegroundService(intent) }
                    .onFailure { Log.w("Dhizuku", "startForegroundService failed", it) }
            } else {
                runCatching { context.stopService(intent) }
            }
        } else {
            if (current.isActive) {
                context.startService(intent)
                context.startForegroundService(intent)
            } else context.stopService(intent)
        }
    }
}
