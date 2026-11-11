package com.neodhizuku.more.ui.page.settings.updatesettings

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.AlertDialog
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.UpdateSource
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.exclude

import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettingsPage(
    windowInsets: WindowInsets,
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: UpdateSettingsViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.collect()
    }

    val state by viewModel.state.collectAsState()

    if (viewModel.showSourceDialog) {
        SourceDialog(
            current = state.source,
            onPick = { viewModel.setSource(it) },
            onDismiss = { viewModel.dismissSourceDialog() }
        )
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
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = {
                    Text(stringResource(R.string.update_settings_title))
                }
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("source_card") {
                NeoCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        EntryRow(
                            title = stringResource(R.string.update_settings_source),
                            desc = sourceLabel(state.source),
                            onClick = { viewModel.openSourceDialog() }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        SwitchEntry(
                            title = stringResource(R.string.update_settings_startup_check),
                            desc = stringResource(R.string.update_settings_startup_check_desc),
                            checked = state.startupCheck,
                            onCheckedChange = { viewModel.setStartupUpdateCheck(it) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        SwitchEntry(
                            title = stringResource(R.string.update_settings_insecure),
                            desc = stringResource(R.string.update_settings_insecure_desc),
                            checked = state.allowInsecure,
                            onCheckedChange = { viewModel.setAllowInsecure(it) }
                        )
                    }
                }
            }

            if (state.source == UpdateSource.Custom) {
                item("custom_card") {
                    NeoCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                stringResource(R.string.update_settings_custom_url),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = state.customUrl,
                                onValueChange = { viewModel.setCustomUrl(it) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(stringResource(R.string.update_settings_custom_url_hint))
                                }
                            )
                            Text(
                                stringResource(R.string.update_settings_custom_url_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item("official_card") {
                NeoCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        EntryRow(
                            title = stringResource(R.string.update_settings_official_base),
                            desc = if (state.probing) {
                                stringResource(R.string.update_checking)
                            } else {
                                state.officialBase
                            },
                            onClick = { viewModel.reprobeOfficial() }
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
private fun SourceDialog(
    current: UpdateSource,
    onPick: (UpdateSource) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_settings_source)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (source in UpdateSource.visible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = source == current,
                                onClick = { onPick(source) }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = source == current,
                            onClick = null
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = stringResource(
                                    when (source) {
                                        UpdateSource.Official ->
                                            R.string.update_settings_source_official

                                        UpdateSource.GitHub ->
                                            R.string.update_settings_source_github

                                        UpdateSource.Custom ->
                                            R.string.update_settings_source_custom
                                    }
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(
                                    when (source) {
                                        UpdateSource.Official ->
                                            R.string.update_settings_source_official_desc

                                        UpdateSource.GitHub ->
                                            R.string.update_settings_source_github_desc

                                        UpdateSource.Custom ->
                                            R.string.update_settings_source_custom_desc
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EntryRow(
    title: String,
    desc: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
