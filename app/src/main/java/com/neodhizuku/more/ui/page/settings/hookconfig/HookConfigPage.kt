package com.neodhizuku.more.ui.page.settings.hookconfig

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.neodhizuku.more.R
import com.neodhizuku.more.server.HookConfigStore
import com.neodhizuku.more.ui.theme.exclude
import kotlinx.coroutines.delay

private val QUERIES_SNIPPET = """<queries>
    <package android:name="com.neodhizuku.more" />
    <provider android:authorities="com.neodhizuku.more.dhizuku_server.provider" />
    <provider android:authorities="com.neodhizuku.more.hook.config" />
</queries>"""

/**
 * 模块配置：作用域完全跟随 LSPosed / LSPatch 里勾选的应用。
 *
 * 这里不再列「可选的应用」—— 模块被加载到哪个进程是框架在进程启动时决定的，
 * 应用内再勾选一遍既重复又容易和框架状态打架。本页只负责：
 *   - 总开关与行为开关（心跳热更新，已运行的进程无需重启）
 *   - 展示框架类型和 hook 到的应用 / 进程数量
 *   - 明确提醒：改了框架作用域、或刚打开总开关，必须重启目标应用
 *
 * 不注册 Koin：直接读写 [HookConfigStore] 单例，Provider 才是唯一执行入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HookConfigPage(windowInsets: WindowInsets, navController: NavController) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(HookConfigStore.enabled) }
    var query by remember { mutableStateOf(HookConfigStore.flag("hijackQuery", true)) }
    var call by remember { mutableStateOf(HookConfigStore.flag("hijackCall", true)) }
    var forceTrue by remember { mutableStateOf(HookConfigStore.flag("forceOwnerTrue", false)) }
    var installer by remember { mutableStateOf(HookConfigStore.flag("allowPackageInstaller", true)) }
    var appendAdmins by remember { mutableStateOf(HookConfigStore.flag("appendActiveAdmins", true)) }
    var reported by remember { mutableStateOf(HookConfigStore.snapshot()) }
    var framework by remember { mutableStateOf<String?>(null) }
    var hookedApps by remember { mutableStateOf(0) }
    var hookedProcs by remember { mutableStateOf(0) }
    var restartNotice by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HookConfigStore.init(context.applicationContext)
        // 历史版本可能存过应用内 scope；现在一律清空，跟随框架勾选。
        // 清空会让已在运行的进程与框架状态不一致，提示用户重启目标应用。
        if (HookConfigStore.scope.isNotEmpty()) {
            HookConfigStore.scope = emptySet()
            restartNotice = true
        }
        enabled = HookConfigStore.enabled
        query = HookConfigStore.flag("hijackQuery", true)
        call = HookConfigStore.flag("hijackCall", true)
        forceTrue = HookConfigStore.flag("forceOwnerTrue", false)
        installer = HookConfigStore.flag("allowPackageInstaller", true)
        appendAdmins = HookConfigStore.flag("appendActiveAdmins", true)
        while (true) {
            reported = HookConfigStore.snapshot()
            framework = HookConfigStore.frameworkLabel()
            val counts = HookConfigStore.hookedCounts()
            hookedApps = counts.first
            hookedProcs = counts.second
            delay(2000)
        }
    }

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text(stringResource(R.string.hook_config_title)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("note") {
                Text(
                    stringResource(R.string.hook_config_note_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 作用域：只跟随框架，不列应用
            item("scope") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            stringResource(R.string.hook_config_scope_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.hook_config_scope_follow_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val frameworkName = framework
                            ?: stringResource(R.string.hook_config_framework_none)
                        Text(
                            stringResource(R.string.hook_config_framework, frameworkName),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.hook_config_hooked, hookedApps, hookedProcs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 重启提醒：作用域是进程启动时决定的，改了必须重开目标应用
            item("restart") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            stringResource(R.string.hook_config_restart_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.hook_config_restart_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (restartNotice) {
                            Text(
                                stringResource(R.string.hook_config_restart_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = { restartNotice = false }) {
                                Text(stringResource(R.string.hook_config_restart_dismiss))
                            }
                        }
                    }
                }
            }

            item("master") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.hook_config_master), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.hook_config_master_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = {
                            // 从关到开时，已经运行的目标进程不会重新加载模块
                            if (!enabled && it) restartNotice = true
                            enabled = it
                            HookConfigStore.enabled = it
                        })
                    }
                }
            }

            item("behavior_title") {
                Text(stringResource(R.string.hook_config_behavior), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            item("behavior") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        FlagRow(R.string.hook_config_query, query) {
                            query = it; HookConfigStore.setFlag("hijackQuery", it)
                        }
                        FlagRow(R.string.hook_config_call, call) {
                            call = it; HookConfigStore.setFlag("hijackCall", it)
                        }
                        FlagRow(R.string.hook_config_force_true, forceTrue) {
                            forceTrue = it; HookConfigStore.setFlag("forceOwnerTrue", it)
                        }
                        FlagRow(R.string.hook_config_installer, installer) {
                            installer = it; HookConfigStore.setFlag("allowPackageInstaller", it)
                        }
                        FlagRow(R.string.hook_config_append_admins, appendAdmins) {
                            appendAdmins = it; HookConfigStore.setFlag("appendActiveAdmins", it)
                        }
                    }
                }
            }

            item("reported_title") {
                Text(stringResource(R.string.hook_config_reported), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (reported.isEmpty()) {
                item("reported_empty") {
                    Text(stringResource(R.string.hook_config_no_report),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(reported, key = { "${it.pkg}:${it.pid}" }) { r ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(r.pkg, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${r.processName}  pid=${r.pid} uid=${r.uid} ${r.framework} sdk=${r.sdk}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item("lspatch") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.hook_config_lspatch), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.hook_config_lspatch_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("queries", QUERIES_SNIPPET))
                            Toast.makeText(context, "copied", Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.hook_config_copy_queries)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlagRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = { onChange(it) })
    }
}
