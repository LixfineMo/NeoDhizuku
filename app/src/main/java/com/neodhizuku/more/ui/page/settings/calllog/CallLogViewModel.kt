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

    /**
     * collect
     * ()
     * 作用：收集
     * 备注：—
     */
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

    /**
     * trimAsync
     * ()
     * 作用：非挂起版本：内部自起协程
     * 备注：仅内部使用
     */
    private fun trimAsync() {
        viewModelScope.launch(Dispatchers.IO) { trim() }
    }

    /**
     * trim
     * ()
     * 作用：启动时按保留时长清理一次过期记录
     * 备注：挂起函数；仅内部使用
     */
    private suspend fun trim() {
        if (!NeoDhizuku.on(NeoDhizuku.UseCallLogPersist)) return
        val millis = retention.millis ?: return
        runCatching { logRepo.trim(millis) }
            .onFailure { NeoLog.w(NeoLog.UI) { "log trim failed: ${it.message}" } }
    }

    /**
     * applyRetention
     * (value: LogRetention)
     * 作用：应用 Retention
     * 备注：—
     */
    fun applyRetention(value: LogRetention) {
        retention = value
        trimAsync()
    }

    /**
     * clearAll
     * ()
     * 作用：清空 All
     * 备注：—
     */
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
