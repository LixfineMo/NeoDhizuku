package com.neodhizuku.more.ui.page.settings.calllog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity
import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity
import com.neodhizuku.more.data.settings.repo.LogRepo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 保留时长选项 */
enum class LogRetention(val millis: Long?) {
    DAYS_7(7 * 24 * 3600 * 1000L),
    DAYS_30(30 * 24 * 3600 * 1000L),
    DAYS_90(90 * 24 * 3600 * 1000L),
    FOREVER(null)
}

data class CallLogState(
    val audit: List<AuditEntity> = emptyList(),
    val runtime: List<RuntimeLogEntity> = emptyList(),
    val loading: Boolean = true
)

class CallLogViewModel : ViewModel(), KoinComponent {

    private val logRepo by inject<LogRepo>()

    private val _state = MutableStateFlow(CallLogState())
    val state: StateFlow<CallLogState> = _state.asStateFlow()

    /** 默认 30 天 */
    var retention: LogRetention = LogRetention.DAYS_30
        private set

    fun collect() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            launch {
                logRepo.flowAudit().collect { list ->
                    _state.value = _state.value.copy(audit = list, loading = false)
                }
            }
            launch {
                logRepo.flowRuntimeLog().collect { list ->
                    _state.value = _state.value.copy(runtime = list)
                }
            }
            trimAsync()
        }
    }

    /** 非挂起版本：内部自起协程 */
    private fun trimAsync() {
        viewModelScope.launch(Dispatchers.IO) { trim() }
    }

    /** 启动时按保留时长清理一次过期记录 */
    private suspend fun trim() {
        if (!NeoDhizuku.on(NeoDhizuku.UseCallLogPersist)) return
        val millis = retention.millis ?: return
        runCatching { logRepo.trim(millis) }
            .onFailure { NeoLog.w(NeoLog.UI) { "log trim failed: ${it.message}" } }
    }

    fun setRetention(value: LogRetention) {
        retention = value
        trimAsync()
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                logRepo.clearAudit()
                logRepo.clearRuntimeLog()
            }
            NeoLog.i(NeoLog.UI) { "logs cleared" }
        }
    }
}
