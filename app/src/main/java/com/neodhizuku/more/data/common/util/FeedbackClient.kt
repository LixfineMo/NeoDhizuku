package com.neodhizuku.more.data.common.util

import com.neodhizuku.more.NeoLog

import org.json.JSONObject

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 反馈提交。
 * 地址与更新清单同源：从更新配置里取 `scheme://host:port` 再拼上 [PATH]，
 * 不单独再配一套服务器地址 —— 用户改一次更新来源，两边一起生效。
 * 服务端只收不展示，返回 `{"ok":true,"message":"..."}`；
 * 超出每日条数时返回 429，message 里带上原因。
 */
object FeedbackClient {

    /** 服务端路径。 */
    const val PATH: String = "/feedback"

    /** 提交结果。 */
    sealed interface Result {
        /** 已写入 */
        data object Success : Result

        /** 内容为空，本地直接拦下，不发请求 */
        data object Empty : Result

        /** 失败；[message] 是服务端给的说明，可能为 null */
        data class Failed(val message: String?) : Result
    }

    /** 去掉清单路径，只留 `scheme://host:port`。 */
    fun baseOf(config: UpdateConfig): String {
        val raw = when (config.source) {
            UpdateSource.Official -> config.officialBase
            UpdateSource.Custom -> config.customUrl
            // GitHub 上没有服务端，反馈无从提交
            UpdateSource.GitHub -> return ""
        }.trim()
        if (raw.isBlank()) return ""
        val withScheme =
            if (raw.contains("://")) raw else "${UpdateLinks.OFFICIAL_SCHEME}://$raw"
        val hostStart = withScheme.indexOf("://") + 3
        val cut = withScheme.indexOf('/', hostStart)
        return (if (cut >= 0) withScheme.substring(0, cut) else withScheme).trimEnd('/')
    }

    /** 反馈地址；来源不可用或地址为空时返回空串。 */
    fun endpointOf(config: UpdateConfig): String {
        val base = baseOf(config)
        if (base.isBlank()) return ""
        return base + PATH
    }

    /**
     * 提交一条反馈。
     * 明文地址不额外拦截：这正是用户在更新设置里亲手填的服务器，
     * 与"允许不安全的链接"那个开关（管的是 APK 下载地址）不是一回事。
     */
    fun send(
        config: UpdateConfig,
        title: String,
        content: String,
        contact: String
    ): Result {
        val body = content.trim()
        if (body.isEmpty()) return Result.Empty

        val url = endpointOf(config)
        if (url.isBlank()) {
            NeoLog.w(NeoLog.UI) { "反馈：地址为空，来源=${config.source}" }
            return Result.Failed(null)
        }

        val payload = JSONObject()
            .put("title", title.trim())
            .put("content", body)
            .put("contact", contact.trim())
            .toString()

        NeoLog.i(NeoLog.UI) { "反馈：提交 $url" }
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = config.timeoutMs
            connection.readTimeout = config.timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(payload)
            }

            val code = connection.responseCode
            val text = if (code in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                runCatching {
                    connection.errorStream?.bufferedReader()?.readText()
                }.getOrNull().orEmpty()
            }
            val json = runCatching { JSONObject(text) }.getOrNull()
            val message = json?.optString("message")?.takeIf { it.isNotBlank() }

            if (code in 200..299 && json?.optBoolean("ok") == true) {
                NeoLog.i(NeoLog.UI) { "反馈：已提交" }
                Result.Success
            } else {
                NeoLog.w(NeoLog.UI) { "反馈：失败 code=$code msg=$message" }
                Result.Failed(message)
            }
        } catch (t: Throwable) {
            NeoLog.w(NeoLog.UI) { "反馈：异常 ${t.message}" }
            Result.Failed(t.message)
        } finally {
            connection.disconnect()
        }
    }
}
