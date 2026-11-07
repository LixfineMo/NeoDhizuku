package com.neodhizuku.more.ui.page.settings.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.data.settings.repo.DenyMode
import com.neodhizuku.more.data.settings.repo.InsufficientMode
import com.neodhizuku.more.data.settings.repo.SettingsRepo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SecurityViewModel : ViewModel(), KoinComponent {
    private val settingsRepo by inject<SettingsRepo>()

    var state by mutableStateOf(SecurityViewState())
        private set

    fun collect() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowAllowOwnerTransfer().collect { allow ->
                state = state.copy(allowOwnerTransfer = allow)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowDenyMode().collect { mode ->
                state = state.copy(denyMode = DenyMode.from(mode))
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowDhizukuEnabled().collect { enabled ->
                state = state.copy(dhizukuEnabled = enabled)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowWhitelistMode().collect { enabled ->
                state = state.copy(whitelistMode = enabled)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUseLegacyVersion().collect { legacy ->
                state = state.copy(useLegacyVersion = legacy)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowStrictServiceCheck().collect { strict ->
                state = state.copy(strictServiceCheck = strict)
            }
        }
    }

    fun setAllowOwnerTransfer(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isAllowOwnerTransfer = enabled
        }
    }

    fun setDenyMode(mode: DenyMode) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.denyMode = mode.name
        }
    }

    fun setDhizukuEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isDhizukuEnabled = enabled
        }
    }

    /**
     * 能力不足时的处理方式。
     *
     * 注意：本模式只在**非设备所有者**身份下才有意义 ——
     * 设备所有者可以执行全部方法，不存在能力不足。
     */
    fun setInsufficientMode(mode: InsufficientMode) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.insufficientMode = mode.name
        }
    }

    fun setWhitelistMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isWhitelistMode = enabled
        }
    }

    /**
     * 向客户端返回的版本号来源。
     *
     * 开启时返回上游 Dhizuku 的版本，关闭时返回本项目自身的版本。
     * 默认开启：部分客户端会校验版本，返回本项目的版本可能被判定为不兼容。
     */
    fun setUseLegacyVersion(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isUseLegacyVersion = enabled
        }
    }

    /**
     * 更严格的服务校验。
     *
     * 关闭（默认）时只按包名放行，只认上游权限名的硬编码客户端同样可用；
     * 开启后额外要求调用方持有本应用自身声明的权限。
     */
    fun setStrictServiceCheck(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isStrictServiceCheck = enabled
        }
    }
}

data class SecurityViewState(
    /** 允许第三方应用主动转移设备所有者，默认关闭 */
    val allowOwnerTransfer: Boolean = false,
    val denyMode: DenyMode = DenyMode.DEFAULT,
    /** API 服务总开关：关闭时第三方一律无法使用 */
    val dhizukuEnabled: Boolean = true,
    /** 仅允许白名单应用使用 */
    val whitelistMode: Boolean = false,
    /** 能力不足时的处理 */
    val insufficientMode: InsufficientMode = InsufficientMode.DEFAULT,
    /** 返回上游 Dhizuku 的版本号，默认开启 */
    val useLegacyVersion: Boolean = true,
    /** 更严格的服务校验，默认关闭 */
    val strictServiceCheck: Boolean = false
)
