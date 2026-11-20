package com.neodhizuku.more.ui.page.settings.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.BuildResult
import com.neodhizuku.more.data.common.util.UpdateChecker
import com.neodhizuku.more.data.common.util.UpdateConfig
import com.neodhizuku.more.data.common.util.UpdateLinks
import com.neodhizuku.more.data.common.util.UpdateSource
import com.neodhizuku.more.data.common.util.UrlUtil
import com.neodhizuku.more.data.settings.repo.SettingsRepo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 调试页的界面状态。 */
data class DebugState(
    val source: UpdateSource = UpdateSource.Official,
    val allowInsecure: Boolean = false,
    val customUrl: String = "",
    val officialBase: String = UpdateLinks.OFFICIAL_BASE_DEFAULT,
    val timeoutMs: Int = UpdateConfig.DEFAULT_TIMEOUT_MS
) {
    /**
     * toConfig
     * ()
     * 作用：执行 toConfig
     * 备注：—
     */
    fun toConfig(): UpdateConfig = UpdateConfig(
        source = source,
        allowInsecure = allowInsecure,
        customUrl = customUrl,
        officialBase = officialBase.ifBlank { UpdateLinks.OFFICIAL_BASE_DEFAULT },
        timeoutMs = timeoutMs
    )
}

/** 拉取最新 Build 的结果。 */
sealed interface BuildState {
    data object Idle : BuildState
    data object Loading : BuildState
    data class Found(val date: Int, val version: String, val url: String, val total: Int) : BuildState
    data class Failed(val reason: String?) : BuildState
}

class DebugViewModel : ViewModel(), KoinComponent {
    private val settingsRepo by inject<SettingsRepo>()

    var state by mutableStateOf(DebugState())
        private set

    var buildState by mutableStateOf<BuildState>(BuildState.Idle)
        private set

    /** 编辑框里的原文，保存后才写进设置 */
    var draftOfficialBase by mutableStateOf("")
        private set
    var draftCustomUrl by mutableStateOf("")
        private set
    var draftTimeout by mutableStateOf("")
        private set

    /** 保存 / 还原的结果，界面直接展示原文 */
    var message by mutableStateOf("")
        private set

    private var loaded = false

    /**
     * collect
     * ()
     * 作用：收集
     * 备注：—
     */
    fun collect() {
        if (loaded) return
        loaded = true
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUpdateSource().collect {
                state = state.copy(source = UpdateSource.of(it))
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUpdateAllowInsecure().collect {
                state = state.copy(allowInsecure = it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUpdateCustomUrl().collect {
                state = state.copy(customUrl = it)
                if (draftCustomUrl.isEmpty()) draftCustomUrl = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowDebugOfficialBase().collect {
                state = state.copy(officialBase = it)
                if (draftOfficialBase.isEmpty()) draftOfficialBase = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowDebugTimeoutMs().collect {
                state = state.copy(timeoutMs = it)
                if (draftTimeout.isEmpty()) draftTimeout = it.toString()
            }
        }
    }

    /**
     * editOfficialBase
     * (value: String)
     * 作用：执行 editOfficialBase
     * 备注：—
     */
    fun editOfficialBase(value: String) {
        draftOfficialBase = value
    }

    /**
     * editCustomUrl
     * (value: String)
     * 作用：执行 editCustomUrl
     * 备注：—
     */
    fun editCustomUrl(value: String) {
        draftCustomUrl = value
    }

    /**
     * editTimeout
     * (value: String)
     * 作用：执行 editTimeout
     * 备注：—
     */
    fun editTimeout(value: String) {
        draftTimeout = value.filter { it.isDigit() }
    }

    /**
     * setSource
     * (source: UpdateSource)
     * 作用：设置 Source
     * 备注：—
     */
    fun setSource(source: UpdateSource) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateSource = source.key
        }
    }

    /**
     * setAllowInsecure
     * (enabled: Boolean)
     * 作用：设置 AllowInsecure
     * 备注：—
     */
    fun setAllowInsecure(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isUpdateAllowInsecure = enabled
        }
    }

    /**
     * save
     * ()
     * 作用：把编辑框里的内容写进设置；超时越界时不落库，避免把检测卡死。
     * 备注：—
     */
    fun save() {
        val timeout = draftTimeout.toIntOrNull()
        if (timeout == null || timeout !in 1_000..120_000) {
            message = "timeout must be 1000..120000"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.debugOfficialBase = draftOfficialBase
            settingsRepo.updateCustomUrl = draftCustomUrl
            settingsRepo.debugTimeoutMs = timeout
            withContext(Dispatchers.Main) { message = "saved" }
        }
    }

    /**
     * reset
     * ()
     * 作用：放弃改动，回到已保存的值。
     * 备注：—
     */
    fun reset() {
        draftOfficialBase = state.officialBase
        draftCustomUrl = state.customUrl
        draftTimeout = state.timeoutMs.toString()
        message = "reverted"
    }

    /**
     * fetchLatestBuild
     * ()
     * 作用：拉官方服务器上的最新 Build，按日期号比较。
     * 备注：—
     */
    fun fetchLatestBuild() {
        if (buildState is BuildState.Loading) return
        viewModelScope.launch {
            buildState = BuildState.Loading
            val result = withContext(Dispatchers.IO) {
                UpdateChecker.fetchLatestBuild(state.toConfig()) { host ->
                    settingsRepo.debugOfficialBase = UrlUtil.baseOf(host)
                }
            }
            NeoLog.i(NeoLog.UI) { "调试：拉取最新 Build -> ${result.javaClass.simpleName}" }
            buildState = when (result) {
                is BuildResult.Found -> BuildState.Found(
                    date = result.latest.date,
                    version = result.latest.version,
                    url = result.latest.apkUrl,
                    total = result.total
                )

                is BuildResult.Failed -> BuildState.Failed(result.reason)
            }
        }
    }
}
