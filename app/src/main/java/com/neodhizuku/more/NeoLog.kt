package com.neodhizuku.more

import android.util.Log

import com.neodhizuku.more.data.settings.model.room.DhizukuRoom
import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.koin.core.component.KoinComponent
import org.koin.core.component.get

import java.util.ArrayDeque

/**
 * 统一调试日志。
 *
 * 设计要点：
 * - 单一 TAG `NeoDhizuku`，用 `adb logcat -s NeoDhizuku:V` 可以只看本项目输出；
 * - 每个分类有独立子标签（如 `Nav`、`Perm`），便于过滤；
 * - 消息用 lambda 传入，关闭日志时不产生字符串拼接开销；
 * - 受 `NeoDhizuku.DebugLog` 开关控制；总开关关闭则完全静默；
 * - 同时维护一个内存环形缓冲，供调用记录截取「日志片段」；
 * - W / E / I 级日志会批量落库，可在「调用记录 → 运行日志」中长期查看。
 *
 * 用法：
 * ```
 * NeoLog.d(NeoLog.NAV, "navigate to $route")
 * NeoLog.w(NeoLog.THEME, "unsupported monet")
 * NeoLog.e(NeoLog.PERM, "persist failed", throwable)
 * ```
 */
object NeoLog : KoinComponent {

    private const val TAG = "NeoDhizuku"

    /** 分类子标签 */
    const val NAV = "Nav"
    const val THEME = "Theme"
    const val PERM = "Perm"
    const val HOME = "Home"
    const val APPS = "Apps"
    const val DAEMON = "Daemon"
    const val SERVER = "Server"
    const val ACTIVATE = "Activate"
    const val UI = "UI"
    const val BOOT = "Boot"

    /** 内存环形缓冲容量 */
    private const val BUFFER_SIZE = 300

    /** 落库批量阈值 */
    private const val FLUSH_THRESHOLD = 64

    /** 落库间隔 */
    private const val FLUSH_INTERVAL_MS = 5_000L

    data class Line(
        val level: String,
        val sub: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bufferMutex = Mutex()
    private val pendingMutex = Mutex()

    /** 环形缓冲：最新的在末尾 */
    private val buffer = ArrayDeque<Line>(BUFFER_SIZE)

    /** 待落库队列 */
    private val pending = ArrayDeque<RuntimeLogEntity>()

    private val room: DhizukuRoom?
        get() = runCatching { get<DhizukuRoom>() }.getOrNull()

    private fun enabled(): Boolean = NeoDhizuku.on(NeoDhizuku.DebugLog)

    @JvmStatic
    fun v(sub: String, msg: () -> String) {
        if (enabled()) {
            val text = msg()
            Log.v(TAG, "[$sub] $text")
            onLine("V", sub, text)
        }
    }

    @JvmStatic
    fun d(sub: String, msg: () -> String) {
        if (enabled()) {
            val text = msg()
            Log.d(TAG, "[$sub] $text")
            onLine("D", sub, text)
        }
    }

    @JvmStatic
    fun i(sub: String, msg: () -> String) {
        if (enabled()) {
            val text = msg()
            Log.i(TAG, "[$sub] $text")
            onLine("I", sub, text)
        }
    }

    @JvmStatic
    fun w(sub: String, msg: () -> String) {
        if (enabled()) {
            val text = msg()
            Log.w(TAG, "[$sub] $text")
            onLine("W", sub, text)
        }
    }

    @JvmStatic
    fun e(sub: String, msg: () -> String, tr: Throwable? = null) {
        if (!enabled()) return
        val text = if (tr != null) {
            Log.e(TAG, "[$sub] ${msg()}", tr)
            "${msg()} | ${tr.javaClass.simpleName}: ${tr.message}"
        } else {
            Log.e(TAG, "[$sub] ${msg()}")
            msg()
        }
        onLine("E", sub, text)
    }

    /**
     * 写入缓冲，并按级别决定是否落库。
     *
     * V / D 只进内存缓冲（量太大，落库会迅速撑爆表）；
     * I / W / E 同时进入待落库队列。
     */
    private fun onLine(level: String, sub: String, message: String) {
        val line = Line(level, sub, message)
        val shouldPersist = NeoDhizuku.on(NeoDhizuku.UseRuntimeLogPersist) &&
            (level == "I" || level == "W" || level == "E")
        ioScope.launch {
            bufferMutex.withLock {
                buffer.addLast(line)
                while (buffer.size > BUFFER_SIZE) buffer.removeFirst()
            }
            if (!shouldPersist) return@launch
            val reached = pendingMutex.withLock {
                pending.addLast(
                    RuntimeLogEntity(
                        level = level,
                        sub = sub,
                        message = message,
                        timestamp = line.timestamp
                    )
                )
                while (pending.size > BUFFER_SIZE * 4) pending.removeFirst()
                pending.size >= FLUSH_THRESHOLD
            }
            if (reached) flushAsync()
        }
    }

    /**
     * 取缓冲快照，最新的在最前面。
     *
     * 供调用记录落库时截取「该次调用发生时的运行日志片段」。
     */
    suspend fun snapshot(limit: Int = 20): List<Line> = bufferMutex.withLock {
        buffer.toList().asReversed().take(limit)
    }

    /** 把待落库队列写入数据库 */
    suspend fun flush() {
        if (!NeoDhizuku.on(NeoDhizuku.UseRuntimeLogPersist)) return
        val batch: List<RuntimeLogEntity> = pendingMutex.withLock {
            val items = pending.toList()
            pending.clear()
            items
        }
        if (batch.isEmpty()) return
        runCatching { room?.runtimeLogDao?.insertAll(batch) }
            .onFailure { Log.w(TAG, "[Boot] runtime log flush failed: ${it.message}") }
    }

    /** 非挂起版本：内部自起协程，供非协程上下文调用 */
    @JvmStatic
    fun flushAsync() {
        ioScope.launch {
            try {
                flush()
            } catch (t: Throwable) {
                Log.w(TAG, "[Boot] flush error: ${t.message}")
            }
        }
    }

    /** 启动定时 flush；由 App.onCreate 调用一次 */
    @JvmStatic
    fun startFlushLoop() {
        if (!NeoDhizuku.on(NeoDhizuku.UseRuntimeLogPersist)) return
        ioScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushAsync()
            }
        }
    }

    /**
     * 一次性输出全部开关状态，便于复现问题时贴到 issue 里。
     */
    @JvmStatic
    fun dumpSwitches() {
        if (!enabled()) return
        i(BOOT) { "开关状态：${NeoDhizuku.describe()}" }
    }
}
