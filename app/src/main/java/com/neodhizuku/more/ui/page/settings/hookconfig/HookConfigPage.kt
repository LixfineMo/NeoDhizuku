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
import com.neodhizuku.more.ui.component.NeoCard
import com.neodhizuku.more.ui.theme.exclude
import kotlinx.coroutines.delay

private val QUERIES_SNIPPET = """<queries>
    <package android:name="com.neodhizuku.more" />
    <provider android:authorities="com.neodhizuku.more.dhizuku_server.provider" />
    <provider android:authorities="com.neodhizuku.more.hook.config" />
</queries>"""

/**
 * 模块设置。
 * 作用范围完全由框架决定：LSPosed 里勾选哪些应用、LSPatch 修补哪些应用，
 * 模块就只在这些进程里生效。框架本身就是作用域选择器。
 * 本页只做两件事：显示当前接管状态，以及调整「接管之后做哪些事」。
 * 行为开关走 configVersion 心跳，已运行的应用不需要重启。
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
    var hiddenOwner by remember { mutableStateOf(HookConfigStore.flag("allowHiddenOwnerMethods", true)) }
    var reported by remember { mutableStateOf(HookConfigStore.snapshot()) }
    var framework by remember { mutableStateOf<String?>(null) }
    var hookedApps by remember { mutableStateOf(0) }
    var hookedProcs by remember { mutableStateOf(0) }
    var restartNotice by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HookConfigStore.init(context.applicationContext)
        // 清除历史的应用内允许名单，保证框架勾选是唯一作用域。
        if (HookConfigStore.scope.isNotEmpty()) HookConfigStore.scope = emptySet()
        enabled = HookConfigStore.enabled
        query = HookConfigStore.flag("hijackQuery", true)
        call = HookConfigStore.flag("hijackCall", true)
        forceTrue = HookConfigStore.flag("forceOwnerTrue", false)
        installer = HookConfigStore.flag("allowPackageInstaller", true)
        appendAdmins = HookConfigStore.flag("appendActiveAdmins", true)
        hiddenOwner = HookConfigStore.flag("allowHiddenOwnerMethods", true)
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

            // 作用域：只读，始终跟随框架勾选
            item("scope") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
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

            item("restart") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
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
                NeoCard(modifier = Modifier.fillMaxWidth()) {
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
                            if (!enabled && it) restartNotice = true
                            enabled = it
                            HookConfigStore.enabled = it
                        })
                    }
                }
            }

            // 接管之后做哪些事
            item("group_query") {
                Text(stringResource(R.string.hook_config_group_query),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            item("behavior_query") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        FlagRow(R.string.hook_config_query, R.string.hook_config_query_desc, query) {
                            query = it; HookConfigStore.setFlag("hijackQuery", it)
                        }
                        FlagRow(R.string.hook_config_hidden_owner, R.string.hook_config_hidden_owner_desc, hiddenOwner) {
                            hiddenOwner = it; HookConfigStore.setFlag("allowHiddenOwnerMethods", it)
                        }
                        FlagRow(R.string.hook_config_append_admins, R.string.hook_config_append_admins_desc, appendAdmins) {
                            appendAdmins = it; HookConfigStore.setFlag("appendActiveAdmins", it)
                        }
                    }
                }
            }

            item("group_call") {
                Text(stringResource(R.string.hook_config_group_call),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            item("behavior_call") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        FlagRow(R.string.hook_config_call, R.string.hook_config_call_desc, call) {
                            call = it; HookConfigStore.setFlag("hijackCall", it)
                        }
                        FlagRow(R.string.hook_config_installer, R.string.hook_config_installer_desc, installer) {
                            installer = it; HookConfigStore.setFlag("allowPackageInstaller", it)
                        }
                    }
                }
            }

            item("group_fallback") {
                Text(stringResource(R.string.hook_config_group_fallback),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            item("behavior_fallback") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        FlagRow(R.string.hook_config_force_true, R.string.hook_config_force_true_desc, forceTrue) {
                            forceTrue = it; HookConfigStore.setFlag("forceOwnerTrue", it)
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
                    NeoCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(r.pkg, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                r.framework,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item("lspatch") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
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
                            Toast.makeText(
                                context,
                                context.getString(R.string.hook_config_copy_done),
                                Toast.LENGTH_SHORT
                            ).show()
                        }) { Text(stringResource(R.string.hook_config_copy_queries)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlagRow(labelRes: Int, descRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = checked, onCheckedChange = { onChange(it) })
    }
}
