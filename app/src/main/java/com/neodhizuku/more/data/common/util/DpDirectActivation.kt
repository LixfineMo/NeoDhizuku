package com.neodhizuku.more.data.common.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.server.DhizukuState

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 免删账号激活设备所有者。
 * 走的是 `DevicePolicyManagerService#setDeviceOwner` 的**非 ADB 分支**：
 * 用应用自身 uid 直接调接口，此时 `isAdb()` 为 false，系统只校验
 * `hasUserSetupCompleted`，**完全不读账户列表**。
 * 因此不需要删除账号 —— 这是与原版 Dhizuku（走 ADB，`dpm set-device-owner`
 * 三道闸全读、必须清账户）的根本区别。
 * 前置条件：
 *  - Android 13（API 33）及以上：`DEVICE_POLICY_MANAGEMENT` Role 自该版本引入
 *  - Shizuku 或 root：用于执行 `cmd role` 与 `settings put secure`
 * 链路：
 *  ① shell: cmd role set-bypassing-role-qualification true
 *  ② shell: cmd role add-role-holder <DP_ROLE> <pkg> --user 0
 *     → 获得 MANAGE_PROFILE_AND_DEVICE_OWNERS / MANAGE_DEVICE_ADMINS
 *  ③ shell: settings put secure user_setup_complete 0
 *  ④ 应用自身: forceUpdateUserSetupComplete(0)   ← 把③的值搬进 DPM 内存态
 *  ⑤ 应用自身: setActiveAdmin(self, true)
 *  ⑥ 应用自身: setDeviceOwner(...)
 *  ⑦ shell: settings put secure user_setup_complete 1   ← 必须恢复
 * ③与④必须配对：只做③不做④无效，内存态不变，闸仍关闭。
 * 每个对外入口有两条实现路径，由实验性开关 [isFixEnabled] 选择，默认关闭。
 */
object DpDirectActivation : KoinComponent {

    /** `DEVICE_POLICY_MANAGEMENT` Role，自 Android 13（API 33）引入。 */
    const val ROLE_DEVICE_POLICY_MANAGEMENT = "android.app.role.DEVICE_POLICY_MANAGEMENT"

    private const val PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS =
        "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS"

    /** ② 同时会 grant 的第二个权限。 */
    private const val PERM_MANAGE_DEVICE_ADMINS =
        "android.permission.MANAGE_DEVICE_ADMINS"

    /** `Settings.Secure.USER_SETUP_COMPLETE` 的字面量（该常量 @hide，SDK 里没有）。 */
    const val SETTING_USER_SETUP_COMPLETE = "user_setup_complete"

    /** 回读标记，用于从 shell 输出中解析真实值。 */
    const val SETUP_READBACK_TAG = "NEO_SETUP_NOW="

    /** 低于此版本无法使用：DP Role 未引入。 */
    const val MIN_SDK = Build.VERSION_CODES.TIRAMISU

    // 实验性开关

    private val settingsRepo: SettingsRepo by inject()

    /**
     * 是否走修复后的分支。
     * 默认关闭：修复项涉及系统接口的运行期行为差异，需要真机验证，
     * 出问题时要能一键退回原逻辑对照。
     */
    fun isFixEnabled(): Boolean = settingsRepo.isExperimentalQuickActivateFix

    // 向导遗留状态
    // `user_setup_complete` 被置 0 后若没能恢复，设备会长期停留在
    // "未完成开机向导"状态（三键导航的 Home / 最近任务失灵）。
    // 这里用一个持久化标记把它记下来，供界面提示与入口判断使用。

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

    private const val KEY_TEMPORARY_OWNER = "temporary_owner"

    /**
     * 当前设备所有者是否通过免删模式取得。
     * 免删模式取得的设备所有者与 ADB / Shizuku 取得的**能力完全相同**，
     * 这里只用于界面上区分获取途径。内存缓存避免每次 compose 都读磁盘。
     */
    @Volatile
    private var temporaryCached: Boolean? = null

    fun isTemporaryCached(): Boolean = temporaryCached ?: false

    /** 读取持久化值并刷新缓存；一般在应用启动或激活完成后调用 */
    fun refreshTemporary(context: Context) {
        temporaryCached = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TEMPORARY_OWNER, false)
    }

    fun markTemporary(context: Context, temporary: Boolean) {
        temporaryCached = temporary
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TEMPORARY_OWNER, temporary)
            .apply()
        NeoLog.i(NeoLog.ACTIVATE) { "temporaryOwner -> $temporary" }
    }

    /** 权限 grant 轮询次数与间隔 */
    private const val PERM_WAIT_TIMES = 10
    private const val PERM_WAIT_INTERVAL_MILLIS = 300L

    /** shell 等待上限，避免 `waitFor()` 卡死整条链路 */
    private const val SHELL_WAIT_TIMEOUT_MILLIS = 15_000L

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_SDK

    /** 执行结果 */
    data class StepResult(
        val success: Boolean,
        val message: String,
        val detail: String = ""
    )

    /** `setDeviceOwner` 的调用结果，用于区分"被拒"与"接口不存在" */
    private enum class InvokeStatus {
        OK,
        /** 方法根本不存在，未调进系统 */
        METHOD_UNAVAILABLE,
        /** 调进去了，被系统拒绝 */
        REJECTED
    }

    private data class InvokeResult(val status: InvokeStatus, val note: String = "")

    // 命令构造

    /** ①+②：授予 DP Role（唯一能拿到后续权限的通道）。 */
    fun buildGrantRoleScript(pkg: String): String =
        if (isFixEnabled()) buildGrantRoleScriptV2(pkg) else buildGrantRoleScriptV1(pkg)

    private fun buildGrantRoleScriptV1(pkg: String): String = buildRoleScript(pkg, false)

    private fun buildGrantRoleScriptV2(pkg: String): String = buildRoleScript(pkg, true)

    /**
     * 授予 DP Role 的 shell 脚本。三种 `add-role-holder` 写法依次尝试，
     * 每次 add 后回读 `get-role-holders`，以回读命中为准（部分 ROM 退出码为 0
     * 却静默失败）。输出 NEO_* 诊断行便于定位被拒的环节。
     * bypass 是全局开关，结束时无条件复位；withTrap 时用 trap 兜底中断场景。
     */
    private fun buildRoleScript(pkg: String, withTrap: Boolean): String {
        val body = """ROLE='android.app.role.DEVICE_POLICY_MANAGEMENT'
PKG='__PKG__'
echo "NEO_SDK=${'$'}(getprop ro.build.version.sdk)"
echo "NEO_HELP=${'$'}(cmd role help 2>&1 | head -n 14 | tr '\n' ' ')"
BOUT=${'$'}(cmd role set-bypassing-role-qualification true 2>&1); BRC=${'$'}?
echo "NEO_BYPASS_RC=${'$'}BRC OUT=${'$'}BOUT"
sleep 1
RC=255
OUT=''
SUCCESS=''
TRIED=''
add() {
  LABEL=${'$'}1; shift
  O=${'$'}(cmd role "${'$'}@" 2>&1); R=${'$'}?
  H=${'$'}(cmd role get-role-holders "${'$'}ROLE" 2>&1)
  TRIED="${'$'}TRIED | ${'$'}LABEL rc=${'$'}R out=${'$'}O holders=${'$'}H"
  if [ "${'$'}R" -eq 0 ] && [ -z "${'$'}SUCCESS" ]; then SUCCESS=1; RC=0; OUT="${'$'}O"; fi
  if printf '%s\n' "${'$'}H" | grep -qF "${'$'}PKG"; then SUCCESS=1; RC=0; OUT="${'$'}O"; fi
}
add official add-role-holder "${'$'}ROLE" "${'$'}PKG"
add userfront add-role-holder --user 0 "${'$'}ROLE" "${'$'}PKG"
add userback add-role-holder "${'$'}ROLE" "${'$'}PKG" --user 0
echo "NEO_TRIED=${'$'}TRIED"
echo "NEO_HOLDERS=${'$'}(cmd role get-role-holders "${'$'}ROLE" 2>&1)"
cmd role set-bypassing-role-qualification false >/dev/null 2>&1
echo "RC=${'$'}RC OUT=${'$'}OUT"
""".trimIndent().replace("__PKG__", pkg)
        return if (withTrap) {
            "trap 'cmd role set-bypassing-role-qualification false >/dev/null 2>&1' " +
                "EXIT INT TERM\n" + body
        } else body
    }

    /**
     * ③ / ⑦：写入 `user_setup_complete` 并回读确认。
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
     * 授予命令退出码为 0 并不等于角色真的生效 ——
     * 部分 ROM 上 `add-role-holder` 静默失败。这里回读用于确认。
     */
    fun buildGetRoleHoldersScript(role: String = ROLE_DEVICE_POLICY_MANAGEMENT): String =
        "cmd role get-role-holders $role 2>&1"

    /** 从回读输出中判断是否包含指定包名。 */
    fun parseRoleHolders(output: String?, pkg: String): Boolean =
        if (isFixEnabled()) parseRoleHoldersV2(output, pkg) else parseRoleHoldersV1(output, pkg)

    private fun parseRoleHoldersV1(output: String?, pkg: String): Boolean =
        output?.contains(pkg) == true

    /** 按行精确匹配，避免 ROM 输出混入其它信息或包名恰为目标子串时误判。 */
    private fun parseRoleHoldersV2(output: String?, pkg: String): Boolean =
        output?.lineSequence()
            ?.map { it.trim() }
            ?.any { line -> line == pkg } == true

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
    fun buildManualCommands(pkg: String, admin: ComponentName): String =
        if (isFixEnabled()) buildManualCommandsV2(pkg, admin)
        else buildManualCommandsV1(pkg, admin)

    private fun buildManualCommandsV1(pkg: String, admin: ComponentName): String = buildString {
        appendLine("# 1. 跳过角色资格校验（必须单独执行，别和下一步挤在同一行）")
        appendLine("adb shell cmd role set-bypassing-role-qualification true")
        appendLine()
        appendLine("# 2. 授予设备管理角色（TestDPC 官方写法，不带 --user）")
        appendLine("adb shell cmd role add-role-holder $ROLE_DEVICE_POLICY_MANAGEMENT $pkg")
        appendLine("# 若上条报 Unknown option，再试带 --user 的写法：")
        appendLine("adb shell cmd role add-role-holder --user 0 $ROLE_DEVICE_POLICY_MANAGEMENT $pkg")
        appendLine("# 回读确认（输出里应能看到本应用包名）：")
        appendLine("adb shell cmd role get-role-holders $ROLE_DEVICE_POLICY_MANAGEMENT")
        appendLine()
        appendLine("# 3. 临时标记为未完成开机向导")
        appendLine("adb shell settings put secure $SETTING_USER_SETUP_COMPLETE 0")
        appendLine()
        appendLine("# 4. 回到应用点第 2 步「尝试激活」（应用自身调用，不走 ADB 分支）")
        appendLine("#    命令行 dpm set-device-owner 走 ADB 分支，仍要求设备无任何账号：")
        appendLine("adb shell dpm set-device-owner ${admin.flattenToShortString()}")
        appendLine()
        appendLine("# 5. 务必恢复")
        appendLine("adb shell settings put secure $SETTING_USER_SETUP_COMPLETE 1")
        appendLine("adb shell cmd role set-bypassing-role-qualification false")
        appendLine()
        appendLine("# 注意：该角色不持久，重启后失效，需要重新执行一次第 1~2 步。")
    }

    /** 额外标出 ADB 分支与免账号的冲突。 */
    private fun buildManualCommandsV2(pkg: String, admin: ComponentName): String = buildString {
        append(buildManualCommandsV1(pkg, admin))
        appendLine()
        appendLine("# 补充：第 4 步的 dpm 命令走 ADB 分支（isAdb=true，三道闸全读），")
        appendLine("# 仍要求设备上没有任何账号，与「免删账号」冲突。")
        appendLine("# 免账号只能在应用内通过前两步 + 应用自身调用完成，命令行做不到。")
    }


    /**
     * 打开向导闸：授予 DP Role 并把 `user_setup_complete` 置 0。
     * 这两步都必须借 shell 身份：`cmd role` 与 `settings put secure`
     * 都不是普通应用能直接调用的。
     */
    suspend fun openSetupGate(context: Context): StepResult =
        if (isFixEnabled()) openSetupGateV2(context) else openSetupGateV1(context)

    private suspend fun openSetupGateV1(context: Context): StepResult {
        if (!isSupported()) {
            return StepResult(false, "当前系统版本不支持", "需要 Android 13（API 33）及以上")
        }
        val pkg = context.packageName

        // 已经是 DO 时不必再要角色：部分 ROM 在已有所有者的设备上会直接
        // 拒绝授予 DM Role，否则用户会看到一个毫无意义的「角色未生效」。
        val earlyDpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (earlyDpm != null && runCatching { earlyDpm.isDeviceOwnerApp(pkg) }.getOrDefault(false)) {
            return StepResult(
                true,
                "本应用已是设备所有者",
                "无需再授予设备管理角色，可直接退出或进行第 2 步复查。"
            )
        }

        val grant = ShizukuShell.exec(buildGrantRoleScriptV1(pkg))
        NeoLog.i(NeoLog.ACTIVATE) { "DP Role 授予：${grant.output} ${grant.error}" }

        val holders = ShizukuShell.exec(buildGetRoleHoldersScript())
        val isHolder = parseRoleHoldersV1(holders.output, pkg)
        NeoLog.i(NeoLog.ACTIVATE) {
            "DP Role 回读：isHolder=$isHolder out=${holders.output}"
        }

        // 权限 grant 与角色授予之间可能有延迟，轮询等待而不是一次性判定
        val hasPerm = waitForPermissionV1(context)
        NeoLog.i(NeoLog.ACTIVATE) { "DP Role 权限：granted=$hasPerm" }

        if (!hasPerm) {
            val declared = isPermissionDeclared(context, PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS)
            val detail = buildString {
                appendLine("系统：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})，用户 ${Process.myUid() / 100000}")
                appendLine("授权输出：${grant.output.ifBlank { "（空）" }}")
                appendLine("授权错误：${grant.error.ifBlank { "（空）" }}")
                appendLine("持有者回读：isHolder=$isHolder out=${holders.output.ifBlank { "（空）" }}")
                appendLine("清单已声明该权限：$declared")
                appendLine()
                if (isHolder) {
                    appendLine("角色已授予，但权限尚未同步到本应用。")
                    appendLine("等几秒后重试第 1 步，或直接进行第 2 步。")
                } else {
                    appendLine("系统在授予 DEVICE_POLICY_MANAGEMENT 角色时返回失败。")
                    appendLine("若上方 NEO_BYPASS_RC 非 0、或 NEO_HELP 里没有")
                    appendLine("set-bypassing-role-qualification，说明这台 ROM")
                    appendLine("不支持跳过角色资格校验，免删激活在该机不可用。")
                    appendLine("可先用「查看手动命令」在电脑 adb 里跑一遍确认，")
                    appendLine("并在 设置-实验性功能 打开「免删激活修复」后重试。")
                }
                if (!declared) {
                    appendLine("→ 另需在 AndroidManifest.xml 声明该权限，否则系统不会 grant。")
                }
            }
            return StepResult(false, "系统未授予设备管理角色", detail.trimEnd())
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

    private suspend fun openSetupGateV2(context: Context): StepResult {
        if (!isSupported()) {
            return StepResult(false, "当前系统版本不支持", "需要 Android 13（API 33）及以上")
        }
        val pkg = context.packageName

        val earlyDpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (earlyDpm != null && runCatching { earlyDpm.isDeviceOwnerApp(pkg) }.getOrDefault(false)) {
            return StepResult(
                true,
                "本应用已是设备所有者",
                "无需再授予设备管理角色，可直接退出或进行第 2 步复查。"
            )
        }

        val grant = ShizukuShell.exec(
            buildGrantRoleScriptV2(pkg), SHELL_WAIT_TIMEOUT_MILLIS
        )
        NeoLog.i(NeoLog.ACTIVATE) { "DP Role 授予：${grant.output} ${grant.error}" }

        val holders = ShizukuShell.exec(buildGetRoleHoldersScript(), SHELL_WAIT_TIMEOUT_MILLIS)
        val isHolder = parseRoleHoldersV2(holders.output, pkg)
        NeoLog.i(NeoLog.ACTIVATE) {
            "DP Role 回读：isHolder=$isHolder out=${holders.output}"
        }

        val perm = waitForPermissionV2(context)
        NeoLog.i(NeoLog.ACTIVATE) {
            "DP Role 权限：owners=${perm.first} admins=${perm.second}"
        }

        if (!perm.first || !perm.second) {
            val declaredOwners = isPermissionDeclared(context, PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS)
            val declaredAdmins = isPermissionDeclared(context, PERM_MANAGE_DEVICE_ADMINS)
            val detail = buildString {
                appendLine("系统：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})，用户 ${Process.myUid() / 100000}")
                appendLine("授权输出：${grant.output.ifBlank { "（空）" }}")
                appendLine("授权错误：${grant.error.ifBlank { "（空）" }}")
                appendLine("持有者回读：isHolder=$isHolder out=${holders.output.ifBlank { "（空）" }}")
                appendLine("MANAGE_PROFILE_AND_DEVICE_OWNERS：granted=${perm.first} 已声明=$declaredOwners")
                appendLine("MANAGE_DEVICE_ADMINS：granted=${perm.second} 已声明=$declaredAdmins")
                appendLine()
                if (isHolder) {
                    appendLine("角色已授予，但权限尚未同步到本应用。")
                    appendLine("等几秒后重试第 1 步，或直接进行第 2 步。")
                } else {
                    appendLine("系统在授予 DEVICE_POLICY_MANAGEMENT 角色时返回失败。")
                    appendLine("若上方 NEO_BYPASS_RC 非 0、或 NEO_HELP 里没有")
                    appendLine("set-bypassing-role-qualification，说明这台 ROM")
                    appendLine("不支持跳过角色资格校验，免删激活在该机不可用。")
                    appendLine("可先用「查看手动命令」在电脑 adb 里跑一遍确认。")
                }
                if (!declaredOwners || !declaredAdmins) {
                    appendLine("→ 另需在 AndroidManifest.xml 声明缺失的权限，否则系统不会 grant。")
                }
            }
            return StepResult(false, "系统未授予设备管理角色", detail.trimEnd())
        }

        // 区分"写入失败"与"回读失败"。
        val set = ShizukuShell.exec(buildSetSetupCompleteScript(0), SHELL_WAIT_TIMEOUT_MILLIS)
        val now = parseSetupComplete(set.output)
        if (now == null) {
            return StepResult(
                false,
                "回读失败，无法确认向导状态",
                "未能从输出中解析出 $SETTING_USER_SETUP_COMPLETE 的值。\n" +
                    "输出：${set.output}\n错误：${set.error}"
            )
        }
        if (now != 0) {
            return StepResult(
                false,
                "写入未生效，向导状态仍为 $now",
                "settings put 已执行但回读不是 0。\n输出：${set.output}\n错误：${set.error}"
            )
        }
        markPendingRestore(context, true)
        return StepResult(true, "向导闸已打开", "角色已授予，user_setup_complete=0")
    }

    /**
     * 尝试激活：同步内存态 → setActiveAdmin → setDeviceOwner。
     * 无论成败都会尝试恢复 `user_setup_complete=1`，
     * 避免中途失败把设备留在"未完成向导"状态（三键导航的 Home/最近任务会失灵）。
     */
    suspend fun activate(context: Context): StepResult =
        if (isFixEnabled()) activateV2(context) else activateV1(context)

    private suspend fun activateV1(context: Context): StepResult {
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

            val adminOk = invokeSetActiveAdminV1(dpm, admin)
            log += "setActiveAdmin: ${if (adminOk) "成功" else "失败"}"
            if (!adminOk) {
                return StepResult(false, "无法注册为设备管理员", log.joinToString("\n"))
            }

            // ⑥ 反射返回 void，必须复查实际状态
            invokeSetDeviceOwnerV1(dpm, admin)
            val nowOwner = runCatching { dpm.isDeviceOwnerApp(pkg) }.getOrDefault(false)
            log += "isDeviceOwnerApp 复查：$nowOwner"

            return if (nowOwner) {
                // 免删模式取得的所有者，界面上标注为"临时"
                markTemporary(context, true)
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

    private suspend fun activateV2(context: Context): StepResult {
        if (!isSupported()) {
            return StepResult(false, "当前系统版本不支持", "需要 Android 13（API 33）及以上")
        }
        val pkg = context.packageName
        val admin = DhizukuState.admin
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return StepResult(false, "无法获取 DevicePolicyManager", "")

        val alreadyOwner = runCatching { dpm.isDeviceOwnerApp(pkg) }.getOrDefault(false)

        // 所有者已被撤销时清掉"临时"标记。
        if (!alreadyOwner && isTemporaryCached()) {
            NeoLog.i(NeoLog.ACTIVATE) { "所有者已不在本应用，清除临时标记" }
            markTemporary(context, false)
        }

        if (alreadyOwner) {
                // 若残留 pendingRestore，用户看到成功、设备却仍停在未完成向导态。
            if (isPendingRestore(context)) {
                val restored = restoreSetupComplete(context)
                DhizukuState.sync(context)
                return StepResult(
                    true,
                    "本应用已是设备所有者",
                    "此前遗留的向导状态已补做恢复：${restored.message}"
                )
            }
            return StepResult(true, "本应用已是设备所有者", "")
        }

        val log = mutableListOf<String>()
        var result = StepResult(false, "未知结果", "")
        try {
            // ④ 只是"把 Settings 的真实值搬进内存"，
            // 若当前值是 1，搬进去就是 1，闸仍关闭，⑥ 必被拒。
            // 所以这里先回读，不为 0 就自行补做 ③。
            val current = readSetupCompleteNow(context)
            log += "进入时 user_setup_complete=$current"

            if (current != 0) {
                val set = ShizukuShell.exec(
                    buildSetSetupCompleteScript(0), SHELL_WAIT_TIMEOUT_MILLIS
                )
                val now = parseSetupComplete(set.output)
                log += "补做③ 置 0 -> $now"
                if (now != 0) {
                    // 不用 return：早退也必须让 finally 里的恢复与提示合并生效
                    result = StepResult(
                        false,
                        "向导闸未打开",
                        (log + "输出：${set.output}" + "错误：${set.error}").joinToString("\n")
                    )
                } else {
                    markPendingRestore(context, true)
                    result = activateCore(context, dpm, admin, pkg, log)
                }
            } else {
                result = activateCore(context, dpm, admin, pkg, log)
            }
        } finally {
            val restored = restoreSetupComplete(context)
            NeoLog.i(NeoLog.ACTIVATE) { "恢复：${restored.success} ${restored.message}" }
            if (!restored.success) {
                result = result.copy(
                    detail = buildString {
                        append(result.detail)
                        if (result.detail.isNotBlank()) appendLine()
                        appendLine("【警告】向导状态未能确认恢复：${restored.message}")
                        appendLine("请手动执行第 3 步「手动恢复」。")
                    }.trimEnd()
                )
            }
            DhizukuState.sync(context)
        }
        return result
    }

    /** ④→⑤→⑥ 的主体，抽出来是为了让上面的失败分支也能共用。 */
    private fun activateCore(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName,
        pkg: String,
        log: MutableList<String>
    ): StepResult {
        // ④ 同步内存态。
        // userId 传死 0 在副用户/工作资料下会错，改用自身 uid 推导。
        val userId = Process.myUid() / 100000
        val setupOk = invokeForceUpdateUserSetupComplete(dpm, userId)
        log += "forceUpdateUserSetupComplete($userId): ${if (setupOk) "成功" else "失败（不阻断）"}"

        // ⑤ 运行期扫描候选签名
        val adminResult = invokeSetActiveAdminV2(dpm, admin, userId)
        log += "setActiveAdmin: ${adminResult.status} ${adminResult.note}"
        if (adminResult.status != InvokeStatus.OK) {
            return StepResult(
                false,
                if (adminResult.status == InvokeStatus.METHOD_UNAVAILABLE)
                    "当前系统没有可用的 setActiveAdmin 接口"
                else "无法注册为设备管理员",
                log.joinToString("\n")
            )
        }

        // ⑥ 反射返回 void，必须复查实际状态
        val ownerResult = invokeSetDeviceOwnerV2(dpm, admin, userId)
        log += "setDeviceOwner: ${ownerResult.status} ${ownerResult.note}"
        val nowOwner = runCatching { dpm.isDeviceOwnerApp(pkg) }.getOrDefault(false)
        log += "isDeviceOwnerApp 复查：$nowOwner"

        if (nowOwner) {
            markTemporary(context, true)
            return StepResult(true, "已激活设备所有者", log.joinToString("\n"))
        }
        // 把"被系统拒绝"与"接口不存在"分开报，
        return if (ownerResult.status == InvokeStatus.METHOD_UNAVAILABLE) {
            StepResult(
                false,
                "当前系统接口不兼容",
                (log + "未能找到 setDeviceOwner 的可用签名，本次未真正调用系统。")
                    .joinToString("\n")
            )
        } else {
            StepResult(false, "系统拒绝了激活请求", log.joinToString("\n"))
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

    // 工具

    /** 只读回读当前的 `user_setup_complete`。 */
    private suspend fun readSetupCompleteNow(context: Context): Int? {
        val out = ShizukuShell.exec(buildReadSetupCompleteScript(), SHELL_WAIT_TIMEOUT_MILLIS)
        return parseSetupComplete(out.output)
    }

    /** ⑦：写回 1 并回读确认。 */
    private suspend fun restoreSetupComplete(context: Context): StepResult {
        val set = ShizukuShell.exec(buildSetSetupCompleteScript(1), SHELL_WAIT_TIMEOUT_MILLIS)
        val now = parseSetupComplete(set.output)
        markPendingRestore(context, now != 1)
        return if (now == 1) {
            StepResult(true, "user_setup_complete=1", "")
        } else {
            StepResult(
                false,
                "回读值：$now",
                "输出：${set.output}\n错误：${set.error}"
            )
        }
    }

    // 反射封装（全部 try/catch，绝不抛出）

    /**
     * 取出异常的根本原因。
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
    private suspend fun waitForPermissionV1(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            repeat(PERM_WAIT_TIMES) { i ->
                val granted = runCatching {
                    context.checkSelfPermission(PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS) ==
                        PackageManager.PERMISSION_GRANTED
                }.getOrDefault(false)
                if (granted) return@withContext true
                if (i < PERM_WAIT_TIMES - 1) delay(PERM_WAIT_INTERVAL_MILLIS)
            }
            false
        }

    /**
     * 在 IO 上用 `delay` 同时等待两个权限，不阻塞主线程。
     * @return 先 [PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS] 后 [PERM_MANAGE_DEVICE_ADMINS]
     */
    private suspend fun waitForPermissionV2(context: Context): Pair<Boolean, Boolean> =
        withContext(Dispatchers.IO) {
            var owners = false
            var admins = false
            repeat(PERM_WAIT_TIMES) { i ->
                owners = isGranted(context, PERM_MANAGE_PROFILE_AND_DEVICE_OWNERS)
                admins = isGranted(context, PERM_MANAGE_DEVICE_ADMINS)
                if (owners && admins) return@repeat
                if (i < PERM_WAIT_TIMES - 1) delay(PERM_WAIT_INTERVAL_MILLIS)
            }
            owners to admins
        }

    private fun isGranted(context: Context, permission: String): Boolean =
        runCatching {
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /** 该权限是否在清单中声明。用于把"未声明"和"未授予"区分开。 */
    private fun isPermissionDeclared(context: Context, permission: String): Boolean =
        runCatching {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            info.requestedPermissions?.contains(permission) == true
        }.getOrDefault(false)

    /**
     * `DevicePolicyManager#forceUpdateUserSetupComplete(int)`。
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
    private fun invokeSetActiveAdminV1(dpm: DevicePolicyManager, admin: ComponentName): Boolean =
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
     * 运行期扫描 `setActiveAdmin` 的全部重载：系统版本间签名会漂移，
     * 写死某一套会直接 NoSuchMethodException。按参数位置填值，
     * 多出来的尾部参数按类型给默认值。
     */
    private fun invokeSetActiveAdminV2(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        userId: Int
    ): InvokeResult {
        val methods = runCatching {
            DevicePolicyManager::class.java.methods.filter { it.name == "setActiveAdmin" }
        }.getOrDefault(emptyList())

        if (methods.isEmpty()) {
            return InvokeResult(InvokeStatus.METHOD_UNAVAILABLE, "未找到 setActiveAdmin")
        }
        NeoLog.i(NeoLog.ACTIVATE) { "setActiveAdmin 候选 ${methods.size} 个" }

        val errors = mutableListOf<String>()
        methods.forEach { m ->
            val args = buildArgs(m.parameterTypes, admin, userId) ?: return@forEach
            val r = runCatching {
                m.isAccessible = true
                m.invoke(dpm, *args)
            }
            if (r.isSuccess) {
                NeoLog.i(NeoLog.ACTIVATE) {
                    "setActiveAdmin 成功：(${m.parameterTypes.joinToString { it.simpleName }})"
                }
                return InvokeResult(InvokeStatus.OK, "签名 ${m.parameterTypes.size} 参")
            }
            errors += "${m.parameterTypes.joinToString { it.simpleName }}: " +
                rootCause(r.exceptionOrNull()!!)
        }
        NeoLog.w(NeoLog.ACTIVATE) { "setActiveAdmin 全部候选失败：$errors" }
        return InvokeResult(InvokeStatus.REJECTED, errors.firstOrNull() ?: "")
    }

    /**
     * 按参数类型填实参。
     * 首位固定是目标 [admin]，其余按类型给默认值；
     * 出现无法填充的类型时返回 null（跳过该候选）。
     */
    private fun buildArgs(
        types: Array<Class<*>>,
        admin: ComponentName,
        userId: Int
    ): Array<Any?>? {
        if (types.isEmpty()) return null
        if (types[0] != ComponentName::class.java) return null
        for (i in 1 until types.size) {
            when (types[i]) {
                Boolean::class.java, Boolean::class.javaObjectType -> Unit
                Int::class.java, Int::class.javaObjectType -> Unit
                String::class.java -> Unit
                else -> return null
            }
        }
        return Array(types.size) { i ->
            when (i) {
                0 -> admin
                else -> when (types[i]) {
                    Boolean::class.java, Boolean::class.javaObjectType -> true
                    Int::class.java, Int::class.javaObjectType -> userId
                    else -> null
                }
            }
        }
    }

    /**
     * `setDeviceOwner`。签名在 Android 14 处发生过变更：
     *  - A13: `(ComponentName, String ownerName, int userId)`
     *  - A14+: `(ComponentName, int userId)`
     * 写死三参数在 A14+ 会直接 NoSuchMethodException，
     * 所以按 SDK 优先选，失败再回退另一签名。
     */
    private fun invokeSetDeviceOwnerV1(dpm: DevicePolicyManager, admin: ComponentName) {
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

    /**
     * 扫描候选签名并返回状态，不吞掉失败。
     * 所有候选都是 `NoSuchMethodException` → 接口不存在；
     * 至少有一个调进去了但抛异常 → 被系统拒绝。
     */
    private fun invokeSetDeviceOwnerV2(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        userId: Int
    ): InvokeResult {
        val methods = runCatching {
            DevicePolicyManager::class.java.methods
                .filter { it.name == "setDeviceOwner" }
                // 参数多的排在后面，优先试更像当前系统的两参版本
                .sortedBy { it.parameterTypes.size }
        }.getOrDefault(emptyList())

        if (methods.isEmpty()) {
            return InvokeResult(InvokeStatus.METHOD_UNAVAILABLE, "未找到 setDeviceOwner")
        }

        var invoked = false
        val errors = mutableListOf<String>()
        methods.forEach { m ->
            val args = buildArgs(m.parameterTypes, admin, userId) ?: return@forEach
            val r = runCatching {
                m.isAccessible = true
                m.invoke(dpm, *args)
            }
            // 走到这里说明方法存在且被真正调用了
            invoked = true
            if (r.isSuccess) {
                NeoLog.i(NeoLog.ACTIVATE) {
                    "setDeviceOwner 成功：(${m.parameterTypes.joinToString { it.simpleName }})"
                }
                return InvokeResult(InvokeStatus.OK, "签名 ${m.parameterTypes.size} 参")
            }
            errors += "${m.parameterTypes.joinToString { it.simpleName }}: " +
                rootCause(r.exceptionOrNull()!!)
        }

        return if (!invoked) {
            NeoLog.w(NeoLog.ACTIVATE) { "setDeviceOwner：没有可用签名" }
            InvokeResult(InvokeStatus.METHOD_UNAVAILABLE, "没有匹配的参数组合")
        } else {
            NeoLog.w(NeoLog.ACTIVATE) { "setDeviceOwner 被系统拒绝：$errors" }
            InvokeResult(InvokeStatus.REJECTED, errors.firstOrNull() ?: "")
        }
    }
}
