package com.neodhizuku.more.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 由单个种子色推导整套 Material3 配色。
 * 走 HSV 变换：主色沿用种子色的色相与大致明度（只做对比度收敛），
 * 次色 / 第三色按色相偏移生成，中性面（surface 系列）掺入极少量色相，
 * 这样整体不是"一堆灰 + 一个高饱和主色"，而是和取色值同源的一整套。
 */
private const val HUE_SHIFT_SECONDARY = 18f
private const val HUE_SHIFT_TERTIARY = 52f

/** 中性面允许掺入的最大饱和度，超过会让整屏发色、正文难读。 */
private const val MAX_NEUTRAL_SATURATION = 0.10f

private fun hsv(h: Float, s: Float, v: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    return Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf(hue, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        )
    )
}

private fun seedHsv(seed: Color): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), out)
    return out
}

/**
 * 只负责按种子色推导配色，不处理 AMOLED。
 * 纯黑统一由调用方套 [applyAmoled]，否则 Monet 与种子色两条路径会压出两套结果。
 */
fun buildSeedColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val base = seedHsv(seed)
    val hue = base[0]
    val sat = base[1].coerceIn(0.22f, 0.92f)
    val value = base[2]
    val neutral = (sat * 0.12f).coerceAtMost(MAX_NEUTRAL_SATURATION)

    return if (dark) {
        val background = hsv(hue, neutral * 0.8f, 0.07f)
        darkColorScheme(
            primary = hsv(hue, sat * 0.92f, value.coerceIn(0.60f, 0.88f)),
            onPrimary = hsv(hue, sat, 0.18f),
            primaryContainer = hsv(hue, sat * 0.72f, 0.32f),
            onPrimaryContainer = hsv(hue, sat * 0.50f, 0.92f),
            secondary = hsv(hue + HUE_SHIFT_SECONDARY, sat * 0.55f, 0.78f),
            onSecondary = hsv(hue + HUE_SHIFT_SECONDARY, sat * 0.60f, 0.16f),
            secondaryContainer = hsv(hue + HUE_SHIFT_SECONDARY, sat * 0.45f, 0.28f),
            onSecondaryContainer = hsv(hue + HUE_SHIFT_SECONDARY, sat * 0.35f, 0.90f),
            tertiary = hsv(hue + HUE_SHIFT_TERTIARY, sat * 0.68f, 0.82f),
            onTertiary = hsv(hue + HUE_SHIFT_TERTIARY, sat * 0.70f, 0.18f),
            tertiaryContainer = hsv(hue + HUE_SHIFT_TERTIARY, sat * 0.50f, 0.30f),
            onTertiaryContainer = hsv(hue + HUE_SHIFT_TERTIARY, sat * 0.40f, 0.92f),
            background = background,
            onBackground = hsv(hue, neutral * 0.6f, 0.92f),
            surface = background,
            onSurface = hsv(hue, neutral * 0.6f, 0.92f),
            surfaceVariant = hsv(hue, neutral, 0.16f),
            onSurfaceVariant = hsv(hue, neutral, 0.78f),
            surfaceTint = hsv(hue, sat * 0.92f, value.coerceIn(0.60f, 0.88f)),
            outline = hsv(hue, neutral, 0.42f),
            outlineVariant = hsv(hue, neutral, 0.28f),
            surfaceDim = hsv(hue, neutral * 0.8f, 0.06f),
            surfaceBright = hsv(hue, neutral, 0.22f),
            surfaceContainerLowest = hsv(hue, neutral * 0.8f, 0.05f),
            surfaceContainerLow = hsv(hue, neutral * 0.8f, 0.09f),
            surfaceContainer = hsv(hue, neutral * 0.8f, 0.12f),
            surfaceContainerHigh = hsv(hue, neutral, 0.16f),
            surfaceContainerHighest = hsv(hue, neutral, 0.20f),
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = hsv(hue, sat, value.coerceIn(0.42f, 0.62f)),
            onPrimary = Color.White,
            primaryContainer = hsv(hue, sat * 0.55f, 0.94f),
            onPrimaryContainer = hsv(hue, sat, 0.24f),
            secondary = hsv(hue + HUE_SHIFT_SECONDARY, sat * 0.50f, 0.52f),
            onSecondary = Color.White,
            secondaryContainer = hsv(hue + HUE_SHIFT_SECONDARY, sat * 0.35f, 0.94f),
            onSecondaryContainer = hsv(hue + HUE_SHIFT_SECONDARY, sat * 0.50f, 0.22f),
            tertiary = hsv(hue + HUE_SHIFT_TERTIARY, sat * 0.60f, 0.50f),
            onTertiary = Color.White,
            tertiaryContainer = hsv(hue + HUE_SHIFT_TERTIARY, sat * 0.40f, 0.93f),
            onTertiaryContainer = hsv(hue + HUE_SHIFT_TERTIARY, sat * 0.60f, 0.22f),
            background = hsv(hue, neutral * 0.4f, 0.98f),
            onBackground = hsv(hue, neutral, 0.12f),
            surface = hsv(hue, neutral * 0.4f, 0.98f),
            onSurface = hsv(hue, neutral, 0.12f),
            surfaceVariant = hsv(hue, neutral * 0.6f, 0.95f),
            onSurfaceVariant = hsv(hue, neutral, 0.34f),
            surfaceTint = hsv(hue, sat, value.coerceIn(0.42f, 0.62f)),
            outline = hsv(hue, neutral * 0.6f, 0.55f),
            outlineVariant = hsv(hue, neutral * 0.5f, 0.80f),
            surfaceDim = hsv(hue, neutral * 0.5f, 0.87f),
            surfaceBright = hsv(hue, neutral * 0.3f, 1.0f),
            surfaceContainerLowest = hsv(hue, neutral * 0.3f, 1.0f),
            surfaceContainerLow = hsv(hue, neutral * 0.35f, 0.98f),
            surfaceContainer = hsv(hue, neutral * 0.4f, 0.96f),
            surfaceContainerHigh = hsv(hue, neutral * 0.45f, 0.94f),
            surfaceContainerHighest = hsv(hue, neutral * 0.5f, 0.92f),
            scrim = Color.Black
        )
    }
}
