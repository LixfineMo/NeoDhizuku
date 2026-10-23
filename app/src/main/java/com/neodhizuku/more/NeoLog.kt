package com.neodhizuku.more

import android.util.Log

import com.neodhizuku.more.data.settings.model.room.DhizukuRoom
import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity
import com.neodhizuku.more.data.settings.repo.SettingsRepo

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

    /**
     * 是否记录全部运行日志。
     *
     * 由「实验性功能 → 完整运行日志」控制，默认关闭。
     * 关闭时只保留权限调用、权限变更与激活相关的日志，
     * 其余分类（导航、主题、界面、守护、首页、软件）一律不输出也不落库。
     */
    @Volatile
    private var verboseLog = false

    /** 精简模式下保留的分类 */
    private val essentialSubs = setOf(PERM, ACTIVATE, SERVER)

    /** SERVER 分类里属于权限调用或高风险操作的关键字 */
    private val serverEssentialKeys = listOf(
        "remote_transact", "provider", "check", "wipe", "擦除",
        "转移", "委派", "授权", "拒绝", "允许"
    )

    /**
     * 切换记录模式。
     *
     * 这条提示走系统 Log 直写，不经过下面的过滤规则，
     * 保证开关是否生效一眼可见。
     */
    private fun onVerboseChanged(value: Boolean) {
        verboseLog = value
        Log.i(TAG, "[Log] 运行日志模式=${if (value) "完整" else "精简"}")
    }

    /**
     * 订阅「完整运行日志」开关。
     *
     * 由 App.onCreate 调用一次。订阅前先同步读一次当前值，
     * 否则启动阶段早于首次发射的日志会按精简模式丢掉。
     */
    @JvmStatic
    fun bindVerboseLog() {
        val repo = runCatching { get<SettingsRepo>() }.getOrNull()
        if (repo == null) {
            Log.w(TAG, "[Log] 设置库不可用，运行日志保持精简模式")
            return
        }
        onVerboseChanged(runCatching { repo.isExperimentalVerboseLog }.getOrDefault(false))
        ioScope.launch {
            runCatching {
                repo.flowExperimentalVerboseLog().collect { onVerboseChanged(it) }
            }.onFailure {
                Log.w(TAG, "[Log] 订阅完整运行日志开关失败：${it.message}")
            }
        }
    }

    /** 第一层过滤：按分类粗筛，避免不必要的字符串拼接 */
    private fun keepClass(sub: String): Boolean = verboseLog || sub in essentialSubs

    /** 第二层过滤：SERVER 分类按内容筛，只留权限调用与高风险操作 */
    private fun keepContent(sub: String, text: String): Boolean {
        if (verboseLog) return true
        if (sub != SERVER) return true
        return serverEssentialKeys.any { text.contains(it) }
    }

    @JvmStatic
    fun v(sub: String, msg: () -> String) {
        if (enabled() && keepClass(sub)) {
            val text = msg()
            if (!keepContent(sub, text)) return
            Log.v(TAG, "[$sub] $text")
            onLine("V", sub, text)
        }
    }

    @JvmStatic
    fun d(sub: String, msg: () -> String) {
        if (enabled() && keepClass(sub)) {
            val text = msg()
            if (!keepContent(sub, text)) return
            Log.d(TAG, "[$sub] $text")
            onLine("D", sub, text)
        }
    }

    @JvmStatic
    fun i(sub: String, msg: () -> String) {
        if (enabled() && keepClass(sub)) {
            val text = msg()
            if (!keepContent(sub, text)) return
            Log.i(TAG, "[$sub] $text")
            onLine("I", sub, text)
        }
    }

    @JvmStatic
    fun w(sub: String, msg: () -> String) {
        if (enabled() && keepClass(sub)) {
            val text = msg()
            if (!keepContent(sub, text)) return
            Log.w(TAG, "[$sub] $text")
            onLine("W", sub, text)
        }
    }

    @JvmStatic
    fun e(sub: String, msg: () -> String, tr: Throwable? = null) {
        if (!enabled() || !keepClass(sub)) return
        val base = msg()
        if (!keepContent(sub, base)) return
        val text = if (tr != null) {
            Log.e(TAG, "[$sub] $base", tr)
            "$base | ${tr.javaClass.simpleName}: ${tr.message}"
        } else {
            Log.e(TAG, "[$sub] $base")
            base
        }
        onLine("E", sub, text)
    }

    /**
     * 写入缓冲，并按级别决定是否落库。
     *
     * 精简模式下 V / D 只进内存缓冲（量太大，落库会迅速撑爆表），
     * 只有 I / W / E 落库；完整模式下全部级别都落库，
     * 否则导航、主题一类以 V / D 输出的日志在「运行日志」里永远看不到。
     */
    private fun onLine(level: String, sub: String, message: String) {
        val line = Line(level, sub, message)
        val shouldPersist = NeoDhizuku.on(NeoDhizuku.UseRuntimeLogPersist) &&
            (verboseLog || level == "I" || level == "W" || level == "E")
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
