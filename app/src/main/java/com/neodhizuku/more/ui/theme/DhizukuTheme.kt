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
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * 把任意深色配色压成 AMOLED 纯黑。
 * 大面积（background / surface / surfaceDim / surfaceContainerLowest）压到纯黑，
 * 其余容器只保留极细微的递增差异——完全抹平会让卡片和内容分不出来，
 * 但这些差值小到在 OLED 上几乎不发光，仍是省电的。
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
 * 新增 [themeSettings] 参数。
 * - 传入非 null 时会按其 `themeMode` / `useMonet` 决定配色，并支持 AMOLED 纯黑；
 * - 传 null（或开关关闭）时行为与默认实现一致。
 */
@Composable
fun DhizukuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeSettings: ThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val uiEngine = LocalUiEngine.current

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

    // 设了自定义背景图时把各层底色设为半透明，让底下的图透上来。
    // 卡片、列表、对话框都用这几个颜色，改在主题层就能一次生效。
    val finalScheme =
        if (settings?.backgroundImageSet == true) translucentScheme(colorScheme)
        else colorScheme

    // miuix 引擎不再改写 MaterialTheme 的配色：组件库自己有 MiuixTheme，
    // 主色从当前配色注入那里即可，避免切引擎强制变蓝。
    val engineScheme = finalScheme
    val engineTypography = if (uiEngine == UiEngine.MIUIX) miuixTypography(Typography)
    else Typography

    MaterialTheme(
        colorScheme = engineScheme,
        typography = engineTypography
    ) {
        if (uiEngine == UiEngine.MIUIX) {
            // miuix 的 Card / 下拉 / 按钮读的是 MiuixTheme.colorScheme；
            // 不注入就会回落到组件库默认蓝，用户自定义主题色会被盖掉。
            MiuixTheme(colors = miuixEngineColors(colorScheme, effectiveDark)) {
                content()
            }
        } else {
            content()
        }
    }
}

/**
 * 把当前 Material 配色注入 miuix 组件库。只覆盖主色相关角色，
 * 背景/表面取自未透明化的 [colorScheme]，背景图模式下 miuix 卡片也不会变透明。
 */
private fun miuixEngineColors(scheme: ColorScheme, dark: Boolean) =
    if (dark) miuixDarkColorScheme(
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryVariant = scheme.primary,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        background = scheme.background,
        surface = scheme.surface
    ) else miuixLightColorScheme(
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryVariant = scheme.primary,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        background = scheme.background,
        surface = scheme.surface
    )

/** miuix 风格是折叠大标题：把 titleLarge 放大即可让所有 TopAppBar 跟着变。 */
private fun miuixTypography(base: androidx.compose.material3.Typography) =
    base.copy(
        titleLarge = base.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
        headlineMedium = base.headlineMedium.copy(fontSize = 30.sp)
    )

private fun translucentScheme(scheme: ColorScheme): ColorScheme = scheme.copy(
    // Scaffold / Surface / TopAppBar 都要完全透明，否则浅色主题下那层白会把
    // 背景图整片盖掉，只剩卡片里看得出模糊。
    // 暗化统一交给 AppBackground 的中性黑遮罩，四周因此仍比卡片略深。
    background = scheme.background.copy(alpha = 0f),
    surface = scheme.surface.copy(alpha = 0f),
    surfaceVariant = scheme.surfaceVariant.copy(alpha = 0.32f),
    surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = 0.32f),
    surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = 0.40f),
    // 顶栏滚动后的容器色压低，保持与背景一致。
    surfaceContainer = scheme.surfaceContainer.copy(alpha = 0.22f),
    surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = 0.58f),
    surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = 0.66f)
)