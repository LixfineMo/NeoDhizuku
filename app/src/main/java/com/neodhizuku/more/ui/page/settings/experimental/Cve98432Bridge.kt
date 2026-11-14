package com.neodhizuku.more.ui.page.settings.experimental

import kotlin.jvm.JvmStatic

/**
 * native 侧的入口与日志回调。所有内核相关操作都在 lib 里，Java 层只负责
 * 传 tun 描述符、profile 路径和取消信号。
 */
object Cve98432Bridge {

    @Volatile
    var logSink: ((String) -> Unit)? = null

    @Volatile
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        System.loadLibrary("cve98432")
        nativeInit()
        loaded = true
    }

    fun available(): Boolean = runCatching {
        ensureLoaded()
        true
    }.getOrElse { false }

    /** 由 native 线程调用，签名必须与 cve98432.c 的 GetStaticMethodID 一致。 */
    @JvmStatic
    fun onNativeLog(line: String) {
        logSink?.invoke(line)
    }

    fun prepare(tunFd: Int, profilePath: String): Int = runCatching {
        ensureLoaded()
        nativePrepare(tunFd, profilePath)
    }.getOrDefault(-1)

    fun probe(rounds: Int): Int = runCatching {
        ensureLoaded()
        nativeProbe(rounds)
    }.getOrDefault(-1)

    fun resolveSlide(): Long = runCatching {
        ensureLoaded()
        nativeResolveSlide()
    }.getOrDefault(0L)

    fun setPermissive(): Int = runCatching {
        ensureLoaded()
        nativeSetPermissive()
    }.getOrDefault(-1)

    fun cancel() = runCatching {
        if (loaded) nativeCancel()
    }

    fun release() = runCatching {
        if (loaded) nativeRelease()
    }

    @JvmStatic external fun nativeInit()
    @JvmStatic external fun nativePrepare(tunFd: Int, profilePath: String): Int
    @JvmStatic external fun nativeProbe(rounds: Int): Int
    @JvmStatic external fun nativeResolveSlide(): Long
    @JvmStatic external fun nativeSetPermissive(): Int
    @JvmStatic external fun nativeCancel()
    @JvmStatic external fun nativeRelease()
}
