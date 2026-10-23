package com.neodhizuku.more.ui.page.settings.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.UpdateChecker
import com.neodhizuku.more.data.common.util.UpdateInfo
import com.neodhizuku.more.data.common.util.UpdateResult
import com.neodhizuku.more.data.settings.repo.SettingsRepo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 关于页：更新检测与灰度开关。
 *
 * 检测始终走网络，所以只在用户点按钮时才发起；开关只决定
 * 非正式版本算不算"可更新"，不影响是否发起请求。
 */
class AboutViewModel : ViewModel(), KoinComponent {
    private val settingsRepo by inject<SettingsRepo>()

    /** 是否把非正式版本也算作可更新 */
    var allowPreRelease by mutableStateOf(false)
        private set

    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    fun collect() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUpdateAllowPreRelease().collect {
                allowPreRelease = it
            }
        }
    }

    fun updateAllowPreRelease(enabled: Boolean) {
        NeoLog.i(NeoLog.UI) { "更新：灰度与非正式更新 -> $enabled" }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isUpdateAllowPreRelease = enabled
        }
    }

    /** 手动检测更新。 */
    fun check() {
        if (updateState is UpdateState.Checking) return
        viewModelScope.launch {
            updateState = UpdateState.Checking
            val result = withContext(Dispatchers.IO) {
                UpdateChecker.compare(
                    info = UpdateChecker.fetch(),
                    currentCode = BuildConfig.VERSION_CODE,
                    allowPreRelease = allowPreRelease
                )
            }
            NeoLog.i(NeoLog.UI) { "更新：检测结果 ${result.javaClass.simpleName}" }
            updateState = when (result) {
                is UpdateResult.UpToDate -> UpdateState.UpToDate
                is UpdateResult.Available ->
                    UpdateState.Available(result.info, result.changelog)

                is UpdateResult.PreReleaseBlocked ->
                    UpdateState.PreReleaseBlocked(result.info)

                is UpdateResult.Failed -> UpdateState.Failed(result.reason)
            }
        }
    }

    /** 关掉更新提示。 */
    fun dismissUpdate() {
        updateState = UpdateState.Idle
    }
}

/** 更新检测的界面状态。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo, val changelog: String) : UpdateState
    data class PreReleaseBlocked(val info: UpdateInfo) : UpdateState
    data class Failed(val reason: String?) : UpdateState
}
