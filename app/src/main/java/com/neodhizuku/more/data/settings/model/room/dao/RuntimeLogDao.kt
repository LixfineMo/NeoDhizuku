package com.neodhizuku.more.data.settings.model.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity

import kotlinx.coroutines.flow.Flow

@Dao
interface RuntimeLogDao {
    @Insert
    suspend fun insert(entity: RuntimeLogEntity)

    @Insert
    suspend fun insertAll(entities: List<RuntimeLogEntity>)

    /** 倒序：最新的在最上面 */
    @Query("select * from runtime_log order by timestamp desc")
    fun flowAll(): Flow<List<RuntimeLogEntity>>

    @Query("delete from runtime_log where timestamp < :before")
    suspend fun deleteBefore(before: Long)

    @Query("delete from runtime_log")
    suspend fun clear()
}
