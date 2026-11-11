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
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

/**
 * 「实时参数修改」：列出进程里所有可写变量，点任意一项弹出输入框改值。
 * 变量名与取值保持代码原样，不翻译；不做危险项屏蔽 —— 关掉守护服务、
 * 覆盖身份状态、清空 token 表都会立刻生效，下一次 sync 或重启才会恢复。
 */
@Composable
fun RuntimeParamPage(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: RuntimeParamViewModel = koinViewModel()
) {
    val groups = remember(viewModel.revision) { RuntimeParamCatalog.groups() }
    val editing = viewModel.editing

    DebugScaffold(
        title = stringResource(R.string.debug_params_title),
        windowInsets = windowInsets,
        onBack = { navController.navigateUp() }
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
            if (viewModel.message.isNotBlank()) {
                item("params_message") {
                    Text(
                        viewModel.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            groups.forEach { group ->
                item("group_${group.name}") {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(group.params, key = { "${group.name}.${it.name}" }) { param ->
                    ParamCard(param) { viewModel.open(param) }
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
