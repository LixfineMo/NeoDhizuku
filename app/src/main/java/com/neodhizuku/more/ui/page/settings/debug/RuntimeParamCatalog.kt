package com.neodhizuku.more.ui.page.settings.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

import com.neodhizuku.more.data.common.util.OfficialEndpoint
import com.neodhizuku.more.data.common.util.StartupUpdateChecker
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.server.DpmApiCatalog
import com.neodhizuku.more.server.RequestPermissionToken
import com.neodhizuku.more.server.ServiceStatus

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 参数值类型，决定输入框怎么解析。 */
enum class ParamKind { BOOL, INT, LONG, STRING }

/**
 * 一个可写的运行时参数。
 * [name] 直接用变量名，不做翻译；[read] / [write] 都跑在调用线程上，
 * 不挂起、不阻塞 Binder 线程之外的任何东西。
 */
data class RuntimeParam(
    val name: String,
    val kind: ParamKind,
    val read: () -> String,
    val write: (String) -> Boolean,
    val hint: String = ""
)

data class ParamGroup(
    val name: String,
    val params: List<RuntimeParam>
)

/**
 * 「实时参数修改」的数据源。
 * 收录所有**运行时可写**的变量：设置仓库的全部属性、服务运行态、
 * 身份状态、更新检查状态、选点缓存、token 表与事务码缓存。
 * 编译期常量（NeoDhizuku.FixXxx 等）无法在运行时改写，统一放到
 * 「详细运行信息」里展出，不在这里伪装成可改项。
 * 不做任何危险项屏蔽：调试页本来就是给需要动刀的人用的。
 */
object RuntimeParamCatalog : KoinComponent {

    private val settingsRepo: SettingsRepo by inject()

    private fun bool(
        name: String,
        get: () -> Boolean,
        set: (Boolean) -> Unit,
        hint: String = "true / false"
    ) = RuntimeParam(
        name = name,
        kind = ParamKind.BOOL,
        read = { get().toString() },
        write = { raw ->
            val v = when (raw.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
            if (v == null) false else runCatching { set(v) }.isSuccess
        },
        hint = hint
    )

    private fun int(
        name: String,
        get: () -> Int,
        set: (Int) -> Unit,
        hint: String = "integer"
    ) = RuntimeParam(
        name = name,
        kind = ParamKind.INT,
        read = { get().toString() },
        write = { raw ->
            val v = raw.trim().toIntOrNull()
            if (v == null) false else runCatching { set(v) }.isSuccess
        },
        hint = hint
    )

    private fun long(
        name: String,
        get: () -> Long,
        set: (Long) -> Unit,
        hint: String = "long"
    ) = RuntimeParam(
        name = name,
        kind = ParamKind.LONG,
        read = { get().toString() },
        write = { raw ->
            val v = raw.trim().toLongOrNull()
            if (v == null) false else runCatching { set(v) }.isSuccess
        },
        hint = hint
    )

    private fun str(
        name: String,
        get: () -> String,
        set: (String) -> Unit,
        hint: String = ""
    ) = RuntimeParam(
        name = name,
        kind = ParamKind.STRING,
        read = get,
        write = { raw -> runCatching { set(raw) }.isSuccess },
        hint = hint
    )

    fun groups(): List<ParamGroup> = listOf(
        ParamGroup(
            "SettingsRepo",
            listOf(
                bool("isWhitelistMode", { settingsRepo.isWhitelistMode }, { settingsRepo.isWhitelistMode = it }),
                bool("isDhizukuEnabled", { settingsRepo.isDhizukuEnabled }, { settingsRepo.isDhizukuEnabled = it }),
                bool("isDonateButtonHidden", { settingsRepo.isDonateButtonHidden }, { settingsRepo.isDonateButtonHidden = it }),
                bool("isServiceStoppedByUser", { settingsRepo.isServiceStoppedByUser }, { settingsRepo.isServiceStoppedByUser = it }),
                bool("isUseLegacyVersion", { settingsRepo.isUseLegacyVersion }, { settingsRepo.isUseLegacyVersion = it }),
                bool("isStrictServiceCheck", { settingsRepo.isStrictServiceCheck }, { settingsRepo.isStrictServiceCheck = it }),
                bool("isAllowOwnerTransfer", { settingsRepo.isAllowOwnerTransfer }, { settingsRepo.isAllowOwnerTransfer = it },
                    "true = 允许第三方应用主动转移设备所有者（不影响应用内转移）"),
                bool("isExperimentalQuickActivateFix", { settingsRepo.isExperimentalQuickActivateFix }, { settingsRepo.isExperimentalQuickActivateFix = it }),
                bool("isExperimentalDelegationCleanup", { settingsRepo.isExperimentalDelegationCleanup }, { settingsRepo.isExperimentalDelegationCleanup = it }),
                bool("isExperimentalVerboseLog", { settingsRepo.isExperimentalVerboseLog }, { settingsRepo.isExperimentalVerboseLog = it }),
                bool("isExperimentalStrictWhitelist", { settingsRepo.isExperimentalStrictWhitelist }, { settingsRepo.isExperimentalStrictWhitelist = it }),
                bool("isUpdateAllowPreRelease", { settingsRepo.isUpdateAllowPreRelease }, { settingsRepo.isUpdateAllowPreRelease = it }),
                bool("isStartupUpdateCheck", { settingsRepo.isStartupUpdateCheck }, { settingsRepo.isStartupUpdateCheck = it }),
                bool("isUpdateAllowInsecure", { settingsRepo.isUpdateAllowInsecure }, { settingsRepo.isUpdateAllowInsecure = it }),
                str("denyMode", { settingsRepo.denyMode }, { settingsRepo.denyMode = it }, "DenyMode name"),
                str("insufficientMode", { settingsRepo.insufficientMode }, { settingsRepo.insufficientMode = it }, "InsufficientMode name"),
                str("updateSource", { settingsRepo.updateSource }, { settingsRepo.updateSource = it }, "official / github / custom"),
                str("updateCustomUrl", { settingsRepo.updateCustomUrl }, { settingsRepo.updateCustomUrl = it }, "https://.../update.json"),
                str("debugOfficialBase", { settingsRepo.debugOfficialBase }, { settingsRepo.debugOfficialBase = it }, "http://host:9000"),
                str("debugBuildIndex", { settingsRepo.debugBuildIndex }, { settingsRepo.debugBuildIndex = it }, "build index url"),
                int("debugTimeoutMs", { settingsRepo.debugTimeoutMs }, { settingsRepo.debugTimeoutMs = it }, "1000..120000")
            )
        ),
        ParamGroup(
            "ServiceStatus",
            listOf(
                int("authorizedCount", { ServiceStatus.authorizedCount }, { ServiceStatus.setDebugValues(count = it) }),
                long("startElapsedRealtime", { ServiceStatus.startElapsedRealtime }, { ServiceStatus.setDebugValues(startElapsed = it) }, "-1 = stopped")
            )
        ),
        ParamGroup(
            "DhizukuState",
            listOf(
                bool("isDeviceOwner", { DhizukuState.state.isDeviceOwner }, {
                    DhizukuState.setDebugState(it, DhizukuState.state.isProfileOwner, DhizukuState.state.isDeviceAdmin)
                }, "改全局真值；sync 会打回；影响能力分级"),
                bool("isProfileOwner", { DhizukuState.state.isProfileOwner }, {
                    DhizukuState.setDebugState(DhizukuState.state.isDeviceOwner, it, DhizukuState.state.isDeviceAdmin)
                }, "改全局真值；sync 会打回；影响能力分级"),
                bool("isDeviceAdmin", { DhizukuState.state.isDeviceAdmin }, {
                    DhizukuState.setDebugState(DhizukuState.state.isDeviceOwner, DhizukuState.state.isProfileOwner, it)
                }, "改全局真值；sync 会打回；仅管理员不足以让客户端 init 成功")
            )
        ),
        ParamGroup(
            "UpdateRuntime",
            listOf(
                str("OfficialEndpoint.cached", { OfficialEndpoint.debugCachedHost() }, { OfficialEndpoint.setDebugCachedHost(it) }, "host or empty"),
                bool("StartupUpdateChecker.started", { StartupUpdateChecker.isStarted() }, { StartupUpdateChecker.setStartedForDebug(it) }),
                str("StartupUpdateChecker.state", { StartupUpdateChecker.state.javaClass.simpleName }, { raw ->
                    val state = when (raw.trim().lowercase()) {
                        "idle" -> StartupUpdateChecker.State.Idle
                        "checking" -> StartupUpdateChecker.State.Checking
                        "uptodate" -> StartupUpdateChecker.State.UpToDate
                        "available" -> StartupUpdateChecker.State.Available("-", 0, "", "")
                        "failed" -> StartupUpdateChecker.State.Failed("debug")
                        else -> null
                    }
                    if (state != null) StartupUpdateChecker.forceState(state)
                }, "Idle / Checking / UpToDate / Available / Failed")
            )
        ),
        ParamGroup(
            "UiOverrides",
            listOf(
                bool("DebugUiOverrides.forceActivatedDisplay",
                    { DebugUiOverrides.forceActivatedDisplay },
                    { DebugUiOverrides.applyForceActivatedDisplay(it) },
                    "true = 首页/设置页强制显示已激活（仅 UI，不改判定链）"),
                str("DebugUiOverrides.forceActivationKind",
                    { DebugUiOverrides.forceActivationKind },
                    { DebugUiOverrides.applyForceActivationKind(it) },
                    "device_owner / profile_owner / device_admin"),
                bool("DebugUiOverrides.reset", { false }, { if (it) DebugUiOverrides.reset() },
                    "true = 全部复位")
            )
        ),
        ParamGroup(
            "ServerRuntime",
            listOf(
                bool("RequestPermissionToken.clearAll", { false }, { if (it) RequestPermissionToken.clearAll() }, "true = purge all tokens"),
                bool("DpmApiCatalog.cache", { DpmApiCatalog.isCatalogLoaded() }, { if (!it) DpmApiCatalog.invalidateCache() }, "false = invalidate code map")
            )
        )
    )
}

/** 「实时参数修改」的界面状态。 */
class RuntimeParamViewModel : ViewModel() {

    /** 每次成功写入后自增，驱动界面重新读取当前值 */
    var revision by mutableStateOf(0)
        private set

    var editing by mutableStateOf<RuntimeParam?>(null)
        private set

    var message by mutableStateOf("")
        private set

    fun open(param: RuntimeParam) {
        message = ""
        editing = param
    }

    fun dismiss() {
        editing = null
    }

    fun confirm(raw: String) {
        val param = editing ?: return
        val ok = runCatching { param.write(raw) }.getOrDefault(false)
        message = if (ok) "${param.name} <- $raw" else "rejected: ${param.name} <- $raw"
        if (ok) revision++
    }
}
