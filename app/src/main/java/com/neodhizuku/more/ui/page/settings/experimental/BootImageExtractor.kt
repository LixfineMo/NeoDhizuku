package com.neodhizuku.more.ui.page.settings.experimental

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 从用户导入的 boot.img 还原内核内嵌 kallsyms，产出候选 profile。
 *
 * 解析流程移植自 GhostLock tools/extract_rs：boot 头 → gzip / LZ4 legacy
 * 解压 → arm64 Image → 256 项 token table → markers / names / offsets →
 * 相对 _text 的符号偏移。结构体字段沿用 kernel_profiles 的分版本默认值。
 */
object BootImageExtractor {

    private const val MAX_IMAGE = 64 * 1024 * 1024
    private const val IO_CHUNK = 64 * 1024
    private const val ALIGN = 8L
    private const val TOKEN_COUNT = 256
    private const val MARKER_WINDOW = 4 * 1024 * 1024
    private const val NAME_TAIL_SEARCH = 0x40000
    private const val MAX_TOKEN_TABLES = 8
    private const val MAX_NAME_BLOCKS = 8
    private const val SYMBOL_TYPES = "aAbBcCdDeEfFgGiInNpPrRsStTuUvVwW?-"
    private const val KERNEL_VERSION = "Linux version "
    private val PAGE_SIZES = intArrayOf(4096, 2048, 8192, 16384, 65536)
    private val LZ4_MAGIC = byteArrayOf(0x02.toByte(), 0x21.toByte(), 0x4c.toByte(), 0x18.toByte())
    private val GZIP_MAGIC = byteArrayOf(0x1f.toByte(), 0x8b.toByte())
    private val DIGIT_NEEDLE = ByteArray(20) { i ->
        if (i % 2 == 0) (0x30 + i / 2).toByte() else 0.toByte()
    }

    data class Result(
        val profile: KernelProfile?,
        val imageSize: Long,
        val lines: List<String>,
        val version: String? = null,
        /** 某一段失败但前面的信息仍然有效，不是整体失败。 */
        val partial: Boolean = false
    )

    private data class TokenTable(val tokens: Array<ByteArray>, val tableOffset: Int, val indexOffset: Int)
    private data class NameBlock(
        val numSymsOffset: Int,
        val namesOffset: Int,
        val markersOffset: Int,
        val kinds: CharArray,
        val names: Array<String>
    )
    private data class Kallsyms(val imageSize: Long, val symbols: Map<String, Long>, val kinds: Map<String, Char>, val count: Int)

    /**
     * 统一数据访问层。boot.img 留在堆内数组，解压后的内核映像放在 direct buffer：
     * Java heap 里只有 ByteBuffer 壳，不再产生 4/8/16/32/64MB 的 LOS 大对象。
     */
    private interface KernelImage {
        val size: Int
        fun u8(o: Int): Int
        fun read(dst: ByteArray, dstOff: Int, srcOff: Int, len: Int)
        fun slice(from: Int, to: Int): ByteArray
        fun u16(o: Int): Int = u8(o) or (u8(o + 1) shl 8)
        fun u32(o: Int): Long = u8(o).toLong() or (u8(o + 1).toLong() shl 8) or
            (u8(o + 2).toLong() shl 16) or (u8(o + 3).toLong() shl 24)
        fun u64(o: Int): Long = u32(o) or (u32(o + 4) shl 32)
        fun i32(o: Int): Int = u32(o).toInt()
        fun close() {}
    }

    /** boot.img 主路径：direct buffer 视图，不把整份镜像搬回 dalvik heap。 */
    private class BytesImage(private val b: BootBytes) : KernelImage {
        override val size get() = b.size
        override fun u8(o: Int): Int = b[o].toInt() and 0xff
        override fun read(dst: ByteArray, dstOff: Int, srcOff: Int, len: Int) =
            b.read(dst, dstOff, srcOff, len)
        override fun slice(from: Int, to: Int): ByteArray = b.copyRange(from, to)
    }

    /** 未压缩内核：用偏移视图，不复制整段。 */
    private class OffsetImage(private val base: KernelImage, private val off: Int) : KernelImage {
        override val size get() = max(0, base.size - off)
        override fun u8(o: Int): Int = base.u8(off + o)
        override fun read(dst: ByteArray, dstOff: Int, srcOff: Int, len: Int) =
            base.read(dst, dstOff, off + srcOff, len)
        override fun slice(from: Int, to: Int): ByteArray = base.slice(off + from, off + to)
    }

    /**
     * 解压输出：稀疏临时文件 + 文件映射。
     * 旧的 allocateDirect 倍增策略在解压出 ~35MB 时会扩容到 64MB，与 boot.img
     * 那份 64MB direct buffer 一起顶满 growth limit；mmap 完全不算堆分配。
     */
    private class DirectImage(cacheDir: File, private val limit: Int) : KernelImage {
        private val file: File = File.createTempFile("cve98432-kernel-", ".img", cacheDir)
        private val buf: MappedByteBuffer = RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(limit.toLong())
            raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, limit.toLong())
        }

        init { file.deleteOnExit() }

        override var size = 0
            private set

        override fun u8(o: Int): Int = buf.get(o).toInt() and 0xff

        override fun read(dst: ByteArray, dstOff: Int, srcOff: Int, len: Int) {
            val d = buf.duplicate()
            d.position(srcOff)
            d.get(dst, dstOff, len)
        }

        override fun slice(from: Int, to: Int): ByteArray {
            val out = ByteArray(to - from)
            read(out, 0, from, out.size)
            return out
        }

        /** 新稀疏文件的映射区初始全 0，写零只需推进 size。 */
        fun writeZeros(n: Int): Boolean {
            if (size + n > limit) return false
            size += n
            return true
        }

        fun write(src: ByteArray, off: Int, len: Int): Boolean {
            if (size + len > limit) return false
            val d = buf.duplicate()
            d.position(size)
            d.put(src, off, len)
            size += len
            return true
        }

        fun writeMatch(offset: Int, len: Int): Boolean {
            if (offset <= 0 || offset > size || size + len > limit) return false
            val from = size - offset
            for (i in 0 until len) buf.put(size + i, buf.get(from + i % offset))
            size += len
            return true
        }

        override fun close() {
            runCatching { file.delete() }
        }
    }

    /** 让 GZIPInputStream 直接读 KernelImage，不把压缩段复制成第二份大数组。 */
    private class ImageInput(private val img: KernelImage, private val start: Int) : InputStream() {
        private var pos = start
        override fun read(): Int = if (pos >= img.size) -1 else img.u8(pos++)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= img.size) return -1
            val n = min(len, img.size - pos)
            img.read(b, off, pos, n)
            pos += n
            return n
        }
    }

    fun extract(bytes: BootBytes, versionHint: String?, cacheDir: File): Result {
        val lines = mutableListOf<String>()
        lines += "boot read     : ${bytes.size} bytes (mmap, zero dalvik heap)"

        val raw: KernelImage = BytesImage(bytes)
        val start = findKernelStart(raw)
        if (start < 0) {
            lines += "kernel segment: NOT FOUND (no gzip / LZ4 / arm64 Image header at a known page size)"
            lines += "result        : FAILED (no usable kernel segment)"
            return Result(null, 0L, lines)
        }
        lines += "kernel segment: offset 0x${java.lang.Long.toHexString(start.toLong())}"

        val image = decompress(raw, start, lines, cacheDir)
        if (image == null) {
            lines += "kernel image  : DECOMPRESS FAILED"
            lines += "result        : PARTIAL (header info retained, later stages skipped)"
            return Result(null, 0L, lines, null, true)
        }
        try {
            if (image.size < 64) {
                lines += "kernel image  : DECOMPRESS FAILED or too small"
                lines += "result        : PARTIAL (header info retained, later stages skipped)"
                return Result(null, 0L, lines, null, true)
            }
            val arm64 = image.u8(0) == 0x4d && image.u8(1) == 0x5a &&
                image.u8(0x38) == 0x41 && image.u8(0x39) == 0x52 &&
                image.u8(0x3a) == 0x4d && image.u8(0x3b) == 0x64
            val imageSize = if (arm64) image.u64(0x10) else image.size.toLong()
            lines += "kernel image  : ${image.size} bytes (mmap file), arm64=${if (arm64) "yes" else "no"}, declared size=0x${java.lang.Long.toHexString(imageSize)}"

            // 压缩段里没有明文版本串；linux_banner 在解压后的 Image 的 .rodata 里。
            // 每一段独立兜底：任何一段抛异常或返回空，都只记 note 继续，
            // 不再让整批日志被外层 runCatching 吞成一行 ERROR。
            val version = runCatching { findVersionString(image) }
                .onFailure { lines += "kernel version: scan failed (${it.javaClass.simpleName}), continuing" }
                .getOrNull()
            if (version != null) lines += "kernel version (decompressed): $version"

            val kallsyms = runCatching { recoverKallsyms(image, imageSize, lines) }
                .onFailure { lines += "kallsyms      : recovery threw ${it.javaClass.simpleName}, continuing" }
                .getOrNull()
            if (kallsyms == null) {
                lines += "kallsyms      : NOT RECOVERED (see layout diagnostics above)"
                lines += "result        : PARTIAL (offsets unavailable; version / image info retained)"
                return Result(null, imageSize, lines, version, true)
            }
            lines += "kallsyms      : ${kallsyms.count} symbols, _text=0x${java.lang.Long.toHexString(kallsyms.symbols["_text"] ?: 0L)}"

            val text = kallsyms.symbols["_text"] ?: 0L
            val end = kallsyms.symbols["_end"] ?: 0L
            val selinuxAbs = kallsyms.symbols["selinux_state"]
                ?: kallsyms.symbols["selinux_enforcing"]
            // arm64 内核地址 0xffff_xxxx_xxxx_xxxx 作为有符号 Long 是负数，不能用 > 0 判断。
            if (text == 0L || selinuxAbs == null) {
                lines += "symbols       : MISSING _text or selinux_state / selinux_enforcing"
                lines += "result        : PARTIAL (symbol table recovered, required anchors missing)"
                return Result(null, imageSize, lines, version, true)
            }

            val effectiveVersion = version ?: versionHint
            val major = effectiveVersion?.substringBefore('.')?.toIntOrNull() ?: 6
            val base = KernelProfile.defaultsFor(major, effectiveVersion ?: "unknown")
            val anchors = kallsyms.symbols.entries
                .filter { (name, addr) ->
                    addr in text..end && kallsyms.kinds[name]?.let { it == 'T' || it == 't' || it == 'W' } == true
                }
                .map { (_, addr) -> addr - text }
                .distinct()
                .sorted()
                .let { list ->
                    if (list.size <= 96) list else (0 until 96).map { list[it * list.size / 96] }
                }

            val profile = base.copy(
                textBase = text,
                selinuxState = selinuxAbs - text,
                initTask = (kallsyms.symbols["init_task"] ?: text) - text,
                initCred = (kallsyms.symbols["init_cred"] ?: text) - text,
                textSymbols = anchors
            )
            lines += "profile       : candidate ready (${profile.missing().size} missing fields)"
            lines += "note          : struct offsets are per-branch defaults; cross-check with this exact build"
            return Result(profile, imageSize, lines, version)
        } finally {
            image.close()
        }
    }

    /** 兼容旧入口：复用 Engine 的单次读取，避免 BAOS + toByteArray 双份大数组。 */
    fun extract(context: Context, uri: Uri, versionHint: String?): Result {
        val bytes = Cve98432Engine.readBoot(context, uri)?.bytes
            ?: return Result(null, 0L, listOf("boot read     : FAILED (cannot open the selected document)"))
        return extract(bytes, versionHint, context.cacheDir)
    }

    private fun findKernelStart(b: KernelImage): Int {
        for (ps in PAGE_SIZES) {
            if (ps + 4 > b.size) continue
            if (matches(b, ps, GZIP_MAGIC)) return ps
            if (matches(b, ps, LZ4_MAGIC)) return ps
            if (b.u8(ps) == 0x4d && b.u8(ps + 1) == 0x5a &&
                ps + 0x3c <= b.size && b.u8(ps + 0x38) == 0x41 && b.u8(ps + 0x3b) == 0x64
            ) return ps
        }
        return -1
    }

    private fun matches(b: KernelImage, off: Int, magic: ByteArray): Boolean {
        if (off + magic.size > b.size) return false
        return magic.indices.all { b.u8(off + it) == (magic[it].toInt() and 0xff) }
    }

    private fun decompress(raw: KernelImage, start: Int, lines: MutableList<String>, cacheDir: File): KernelImage? {
        if (matches(raw, start, GZIP_MAGIC)) {
            return runCatching {
                val sink = DirectImage(cacheDir, MAX_IMAGE)
                val buf = ByteArray(IO_CHUNK)
                GZIPInputStream(ImageInput(raw, start)).use { gz ->
                    while (sink.size < MAX_IMAGE) {
                        val n = gz.read(buf, 0, buf.size)
                        if (n < 0) break
                        if (!sink.write(buf, 0, n)) break
                    }
                }
                sink
            }.getOrNull().also { lines += "decompress    : gzip (mmap file)" }
        }
        if (matches(raw, start, LZ4_MAGIC)) {
            return lz4Legacy(raw, start, cacheDir).also { lines += "decompress    : LZ4 legacy (mmap file)" }
        }
        lines += "decompress    : raw arm64 Image (offset view, zero copy)"
        return OffsetImage(raw, start)
    }

    /** GhostLock boot.rs::decompress_lz4_legacy_frame 的等价实现。 */
    private fun lz4Legacy(raw: KernelImage, start: Int, cacheDir: File): KernelImage? {
        var ip = start + 4
        val sink = DirectImage(cacheDir, MAX_IMAGE)
        while (ip + 4 <= raw.size && sink.size < MAX_IMAGE) {
            val blockLen = raw.u32(ip).toInt()
            ip += 4
            if (blockLen <= 0 || ip + blockLen > raw.size) break
            val before = sink.size
            if (!lz4Block(raw, ip, blockLen, sink)) return null
            ip += blockLen
            if (sink.size - before < 8 * 1024 * 1024) break
        }
        return sink.takeIf { it.size > 0x3c }
    }

    /** LZ4 block decoder：输出写进文件映射，匹配从已写内容原地取。 */
    private fun lz4Block(src: KernelImage, off: Int, len: Int, sink: DirectImage): Boolean {
        var ip = off
        val end = off + len
        while (ip < end) {
            val token = src.u8(ip++)
            var lit = token shr 4
            if (lit == 15) {
                while (true) {
                    if (ip >= end) return false
                    val b = src.u8(ip++)
                    lit += b
                    if (b != 255) break
                }
            }
            if (ip + lit > end) return false
            val tmp = ByteArray(min(lit, IO_CHUNK))
            var left = lit
            while (left > 0) {
                val c = min(left, tmp.size)
                src.read(tmp, 0, ip, c)
                if (!sink.write(tmp, 0, c)) return false
                ip += c
                left -= c
            }
            if (ip >= end) break
            if (ip + 2 > end) return false
            val offset = src.u8(ip) or (src.u8(ip + 1) shl 8)
            ip += 2
            var match = token and 0x0f
            if (match == 15) {
                while (true) {
                    if (ip >= end) return false
                    val b = src.u8(ip++)
                    match += b
                    if (b != 255) break
                }
            }
            match += 4
            if (offset == 0) {
                if (!sink.writeZeros(match)) return false
                continue
            }
            if (!sink.writeMatch(offset, match)) return false
        }
        return true
    }

    private fun recoverKallsyms(image: KernelImage, imageSize: Long, lines: MutableList<String>): Kallsyms? {
        val tables = findTokenTables(image)
        if (tables.isEmpty()) {
            lines += "kallsyms      : token table not found"
            return null
        }
        tables.forEachIndexed { i, t ->
            lines += "kallsyms      : token table[$i] @0x${java.lang.Long.toHexString(t.tableOffset.toLong())}"
        }
        var blocksFound = 0
        var best: Kallsyms? = null
        for (table in tables) {
            val blocks = findNameBlocks(image, table)
            blocksFound += blocks.size
            for (block in blocks) {
                val count = block.names.size
                lines += ("kallsyms      : names @0x${java.lang.Long.toHexString(block.namesOffset.toLong())}, " +
                    "num_syms @0x${java.lang.Long.toHexString(block.numSymsOffset.toLong())}, " +
                    "markers @0x${java.lang.Long.toHexString(block.markersOffset.toLong())}, " +
                    "count=$count")
                val oldRelativeBase = alignUp(block.numSymsOffset - ALIGN, ALIGN).toInt()
                val oldOffsets = alignUp(oldRelativeBase - alignUp(count * 4L, ALIGN), ALIGN).toInt()
                val newOffsets = alignUp((table.indexOffset + TOKEN_COUNT * 2).toLong(), ALIGN).toInt()
                val newRelativeBase = alignUp((newOffsets + count * 4L), ALIGN).toInt()
                for ((layout, offsetsOff, baseOff) in listOf(
                    Triple("pre-6.4", oldOffsets, oldRelativeBase),
                    Triple("6.4+", newOffsets, newRelativeBase)
                )) {
                    val decoded = decodeAddresses(image, imageSize, block.names, block.kinds, offsetsOff, baseOff)
                        ?: continue
                    lines += "kallsyms      : layout=$layout matched ($count symbols)"
                    if (best == null || decoded.count > best.count) best = decoded
                }
            }
        }
        if (best == null) {
            lines += "kallsyms      : NOT RECOVERED (token tables=${tables.size}, name blocks=$blocksFound, address layouts matched=0)"
        }
        return best
    }

    private fun findTokenTables(image: KernelImage): List<TokenTable> {
        val starts = IntArray(TOKEN_COUNT)
        val ends = IntArray(TOKEN_COUNT)
        val offsets = IntArray(TOKEN_COUNT)
        val out = ArrayList<TokenTable>(MAX_TOKEN_TABLES)
        var from = 0
        while (from < image.size && out.size < MAX_TOKEN_TABLES) {
            val digit = indexOf(image, DIGIT_NEEDLE, from)
            if (digit < 0) break
            val earliest = max(0, digit - 0x30 * 257)
            var start = alignUp(earliest.toLong(), ALIGN).toInt()
            while (start <= digit && out.size < MAX_TOKEN_TABLES) {
                val parsed = parseTokenTableAt(image, start, digit, starts, ends, offsets)
                if (parsed != null) out += parsed
                start += ALIGN.toInt()
            }
            from = digit + 1
        }
        return out
    }

    private fun parseTokenTableAt(
        image: KernelImage, start: Int, digit: Int,
        starts: IntArray, ends: IntArray, offsets: IntArray
    ): TokenTable? {
        if (start % ALIGN.toInt() != 0 || start < 0) return null
        var pos = start
        for (i in 0 until TOKEN_COUNT) {
            if (pos >= image.size) return null
            var end = pos
            while (end < image.size && end - pos <= 256 && image.u8(end) != 0) {
                val v = image.u8(end)
                if (v < 0x20 || v > 0x7e) return null
                end++
            }
            if (end >= image.size || end == pos) return null
            starts[i] = pos
            ends[i] = end
            offsets[i] = pos - start
            pos = end + 1
        }
        if (starts[0x30] != digit) return null
        for (c in "0123456789_abcdefghijklmnopqrstuvwxyzT") {
            val i = c.code
            if (ends[i] - starts[i] != 1 || image.u8(starts[i]) != i) return null
        }
        val indexOff = alignUp(pos.toLong(), ALIGN).toInt()
        for (k in pos until min(indexOff, image.size)) if (image.u8(k) != 0) return null
        if (indexOff + TOKEN_COUNT * 2 > image.size || offsets[TOKEN_COUNT - 1] > 0xffff) return null
        for (i in 0 until TOKEN_COUNT) {
            if (image.u16(indexOff + i * 2) != offsets[i]) return null
        }
        val tokens = Array(TOKEN_COUNT) { i -> image.slice(starts[i], ends[i]) }
        return TokenTable(tokens, start, indexOff)
    }

    private fun findNameBlocks(image: KernelImage, table: TokenTable): List<NameBlock> {
        val markers = LongArray(4096)
        val lower = max(0, table.tableOffset - MARKER_WINDOW)
        val out = ArrayList<NameBlock>(MAX_NAME_BLOCKS)
        var pos = alignUp(lower.toLong(), ALIGN).toInt()
        while (pos + 8 <= table.tableOffset && out.size < MAX_NAME_BLOCKS) {
            if (image.u32(pos) == 0L) {
                val n = readMarkers(image, pos, table.tableOffset, markers)
                if (n >= 8) {
                    val minCount = (n - 1) * 256 + 1
                    val maxCount = n * 256
                    // GhostLock find_name_tables: names 起点 = markers 数组偏移 - 最后一个 marker。
                    // 之前误用 tableOffset（token table 在 markers 之后），names 起点被推到镜像中部。
                    val approximate = pos - markers[n - 1].toInt()
                    if (approximate > 0) {
                        var numOff = alignUp(approximate.toLong(), ALIGN).toInt()
                        val searchStart = max(0, approximate - NAME_TAIL_SEARCH)
                        while (numOff >= searchStart && numOff + 8 <= image.size && out.size < MAX_NAME_BLOCKS) {
                            val count = image.u32(numOff).toInt()
                            val paddingZero = image.u32(numOff + 4) == 0L
                            if (count in minCount..maxCount && paddingZero) {
                                val namesOff = numOff + ALIGN.toInt()
                                for (uleb in booleanArrayOf(true, false)) {
                                    val spans = parseNameSpans(
                                        image, namesOff, count, pos, markers, n, uleb
                                    ) ?: continue
                                    val decoded = decodeNames(image, spans, count, table.tokens) ?: continue
                                    val (kinds, names) = decoded
                                    if (hasAnchors(names)) {
                                        out += NameBlock(numOff, namesOff, pos, kinds, names)
                                        break
                                    }
                                }
                            }
                            numOff -= ALIGN.toInt()
                        }
                    }
                }
            }
            pos += ALIGN.toInt()
        }
        return out
    }

    private fun hasAnchors(names: Array<String>): Boolean {
        var text = false
        var end = false
        var loadModule = false
        for (n in names) {
            if (!text && n == "_text") text = true
            else if (!end && n == "_end") end = true
            else if (!loadModule && n == "load_module") loadModule = true
            if (text && end && loadModule) return true
        }
        return false
    }

    private fun readMarkers(image: KernelImage, start: Int, limit: Int, out: LongArray): Int {
        if (start % ALIGN.toInt() != 0 || start + 8 > limit) return 0
        if (image.u32(start) != 0L) return 0
        val second = image.u32(start + 4)
        if (second !in 0x200L..0x40000L) return 0
        out[0] = 0L
        out[1] = second
        var n = 2
        var pos = start + 8
        while (n < out.size && pos + 4 <= limit) {
            val v = image.u32(pos)
            val delta = v - out[n - 1]
            if (delta !in 0x200L..0x40000L) break
            out[n++] = v
            pos += 4
        }
        return if (n >= 8) n else 0
    }

    private fun parseNameSpans(
        image: KernelImage, namesOff: Int, count: Int, markersOff: Int,
        markers: LongArray, markerCount: Int, uleb: Boolean
    ): IntArray? {
        if (count <= 0 || markerCount < (count + 255) / 256) return null
        val spans = IntArray(count * 2)
        var pos = namesOff
        for (i in 0 until count) {
            if (i % 256 == 0 && pos - namesOff != markers[i / 256].toInt()) return null
            if (pos >= markersOff) return null
            var length = image.u8(pos++)
            if (uleb && (length and 0x80) != 0) {
                if (pos >= markersOff) return null
                val high = image.u8(pos++)
                if ((high and 0x80) != 0) return null
                length = (length and 0x7f) or (high shl 7)
            }
            if (length == 0 || length > 0x3fff || pos + length > markersOff) return null
            spans[i * 2] = pos
            spans[i * 2 + 1] = length
            pos += length
        }
        if (alignUp(pos.toLong(), ALIGN).toInt() != markersOff) return null
        for (k in pos until markersOff) if (image.u8(k) != 0) return null
        return spans
    }

    private fun decodeNames(
        image: KernelImage, spans: IntArray, count: Int, tokens: Array<ByteArray>
    ): Pair<CharArray, Array<String>>? {
        val kinds = CharArray(count)
        val names = Array(count) { "" }
        for (i in 0 until count) {
            val pos = spans[i * 2]
            val len = spans[i * 2 + 1]
            val sb = StringBuilder(len + 1)
            for (k in pos until pos + len) {
                val t = tokens.getOrNull(image.u8(k)) ?: return null
                for (j in t.indices) sb.append(t[j].toInt().toChar())
            }
            if (sb.isEmpty() || sb.length > 4096 || sb[0] !in SYMBOL_TYPES) return null
            val name = sb.substring(1)
            if (name.isEmpty() || name.any { it.code !in 0x21..0x7e }) return null
            kinds[i] = sb[0]
            names[i] = name
        }
        return kinds to names
    }

    private fun decodeAddresses(
        image: KernelImage, imageSize: Long, names: Array<String>, kinds: CharArray,
        offsetsOff: Int, baseOff: Int
    ): Kallsyms? {
        val count = names.size
        val offsetsEnd = offsetsOff + count * 4
        if (offsetsOff < 0 || offsetsEnd > image.size || baseOff < offsetsEnd || baseOff + 8 > image.size) return null
        for (k in offsetsEnd until baseOff) if (image.u8(k) != 0) return null
        val relativeBase = image.u64(baseOff)
        if (relativeBase ushr 48 != 0xffffL || relativeBase % 4096 != 0L) return null
        var negatives = 0
        for (i in 0 until count) if (image.i32(offsetsOff + i * 4) < 0) negatives++
        val addresses = LongArray(count)
        if (negatives * 2 >= count) {
            for (i in 0 until count) {
                val o = image.i32(offsetsOff + i * 4)
                addresses[i] = if (o < 0) relativeBase - 1 - o.toLong() else o.toLong()
            }
        } else {
            for (i in 0 until count) addresses[i] = relativeBase + image.u32(offsetsOff + i * 4)
        }
        for (i in 1 until count) if (addresses[i - 1] > addresses[i]) return null

        // 先做廉价校验，失败候选不再构造十万条目的 HashMap。
        var textIdx = -1
        var endIdx = -1
        for (i in 0 until count) {
            if (textIdx < 0 && names[i] == "_text") textIdx = i
            if (endIdx < 0 && names[i] == "_end") endIdx = i
            if (textIdx >= 0 && endIdx >= 0) break
        }
        if (textIdx < 0 || endIdx < 0) return null
        val text = addresses[textIdx]
        val end = addresses[endIdx]
        val span = end - text
        if (text != relativeBase || (span != imageSize && span != image.size.toLong())) return null

        val map = HashMap<String, Long>(count * 2)
        val kindMap = HashMap<String, Char>(count * 2)
        for (i in 0 until count) {
            val n = names[i]
            if (!map.containsKey(n)) {
                map[n] = addresses[i]
                kindMap[n] = kinds[i]
            }
        }
        return Kallsyms(imageSize, map, kindMap, count)
    }

    /** 从解压后的 Image 取 linux_banner：逐字节读，不复制内核段、不整段转 String。 */
    private fun findVersionString(image: KernelImage): String? {
        val needle = KERNEL_VERSION.toByteArray(Charsets.US_ASCII)
        val hit = indexOf(image, needle, 0)
        if (hit < 0) return null
        var p = hit + needle.size
        val parts = IntArray(3)
        for (j in 0..2) {
            var v = 0
            var any = false
            while (p < image.size) {
                val c = image.u8(p)
                if (c < 0x30 || c > 0x39) break
                v = v * 10 + (c - 0x30)
                any = true
                p++
            }
            if (!any) return null
            parts[j] = v
            if (j < 2) {
                if (p >= image.size || image.u8(p) != 0x2e) return null
                p++
            }
        }
        val sb = StringBuilder(64)
        sb.append(parts[0]).append('.').append(parts[1]).append('.').append(parts[2])
        while (p < image.size && sb.length < 96) {
            val c = image.u8(p)
            if (c < 0x21 || c > 0x7e) break
            sb.append(c.toChar())
            p++
        }
        return sb.toString()
    }

    private fun indexOf(haystack: KernelImage, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || from >= haystack.size) return -1
        val first = needle[0].toInt() and 0xff
        val last = haystack.size - needle.size
        var i = from
        while (i <= last) {
            if (haystack.u8(i) == first) {
                var j = 1
                while (j < needle.size && haystack.u8(i + j) == (needle[j].toInt() and 0xff)) j++
                if (j == needle.size) return i
            }
            i++
        }
        return -1
    }

    private fun alignUp(v: Long, a: Long): Long = (v + a - 1) / a * a
}
