package com.neodhizuku.more.ui.page.settings.app_management

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.getPackageInfoForUid
import com.neodhizuku.more.data.common.util.signature
import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.server.FullAccessStore
import com.neodhizuku.more.server.HookConfigStore
import com.neodhizuku.more.server.PermissionCache
import com.neodhizuku.more.server.ServiceStatus
import com.rosan.dhizuku.shared.DhizukuVariables

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

class AppManagementViewModel : ViewModel(), KoinComponent {
    val context by lazy {
        get<Context>()
    }

    private val packageManager: PackageManager by lazy {
        context.packageManager
    }

    private val repo by inject<AppRepo>()

    var state by mutableStateOf(AppManagementViewState())
        private set

    /**
     * collect
     * ()
     * 作用：收集
     * 备注：—
     */
    fun collect() {
        collectRepo()
    }

    private var collectRepoJob: Job? = null

    /**
     * hookedPackages
     * ()
     * 作用：执行 hookedPackages
     * 备注：仅内部使用
     */
    private fun hookedPackages(): Set<String> =
        (HookConfigStore.snapshot().map { it.pkg } + HookConfigStore.scope).toHashSet()

    /**
     * sortApps
     * (data: List<AppManagementViewData>, hooked: Set<String>)
     * 作用：执行 sortApps
     * 备注：仅内部使用
     */
    private fun sortApps(
        data: List<AppManagementViewData>,
        hooked: Set<String>
    ): List<AppManagementViewData> =
        data.distinctBy { it.applicationInfo.packageName }
            .sortedWith(
                compareByDescending<AppManagementViewData> {
                    it.applicationInfo.packageName in hooked
                }.thenByDescending { it.fullAccess }
                    .thenBy { it.applicationInfo.packageName }
            )

    /**
     * collectRepo
     * ()
     * 作用：收集 Repo
     * 备注：—
     */
    fun collectRepo() {
        state = state.copy(loading = true)
        collectRepoJob?.cancel()
        collectRepoJob = viewModelScope.launch(Dispatchers.IO) {
            repo.flowAll().collect {
                val hooked = hookedPackages()
                val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS

                if (NeoDhizuku.on(NeoDhizuku.FixAppManagementPerf)) {
                    // 对每个已安装包执行一次 `it.find { it.uid == uid }` 是线性查找，
                    // 复杂度 O(n·m)。这里先把授权表转成 Map。
                    val entityMap = it.associateBy { entity -> entity.uid }
                    val data = buildList<AppManagementViewData> {
                        // 只扫描"声明了 Dhizuku API 权限"的包，避免全量遍历。
                        // 注意：getPackagesHoldingPermissions 是 API 33+，低版本回退全量扫描。
                        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            runCatching {
                                packageManager.getPackagesHoldingPermissions(
                                    arrayOf(DhizukuVariables.PERMISSION_API),
                                    flags
                                )
                            }.getOrNull() ?: packageManager.getInstalledPackages(flags)
                        } else {
                            packageManager.getInstalledPackages(flags)
                        }

                        // getPackagesHoldingPermissions 只返回「在清单里声明了该权限」的应用。
                        // 若某个已授权应用没在清单声明（不少客户端直接调 API 而不声明 uses-permission），
                        // 它就不会出现在 candidates 里，导致「已授权」标签页一片空白。
                        // 因此把数据库里有记录的 uid 反查成包名后合并进候选集。
                        val merged = LinkedHashMap<String, PackageInfo>()
                        candidates.forEach { pkg -> merged[pkg.packageName] = pkg }
                        val knownUids = HashSet<Int>()
                        candidates.forEach { pkg ->
                            pkg.applicationInfo?.let { knownUids.add(it.uid) }
                        }
                        entityMap.keys.forEach { uid ->
                            if (knownUids.contains(uid)) return@forEach
                            // 显式声明类型：runCatching{}.getOrNull() ?: return 在 lambda 内
                            // 会让编译器无法推断 R，报 "receiver hasn't been inferred yet"
                            val names: Array<String>? = runCatching {
                                packageManager.getPackagesForUid(uid)
                            }.getOrNull()
                            if (names == null) return@forEach
                            names.forEach { name ->
                                if (merged.containsKey(name)) return@forEach
                                val pkgInfo: PackageInfo? = runCatching {
                                    @Suppress("DEPRECATION")
                                    packageManager.getPackageInfo(name, flags)
                                }.getOrNull()
                                if (pkgInfo != null) merged[name] = pkgInfo
                            }
                        }

                        merged.values.forEach { packageInfo ->
                            if (packageInfo.packageName == context.packageName) return@forEach
                            val applicationInfo =
                                packageInfo.applicationInfo ?: return@forEach

                            val uid = applicationInfo.uid
                            val requested = packageInfo.requestedPermissions
                                ?.contains(DhizukuVariables.PERMISSION_API) ?: false
                            val entity = entityMap[uid]
                            val allowApi = entity?.allowApi ?: false
                            val blocked = entity?.blocked ?: false

                            if (!requested && entity == null) return@forEach

                            add(
                                AppManagementViewData(
                                    applicationInfo = applicationInfo,
                                    enabled = allowApi,
                                    blocked = blocked,
                                    fullAccess = FullAccessStore.isFull(uid, entity?.signature)
                                )
                            )
                        }
                    }
                    val finalData = sortApps(data, hooked)
                    NeoLog.d(NeoLog.APPS) {
                        "loaded: total=${finalData.size}, " +
                            "authorized=${finalData.count { it.enabled && !it.blocked }}, " +
                            "blocked=${finalData.count { it.blocked }} " +
                            "(dbRecords=${entityMap.size})"
                    }
                    state = state.copy(data = finalData, loading = false)
                    return@collect
                }

                val data = packageManager
                    .getInstalledPackages(flags)
                    .mapNotNull { packageInfo ->
                        if (packageInfo.packageName == context.packageName) return@mapNotNull null
                        val applicationInfo =
                            packageInfo.applicationInfo ?: return@mapNotNull null

                        val uid = applicationInfo.uid
                        val requested = packageInfo.requestedPermissions
                            ?.contains(DhizukuVariables.PERMISSION_API) ?: false
                        val entity = it.find { it.uid == uid }
                        val allowApi = entity?.allowApi ?: false
                        val blocked = entity?.blocked ?: false

                        if (!requested && entity == null)
                            return@mapNotNull null

                        AppManagementViewData(
                            applicationInfo = applicationInfo,
                            enabled = allowApi,
                            blocked = blocked,
                            fullAccess = FullAccessStore.isFull(uid, entity?.signature)
                        )
                    }
                state = state.copy(
                    data = sortApps(data, hooked),
                    loading = false
                )
            }
        }
    }

    /**
     * setEnabled
     * (uid: Int, bool: Boolean)
     * 作用：设置 Enabled
     * 备注：—
     */
    fun setEnabled(uid: Int, bool: Boolean) {
        NeoLog.d(NeoLog.APPS) { "setEnabled: uid=$uid -> $bool" }
        viewModelScope.launch(Dispatchers.IO) {
            val signature = packageManager.getPackageInfoForUid(uid)?.signature ?: return@launch
            val entity = repo.findByUID(uid)
            if (entity == null)
                repo.insert(AppEntity(uid = uid, signature = signature, allowApi = bool))
            else
                repo.update(entity.copy(signature = signature, allowApi = bool))
            runCatching { PermissionCache.refreshOne(uid) }
            // 刷新首页「授权应用」计数
            if (NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard)) {
                runCatching {
                    ServiceStatus.updateAuthorizedCount(
                        repo.all().count { it.allowApi && !it.blocked }
                    )
                }
            }
        }
    }

    /**
     * setBlocked
     * (uid: Int, bool: Boolean)
     * 作用：设置 Blocked
     * 备注：—
     */
    fun setBlocked(uid: Int, bool: Boolean) {
        NeoLog.d(NeoLog.APPS) { "setBlocked: uid=$uid -> $bool" }
        viewModelScope.launch(Dispatchers.IO) {
            val signature = packageManager.getPackageInfoForUid(uid)?.signature ?: return@launch
            val entity = repo.findByUID(uid)
            if (entity == null)
                repo.insert(AppEntity(uid = uid, signature = signature, allowApi = false, blocked = bool))
            else
                repo.update(entity.copy(signature = signature, blocked = bool, allowApi = if (bool) false else entity.allowApi))
            runCatching { PermissionCache.refreshOne(uid) }
            // 刷新首页「授权应用」计数
            if (NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard)) {
                runCatching {
                    ServiceStatus.updateAuthorizedCount(
                        repo.all().count { it.allowApi && !it.blocked }
                    )
                }
            }
        }
    }
}
