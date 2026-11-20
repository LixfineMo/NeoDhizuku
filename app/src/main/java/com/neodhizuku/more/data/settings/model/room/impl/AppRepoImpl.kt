package com.neodhizuku.more.data.settings.model.room.impl

import android.content.Context
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.data.common.util.DelegationManager
import com.neodhizuku.more.data.settings.model.room.dao.AppDao
import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppRepoImpl(
    private val dao: AppDao
) : AppRepo, KoinComponent {
    private val context by inject<Context>()
    private val settingsRepo by inject<SettingsRepo>()

    private val packageManager by lazy { context.packageManager }

    /**
     * flowAll
     * ()
     * 作用：提供 All 数据流
     * 备注：重写父类 / 接口方法
     */
    override fun flowAll(): Flow<List<AppEntity>> = dao.flowAll()

    /**
     * flowFind
     * (id: Long)
     * 作用：提供 Find 数据流
     * 备注：重写父类 / 接口方法
     */
    override fun flowFind(id: Long): Flow<AppEntity?> = dao.flowFind(id)

    /**
     * flowFindByUID
     * (uid: Int)
     * 作用：提供 FindByUID 数据流
     * 备注：重写父类 / 接口方法
     */
    override fun flowFindByUID(uid: Int): Flow<AppEntity?> = dao.flowFindByUID(uid)

    /**
     * all
     * ()
     * 作用：执行 all
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun all(): List<AppEntity> = dao.all()

    /**
     * find
     * (id: Long)
     * 作用：查找
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun find(id: Long): AppEntity? = dao.find(id)

    /**
     * findByUID
     * (uid: Int)
     * 作用：查找 ByUID
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun findByUID(uid: Int): AppEntity? = dao.findByUID(uid)

    /**
     * update
     * (entity: AppEntity)
     * 作用：更新
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun update(entity: AppEntity) {
        if (!entity.allowApi || entity.blocked) {
            clearDelegatedScopes(entity)
        }
        entity.modifiedAt = System.currentTimeMillis()
        dao.update(entity)
    }

    /**
     * insert
     * (entity: AppEntity)
     * 作用：写入
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun insert(entity: AppEntity) {
        entity.createdAt = System.currentTimeMillis()
        entity.modifiedAt = System.currentTimeMillis()
        // 改用 upsert，uid 唯一索引冲突时替换而非抛异常
        if (NeoDhizuku.on(NeoDhizuku.FixPermissionResultWrite)) dao.upsert(entity)
        else dao.insert(entity)
    }

    /**
     * upsert
     * (entity: AppEntity)
     * 作用：执行 upsert
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun upsert(entity: AppEntity) {
        if (entity.createdAt == 0L) entity.createdAt = System.currentTimeMillis()
        entity.modifiedAt = System.currentTimeMillis()
        dao.upsert(entity)
    }

    /**
     * delete
     * (entity: AppEntity)
     * 作用：删除
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun delete(entity: AppEntity) {
        clearDelegatedScopes(entity)
        dao.delete(entity)
    }

    /**
     * 取消授权 / 删除记录时清掉已委派的作用域。
     * @param restore 是否先还原已生效的状态，由实验性开关决定。
     */
    private fun clearDelegatedScopes(entity: AppEntity) {
        DelegationManager.clearForUid(
            context = context,
            uid = entity.uid,
            restore = settingsRepo.isExperimentalDelegationCleanup
        )
    }
}
