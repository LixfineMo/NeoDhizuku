package com.neodhizuku.more.data.settings.model.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 服务端运行日志。
 *
 * 只落 W / E 及关键 I，避免表无限膨胀；V / D 仅写入内存环形缓冲，不入库。
 * 未激活时也能查看，用于排查激活失败等问题。
 */
@Entity(
    tableName = "runtime_log",
    indices = [Index(value = ["timestamp"])]
)
data class RuntimeLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0L,
    /** V / D / I / W / E 中的 I、W、E */
    @ColumnInfo(name = "level") var level: String,
    /** 分类子标签，如 Nav / Perm / Daemon */
    @ColumnInfo(name = "sub") var sub: String = "",
    @ColumnInfo(name = "message") var message: String,
    @ColumnInfo(name = "timestamp") var timestamp: Long = System.currentTimeMillis()
)
