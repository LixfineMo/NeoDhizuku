package com.neodhizuku.more.server

import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.repo.AppRepo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap

/**
 * 把授权表在内存里缓存一份，避免每次 API 调用都在 Binder 线程上
 * runBlocking 地查一次 Room（首次还涉及开库 / 迁移）。
 * 由 DaemonService 在启动时全量载入，并在包安装 / 卸载 / 变更广播时增量更新。
 * 未载入完成时 [get] 返回 null，调用方会回退到原来的 runBlocking 查询路径，
 * 因此不会引入"缓存未就绪导致拒绝服务"的风险。
 */
object PermissionCache : KoinComponent {
    private val appRepo by inject<AppRepo>()

    private val cache = ConcurrentHashMap<Int, AppEntity>()

    private val mutex = Mutex()

    @Volatile
    var ready: Boolean = false
        private set

    /**
     * get
     * (uid: Int)
     * 作用：读取
     * 备注：—
     */
    fun get(uid: Int): AppEntity? = cache[uid]

    /**
     * put
     * (entity: AppEntity)
     * 作用：写入
     * 备注：—
     */
    fun put(entity: AppEntity) {
        cache[entity.uid] = entity
    }

    /**
     * remove
     * (uid: Int)
     * 作用：移除
     * 备注：—
     */
    fun remove(uid: Int) {
        cache.remove(uid)
    }

    /**
     * refresh
     * ()
     * 作用：刷新
     * 备注：挂起函数
     */
    suspend fun refresh() {
        mutex.withLock {
            val all = runCatching { appRepo.all() }.getOrNull()
            if (all == null) {
                ready = false
            } else {
                cache.clear()
                all.forEach { cache[it.uid] = it }
                ready = true
            }
        }
    }

    /**
     * refreshOne
     * (uid: Int)
     * 作用：刷新 One
     * 备注：挂起函数
     */
    suspend fun refreshOne(uid: Int) {
        mutex.withLock {
            val entity = runCatching { appRepo.findByUID(uid) }.getOrNull()
            if (entity == null) cache.remove(uid) else cache[uid] = entity
        }
    }
}
