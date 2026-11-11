package com.neodhizuku.more.ui.page.settings.apipolicy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.data.settings.repo.ApiPolicyRepo
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.server.DpmPolicyCatalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

/** 单项接口配置页里的一张应用卡片 */
data class ApiPolicyAppItem(
    val applicationInfo: ApplicationInfo,
    val label: String,
    val packageName: String,
    /** 单独配置过的接口 id；为空表示跟随全局 */
    val configured: Set<String>
)

data class ApiPolicyViewState(
    val loading: Boolean = true,
    /** 有单独配置的应用，排在前面 */
    val configuredApps: List<ApiPolicyAppItem> = emptyList(),
    /** 其余候选应用 */
    val others: List<ApiPolicyAppItem> = emptyList(),
    /** 全局配置快照：接口 id -> 是否允许；未配置过的键不存在 */
    val global: Map<String, Boolean> = emptyMap(),
    /** 各应用的单独配置快照 */
    val apps: Map<String, Map<String, Boolean>> = emptyMap()
)

/**
 * 单项接口配置。
 * 候选列表沿用白名单的取法：只列「与本服务有关」的应用
 * （声明了 Dhizuku 权限、已有授权记录、或已经配过单项策略），
 * 而不是全量已安装应用。
 */
class ApiPolicyViewModel : ViewModel(), KoinComponent {
    val context by lazy { get<Context>() }

    private val packageManager: PackageManager by lazy { context.packageManager }

    private val apiPolicyRepo by inject<ApiPolicyRepo>()
    private val appRepo by inject<AppRepo>()

    var state by mutableStateOf(ApiPolicyViewState())
        private set

    /** 上游与本应用各自的权限名 */
    private val permissionLegacy by lazy { "com.rosan.dhizuku.permission.API" }
    private val permissionSelf by lazy { "${context.packageName}.permission.API" }

    fun collect() {
        state = state.copy(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                apiPolicyRepo.flowApps(),
                appRepo.flowAll()
            ) { configured, authorized ->
                val authorizedUids = authorized.map { it.uid }.toHashSet()

                val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
                val installed = runCatching {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(flags)
                }.getOrDefault(emptyList())

                val items = installed.mapNotNull { info ->
                    val appInfo = info.applicationInfo ?: return@mapNotNull null
                    val pkg = info.packageName
                    if (pkg == context.packageName) return@mapNotNull null

                    val declared = info.requestedPermissions
                        ?.any { it == permissionLegacy || it == permissionSelf }
                        ?: false
                    val hasRecord = runCatching {
                        packageManager.getPackageUid(pkg, 0)
                    }.getOrNull()?.let { it in authorizedUids } ?: false
                    val hasPolicy = configured.containsKey(pkg)
                    if (!declared && !hasRecord && !hasPolicy) return@mapNotNull null

                    val label = runCatching {
                        appInfo.loadLabel(packageManager).toString()
                    }.getOrDefault(pkg)

                    ApiPolicyAppItem(
                        applicationInfo = appInfo,
                        label = label,
                        packageName = pkg,
                        configured = configured[pkg]?.keys?.toSet() ?: emptySet()
                    )
                }.sortedBy { it.label.lowercase() }

                val withPolicy = items.filter { it.configured.isNotEmpty() }
                    .sortedByDescending { it.configured.size }
                val rest = items.filter { it.configured.isEmpty() }
                Pair(withPolicy, rest)
            }.collect { (withPolicy, rest) ->
                state = state.copy(
                    loading = false,
                    configuredApps = withPolicy,
                    others = rest
                )
            }
        }
        // 配置快照单独收集：改动全局或单项后界面要立刻跟上
        viewModelScope.launch(Dispatchers.IO) {
            apiPolicyRepo.flowGlobal().collect { state = state.copy(global = it) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            apiPolicyRepo.flowApps().collect { state = state.copy(apps = it) }
        }
    }

    fun setGlobal(apiId: String, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { apiPolicyRepo.setGlobal(apiId, allowed) }
    }

    fun setApp(packageName: String, apiId: String, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { apiPolicyRepo.setApp(packageName, apiId, allowed) }
    }

    /** 撤掉单项，回到跟随全局 */
    fun clearApp(packageName: String, apiId: String) {
        viewModelScope.launch(Dispatchers.IO) { apiPolicyRepo.clearApp(packageName, apiId) }
    }

    fun clearAppAll(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) { apiPolicyRepo.clearAppAll(packageName) }
    }

    fun resetGlobal() {
        viewModelScope.launch(Dispatchers.IO) { apiPolicyRepo.resetGlobal() }
    }

    /** 全部可配置项，按高危在后排序 */
    fun allApis() = DpmPolicyCatalog.ALL
}
