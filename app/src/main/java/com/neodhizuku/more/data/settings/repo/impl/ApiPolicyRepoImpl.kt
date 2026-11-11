package com.neodhizuku.more.data.settings.repo.impl

import android.content.Context
import android.os.Build

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.settings.repo.ApiDecision
import com.neodhizuku.more.data.settings.repo.ApiPolicyRepo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import java.io.File

/**
 * 单项接口策略：存放在应用私有目录的 [FILE_NAME] 里。
 * 与白名单同构 —— 内存镜像 + 临时文件改名落盘，
 * 原因是服务端要在 binder 线程上同步判定，不能挂起等 IO。
 * 文件结构：
 * ```
 * { "global": { "remote_process": false },
 *   "apps":   { "com.aistra.hails": { "remote_process": true } } }
 * ```
 * 只存「显式配置过」的项，没存过的一律走默认值，
 * 这样以后新增接口项不会改变老用户的既有行为。
 */
class ApiPolicyRepoImpl : ApiPolicyRepo, KoinComponent {
    private val context by inject<Context>()

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

    private val globalState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val appsState = MutableStateFlow<Map<String, Map<String, Boolean>>>(emptyMap())

    init {
        reload()
    }

    /** 私有配置文件被外部直接改写后，重新载入内存镜像（服务配置修改页用）。 */
    fun reload() {
        val loaded = loadFromDisk()
        globalState.value = loaded.first
        appsState.value = loaded.second
    }

    override fun flowGlobal(): Flow<Map<String, Boolean>> = globalState.asStateFlow()

    override fun flowApps(): Flow<Map<String, Map<String, Boolean>>> = appsState.asStateFlow()

    override fun globalSnapshot(): Map<String, Boolean> = globalState.value

    override fun appSnapshot(packageName: String): Map<String, Boolean> =
        appsState.value[packageName] ?: emptyMap()

    override fun decide(
        packageName: String?,
        apiId: String,
        defaultAllowed: Boolean
    ): ApiDecision {
        val app = packageName?.let { appsState.value[it] }
        val explicit = app?.get(apiId)
        if (explicit != null) return ApiDecision(explicit, fromApp = true)
        val global = globalState.value[apiId]
        if (global != null) return ApiDecision(global, fromApp = false)
        return ApiDecision(defaultAllowed, fromApp = false)
    }

    override suspend fun setGlobal(apiId: String, allowed: Boolean) {
        mutex.withLock {
            globalState.value = globalState.value.toMutableMap().apply { put(apiId, allowed) }
            saveToDisk()
        }
    }

    override suspend fun setApp(packageName: String, apiId: String, allowed: Boolean) {
        mutex.withLock {
            val apps = appsState.value.toMutableMap()
            val current = apps[packageName]?.toMutableMap() ?: LinkedHashMap()
            current[apiId] = allowed
            apps[packageName] = current
            appsState.value = apps
            saveToDisk()
        }
    }

    override suspend fun clearApp(packageName: String, apiId: String) {
        mutex.withLock {
            val apps = appsState.value.toMutableMap()
            val current = apps[packageName]?.toMutableMap() ?: return@withLock
            current.remove(apiId)
            if (current.isEmpty()) apps.remove(packageName) else apps[packageName] = current
            appsState.value = apps
            saveToDisk()
        }
    }

    override suspend fun clearAppAll(packageName: String) {
        mutex.withLock {
            appsState.value = appsState.value.toMutableMap().apply { remove(packageName) }
            saveToDisk()
        }
    }

    override suspend fun resetGlobal() {
        mutex.withLock {
            globalState.value = emptyMap()
            saveToDisk()
        }
    }


    private fun loadFromDisk(): Pair<Map<String, Boolean>, Map<String, Map<String, Boolean>>> =
        runCatching {
            if (!file.exists()) return@runCatching Pair(emptyMap<String, Boolean>(), emptyMap<String, Map<String, Boolean>>())
            val text = file.readText()
            if (text.isBlank()) return@runCatching Pair(emptyMap<String, Boolean>(), emptyMap<String, Map<String, Boolean>>())
            val root = JSONObject(text)

            val global = LinkedHashMap<String, Boolean>()
            root.optJSONObject(KEY_GLOBAL)?.let { obj ->
                obj.keys().forEach { key -> global[key] = obj.optBoolean(key) }
            }

            val apps = LinkedHashMap<String, Map<String, Boolean>>()
            root.optJSONObject(KEY_APPS)?.let { obj ->
                obj.keys().forEach { pkg ->
                    val inner = obj.optJSONObject(pkg) ?: return@forEach
                    val map = LinkedHashMap<String, Boolean>()
                    inner.keys().forEach { key -> map[key] = inner.optBoolean(key) }
                    if (map.isNotEmpty()) apps[pkg] = map
                }
            }
            Pair(global, apps)
        }.onFailure {
            NeoLog.e(NeoLog.PERM, { "单项接口策略读取失败，按默认策略处理" }, it)
        }.getOrDefault(
            Pair(emptyMap<String, Boolean>(), emptyMap<String, Map<String, Boolean>>())
        )

    private fun saveToDisk() {
        runCatching {
            val root = JSONObject()
            root.put(KEY_GLOBAL, JSONObject().apply {
                globalState.value.forEach { (k, v) -> put(k, v) }
            })
            root.put(KEY_APPS, JSONObject().apply {
                appsState.value.forEach { (pkg, map) ->
                    put(pkg, JSONObject().apply { map.forEach { (k, v) -> put(k, v) } })
                }
            })
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
            NeoLog.e(NeoLog.PERM, { "单项接口策略写入失败" }, it)
        }
    }

    private companion object {
        const val FILE_NAME = "api_policy.json"
        const val KEY_GLOBAL = "global"
        const val KEY_APPS = "apps"
    }
}
