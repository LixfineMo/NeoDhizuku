package com.neodhizuku.more.ui.page.settings.experimental

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.settings.repo.SettingsRepo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 实验性功能。
 * 与设置里其它开关的区别：这些是**未经验证的修复**，默认全部关闭。
 * 关闭时走原有逻辑，开启后才走修复后的分支 —— 两套逻辑并存，
 * 出问题时可以一键退回对照。
 */
class ExperimentalViewModel : ViewModel(), KoinComponent {
    private val settingsRepo by inject<SettingsRepo>()

    var state by mutableStateOf(ExperimentalViewState())
        private set

    /**
     * 待确认的开关：非空时界面弹出警告。
     * 只有从"关闭"切到"开启"才需要确认；关掉不用拦。
     */
    var pendingEnable by mutableStateOf<ExperimentalFeature?>(null)
        private set

    fun collect() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowExperimentalQuickActivateFix().collect {
                state = state.copy(quickActivateFix = it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowExperimentalDelegationCleanup().collect {
                state = state.copy(delegationCleanup = it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowExperimentalVerboseLog().collect {
                state = state.copy(verboseLog = it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowExperimentalStrictWhitelist().collect {
                state = state.copy(strictWhitelist = it)
            }
        }
    }

    /**
     * 请求切换某个开关。
     * 开启会先弹警告（[pendingEnable]），确认后才真正写入。
     */
    fun requestToggle(feature: ExperimentalFeature, enabled: Boolean) {
        if (enabled) {
            pendingEnable = feature
            return
        }
        apply(feature, false)
    }

    /** 警告弹窗点了确认。 */
    fun confirmPending() {
        val feature = pendingEnable ?: return
        pendingEnable = null
        apply(feature, true)
    }

    /** 警告弹窗取消或关闭。 */
    fun dismissPending() {
        pendingEnable = null
    }

    private fun apply(feature: ExperimentalFeature, enabled: Boolean) {
        NeoLog.i(NeoLog.UI) { "实验性功能 ${feature.name} -> $enabled" }
        viewModelScope.launch(Dispatchers.IO) {
            when (feature) {
                ExperimentalFeature.QuickActivateFix ->
                    settingsRepo.isExperimentalQuickActivateFix = enabled

                ExperimentalFeature.DelegationCleanup ->
                    settingsRepo.isExperimentalDelegationCleanup = enabled

                ExperimentalFeature.VerboseLog ->
                    settingsRepo.isExperimentalVerboseLog = enabled

                ExperimentalFeature.StrictWhitelist ->
                    settingsRepo.isExperimentalStrictWhitelist = enabled
            }
        }
    }
}

/** 实验性功能项 */
enum class ExperimentalFeature {
    /** 免删账号激活缺陷修复 */
    QuickActivateFix,

    /** 权限委派残留清理 */
    DelegationCleanup,

    /** 完整运行日志 */
    VerboseLog,

    /** 使用更严格的白名单校验 */
    StrictWhitelist
}

data class ExperimentalViewState(
    /** 免删账号激活缺陷修复，默认关闭 */
    val quickActivateFix: Boolean = false,
    /** 权限委派残留清理，默认关闭 */
    val delegationCleanup: Boolean = false,
    /** 完整运行日志，默认关闭 */
    val verboseLog: Boolean = false,
    /** 使用更严格的白名单校验，默认关闭 */
    val strictWhitelist: Boolean = false
)
