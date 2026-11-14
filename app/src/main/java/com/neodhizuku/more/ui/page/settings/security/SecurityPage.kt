package com.neodhizuku.more.ui.page.settings.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.twotone.CheckCircle
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.data.settings.repo.DenyMode
import com.neodhizuku.more.data.settings.repo.InsufficientMode
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.AppIconCache
import com.neodhizuku.more.ui.theme.exclude

import org.koin.androidx.compose.koinViewModel

/** 服务策略页：API 总开关、白名单、关闭服务后的处理方式、所有者转移。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityPage(
    windowInsets: WindowInsets,
    navController: NavController
) {
    val viewModel = koinViewModel<SecurityViewModel>()
    LaunchedEffect(Unit) { viewModel.collect() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
            )
        ) {
            item("service_title") {
                Text(
                    stringResource(R.string.security_service_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("service_card") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchEntry(
                            title = stringResource(R.string.enable_dhizuku),
                            desc = stringResource(R.string.enable_dhizuku_desc),
                            checked = viewModel.state.dhizukuEnabled,
                            onCheckedChange = viewModel::setDhizukuEnabled
                        )
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchEntry(
                            title = stringResource(R.string.whitelist_mode),
                            desc = stringResource(R.string.whitelist_mode_desc),
                            checked = viewModel.state.whitelistMode,
                            onCheckedChange = viewModel::setWhitelistMode
                        )
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchEntry(
                            title = stringResource(R.string.use_legacy_version),
                            desc = stringResource(R.string.use_legacy_version_desc),
                            checked = viewModel.state.useLegacyVersion,
                            onCheckedChange = viewModel::setUseLegacyVersion
                        )
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchEntry(
                            title = stringResource(R.string.strict_service_check),
                            desc = stringResource(R.string.strict_service_check_desc),
                            checked = viewModel.state.strictServiceCheck,
                            onCheckedChange = viewModel::setStrictServiceCheck
                        )
                    }
                }
            }

            item("whitelist_title") {
                Text(
                    stringResource(R.string.whitelist_manage_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("whitelist_card") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    EntryRow(
                        title = stringResource(R.string.whitelist_manage_title),
                        desc = stringResource(R.string.whitelist_manage_entry_desc),
                        onClick = { navController.navigate(SettingsRoute.Whitelist.route) }
                    )
                }
            }

            item("deny_title") {
                Text(
                    stringResource(R.string.security_deny_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("deny_card") {
                var modeExpanded by remember { mutableStateOf(false) }
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 折叠选择器：与主题页深浅色一致，
                        // 常态显示当前项，点击展开列出全部（含当前项）
                        DenyModeSelector(
                            current = viewModel.state.denyMode,
                            expanded = modeExpanded,
                            onToggle = { modeExpanded = !modeExpanded }
                        )

                        AnimatedVisibility(visible = modeExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                DenyMode.entries.forEach { mode ->
                                    androidx.compose.material3.HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    DenyModeItem(
                                        label = denyModeLabel(mode),
                                        desc = denyModeDesc(mode),
                                        selected = mode == viewModel.state.denyMode,
                                        onClick = {
                                            viewModel.setDenyMode(mode)
                                            modeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                    }
                }
            }

            item("transfer_title") {
                Text(
                    stringResource(R.string.security_transfer_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("transfer_card") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchEntry(
                            title = stringResource(R.string.settings_allow_owner_transfer),
                            desc = stringResource(R.string.settings_allow_owner_transfer_desc),
                            checked = viewModel.state.allowOwnerTransfer,
                            onCheckedChange = viewModel::setAllowOwnerTransfer
                        )
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.security_transfer_confirm_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.security_transfer_confirm_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item("full_access_title") {
                Text(
                    stringResource(R.string.full_access_badge),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("full_access_card") {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val apps = viewModel.state.fullAccessApps
                        apps.forEachIndexed { index, item ->
                            FullAccessAppRow(
                                item = item,
                                onCheckedChange = { enabled ->
                                    viewModel.setFullAccess(item, enabled)
                                }
                            )
                            if (index != apps.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullAccessAppRow(
    item: FullAccessAppItem,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!item.fullAccess) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                bitmap = AppIconCache.rememberImageBitmapState(item.applicationInfo).value,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = item.fullAccess,
                onCheckedChange = onCheckedChange
            )
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
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
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
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 开关行：标题 + 描述 + 右侧开关 */
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

/** 折叠选择器头部：常态显示当前项，右侧是展开箭头，点击整行展开/收起。 */
@Composable
private fun DenyModeSelector(
    current: DenyMode,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.security_deny_mode_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.security_deny_mode_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    denyModeLabel(current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DenyModeItem(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // 不用 Modifier.clickable（该符号在部分 Compose foundation 版本下解析不到），
    // 改用 material3 的 Surface(onClick=...)。
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.TwoTone.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                RadioButton(selected = false, onClick = onClick)
            }
        }
    }
}

@Composable
private fun denyModeLabel(mode: DenyMode): String = stringResource(
    when (mode) {
        DenyMode.DENY_DANGEROUS_FAIL -> R.string.deny_mode_dangerous_fail
        DenyMode.DENY_DANGEROUS_SUCCESS -> R.string.deny_mode_dangerous_success
        DenyMode.DENY_ALL_FAIL -> R.string.deny_mode_all_fail
        DenyMode.DENY_ALL_SUCCESS -> R.string.deny_mode_all_success
        DenyMode.NO_ACTION -> R.string.deny_mode_no_action
    }
)

@Composable
private fun denyModeDesc(mode: DenyMode): String = stringResource(
    when (mode) {
        DenyMode.DENY_DANGEROUS_FAIL -> R.string.deny_mode_dangerous_fail_desc
        DenyMode.DENY_DANGEROUS_SUCCESS -> R.string.deny_mode_dangerous_success_desc
        DenyMode.DENY_ALL_FAIL -> R.string.deny_mode_all_fail_desc
        DenyMode.DENY_ALL_SUCCESS -> R.string.deny_mode_all_success_desc
        DenyMode.NO_ACTION -> R.string.deny_mode_no_action_desc
    }
)

/** 能力不足处理的选择器头部，样式与 [DenyModeSelector] 一致。暂不在界面展示。 */
@Suppress("unused")
@Composable
private fun InsufficientSelector(
    current: InsufficientMode,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.security_insufficient_mode),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.security_insufficient_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    insufficientLabel(current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = null
            )
        }
    }
}

@Suppress("unused")
@Composable
private fun insufficientLabel(mode: InsufficientMode): String = stringResource(
    when (mode) {
        InsufficientMode.RETURN_FAIL -> R.string.insufficient_mode_fail
        InsufficientMode.RETURN_SUCCESS -> R.string.insufficient_mode_success
        InsufficientMode.NO_ACTION -> R.string.insufficient_mode_no_action
    }
)

@Suppress("unused")
@Composable
private fun insufficientDesc(mode: InsufficientMode): String = stringResource(
    when (mode) {
        InsufficientMode.RETURN_FAIL -> R.string.insufficient_mode_fail_desc
        InsufficientMode.RETURN_SUCCESS -> R.string.insufficient_mode_success_desc
        InsufficientMode.NO_ACTION -> R.string.insufficient_mode_no_action_desc
    }
)
