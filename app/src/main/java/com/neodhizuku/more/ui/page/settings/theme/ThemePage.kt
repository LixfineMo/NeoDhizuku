package com.neodhizuku.more.ui.page.settings.theme

import android.content.Context
import android.os.Build

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.neodhizuku.more.ui.theme.ThemeConfig
import com.neodhizuku.more.ui.theme.ThemeMode
import com.neodhizuku.more.ui.theme.ThemeSettings

/** 主题设置页：大标题分组 + 卡片，含深色模式四档与动态取色开关。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePage(
    context: Context,
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val settings by ThemeConfig.getThemeFlow(context)
        .collectAsState(initial = ThemeSettings())

    val monetSupported = ThemeConfig.isMonetSupported()

    var modeExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        NeoLog.d(NeoLog.NAV) { "ThemePage: navigateUp" }
                        navController.navigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = {
                    Text(stringResource(R.string.theme_title))
                }
            )
        }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("mode_title") {
                Text(
                    stringResource(R.string.theme_mode_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("mode_card") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 折叠选择器：未展开时只显示当前项，点击后展开列出全部
                        ThemeModeSelector(
                            current = settings.themeMode,
                            expanded = modeExpanded,
                            onToggle = {
                                modeExpanded = !modeExpanded
                                NeoLog.v(NeoLog.THEME) { "mode expanded -> $modeExpanded" }
                            }
                        )

                        AnimatedVisibility(visible = modeExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 必须列出全部模式：若用 filter 排除当前项，
                                // 用户点选后该项变成当前值，下次展开就找不到了。
                                ThemeMode.entries
                                    .forEach { mode ->
                                        androidx.compose.material3.HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        ThemeModeItem(
                                            label = stringResource(labelResOf(mode)),
                                            desc = stringResource(descResOf(mode)),
                                            selected = mode == settings.themeMode,
                                            onClick = {
                                                NeoLog.d(NeoLog.THEME) {
                                                    "mode selected: ${mode.name} " +
                                                        "(prev=${settings.themeMode.name})"
                                                }
                                                ThemeConfig.setThemeMode(context, mode)
                                                modeExpanded = false
                                            }
                                        )
                                    }
                            }
                        }
                    }
                }
            }

            item("color_title") {
                Text(
                    stringResource(R.string.theme_color_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("color_card") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (monetSupported) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.theme_monet),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        stringResource(R.string.theme_monet_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.useMonet,
                                    onCheckedChange = { enabled ->
                                        NeoLog.d(NeoLog.THEME) {
                                            "monet -> $enabled (prev=${settings.useMonet})"
                                        }
                                        ThemeConfig.setUseMonet(context, enabled)
                                    }
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(
                                        R.string.theme_monet_unsupported,
                                        Build.VERSION.SDK_INT
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun labelResOf(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
    ThemeMode.AMOLED -> R.string.theme_mode_amoled
}

private fun descResOf(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system_desc
    ThemeMode.LIGHT -> R.string.theme_mode_light_desc
    ThemeMode.DARK -> R.string.theme_mode_dark_desc
    ThemeMode.AMOLED -> R.string.theme_mode_amoled_desc
}

/**
 * 折叠选择器头部：常态只显示当前选中的模式，右侧是展开箭头。
 * 点击整行切换展开/收起。
 */
@Composable
private fun ThemeModeSelector(
    current: ThemeMode,
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
                    stringResource(labelResOf(current)),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(descResOf(current)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ThemeModeItem(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // 不用 Modifier.clickable（该符号在部分 Compose foundation 版本下解析不到），
    // 改用 material3 的 Surface(onClick=...)。
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
                RadioButton(selected = selected, onClick = onClick)
            }
        }
    }
}
