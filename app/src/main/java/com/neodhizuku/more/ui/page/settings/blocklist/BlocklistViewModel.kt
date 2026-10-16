package com.neodhizuku.more.ui.page.settings.blocklist

import android.content.Context
import android.content.pm.PackageManager

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
import com.neodhizuku.more.server.PermissionCache
import com.neodhizuku.more.server.ServiceStatus
import com.neodhizuku.more.ui.page.settings.app_management.AppManagementViewData

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

data class BlocklistViewState(
    val loading: Boolean = true,
    val data: List<AppManagementViewData> = emptyList()
)

class BlocklistViewModel : ViewModel(), KoinComponent {

    val context by lazy { get<Context>() }

    private val packageManager: PackageManager by lazy { context.packageManager }

    private val repo by inject<AppRepo>()

    var state by mutableStateOf(BlocklistViewState())
        private set

    private var job: Job? = null

    fun collect() {
        state = state.copy(loading = true)
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            repo.flowAll().collect { entities ->
                val blocked = entities.filter { it.blocked }
                val data = blocked.mapNotNull { entity ->
                    val names = runCatching {
                        packageManager.getPackagesForUid(entity.uid)
                    }.getOrNull()
                    val name = names?.firstOrNull() ?: return@mapNotNull null
                    val packageInfo: android.content.pm.PackageInfo? = runCatching {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(name, PackageManager.GET_META_DATA)
                    }.getOrNull()
                    val info = packageInfo?.applicationInfo ?: return@mapNotNull null
                    AppManagementViewData(
                        applicationInfo = info,
                        enabled = entity.allowApi,
                        blocked = true
                    )
                }.distinctBy { it.applicationInfo.packageName }
                    .sortedBy { it.applicationInfo.packageName }

                NeoLog.d(NeoLog.APPS) {
                    "blocklist: db=${entities.size}, blocked=${blocked.size}, shown=${data.size}"
                }
                state = state.copy(data = data, loading = false)
            }
        }
    }

    /** 解除拉黑 */
    fun unblock(uid: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val signature = packageManager.getPackageInfoForUid(uid)?.signature ?: return@launch
            val entity = repo.findByUID(uid)
            if (entity == null) {
                repo.insert(
                    AppEntity(
                        uid = uid,
                        signature = signature,
                        allowApi = false,
                        blocked = false
                    )
                )
            } else {
                repo.update(entity.copy(signature = signature, blocked = false))
            }
            runCatching { PermissionCache.refreshOne(uid) }
            if (NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard)) {
                runCatching {
                    ServiceStatus.updateAuthorizedCount(
                        repo.all().count { it.allowApi && !it.blocked }
                    )
                }
            }
            NeoLog.d(NeoLog.APPS) { "unblock: uid=$uid" }
        }
    }
}
