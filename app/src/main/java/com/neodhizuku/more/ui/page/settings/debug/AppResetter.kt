package com.neodhizuku.more.ui.page.settings.debug

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Process
import kotlin.system.exitProcess

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.OfficialEndpoint
import com.neodhizuku.more.data.common.util.StartupUpdateChecker
import com.neodhizuku.more.data.settings.model.room.DhizukuRoom
import com.neodhizuku.more.server.DaemonService
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.server.DpmApiCatalog
import com.neodhizuku.more.server.RequestPermissionToken
import com.neodhizuku.more.server.ServiceStatus

import org.koin.core.component.KoinComponent
import org.koin.core.component.get

import java.io.File

/**
 * 「重置 NeoDhizuku」。
 * 清掉的是**本应用自己的全部数据与配置**：SharedPreferences、私有目录文件、
 * Room 数据库、背景图、内存 token / 状态缓存；然后直接结束进程。
 * 刻意不动系统级身份（设备所有者 / 设备管理员）—— 那不属于应用数据，
 * 清掉会直接让应用失去能力且无法自行恢复。下一次进入时 DhizukuState.sync()
 * 会按系统真实状态重建，服务也会按默认配置重新拉起，即「清空重建」。
 */
object AppResetter : KoinComponent {

    fun reset(context: Context) {
        val app = context.applicationContext

        // 1) 先关数据库，否则删掉文件后仍持有打开的句柄
        runCatching { get<DhizukuRoom>().close() }

        // 2) 清空 CE / DE 两边的全部 SharedPreferences
        val contexts = mutableListOf(app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            contexts += app.createDeviceProtectedStorageContext()
        }
        contexts.forEach { ctx ->
            val dir = File(ctx.applicationInfo.dataDir, "shared_prefs")
            runCatching {
                dir.listFiles()?.forEach { f ->
                    val name = f.name.removeSuffix(".xml")
                    if (name.isNotBlank()) {
                        ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
                            .edit().clear().commit()
                    }
                }
            }
        }

        // 3) 清内存运行态
        RequestPermissionToken.clearAll()
        ServiceStatus.setDebugValues(count = 0, startElapsed = -1L)
        OfficialEndpoint.forget()
        StartupUpdateChecker.resetForDebug()
        DpmApiCatalog.invalidateCache()

        // 4) 删除私有目录里的一切（配置 json、背景图、数据库、缓存）
        contexts.forEach { ctx ->
            listOf(
                ctx.filesDir,
                ctx.noBackupFilesDir,
                ctx.cacheDir,
                File(ctx.applicationInfo.dataDir, "databases"),
                File(ctx.applicationInfo.dataDir, "shared_prefs")
            ).forEach { dir -> runCatching { dir.deleteRecursively() } }
        }
        runCatching { app.externalCacheDir?.deleteRecursively() }

        // 5) 通知与守护服务
        runCatching {
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        }
        runCatching { app.stopService(Intent(app, DaemonService::class.java)) }

        // 6) 身份状态按系统真实值重建一次（不写库，只刷新内存）
        runCatching { DhizukuState.sync(app) }

        NeoLog.i(NeoLog.BOOT) { "重置：应用数据已清空，准备退出管理器" }
    }

    /** 结束整个任务栈并杀掉进程，等用户重新点图标进入。 */
    fun exit(activity: Activity) {
        runCatching { activity.finishAffinity() }
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}

/** 从 Compose 的 Context 里找到宿主 Activity。 */
internal fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
