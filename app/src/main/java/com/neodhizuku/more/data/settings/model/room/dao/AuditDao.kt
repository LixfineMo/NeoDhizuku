package com.neodhizuku.more.data.settings.model.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity

import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entity: AuditEntity)

    @Insert
    suspend fun insertAll(entities: List<AuditEntity>)

    /** 倒序：最新的在最上面 */
    @Query("select * from audit order by timestamp desc")
    fun flowAll(): Flow<List<AuditEntity>>

    /** 按时间窗口查询，用于按日分组 */
    @Query("select * from audit where timestamp >= :from order by timestamp desc")
    fun flowSince(from: Long): Flow<List<AuditEntity>>

    @Query("delete from audit where timestamp < :before")
    suspend fun deleteBefore(before: Long)

    @Query("delete from audit")
    suspend fun clear()
}
