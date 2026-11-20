package com.neodhizuku.more.ui.page.settings.debug

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.OfficialEndpoint
import com.neodhizuku.more.data.common.util.StartupUpdateChecker
import com.neodhizuku.more.data.common.util.UpdateConfig
import com.neodhizuku.more.data.common.util.UpdateLinks
import com.neodhizuku.more.data.settings.repo.ApiPolicyRepo
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.LogRepo
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.data.settings.repo.WhitelistRepo
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.server.DpmApiCatalog
import com.neodhizuku.more.server.RequestPermissionToken
import com.neodhizuku.more.server.ServiceStatus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class InfoSection(
    val name: String,
    val rows: List<Pair<String, String>>
)

/**
 * 「详细运行信息」的数据源。
 * 分板块展出进程、身份、服务、授权 token、接口状态、更新状态、
 * 全部设置值、编译期开关、内存、私有文件与最近日志。
 * 所有变量名保持代码原样，不做翻译。
 */
object RuntimeInfoCollector : KoinComponent {

    private val settingsRepo: SettingsRepo by inject()
    private val apiPolicyRepo: ApiPolicyRepo by inject()
    private val whitelistRepo: WhitelistRepo by inject()
    private val appRepo: AppRepo by inject()
    private val logRepo: LogRepo by inject()

    /**
     * section
     * (name: String, vararg rows: Pair<String, String>)
     * 作用：执行 section
     * 备注：仅内部使用
     */
    private fun section(name: String, vararg rows: Pair<String, String>) =
        InfoSection(name, rows.toList())

    /**
     * safe
     * (block: () -> String)
     * 作用：执行 safe
     * 备注：仅内部使用
     */
    private fun safe(block: () -> String): String = runCatching(block).getOrDefault("!")

    /**
     * collect
     * (context: Context)
     * 作用：收集
     * 备注：—
     */
    fun collect(context: Context): List<InfoSection> {
        val app = context.applicationContext
        val out = mutableListOf<InfoSection>()

        out += section(
            "PROCESS",
            "Process.myPid" to Process.myPid().toString(),
            "Process.myUid" to Process.myUid().toString(),
            "Process.myTid" to Process.myTid().toString(),
            "processName" to safe { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else BuildConfig.APPLICATION_ID },
            "packageName" to app.packageName,
            "applicationId" to BuildConfig.APPLICATION_ID,
            "versionName" to BuildConfig.VERSION_NAME,
            "versionCode" to BuildConfig.VERSION_CODE.toString(),
            "BUILD_TYPE" to BuildConfig.BUILD_TYPE,
            "DEBUG" to BuildConfig.DEBUG.toString(),
            "SDK_INT" to Build.VERSION.SDK_INT.toString(),
            "RELEASE" to Build.VERSION.RELEASE,
            "FINGERPRINT" to Build.FINGERPRINT,
            "MANUFACTURER" to Build.MANUFACTURER,
            "MODEL" to Build.MODEL,
            "BOARD" to Build.BOARD,
            "HARDWARE" to Build.HARDWARE,
            "SUPPORTED_ABIS" to Build.SUPPORTED_ABIS.joinToString(","),
            "TYPE" to Build.TYPE,
            "DISPLAY" to Build.DISPLAY,
            "Locale.getDefault" to java.util.Locale.getDefault().toString(),
            "TimeZone" to java.util.TimeZone.getDefault().id,
            "isDeviceProtectedStorage" to app.isDeviceProtectedStorage.toString(),
            "dataDir" to app.applicationInfo.dataDir,
            "filesDir" to app.filesDir.absolutePath,
            "noBackupFilesDir" to app.noBackupFilesDir.absolutePath,
            "firstInstallTime" to safe { app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime.toString() },
            "lastUpdateTime" to safe { app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime.toString() },
            "signatureSha256" to signature(app)
        )

        val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = DhizukuState.admin
        val st = DhizukuState.state
        out += section(
            "IDENTITY",
            "DhizukuState.state" to st.toString(),
            "state.isDeviceOwner" to st.isDeviceOwner.toString(),
            "state.isProfileOwner" to st.isProfileOwner.toString(),
            "state.isDeviceAdmin" to st.isDeviceAdmin.toString(),
            "state.isOwner" to st.isOwner.toString(),
            "state.isActive" to st.isActive.toString(),
            "DebugUiOverrides.forceActivatedDisplay" to DebugUiOverrides.forceActivatedDisplay.toString(),
            "DebugUiOverrides.forceActivationKind" to DebugUiOverrides.forceActivationKind,
            "DebugUiOverrides.displayState" to DebugUiOverrides.displayState().toString(),
            "state.level" to st.level.toString(),
            "admin" to admin.flattenToString(),
            "dpm.isDeviceOwnerApp" to safe { dpm.isDeviceOwnerApp(app.packageName).toString() },
            "dpm.isProfileOwnerApp" to safe { dpm.isProfileOwnerApp(app.packageName).toString() },
            "dpm.isAdminActive" to safe { dpm.isAdminActive(admin).toString() },
            "DpDirectActivation.isTemporaryCached" to safe {
                com.neodhizuku.more.data.common.util.DpDirectActivation.isTemporaryCached().toString()
            }
        )

        out += section(
            "SERVICE",
            "ServiceStatus.isRunning" to ServiceStatus.isRunning().toString(),
            "ServiceStatus.startElapsedRealtime" to ServiceStatus.startElapsedRealtime.toString(),
            "ServiceStatus.uptimeMillis" to ServiceStatus.uptimeMillis().toString(),
            "ServiceStatus.formatUptime" to ServiceStatus.formatUptime(ServiceStatus.uptimeMillis()),
            "ServiceStatus.authorizedCount" to ServiceStatus.authorizedCount.toString(),
            "SettingsRepo.isServiceStoppedByUser" to safe { settingsRepo.isServiceStoppedByUser.toString() },
            "SettingsRepo.isDhizukuEnabled" to safe { settingsRepo.isDhizukuEnabled.toString() }
        )

        val tokens = RequestPermissionToken.snapshot()
        val tokenRows = mutableListOf<Pair<String, String>>()
        tokenRows += "RequestPermissionToken.size" to tokens.size.toString()
        tokenRows += "RequestPermissionToken.TRANSACT_REQUEST_PERMISSION" to
            RequestPermissionToken.TRANSACT_REQUEST_PERMISSION.toString()
        tokenRows += "RequestPermissionToken.IDHIZUKU_DESCRIPTOR" to
            RequestPermissionToken.IDHIZUKU_DESCRIPTOR
        tokenRows += "RequestPermissionToken.TTL_MILLIS" to "600000"
        tokens.forEachIndexed { index, t ->
            val prefix = "tokens[$index]"
            tokenRows += "$prefix.token" to t.token
            tokenRows += "$prefix.uid" to t.uid.toString()
            tokenRows += "$prefix.issuedAt" to t.issuedAt.toString()
            tokenRows += "$prefix.consumed" to t.consumed.toString()
            tokenRows += "$prefix.hasListener" to t.hasListener.toString()
            tokenRows += "$prefix.remainingMs" to t.remainingMs.toString()
        }
        out += InfoSection("TOKENS", tokenRows)

        val global = apiPolicyRepo.globalSnapshot()
        val appsPolicy = runBlocking(Dispatchers.IO) {
            runCatching { apiPolicyRepo.flowApps().first() }.getOrDefault(emptyMap())
        }
        val authorizedApps = runBlocking(Dispatchers.IO) {
            runCatching { appRepo.all() }.getOrDefault(emptyList())
        }
        val audits = runBlocking(Dispatchers.IO) {
            runCatching { logRepo.flowAudit().first() }.getOrDefault(emptyList())
        }
        val whitelist = whitelistRepo.snapshot()
        out += section(
            "API_STATE",
            "DpmApiCatalog.isCatalogLoaded" to DpmApiCatalog.isCatalogLoaded().toString(),
            "DpmApiCatalog.methodName(1)" to (DpmApiCatalog.methodName(1) ?: "-"),
            "DpmApiCatalog.methodName(17)" to (DpmApiCatalog.methodName(17) ?: "-"),
            "ApiPolicyRepo.globalSnapshot.size" to global.size.toString(),
            "ApiPolicyRepo.globalSnapshot" to global.toString(),
            "ApiPolicyRepo.flowApps.size" to appsPolicy.size.toString(),
            "ApiPolicyRepo.flowApps" to appsPolicy.toString(),
            "AppRepo.all.size" to authorizedApps.size.toString(),
            "AppRepo.all" to authorizedApps.take(30).joinToString("\n") {
                "uid=${it.uid} allowApi=${it.allowApi} blocked=${it.blocked} " +
                    "signature=${it.signature.take(16)} createdAt=${it.createdAt}"
            },
            "LogRepo.flowAudit.size" to audits.size.toString(),
            "WhitelistRepo.snapshot.size" to whitelist.size.toString(),
            "WhitelistRepo.snapshot" to whitelist.joinToString("\n") { it.packageName }
        )

        val cfg = runCatching {
            UpdateConfig(
                source = com.neodhizuku.more.data.common.util.UpdateSource.of(settingsRepo.updateSource),
                allowInsecure = settingsRepo.isUpdateAllowInsecure,
                customUrl = settingsRepo.updateCustomUrl,
                officialBase = settingsRepo.debugOfficialBase
            )
        }.getOrNull()
        out += section(
            "UPDATE",
            "StartupUpdateChecker.state" to StartupUpdateChecker.state.toString(),
            "StartupUpdateChecker.isStarted" to StartupUpdateChecker.isStarted().toString(),
            "OfficialEndpoint.debugCachedHost" to OfficialEndpoint.debugCachedHost(),
            "UpdateLinks.OFFICIAL_HOSTS" to UpdateLinks.OFFICIAL_HOSTS.joinToString(","),
            "UpdateLinks.OFFICIAL_BASE_DEFAULT" to UpdateLinks.OFFICIAL_BASE_DEFAULT,
            "UpdateLinks.OFFICIAL_VERSION_PATH" to UpdateLinks.OFFICIAL_VERSION_PATH,
            "UpdateLinks.GITHUB_VERSION_JSON" to UpdateLinks.GITHUB_VERSION_JSON,
            "UpdateLinks.GITHUB_ENABLED" to UpdateLinks.GITHUB_ENABLED.toString(),
            "UpdateConfig.versionJsonUrl" to (cfg?.versionJsonUrl ?: "-"),
            "SettingsRepo.updateSource" to safe { settingsRepo.updateSource },
            "SettingsRepo.updateCustomUrl" to safe { settingsRepo.updateCustomUrl },
            "SettingsRepo.debugOfficialBase" to safe { settingsRepo.debugOfficialBase },
            "SettingsRepo.debugBuildIndex" to safe { settingsRepo.debugBuildIndex },
            "SettingsRepo.debugTimeoutMs" to safe { settingsRepo.debugTimeoutMs.toString() },
            "SettingsRepo.isUpdateAllowInsecure" to safe { settingsRepo.isUpdateAllowInsecure.toString() },
            "SettingsRepo.isUpdateAllowPreRelease" to safe { settingsRepo.isUpdateAllowPreRelease.toString() },
            "SettingsRepo.isStartupUpdateCheck" to safe { settingsRepo.isStartupUpdateCheck.toString() }
        )

        out += section(
            "SETTINGS",
            "isWhitelistMode" to safe { settingsRepo.isWhitelistMode.toString() },
            "isDhizukuEnabled" to safe { settingsRepo.isDhizukuEnabled.toString() },
            "isDonateButtonHidden" to safe { settingsRepo.isDonateButtonHidden.toString() },
            "isAllowOwnerTransfer" to safe { settingsRepo.isAllowOwnerTransfer.toString() },
            "isServiceStoppedByUser" to safe { settingsRepo.isServiceStoppedByUser.toString() },
            "isUseLegacyVersion" to safe { settingsRepo.isUseLegacyVersion.toString() },
            "isStrictServiceCheck" to safe { settingsRepo.isStrictServiceCheck.toString() },
            "isExperimentalQuickActivateFix" to safe { settingsRepo.isExperimentalQuickActivateFix.toString() },
            "isExperimentalDelegationCleanup" to safe { settingsRepo.isExperimentalDelegationCleanup.toString() },
            "isExperimentalVerboseLog" to safe { settingsRepo.isExperimentalVerboseLog.toString() },
            "isExperimentalStrictWhitelist" to safe { settingsRepo.isExperimentalStrictWhitelist.toString() },
            "isUpdateAllowPreRelease" to safe { settingsRepo.isUpdateAllowPreRelease.toString() },
            "isStartupUpdateCheck" to safe { settingsRepo.isStartupUpdateCheck.toString() },
            "isUpdateAllowInsecure" to safe { settingsRepo.isUpdateAllowInsecure.toString() },
            "denyMode" to safe { settingsRepo.denyMode },
            "insufficientMode" to safe { settingsRepo.insufficientMode },
            "updateSource" to safe { settingsRepo.updateSource },
            "updateCustomUrl" to safe { settingsRepo.updateCustomUrl },
            "debugOfficialBase" to safe { settingsRepo.debugOfficialBase },
            "debugBuildIndex" to safe { settingsRepo.debugBuildIndex },
            "debugTimeoutMs" to safe { settingsRepo.debugTimeoutMs.toString() }
        )

        val flags = NeoDhizuku::class.java.declaredFields
            .filter { it.type == java.lang.Boolean.TYPE }
            .mapNotNull { f ->
                runCatching {
                    f.isAccessible = true
                    f.name to f.getBoolean(null).toString()
                }.getOrNull()
            }
            .sortedBy { it.first }
        out += InfoSection("BUILD_FLAGS (const, read-only)", flags)

        val rt = Runtime.getRuntime()
        out += section(
            "MEMORY",
            "Runtime.maxMemory" to rt.maxMemory().toString(),
            "Runtime.totalMemory" to rt.totalMemory().toString(),
            "Runtime.freeMemory" to rt.freeMemory().toString(),
            "Runtime.availableProcessors" to rt.availableProcessors().toString(),
            "Thread.activeCount" to Thread.activeCount().toString(),
            "android.os.Debug.nativeHeapAllocatedSize" to android.os.Debug.getNativeHeapAllocatedSize().toString(),
            "System.getProperty(os.arch)" to (System.getProperty("os.arch") ?: "-")
        )

        val files = PrivateFileStore.list(app)
        out += InfoSection(
            "PRIVATE_FILES",
            files.map { "${it.relative} (${it.sizeBytes}B, editable=${it.editable})" to it.absolute }
        )

        val logs = runBlocking(Dispatchers.IO) {
            runCatching { NeoLog.snapshot(30) }.getOrDefault(emptyList())
        }
        out += InfoSection(
            "LOG_BUFFER",
            logs.map { "${it.level}/${it.sub} @${it.timestamp}" to it.message }
        )

        return out
    }

    /**
     * signature
     * (context: Context)
     * 作用：执行 signature
     * 备注：仅内部使用
     */
    private fun signature(context: Context): String = safe {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val first = sigs?.firstOrNull()
        if (first == null) {
            "-"
        } else {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.update(first.toByteArray())
            md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(32)
        }
    }
}

/** 「详细运行信息」的界面状态。 */
class RuntimeInfoViewModel : ViewModel() {
    var sections by mutableStateOf<List<InfoSection>>(emptyList())
        private set

    var copied by mutableStateOf("")
        private set

    var query by mutableStateOf("")
        private set

    /** 收起的分区名；默认全部展开 */
    var collapsed by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 本次快照的采集时刻，界面用它区分新旧数据 */
    var collectedAt by mutableStateOf(0L)
        private set

    /**
     * updateQuery
     * (value: String)
     * 作用：更新 Query
     * 备注：—
     */
    fun updateQuery(value: String) {
        query = value
    }

    /**
     * toggle
     * (name: String)
     * 作用：切换
     * 备注：—
     */
    fun toggle(name: String) {
        collapsed = if (name in collapsed) collapsed - name else collapsed + name
    }

    /**
     * timeLabel
     * ()
     * 作用：执行 timeLabel
     * 备注：—
     */
    fun timeLabel(): String = if (collectedAt == 0L) "-" else
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(collectedAt))

    /**
     * refresh
     * (context: Context)
     * 作用：刷新
     * 备注：—
     */
    fun refresh(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { RuntimeInfoCollector.collect(context.applicationContext) }
                .getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                sections = result
                collectedAt = System.currentTimeMillis()
            }
        }
    }

    /**
     * copy
     * (context: Context, value: String)
     * 作用：复制
     * 备注：—
     */
    fun copy(context: Context, value: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText("NeoDhizuku", value))
        copied = value.take(60)
    }

    /**
     * copyAll
     * (context: Context)
     * 作用：整份快照拼成纯文本，便于一次贴到 issue 里。
     * 备注：—
     */
    fun copyAll(context: Context) {
        val rows = sections.sumOf { it.rows.size }
        val text = buildString {
            sections.forEach { section ->
                append("## ").append(section.name).append('\n')
                section.rows.forEach { (label, value) ->
                    append(label).append(" = ").append(value).append('\n')
                }
                append('\n')
            }
        }
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText("NeoDhizuku", text))
        copied = "snapshot ($rows rows, ${sections.size} sections)"
    }
}
