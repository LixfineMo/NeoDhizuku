package com.neodhizuku.more.data.settings.model.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

import com.neodhizuku.more.data.settings.model.room.entity.AppEntity

import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    /**
     * flowAll
     * ()
     * 作用：提供 All 数据流
     * 备注：—
     */
    @Query("select * from app")
    fun flowAll(): Flow<List<AppEntity>>

    /**
     * flowFind
     * (id: Long)
     * 作用：提供 Find 数据流
     * 备注：—
     */
    @Query("select * from app where id = :id limit 1")
    fun flowFind(id: Long): Flow<AppEntity?>

    /**
     * flowFindByUID
     * (uid: Int)
     * 作用：提供 FindByUID 数据流
     * 备注：—
     */
    @Query("select * from app where uid = :uid limit 1")
    fun flowFindByUID(uid: Int): Flow<AppEntity?>

    /**
     * all
     * ()
     * 作用：执行 all
     * 备注：挂起函数
     */
    @Query("select * from app")
    suspend fun all(): List<AppEntity>

    /**
     * find
     * (id: Long)
     * 作用：查找
     * 备注：挂起函数
     */
    @Query("select * from app where id = :id limit 1")
    suspend fun find(id: Long): AppEntity?

    /**
     * findByUID
     * (uid: Int)
     * 作用：查找 ByUID
     * 备注：挂起函数
     */
    @Query("select * from app where uid = :uid limit 1")
    suspend fun findByUID(uid: Int): AppEntity?

    /**
     * update
     * (entity: AppEntity)
     * 作用：更新
     * 备注：挂起函数
     */
    @Update
    suspend fun update(entity: AppEntity)

    /**
     * insert
     * (entity: AppEntity)
     * 作用：写入
     * 备注：挂起函数
     */
    @Insert
    suspend fun insert(entity: AppEntity)

    /**
     * upsert
     * (entity: AppEntity)
     * 作用：uid 列有唯一索引，@Insert 默认 OnConflictStrategy.ABORT， 并发写入时会抛 SQLiteConstraintException，因此这里用 REPLACE。
     * 备注：挂起函数
     */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppEntity)

    /**
     * delete
     * (entity: AppEntity)
     * 作用：删除
     * 备注：挂起函数
     */
    @Delete
    suspend fun delete(entity: AppEntity)
}