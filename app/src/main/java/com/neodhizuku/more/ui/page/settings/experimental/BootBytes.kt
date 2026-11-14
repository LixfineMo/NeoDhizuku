package com.neodhizuku.more.ui.page.settings.experimental

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer

/**
 * boot.img 的统一只读视图。
 *
 * 工厂镜像的 boot.img 常按分区大小补齐（Pixel 上正好 64MB）。ART 上的
 * ByteBuffer.allocateDirect 会在 dalvik heap 里开 non-movable 大数组（LOS），
 * 一样受 growth limit 约束，连续两次导入就会 OOM。主路径改用 FileChannel.map：
 * 映射的是文件页，不算堆分配，Java heap 只剩一个壳和 64KB IO 缓冲。
 */
interface BootBytes {
    val size: Int

    operator fun get(i: Int): Byte

    fun read(dst: ByteArray, dstOff: Int, srcOff: Int, len: Int)

    fun copyRange(from: Int, to: Int): ByteArray {
        val out = ByteArray(to - from)
        read(out, 0, from, out.size)
        return out
    }

    fun stream(off: Int, len: Int): InputStream
}

/** 极小文件 / 兼容路径：仍走堆内数组。 */
class HeapBootBytes(private val b: ByteArray) : BootBytes {
    override val size get() = b.size
    override fun get(i: Int): Byte = b[i]
    override fun read(dst: ByteArray, dstOff: Int, srcOff: Int, len: Int) =
        System.arraycopy(b, srcOff, dst, dstOff, len)

    override fun stream(off: Int, len: Int): InputStream =
        ByteArrayInputStream(b, off, minOf(len, b.size - off))
}

/** 兼容旧路径：allocateDirect 的镜像；主路径已改用 MappedBootBytes。 */
class DirectBootBytes(private val buf: ByteBuffer, override val size: Int) : BootBytes {
    override fun get(i: Int): Byte = buf.get(i)

    override fun read(dst: ByteArray, dstOff: Int, srcOff: Int, len: Int) {
        val d = buf.duplicate()
        d.position(srcOff)
        d.get(dst, dstOff, len)
    }

    override fun stream(off: Int, len: Int): InputStream = object : InputStream() {
        private var pos = off
        private val end = minOf(off + len, size)

        override fun read(): Int =
            if (pos >= end) -1 else (buf.get(pos++).toInt() and 0xff)

        override fun read(b: ByteArray, o: Int, n: Int): Int {
            if (pos >= end) return -1
            val c = minOf(n, end - pos)
            if (c == 0) return 0
            this@DirectBootBytes.read(b, o, pos, c)
            pos += c
            return c
        }

        override fun available(): Int = end - pos
    }
}

/**
 * 主路径：文件只读映射。MappedByteBuffer 的绝对 get(i) / duplicate + position
 * 与 DirectByteBuffer 完全同语义，但 backing store 是页缓存，不占 dalvik heap。
 */
class MappedBootBytes(private val buf: MappedByteBuffer, override val size: Int) : BootBytes {
    override fun get(i: Int): Byte = buf.get(i)

    override fun read(dst: ByteArray, dstOff: Int, srcOff: Int, len: Int) {
        val d = buf.duplicate()
        d.position(srcOff)
        d.get(dst, dstOff, len)
    }

    override fun stream(off: Int, len: Int): InputStream = object : InputStream() {
        private var pos = off
        private val end = minOf(off + len, size)

        override fun read(): Int =
            if (pos >= end) -1 else (buf.get(pos++).toInt() and 0xff)

        override fun read(b: ByteArray, o: Int, n: Int): Int {
            if (pos >= end) return -1
            val c = minOf(n, end - pos)
            if (c == 0) return 0
            this@MappedBootBytes.read(b, o, pos, c)
            pos += c
            return c
        }

        override fun available(): Int = end - pos
    }
}
