package com.neodhizuku.more.data.common.util

import com.neodhizuku.more.NeoLog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import rikka.shizuku.Shizuku

import java.io.InputStream

/**
 * 经 Shizuku 以 shell / root 身份执行一条 shell 命令。
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
     * 必须在非主线程调用（内部已切到 IO）。
     * stdout / stderr 分两个线程并发读取，避免任一缓冲区写满后阻塞子进程 ——
     * 只读取其中一个流是常见死锁来源。
     * @param waitTimeoutMillis 等待子进程结束的超时；**0 表示不限制**（原有行为）。
     *   进程卡住时 `waitFor()` 本身不会返回，20 秒的 `join` 救不了它，
     *   所以需要在单独线程里等并带超时，超时后主动 destroy。
     */
    suspend fun exec(script: String, waitTimeoutMillis: Long = 0L): Result =
        withContext(Dispatchers.IO) {
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

        val exitCode = if (waitTimeoutMillis > 0L) {
            waitForWithTimeout(process, waitTimeoutMillis)
        } else {
            runCatching { process.waitFor() }.getOrDefault(-1)
        }
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
     * waitForWithTimeout
     * (process: RemoteProcess, timeoutMillis: Long)
     * 作用：带超时地等待进程结束。 `RemoteProcess.waitFor()` 内部是 `Process.waitFor()`，进程卡住时会一直阻塞。 这里把它放到单独线程里等，超时后 destroy 让对端退出，避免整条链路挂死。
     * 备注：仅内部使用
     */
    private fun waitForWithTimeout(process: RemoteProcess, timeoutMillis: Long): Int {
        val exit = java.util.concurrent.atomic.AtomicInteger(-1)
        val waiter = Thread {
            exit.set(runCatching { process.waitFor() }.getOrDefault(-1))
        }.apply { isDaemon = true }
        waiter.start()
        waiter.join(timeoutMillis)
        if (waiter.isAlive) {
            NeoLog.w(NeoLog.ACTIVATE) { "shell exec: 等待超时 ${timeoutMillis}ms，主动结束进程" }
            runCatching { process.destroy() }
            // destroy 之后 waitFor 通常很快返回，再给一点时间取退出码
            waiter.join(1_000L)
        }
        return exit.get()
    }

    /**
     * createProcess
     * (script: String)
     * 作用：创建远程进程。 `Shizuku.newProcess` 在 API 13.x 里是 **private static**，直接调用编译不过， 因此这里用反射取。返回的是 `ShizukuRemoteProcess`， 不一定能直接转型为 `java.lang.Process`，所以统一走反射访问流与方法。
     * 备注：仅内部使用
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
     * 反射调用 `getInputStream` / `getErrorStream` / `waitFor` / `destroy` ——
     * 这些方法无论是 `ShizukuRemoteProcess` 自带的，还是从 `java.lang.Process`
     * 继承来的，`getMethod` 都能取到，因此不必关心它到底继承关系如何。
     */
    private class RemoteProcess(private val obj: Any) {
        /**
         * call
         * (name: String)
         * 作用：调用
         * 备注：仅内部使用
         */
        @Suppress("UNCHECKED_CAST")
        private fun <T> call(name: String): T? = runCatching {
            obj.javaClass.getMethod(name).invoke(obj) as? T
        }.getOrNull()

        /**
         * openStdout
         * ()
         * 作用：打开 Stdout
         * 备注：—
         */
        fun openStdout(): InputStream? = call("getInputStream")
        /**
         * openStderr
         * ()
         * 作用：打开 Stderr
         * 备注：—
         */
        fun openStderr(): InputStream? = call("getErrorStream")
        /**
         * waitFor
         * ()
         * 作用：等待 For
         * 备注：—
         */
        fun waitFor(): Int = call<Int>("waitFor") ?: -1
        /**
         * destroy
         * ()
         * 作用：执行 destroy
         * 备注：—
         */
        fun destroy() = runCatching { obj.javaClass.getMethod("destroy").invoke(obj) }
    }
}
