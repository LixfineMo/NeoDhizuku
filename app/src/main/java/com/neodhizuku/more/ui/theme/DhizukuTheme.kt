package com.neodhizuku.more.ui.theme

import android.app.Activity
import android.os.Build

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext

import com.neodhizuku.more.NeoDhizuku
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * AMOLED 纯黑配色。
 * 在深色方案基础上把 background / surface / surfaceContainer* 压到纯黑，
 * 深色 OLED 屏上省电且更"沉浸"。
 */
private val AmoledColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = androidx.compose.ui.graphics.Color.Black,
    surface = androidx.compose.ui.graphics.Color.Black,
    surfaceVariant = androidx.compose.ui.graphics.Color.Black,
    surfaceContainer = androidx.compose.ui.graphics.Color.Black,
    surfaceContainerLow = androidx.compose.ui.graphics.Color.Black,
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF0A0A0A)
)

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

    val colorScheme = when {
        effectiveMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        effectiveDark -> if (amoled) AmoledColorScheme else DarkColorScheme
        else -> LightColorScheme
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}