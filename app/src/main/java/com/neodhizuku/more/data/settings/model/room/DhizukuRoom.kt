package com.neodhizuku.more.data.settings.model.room

import android.content.Context
import android.os.Build

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.data.settings.model.room.dao.AppDao
import com.neodhizuku.more.data.settings.model.room.dao.AuditDao
import com.neodhizuku.more.data.settings.model.room.dao.RuntimeLogDao
import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity
import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity

import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Database(
    entities = [AppEntity::class, AuditEntity::class, RuntimeLogEntity::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
abstract class DhizukuRoom : RoomDatabase() {
    companion object : KoinComponent {
        /**
         * createInstance
         * ()
         * 作用：创建 Instance
         * 备注：—
         */
        fun createInstance(): DhizukuRoom {
            var context: Context = get()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && !context.isDeviceProtectedStorage
            ) {
                context = context.createDeviceProtectedStorageContext()
            }
            val builder = Room.databaseBuilder(
                context,
                DhizukuRoom::class.java,
                "dhizuku.db",
            )
            if (NeoDhizuku.on(NeoDhizuku.FixDbDowngradeAndDeStorage)) {
                // 允许降级时重建数据库，授权记录本就可以重新生成
                builder.fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            }
            return builder.build()
        }
    }

    abstract val appDao: AppDao

    abstract val auditDao: AuditDao

    abstract val runtimeLogDao: RuntimeLogDao

    /**
     * trimLogs
     * (retentionMillis: Long)
     * 作用：执行 trimLogs
     * 备注：挂起函数
     */
    suspend fun trimLogs(retentionMillis: Long) {
        val before = System.currentTimeMillis() - retentionMillis
        auditDao.deleteBefore(before)
        runtimeLogDao.deleteBefore(before)
    }
}
