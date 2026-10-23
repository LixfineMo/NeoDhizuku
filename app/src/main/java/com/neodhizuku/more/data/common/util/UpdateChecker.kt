package com.neodhizuku.more.data.common.util

import com.neodhizuku.more.NeoLog

import org.json.JSONObject

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检测。
 *
 * 服务端只有一个 JSON 文件：[UpdateLinks.VERSION_JSON]，字段见 [UpdateInfo]。
 * 其中 `changelog` 存的是**另一个 Raw 文件的地址**，拿到后再取一次文本。
 */
object UpdateLinks {

    /** 版本清单地址 */
    const val VERSION_JSON: String =
        "https://raw.githubusercontent.com/Qingyu-ch/NeoDhizuku/refs/heads/master/Update/verson.json"

    /** 清单里没有下载地址时退回仓库主页 */
    const val REPO_HOME: String = "https://github.com/Qingyu-ch/NeoDhizuku"
}

/** 版本清单。 */
data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelogUrl: String,
    val isAlpha: Boolean,
    val isBeta: Boolean,
    val isGrayRelease: Boolean
) {
    /** 非正式版本：alpha、beta 与灰度都算。 */
    val isPreRelease: Boolean get() = isAlpha || isBeta || isGrayRelease
}

/** 一次检测的结果。 */
sealed interface UpdateResult {

    /** 当前已是最新 */
    data object UpToDate : UpdateResult

    /** 有可用更新 */
    data class Available(val info: UpdateInfo, val changelog: String) : UpdateResult

    /** 存在更新但被灰度开关挡下了 */
    data class PreReleaseBlocked(val info: UpdateInfo) : UpdateResult

    /** 取不到清单或解析失败 */
    data class Failed(val reason: String?) : UpdateResult
}

object UpdateChecker {

    private const val TIMEOUT_MS = 8_000

    /**
     * 取版本清单。
     *
     * 字段类型不保证一致 —— `isAlpha` 一类的标记在仓库里是字符串 `"true"`，
     * 版本号也可能是字符串，所以统一按容错方式读；读不出来就当作 0 / false，
     * 宁可判成"无更新"也不能因为解析问题弹个假更新。
     */
    fun fetch(): UpdateInfo? = runCatching {
        val text = getText(UpdateLinks.VERSION_JSON) ?: return null
        val json = JSONObject(text)
        UpdateInfo(
            version = json.optString("version").ifBlank { "" },
            versionCode = optInt(json, "versionCode"),
            apkUrl = json.optString("apkUrl").trim(),
            changelogUrl = json.optString("changelog").trim(),
            isAlpha = optFlag(json, "isAlpha"),
            isBeta = optFlag(json, "isBeta"),
            isGrayRelease = optFlag(json, "isGrayRelease")
        )
    }.onFailure {
        NeoLog.w(NeoLog.UI) { "更新：读取版本清单失败：${it.message}" }
    }.getOrNull()

    /** 取更新日志正文；地址为空或取不到都返回空串。 */
    fun fetchChangelog(url: String): String {
        if (url.isBlank()) return ""
        return runCatching { getText(url).orEmpty() }
            .onFailure {
                NeoLog.w(NeoLog.UI) { "更新：读取更新日志失败：${it.message}" }
            }
            .getOrDefault("")
    }

    /**
     * 与本地版本比对。
     *
     * @param currentCode 本地 versionCode
     * @param allowPreRelease 是否把非正式版本也算作可更新
     */
    fun compare(
        info: UpdateInfo?,
        currentCode: Int,
        allowPreRelease: Boolean
    ): UpdateResult {
        if (info == null) return UpdateResult.Failed(null)
        if (info.versionCode <= currentCode) return UpdateResult.UpToDate
        if (info.isPreRelease && !allowPreRelease) return UpdateResult.PreReleaseBlocked(info)
        val changelog = fetchChangelog(info.changelogUrl)
        return UpdateResult.Available(info, changelog)
    }

    private fun getText(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "*/*")
            if (connection.responseCode !in 200..299) {
                NeoLog.w(NeoLog.UI) { "更新：$url 返回 ${connection.responseCode}" }
                return null
            }
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            connection.disconnect()
        }
    }

    private fun optInt(json: JSONObject, key: String): Int {
        val value = json.opt(key) ?: return 0
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: 0
            else -> 0
        }
    }

    /** 布尔标记容错：可能是 true / "true" / 1。 */
    private fun optFlag(json: JSONObject, key: String): Boolean {
        val value = json.opt(key) ?: return false
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> value.toString().trim().equals("true", ignoreCase = true)
        }
    }
}
