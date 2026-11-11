package com.neodhizuku.more.data.settings.model.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 调用记录（授权审计）。
 * 每条记录对应一次第三方应用通过 Dhizuku 调用受保护能力的请求。
 * [logExcerpt] 是该次调用发生时截取的服务端运行日志片段，便于事后追溯。
 */
@Entity(
    tableName = "audit",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["uid"])
    ]
)
data class AuditEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0L,
    @ColumnInfo(name = "uid") var uid: Int,
    @ColumnInfo(name = "package_name") var packageName: String,
    /** 内部 API 名，展示时由 AuditLogStore.describe 映射为中文短句 */
    @ColumnInfo(name = "api") var api: String,
    @ColumnInfo(name = "detail") var detail: String = "",
    /** 该次调用末尾截取的运行日志片段 */
    @ColumnInfo(name = "log_excerpt") var logExcerpt: String = "",
    @ColumnInfo(name = "timestamp") var timestamp: Long = System.currentTimeMillis()
)
