package com.neodhizuku.more.ui.page.settings.debug

import android.content.Context
import android.os.Build
import android.util.Base64

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.data.settings.repo.ApiPolicyRepo
import com.neodhizuku.more.data.settings.repo.WhitelistRepo
import com.neodhizuku.more.data.settings.repo.impl.ApiPolicyRepoImpl
import com.neodhizuku.more.data.settings.repo.impl.WhitelistRepoImpl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import java.io.File

data class PrivateFile(
    /** 路由参数：相对路径的 URL-safe Base64，不含斜杠，导航安全 */
    val key: String,
    val relative: String,
    val absolute: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val editable: Boolean,
    val reason: String
)

/**
 * 应用私有区的配置文件访问。
 * CE 与 DE（设备加密存储）两边都扫 —— 本项目从某个版本起把设置与授权库
 * 迁到了 DE，只看 filesDir 会漏掉 api_policy.json / whitelist.json /
 * dhizuku_settings.xml 这些真正在生效的文件。
 * 只允许文本类文件编辑；数据库、图片等二进制只读，避免把数据库写坏。
 */
object PrivateFileStore : KoinComponent {

    private val apiPolicyRepo: ApiPolicyRepo by inject()
    private val whitelistRepo: WhitelistRepo by inject()

    private val TEXT_EXT = setOf(
        "json", "xml", "txt", "ini", "properties", "cfg", "conf",
        "log", "md", "toml", "yaml", "yml"
    )
    private const val MAX_EDIT_BYTES = 512 * 1024L

    private fun encode(relative: String): String = Base64.encodeToString(
        relative.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private fun decode(key: String): String = runCatching {
        String(
            Base64.decode(key, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8
        )
    }.getOrDefault("")

    /** 所有要扫描的目录，前缀同时也是路由里相对路径的第一段。 */
    private fun roots(context: Context): List<Pair<String, File>> {
        val ce = context.applicationContext
        val out = mutableListOf<Pair<String, File>>()
        out += "files" to ce.filesDir
        out += "shared_prefs" to File(ce.applicationInfo.dataDir, "shared_prefs")
        out += "databases" to File(ce.applicationInfo.dataDir, "databases")
        out += "no_backup" to ce.noBackupFilesDir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val de = ce.createDeviceProtectedStorageContext()
            out += "de_files" to de.filesDir
            out += "de_shared_prefs" to File(de.applicationInfo.dataDir, "shared_prefs")
            out += "de_databases" to File(de.applicationInfo.dataDir, "databases")
            out += "de_no_backup" to de.noBackupFilesDir
        }
        return out
    }

    private fun resolve(context: Context, key: String): File? {
        val relative = decode(key)
        if (relative.isBlank() || relative.contains("..")) return null
        val prefix = relative.substringBefore('/')
        val rest = relative.substringAfter('/', "")
        val root = roots(context).firstOrNull { it.first == prefix }?.second ?: return null
        val file = File(root, rest)
        return file.takeIf { it.isFile && it.absolutePath.startsWith(root.absolutePath) }
    }

    fun list(context: Context): List<PrivateFile> {
        val out = mutableListOf<PrivateFile>()
        for ((prefix, root) in roots(context)) {
            if (!root.exists()) continue
            val found = runCatching {
                root.walkTopDown().maxDepth(2).filter { it.isFile }.toList()
            }.getOrDefault(emptyList())
            for (file in found) {
                val rest = file.absolutePath.removePrefix(root.absolutePath).removePrefix("/")
                val relative = "$prefix/$rest"
                val ext = file.extension.lowercase()
                val size = file.length()
                val editable = ext in TEXT_EXT && size <= MAX_EDIT_BYTES
                val reason = when {
                    editable && prefix.contains("shared_prefs") ->
                        "SharedPreferences 已加载实例不会刷新，重启或下次写入可能覆盖"
                    editable -> "text"
                    ext !in TEXT_EXT -> "binary (.${ext.ifBlank { "none" }})"
                    else -> "too large (${size}B > ${MAX_EDIT_BYTES}B)"
                }
                out += PrivateFile(
                    key = encode(relative),
                    relative = relative,
                    absolute = file.absolutePath,
                    sizeBytes = size,
                    lastModified = file.lastModified(),
                    editable = editable,
                    reason = reason
                )
            }
        }
        return out.sortedWith(compareBy({ !it.editable }, { it.relative }))
    }

    fun find(context: Context, key: String): PrivateFile? =
        list(context).firstOrNull { it.key == key }

    fun read(context: Context, key: String): String {
        val file = resolve(context, key) ?: return ""
        return runCatching { file.readText(Charsets.UTF_8) }
            .getOrDefault("")
    }

    /** 原子写入，并在改到内存镜像对应的文件时立即重载，让修改当场生效。 */
    fun write(context: Context, key: String, content: String): Boolean {
        val file = resolve(context, key) ?: return false
        val relative = decode(key)
        val ok = runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(content, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(content, Charsets.UTF_8)
                tmp.delete()
            }
            true
        }.getOrDefault(false)
        if (!ok) return false
        runCatching {
            when (file.name) {
                "api_policy.json" ->
                    (apiPolicyRepo as? ApiPolicyRepoImpl)?.reload()
                "whitelist.json" ->
                    (whitelistRepo as? WhitelistRepoImpl)?.reload()
            }
        }
        return true
    }
}

/** 「服务配置修改」列表页状态。 */
class ServiceConfigViewModel : ViewModel() {
    var files by mutableStateOf<List<PrivateFile>>(emptyList())
        private set

    fun refresh(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { PrivateFileStore.list(context.applicationContext) }
                .getOrDefault(emptyList())
            withContext(Dispatchers.Main) { files = result }
        }
    }
}

/** 单文件编辑页状态。 */
class ServiceFileEditViewModel : ViewModel() {
    var file by mutableStateOf<PrivateFile?>(null)
        private set
    var content by mutableStateOf("")
        private set
    var message by mutableStateOf("")
        private set

    fun load(context: Context, key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext
            val f = PrivateFileStore.find(app, key)
            val text = PrivateFileStore.read(app, key)
            withContext(Dispatchers.Main) {
                file = f
                content = text
                message = ""
            }
        }
    }

    fun edit(value: String) {
        content = value
    }

    fun save(context: Context, key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = PrivateFileStore.write(context.applicationContext, key, content)
            withContext(Dispatchers.Main) {
                message = if (ok) "saved" else "write failed"
            }
        }
    }
}
