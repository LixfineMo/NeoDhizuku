package com.neodhizuku.more.ui.page.settings.experimental

import android.content.pm.PackageManager
import com.neodhizuku.more.data.common.util.ShizukuShell
import com.neodhizuku.more.data.common.util.checkShizukuWorked
import com.neodhizuku.more.data.common.util.requireShizukuPermissionGranted
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * Shizuku 在这里只承担「读状态」，不承担提权或内核写入。
 *
 * shell(uid 2000) 对 /sys/fs/selinux/enforce 只有读权限，写需要 root 或特定 SELinux 域；
 * 它也读不了内核内存、构造不了带 IPv4 选项的报文。UAF 竞态与内核写仍由
 * VpnService 的 tun 通道 + native 链完成，这里只把 untrusted_app 读不到的
 * selinux / /proc 状态如实显示出来，避免「unreadable」被误当成「enforcing」。
 */
object Cve98432Shell {

    data class Status(
        val enforce: String?,
        val version: String?,
        val kptr: String?,
        val configs: List<String>
    )

    private const val REQUEST_TIMEOUT = 15_000L
    private const val PROBE_TIMEOUT = 12_000L

    private const val PROBE_SCRIPT = """
echo __ENFORCE__
getenforce 2>/dev/null || cat /sys/fs/selinux/enforce 2>/dev/null
echo __VERSION__
cat /proc/version 2>/dev/null
echo __KPTR__
cat /proc/sys/kernel/kptr_restrict 2>/dev/null
echo __CONFIG__
(zcat /proc/config.gz 2>/dev/null || cat /proc/config.gz 2>/dev/null) | grep -E '^(CONFIG_KALLSYMS|CONFIG_SLAB_FREELIST_HARDENED|CONFIG_RANDOMIZE_BASE)='
echo __END__
"""

    /** 是否已有可用的 shell 通道；不弹任何授权框。 */
    fun ready(): Boolean = runCatching {
        checkShizukuWorked() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * 主动申请一次 Shizuku 权限，只在「环境准备」时调用。
     * 已授权直接返回；未授权带超时，避免授权框不回调时把自检挂死。
     */
    suspend fun request(): Boolean {
        if (ready()) return true
        // Shizuku / Sui 没起来时不要进权限请求，否则 callbackFlow 会白等一个超时。
        if (!runCatching { checkShizukuWorked() }.getOrDefault(false)) return false
        return runCatching {
            withTimeout(REQUEST_TIMEOUT) {
                requireShizukuPermissionGranted { true }
            }
        }.getOrDefault(false)
    }

    /** 一次 shell 调用拿全部状态；通道不可用或命令失败返回 null。 */
    suspend fun probe(): Status? {
        if (!ready()) return null
        val result = runCatching {
            ShizukuShell.exec(PROBE_SCRIPT.trimIndent(), PROBE_TIMEOUT)
        }.getOrNull() ?: return null
        if (!result.isSuccess && result.output.isBlank()) return null

        val enforce = mutableListOf<String>()
        val version = mutableListOf<String>()
        val kptr = mutableListOf<String>()
        val configs = mutableListOf<String>()
        var section = ""
        for (raw in result.output.lines()) {
            val line = raw.trim()
            when (line) {
                "__ENFORCE__" -> { section = "e"; continue }
                "__VERSION__" -> { section = "v"; continue }
                "__KPTR__" -> { section = "k"; continue }
                "__CONFIG__" -> { section = "c"; continue }
                "__END__" -> break
            }
            if (line.isEmpty()) continue
            when (section) {
                "e" -> enforce += line
                "v" -> version += line
                "k" -> kptr += line
                "c" -> configs += line
            }
        }

        val state = enforce.firstOrNull()?.let { raw ->
            when {
                raw.equals("permissive", true) || raw == "0" -> "permissive"
                raw.equals("enforcing", true) || raw == "1" -> "enforcing"
                else -> null
            }
        }
        return Status(
            enforce = state,
            version = version.firstOrNull()?.take(160),
            kptr = kptr.firstOrNull(),
            configs = configs.filter { it.startsWith("CONFIG_") }
        )
    }
}
