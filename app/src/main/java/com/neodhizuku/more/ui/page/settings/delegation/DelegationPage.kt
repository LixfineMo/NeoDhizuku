package com.neodhizuku.more.ui.page.settings.delegation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.twotone.Warning
import com.neodhizuku.more.ui.component.NeoAlertDialog as AlertDialog
import androidx.compose.material3.Checkbox
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

import android.content.Context

import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.DelegationManager
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.ui.theme.exclude

import org.koin.androidx.compose.koinViewModel

/**
 * DelegationPage
 * (windowInsets: WindowInsets, navController: NavController, contentPadding: PaddingValues = PaddingValues(0.dp), viewModel: DelegationViewModel = koinViewModel())
 * 作用：执行 DelegationPage
 * 备注：Compose 组件；使用了实验性 API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DelegationPage(
    windowInsets: WindowInsets,
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: DelegationViewModel = koinViewModel()
) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val savedText = stringResource(R.string.delegation_saved)
    val clearedText = stringResource(R.string.delegation_cleared)
    val failedText = stringResource(R.string.delegation_failed)

    LaunchedEffect(viewModel.state.message) {
        val message = viewModel.state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(
            when (message) {
                DelegationMessage.Saved -> savedText
                DelegationMessage.Cleared -> clearedText
                DelegationMessage.Failed -> failedText
            }
        )
        viewModel.consumeMessage()
    }

    viewModel.state.editing?.let { entry ->
        ScopeDialog(
            entry = entry,
            onDismiss = { viewModel.edit(null) },
            onSave = { scopes -> viewModel.save(entry.pkg, scopes) },
            onClear = { viewModel.clear(entry) }
        )
    }

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
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
                    Text(stringResource(R.string.delegation_title))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("delegation_note") {
                NoteCard()
            }

            if (DhizukuState.state.isOwner) {
                item("delegation_search") {
                    SearchField(
                        value = viewModel.state.query,
                        onValueChange = { viewModel.setQuery(it) }
                    )
                }
            }

            if (!DhizukuState.state.isOwner) {
                item("delegation_owner_required") {
                    OwnerRequiredCard()
                }
            } else if (viewModel.state.loading) {
                item("delegation_loading") {
                    Text(
                        text = stringResource(R.string.delegation_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item("delegation_granted_title") {
                    SectionTitle(stringResource(R.string.delegation_section_granted))
                }

                if (viewModel.visibleGranted.isEmpty()) {
                    item("delegation_granted_empty") {
                        EmptyCard(stringResource(R.string.delegation_granted_empty))
                    }
                } else {
                    item("delegation_clear_all") {
                        ClearAllRow { viewModel.clearAll() }
                    }

                    items(viewModel.visibleGranted, key = { "granted_" + it.pkg }) { entry ->
                        DelegationRow(
                            entry = entry,
                            onClick = { viewModel.edit(entry) }
                        )
                    }
                }

                item("delegation_others_title") {
                    SectionTitle(stringResource(R.string.delegation_section_others))
                }

                if (viewModel.visibleOthers.isEmpty()) {
                    item("delegation_others_empty") {
                        EmptyCard(stringResource(R.string.delegation_others_empty))
                    }
                } else {
                    items(viewModel.visibleOthers, key = { "other_" + it.pkg }) { entry ->
                        DelegationRow(
                            entry = entry,
                            onClick = { viewModel.edit(entry) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * SectionTitle
 * (text: String)
 * 作用：执行 SectionTitle
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * SearchField
 * (value: String, onValueChange: (String) -> Unit)
 * 作用：关键字过滤：应用名或包名匹配即可。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                stringResource(R.string.delegation_search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel)
                    )
                }
            }
        }
    )
}

/**
 * DelegationRow
 * (entry: DelegationEntry, onClick: () -> Unit)
 * 作用：执行 DelegationRow
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun DelegationRow(
    entry: DelegationEntry,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    NeoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = entry.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.scopes.isEmpty()) {
                Text(
                    text = stringResource(R.string.delegation_not_delegated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = entry.scopes.joinToString { scope ->
                        scopeLabel(context, scope)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * scopeLabel
 * (context: Context, scope: String)
 * 作用：作用域 id 到界面文案。取资源走 Context，便于在非组合上下文（如 joinToString）里使用。
 * 备注：仅内部使用
 */
private fun scopeLabel(context: Context, scope: String): String = context.getString(
    when (scope) {
        android.app.admin.DevicePolicyManager.DELEGATION_APP_RESTRICTIONS ->
            R.string.delegation_scope_app_restrictions

        android.app.admin.DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL ->
            R.string.delegation_scope_block_uninstall

        android.app.admin.DevicePolicyManager.DELEGATION_PERMISSION_GRANT ->
            R.string.delegation_scope_permission_granting

        android.app.admin.DevicePolicyManager.DELEGATION_PACKAGE_ACCESS ->
            R.string.delegation_scope_package_access

        android.app.admin.DevicePolicyManager.DELEGATION_ENABLE_SYSTEM_APP ->
            R.string.delegation_scope_enable_system_apps

        android.app.admin.DevicePolicyManager.DELEGATION_CERT_INSTALL ->
            R.string.delegation_scope_cert_install

        android.app.admin.DevicePolicyManager.DELEGATION_NETWORK_LOGGING ->
            R.string.delegation_scope_network_logging

        android.app.admin.DevicePolicyManager.DELEGATION_SECURITY_LOGGING ->
            R.string.delegation_scope_security_logging

        android.app.admin.DevicePolicyManager.DELEGATION_CERT_SELECTION ->
            R.string.delegation_scope_cert_selection

        android.app.admin.DevicePolicyManager.DELEGATION_INSTALL_EXISTING_PACKAGE ->
            R.string.delegation_scope_install_existing_package

        else -> R.string.delegation_scope_unknown
    }
)

/**
 * scopeDesc
 * (scope: String)
 * 作用：执行 scopeDesc
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun scopeDesc(scope: String): String = stringResource(
    when (scope) {
        android.app.admin.DevicePolicyManager.DELEGATION_APP_RESTRICTIONS ->
            R.string.delegation_scope_app_restrictions_desc

        android.app.admin.DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL ->
            R.string.delegation_scope_block_uninstall_desc

        android.app.admin.DevicePolicyManager.DELEGATION_PERMISSION_GRANT ->
            R.string.delegation_scope_permission_granting_desc

        android.app.admin.DevicePolicyManager.DELEGATION_PACKAGE_ACCESS ->
            R.string.delegation_scope_package_access_desc

        android.app.admin.DevicePolicyManager.DELEGATION_ENABLE_SYSTEM_APP ->
            R.string.delegation_scope_enable_system_apps_desc

        else -> R.string.delegation_scope_unknown_desc
    }
)

/**
 * NoteCard
 * ()
 * 作用：执行 NoteCard
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun NoteCard() {
    NeoCard {
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
                    text = stringResource(R.string.delegation_note_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.delegation_note_dsp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * OwnerRequiredCard
 * ()
 * 作用：执行 OwnerRequiredCard
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun OwnerRequiredCard() {
    NeoCard {
        Text(
            text = stringResource(R.string.delegation_owner_required),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * EmptyCard
 * (text: String)
 * 作用：执行 EmptyCard
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun EmptyCard(text: String) {
    NeoCard {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * ClearAllRow
 * (onClick: () -> Unit)
 * 作用：执行 ClearAllRow
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun ClearAllRow(onClick: () -> Unit) {
    NeoCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.delegation_clear_all),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * ScopeDialog
 * (entry: DelegationEntry, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit, onClear: () -> Unit)
 * 作用：编辑某个应用被委派的范围；尚未持有委派时用于主动派发。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun ScopeDialog(
    entry: DelegationEntry,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onClear: () -> Unit
) {
    val supported = remember { DelegationManager.supportedScopes().map { it.id } }
    val context = LocalContext.current
    val hasExisting = entry.scopes.isNotEmpty()
    var selected by remember(entry.pkg) {
        mutableStateOf(entry.scopes.filter { it in supported }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.label) },
        text = {
            // 作用域有 10+ 项，不限制高度会把确认 / 取消按钮顶出屏幕
            // （miuix 弹窗是普通 Column，没有 Material3 那层高度约束）。
            // 这里限高 + 自身滚动，保证按钮永远留在弹窗底部可见范围内。
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (!hasExisting) {
                    Text(
                        text = stringResource(R.string.delegation_grant_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                supported.forEach { scope ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = scope in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + scope else selected - scope
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = scopeLabel(context, scope),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = scopeDesc(scope),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = entry.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text(stringResource(R.string.delegation_save))
            }
        },
        dismissButton = {
            Row {
                if (hasExisting) {
                    TextButton(onClick = onClear) {
                        Text(
                            stringResource(R.string.delegation_clear),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
