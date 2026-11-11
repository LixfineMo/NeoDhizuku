package com.neodhizuku.more.data.common.util

import com.neodhizuku.more.NeoLog

import org.json.JSONArray
import org.json.JSONObject

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * 更新检测。
 * 清单里同时给出 **release / beta / alpha 三个通道**，每个通道一份完整的版本信息。
 * 这样没开"获取内测版"的用户也能正常拿到最新的 Release —— 单一版本的清单
 * 一旦仓库上挂的是 alpha，普通用户就永远被挡在门外。
 * 通道缺失时按下面的顺序退回：
 *   1. `channels` 对象里带 release / beta / alpha 三个键
 *   2. 顶层直接带 release / beta / alpha 三个键
 *   3. 兼容格式：顶层只有一个版本，靠 isAlpha / isBeta 标记判断属于哪个通道
 */
object UpdateLinks {

    /** 官方端口。四个域名共用。 */
    const val OFFICIAL_PORT: Int = 9000

    /** 官方服务器是明文 HTTP，需要配合"允许不安全的链接"。 */
    const val OFFICIAL_SCHEME: String = "http"

    /**
     * 官方服务器的候选域名。
     * 都是免费解析，随时可能挂掉，所以按顺序试，能连通的记下来下次直接用，
     * 全部不通才回到第一个 —— 这样换域名不用改代码，改这个列表就行。
     */
    val OFFICIAL_HOSTS: List<String> = listOf(
        "neodhizuku.dynv6.net",
        "neodhizuku.v6.navy",
        "neodhizuku.dns.army",
        "neodhizuku.v6.rocks"
    )

    /** 默认基址，取第一个候选域名。 */
    val OFFICIAL_BASE_DEFAULT: String =
        "$OFFICIAL_SCHEME://${OFFICIAL_HOSTS.first()}:$OFFICIAL_PORT"

    /**
     * 官方服务器的版本清单路径。
     * Build 信息也在同一个文件里（`build` 节点），与版本清单共用一个地址 ——
     * 客户端和服务端都只认这一个地址，改地址时不会漏改第二处。
     */
    const val OFFICIAL_VERSION_PATH: String = "/update.json"

    /** GitHub 仓库里的版本清单 */
    const val GITHUB_VERSION_JSON: String =
        "https://raw.githubusercontent.com/Qingyu-ch/NeoDhizuku/refs/heads/master/Update/verson.json"

    /** 清单里没有下载地址时退回仓库主页 */
    const val REPO_HOME: String = "https://github.com/Qingyu-ch/NeoDhizuku"

    /**
     * GitHub 源是否出现在选择列表里。
     * 仓库当前不可用，默认关掉；恢复时置为 true 即可让列表多出一项，
     * 不用动任何界面代码。已经选了 GitHub 的用户不受影响，照旧能拉。
     */
    const val GITHUB_ENABLED: Boolean = false
}

/** 更新拉取来源。 */
enum class UpdateSource(val key: String) {
    /** 自建服务器，明文 HTTP，需配合 [UpdateConfig.allowInsecure] */
    Official("official"),

    /** GitHub Raw */
    GitHub("github"),

    /** 用户自己填的地址 */
    Custom("custom");

    companion object {
        fun of(key: String?): UpdateSource =
            entries.firstOrNull { it.key == key } ?: Official

        /** 界面上实际列出的来源，受 [UpdateLinks.GITHUB_ENABLED] 控制。 */
        val visible: List<UpdateSource>
            get() = entries.filter { it != GitHub || UpdateLinks.GITHUB_ENABLED }
    }
}

/** 一次检测用的全部配置。 */
data class UpdateConfig(
    val source: UpdateSource = UpdateSource.Official,
    val allowInsecure: Boolean = false,
    val customUrl: String = "",
    val officialBase: String = UpdateLinks.OFFICIAL_BASE_DEFAULT,
    /** 上次连通的官方域名；空表示还没探测过 */
    val officialHost: String = "",
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000
    }

    /** 按来源算出最终地址；版本清单与 Build 都走它。 */
    val versionJsonUrl: String
        get() = when (source) {
            UpdateSource.GitHub -> UpdateLinks.GITHUB_VERSION_JSON
            UpdateSource.Custom -> UrlUtil.withDefaultPath(customUrl)
            UpdateSource.Official -> UrlUtil.withDefaultPath(officialBase)
        }
}

/** 地址处理。 */
object UrlUtil {

    /** 拿掉协议、端口和路径，只留主机名。 */
    fun hostOf(raw: String): String {
        val s = raw.trim()
        if (s.isBlank()) return ""
        val noScheme = if (s.contains("://")) s.substringAfter("://") else s
        val hostPart = noScheme.substringBefore('/')
        // [::1]:9000 这种 IPv6 带端口的写法要留着方括号里的内容
        return if (hostPart.startsWith("[")) {
            hostPart.substringAfter("[").substringBefore("]")
        } else {
            hostPart.substringBefore(':')
        }
    }

    /** 主机名拼成基址，已经是 IPv6 的自动加方括号。 */
    fun baseOf(host: String, port: Int = UpdateLinks.OFFICIAL_PORT): String {
        val h = host.trim().trim('[', ']')
        if (h.isBlank()) return ""
        val wrapped = if (h.contains(':')) "[$h]" else h
        return "${UpdateLinks.OFFICIAL_SCHEME}://$wrapped:$port"
    }

    /**
     * 补齐地址：没有协议就按明文 http 补，只有主机就补上清单路径。
     * 手填的地址经常写成 `127.0.0.1:9000` 这种不带协议的写法，直接丢给 URL 会
     * 连协议都认不出来；而只填主机的话，服务端收到的是根路径请求，拿不到清单。
     * 已经带路径的（比如 `/update`）原样保留，不覆盖用户意图。
     */
    fun withDefaultPath(
        raw: String,
        path: String = UpdateLinks.OFFICIAL_VERSION_PATH
    ): String {
        var s = raw.trim()
        if (s.isBlank()) return ""
        if (!s.contains("://")) s = "${UpdateLinks.OFFICIAL_SCHEME}://$s"
        val noScheme = s.substringAfter("://")
        val tail = noScheme.substringAfter('/', "")
        if (tail.isEmpty()) return s.trimEnd('/') + path
        return s
    }

    /** 清单里可能是相对路径（服务端按来访地址下发），这里补成绝对地址。 */
    fun absolute(url: String, base: String): String {
        val u = url.trim()
        if (u.isBlank()) return ""
        if (u.startsWith("http://", true) || u.startsWith("https://", true)) return u
        val b = base.trim().trimEnd('/')
        if (b.isBlank()) return u
        return if (u.startsWith("/")) b + u else "$b/$u"
    }
}

/** 官方服务器选点：记住上次连通的域名，不通了才重新试一遍。 */
object OfficialEndpoint {

    /** 进程内缓存，避免一次会话里反复探测。 */
    @Volatile
    private var cached: String = ""

    /** 探测超时，比清单读取短得多，只判断端口通不通。 */
    private const val PROBE_TIMEOUT_MS = 1_500

    fun remember(host: String) {
        if (host.isNotBlank()) cached = host.trim()
    }

    fun forget() {
        cached = ""
    }

    /** 调试用：读/写进程内的选中域名缓存。 */
    fun debugCachedHost(): String = cached

    fun setDebugCachedHost(host: String) {
        cached = host.trim()
    }

    /** 端口能不能连上；DNS 解析失败也算不通。 */
    fun probe(host: String, port: Int = UpdateLinks.OFFICIAL_PORT): Boolean {
        val h = host.trim().trim('[', ']')
        if (h.isBlank()) return false
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(h, port), PROBE_TIMEOUT_MS)
                true
            }
        }.getOrDefault(false)
    }

    /**
     * 挑一个能用的官方域名。
     * 先试记下来的那个，不通才逐个试列表；命中就回传给调用方存进设置，
     * 下次启动直接用，不用每次都解析一轮。全不通时返回第一个，让上层按正常
     * 失败流程处理，而不是在这里把错误吞掉。
     */
    fun resolve(
        savedHost: String,
        port: Int = UpdateLinks.OFFICIAL_PORT,
        onPicked: (String) -> Unit = {}
    ): String {
        val order = ArrayList<String>(UpdateLinks.OFFICIAL_HOSTS.size + 1)
        if (cached.isNotBlank()) order.add(cached)
        if (savedHost.isNotBlank()) order.add(savedHost.trim())
        for (host in UpdateLinks.OFFICIAL_HOSTS) order.add(host)

        val seen = HashSet<String>()
        for (host in order) {
            val h = host.trim()
            if (h.isBlank() || !seen.add(h.lowercase())) continue
            if (!probe(h, port)) {
                NeoLog.d(NeoLog.UI) { "更新：域名不通 $h" }
                continue
            }
            if (h != cached) {
                cached = h
                onPicked(h)
            }
            NeoLog.i(NeoLog.UI) { "更新：已选定官方域名 $h" }
            return h
        }
        NeoLog.w(NeoLog.UI) { "更新：所有官方域名都不通" }
        return UpdateLinks.OFFICIAL_HOSTS.first()
    }
}

/** 发布通道。 */
enum class UpdateChannel(val key: String) {
    Release("release"),
    Beta("beta"),
    Alpha("alpha");

    companion object {
        fun of(key: String?): UpdateChannel? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/** 单个通道的版本信息。 */
data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelogUrl: String,
    val isAlpha: Boolean,
    val isBeta: Boolean,
    val isGrayRelease: Boolean,
    val channel: UpdateChannel = when {
        isAlpha -> UpdateChannel.Alpha
        isBeta -> UpdateChannel.Beta
        else -> UpdateChannel.Release
    },
    /** 构建日期，形如 20261010；用于 Build 通道比较，无则为 0 */
    val date: Int = 0,
    /** 清单里的"最新版本链接"，下载地址为空时退回它 */
    val latestUrl: String = ""
) {
    /** 非正式版本：alpha、beta 与灰度都算。 */
    val isPreRelease: Boolean get() = isAlpha || isBeta || isGrayRelease

    /** 实际可用的下载地址。 */
    val downloadUrl: String get() = apkUrl.ifBlank { latestUrl }
}

/** 一次拉到的完整清单。 */
data class UpdateManifest(
    val channels: Map<UpdateChannel, UpdateInfo>,
    val latestUrl: String
) {
    fun get(channel: UpdateChannel): UpdateInfo? = channels[channel]
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

/** Build 索引里的一条。 */
data class BuildEntry(
    val date: Int,
    val version: String,
    val versionCode: Int,
    val apkUrl: String
)

/** 拉取 Build 索引的结果。 */
sealed interface BuildResult {
    data class Found(val latest: BuildEntry, val total: Int) : BuildResult
    data class Failed(val reason: String?) : BuildResult
}

object UpdateChecker {

    /**
     * 取版本清单。
     * 字段类型不保证一致 —— `isAlpha` 一类的标记在仓库里是字符串 `"true"`，
     * 版本号也可能是字符串，所以统一按容错方式读；读不出来就当作 0 / false，
     * 宁可判成"无更新"也不能因为解析问题弹个假更新。
     */
    fun fetch(
        config: UpdateConfig,
        onHostPicked: (String) -> Unit = {}
    ): UpdateManifest? = runCatching {
        val resolved = resolveOfficial(config, onHostPicked)
        val url = resolved.versionJsonUrl
        if (url.isBlank()) {
            NeoLog.w(NeoLog.UI) { "更新：清单地址为空" }
            return null
        }
        if (!isUrlAllowed(url, resolved.allowInsecure)) {
            NeoLog.w(NeoLog.UI) { "更新：已拒绝不安全的地址 $url" }
            return null
        }
        NeoLog.i(NeoLog.UI) { "更新：拉取清单 $url" }
        val text = getText(url, resolved.timeoutMs) ?: return null
        parseManifest(JSONObject(text), linkBaseOf(resolved))
    }.onFailure {
        NeoLog.w(NeoLog.UI) { "更新：读取版本清单失败：${it.message}" }
    }.getOrNull()

    /** 取更新日志正文；地址为空或取不到都返回空串。 */
    fun fetchChangelog(url: String, config: UpdateConfig): String {
        if (url.isBlank()) return ""
        if (!isUrlAllowed(url, config.allowInsecure)) return ""
        return runCatching { getText(url, config.timeoutMs).orEmpty() }
            .onFailure {
                NeoLog.w(NeoLog.UI) { "更新：读取更新日志失败：${it.message}" }
            }
            .getOrDefault("")
    }

    /**
     * 与本地版本比对。
     * 未开非正式版时只看 Release 通道；开了之后在三个通道里挑 versionCode 最大的那个。
     * @param currentCode 本地 versionCode
     * @param allowPreRelease 是否把非正式版本也算作可更新
     */
    fun compare(
        manifest: UpdateManifest?,
        currentCode: Int,
        allowPreRelease: Boolean,
        config: UpdateConfig = UpdateConfig()
    ): UpdateResult {
        if (manifest == null) return UpdateResult.Failed(null)

        val pool = if (allowPreRelease) {
            listOf(UpdateChannel.Release, UpdateChannel.Beta, UpdateChannel.Alpha)
        } else {
            listOf(UpdateChannel.Release)
        }

        val candidates = pool.mapNotNull { manifest.get(it) }
            .filter { it.versionCode > currentCode }
        if (candidates.isEmpty()) {
            // 三个通道都没有比本地新的版本时，若非正式通道里有新版，仍提示被挡下
            val blocked = manifest.channels
                .filterKeys { it != UpdateChannel.Release }
                .values
                .filter { it.versionCode > currentCode }
                .maxByOrNull { it.versionCode }
            return if (blocked != null && !allowPreRelease) {
                UpdateResult.PreReleaseBlocked(blocked)
            } else {
                UpdateResult.UpToDate
            }
        }

        val best = candidates.maxBy { it.versionCode }
        if (best.isPreRelease && !allowPreRelease) {
            return UpdateResult.PreReleaseBlocked(best)
        }
        val changelog = fetchChangelog(best.changelogUrl, config)
        return UpdateResult.Available(best, changelog)
    }

    /**
     * 从服务器拉最新 Build。
     * 读的是**同一份**版本清单（[UpdateConfig.versionJsonUrl]）里的 `build` 节点，
     * 读同一份版本清单，只比日期号（形如 20261010），不比 versionCode ——
     * 同一天的多个构建版本号可能倒着来，日期才是唯一的先后依据。
     */
    fun fetchLatestBuild(
        config: UpdateConfig,
        onHostPicked: (String) -> Unit = {}
    ): BuildResult = runCatching {
        val resolved = resolveOfficial(config, onHostPicked)
        val url = resolved.versionJsonUrl
        if (!isUrlAllowed(url, resolved.allowInsecure)) {
            NeoLog.w(NeoLog.UI) { "构建：已拒绝不安全的地址 $url" }
            return BuildResult.Failed("insecure")
        }
        NeoLog.i(NeoLog.UI) { "构建：拉取索引 $url" }
        val text = getText(url, resolved.timeoutMs) ?: return BuildResult.Failed(null)
        val base = linkBaseOf(resolved)
        val entries = parseBuilds(JSONObject(text)).map {
            it.copy(apkUrl = UrlUtil.absolute(it.apkUrl, base))
        }
        val latest = entries.maxByOrNull { it.date } ?: return BuildResult.Failed(null)
        BuildResult.Found(latest, entries.size)
    }.onFailure {
        NeoLog.w(NeoLog.UI) { "构建：读取索引失败：${it.message}" }
    }.getOrDefault(BuildResult.Failed(null))

    /** 明文地址默认拒绝；开了开关才放行。 */
    fun isUrlAllowed(url: String, allowInsecure: Boolean): Boolean {
        if (allowInsecure) return true
        return url.startsWith("https://", ignoreCase = true)
    }

    /**
     * 解析清单。
     * 三种写法都认，见类注释。解析不出来返回空清单而不是抛异常，
     * 让上层统一走"检测失败"，避免把解析问题显示成"发现新版本"。
     */
    fun parseManifest(json: JSONObject, base: String = ""): UpdateManifest {
        val latestUrl = UrlUtil.absolute(
            json.optString("latest").trim()
                .ifBlank { json.optString("latestUrl").trim() },
            base
        )

        val channels = linkedMapOf<UpdateChannel, UpdateInfo>()

        // 写法一 / 二：channels 对象，或直接放在顶层
        val container = json.optJSONObject("channels") ?: json
        for (channel in UpdateChannel.entries) {
            val obj = container.optJSONObject(channel.key) ?: continue
            parseEntry(obj, latestUrl, base)?.let { channels[channel] = it }
        }

        // 写法三：兼容格式，顶层只有一个版本
        if (channels.isEmpty()) {
            parseEntry(json, latestUrl, base)?.let { channels[it.channel] = it }
        }

        return UpdateManifest(channels, latestUrl)
    }

    /**
     * 官方源才需要选域名；用户在调试页填了自己的地址（内网 IP 之类）就照用，
     * 不做探测 —— 探测自家局域网没有任何意义，还会把连接拖慢。
     */
    private fun resolveOfficial(
        config: UpdateConfig,
        onHostPicked: (String) -> Unit
    ): UpdateConfig {
        if (config.source != UpdateSource.Official) return config
        val configured = UrlUtil.hostOf(config.officialBase)
        if (configured.isNotBlank() &&
            !UpdateLinks.OFFICIAL_HOSTS.any { it.equals(configured, ignoreCase = true) }
        ) {
            return config
        }
        val host = OfficialEndpoint.resolve(configured, onPicked = onHostPicked)
        return config.copy(officialBase = UrlUtil.baseOf(host))
    }

    /** 清单里的相对地址要按这个基址补全，下载才不会绕到公网。 */
    private fun linkBaseOf(config: UpdateConfig): String = when (config.source) {
        UpdateSource.Official -> config.officialBase
        UpdateSource.Custom -> {
            val raw = UrlUtil.withDefaultPath(config.customUrl)
            val noScheme = raw.substringAfter("://")
            UrlUtil.baseOf(UrlUtil.hostOf(raw), portOf(noScheme))
        }

        UpdateSource.GitHub -> ""
    }

    private fun portOf(noScheme: String): Int {
        val hostPart = noScheme.substringBefore('/')
        val tail = if (hostPart.startsWith("[")) {
            hostPart.substringAfter(']').removePrefix(":")
        } else {
            hostPart.substringAfter(':', "")
        }
        return tail.toIntOrNull() ?: UpdateLinks.OFFICIAL_PORT
    }

    private fun parseEntry(json: JSONObject, latestUrl: String, base: String): UpdateInfo? {
        val hasAny = json.has("version") || json.has("versionCode") || json.has("apkUrl")
        if (!hasAny) return null

        val isAlpha = optFlag(json, "isAlpha")
        val isBeta = optFlag(json, "isBeta")
        val channelKey = json.optString("channel").trim()
        val channel = UpdateChannel.of(channelKey)
            ?: when {
                isAlpha -> UpdateChannel.Alpha
                isBeta -> UpdateChannel.Beta
                else -> UpdateChannel.Release
            }

        return UpdateInfo(
            version = json.optString("version").trim(),
            versionCode = optInt(json, "versionCode"),
            apkUrl = UrlUtil.absolute(
                json.optString("apkUrl").trim().ifBlank { json.optString("url").trim() },
                base
            ),
            changelogUrl = UrlUtil.absolute(
                json.optString("changelog").trim()
                    .ifBlank { json.optString("changelogUrl").trim() },
                base
            ),
            isAlpha = isAlpha,
            isBeta = isBeta,
            isGrayRelease = optFlag(json, "isGrayRelease"),
            channel = channel,
            date = optInt(json, "date"),
            latestUrl = UrlUtil.absolute(
                json.optString("latestUrl").trim(),
                base
            ).ifBlank { latestUrl }
        )
    }

    /**
     * 解析 Build 信息，三种写法都认：
     *   1. 顶层 `build` 对象（服务端现在用的写法）
     *   2. 顶层 `builds` 数组，取 date 最大的那条
     *   3. 都没有时退回 alpha 通道的 date —— 仅含通道信息的清单
     * 每项至少要有一个大于 0 的 date，否则无法比较先后，直接丢弃。
     */
    fun parseBuilds(json: JSONObject): List<BuildEntry> {
        val result = ArrayList<BuildEntry>()

        json.optJSONObject("build")?.let { obj ->
            val date = optInt(obj, "date")
            if (date > 0) result.add(buildEntry(obj, date))
        }

        val array: JSONArray? = json.optJSONArray("builds")
        if (array != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val date = optInt(obj, "date")
                if (date <= 0) continue
                result.add(buildEntry(obj, date))
            }
        }

        if (result.isEmpty()) {
            val container = json.optJSONObject("channels") ?: json
            container.optJSONObject(UpdateChannel.Alpha.key)?.let { obj ->
                val date = optInt(obj, "date")
                if (date > 0) result.add(buildEntry(obj, date))
            }
        }

        return result
    }

    private fun buildEntry(obj: JSONObject, date: Int): BuildEntry = BuildEntry(
        date = date,
        version = obj.optString("version").trim(),
        versionCode = optInt(obj, "versionCode"),
        apkUrl = obj.optString("apkUrl").trim()
            .ifBlank { obj.optString("url").trim() }
    )

    private fun getText(url: String, timeoutMs: Int): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
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
