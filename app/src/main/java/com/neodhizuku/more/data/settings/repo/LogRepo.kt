package com.neodhizuku.more.data.settings.repo

import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity
import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity

import kotlinx.coroutines.flow.Flow

/** 调用记录与运行日志的仓储接口。 */
interface LogRepo {
    /**
     * flowAudit
     * ()
     * 作用：提供 Audit 数据流
     * 备注：—
     */
    fun flowAudit(): Flow<List<AuditEntity>>
    /**
     * flowRuntimeLog
     * ()
     * 作用：提供 RuntimeLog 数据流
     * 备注：—
     */
    fun flowRuntimeLog(): Flow<List<RuntimeLogEntity>>

    /**
     * insertAudit
     * (entity: AuditEntity)
     * 作用：写入 Audit
     * 备注：挂起函数
     */
    suspend fun insertAudit(entity: AuditEntity)

    /**
     * trim
     * (retentionMillis: Long)
     * 作用：执行 trim
     * 备注：挂起函数
     */
    suspend fun trim(retentionMillis: Long)

    /**
     * clearAudit
     * ()
     * 作用：清空 Audit
     * 备注：挂起函数
     */
    suspend fun clearAudit()

    /**
     * clearRuntimeLog
     * ()
     * 作用：清空 RuntimeLog
     * 备注：挂起函数
     */
    suspend fun clearRuntimeLog()
}
