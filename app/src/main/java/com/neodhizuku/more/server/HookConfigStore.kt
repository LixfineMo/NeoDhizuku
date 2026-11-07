package com.neodhizuku.more.server

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Xposed 模块（DAX 化转发）的配置与报到表。
 *
 * 这个 object 住在 NeoDhizuku 主进程里；[HookConfigProvider] 是它唯一的跨进程入口。
 * 模块代码运行在**被 hook 的第三方进程**，只能通过 ContentProvider.call 取配置。
 *
 * 设计要点：
 *  - 总开关默认关闭。没打开时 Provider 返回 disabled，模块加载了也完全 no-op，
 *    这就是「没有 LSPosed / root 的用户无感」在代码侧的保证。
 *  - scope 留空 = 不额外过滤，交给 LSPosed 里勾选的作用域决定；
 *    一旦填了包名，就只有这些包会被放行。
 *  - configVersion 每次改动自增，模块心跳据此热更新行为参数。
 *  - 报到表只存内存，进程重启即清空，与「启动后才生效」语义一致。
 */
object HookConfigStore {

    private const val PREF = "hook_config"

    const val AUTHORITY = "com.neodhizuku.more.hook.config"
    const val M_SCOPE = "scope"
    const val M_REGISTER = "register"
    const val M_HEARTBEAT = "heartbeat"

    private lateinit var app: Context
    private val version = AtomicLong(0)
    private val reports = ConcurrentHashMap<String, Report>()
    /** 模块最近一次与主进程通讯的时间（含被拒绝的 scope 查询）。 */
    private val lastContact = AtomicLong(0)

    data class Report(
        val pkg: String,
        val pid: Int,
        val uid: Int,
        val processName: String,
        val framework: String,
        val sdk: Int,
        val time: Long
    )

    private fun prefs() = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        version.set(prefs().getLong("configVersion", 1).coerceAtLeast(1))
    }

    private fun bump() {
        val v = version.incrementAndGet()
        prefs().edit().putLong("configVersion", v).apply()
    }

    val configVersion: Long get() = version.get().coerceAtLeast(1)

    var enabled: Boolean
        get() = if (::app.isInitialized) prefs().getBoolean("enabled", false) else false
        set(value) {
            prefs().edit().putBoolean("enabled", value).apply()
            bump()
        }

    /** 留空表示跟随 LSPosed 里勾选的作用域。 */
    var scope: Set<String>
        get() = (if (::app.isInitialized) prefs().getStringSet("scope", emptySet()) else emptySet())
            ?.toSet() ?: emptySet()
        set(value) {
            prefs().edit().putStringSet("scope", value.toSet()).apply()
            bump()
        }

    fun flag(key: String, default: Boolean): Boolean =
        if (::app.isInitialized) prefs().getBoolean(key, default) else default

    fun setFlag(key: String, value: Boolean) {
        prefs().edit().putBoolean(key, value).apply()
        bump()
    }

    fun allowed(pkg: String): Boolean {
        if (!enabled) return false
        val s = scope
        return s.isEmpty() || pkg in s
    }

    /** 下发给模块的配置。未放行时返回 empty=true，模块据此保持 no-op。 */
    fun buildConfig(pkg: String): Bundle {
        lastContact.set(System.currentTimeMillis())
        val b = Bundle()
        if (!allowed(pkg)) {
            b.putBoolean("enabled", false)
            b.putBoolean("empty", true)
            return b
        }
        b.putBoolean("enabled", true)
        b.putLong("configVersion", configVersion)
        b.putBoolean("hijackQuery", flag("hijackQuery", true))
        b.putBoolean("hijackCall", flag("hijackCall", true))
        b.putBoolean("forceOwnerTrue", flag("forceOwnerTrue", false))
        b.putBoolean("allowPackageInstaller", flag("allowPackageInstaller", true))
        b.putBoolean("allowHiddenOwnerMethods", flag("allowHiddenOwnerMethods", true))
        b.putBoolean("appendActiveAdmins", flag("appendActiveAdmins", true))
        b.putInt("heartbeatMs", 5000)
        b.putString("ownerPackage", app.packageName)
        b.putString(
            "ownerComponent",
            ComponentName(app, DhizukuDAReceiver::class.java).flattenToString()
        )
        return b
    }

    fun register(
        pkg: String, pid: Int, uid: Int, processName: String, framework: String, sdk: Int
    ): Boolean {
        // 报到也刷新接触时间
        lastContact.set(System.currentTimeMillis())
        if (!allowed(pkg)) return false
        reports["$pkg:$pid"] = Report(pkg, pid, uid, processName, framework, sdk, System.currentTimeMillis())
        return true
    }

    fun heartbeat(): Bundle = Bundle().apply { putLong("configVersion", configVersion) }

    /** 心跳刷新报到时间，避免长时间运行的目标进程被判成已失联。 */
    fun touch(pkg: String, pid: Int) {
        lastContact.set(System.currentTimeMillis())
        val key = "$pkg:$pid"
        reports[key]?.let { reports[key] = it.copy(time = System.currentTimeMillis()) }
    }

    /**
     * 首页药丸用：60 秒内有报到的进程才认为模块真的 hook 到了。
     * 只有 scope 查询（模块已加载但被总开关挡住）时显示 LSPosed。
     */
    fun frameworkLabel(): String? {
        val now = System.currentTimeMillis()
        val alive = snapshot().filter { now - it.time < 60_000L }
        if (alive.isNotEmpty()) {
            return if (alive.any { it.framework.equals("LSPatch", ignoreCase = true) })
                "LSPatch" else "LSPosed"
        }
        return if (now - lastContact.get() < 60_000L) "LSPosed" else null
    }

    fun snapshot(): List<Report> = reports.values.sortedByDescending { it.time }

    /**
     * 模块是否已被 LSPosed / LSPatch 加载过。
     *
     * 只看「最近 60 秒内有没有来问过配置或报到」，与总开关无关：
     * 用户还没打开总开关时也必须能看见配置入口，否则永远打不开它。
     * 没有 Xposed 框架的设备上没人调用 Provider，恒为 false。
     */
    fun moduleDetected(): Boolean = frameworkLabel() != null

    /** 已 hook 到的去重应用数 / 活跃进程数（60 秒内有心跳）。 */
    fun hookedCounts(): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val alive = snapshot().filter { now - it.time < 60_000L }
        return alive.map { it.pkg }.distinct().size to alive.size
    }

    /** 供调试页重置：清空全部模块配置与报到记录。 */
    fun reset() {
        prefs().edit().clear().apply()
        version.set(1)
        reports.clear()
    }
}
