package com.neodhizuku.more.ui.page.settings.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowForward
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.exclude

import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalPage(
    windowInsets: WindowInsets,
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ExperimentalViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.collect()
    }

    // 开启任何一项之前都要确认
    viewModel.pendingEnable?.let { feature ->
        EnableWarningDialog(
            featureTitle = featureTitle(feature),
            onConfirm = { viewModel.confirmPending() },
            onDismiss = { viewModel.dismissPending() }
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
                    Text(stringResource(R.string.experimental_title))
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
            item("experimental_note") {
                NoteCard()
            }

            item("experimental_card") {
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchEntry(
                            title = stringResource(R.string.experimental_quick_activate_fix),
                            desc = stringResource(R.string.experimental_quick_activate_fix_desc),
                            checked = viewModel.state.quickActivateFix,
                            onCheckedChange = { enabled ->
                                viewModel.requestToggle(
                                    ExperimentalFeature.QuickActivateFix, enabled
                                )
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        EntryRow(
                            title = stringResource(R.string.delegation_title),
                            desc = stringResource(R.string.delegation_entry_dsp),
                            onClick = { navController.navigate(SettingsRoute.Delegation.route) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        SwitchEntry(
                            title = stringResource(R.string.experimental_delegation_cleanup),
                            desc = stringResource(R.string.experimental_delegation_cleanup_desc),
                            checked = viewModel.state.delegationCleanup,
                            onCheckedChange = { enabled ->
                                viewModel.requestToggle(
                                    ExperimentalFeature.DelegationCleanup, enabled
                                )
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        SwitchEntry(
                            title = stringResource(R.string.experimental_verbose_log),
                            desc = stringResource(R.string.experimental_verbose_log_desc),
                            checked = viewModel.state.verboseLog,
                            onCheckedChange = { enabled ->
                                viewModel.requestToggle(
                                    ExperimentalFeature.VerboseLog, enabled
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun featureTitle(feature: ExperimentalFeature): String = stringResource(
    when (feature) {
        ExperimentalFeature.QuickActivateFix -> R.string.experimental_quick_activate_fix
        ExperimentalFeature.DelegationCleanup -> R.string.experimental_delegation_cleanup
        ExperimentalFeature.VerboseLog -> R.string.experimental_verbose_log
    }
)

/** 顶部说明：这些功能默认关闭，且未经充分验证。 */
@Composable
private fun NoteCard() {
    ElevatedCard {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.TwoTone.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.experimental_note_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.experimental_note_dsp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 跳转子页面的入口行 */
@Composable
private fun EntryRow(
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.TwoTone.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一个带说明文字的开关行 */
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 开启前的确认弹窗。 */
@Composable
private fun EnableWarningDialog(
    featureTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.TwoTone.Warning, contentDescription = null)
        },
        title = {
            Text(featureTitle)
        },
        text = {
            Text(stringResource(R.string.experimental_enable_warning))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.experimental_enable_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
