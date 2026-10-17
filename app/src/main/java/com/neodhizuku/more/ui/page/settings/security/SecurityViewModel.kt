package com.neodhizuku.more.ui.page.settings.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.data.settings.repo.DenyMode
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

    fun setWhitelistMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isWhitelistMode = enabled
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
    val whitelistMode: Boolean = false
)
