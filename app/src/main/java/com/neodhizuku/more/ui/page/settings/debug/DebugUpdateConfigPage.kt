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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.UpdateSource
import com.neodhizuku.more.ui.component.NeoCard
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

/**
 * DebugUpdateConfigPage
 * (windowInsets: WindowInsets, navController: NavController, viewModel: DebugViewModel = koinViewModel())
 * 作用：「更新配置」：来源、官方地址、自定义清单地址、超时、允许不安全链接、 最新 Build 拉取、当前生效配置。地址类内容都能点行复制。
 * 备注：Compose 组件；使用了实验性 API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugUpdateConfigPage(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: DebugViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) { viewModel.collect() }

    DebugScaffold(
        title = stringResource(R.string.debug_update_config_title),
        windowInsets = windowInsets,
        onBack = { navController.navigateUp() }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp).combine(padding),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("debug_source") {
                NeoCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            stringResource(R.string.update_settings_source),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sources = listOf(
                                UpdateSource.Official,
                                UpdateSource.GitHub,
                                UpdateSource.Custom
                            )
                            sources.forEach { source ->
                                val selected = viewModel.state.source == source
                                if (selected) {
                                    Button(
                                        onClick = { viewModel.setSource(source) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(sourceLabel(source)) }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.setSource(source) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(sourceLabel(source)) }
                                }
                            }
                        }
                        SwitchEntry(
                            title = stringResource(R.string.update_settings_insecure),
                            desc = stringResource(R.string.update_settings_insecure_desc),
                            checked = viewModel.state.allowInsecure,
                            onCheckedChange = { viewModel.setAllowInsecure(it) }
                        )
                    }
                }
            }

            item("debug_urls") {
                NeoCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Field(
                            label = stringResource(R.string.debug_official_base),
                            value = viewModel.draftOfficialBase,
                            onValueChange = { viewModel.editOfficialBase(it) }
                        )
                        Field(
                            label = stringResource(R.string.update_settings_custom_url),
                            value = viewModel.draftCustomUrl,
                            onValueChange = { viewModel.editCustomUrl(it) }
                        )
                        Field(
                            label = stringResource(R.string.debug_timeout),
                            value = viewModel.draftTimeout,
                            onValueChange = { viewModel.editTimeout(it) }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.save() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.debug_save))
                            }
                            TextButton(
                                onClick = { viewModel.reset() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.debug_reset))
                            }
                        }
                        if (viewModel.message.isNotBlank()) {
                            Text(
                                viewModel.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item("debug_build") {
                NeoCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            stringResource(R.string.debug_build_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.debug_build_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.fetchLatestBuild() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.debug_build_fetch))
                        }
                        Text(
                            text = buildResultText(viewModel.buildState),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val found = viewModel.buildState as? BuildState.Found
                        if (found != null) {
                            CopyableLine("apkUrl", found.url.ifBlank { "-" })
                        }
                    }
                }
            }

            item("debug_current") {
                NeoCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.debug_current_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val cfg = viewModel.state.toConfig()
                        CopyableLine("source", cfg.source.key)
                        CopyableLine("versionJsonUrl", cfg.versionJsonUrl)
                        CopyableLine("officialBase", cfg.officialBase)
                        CopyableLine("officialHost", cfg.officialHost.ifBlank { "-" })
                        CopyableLine("customUrl", cfg.customUrl.ifBlank { "-" })
                        CopyableLine("timeoutMs", cfg.timeoutMs.toString())
                        CopyableLine("allowInsecure", cfg.allowInsecure.toString())
                    }
                }
            }
        }
    }
}

/**
 * CopyableLine
 * (label: String, value: String)
 * 作用：点行即把值放进剪贴板，长地址不用手动选中。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun CopyableLine(label: String, value: String) {
    val context = LocalContext.current
    DebugInfoRow(label = label, value = value) { text ->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("NeoDhizuku", text))
    }
}

/**
 * sourceLabel
 * (source: UpdateSource)
 * 作用：执行 sourceLabel
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun sourceLabel(source: UpdateSource): String = stringResource(
    when (source) {
        UpdateSource.Official -> R.string.update_settings_source_official
        UpdateSource.GitHub -> R.string.update_settings_source_github
        UpdateSource.Custom -> R.string.update_settings_source_custom
    }
)

/**
 * buildResultText
 * (state: BuildState)
 * 作用：构建 ResultText
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun buildResultText(state: BuildState): String = when (state) {
    BuildState.Idle -> stringResource(R.string.debug_build_idle)
    BuildState.Loading -> stringResource(R.string.update_checking)
    is BuildState.Found -> stringResource(
        R.string.debug_build_found,
        state.date,
        state.version.ifBlank { "-" },
        state.total
    )

    is BuildState.Failed -> stringResource(
        R.string.debug_build_failed,
        state.reason ?: "-"
    )
}

/**
 * Field
 * (label: String, value: String, onValueChange: (String) -> Unit)
 * 作用：执行 Field
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) }
    )
}

/**
 * SwitchEntry
 * (title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit)
 * 作用：执行 SwitchEntry
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun SwitchEntry(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
