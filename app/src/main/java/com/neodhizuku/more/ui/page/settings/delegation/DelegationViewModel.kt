package com.neodhizuku.more.ui.page.settings.delegation

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.DelegationManager
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.server.DhizukuState

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 权限委派管理。
 *
 * 委派是所有者把有限几类 DPM 能力授权给普通应用的通道，
 * 与本应用的授权开关相互独立。这里把它做成可见、可改、可清。
 */
class DelegationViewModel : ViewModel(), KoinComponent {
    private val context by inject<Context>()
    private val settingsRepo by inject<SettingsRepo>()

    private val packageManager get() = context.packageManager

    private val devicePolicyManager: DevicePolicyManager?
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    var state by mutableStateOf(DelegationViewState())
        private set

    /** 刷新委派清单。 */
    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = true, message = null)
            val result = withContext(Dispatchers.IO) { scan() }
            state = state.copy(
                loading = false,
                granted = result.first,
                others = result.second,
                message = null
            )
        }
    }

    /** 打开某个应用的编辑弹窗。 */
    fun edit(entry: DelegationEntry?) {
        state = state.copy(editing = entry)
    }

    /** 写入某个应用的委派范围。 */
    fun save(pkg: String, scopes: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val admin = DhizukuState.admin
            val dpm = devicePolicyManager
            if (dpm == null) {
                withContext(Dispatchers.Main.immediate) {
                    state = state.copy(editing = null)
                }
                return@launch
            }
            val ok = DelegationManager.writeScopes(dpm, admin, pkg, scopes)
            NeoLog.i(NeoLog.UI) { "委派：$pkg -> ${scopes.joinToString()} 结果=$ok" }
            withContext(Dispatchers.Main.immediate) {
                state = state.copy(
                    editing = null,
                    message = if (ok) DelegationMessage.Saved else DelegationMessage.Failed
                )
            }
            refresh()
        }
    }

    /** 清除某个应用的委派。 */
    fun clear(entry: DelegationEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val admin = DhizukuState.admin
            val dpm = devicePolicyManager
            if (dpm == null) return@launch
            DelegationManager.clear(
                context = context,
                dpm = dpm,
                admin = admin,
                pkg = entry.pkg,
                restore = settingsRepo.isExperimentalDelegationCleanup
            )
            NeoLog.i(NeoLog.UI) { "委派：清除 ${entry.pkg}" }
            withContext(Dispatchers.Main.immediate) {
                state = state.copy(
                    editing = null,
                    message = DelegationMessage.Cleared
                )
            }
            refresh()
        }
    }

    /** 清除全部委派。 */
    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val admin = DhizukuState.admin
            val dpm = devicePolicyManager
            if (dpm == null) return@launch
            state.granted.forEach { entry ->
                DelegationManager.clear(
                    context = context,
                    dpm = dpm,
                    admin = admin,
                    pkg = entry.pkg,
                    restore = settingsRepo.isExperimentalDelegationCleanup
                )
            }
            NeoLog.i(NeoLog.UI) { "委派：清除全部（${state.granted.size} 项）" }
            withContext(Dispatchers.Main.immediate) {
                state = state.copy(
                    editing = null,
                    message = DelegationMessage.Cleared
                )
            }
            refresh()
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    private suspend fun refresh() {
        val result = withContext(Dispatchers.IO) { scan() }
        withContext(Dispatchers.Main.immediate) {
            state = state.copy(granted = result.first, others = result.second)
        }
    }

    /** 搜索关键字，同时过滤两个分组。 */
    fun setQuery(value: String) {
        state = state.copy(query = value)
    }

    /** 界面用的已持有列表（已按关键字过滤）。 */
    val visibleGranted: List<DelegationEntry>
        get() = state.granted.filtered()

    /** 界面用的其余应用列表（已按关键字过滤）。 */
    val visibleOthers: List<DelegationEntry>
        get() = state.others.filtered()

    /**
     * 扫描全部已安装包，按是否持有委派分成两组。
     *
     * 委派不要求目标应用事先声明什么，所有者可以把范围派给任意一个包，
     * 所以这里扫的是全部已安装应用，而不是只扫声明过 Dhizuku API 权限的那批。
     *
     * 每个包都要查一次委派，属于逐包调用；包多时耗时在几百毫秒量级，
     * 因此整个扫描放在 IO 线程，界面用 loading 态过渡。
     */
    private fun scan(): Pair<List<DelegationEntry>, List<DelegationEntry>> {
        if (!DhizukuState.state.isOwner) return emptyList<DelegationEntry>() to emptyList()
        val admin = DhizukuState.admin
        val dpm = devicePolicyManager ?: return emptyList<DelegationEntry>() to emptyList()

        val startedAt = System.currentTimeMillis()
        val flags = PackageManager.GET_META_DATA
        val installed = runCatching { packageManager.getInstalledPackages(flags) }
            .getOrElse { emptyList() }

        val granted = ArrayList<DelegationEntry>()
        val others = ArrayList<DelegationEntry>()

        installed.forEach { info ->
            val pkg = info.packageName ?: return@forEach
            if (pkg == context.packageName) return@forEach
            val label = runCatching {
                info.applicationInfo?.loadLabel(packageManager)?.toString()
            }.getOrNull() ?: pkg
            val scopes = DelegationManager.readScopes(dpm, admin, pkg)
            val entry = DelegationEntry(pkg = pkg, label = label, scopes = scopes)
            if (scopes.isEmpty()) others.add(entry) else granted.add(entry)
        }

        granted.sortBy { it.label.lowercase() }
        others.sortBy { it.label.lowercase() }
        NeoLog.i(NeoLog.UI) {
            "委派扫描：共 ${installed.size} 个包，已持有 ${granted.size} 项，" +
                "其余 ${others.size} 项，耗时 ${System.currentTimeMillis() - startedAt}ms"
        }
        return granted to others
    }

    /** 按关键字过滤；为空时不过滤。 */
    private fun List<DelegationEntry>.filtered(): List<DelegationEntry> {
        val keyword = state.query.trim()
        if (keyword.isEmpty()) return this
        return filter {
            it.label.contains(keyword, ignoreCase = true) ||
                it.pkg.contains(keyword, ignoreCase = true)
        }
    }
}

/** 一条委派记录。 */
data class DelegationEntry(
    val pkg: String,
    val label: String,
    val scopes: List<String>
)

/** 操作结果提示。 */
enum class DelegationMessage {
    Saved,
    Cleared,
    Failed
}

data class DelegationViewState(
    val loading: Boolean = false,
    /** 已持有委派的应用。 */
    val granted: List<DelegationEntry> = emptyList(),
    /** 其余候选，用于主动委派。 */
    val others: List<DelegationEntry> = emptyList(),
    val editing: DelegationEntry? = null,
    val message: DelegationMessage? = null,
    /** 搜索关键字 */
    val query: String = ""
)
