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

    /**
     * flowAudit
     * ()
     * 作用：提供 Audit 数据流
     * 备注：重写父类 / 接口方法
     */
    override fun flowAudit(): Flow<List<AuditEntity>> = room.auditDao.flowAll()

    /**
     * flowRuntimeLog
     * ()
     * 作用：提供 RuntimeLog 数据流
     * 备注：重写父类 / 接口方法
     */
    override fun flowRuntimeLog(): Flow<List<RuntimeLogEntity>> = room.runtimeLogDao.flowAll()

    /**
     * insertAudit
     * (entity: AuditEntity)
     * 作用：写入 Audit
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun insertAudit(entity: AuditEntity) {
        room.auditDao.insert(entity)
    }

    /**
     * trim
     * (retentionMillis: Long)
     * 作用：执行 trim
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun trim(retentionMillis: Long) {
        room.trimLogs(retentionMillis)
    }

    /**
     * clearAudit
     * ()
     * 作用：清空 Audit
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun clearAudit() {
        room.auditDao.clear()
    }

    /**
     * clearRuntimeLog
     * ()
     * 作用：清空 RuntimeLog
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun clearRuntimeLog() {
        room.runtimeLogDao.clear()
    }
}
