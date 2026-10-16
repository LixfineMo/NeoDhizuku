package com.neodhizuku.more.ui.page.settings.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowForward
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.server.ServiceStatus
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.page.settings.app_management.AppTab
import com.neodhizuku.more.ui.page.settings.app_management.AppManagementStartTab
import com.neodhizuku.more.ui.theme.exclude
import com.neodhizuku.more.ui.theme.plus

import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    windowInsets: WindowInsets,
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = koinViewModel()
) {
    val isOwner = DhizukuState.state.isOwner

    LaunchedEffect(Unit) {
        viewModel.collect()
    }

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        // 外层 NavHost 已应用 Scaffold 的 padding（含系统 inset 与底栏高度），
        // 这里再处理一次 Bottom 会导致列表底部多出一块空白。
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = {
                    Text(stringResource(R.string.settings))
                }
            )
        }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(it + contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.enable_dhizuku),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(R.string.enable_dhizuku_desc),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = viewModel.state.dhizukuEnabled,
                                onCheckedChange = viewModel::setDhizukuEnabled
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.whitelist_mode),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(R.string.whitelist_mode_desc),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = viewModel.state.whitelistMode,
                                onCheckedChange = viewModel::setWhitelistMode
                            )
                        }
                    }
                }
            }

            // 管理分区只在已激活时显示，未激活时没有可管理的内容
            if (isOwner) item("manage_title") {
                Text(
                    stringResource(R.string.settings_manage_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isOwner) item("manage_card") {
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ManageEntry(
                            title = stringResource(R.string.home_app_management_title),
                            desc = stringResource(
                                R.string.settings_manage_apps_desc,
                                ServiceStatus.authorizedCount
                            ),
                            onClick = {
                                AppManagementStartTab.tab = AppTab.Authorized
                                navController.navigate(SettingsRoute.AppManagement.route)
                            }
                        )
                        HorizontalDividerSafe()
                        ManageEntry(
                            title = stringResource(R.string.app_tab_blocked),
                            desc = stringResource(R.string.settings_manage_blocklist_desc),
                            onClick = {
                                navController.navigate(SettingsRoute.Blocklist.route)
                            }
                        )
                    }
                }
            }

            // 安全分区：转移开关默认关闭
            item("security_title") {
                Text(
                    stringResource(R.string.settings_security_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("security_card") {
                ElevatedCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_allow_owner_transfer),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(R.string.settings_allow_owner_transfer_desc),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = viewModel.state.allowOwnerTransfer,
                                onCheckedChange = viewModel::setAllowOwnerTransfer
                            )
                        }
                    }
                }
            }

            // 调用记录常驻：未激活也能查看运行日志
            item("logs_title") {
                Text(
                    stringResource(R.string.nav_call_log),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("logs_card") {
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ManageEntry(
                            title = stringResource(R.string.nav_call_log),
                            desc = stringResource(R.string.settings_manage_calllog_desc),
                            onClick = {
                                navController.navigate(SettingsRoute.CallLog.route)
                            }
                        )
                    }
                }
            }

            // 外观分区（主题页入口）
            item("appearance_title") {
                Text(
                    stringResource(R.string.settings_appearance_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("appearance_card") {
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ManageEntry(
                            title = stringResource(R.string.theme_title),
                            desc = stringResource(R.string.settings_theme_desc),
                            onClick = {
                                NeoLog.d(NeoLog.NAV) { "settings -> theme" }
                                navController.navigate(SettingsRoute.Theme.route)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalDividerSafe() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** 设置页里的「跳转型」条目：标题 + 描述 + 右侧箭头 */
@Composable
private fun ManageEntry(
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    // 不用 Modifier.clickable（该符号在部分 Compose foundation 版本下解析不到），
    // 改用 material3 的 Surface(onClick=...)，material3 在本模块已大量使用。
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
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