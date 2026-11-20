package com.neodhizuku.more.data.settings.repo.impl

import android.content.Context
import android.os.Build

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.settings.repo.WhitelistEntry
import com.neodhizuku.more.data.settings.repo.WhitelistRepo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import java.io.File

/**
 * 白名单：存放在应用私有目录的 [FILE_NAME] 里。
 * 选择文件而不是 SharedPreferences / Room，理由见 [WhitelistRepo] 的说明 ——
 * 这份数据要独立于授权库，且便于整体导出。
 * 读写策略：
 * - 读取走内存镜像（[state]），服务端在 binder 线程上同步调用 [snapshot] 不会阻塞；
 * - 写入先更新内存镜像再落盘，落盘用「临时文件 + rename」保证断电不会留下半截文件。
 */
class WhitelistRepoImpl : WhitelistRepo, KoinComponent {
    private val context by inject<Context>()

    /**
     * 与设置存储保持一致：能用设备加密存储就用，
     * 否则 Direct Boot 锁屏阶段读到的会是空名单。
     */
    private val storageContext: Context by lazy {
        if (NeoDhizuku.on(NeoDhizuku.FixDbDowngradeAndDeStorage)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && !context.isDeviceProtectedStorage
        ) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    private val file: File by lazy { File(storageContext.filesDir, FILE_NAME) }

    private val mutex = Mutex()

    private val state = MutableStateFlow<List<WhitelistEntry>>(loadFromDisk())

    /**
     * reload
     * ()
     * 作用：私有配置文件被外部直接改写后，重新载入内存镜像（服务配置修改页用）。
     * 备注：—
     */
    fun reload() {
        state.value = loadFromDisk()
    }

    /**
     * flowAll
     * ()
     * 作用：提供 All 数据流
     * 备注：重写父类 / 接口方法
     */
    override fun flowAll(): Flow<List<WhitelistEntry>> = state.asStateFlow()

    /**
     * snapshot
     * ()
     * 作用：执行 snapshot
     * 备注：重写父类 / 接口方法
     */
    override fun snapshot(): List<WhitelistEntry> = state.value

    /**
     * contains
     * (packageName: String)
     * 作用：判断
     * 备注：重写父类 / 接口方法
     */
    override fun contains(packageName: String): Boolean =
        state.value.any { it.packageName == packageName }

    /**
     * isWhitelisted
     * (packageName: String, signatures: Set<String>, strict: Boolean)
     * 作用：判断 Whitelisted
     * 备注：重写父类 / 接口方法
     */
    override fun isWhitelisted(
        packageName: String,
        signatures: Set<String>,
        strict: Boolean
    ): Boolean {
        val entry = state.value.firstOrNull { it.packageName == packageName } ?: return false
        if (!strict) return true

        val recorded = entry.signature
        // 加入白名单时没记下签名（低版本系统或取签名失败），
        // 严格模式下无从比对，只能退回按包名判定。
        if (recorded.isNullOrBlank()) {
            NeoLog.w(NeoLog.PERM) {
                "白名单严格校验: $packageName 未记录签名，降级为按包名判定"
            }
            return true
        }
        return signatures.contains(recorded)
    }

    /**
     * add
     * (packageName: String, signature: String?)
     * 作用：添加
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun add(packageName: String, signature: String?) {
        mutex.withLock {
            val current = state.value
            // 已在名单里则只补签名，避免重复项与 addedAt 被刷新
            val merged = if (current.any { it.packageName == packageName }) {
                current.map {
                    if (it.packageName == packageName) it.copy(signature = signature ?: it.signature)
                    else it
                }
            } else {
                current + WhitelistEntry(packageName = packageName, signature = signature)
            }.sortedBy { it.packageName }
            state.value = merged
            saveToDisk(merged)
        }
        NeoLog.d(NeoLog.PERM) { "白名单 +$packageName" }
    }

    /**
     * remove
     * (packageName: String)
     * 作用：移除
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun remove(packageName: String) {
        mutex.withLock {
            val merged = state.value
                .filterNot { it.packageName == packageName }
                .sortedBy { it.packageName }
            state.value = merged
            saveToDisk(merged)
        }
        NeoLog.d(NeoLog.PERM) { "白名单 -$packageName" }
    }


    // 显式标注类型：lambda 内的 return@runCatching 会让编译器无法推断 R
    /**
     * loadFromDisk
     * ()
     * 作用：加载 FromDisk
     * 备注：仅内部使用
     */
    private fun loadFromDisk(): List<WhitelistEntry> = runCatching<List<WhitelistEntry>> {
        if (!file.exists()) return@runCatching emptyList()
        val text = file.readText()
        if (text.isBlank()) return@runCatching emptyList()
        val array = JSONArray(text)
        buildList<WhitelistEntry> {
            repeat(array.length()) { i ->
                val obj = array.optJSONObject(i) ?: return@repeat
                val pkg = obj.optString(KEY_PKG)
                if (pkg.isBlank()) return@repeat
                val sig = obj.optString(KEY_SIG).takeIf { it.isNotBlank() }
                add(
                    WhitelistEntry(
                        packageName = pkg,
                        signature = sig,
                        addedAt = obj.optLong(KEY_AT, System.currentTimeMillis())
                    )
                )
            }
        }.sortedBy { it.packageName }
    }.onFailure {
        NeoLog.e(NeoLog.PERM, { "白名单读取失败，按空名单处理" }, it)
    }.getOrDefault(emptyList())

    /**
     * saveToDisk
     * (list: List<WhitelistEntry>)
     * 作用：保存 ToDisk
     * 备注：仅内部使用
     */
    private fun saveToDisk(list: List<WhitelistEntry>) {
        runCatching {
            val array = JSONArray()
            list.forEach { entry ->
                array.put(
                    JSONObject()
                        .put(KEY_PKG, entry.packageName)
                        .put(KEY_SIG, entry.signature.orEmpty())
                        .put(KEY_AT, entry.addedAt)
                )
            }
            val parent = file.parentFile ?: return@runCatching
            if (!parent.exists()) parent.mkdirs()
            // 先写临时文件再改名：写入中途异常不会破坏原文件
            val tmp = File(parent, "$FILE_NAME.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(file)) {
                // 某些文件系统跨目录 rename 会失败，退化为直接覆盖
                file.writeText(array.toString())
                tmp.delete()
            }
            Unit
        }.onFailure {
            NeoLog.e(NeoLog.PERM, { "白名单写入失败" }, it)
        }
    }

    private companion object {
        const val FILE_NAME = "whitelist.json"
        const val KEY_PKG = "pkg"
        const val KEY_SIG = "sig"
        const val KEY_AT = "at"
    }
}
