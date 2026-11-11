package com.neodhizuku.more.server

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.getPackageInfoForUid
import com.neodhizuku.more.data.common.util.getSignaturesForUid
import com.neodhizuku.more.data.common.util.signature
import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DaemonService : Service(), KoinComponent {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val repo by inject<AppRepo>()
    private val settingsRepo by inject<SettingsRepo>()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
            if (uid < 0) return

            if (NeoDhizuku.on(NeoDhizuku.FixPackageReplaceBroadcast)) {
                // ACTION_PACKAGE_REMOVED 在"覆盖安装（更新）"时同样会发出，
                // 此时 EXTRA_REPLACING=true / EXTRA_DATA_REMOVED=false。
                // 不区分的话目标 App 每次升级都可能丢掉授权记录。
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                    val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    val dataRemoved = intent.getBooleanExtra(Intent.EXTRA_DATA_REMOVED, false)
                    if (replacing || !dataRemoved) return
                }
            }

            scope.launch {
                val entity = repo.findByUID(uid) ?: return@launch
                if (NeoDhizuku.on(NeoDhizuku.FixBlacklistPurge)) {
                    // 只在"包真的没了 / 签名变了"时才清理记录。
                    // 不能因为 blocked 或 allowApi=false 而删除——那是用户的策略，必须保留。
                    if (!isStale(entity)) {
                        PermissionCache.put(entity)
                        return@launch
                    }
                } else {
                    if (verify(entity)) return@launch
                }
                repo.delete(entity)
                PermissionCache.remove(uid)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001

        private const val NOTIFICATION_CHANNEL_ID = "daemon_service"
    }

    override fun onCreate() {
        super.onCreate()
        registerPackageReceiver()

        // 通过 startForegroundService() 启动后，必须在超时前调用 startForeground()，
        // 否则系统抛出 RemoteServiceException 并直接杀掉进程。
        // 这里先无条件满足前台服务契约，之后再由策略决定是否继续保持前台。
        // 判据用 isActive 而不是 isOwner：仅拥有设备管理员时 isOwner 为 false，
        // 若据此不调用 startForeground()，服务会被系统判定为违规并杀进程。
        foreground(DhizukuState.state.isActive)

        // DaemonService 是 START_STICKY，stopService() 后系统会立刻重建。
        // 用户主动停止时记一个标记，重建后在此再停一次，否则"关闭"永远无效。
        // 直接启动（LOCKED_BOOT_COMPLETED）下存储可能尚不可用，故做兜底。
        val stoppedByUser = runCatching { settingsRepo.isServiceStoppedByUser }
            .getOrDefault(false)
        if (stoppedByUser) {
            NeoLog.i(NeoLog.DAEMON) { "onCreate: 用户已停止，立即再次停止" }
            ServiceStatus.markStopped()
            foreground(false)
            stopSelf()
            return
        }

        // 每次服务启动都重置计时基准 —— 被杀重启后运行时间归零重算
        if (NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard)) {
            ServiceStatus.markStarted()
            refreshAuthorizedCount()
        }
        NeoLog.i(NeoLog.DAEMON) { "onCreate: isOwner=${DhizukuState.state.isOwner}" }
        dumpState()
    }

    /**
     * 服务启动时把影响"第三方能否识别"的关键状态一次性打全。
     * 这些值分散在 DhizukuState / SettingsRepo / 授权表三处，
     * 分别查看很容易漏，集中输出便于一眼定位。
     */
    private fun dumpState() {
        val state = DhizukuState.state
        NeoLog.i(NeoLog.DAEMON) {
            "状态: deviceOwner=${state.isDeviceOwner} profileOwner=${state.isProfileOwner} " +
                "enabled=${settingsRepo.isDhizukuEnabled} " +
                "whitelist=${settingsRepo.isWhitelistMode} " +
                "allowThirdPartyOwnerTransfer=${settingsRepo.isAllowOwnerTransfer}"
        }
        scope.launch {
            val all = runCatching { repo.all() }.getOrNull() ?: return@launch
            val granted = all.count { it.allowApi && !it.blocked }
            val blocked = all.count { it.blocked }
            NeoLog.i(NeoLog.DAEMON) {
                "授权表: 共 ${all.size} 条，已授权 $granted，已拉黑 $blocked"
            }
        }
        // 刷新一次内存缓存，否则新授权的应用要等下次服务重启才生效
        scope.launch {
            runCatching { PermissionCache.refresh() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stoppedByUser = runCatching { settingsRepo.isServiceStoppedByUser }
            .getOrDefault(false)
        if (stoppedByUser) {
            NeoLog.i(NeoLog.DAEMON) { "onStartCommand: 用户已停止，立即再次停止" }
            ServiceStatus.markStopped()
            foreground(false)
            stopSelf()
            return START_NOT_STICKY
        }
        foreground(DhizukuState.state.isActive)
        return START_STICKY
    }

    /**
     * 用户从多任务划掉本应用的任务时回调。
     * 守护服务和界面在同一个进程，划卡会连带杀掉服务；MIUI 等定制系统的划卡
     * 接近强制停止，START_STICKY 的重建会被压制或明显延迟，期间第三方（如雹）
     * 拿不到 binder。这里抢在进程被杀前补一次启动，缩短空窗。
     * 未激活时不能走 startForegroundService：onCreate 里 foreground(false)
     * 会不调 startForeground()，反而触发 RemoteServiceException 直接杀进程。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        val stoppedByUser = runCatching { settingsRepo.isServiceStoppedByUser }
            .getOrDefault(false)
        if (stoppedByUser) {
            NeoLog.i(NeoLog.DAEMON) { "onTaskRemoved: 用户已停止，不拉起" }
            return
        }
        if (!DhizukuState.state.isActive) {
            NeoLog.i(NeoLog.DAEMON) { "onTaskRemoved: 未激活，不拉起" }
            return
        }

        NeoLog.i(NeoLog.DAEMON) { "onTaskRemoved: 划卡后重新拉起服务" }
        val intent = Intent(this, DaemonService::class.java)
        // 后台启动前台服务在 Android 12+ 受限，失败就退回普通 startService，
        // 由 onCreate 里的 foreground() 决定是否继续走前台。
        runCatching { startForegroundService(intent) }
            .onFailure { e ->
                NeoLog.w(NeoLog.DAEMON) { "onTaskRemoved: startForegroundService 失败，退回 startService" }
                runCatching { startService(intent) }
                    .onFailure { NeoLog.w(NeoLog.DAEMON) { "onTaskRemoved: 拉起失败" } }
            }
    }

    override fun onDestroy() {
        foreground(false)
        unregisterReceiver(packageReceiver)
        if (NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard)) {
            ServiceStatus.markStopped()
        }
        NeoLog.i(NeoLog.DAEMON) { "onDestroy" }
        super.onDestroy()
    }

    private fun registerPackageReceiver() {
        if (NeoDhizuku.on(NeoDhizuku.FixPermissionMemoryCache)) {
            scope.launch { PermissionCache.refresh() }
        }

        scope.launch {
            repo.all().forEach { entity ->
                val shouldDelete = if (NeoDhizuku.on(NeoDhizuku.FixBlacklistPurge)) {
                    isStale(entity)
                } else {
                    !verify(entity)
                }
                if (shouldDelete) {
                    repo.delete(entity)
                    PermissionCache.remove(entity.uid)
                } else {
                    PermissionCache.put(entity)
                }
            }
        }

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        filter.addDataScheme("package")
        registerReceiver(packageReceiver, filter)
    }

    /**
     * 只有"包已卸载"或"签名已变化"才算失效。
     * 与 [verify] 的差别是：blocked / allowApi=false 不作为可删除条件。
     */
    private fun isStale(entity: AppEntity): Boolean {
        val packageInfo = packageManager.getPackageInfoForUid(entity.uid) ?: return true
        if (packageInfo.packageName.isNullOrBlank()) return true
        if (NeoDhizuku.on(NeoDhizuku.FixSignatureSetCheck)) {
            val signatures = getSignaturesForUid(packageManager, entity.uid)
            if (signatures.isEmpty()) return true
            return !signatures.contains(entity.signature)
        }
        return entity.signature != packageInfo.signature
    }

    private fun verify(entity: AppEntity): Boolean {
        val packageInfo = packageManager.getPackageInfoForUid(entity.uid) ?: return false

        if (!entity.allowApi || entity.blocked) return false

        if (entity.signature != packageInfo.signature) return false

        return true
    }

    /** 刷新首页「授权应用」计数 */
    private fun refreshAuthorizedCount() {
        scope.launch {
            runCatching {
                val list = repo.all()
                ServiceStatus.updateAuthorizedCount(
                    list.count { it.allowApi && !it.blocked }
                )
            }
        }
    }

    private fun foreground(enabled: Boolean) {
        if (!enabled) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        val manager = NotificationManagerCompat.from(this)


        val channel = NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW
        ).setName(getString(R.string.daemon_service_notification_title))
            .setDescription(getString(R.string.daemon_service_notification_text)).build()
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.daemon_service_notification_title))
            .setContentText(getString(R.string.daemon_service_notification_text))
            .setSmallIcon(R.drawable.round_hourglass_empty_black_24)
            // setNotificationSilent() 已弃用，官方建议改由通知渠道配置。
            // 这里的渠道是 IMPORTANCE_LOW（本就无提示音与震动），
            // 且下面显式设了 sound=null，行为等价，保留调用以免旧版本行为变化。
            .apply {
                @Suppress("DEPRECATION")
                setNotificationSilent()
            }
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
        if (NeoDhizuku.on(NeoDhizuku.FixFgsType)) {
            // 前台服务类型按实际用途声明（守护 / 维持可用性）
            androidx.core.app.ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
