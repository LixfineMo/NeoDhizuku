package com.neodhizuku.more.ui.page.settings.theme

import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material3.CardDefaults
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.BackgroundImageStore
import com.neodhizuku.more.ui.theme.ThemeConfig
import com.neodhizuku.more.ui.theme.ThemeMode
import com.neodhizuku.more.ui.theme.ThemeSettings
import com.neodhizuku.more.ui.component.NeoDropdownItem
import com.neodhizuku.more.ui.component.NeoDropdownMenu
import com.neodhizuku.more.ui.theme.UiEngine
import com.neodhizuku.more.ui.theme.UiEngineStore
import com.neodhizuku.more.ui.util.RecentsVisibility

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
    val uiEngine by UiEngineStore.engine.collectAsState()

    val monetSupported = ThemeConfig.isMonetSupported()
    // 平板药丸开关只在宽屏设备上出现，手机端不需要知道这个选项。
    val expanded = LocalConfiguration.current.screenWidthDp >= 600

    var modeExpanded by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val ok = BackgroundImageStore.import(context, uri)
            ThemeConfig.setBackgroundImageSet(context, ok)
            if (!ok) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.theme_background_import_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

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
            item("appearance_title") {
                Text(
                    stringResource(R.string.theme_appearance_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("appearance_card") {
                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                ThemeMode.entries.forEach { mode ->
                                    HorizontalDivider(
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

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

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

                        // 关闭动态取色（或系统不支持）时才给出内置取色板
                        val showPalette = !monetSupported || !settings.useMonet
                        if (showPalette) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            val scrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(scrollState)
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ThemePalettes.forEach { (colorHex, labelRes) ->
                                    ThemeColorChip(
                                        color = Color(colorHex),
                                        label = stringResource(labelRes),
                                        selected = colorHex == settings.customColor,
                                        onClick = {
                                            NeoLog.d(NeoLog.THEME) {
                                                "customColor -> #${Integer.toHexString(colorHex)} " +
                                                    "(prev=#${Integer.toHexString(settings.customColor)})"
                                            }
                                            ThemeConfig.setCustomColor(context, colorHex)
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        EngineEntry(
                            title = stringResource(R.string.ui_engine_title),
                            desc = stringResource(R.string.ui_engine_desc),
                            current = uiEngine,
                            onSelect = { UiEngineStore.set(context, it) }
                        )
                    }
                }
            }

            item("nav_title") {
                Text(
                    stringResource(R.string.theme_nav_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("nav_card") {
                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.theme_liquid_glass),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(R.string.theme_liquid_glass_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.useLiquidGlass,
                                onCheckedChange = { enabled ->
                                    NeoLog.d(NeoLog.THEME) {
                                        "liquidGlass -> $enabled (prev=${settings.useLiquidGlass})"
                                    }
                                    ThemeConfig.setUseLiquidGlass(context, enabled)
                                }
                            )
                        }

                        // 仅宽屏可见：默认关闭，关闭时侧栏贴边、不画选中胶囊。
                        if (expanded) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.theme_tablet_pill),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        stringResource(R.string.theme_tablet_pill_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.tabletShowPill,
                                    onCheckedChange = { enabled ->
                                        NeoLog.d(NeoLog.THEME) {
                                            "tabletShowPill -> $enabled (prev=${settings.tabletShowPill})"
                                        }
                                        ThemeConfig.setTabletShowPill(context, enabled)
                                    }
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.theme_hide_from_recents),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(R.string.theme_hide_from_recents_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.hideFromRecents,
                                onCheckedChange = { enabled ->
                                    ThemeConfig.setHideFromRecents(context, enabled)
                                    RecentsVisibility.apply(context, enabled)
                                }
                            )
                        }
                    }
                }
            }

            item("background_title") {
                Text(
                    stringResource(R.string.theme_background_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("background_card") {
                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { pickLauncher.launch("image/*") }) {
                                Text(stringResource(R.string.theme_background_pick))
                            }
                            if (settings.backgroundImageSet) {
                                TextButton(onClick = { showCrop = true }) {
                                    Text(stringResource(R.string.theme_background_adjust))
                                }
                            }
                            if (settings.backgroundImageSet) {
                                TextButton(onClick = {
                                    BackgroundImageStore.clear(context)
                                    ThemeConfig.setBackgroundImageSet(context, false)
                                }) {
                                    Text(stringResource(R.string.theme_background_clear))
                                }
                            }
                        }

                        if (settings.backgroundImageSet) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.theme_background_blur),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = settings.backgroundBlur,
                                    onValueChange = {
                                        ThemeConfig.setBackgroundBlur(context, it)
                                    },
                                    valueRange = 0f..ThemeConfig.MAX_BACKGROUND_BLUR,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    stringResource(R.string.theme_background_dim),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = settings.backgroundDim,
                                    onValueChange = {
                                        ThemeConfig.setBackgroundDim(context, it)
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    stringResource(R.string.theme_card_blur),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    stringResource(R.string.theme_card_blur_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = settings.cardBlur,
                                    onValueChange = {
                                        ThemeConfig.setCardBlur(context, it)
                                    },
                                    valueRange = 0f..ThemeConfig.MAX_CARD_BLUR,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showCrop) {
            BackgroundCropDialog(
                initialScale = settings.backgroundScale,
                initialOffsetX = settings.backgroundOffsetX,
                initialOffsetY = settings.backgroundOffsetY,
                onDismiss = { showCrop = false }
            )
        }
    }
}

/** 内置取色板：第一个是品牌紫。 */
private val ThemePalettes: List<Pair<Int, Int>> = listOf(
    0xFF6650A4.toInt() to R.string.theme_color_brand_purple,
    0xFFF27297.toInt() to R.string.theme_color_cherry_blossom,
    0xFF007AFF.toInt() to R.string.theme_color_default_blue,
    0xFF34C759.toInt() to R.string.theme_color_fresh_green,
    0xFFAF52DE.toInt() to R.string.theme_color_elegant_purple,
    0xFFFF9500.toInt() to R.string.theme_color_vibrant_orange,
    0xFF00BCD4.toInt() to R.string.theme_color_cyan,
    0xFF81C784.toInt() to R.string.theme_color_mint_green,
    0xFFF06292.toInt() to R.string.theme_color_pink,
    0xFFD81B60.toInt() to R.string.theme_color_deep_pink,
    0xFF64B5F6.toInt() to R.string.theme_color_ice_blue,
    0xFFE91E63.toInt() to R.string.theme_color_rose
)

@Composable
private fun ThemeColorChip(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(72.dp)
    ) {
        // 用 material3 的 Surface(onClick) 承载点击：Modifier.clickable 在部分
        // foundation 版本下解析不到，这里统一走 Surface。
        Surface(
            onClick = onClick,
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = color
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.TwoTone.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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

/** 「UI 引擎」：原版 / miuix，切换后立即生效。 */
@Composable
private fun EngineEntry(
    title: String,
    desc: String,
    current: UiEngine,
    onSelect: (UiEngine) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
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
                Text(
                    stringResource(
                        if (current == UiEngine.MIUIX) R.string.ui_engine_miuix
                        else R.string.ui_engine_original
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        NeoDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NeoDropdownItem(
                text = stringResource(R.string.ui_engine_original),
                selected = current == UiEngine.ORIGINAL,
                onClick = { onSelect(UiEngine.ORIGINAL); expanded = false }
            )
            NeoDropdownItem(
                text = stringResource(R.string.ui_engine_miuix),
                selected = current == UiEngine.MIUIX,
                onClick = { onSelect(UiEngine.MIUIX); expanded = false }
            )
        }
    }
}
