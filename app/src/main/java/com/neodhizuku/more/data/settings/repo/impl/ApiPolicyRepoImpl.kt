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

    /**
     * reload
     * ()
     * 作用：私有配置文件被外部直接改写后，重新载入内存镜像（服务配置修改页用）。
     * 备注：—
     */
    fun reload() {
        val loaded = loadFromDisk()
        globalState.value = loaded.first
        appsState.value = loaded.second
    }

    /**
     * flowGlobal
     * ()
     * 作用：提供 Global 数据流
     * 备注：重写父类 / 接口方法
     */
    override fun flowGlobal(): Flow<Map<String, Boolean>> = globalState.asStateFlow()

    /**
     * flowApps
     * ()
     * 作用：提供 Apps 数据流
     * 备注：重写父类 / 接口方法
     */
    override fun flowApps(): Flow<Map<String, Map<String, Boolean>>> = appsState.asStateFlow()

    /**
     * globalSnapshot
     * ()
     * 作用：执行 globalSnapshot
     * 备注：重写父类 / 接口方法
     */
    override fun globalSnapshot(): Map<String, Boolean> = globalState.value

    /**
     * appSnapshot
     * (packageName: String)
     * 作用：执行 appSnapshot
     * 备注：重写父类 / 接口方法
     */
    override fun appSnapshot(packageName: String): Map<String, Boolean> =
        appsState.value[packageName] ?: emptyMap()

    /**
     * decide
     * (packageName: String?, apiId: String, defaultAllowed: Boolean)
     * 作用：执行 decide
     * 备注：重写父类 / 接口方法
     */
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

    /**
     * setGlobal
     * (apiId: String, allowed: Boolean)
     * 作用：设置 Global
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun setGlobal(apiId: String, allowed: Boolean) {
        mutex.withLock {
            globalState.value = globalState.value.toMutableMap().apply { put(apiId, allowed) }
            saveToDisk()
        }
    }

    /**
     * setApp
     * (packageName: String, apiId: String, allowed: Boolean)
     * 作用：设置 App
     * 备注：挂起函数；重写父类 / 接口方法
     */
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

    /**
     * clearApp
     * (packageName: String, apiId: String)
     * 作用：清空 App
     * 备注：挂起函数；重写父类 / 接口方法
     */
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

    /**
     * clearAppAll
     * (packageName: String)
     * 作用：清空 AppAll
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun clearAppAll(packageName: String) {
        mutex.withLock {
            appsState.value = appsState.value.toMutableMap().apply { remove(packageName) }
            saveToDisk()
        }
    }

    /**
     * resetGlobal
     * ()
     * 作用：复位 Global
     * 备注：挂起函数；重写父类 / 接口方法
     */
    override suspend fun resetGlobal() {
        mutex.withLock {
            globalState.value = emptyMap()
            saveToDisk()
        }
    }


    /**
     * loadFromDisk
     * ()
     * 作用：加载 FromDisk
     * 备注：仅内部使用
     */
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

    /**
     * saveToDisk
     * ()
     * 作用：保存 ToDisk
     * 备注：仅内部使用
     */
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
