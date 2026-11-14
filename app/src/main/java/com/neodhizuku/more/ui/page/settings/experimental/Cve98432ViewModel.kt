package com.neodhizuku.more.ui.page.settings.experimental

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_LOG_LINES = 500

class Cve98432ViewModel : ViewModel() {

    var bootInfo by mutableStateOf<BootImageInfo?>(null)
        private set

    var profile by mutableStateOf<KernelProfile?>(null)
        private set

    var permissiveMode by mutableStateOf(false)
        private set

    var prepared by mutableStateOf(false)
        private set

    var vpnGranted by mutableStateOf(false)
        private set

    var running by mutableStateOf(false)
        private set

    var handoff by mutableStateOf(false)
        private set

    var showConfirm by mutableStateOf(false)
        private set

    var logs by mutableStateOf(listOf<String>())
        private set

    val logText: String get() = logs.joinToString("\n")

    init {
        Cve98432Bridge.logSink = ::append
    }

    fun setExecutionMode(enabled: Boolean) {
        permissiveMode = enabled
        append("mode          : ${if (enabled) "permissive only (no root)" else "read-only probe"}")
    }

    fun importBoot(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val buffer = runCatching { Cve98432Engine.readBoot(context, uri) }
                .getOrElse { e ->
                    append("ERROR: ${e.javaClass.simpleName}: ${e.message ?: "read failed"}")
                    return@launch
                }
            if (buffer == null) {
                append("ERROR: cannot open the selected document")
                return@launch
            }
            append("importing ${buffer.name} (${buffer.sizeBytes} bytes) ...")
            val info = runCatching { Cve98432Engine.parseBootImage(buffer) }
                .getOrElse { e ->
                    append("ERROR: ${e.javaClass.simpleName}: ${e.message ?: "parse failed"}")
                    return@launch
                }
            withContext(Dispatchers.Main) { bootInfo = info }
            append("---- boot.img ----")
            Cve98432Engine.describeBoot(info).forEach(::append)

            append("---- offset extraction (GhostLock kallsyms recovery) ----")
            val (extracted, lines) = runCatching { Cve98432Engine.extractProfile(context, buffer, info) }
                .getOrElse { e ->
                    append("ERROR: ${e.javaClass.simpleName}: ${e.message ?: "extract failed"}")
                    return@launch
                }
            lines.forEach(::append)
            extracted?.describe()?.forEach(::append)
            withContext(Dispatchers.Main) {
                profile = extracted
                prepared = false
                handoff = false
            }
            append("offset extraction finished")
        }
    }

    /** vpnResultCode 非空表示刚从系统授权对话框返回。 */
    fun prepare(context: Context, vpnResultCode: Int? = null) {
        if (running) return
        viewModelScope.launch(Dispatchers.IO) {
            append("---- environment ----")
            if (vpnResultCode != null) {
                append("vpn dialog     : resultCode=$vpnResultCode")
            }
            val lines = runCatching { Cve98432Engine.environment(context) }
                .getOrElse { e -> listOf("ERROR: ${e.javaClass.simpleName}: ${e.message ?: "check failed"}") }
            lines.forEach(::append)
            val granted = Cve98432Engine.vpnConsentIntent(context) == null
            withContext(Dispatchers.Main) {
                prepared = true
                vpnGranted = granted
            }
            append(
                if (granted) "environment check finished"
                else "environment check finished - VPN consent still pending"
            )
        }
    }

    fun requestStart() {
        if (running) return
        showConfirm = true
    }

    fun dismissConfirm() {
        showConfirm = false
    }

    fun start(context: Context) {
        if (running) return
        showConfirm = false
        running = true
        handoff = false
        append("---- execution ----")
        append("vpn granted    : $vpnGranted")
        viewModelScope.launch(Dispatchers.IO) {
            val lines = runCatching {
                Cve98432Engine.execute(context, bootInfo, profile, prepared, permissiveMode)
            }.getOrElse { e -> listOf("ERROR: ${e.javaClass.simpleName}: ${e.message ?: "execution failed"}") }
            lines.forEach(::append)
            val ok = Cve98432Engine.confirmPermissive()
            withContext(Dispatchers.Main) {
                running = false
                handoff = ok
            }
        }
    }

    fun cancel() {
        Cve98432Bridge.cancel()
        Cve98432VpnService.stop()
        running = false
        append("execution cancelled by user")
    }

    fun openKsu(context: Context): Boolean {
        val intent = runCatching {
            context.packageManager.getLaunchIntentForPackage("me.weishu.kernelsu")
        }.getOrNull() ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun clearLogs() {
        logs = emptyList()
    }

    private val stampLock = Any()
    private val stampFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun append(line: String) {
        // native 线程也会调用，格式器非线程安全，用短临界区复用而不是每行 new 一个。
        val stamp = synchronized(stampLock) { stampFormat.format(Date()) }
        val entry = "$stamp  $line"
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val next = logs + entry
            logs = if (next.size > MAX_LOG_LINES) next.takeLast(MAX_LOG_LINES) else next
        }
    }
}
