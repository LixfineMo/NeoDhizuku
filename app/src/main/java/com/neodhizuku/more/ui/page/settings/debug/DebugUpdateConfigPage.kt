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
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.UpdateSource
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

/**
 * 「更新配置」：原来调试页的全部内容。
 *
 * 官方地址、自定义清单地址、超时、允许不安全链接、最新 Build 拉取、
 * 当前生效地址，都留在这里，行为与旧版完全一致。
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
            item("debug_insecure") {
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchEntry(
                            title = stringResource(R.string.update_settings_insecure),
                            desc = stringResource(R.string.update_settings_insecure_desc),
                            checked = viewModel.state.allowInsecure,
                            onCheckedChange = { viewModel.setAllowInsecure(it) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Column(modifier = Modifier.padding(16.dp)) {
                            InfoLine(
                                stringResource(R.string.update_settings_source),
                                sourceLabel(viewModel.state.source)
                            )
                        }
                    }
                }
            }

            item("debug_urls") {
                ElevatedCard {
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
                    }
                }
            }

            item("debug_build") {
                ElevatedCard {
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
                    }
                }
            }

            item("debug_current") {
                ElevatedCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            stringResource(R.string.debug_current_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        InfoLine(
                            stringResource(R.string.debug_current_version_json),
                            viewModel.state.toConfig().versionJsonUrl
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sourceLabel(source: UpdateSource): String = stringResource(
    when (source) {
        UpdateSource.Official -> R.string.update_settings_source_official
        UpdateSource.GitHub -> R.string.update_settings_source_github
        UpdateSource.Custom -> R.string.update_settings_source_custom
    }
)

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

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchEntry(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
