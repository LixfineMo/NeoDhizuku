package com.neodhizuku.more.server

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * 模块 -> 主进程的唯一 IPC 入口，authority 固定为
 * `com.neodhizuku.more.hook.config`（与 HookConfigClient 里的常量一致）。
 * 只实现 call()，不提供任何 query/insert/openFile —— 报到与配置都走 Bundle，
 * 没有可读的文件或 Cursor，也不对外暴露内部状态。
 * 未放行时 scope 返回 empty=true，模块加载后保持完全 no-op：
 * 没有 LSPosed / LSPatch 的设备上没人调用它，普通用户完全无感。
 */
class HookConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        HookConfigStore.init(ctx)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val e = extras ?: Bundle()
        return runCatching {
            when (method) {
                HookConfigStore.M_SCOPE -> HookConfigStore.buildConfig(e.getString("pkg").orEmpty())
                HookConfigStore.M_REGISTER -> {
                    val accepted = HookConfigStore.register(
                        pkg = e.getString("pkg").orEmpty(),
                        pid = e.getInt("pid", -1),
                        uid = e.getInt("uid", -1),
                        processName = e.getString("processName").orEmpty(),
                        framework = e.getString("framework").orEmpty(),
                        sdk = e.getInt("sdk", 0)
                    )
                    Bundle().apply { putBoolean("accepted", accepted) }
                }
                HookConfigStore.M_HEARTBEAT -> {
                    HookConfigStore.touch(e.getString("pkg").orEmpty(), e.getInt("pid", -1))
                    HookConfigStore.heartbeat()
                }
                else -> null
            }
        }.getOrNull()
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null
}
