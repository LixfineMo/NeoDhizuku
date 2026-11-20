package com.neodhizuku.more.ui.page.settings.updatesettings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.OfficialEndpoint
import com.neodhizuku.more.data.common.util.UpdateConfig
import com.neodhizuku.more.data.common.util.UpdateSource
import com.neodhizuku.more.data.common.util.UrlUtil
import com.neodhizuku.more.data.settings.repo.SettingsRepo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 更新设置页的状态。 */
data class UpdateSettingsState(
    val source: UpdateSource = UpdateSource.Official,
    val allowInsecure: Boolean = false,
    val startupCheck: Boolean = true,
    val customUrl: String = "",
    val officialBase: String = "",
    val probing: Boolean = false
) {
    /**
     * toConfig
     * ()
     * 作用：把当前设置拼成检测时用的配置。
     * 备注：—
     */
    fun toConfig(): UpdateConfig = UpdateConfig(
        source = source,
        allowInsecure = allowInsecure,
        customUrl = customUrl,
        officialBase = officialBase.ifBlank { UpdateConfig().officialBase }
    )
}

class UpdateSettingsViewModel : ViewModel(), KoinComponent {
    private val settingsRepo by inject<SettingsRepo>()

    private val _state = MutableStateFlow(UpdateSettingsState())
    val state: StateFlow<UpdateSettingsState> = _state

    /** 来源选择弹窗 */
    var showSourceDialog by mutableStateOf(false)
        private set

    /**
     * reprobeOfficial
     * ()
     * 作用：重新挑官方域名。 免费解析随时会挂，挂了之后要能手动换一个，不然只能等它自己恢复。
     * 备注：—
     */
    fun reprobeOfficial() {
        if (_state.value.probing) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(probing = true)
            OfficialEndpoint.forget()
            val host = OfficialEndpoint.resolve(
                UrlUtil.hostOf(_state.value.officialBase)
            ) { picked ->
                settingsRepo.debugOfficialBase = UrlUtil.baseOf(picked)
            }
            NeoLog.i(NeoLog.UI) { "更新设置：重新探测 -> $host" }
            _state.value = _state.value.copy(probing = false)
        }
    }

    /**
     * collect
     * ()
     * 作用：收集
     * 备注：—
     */
    fun collect() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUpdateSource().collect {
                _state.value = _state.value.copy(source = UpdateSource.of(it))
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUpdateAllowInsecure().collect {
                _state.value = _state.value.copy(allowInsecure = it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowStartupUpdateCheck().collect {
                _state.value = _state.value.copy(startupCheck = it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUpdateCustomUrl().collect {
                _state.value = _state.value.copy(customUrl = it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowDebugOfficialBase().collect {
                _state.value = _state.value.copy(officialBase = it)
            }
        }
    }

    /**
     * openSourceDialog
     * ()
     * 作用：打开 SourceDialog
     * 备注：—
     */
    fun openSourceDialog() {
        showSourceDialog = true
    }

    /**
     * dismissSourceDialog
     * ()
     * 作用：关闭 SourceDialog
     * 备注：—
     */
    fun dismissSourceDialog() {
        showSourceDialog = false
    }

    /**
     * setSource
     * (source: UpdateSource)
     * 作用：设置 Source
     * 备注：—
     */
    fun setSource(source: UpdateSource) {
        NeoLog.i(NeoLog.UI) { "更新设置：来源 -> ${source.key}" }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateSource = source.key
        }
        showSourceDialog = false
    }

    /**
     * setAllowInsecure
     * (enabled: Boolean)
     * 作用：设置 AllowInsecure
     * 备注：—
     */
    fun setAllowInsecure(enabled: Boolean) {
        NeoLog.i(NeoLog.UI) { "更新设置：允许不安全链接 -> $enabled" }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isUpdateAllowInsecure = enabled
        }
    }

    /**
     * setStartupUpdateCheck
     * (enabled: Boolean)
     * 作用：设置 StartupUpdateCheck
     * 备注：—
     */
    fun setStartupUpdateCheck(enabled: Boolean) {
        NeoLog.i(NeoLog.UI) { "更新设置：启动时检查更新 -> $enabled" }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isStartupUpdateCheck = enabled
        }
    }

    /**
     * setCustomUrl
     * (url: String)
     * 作用：设置 CustomUrl
     * 备注：—
     */
    fun setCustomUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateCustomUrl = url
        }
    }
}
