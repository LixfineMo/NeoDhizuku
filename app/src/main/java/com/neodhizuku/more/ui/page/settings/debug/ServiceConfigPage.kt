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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

/** 「服务配置修改」：私有区每个配置文件一张卡片。 */
@Composable
fun ServiceConfigPage(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: ServiceConfigViewModel = koinViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh(context.applicationContext) }

    DebugScaffold(
        title = stringResource(R.string.debug_service_files_title),
        windowInsets = windowInsets,
        onBack = { navController.navigateUp() },
        actions = {
            TextButton(onClick = { viewModel.refresh(context.applicationContext) }) {
                Text(stringResource(R.string.debug_info_refresh))
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp).combine(padding),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("files_notice") {
                Text(
                    stringResource(R.string.debug_service_files_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (viewModel.files.isEmpty()) {
                item("files_empty") {
                    Text(stringResource(R.string.debug_service_files_empty))
                }
            }
            items(viewModel.files, key = { it.key }) { file ->
                FileCard(file) {
                    if (file.editable) {
                        navController.navigate(SettingsRoute.DebugServiceFileEdit.route(file.key))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileCard(file: PrivateFile, onClick: () -> Unit) {
    val tint = if (file.editable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            enabled = file.editable,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(file.relative, style = MaterialTheme.typography.bodyLarge, color = tint)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${file.sizeBytes} B  ${file.lastModified}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        file.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    file.absolute,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 单文件编辑：输入框尽量占满整屏，底部一个「确认修改」。
 * 写入走临时文件改名，改到 api_policy.json / whitelist.json 时内存镜像会立刻重载。
 */
@Composable
fun ServiceFileEditPage(
    windowInsets: WindowInsets,
    navController: NavController,
    key: String,
    viewModel: ServiceFileEditViewModel = koinViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(key) { viewModel.load(context.applicationContext, key) }

    DebugScaffold(
        title = viewModel.file?.relative ?: key,
        windowInsets = windowInsets,
        onBack = { navController.navigateUp() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = viewModel.content,
                onValueChange = { viewModel.edit(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                label = { Text(viewModel.file?.relative ?: "content") }
            )
            if (viewModel.message.isNotBlank()) {
                Text(
                    viewModel.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { viewModel.save(context.applicationContext, key) },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.file?.editable == true
                ) {
                    Text(stringResource(R.string.debug_service_file_save))
                }
            }
        }
    }
}
