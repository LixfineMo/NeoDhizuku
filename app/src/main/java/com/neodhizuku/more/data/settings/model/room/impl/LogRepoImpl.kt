package com.neodhizuku.more.data.settings.model.room.impl

import com.neodhizuku.more.data.settings.model.room.DhizukuRoom
import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity
import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity
import com.neodhizuku.more.data.settings.repo.LogRepo

import kotlinx.coroutines.flow.Flow

import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class LogRepoImpl : LogRepo, KoinComponent {
    private val room: DhizukuRoom by lazy { get() }

    override fun flowAudit(): Flow<List<AuditEntity>> = room.auditDao.flowAll()

    override fun flowRuntimeLog(): Flow<List<RuntimeLogEntity>> = room.runtimeLogDao.flowAll()

    override suspend fun insertAudit(entity: AuditEntity) {
        room.auditDao.insert(entity)
    }

    override suspend fun trim(retentionMillis: Long) {
        room.trimLogs(retentionMillis)
    }

    override suspend fun clearAudit() {
        room.auditDao.clear()
    }

    override suspend fun clearRuntimeLog() {
        room.runtimeLogDao.clear()
    }
}
