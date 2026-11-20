package com.neodhizuku.more.data.settings.model.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity

import kotlinx.coroutines.flow.Flow

@Dao
interface RuntimeLogDao {
    /**
     * insert
     * (entity: RuntimeLogEntity)
     * 作用：写入
     * 备注：挂起函数
     */
    @Insert
    suspend fun insert(entity: RuntimeLogEntity)

    /**
     * insertAll
     * (entities: List<RuntimeLogEntity>)
     * 作用：写入 All
     * 备注：挂起函数
     */
    @Insert
    suspend fun insertAll(entities: List<RuntimeLogEntity>)

    /**
     * flowAll
     * ()
     * 作用：倒序：最新的在最上面
     * 备注：—
     */
    @Query("select * from runtime_log order by timestamp desc")
    fun flowAll(): Flow<List<RuntimeLogEntity>>

    /**
     * deleteBefore
     * (before: Long)
     * 作用：删除 Before
     * 备注：挂起函数
     */
    @Query("delete from runtime_log where timestamp < :before")
    suspend fun deleteBefore(before: Long)

    /**
     * clear
     * ()
     * 作用：清空
     * 备注：挂起函数
     */
    @Query("delete from runtime_log")
    suspend fun clear()
}
