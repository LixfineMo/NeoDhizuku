package com.neodhizuku.more.data.common.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.server.DhizukuState

/**
 * 免删账号激活设备所有者。
 *
 * 走的是 `DevicePolicyManagerService#setDeviceOwner` 的**非 ADB 分支**：
 * 用应用自身 uid 直接调接口，此时 `isAdb()` 为 false，系统只校验
 * `hasUserSetupCompleted`，**完全不读账户列表**。
 * 因此不需要删除账号 —— 这是与原版 Dhizuku（走 ADB，`dpm set-device-owner`
 * 三道闸全读、必须清账户）的根本区别。
 *
 * 前置条件：
 *  - Android 13（API 33）及以上：`DEVICE_POLICY_MANAGEMENT` Role 自该版本引入
 *  - Shizuku 或 root：用于执行 `cmd role` 与 `settings put secure`
 *
 * 链路：
 *  ① shell: cmd role set-bypassing-role-qualification true
 *  ② shell: cmd role add-role-holder <DP_ROLE> <pkg> --user 0
 *     → 获得 MANAGE_PROFILE_AND_DEVICE_OWNERS / MANAGE_DEVICE_ADMINS
 *  ③ shell: settings put secure user_setup_complete 0
 *  ④ 应用自身: forceUpdateUserSetupComplete(0)   ← 把③的值搬进 DPM 内存态
 *  ⑤ 应用自身: setActiveAdmin(self, true)
 *  ⑥ 应用自身: setDeviceOwner(...)
 *  ⑦ shell: settings put secure user_setup_complete 1   ← 必须恢复
 *
 * ③与④必须配对：只做③不做④无效，内存态不变，闸仍关闭。
 */
object DpDirectActivation {

    /** `DEVICE_POLICY_MANAGEMENT` Role，自 Android 13（API 33）引入。 */
    const val ROLE_DEVICE_POLICY_MANAGEMENT = "android.app.role.DEVICE_POLICY_MANAGEMENT"

    private const val PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS =
        "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS"

    /** `Settings.Secure.USER_SETUP_COMPLETE` 的字面量（该常量 @hide，SDK 里没有）。 */
    const val SETTING_USER_SETUP_COMPLETE = "user_setup_complete"

    /** 回读标记，用于从 shell 输出中解析真实值。 */
    const val SETUP_READBACK_TAG = "NEO_SETUP_NOW="

    /** 低于此版本无法使用：DP Role 未引入。 */
    const val MIN_SDK = Build.VERSION_CODES.TIRAMISU

    // ------------------------------------------------------------------
    // 向导遗留状态
    //
    // `user_setup_complete` 被置 0 后若没能恢复，设备会长期停留在
    // "未完成开机向导"状态（三键导航的 Home / 最近任务失灵）。
    // 这里用一个持久化标记把它记下来，供界面提示与入口判断使用。
    // ------------------------------------------------------------------

    private const val PREF_NAME = "dp_setup_gate"
    private const val KEY_PENDING_RESTORE = "pending_restore"

    /** 是否遗留了未恢复的向导状态（置 0 之后没能恢复回 1）。 */
    fun isPendingRestore(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING_RESTORE, false)

    /** 标记/清除遗留状态。 */
    fun markPendingRestore(context: Context, pending: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_RESTORE, pending)
            .apply()
        NeoLog.i(NeoLog.ACTIVATE) { "pendingRestore -> $pending" }
    }

    /** 权限 grant 轮询次数与间隔 */
    private const val PERM_WAIT_TIMES = 10
    private const val PERM_WAIT_INTERVAL_MILLIS = 300L

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_SDK

    /** 执行结果 */
    data class StepResult(
        val success: Boolean,
        val message: String,
        val detail: String = ""
    )

    // ------------------------------------------------------------------
    // 命令构造
    // ------------------------------------------------------------------

    /** ①+②：授予 DP Role（唯一能拿到后续权限的通道）。 */
    fun buildGrantRoleScript(pkg: String): String = buildString {
        val d = Char(36).toString()
        append("ROLE=").append(ROLE_DEVICE_POLICY_MANAGEMENT).append("; ")
        append("PKG=").append(pkg).append("; ")
        append("BYPASS=0; ")
        append("if cmd role set-bypassing-role-qualification true >/dev/null 2>&1; then BYPASS=1; fi; ")
        // 两种 --user 参数位置都试，不同 ROM 支持的不一样
        append("OUT=").append(d).append("(cmd role add-role-holder --user 0 ").append(d)
            .append("ROLE ").append(d).append("PKG 2>&1); RC=").append(d).append("?; ")
        append("if [ ").append(d).append("RC -ne 0 ]; then ")
        append("OUT2=").append(d).append("(cmd role add-role-holder ").append(d)
            .append("ROLE ").append(d).append("PKG --user 0 2>&1); RC2=").append(d).append("?; ")
        append("if [ ").append(d).append("RC2 -eq 0 ]; then RC=0; OUT=").append(d)
            .append("OUT2; fi; ")
        append("fi; ")
        // bypass 是全局开关，用完关掉
        append("if [ ").append(d).append("BYPASS -eq 1 ]; then ")
        append("cmd role set-bypassing-role-qualification false >/dev/null 2>&1; fi; ")
        append("echo \"RC=").append(d).append("RC OUT=").append(d).append("OUT\"")
    }

    /**
     * ③ / ⑦：写入 `user_setup_complete` 并回读确认。
     *
     * 回读是必要的：部分 ROM 上 `settings put` 退出码为 0 但值未落库。
     */
    fun buildSetSetupCompleteScript(value: Int): String = buildString {
        val d = Char(36).toString()
        append("settings put secure ").append(SETTING_USER_SETUP_COMPLETE)
            .append(' ').append(value).append("; ")
        append("sleep 1; ")
        append("echo ").append(SETUP_READBACK_TAG).append(d)
            .append("(settings get secure ").append(SETTING_USER_SETUP_COMPLETE)
            .append(" 2>/dev/null)")
    }

    /**
     * 回读某个 Role 当前的持有者列表。
     *
     * 授予命令退出码为 0 并不等于角色真的生效 ——
     * 部分 ROM 上 `add-role-holder` 静默失败。这里回读用于确认。
     */
    fun buildGetRoleHoldersScript(role: String = ROLE_DEVICE_POLICY_MANAGEMENT): String =
        "cmd role get-role-holders $role 2>&1"

    /** 从回读输出中判断是否包含指定包名。 */
    fun parseRoleHolders(output: String?, pkg: String): Boolean =
        output?.contains(pkg) == true

    /** 只读回读当前值（不写入，可安全反复执行）。 */
    fun buildReadSetupCompleteScript(): String = buildString {
        val d = Char(36).toString()
        append("echo ").append(SETUP_READBACK_TAG).append(d)
            .append("(settings get secure ").append(SETTING_USER_SETUP_COMPLETE)
            .append(" 2>/dev/null)")
    }

    /** 从 shell 输出中解析回读值，解析不到返回 null（调用方应按"未确认"处理）。 */
    fun parseSetupComplete(output: String?): Int? =
        output?.lineSequence()
            ?.firstOrNull { it.contains(SETUP_READBACK_TAG) }
            ?.substringAfter(SETUP_READBACK_TAG)
            ?.trim()
            ?.toIntOrNull()

    /** 手动执行的完整指令，供界面展示与复制。 */
    fun buildManualCommands(pkg: String, admin: ComponentName): String = buildString {
        appendLine("# 1. 授予设备策略管理角色")
        appendLine("adb shell cmd role set-bypassing-role-qualification true")
        appendLine("adb shell cmd role add-role-holder --user 0 $ROLE_DEVICE_POLICY_MANAGEMENT $pkg")
        appendLine("adb shell cmd role set-bypassing-role-qualification false")
        appendLine()
        appendLine("# 2. 临时标记为未完成开机向导")
        appendLine("adb shell settings put secure $SETTING_USER_SETUP_COMPLETE 0")
        appendLine()
        appendLine("# 3. 激活设备所有者")
        appendLine("adb shell dpm set-device-owner ${admin.flattenToShortString()}")
        appendLine()
        appendLine("# 4. 恢复（务必执行）")
        appendLine("adb shell settings put secure $SETTING_USER_SETUP_COMPLETE 1")
    }

    // ------------------------------------------------------------------
    // 步骤
    // ------------------------------------------------------------------

    /**
     * 打开向导闸：授予 DP Role 并把 `user_setup_complete` 置 0。
     *
     * 这两步都必须借 shell 身份：`cmd role` 与 `settings put secure`
     * 都不是普通应用能直接调用的。
     */
    suspend fun openSetupGate(context: Context): StepResult {
        if (!isSupported()) {
            return StepResult(false, "当前系统版本不支持", "需要 Android 13（API 33）及以上")
        }
        val pkg = context.packageName

        val grant = ShizukuShell.exec(buildGrantRoleScript(pkg))
        NeoLog.i(NeoLog.ACTIVATE) { "DP Role 授予：${grant.output} ${grant.error}" }

        val holders = ShizukuShell.exec(buildGetRoleHoldersScript())
        val isHolder = parseRoleHolders(holders.output, pkg)
        NeoLog.i(NeoLog.ACTIVATE) {
            "DP Role 回读：isHolder=$isHolder out=${holders.output}"
        }

        // 权限 grant 与角色授予之间可能有延迟，轮询等待而不是一次性判定
        val hasPerm = waitForPermission(context)
        NeoLog.i(NeoLog.ACTIVATE) { "DP Role 权限：granted=$hasPerm" }

        if (!hasPerm) {
            val declared = isPermissionDeclared(context, PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS)
            val detail = buildString {
                appendLine("授权输出：${grant.output}")
                appendLine("授权错误：${grant.error}")
                appendLine("持有者回读：isHolder=$isHolder ${holders.output}")
                appendLine("清单已声明该权限：$declared")
                if (!declared) {
                    appendLine("→ 必须在 AndroidManifest.xml 中声明该权限，")
                    appendLine("  否则系统不会 grant，checkSelfPermission 恒为 DENIED。")
                }
            }
            return StepResult(false, "角色未生效", detail.trimEnd())
        }

        val set = ShizukuShell.exec(buildSetSetupCompleteScript(0))
        val now = parseSetupComplete(set.output)
        if (now != 0) {
            return StepResult(
                false,
                "未能把向导状态置为未完成",
                "回读值：$now\n输出：${set.output}\n错误：${set.error}"
            )
        }
        markPendingRestore(context, true)
        return StepResult(true, "向导闸已打开", "角色已授予，user_setup_complete=0")
    }

    /**
     * 尝试激活：同步内存态 → setActiveAdmin → setDeviceOwner。
     *
     * 无论成败都会尝试恢复 `user_setup_complete=1`，
     * 避免中途失败把设备留在"未完成向导"状态（三键导航的 Home/最近任务会失灵）。
     */
    suspend fun activate(context: Context): StepResult {
        if (!isSupported()) {
            return StepResult(false, "当前系统版本不支持", "需要 Android 13（API 33）及以上")
        }
        val pkg = context.packageName
        val admin = DhizukuState.admin
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return StepResult(false, "无法获取 DevicePolicyManager", "")

        if (runCatching { dpm.isDeviceOwnerApp(pkg) }.getOrDefault(false)) {
            return StepResult(true, "本应用已是设备所有者", "")
        }

        val log = mutableListOf<String>()
        try {
            // ④ 同步内存态（双向赋值：把 Settings 的真实值搬进 DPM 内存）
            val setupOk = invokeForceUpdateUserSetupComplete(dpm, 0)
            log += "forceUpdateUserSetupComplete(0): ${if (setupOk) "成功" else "失败（不阻断）"}"

            // ⑤
            val adminOk = invokeSetActiveAdmin(dpm, admin)
            log += "setActiveAdmin: ${if (adminOk) "成功" else "失败"}"
            if (!adminOk) {
                return StepResult(false, "无法注册为设备管理员", log.joinToString("\n"))
            }

            // ⑥ 反射返回 void，必须复查实际状态
            invokeSetDeviceOwner(dpm, admin)
            val nowOwner = runCatching { dpm.isDeviceOwnerApp(pkg) }.getOrDefault(false)
            log += "isDeviceOwnerApp 复查：$nowOwner"

            return if (nowOwner) {
                StepResult(true, "已激活设备所有者", log.joinToString("\n"))
            } else {
                StepResult(false, "系统拒绝了激活请求", log.joinToString("\n"))
            }
        } finally {
            val restore = ShizukuShell.exec(buildSetSetupCompleteScript(1))
            val restored = parseSetupComplete(restore.output)
            NeoLog.i(NeoLog.ACTIVATE) { "恢复 user_setup_complete -> $restored" }
            // 只有确认恢复成 1 才清除标记；未确认则保留，让界面继续提示
            markPendingRestore(context, restored != 1)
            DhizukuState.sync(context)
        }
    }

    /** ⑦ 单独恢复（用户手动触发的兜底）。 */
    suspend fun restore(context: Context): StepResult {
        val set = ShizukuShell.exec(buildSetSetupCompleteScript(1))
        val now = parseSetupComplete(set.output)
        markPendingRestore(context, now != 1)
        DhizukuState.sync(context)
        return if (now == 1) {
            StepResult(true, "已恢复", "user_setup_complete=1")
        } else {
            StepResult(false, "恢复未确认", "回读值：$now\n输出：${set.output}\n错误：${set.error}")
        }
    }

    // ------------------------------------------------------------------
    // 反射封装（全部 try/catch，绝不抛出）
    // ------------------------------------------------------------------

    /**
     * 取出异常的根本原因。
     *
     * 反射调用失败时抛的是 `InvocationTargetException`，它的 `message` 为 null
     * —— 直接打印只会看到 "失败：null"，真正的原因在 `targetException` 里
     * （通常是缺权限的 SecurityException），必须解出来才看得到。
     */
    private fun rootCause(t: Throwable): String {
        var cur = t
        var depth = 0
        while (cur is java.lang.reflect.InvocationTargetException && depth < 5) {
            cur = cur.targetException ?: break
            depth++
        }
        return "${cur::class.java.simpleName}: ${cur.message ?: "(无 message)"}"
    }

    /** 轮询等待权限 grant 生效，避免一次性判定把"延迟"误判成"失败"。 */
    private fun waitForPermission(context: Context): Boolean {
        repeat(PERM_WAIT_TIMES) { i ->
            val granted = runCatching {
                context.checkSelfPermission(PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (granted) return true
            if (i < PERM_WAIT_TIMES - 1) {
                runCatching { Thread.sleep(PERM_WAIT_INTERVAL_MILLIS) }
            }
        }
        return false
    }

    /** 该权限是否在清单中声明。用于把"未声明"和"未授予"区分开。 */
    private fun isPermissionDeclared(context: Context, permission: String): Boolean =
        runCatching {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS
            )
            info.requestedPermissions?.contains(permission) == true
        }.getOrDefault(false)

    /**
     * `DevicePolicyManager#forceUpdateUserSetupComplete(int)`。
     *
     * 参数是 **userId**，不是"完成状态"。它把 `Settings.Secure` 里的真实值
     * 原样赋给内存态 `mUserSetupComplete`，所以能双向覆盖。
     */
    private fun invokeForceUpdateUserSetupComplete(dpm: DevicePolicyManager, userId: Int): Boolean =
        try {
            val m = DevicePolicyManager::class.java.getMethod(
                "forceUpdateUserSetupComplete", Integer.TYPE
            )
            m.invoke(dpm, userId)
            true
        } catch (t: Throwable) {
            NeoLog.w(NeoLog.ACTIVATE) {
                "forceUpdateUserSetupComplete 失败：${rootCause(t)}"
            }
            false
        }

    /** `setActiveAdmin`，需要 MANAGE_DEVICE_ADMINS（DP Role 已带）。 */
    private fun invokeSetActiveAdmin(dpm: DevicePolicyManager, admin: ComponentName): Boolean =
        try {
            val m = DevicePolicyManager::class.java.getMethod(
                "setActiveAdmin",
                ComponentName::class.java,
                Boolean::class.javaPrimitiveType,
                Integer.TYPE
            )
            m.invoke(dpm, admin, true, 0)
            true
        } catch (t: Throwable) {
            NeoLog.w(NeoLog.ACTIVATE) { "setActiveAdmin 失败：${rootCause(t)}" }
            false
        }

    /**
     * `setDeviceOwner`。签名在 Android 14 处发生过变更：
     *  - A13: `(ComponentName, String ownerName, int userId)`
     *  - A14+: `(ComponentName, int userId)`
     *
     * 写死三参数在 A14+ 会直接 NoSuchMethodException，
     * 所以按 SDK 优先选，失败再回退另一签名。
     */
    private fun invokeSetDeviceOwner(dpm: DevicePolicyManager, admin: ComponentName) {
        val userId = Process.myUid() / 100000

        // 两参：Android 14 及以上
        val twoArg = runCatching {
            DevicePolicyManager::class.java.getMethod(
                "setDeviceOwner", ComponentName::class.java, Integer.TYPE
            ).invoke(dpm, admin, userId)
        }
        if (twoArg.isSuccess) {
            NeoLog.i(NeoLog.ACTIVATE) { "setDeviceOwner 调用完成（两参）" }
            return
        }
        NeoLog.w(NeoLog.ACTIVATE) {
            "setDeviceOwner 两参失败：${twoArg.exceptionOrNull()?.let { rootCause(it) }}"
        }

        // 三参：Android 13
        val threeArg = runCatching {
            DevicePolicyManager::class.java.getMethod(
                "setDeviceOwner",
                ComponentName::class.java,
                String::class.java,
                Integer.TYPE
            ).invoke(dpm, admin, null, userId)
        }
        if (threeArg.isSuccess) {
            NeoLog.i(NeoLog.ACTIVATE) { "setDeviceOwner 调用完成（三参）" }
            return
        }
        NeoLog.w(NeoLog.ACTIVATE) {
            "setDeviceOwner 三参失败：${threeArg.exceptionOrNull()?.let { rootCause(it) }}"
        }
    }
}
