package com.neodhizuku.more.data.settings.repo

import com.neodhizuku.more.data.settings.model.room.entity.AppEntity

import kotlinx.coroutines.flow.Flow

interface AppRepo {
    fun flowAll(): Flow<List<AppEntity>>

    fun flowFind(id: Long): Flow<AppEntity?>

    fun flowFindByUID(uid: Int): Flow<AppEntity?>

    suspend fun all(): List<AppEntity>

    suspend fun find(id: Long): AppEntity?

    suspend fun findByUID(uid: Int): AppEntity?

    suspend fun update(entity: AppEntity)

    suspend fun insert(entity: AppEntity)

    /** uid 冲突时替换，避免 SQLiteConstraintException */
    suspend fun upsert(entity: AppEntity)

    suspend fun delete(entity: AppEntity)
}