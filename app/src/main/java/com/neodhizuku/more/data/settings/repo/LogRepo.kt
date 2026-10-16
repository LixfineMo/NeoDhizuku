package com.neodhizuku.more.data.settings.repo

import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity
import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity

import kotlinx.coroutines.flow.Flow

/** 调用记录与运行日志的仓储接口。 */
interface LogRepo {
    fun flowAudit(): Flow<List<AuditEntity>>
    fun flowRuntimeLog(): Flow<List<RuntimeLogEntity>>

    suspend fun insertAudit(entity: AuditEntity)

    suspend fun trim(retentionMillis: Long)

    suspend fun clearAudit()

    suspend fun clearRuntimeLog()
}
