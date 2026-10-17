package com.neodhizuku.more.data.common.util

import com.neodhizuku.more.NeoLog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import rikka.shizuku.Shizuku

import java.io.InputStream

/**
 * 经 Shizuku 以 shell / root 身份执行一条 shell 命令。
 *
 * 之所以需要它：`Settings.Secure` 的写入受 `WRITE_SECURE_SETTINGS` 保护
 * （protectionLevel 含 signature|privileged|development），
 * 普通应用（untrusted_app）拿不到；而 `cmd role` 系列同样需要 shell 身份。
 * 这类"必须借身份"的步骤只能通过 Shizuku 起进程完成。
 */
object ShizukuShell {

    /** 执行结果 */
    data class Result(
        val exitCode: Int,
        val output: String,
        val error: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    private const val READ_TIMEOUT_MILLIS = 20_000L

    /**
     * 执行 [script]，并等待结束。
     *
     * 必须在非主线程调用（内部已切到 IO）。
     *
     * stdout / stderr 分两个线程并发读取，避免任一缓冲区写满后阻塞子进程 ——
     * 只读取其中一个流是常见死锁来源。
     */
    suspend fun exec(script: String): Result = withContext(Dispatchers.IO) {
        NeoLog.i(NeoLog.ACTIVATE) { "shell exec: $script" }
        val process = createProcess(script)
        if (process == null) {
            NeoLog.w(NeoLog.ACTIVATE) { "shell exec: 创建进程失败" }
            return@withContext Result(-1, "", "无法创建进程（Shizuku 未就绪？）")
        }

        val out = StringBuilder()
        val err = StringBuilder()

        val outThread = Thread {
            runCatching {
                process.openStdout()?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        out.appendLine(line)
                    }
                }
            }
        }.apply { isDaemon = true }

        val errThread = Thread {
            runCatching {
                process.openStderr()?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        err.appendLine(line)
                    }
                }
            }
        }.apply { isDaemon = true }

        outThread.start()
        errThread.start()

        val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
        runCatching {
            outThread.join(READ_TIMEOUT_MILLIS)
            errThread.join(READ_TIMEOUT_MILLIS)
        }
        runCatching { process.destroy() }

        val result = Result(exitCode, out.toString().trim(), err.toString().trim())
        NeoLog.i(NeoLog.ACTIVATE) {
            "shell result: exit=$exitCode out=${result.output} err=${result.error}"
        }
        result
    }

    /**
     * 创建远程进程。
     *
     * `Shizuku.newProcess` 在 API 13.x 里是 **private static**，直接调用编译不过，
     * 因此这里用反射取。返回的是 `ShizukuRemoteProcess`，
     * 不一定能直接转型为 `java.lang.Process`，所以统一走反射访问流与方法。
     */
    private fun createProcess(script: String): RemoteProcess? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val obj = method.invoke(null, arrayOf("sh", "-c", script), null, null)
            ?: return@runCatching null
        RemoteProcess(obj)
    }.onFailure {
        NeoLog.w(NeoLog.ACTIVATE) { "newProcess 反射失败：${it.message}" }
    }.getOrNull()

    /**
     * 对远程进程对象的反射封装。
     *
     * 反射调用 `getInputStream` / `getErrorStream` / `waitFor` / `destroy` ——
     * 这些方法无论是 `ShizukuRemoteProcess` 自带的，还是从 `java.lang.Process`
     * 继承来的，`getMethod` 都能取到，因此不必关心它到底继承关系如何。
     */
    private class RemoteProcess(private val obj: Any) {
        @Suppress("UNCHECKED_CAST")
        private fun <T> call(name: String): T? = runCatching {
            obj.javaClass.getMethod(name).invoke(obj) as? T
        }.getOrNull()

        fun openStdout(): InputStream? = call("getInputStream")
        fun openStderr(): InputStream? = call("getErrorStream")
        fun waitFor(): Int = call<Int>("waitFor") ?: -1
        fun destroy() = runCatching { obj.javaClass.getMethod("destroy").invoke(obj) }
    }
}
