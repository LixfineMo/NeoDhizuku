package com.neodhizuku.more.ui.util

import android.app.ActivityManager
import android.content.Context
import com.neodhizuku.more.NeoLog

/**
 * 控制本应用的任务是否出现在「多任务」（最近任务）列表。
 * manifest 里的 `android:excludeFromRecents` 在编译期固定，无法跟随开关变化，
 * 因此运行时改用 `ActivityManager.AppTask.setExcludeFromRecents()`（API 21+）。
 * `getAppTasks()` 只返回属于本应用的任务，不会误伤其它应用。
 * 顺带一提：不在多任务显示还有个副作用是划不掉，
 * 因此不会因划卡被系统连带杀掉进程（守护服务与界面同进程）。
 */
object RecentsVisibility {

    fun apply(context: Context, exclude: Boolean) {
        val am = runCatching {
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        }.getOrNull()
        if (am == null) {
            NeoLog.w(NeoLog.THEME) { "recents: 取不到 ActivityManager" }
            return
        }

        val tasks = runCatching { am.appTasks }.getOrNull().orEmpty()
        if (tasks.isEmpty()) {
            NeoLog.d(NeoLog.THEME) { "recents: 无任务可设置（exclude=$exclude）" }
            return
        }

        var applied = 0
        tasks.forEach { task ->
            runCatching {
                task.setExcludeFromRecents(exclude)
                applied++
            }
        }
        NeoLog.d(NeoLog.THEME) { "recents: exclude=$exclude 生效 $applied/${tasks.size}" }
    }
}
