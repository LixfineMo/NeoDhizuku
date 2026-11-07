package com.neodhizuku.more.server

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.ShizukuShell

/**
 * 设备身份的「移除 / 转移」工具，支持被改过包名的同项目实例。
 *
 * 背景：有人把 applicationId 改成别的（例如 com.neodhizuku.morf）后重新编译，
 * 但 receiver 的 android:name 仍是完整类名
 * `com.neodhizuku.more.server.DhizukuDAReceiver`（清单里写的是 `.server.`
 * 缩写，改包名的工具通常只动 manifest 的 package，不动类名）。
 * 于是系统里会同时存在多个 ComponentName，而按 BuildConfig.APPLICATION_ID
 * 拼出来的那一个既不是设备所有者、也查不到残留记录 —— 表现为
 * `Insufficient capability: DEVICE_ADMIN cannot execute transferOwnership
 * (requires DEVICE_OWNER)` 且两个实例都显示「未激活」。
 *
 * 这里不假设包名：从系统实际声明的设备管理员组件里枚举，按 receiver
 * 类名识别同项目实例，再对任意 ComponentName 执行移除或转移。
 *
 * 只做移除与转移。其它能力不因改包名做特殊处理 —— 改包名的人自己承担。
 */
object OwnerIdentityTools {

    private const val ADMIN_ACTION = "android.app.action.DEVICE_ADMIN_ENABLED"

    /** 本项目的设备管理员类名。改包名不会改到它，是识别同项目实例的关键。 */
    private val SELF_CLASS = DhizukuDAReceiver::class.java.name

    data class Entry(
        val component: ComponentName,
        val label: String,
        /** receiver 类名匹配本项目（可能是被改过包名的实例） */
        val isSameProject: Boolean,
        /** 与当前进程的包名一致 */
        val isSelfPackage: Boolean,
        val active: Boolean
    )

    data class ActionResult(val ok: Boolean, val detail: String)

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /** 当前这个实例是否就是设备所有者 —— 转移操作的必要前提。 */
    fun isSelfDeviceOwner(context: Context): Boolean = runCatching {
        dpm(context).isDeviceOwnerApp(context.packageName)
    }.getOrDefault(false)

    /**
     * 枚举可作为操作对象的管理员组件。
     *
     * 主来源是 PackageManager 的 receiver 查询：它不要求调用方是 DO，
     * 因此「两个都未激活」时也能列出来。activeAdmins 只用来补充
     * 「已激活」标记，拿不到（无权限）也不影响列表完整性。
     */
    fun candidates(context: Context): List<Entry> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, ComponentName>()

        runCatching {
            @Suppress("DEPRECATION")
            val resolves = pm.queryBroadcastReceivers(Intent(ADMIN_ACTION), 0)
            resolves.forEach { ri ->
                val info = ri.activityInfo ?: return@forEach
                val c = ComponentName(info.packageName, info.name)
                found[c.flattenToShortString()] = c
            }
        }.onFailure {
            NeoLog.w(NeoLog.DAEMON) { "[Owner] 枚举设备管理员失败：${it.message}" }
        }

        runCatching {
            dpm(context).getActiveAdmins()?.forEach { c ->
                found[c.flattenToShortString()] = c
            }
        }.onFailure {
            NeoLog.w(NeoLog.DAEMON) { "[Owner] 读取 activeAdmins 失败：${it.message}" }
        }

        // 自己一定在列表里，即使上面的查询被厂商裁掉
        found[DhizukuState.admin.flattenToShortString()] = DhizukuState.admin

        val activeSet = runCatching {
            dpm(context).getActiveAdmins()?.map { it.flattenToShortString() }?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())

        return found.values.map { c ->
            val label = runCatching {
                @Suppress("DEPRECATION")
                pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
            }.getOrDefault(c.packageName)
            Entry(
                component = c,
                label = label,
                isSameProject = c.className == SELF_CLASS ||
                    c.className.endsWith(".DhizukuDAReceiver"),
                isSelfPackage = c.packageName == context.packageName,
                active = c.flattenToShortString() in activeSet ||
                    runCatching { dpm(context).isAdminActive(c) }.getOrDefault(false)
            )
        }.sortedWith(
            compareByDescending<Entry> { it.isSameProject }
                .thenByDescending { it.isSelfPackage }
                .thenByDescending { it.active }
                .thenBy { it.label }
        )
    }

    /**
     * 移除一个设备管理员组件（含被改过包名的实例）。
     *
     * 优先走 DPM：调用方是 DO、或目标就是自己的 admin 组件时可用。
     * 若目标包名本身仍是 device owner，再试一次 clearDeviceOwnerApp
     * （只有 DO 自己能调，参数不符时系统会抛异常，这里吞掉即可）。
     */
    @Suppress("DEPRECATION")
    fun remove(context: Context, target: ComponentName): ActionResult {
        val manager = dpm(context)
        val direct = runCatching {
            manager.removeActiveAdmin(target)
            true
        }.getOrDefault(false)
        if (direct) {
            NeoLog.i(NeoLog.DAEMON) { "[Owner] removeActiveAdmin 成功 ${target.flattenToShortString()}" }
            return ActionResult(true, "removeActiveAdmin: ok")
        }

        val cleared = runCatching {
            if (manager.isDeviceOwnerApp(target.packageName)) {
                manager.clearDeviceOwnerApp(target.packageName)
                true
            } else false
        }.getOrDefault(false)
        if (cleared) {
            return ActionResult(true, "clearDeviceOwnerApp: ok")
        }

        return ActionResult(
            false,
            "需要设备所有者身份或 Shizuku / root（当前实例无权移除该组件）"
        )
    }

    /**
     * 用 shell 身份强清残留。adb shell dpm 允许 remove-active-admin，
     * 这是「系统显示未激活、卸载却报设备管理器」这种错位状态最有效的解法。
     *
     * 组件名先用 unflattenFromString 规范化，避免把用户输入直接拼进命令。
     */
    suspend fun removeViaShell(context: Context, target: ComponentName): ActionResult {
        val flat = target.flattenToShortString()
        val result = ShizukuShell.exec("dpm remove-active-admin $flat", 15_000L)
        val text = listOf(result.output, result.error).filter { it.isNotBlank() }.joinToString(" ")
        return ActionResult(
            result.isSuccess,
            if (result.isSuccess) "shell: $flat 已移除" else "shell(exit=${result.exitCode}): $text"
        )
    }

    /**
     * 把设备所有者转移给目标组件。
     *
     * 硬性前提：**调用方必须是当前 device owner**。这不是本项目能绕过的限制 ——
     * 截图里 `DEVICE_ADMIN cannot execute transferOwnership (requires DEVICE_OWNER)`
     * 说的正是这件事，与目标组件是否声明了管理员无关。
     * 若所有者是被改包名的那个实例，请在那一个实例（同一份代码）里执行本操作。
     */
    fun transfer(context: Context, target: ComponentName): ActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return ActionResult(false, "transferOwnership 需要 Android 9 (API 28) 及以上")
        }
        val manager = dpm(context)
        if (!isSelfDeviceOwner(context)) {
            return ActionResult(
                false,
                "当前实例不是设备所有者，无法转移；请到持有所有者的那个实例里操作"
            )
        }
        if (!runCatching { manager.isAdminActive(target) }.getOrDefault(false)) {
            return ActionResult(
                false,
                "目标组件未激活为设备管理员，请先在系统设置里激活它"
            )
        }

        return runCatching {
            @Suppress("DEPRECATION")
            manager.transferOwnership(
                DhizukuState.admin,
                target,
                PersistableBundle()
            )
            NeoLog.i(NeoLog.DAEMON) {
                "[Owner] 已转移所有者到 ${target.flattenToShortString()}"
            }
            ActionResult(true, "已转移所有者到 ${target.flattenToShortString()}")
        }.getOrElse { e ->
            val msg = e.message.orEmpty()
            val hint = when {
                msg.contains("DEVICE_OWNER", ignoreCase = true) ->
                    "调用方不是设备所有者（requires DEVICE_OWNER）"
                msg.contains("admin", ignoreCase = true) && msg.contains("active", ignoreCase = true) ->
                    "目标组件不是有效的已激活设备管理员"
                else -> msg.ifBlank { e.javaClass.simpleName }
            }
            ActionResult(false, hint)
        }
    }

    /** 解析用户手输的 `pkg/cls`，非法输入返回 null。 */
    fun parse(raw: String): ComponentName? = runCatching {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.all { it.isLetterOrDigit() || it in "._/$" }) return null
        ComponentName.unflattenFromString(text)
    }.getOrNull()
}
