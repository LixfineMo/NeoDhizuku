package com.neodhizuku.more.server

import android.content.Context
import android.os.Build

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog

import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

import java.io.File

/**
 * 完全体记录，由共享服务页勾选维护。
 * 私有文件 + 内存镜像，服务端在 binder 线程上同步判定，不能挂起等 IO。
 * 记录 uid 与授权时的签名，签名变化后自动失效。
 */
object FullAccessStore : KoinComponent {

    private const val FILE_NAME = "full_access.json"
    private const val KEY_GRANTS = "grants"
    private const val KEY_UID = "uid"
    private const val KEY_SIGNATURE = "signature"
    private const val KEY_AT = "at"

    data class Grant(val uid: Int, val signature: String, val at: Long)

    @Volatile
    private var snapshot: Map<Int, Grant> = emptyMap()

    @Volatile
    private var loaded = false

    private val storageContext: Context by lazy {
        val ctx: Context = get()
        if (NeoDhizuku.on(NeoDhizuku.FixDbDowngradeAndDeStorage)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && !ctx.isDeviceProtectedStorage
        ) {
            ctx.createDeviceProtectedStorageContext()
        } else {
            ctx
        }
    }

    private val file: File by lazy { File(storageContext.filesDir, FILE_NAME) }

    /**
     * ensureLoaded
     * ()
     * 作用：首次访问时才读盘；Koin 尚未就绪时保持未加载，下次再试，不会崩。
     * 备注：仅内部使用
     */
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                snapshot = loadFromDisk()
                loaded = true
            }.onFailure {
                NeoLog.w(NeoLog.SERVER) { "完全体授权记录读取失败：${it.message}" }
            }
        }
    }

    /**
     * isFullUid
     * (uid: Int)
     * 作用：仅按 uid 判断，用于 binder 线程上的快速判定。
     * 备注：—
     */
    fun isFullUid(uid: Int): Boolean {
        ensureLoaded()
        return snapshot.containsKey(uid)
    }

    /**
     * isFull
     * (uid: Int, signature: String?)
     * 作用：带签名校验，防止 uid 复用。
     * 备注：—
     */
    fun isFull(uid: Int, signature: String?): Boolean {
        ensureLoaded()
        val grant = snapshot[uid] ?: return false
        if (grant.signature.isBlank() || signature.isNullOrBlank()) return true
        return grant.signature == signature
    }

    /**
     * allUids
     * ()
     * 作用：执行 allUids
     * 备注：—
     */
    fun allUids(): Set<Int> {
        ensureLoaded()
        return snapshot.keys.toSet()
    }

    /**
     * grant
     * (uid: Int, signature: String?)
     * 作用：执行 grant
     * 备注：—
     */
    @Synchronized
    fun grant(uid: Int, signature: String?) {
        ensureLoaded()
        val next = snapshot.toMutableMap()
        next[uid] = Grant(uid, signature.orEmpty(), System.currentTimeMillis())
        snapshot = next
        saveToDisk()
        NeoLog.i(NeoLog.PERM) { "完全体记录：已授予 uid=$uid" }
    }

    /**
     * revoke
     * (uid: Int)
     * 作用：执行 revoke
     * 备注：—
     */
    @Synchronized
    fun revoke(uid: Int) {
        ensureLoaded()
        val next = snapshot.toMutableMap()
        if (next.remove(uid) == null) return
        snapshot = next
        saveToDisk()
        NeoLog.i(NeoLog.PERM) { "完全体记录：已撤销 uid=$uid" }
    }

    /**
     * reload
     * ()
     * 作用：供配置变更页手动刷新；正常路径由 grant / revoke 同步维护内存镜像。
     * 备注：—
     */
    fun reload() {
        synchronized(this) {
            runCatching { snapshot = loadFromDisk() }
            loaded = true
        }
    }

    /**
     * loadFromDisk
     * ()
     * 作用：加载 FromDisk
     * 备注：仅内部使用
     */
    private fun loadFromDisk(): Map<Int, Grant> {
        if (!file.exists()) return emptyMap()
        val text = file.readText()
        if (text.isBlank()) return emptyMap()
        val root = JSONObject(text)
        val array = root.optJSONArray(KEY_GRANTS) ?: return emptyMap()
        val map = LinkedHashMap<Int, Grant>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val uid = item.optInt(KEY_UID, -1)
            if (uid < 0) continue
            map[uid] = Grant(
                uid = uid,
                signature = item.optString(KEY_SIGNATURE, ""),
                at = item.optLong(KEY_AT, 0L)
            )
        }
        return map
    }

    /**
     * saveToDisk
     * ()
     * 作用：保存 ToDisk
     * 备注：仅内部使用
     */
    private fun saveToDisk() {
        runCatching {
            val array = JSONArray()
            snapshot.values.forEach { grant ->
                array.put(JSONObject().apply {
                    put(KEY_UID, grant.uid)
                    put(KEY_SIGNATURE, grant.signature)
                    put(KEY_AT, grant.at)
                })
            }
            val root = JSONObject().apply { put(KEY_GRANTS, array) }
            val parent = file.parentFile ?: return@runCatching
            if (!parent.exists()) parent.mkdirs()
            val tmp = File(parent, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
            Unit
        }.onFailure {
            NeoLog.e(NeoLog.PERM, { "完全体授权记录写入失败" }, it)
        }
    }
}
