package com.neodhizuku.more.ui.page.settings.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowForward
import androidx.compose.material.icons.twotone.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.LocaleHelper
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.server.ServiceStatus
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.exclude
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    windowInsets: WindowInsets,
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** 底部四页之间用翻页切换，其余路由交给 NavController */
    onSelectTab: (String) -> Unit = { route ->
        runCatching { navController.navigate(route) }
    },
    viewModel: SettingsViewModel = koinViewModel()
) {
    // 设备管理员也算已激活，管理分区（软件管理 / 黑名单 / 授权）同样可用
    val isOwner = DhizukuState.state.isActive

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
            contentPadding = PaddingValues(16.dp).combine(contentPadding),
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                                onSelectTab(SettingsRoute.AppManagement.route)
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

            // 安全：入口形式，具体项在安全页内
            item("security_title") {
                Text(
                    stringResource(R.string.settings_security_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("security_card") {
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ManageEntry(
                            title = stringResource(R.string.security_title),
                            desc = stringResource(R.string.settings_security_desc),
                            onClick = {
                                navController.navigate(SettingsRoute.Security.route)
                            }
                        )
                        HorizontalDividerSafe()
                        // 单项系统接口：与共享服务同级，直接放在服务板块内
                        ManageEntry(
                            title = stringResource(R.string.api_policy_entry_title),
                            desc = stringResource(R.string.api_policy_entry_desc),
                            onClick = {
                                NeoLog.d(NeoLog.NAV) { "settings -> api_policy" }
                                navController.navigate(SettingsRoute.ApiPolicy.route)
                            }
                        )
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
                                onSelectTab(SettingsRoute.CallLog.route)
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

            // 灰度功能分区：实验性开关，放在软件上方
            item("gray_title") {
                Text(
                    stringResource(R.string.settings_gray_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("gray_card") {
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ManageEntry(
                            title = stringResource(R.string.experimental_title),
                            desc = stringResource(R.string.settings_experimental_desc),
                            onClick = {
                                NeoLog.d(NeoLog.NAV) { "settings -> experimental" }
                                navController.navigate(SettingsRoute.Experimental.route)
                            }
                        )
                    }
                }
            }

            // 软件分区：语言与关于，未激活时同样可用
            item("software_title") {
                Text(
                    stringResource(R.string.settings_software_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("software_card") {
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LanguageEntry()
                        HorizontalDividerSafe()
                        ManageEntry(
                            title = stringResource(R.string.update_settings_title),
                            desc = stringResource(R.string.update_settings_entry_dsp),
                            onClick = {
                                NeoLog.d(NeoLog.NAV) { "settings -> update_settings" }
                                navController.navigate(SettingsRoute.UpdateSettings.route)
                            }
                        )
                        HorizontalDividerSafe()
                        ManageEntry(
                            title = stringResource(R.string.about_title),
                            desc = stringResource(R.string.settings_about_desc),
                            onClick = {
                                NeoLog.d(NeoLog.NAV) { "settings -> about" }
                                navController.navigate(SettingsRoute.About.route)
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

/**
 * 语言条目：右侧显示当前语言，点击弹出悬浮列表。
 *
 * 选中后写入 SharedPreferences 并重建 Activity —— 语言必须在
 * attachBaseContext 阶段套上，重建是让它立即生效的唯一方式。
 */
@Composable
private fun LanguageEntry() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val currentTag = remember { LocaleHelper.current(context) }
    var selectedTag by remember { mutableStateOf(currentTag) }

    val displayName = LocaleHelper.languages
        .firstOrNull { it.tag == selectedTag }
        ?.displayName
        ?: stringResource(R.string.settings_language_system)

    Surface(
        onClick = { expanded = true },
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
                Text(
                    stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.settings_language_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.TwoTone.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    LocaleHelper.languages.forEach { language ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(language.displayName)
                                    // 翻译不完整的语言给出提示，避免用户选中后
                                    // 发现大片英文却不知道原因
                                    if (!language.complete) {
                                        Text(
                                            stringResource(R.string.settings_language_partial),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expanded = false
                                if (language.tag == selectedTag) return@DropdownMenuItem
                                selectedTag = language.tag
                                LocaleHelper.save(context, language.tag)
                                // 重建才能让新语言立即生效
                                LocaleHelper.findActivity(context)?.recreate()
                            }
                        )
                    }
                }
            }
        }
    }
}
