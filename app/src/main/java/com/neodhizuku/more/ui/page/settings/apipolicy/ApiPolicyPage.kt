package com.neodhizuku.more.ui.page.settings.apipolicy

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.Checkbox
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.server.DpmPolicyCatalog
import com.neodhizuku.more.ui.theme.exclude

import org.koin.androidx.compose.koinViewModel

/**
 * 单项接口配置：逐个接口决定「哪个应用能用、用到哪一步」。
 * 与「拒绝服务」相互独立 —— 拒绝服务是整体开关，这里是细项；
 * 但拒绝服务优先：首页停服时按拒绝模式统一处理，此处配置不参与。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiPolicyPage(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: ApiPolicyViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.collect()
    }

    val context = LocalContext.current
    // 按分组顺序展开，组内高危排后；同时记下每组的首项位置，用于插入小节标题
    val apis = remember {
        DpmPolicyCatalog.Group.values().flatMap { g -> DpmPolicyCatalog.byGroup(g) }
    }
    val groupAt = remember(apis) {
        val map = HashMap<Int, DpmPolicyCatalog.Group>()
        apis.forEachIndexed { i, def ->
            if (i == 0 || apis[i - 1].group != def.group) map[i] = def.group
        }
        map
    }
    var editing by remember { mutableStateOf<ApiPolicyAppItem?>(null) }
    var globalExpanded by remember { mutableStateOf(true) }

    // 提到 Scaffold 之外：底部弹窗也在外面用它们，放在 content lambda 里会取不到
    val state = viewModel.state
    val global = state.global

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = { Text(stringResource(R.string.api_policy_title)) }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("note") {
                Text(
                    stringResource(R.string.api_policy_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item("global_title") {
                SectionTitle(stringResource(R.string.api_policy_global_section))
            }

            item("global_card") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { globalExpanded = !globalExpanded }) {
                            Text(
                                if (globalExpanded) {
                                    stringResource(R.string.api_policy_collapse)
                                } else {
                                    stringResource(R.string.api_policy_expand)
                                }
                            )
                        }
                        if (globalExpanded) {
                            apis.forEachIndexed { index, def ->
                                val group = groupAt[index]
                                if (group != null) {
                                    Text(
                                        DpmPolicyCatalog.groupLabel(context, group),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(
                                            start = 16.dp, end = 16.dp,
                                            top = if (index == 0) 0.dp else 12.dp,
                                            bottom = 4.dp
                                        )
                                    )
                                }
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                ApiCheckRow(
                                    label = DpmPolicyCatalog.label(context, def.id),
                                    desc = DpmPolicyCatalog.desc(context, def.id),
                                    checked = global[def.id] ?: def.defaultAllowed,
                                    explicit = global.containsKey(def.id),
                                    onCheckedChange = { viewModel.setGlobal(def.id, it) }
                                )
                            }
                        }
                    }
                }
            }

            item("apps_title") {
                SectionTitle(stringResource(R.string.api_policy_app_section))
            }

            if (!state.loading && state.configuredApps.isEmpty() && state.others.isEmpty()) {
                item("apps_empty") {
                    Text(
                        stringResource(R.string.api_policy_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(state.configuredApps, key = { "c_${it.packageName}" }) { item ->
                AppPolicyCard(
                    item = item,
                    configured = true,
                    onClick = { editing = item }
                )
            }

            items(state.others, key = { "o_${it.packageName}" }) { item ->
                AppPolicyCard(
                    item = item,
                    configured = false,
                    onClick = { editing = item }
                )
            }
        }
    }

    editing?.let { item ->
        AppPolicyDialog(
            item = item,
            global = state.global,
            appConfig = state.apps[item.packageName] ?: emptyMap(),
            onDismiss = { editing = null },
            onToggle = { def, allowed -> viewModel.setApp(item.packageName, def.id, allowed) },
            onClearAll = { viewModel.clearAppAll(item.packageName) }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AppPolicyCard(
    item: ApiPolicyAppItem,
    configured: Boolean,
    onClick: () -> Unit
) {
    NeoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                item.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (configured) {
                Text(
                    stringResource(R.string.api_policy_configured_count, item.configured.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    stringResource(R.string.api_policy_follow_global),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                item.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiCheckRow(
    label: String,
    desc: String,
    checked: Boolean,
    explicit: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (explicit) {
                Text(
                    stringResource(R.string.api_policy_explicit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AppPolicyDialog(
    item: ApiPolicyAppItem,
    global: Map<String, Boolean>,
    appConfig: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onToggle: (DpmPolicyCatalog.DpmDef, Boolean) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    val apis = remember {
        DpmPolicyCatalog.Group.values().flatMap { g -> DpmPolicyCatalog.byGroup(g) }
    }
    val groupAt = remember(apis) {
        val map = HashMap<Int, DpmPolicyCatalog.Group>()
        apis.forEachIndexed { i, def ->
            if (i == 0 || apis[i - 1].group != def.group) map[i] = def.group
        }
        map
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.label) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                apis.forEachIndexed { index, def ->
                    val group = groupAt[index]
                    if (group != null) {
                        Text(
                            DpmPolicyCatalog.groupLabel(context, group),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                top = if (index == 0) 0.dp else 10.dp, bottom = 2.dp
                            )
                        )
                    }
                    val effective = appConfig[def.id] ?: global[def.id] ?: def.defaultAllowed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = effective,
                            onCheckedChange = { onToggle(def, it) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                DpmPolicyCatalog.label(context, def.id),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val apiDesc = DpmPolicyCatalog.desc(context, def.id)
                            if (apiDesc.isNotBlank()) {
                                Text(
                                    apiDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (appConfig.containsKey(def.id)) {
                                Text(
                                    stringResource(R.string.api_policy_explicit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.api_policy_close))
            }
        },
        dismissButton = {
            TextButton(onClick = { onClearAll() }) {
                Text(stringResource(R.string.api_policy_clear_all))
            }
        }
    )
}
