package com.neodhizuku.more.data.settings.model.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity

import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    /**
     * insert
     * (entity: AuditEntity)
     * 作用：写入
     * 备注：挂起函数
     */
    @Insert
    suspend fun insert(entity: AuditEntity)

    /**
     * insertAll
     * (entities: List<AuditEntity>)
     * 作用：写入 All
     * 备注：挂起函数
     */
    @Insert
    suspend fun insertAll(entities: List<AuditEntity>)

    /**
     * flowAll
     * ()
     * 作用：倒序：最新的在最上面
     * 备注：—
     */
    @Query("select * from audit order by timestamp desc")
    fun flowAll(): Flow<List<AuditEntity>>

    /**
     * flowSince
     * (from: Long)
     * 作用：按时间窗口查询，用于按日分组
     * 备注：—
     */
    @Query("select * from audit where timestamp >= :from order by timestamp desc")
    fun flowSince(from: Long): Flow<List<AuditEntity>>

    /**
     * deleteBefore
     * (before: Long)
     * 作用：删除 Before
     * 备注：挂起函数
     */
    @Query("delete from audit where timestamp < :before")
    suspend fun deleteBefore(before: Long)

    /**
     * clear
     * ()
     * 作用：清空
     * 备注：挂起函数
     */
    @Query("delete from audit")
    suspend fun clear()
}
