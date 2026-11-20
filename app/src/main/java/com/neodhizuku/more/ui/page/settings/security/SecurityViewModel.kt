package com.neodhizuku.more.ui.page.settings.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.data.common.util.getPackageInfoForUid
import com.neodhizuku.more.data.common.util.signature
import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.DenyMode
import com.neodhizuku.more.data.settings.repo.InsufficientMode
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.server.FullAccessStore
import com.neodhizuku.more.server.HookConfigStore
import com.neodhizuku.more.server.PermissionCache
import com.neodhizuku.more.server.ServiceStatus
import com.rosan.dhizuku.shared.DhizukuVariables

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

data class FullAccessAppItem(
    val applicationInfo: ApplicationInfo,
    val uid: Int,
    val packageName: String,
    val label: String,
    val hooked: Boolean,
    val fullAccess: Boolean,
    val signature: String
)

class SecurityViewModel : ViewModel(), KoinComponent {
    val context by lazy { get<Context>() }
    private val packageManager: PackageManager by lazy { context.packageManager }
    private val settingsRepo by inject<SettingsRepo>()
    private val appRepo by inject<AppRepo>()

    var state by mutableStateOf(SecurityViewState())
        private set

    /**
     * collect
     * ()
     * 作用：收集
     * 备注：—
     */
    fun collect() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowAllowOwnerTransfer().collect { allow ->
                state = state.copy(allowOwnerTransfer = allow)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowDenyMode().collect { mode ->
                state = state.copy(denyMode = DenyMode.from(mode))
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowDhizukuEnabled().collect { enabled ->
                state = state.copy(dhizukuEnabled = enabled)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowWhitelistMode().collect { enabled ->
                state = state.copy(whitelistMode = enabled)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowUseLegacyVersion().collect { legacy ->
                state = state.copy(useLegacyVersion = legacy)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flowStrictServiceCheck().collect { strict ->
                state = state.copy(strictServiceCheck = strict)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appRepo.flowAll().collect { loadFullAccessApps() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                loadFullAccessApps()
                delay(5000)
            }
        }
    }

    /**
     * hookedPackages
     * ()
     * 作用：执行 hookedPackages
     * 备注：仅内部使用
     */
    private fun hookedPackages(): Set<String> =
        (HookConfigStore.snapshot().map { it.pkg } + HookConfigStore.scope).toHashSet()

    /**
     * loadFullAccessApps
     * ()
     * 作用：加载 FullAccessApps
     * 备注：挂起函数；仅内部使用
     */
    private suspend fun loadFullAccessApps() {
        val app = context.applicationContext
        runCatching { HookConfigStore.init(app) }
        val hooked = hookedPackages()
        val entities = appRepo.all()
        val entityUids = entities.map { it.uid }.toHashSet()
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
        val installed = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }.getOrDefault(emptyList())
        val selfPermission = "${context.packageName}.permission.API"

        val items = installed.mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val pkg = info.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val declared = info.requestedPermissions
                ?.any { it == DhizukuVariables.PERMISSION_API || it == selfPermission }
                ?: false
            val hasRecord = appInfo.uid in entityUids
            val isHooked = pkg in hooked
            if (!declared && !hasRecord && !isHooked) return@mapNotNull null

            val signature = packageManager.getPackageInfoForUid(appInfo.uid)?.signature
                ?: return@mapNotNull null
            val label = runCatching { appInfo.loadLabel(packageManager).toString() }
                .getOrDefault(pkg)

            FullAccessAppItem(
                applicationInfo = appInfo,
                uid = appInfo.uid,
                packageName = pkg,
                label = label,
                hooked = isHooked,
                fullAccess = FullAccessStore.isFull(appInfo.uid, signature),
                signature = signature
            )
        }.distinctBy { it.uid }
            .sortedWith(
                compareByDescending<FullAccessAppItem> { it.hooked }
                    .thenByDescending { it.fullAccess }
                    .thenBy { it.label.lowercase() }
            )

        state = state.copy(fullAccessApps = items)
    }

    /**
     * setFullAccess
     * (item: FullAccessAppItem, enabled: Boolean)
     * 作用：设置 FullAccess
     * 备注：—
     */
    fun setFullAccess(item: FullAccessAppItem, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (enabled) {
                val entity = appRepo.findByUID(item.uid)
                if (entity == null) {
                    appRepo.insert(
                        AppEntity(uid = item.uid, signature = item.signature, allowApi = true)
                    )
                } else {
                    appRepo.update(
                        entity.copy(
                            signature = item.signature,
                            allowApi = true,
                            blocked = false
                        )
                    )
                }
                FullAccessStore.grant(item.uid, item.signature)
            } else {
                FullAccessStore.revoke(item.uid)
            }
            runCatching { PermissionCache.refreshOne(item.uid) }
            if (NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard)) {
                runCatching {
                    ServiceStatus.updateAuthorizedCount(
                        appRepo.all().count { it.allowApi && !it.blocked }
                    )
                }
            }
            loadFullAccessApps()
        }
    }

    /**
     * setAllowOwnerTransfer
     * (enabled: Boolean)
     * 作用：是否允许第三方应用不经确认直接转移设备所有者，默认关闭。
     * 备注：—
     */
    fun setAllowOwnerTransfer(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isAllowOwnerTransfer = enabled
        }
    }

    /**
     * setDenyMode
     * (mode: DenyMode)
     * 作用：设置 DenyMode
     * 备注：—
     */
    fun setDenyMode(mode: DenyMode) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.denyMode = mode.name
        }
    }

    /**
     * setDhizukuEnabled
     * (enabled: Boolean)
     * 作用：设置 DhizukuEnabled
     * 备注：—
     */
    fun setDhizukuEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isDhizukuEnabled = enabled
        }
    }

    /**
     * setInsufficientMode
     * (mode: InsufficientMode)
     * 作用：能力不足时的处理方式。 注意：本模式只在**非设备所有者**身份下才有意义 —— 设备所有者可以执行全部方法，不存在能力不足。
     * 备注：—
     */
    fun setInsufficientMode(mode: InsufficientMode) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.insufficientMode = mode.name
        }
    }

    /**
     * setWhitelistMode
     * (enabled: Boolean)
     * 作用：设置 WhitelistMode
     * 备注：—
     */
    fun setWhitelistMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isWhitelistMode = enabled
        }
    }

    /**
     * setUseLegacyVersion
     * (enabled: Boolean)
     * 作用：向客户端返回的版本号来源。 开启时返回上游 Dhizuku 的版本，关闭时返回本项目自身的版本。 默认开启：部分客户端会校验版本，返回本项目的版本可能被判定为不兼容。
     * 备注：—
     */
    fun setUseLegacyVersion(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isUseLegacyVersion = enabled
        }
    }

    /**
     * setStrictServiceCheck
     * (enabled: Boolean)
     * 作用：更严格的服务校验。 关闭（默认）时只按包名放行，只认上游权限名的硬编码客户端同样可用； 开启后额外要求调用方持有本应用自身声明的权限。
     * 备注：—
     */
    fun setStrictServiceCheck(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.isStrictServiceCheck = enabled
        }
    }
}

data class SecurityViewState(
    /** 允许第三方主动转移设备所有者，默认关闭 */
    val allowOwnerTransfer: Boolean = false,
    val denyMode: DenyMode = DenyMode.DEFAULT,
    /** API 服务总开关：关闭时第三方一律无法使用 */
    val dhizukuEnabled: Boolean = true,
    /** 仅允许白名单应用使用 */
    val whitelistMode: Boolean = false,
    /** 能力不足时的处理 */
    val insufficientMode: InsufficientMode = InsufficientMode.DEFAULT,
    /** 返回上游 Dhizuku 的版本号，默认开启 */
    val useLegacyVersion: Boolean = true,
    /** 更严格的服务校验，默认关闭 */
    val strictServiceCheck: Boolean = false,
    /** 共享服务页内可勾选的应用，接入框架的应用排在前面 */
    val fullAccessApps: List<FullAccessAppItem> = emptyList()
)
