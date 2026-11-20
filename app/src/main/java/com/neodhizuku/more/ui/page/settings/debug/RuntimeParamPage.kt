package com.neodhizuku.more.ui.page.settings.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.ui.component.NeoCard
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

/**
 * RuntimeParamPage
 * (windowInsets: WindowInsets, navController: NavController, viewModel: RuntimeParamViewModel = koinViewModel())
 * 作用：「实时参数修改」：列出进程里所有可写变量，点任意一项弹出输入框改值。 变量名与取值保持代码原样，不翻译；不做危险项屏蔽 —— 关掉守护服务、 覆盖身份状态、清空 token 表都会立刻生效，下一次 sync 或重启才会恢复。
 * 备注：Compose 组件
 */
@Composable
fun RuntimeParamPage(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: RuntimeParamViewModel = koinViewModel()
) {
    val allGroups = remember(viewModel.revision) { RuntimeParamCatalog.groups() }
    val total = allGroups.sumOf { it.params.size }
    val query = viewModel.query.trim()
    val groups = allGroups.map { group ->
        group.copy(
            params = group.params.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                    it.hint.contains(query, ignoreCase = true)
            }
        )
    }.filter { it.params.isNotEmpty() }
    val shown = groups.sumOf { it.params.size }
    val editing = viewModel.editing

    DebugScaffold(
        title = stringResource(R.string.debug_params_title),
        windowInsets = windowInsets,
        onBack = { navController.navigateUp() },
        actions = {
            TextButton(onClick = { viewModel.refresh() }) {
                Text(stringResource(R.string.debug_info_refresh))
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp).combine(padding),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("params_notice") {
                Text(
                    stringResource(R.string.debug_params_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item("params_search") {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.updateQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.debug_search)) }
                )
            }
            item("params_count") {
                Text(
                    stringResource(R.string.debug_params_count, shown, total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (viewModel.message.isNotBlank()) {
                item("params_message") {
                    Text(
                        viewModel.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item("params_ui_override") { UiOverrideCard { viewModel.refresh() } }
            if (groups.isEmpty()) {
                item("params_empty") {
                    Text(
                        stringResource(R.string.debug_params_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            groups.forEach { group ->
                item("group_${group.name}") {
                    Surface(
                        onClick = { viewModel.toggleGroup(group.name) },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${group.name} (${group.params.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (group.name in viewModel.collapsed) "+" else "-",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (group.name !in viewModel.collapsed) {
                    items(group.params, key = { "${group.name}.${it.name}" }) { param ->
                        ParamCard(param) { viewModel.open(param) }
                    }
                }
            }
        }
    }

    if (editing != null) {
        DebugValueDialog(
            name = editing.name,
            value = runCatching { editing.read() }.getOrDefault(""),
            hint = editing.hint,
            onConfirm = { viewModel.confirm(it) },
            onDismiss = { viewModel.dismiss() }
        )
    }
}

/**
 * UiOverrideCard
 * (onChanged: () -> Unit)
 * 作用：显示层覆盖的快捷开关：只影响首页/设置页文案，不进入执行链。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun UiOverrideCard(onChanged: () -> Unit) {
    val kinds = listOf("device_owner", "profile_owner", "device_admin")
    NeoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(R.string.debug_params_ui_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.debug_params_ui_force),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = DebugUiOverrides.forceActivatedDisplay,
                    onCheckedChange = {
                        DebugUiOverrides.applyForceActivatedDisplay(it)
                        onChanged()
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                kinds.forEach { kind ->
                    val selected = DebugUiOverrides.forceActivationKind == kind
                    if (selected) {
                        Button(
                            onClick = {
                                DebugUiOverrides.applyForceActivationKind(kind)
                                onChanged()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(kind, style = MaterialTheme.typography.labelSmall) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                DebugUiOverrides.applyForceActivationKind(kind)
                                onChanged()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(kind, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            TextButton(onClick = {
                DebugUiOverrides.reset()
                onChanged()
            }) {
                Text(stringResource(R.string.debug_params_ui_reset))
            }
        }
    }
}

/**
 * ParamCard
 * (param: RuntimeParam, onClick: () -> Unit)
 * 作用：执行 ParamCard
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun ParamCard(param: RuntimeParam, onClick: () -> Unit) {
    NeoCard(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(param.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        param.kind.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (param.hint.isNotBlank()) {
                        Text(
                            param.hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    runCatching { param.read() }.getOrDefault("?").ifBlank { "\"\"" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
