package com.neodhizuku.more.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 调用记录（授权审计）。
 *
 * 设计取舍：
 * - 只保存在内存（进程存活期间），**不落库、不持久化**，避免引入隐私合规负担与 Room 迁移；
 * - 上限 200 条，超出滚动丢弃最旧的；
 * - 展示文案全部映射为中文短句，不暴露 API 名称（见 [describe]）。
 *
 * 数据来源：MyDhizukuService.onAudit()。
 */
object AuditLogStore {

    private const val MAX_ENTRIES = 200

    data class Entry(
        val uid: Int,
        val packageName: String,
        val api: String,
        val detail: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(uid: Int, packageName: String, api: String, detail: String) {
        val next = ArrayList<Entry>(_entries.value)
        next.add(0, Entry(uid, packageName, api, detail))
        while (next.size > MAX_ENTRIES) next.removeAt(next.lastIndex)
        _entries.value = next
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /**
     * 把内部 API 名映射为用户看得懂的中文短句。
     * 未知 API 统一返回通用描述，绝不回显英文字段名。
     */
    fun describe(api: String): String = when (api) {
        "remote_process" -> "执行了一条系统命令"
        "remote_transact" -> "调用了系统内部接口"
        "bind_user_service" -> "启动了一个后台服务"
        "unbind_user_service" -> "停止了一个后台服务"
        "get_delegated_scopes" -> "读取了设备策略授权范围"
        "set_delegated_scopes" -> "修改了设备策略授权范围"
        else -> "调用了一项受保护的能力"
    }
}
