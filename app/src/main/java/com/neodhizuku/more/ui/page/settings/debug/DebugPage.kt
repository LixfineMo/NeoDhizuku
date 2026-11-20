package com.neodhizuku.more.ui.page.settings.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.R
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.server.RequestPermissionToken
import com.neodhizuku.more.server.ServiceStatus
import com.neodhizuku.more.ui.component.NeoAlertDialog as AlertDialog
import com.neodhizuku.more.ui.component.NeoCard
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.combine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DebugPage
 * (windowInsets: WindowInsets, navController: NavController, contentPadding: PaddingValues = PaddingValues(0.dp))
 * 作用：调试主入口：顶部一份进程速览，下面把能力拆成若干二级页面。 「重置 NeoDhizuku」是唯一在本页直接执行的动作，且必须过两道确认。
 * 备注：Compose 组件
 */
@Composable
fun DebugPage(
    windowInsets: WindowInsets,
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    // 0 = 无弹窗，1 = 第一道确认，2 = 第二道确认
    var resetStep by remember { mutableStateOf(0) }
    var overviewRevision by remember { mutableStateOf(0) }
    var overview by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(overviewRevision) {
        overview = withContext(Dispatchers.IO) { buildOverview(context.applicationContext) }
    }

    DebugScaffold(
        title = stringResource(R.string.debug_title),
        windowInsets = windowInsets,
        onBack = { navController.navigateUp() }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp).combine(padding).combine(contentPadding),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("debug_notice") {
                Text(
                    stringResource(R.string.debug_menu_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item("debug_overview") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.debug_overview_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { overviewRevision++ }) {
                                Text(stringResource(R.string.debug_info_refresh))
                            }
                        }
                        overview.forEach { (label, value) ->
                            DebugInfoRow(label = label, value = value) { text ->
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                    ?.setPrimaryClip(ClipData.newPlainText("NeoDhizuku", text))
                            }
                        }
                    }
                }
            }
            item("debug_entry_update") {
                DebugMenuCard(
                    title = stringResource(R.string.debug_menu_update_config),
                    desc = stringResource(R.string.debug_menu_update_config_desc),
                    onClick = { navController.navigate(SettingsRoute.DebugUpdateConfig.route) }
                )
            }
            item("debug_entry_params") {
                DebugMenuCard(
                    title = stringResource(R.string.debug_menu_runtime_params),
                    desc = stringResource(R.string.debug_menu_runtime_params_desc),
                    onClick = { navController.navigate(SettingsRoute.DebugRuntimeParams.route) }
                )
            }
            item("debug_entry_info") {
                DebugMenuCard(
                    title = stringResource(R.string.debug_menu_runtime_info),
                    desc = stringResource(R.string.debug_menu_runtime_info_desc),
                    onClick = { navController.navigate(SettingsRoute.DebugRuntimeInfo.route) }
                )
            }
            item("debug_entry_files") {
                DebugMenuCard(
                    title = stringResource(R.string.debug_menu_service_files),
                    desc = stringResource(R.string.debug_menu_service_files_desc),
                    onClick = { navController.navigate(SettingsRoute.DebugServiceFiles.route) }
                )
            }
            // 设置-服务里已有同一入口；调试页保留一个直达入口便于排障。
            item("debug_entry_hook") {
                DebugMenuCard(
                    title = stringResource(R.string.hook_config_title),
                    desc = stringResource(R.string.hook_config_entry_desc),
                    onClick = { navController.navigate(SettingsRoute.HookConfig.route) }
                )
            }
            item("debug_entry_reset") {
                DebugMenuCard(
                    title = stringResource(R.string.debug_menu_reset),
                    desc = stringResource(R.string.debug_menu_reset_desc),
                    danger = true,
                    onClick = { resetStep = 1 }
                )
            }
        }
    }

    if (resetStep == 1) {
        AlertDialog(
            onDismissRequest = { resetStep = 0 },
            title = { Text(stringResource(R.string.debug_reset_step1_title)) },
            text = { Text(stringResource(R.string.debug_reset_step1_text)) },
            confirmButton = {
                TextButton(onClick = { resetStep = 2 }) {
                    Text(stringResource(R.string.debug_reset_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { resetStep = 0 }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (resetStep == 2) {
        AlertDialog(
            onDismissRequest = { resetStep = 0 },
            title = { Text(stringResource(R.string.debug_reset_step2_title)) },
            text = { Text(stringResource(R.string.debug_reset_step2_text)) },
            confirmButton = {
                TextButton(onClick = {
                    resetStep = 0
                    AppResetter.reset(context.applicationContext)
                    val activity = context.findActivity()
                    if (activity != null) {
                        AppResetter.exit(activity)
                    } else {
                        // 拿不到 Activity 时数据仍已清空，交给系统回收进程
                        Runtime.getRuntime().exit(0)
                    }
                }) {
                    Text(stringResource(R.string.debug_reset_final))
                }
            },
            dismissButton = {
                TextButton(onClick = { resetStep = 0 }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * buildOverview
 * (context: Context)
 * 作用：只取内存状态与一次文件扫描，不做网络、不读数据库。
 * 备注：仅内部使用
 */
private fun buildOverview(context: Context): List<Pair<String, String>> {
    val state = DhizukuState.state
    val fileCount = runCatching { PrivateFileStore.list(context).size }.getOrDefault(0)
    return listOf(
        "versionName / versionCode" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "BUILD_TYPE / DEBUG / TEST_ONLY" to
            "${BuildConfig.BUILD_TYPE} / ${BuildConfig.DEBUG} / ${BuildConfig.TEST_ONLY}",
        "DhizukuState.state" to state.toString(),
        "DebugUiOverrides.displayState" to DebugUiOverrides.displayState().toString(),
        "ServiceStatus.isRunning / authorizedCount" to
            "${ServiceStatus.isRunning()} / ${ServiceStatus.authorizedCount}",
        "ServiceStatus.formatUptime" to ServiceStatus.formatUptime(ServiceStatus.uptimeMillis()),
        "RequestPermissionToken.size" to RequestPermissionToken.snapshot().size.toString(),
        "PrivateFileStore.list.size" to fileCount.toString()
    )
}
