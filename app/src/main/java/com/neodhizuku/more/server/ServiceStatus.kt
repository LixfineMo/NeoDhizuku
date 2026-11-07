package com.neodhizuku.more.server

import android.os.SystemClock
import androidx.compose.runtime.getValue
import com.neodhizuku.more.NeoLog
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 服务运行状态（首页右侧两张卡的数据源）。
 *
 * 两个字段：
 * - `authorizedCount`：已授权且未拉黑的应用数量
 * - `startElapsedRealtime`：DaemonService 本次启动的 elapsedRealtime 基准。
 *   服务被杀掉重启后由 DaemonService.onCreate 重新写入，运行时间归零重算，
 *   不跨次累计。
 *
 * 用 SystemClock.elapsedRealtime() 而非 System.currentTimeMillis()，
 * 避免用户改系统时间导致显示负数或跳变。
 */
data object ServiceStatus {

    var authorizedCount by mutableStateOf(0)
        private set

    /** < 0 表示服务当前未运行 */
    var startElapsedRealtime by mutableLongStateOf(-1L)
        private set

    fun markStarted() {
        startElapsedRealtime = SystemClock.elapsedRealtime()
        NeoLog.i(NeoLog.DAEMON) { "ServiceStatus: 已启动，计时归零" }
    }

    fun markStopped() {
        startElapsedRealtime = -1L
        NeoLog.i(NeoLog.DAEMON) { "ServiceStatus: 已停止" }
    }

    fun updateAuthorizedCount(count: Int) {
        authorizedCount = count
    }

    /** 调试用：直接覆盖运行态数值，仅改内存，不触碰服务本身。 */
    fun setDebugValues(count: Int? = null, startElapsed: Long? = null) {
        count?.let { authorizedCount = it }
        startElapsed?.let { startElapsedRealtime = it }
    }

    /** 服务是否在运行（计时基准 >= 0 即运行中） */
    fun isRunning(): Boolean = startElapsedRealtime >= 0L

    /** 运行毫秒数；服务未运行时返回 -1 */
    fun uptimeMillis(): Long {
        val start = startElapsedRealtime
        if (start < 0) return -1L
        return SystemClock.elapsedRealtime() - start
    }

    /**
     * 格式化为 HH:MM:SS。
     *
     * 用固定宽度数字而非中文短句：中文会导致卡片宽度随内容跳动、三卡错位。
     * 小时数**不取模 24**，超过 24 小时直接显示 25、100……（无 24h 上限）。
     */
    fun formatUptime(millis: Long): String {
        if (millis < 0) return "00:00:00"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
