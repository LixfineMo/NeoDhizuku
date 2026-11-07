package com.neodhizuku.more.data.common.util

import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.settings.repo.SettingsRepo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 启动时的更新检查。
 *
 * 每次打开应用都跑一次，但**只认官方服务器**：用户在"更新设置"里选的 GitHub /
 * 自定义地址不参与，这里固定走 [UpdateLinks.OFFICIAL_HOSTS] 那一组域名。
 * 这样不管用户怎么改来源，官方有没有新版本永远查得到。
 *
 * 也不做 v4 / v6 能力判断 —— 域名逐个探测，能连上哪个用哪个，没有网络栈上的
 * 前提条件。
 *
 * 结果只是"提示"，不弹窗、不打断操作：有更新就在首页顶部挂一张卡片，
 * 没有就什么都不显示。进程内只查一次，页面反复重组不会重复请求。
 */
object StartupUpdateChecker {

    sealed interface State {

        /** 还没查，或查完没有可用更新 */
        data object Idle : State

        data object Checking : State

        data object UpToDate : State

        data class Available(
            val version: String,
            val versionCode: Int,
            val downloadUrl: String,
            val changelog: String
        ) : State

        data class Failed(val reason: String?) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)

    val state: StateFlow<State> get() = _state

    private val started = AtomicBoolean(false)

    /** 调试用：本次进程是否已经发起过检查 */
    fun isStarted(): Boolean = started.get()

    /** 调试用：清掉一次性标记并回到 Idle，允许再次触发检查 */
    fun resetForDebug() {
        started.set(false)
        _state.value = State.Idle
    }

    /** 调试用：手动改状态显示，不触发网络请求 */
    fun forceState(state: State) {
        _state.value = state
    }

    /** 调试用：直接改一次性标记 */
    fun setStartedForDebug(value: Boolean) {
        started.set(value)
    }

    /**
     * 启动一次检查；重复调用直接返回。
     *
     * @param repo 用来读取超时/灰度开关，以及回写本次连通的官方域名；
     *             拿不到也能跑，只是少了记忆与灰度判断。
     */
    fun checkOnce(repo: SettingsRepo?) {
        if (!started.compareAndSet(false, true)) return
        _state.value = State.Checking
        Thread({
            try {
                val config = officialConfig(repo)
                NeoLog.i(NeoLog.UI) { "启动检查：拉取官方清单 ${config.versionJsonUrl}" }
                val manifest = UpdateChecker.fetch(config) { host ->
                    // 记下这次连通的域名，下次启动先用它，省一轮探测
                    runCatching { repo?.debugOfficialBase = UrlUtil.baseOf(host) }
                }
                val allowPre = runCatching { repo?.isUpdateAllowPreRelease ?: false }
                    .getOrDefault(false)
                val result = UpdateChecker.compare(
                    manifest = manifest,
                    currentCode = BuildConfig.VERSION_CODE,
                    allowPreRelease = allowPre,
                    config = config
                )
                _state.value = when (result) {
                    is UpdateResult.Available -> State.Available(
                        version = result.info.version,
                        versionCode = result.info.versionCode,
                        downloadUrl = result.info.downloadUrl,
                        changelog = result.changelog
                    )

                    is UpdateResult.UpToDate -> State.UpToDate
                    is UpdateResult.PreReleaseBlocked -> State.UpToDate
                    is UpdateResult.Failed -> State.Failed(result.reason)
                }
                NeoLog.i(NeoLog.UI) {
                    "启动检查：${_state.value.javaClass.simpleName}"
                }
            } catch (t: Throwable) {
                NeoLog.w(NeoLog.UI) { "启动检查异常：${t.message}" }
                _state.value = State.Failed(t.message)
            }
        }, "startup-update").start()
    }

    /**
     * 只走官方服务器的配置。
     *
     * officialBase 留空时 [UpdateChecker] 会逐个探测官方域名；用户填了内网地址
     * 也不采用 —— 那是"自定义"来源的事，这里要的是官网。但上次探测到的官方域名
     * 会保留，避免每次冷启动都等一轮探测。
     *
     * 官方是明文 HTTP，所以 allowInsecure 直接给 true：这只是读一份版本清单，
     * 不该被"允许不安全的链接"开关挡住。
     */
    private fun officialConfig(repo: SettingsRepo?): UpdateConfig {
        var base = ""
        var timeoutMs = UpdateConfig.DEFAULT_TIMEOUT_MS
        runCatching {
            val saved = UrlUtil.hostOf(repo?.debugOfficialBase.orEmpty())
            if (UpdateLinks.OFFICIAL_HOSTS.any { it.equals(saved, ignoreCase = true) }) {
                base = UrlUtil.baseOf(saved)
            }
            val t = repo?.debugTimeoutMs ?: UpdateConfig.DEFAULT_TIMEOUT_MS
            if (t > 0) timeoutMs = t
        }
        return UpdateConfig(
            source = UpdateSource.Official,
            allowInsecure = true,
            customUrl = "",
            officialBase = base,
            officialHost = "",
            timeoutMs = timeoutMs
        )
    }
}
