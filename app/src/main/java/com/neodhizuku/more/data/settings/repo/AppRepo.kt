package com.neodhizuku.more.data.settings.repo

import com.neodhizuku.more.data.settings.model.room.entity.AppEntity

import kotlinx.coroutines.flow.Flow

interface AppRepo {
    /**
     * flowAll
     * ()
     * 作用：提供 All 数据流
     * 备注：—
     */
    fun flowAll(): Flow<List<AppEntity>>

    /**
     * flowFind
     * (id: Long)
     * 作用：提供 Find 数据流
     * 备注：—
     */
    fun flowFind(id: Long): Flow<AppEntity?>

    /**
     * flowFindByUID
     * (uid: Int)
     * 作用：提供 FindByUID 数据流
     * 备注：—
     */
    fun flowFindByUID(uid: Int): Flow<AppEntity?>

    /**
     * all
     * ()
     * 作用：执行 all
     * 备注：挂起函数
     */
    suspend fun all(): List<AppEntity>

    /**
     * find
     * (id: Long)
     * 作用：查找
     * 备注：挂起函数
     */
    suspend fun find(id: Long): AppEntity?

    /**
     * findByUID
     * (uid: Int)
     * 作用：查找 ByUID
     * 备注：挂起函数
     */
    suspend fun findByUID(uid: Int): AppEntity?

    /**
     * update
     * (entity: AppEntity)
     * 作用：更新
     * 备注：挂起函数
     */
    suspend fun update(entity: AppEntity)

    /**
     * insert
     * (entity: AppEntity)
     * 作用：写入
     * 备注：挂起函数
     */
    suspend fun insert(entity: AppEntity)

    /**
     * upsert
     * (entity: AppEntity)
     * 作用：uid 冲突时替换，避免 SQLiteConstraintException
     * 备注：挂起函数
     */
    suspend fun upsert(entity: AppEntity)

    /**
     * delete
     * (entity: AppEntity)
     * 作用：删除
     * 备注：挂起函数
     */
    suspend fun delete(entity: AppEntity)
}