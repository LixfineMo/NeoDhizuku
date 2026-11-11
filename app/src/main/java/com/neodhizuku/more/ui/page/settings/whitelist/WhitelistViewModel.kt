package com.neodhizuku.more.ui.page.settings.whitelist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.getPackageInfoForUid
import com.neodhizuku.more.data.common.util.signature
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.WhitelistRepo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

/** 白名单管理页里的一张应用卡片 */
data class WhitelistAppItem(
    val applicationInfo: ApplicationInfo,
    val label: String,
    val packageName: String,
    val whitelisted: Boolean
)

data class WhitelistViewState(
    val loading: Boolean = true,
    /** 已加入白名单的应用 */
    val whitelisted: List<WhitelistAppItem> = emptyList(),
    /** 其余候选应用 */
    val others: List<WhitelistAppItem> = emptyList()
)

/**
 * 白名单管理。
 * 候选列表不是全量已安装应用，而是「与本服务有关」的那些：
 * 声明了任一 Dhizuku 权限名、已有授权记录、或已经在白名单里。
 * 全量扫描在此处没有意义 —— 用户不会给一个从不调用本服务的应用加白名单。
 */
class WhitelistViewModel : ViewModel(), KoinComponent {
    val context by lazy { get<Context>() }

    private val packageManager: PackageManager by lazy { context.packageManager }

    private val whitelistRepo by inject<WhitelistRepo>()
    private val appRepo by inject<AppRepo>()

    var state by mutableStateOf(WhitelistViewState())
        private set

    /** 本应用自身声明的权限名 */
    private val permissionSelf by lazy { "${context.packageName}.permission.API" }

    fun collect() {
        state = state.copy(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                whitelistRepo.flowAll(),
                appRepo.flowAll()
            ) { entries, authorized ->
                val whitelistedPkgs = entries.map { it.packageName }.toHashSet()
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

                    val requested = info.requestedPermissions?.any {
                        it == permissionSelf || it == PERMISSION_LEGACY
                    } ?: false
                    val hasRecord = authorizedUids.contains(appInfo.uid)
                    if (!requested && !hasRecord && !whitelistedPkgs.contains(pkg)) {
                        return@mapNotNull null
                    }

                    WhitelistAppItem(
                        applicationInfo = appInfo,
                        label = runCatching {
                            appInfo.loadLabel(packageManager).toString()
                        }.getOrDefault(pkg),
                        packageName = pkg,
                        whitelisted = whitelistedPkgs.contains(pkg)
                    )
                }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }

                NeoLog.d(NeoLog.APPS) {
                    "whitelist: 候选=${items.size}, 已加入=${items.count { it.whitelisted }}"
                }
                state = state.copy(
                    loading = false,
                    whitelisted = items.filter { it.whitelisted },
                    others = items.filterNot { it.whitelisted }
                )
            }.collect { /* combine 的发射已在上面处理 */ }
        }
    }

    /**
     * 勾选或取消勾选。
     * 加入时顺带记下签名：严格校验开启后要用它比对，
     * 这里不采集的话，严格模式下那条记录会永久降级为「按包名判定」。
     */
    fun setWhitelisted(item: WhitelistAppItem, whitelisted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (whitelisted) {
                val signature = runCatching {
                    packageManager.getPackageInfoForUid(item.applicationInfo.uid)?.signature
                }.getOrNull()
                whitelistRepo.add(item.packageName, signature)
            } else {
                whitelistRepo.remove(item.packageName)
            }
        }
    }

    private companion object {
        /** 上游 Dhizuku 的权限名：硬编码客户端只认这一个 */
        const val PERMISSION_LEGACY = "com.rosan.dhizuku.permission.API"
    }
}
