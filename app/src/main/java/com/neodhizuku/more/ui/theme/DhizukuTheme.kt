package com.neodhizuku.more.ui.theme

import android.app.Activity
import android.os.Build

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * 把任意深色配色压成 AMOLED 纯黑。
 *
 * 大面积（background / surface / surfaceDim / surfaceContainerLowest）压到纯黑，
 * 其余容器只保留极细微的递增差异——完全抹平会让卡片和内容分不出来，
 * 但这些差值小到在 OLED 上几乎不发光，仍是省电的。
 *
 * surfaceTint 一并压黑：Material3 用它在深色下做 elevation 混色，
 * 留着主色会让每张卡片都浮出一层深紫，纯黑就毁了。
 */
private fun applyAmoled(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF0A0A0A),
    surfaceTint = Color.Black,
    scrim = Color.Black
)

/** 纯黑配色没有独立常量：统一由 [applyAmoled] 在最后一步压黑，避免多条路径各压各的。 */

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * Removing the warning is not possible for compatibility reasons.
 *
 * 新增 [themeSettings] 参数。
 * - 传入非 null 时会按其 `themeMode` / `useMonet` 决定配色，并支持 AMOLED 纯黑；
 * - 传 null（或开关关闭）时行为与原始实现一致。
 */
@Composable
fun DhizukuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeSettings: ThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // 未显式传入且开关打开时，从配置流读取
    val flowSettings = if (themeSettings == null && NeoDhizuku.on(NeoDhizuku.UseThemePage)) {
        remember(context) { ThemeConfig.getThemeFlow(context) }
            .collectAsState(initial = ThemeSettings()).value
    } else null

    val settings = themeSettings ?: flowSettings

    val effectiveDark = settings?.let { shouldUseDarkTheme(it, darkTheme) } ?: darkTheme
    val effectiveMonet = settings?.let { it.useMonet && ThemeConfig.isMonetSupported() }
        ?: dynamicColor
    val amoled = settings?.themeMode?.isAmoled ?: false

    val baseScheme = when {
        effectiveMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        settings != null -> buildSeedColorScheme(
            seed = Color(settings.customColor),
            dark = effectiveDark
        )

        effectiveDark -> DarkColorScheme
        else -> LightColorScheme
    }

    // 动态取色有自己的一套 surface，同样要压黑，否则开了 Monet 纯黑就完全失效。
    val colorScheme = if (effectiveDark && amoled) applyAmoled(baseScheme) else baseScheme

    NeoLog.v(NeoLog.THEME) {
        "theme -> dark=$effectiveDark amoled=$amoled monet=$effectiveMonet " +
            "bg=#${Integer.toHexString(colorScheme.background.toArgb())} " +
            "surface=#${Integer.toHexString(colorScheme.surface.toArgb())}"
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, view)

            insetsController.isAppearanceLightStatusBars = !effectiveDark
            insetsController.isAppearanceLightNavigationBars = !effectiveDark
        }
    }

    // 设了自定义背景图时把各层底色改成半透明，让底下的图透上来。
    // 卡片、列表、对话框都用这几个颜色，改在主题层就能一次生效。
    val finalScheme =
        if (settings?.backgroundImageSet == true) translucentScheme(colorScheme)
        else colorScheme

    MaterialTheme(
        colorScheme = finalScheme,
        typography = Typography,
        content = content
    )
}

private fun translucentScheme(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = scheme.background.copy(alpha = 0.45f),
    surface = scheme.surface.copy(alpha = 0.50f),
    surfaceVariant = scheme.surfaceVariant.copy(alpha = 0.50f),
    surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = 0.42f),
    surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = 0.55f),
    surfaceContainer = scheme.surfaceContainer.copy(alpha = 0.58f),
    surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = 0.64f),
    surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = 0.70f)
)