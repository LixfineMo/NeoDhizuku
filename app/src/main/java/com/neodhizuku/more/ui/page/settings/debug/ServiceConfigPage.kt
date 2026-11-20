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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.neodhizuku.more.ui.component.NeoCard
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

/**
 * ServiceConfigPage
 * (windowInsets: WindowInsets, navController: NavController, viewModel: ServiceConfigViewModel = koinViewModel())
 * 作用：「服务配置修改」：私有区每个配置文件一张卡片，按目录分组。
 * 备注：Compose 组件
 */
@Composable
fun ServiceConfigPage(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: ServiceConfigViewModel = koinViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh(context.applicationContext) }

    val query = viewModel.query.trim()
    val filtered = viewModel.files.filter {
        query.isBlank() || it.relative.contains(query, ignoreCase = true) ||
            it.reason.contains(query, ignoreCase = true)
    }
    val grouped = filtered.groupBy { it.relative.substringBefore('/') }.toSortedMap()
    val editableCount = viewModel.files.count { it.editable }
    val totalBytes = viewModel.files.sumOf { it.sizeBytes }
    val rootCount = viewModel.files.map { it.relative.substringBefore('/') }.distinct().size

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
            item("files_stats") {
                Text(
                    stringResource(
                        R.string.debug_files_stats,
                        viewModel.files.size,
                        editableCount,
                        formatSize(totalBytes),
                        rootCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item("files_search") {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.updateQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.debug_search)) }
                )
            }
            if (viewModel.files.isEmpty()) {
                item("files_empty") {
                    Text(stringResource(R.string.debug_service_files_empty))
                }
            } else if (grouped.isEmpty()) {
                item("files_empty_filtered") {
                    Text(
                        stringResource(R.string.debug_files_empty_filtered),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            grouped.forEach { (root, files) ->
                item("root_$root") {
                    Text(
                        "$root (${files.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(files, key = { it.key }) { file ->
                    FileCard(file) {
                        navController.navigate(SettingsRoute.DebugServiceFileEdit.route(file.key))
                    }
                }
            }
        }
    }
}

/**
 * formatSize
 * (bytes: Long)
 * 作用：格式化 Size
 * 备注：仅内部使用
 */
private fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "${bytes}B"
    bytes < 1024L * 1024L -> "%.1fKB".format(bytes / 1024f)
    else -> "%.1fMB".format(bytes / 1024f / 1024f)
}

/**
 * FileCard
 * (file: PrivateFile, onClick: () -> Unit)
 * 作用：执行 FileCard
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun FileCard(file: PrivateFile, onClick: () -> Unit) {
    val tint = if (file.editable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    NeoCard(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
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
                if (!file.editable) {
                    Text(
                        stringResource(R.string.debug_files_readonly),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * ServiceFileEditPage
 * (windowInsets: WindowInsets, navController: NavController, key: String, viewModel: ServiceFileEditViewModel = koinViewModel())
 * 作用：单文件编辑：输入框尽量占满整屏，底部一个「确认修改」。 写入走临时文件改名，改到 api_policy.json / whitelist.json 时内存镜像会立刻重载。 只读文件也能进来预览，只是保存按钮禁用。
 * 备注：Compose 组件
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

    val editable = viewModel.file?.editable == true
    val isJson = viewModel.file?.relative?.endsWith(".json", ignoreCase = true) == true
    val lines = viewModel.content.count { it == '\n' } + if (viewModel.content.isEmpty()) 0 else 1

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.debug_file_stats, viewModel.content.length, lines),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                        ?.setPrimaryClip(
                            ClipData.newPlainText("NeoDhizuku", viewModel.content)
                        )
                }) {
                    Text(stringResource(R.string.debug_file_copy))
                }
                if (isJson && editable) {
                    TextButton(onClick = { viewModel.format() }) {
                        Text(stringResource(R.string.debug_file_format))
                    }
                }
            }
            if (!editable) {
                Text(
                    stringResource(R.string.debug_file_readonly_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            OutlinedTextField(
                value = viewModel.content,
                onValueChange = { viewModel.edit(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                readOnly = !editable,
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
                    enabled = editable
                ) {
                    Text(stringResource(R.string.debug_service_file_save))
                }
            }
        }
    }
}
