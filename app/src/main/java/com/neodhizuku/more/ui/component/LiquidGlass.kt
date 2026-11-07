package com.neodhizuku.more.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃质感：半透明底色 + 顶部镜面高光 + 底部回光 + 内阴影 + 一圈细边。
 *
 * 玻璃感主要来自**顶沿那条细而亮的高光**，不是大面积的白色渐变：
 * 高光越高越窄越像玻璃，铺满半个高度就变成一块发白的塑料。
 *
 * 全部走 `drawWithCache`，按压进度在缓存块里读取，拖动过程中不触发重组。
 */
fun Modifier.glassSurface(
    tintTop: Color,
    tintBottom: Color,
    specularAlpha: Float,
    rimAlpha: Float,
    shadeAlpha: Float,
    borderColors: List<Color>,
    specularHeight: Dp = 2.5.dp,
    pressProgress: () -> Float = { 0f }
): Modifier = this
    .drawWithCache {
        val w = size.width
        val h = size.height
        // 胶囊形：半径取高度一半
        val radius = CornerRadius(h * 0.5f, h * 0.5f)
        val press = pressProgress()

        val body = Brush.verticalGradient(
            colors = listOf(tintTop, tintBottom),
            startY = 0f,
            endY = h
        )

        // 顶沿镜面反射：细亮带，两端往圆角处衰减
        val specularH = minOf(specularHeight.toPx(), h * 0.35f)
        val specular = Brush.horizontalGradient(
            0.00f to Color.Transparent,
            0.14f to Color.White.copy(alpha = specularAlpha * 0.5f),
            0.38f to Color.White.copy(alpha = specularAlpha),
            0.62f to Color.White.copy(alpha = specularAlpha),
            0.86f to Color.White.copy(alpha = specularAlpha * 0.5f),
            1.00f to Color.Transparent
        )

        // 底沿回光：环境光从下方打回来，比顶沿弱得多
        val rimH = minOf(2.5.dp.toPx(), h * 0.25f)
        val rim = Brush.horizontalGradient(
            0.00f to Color.Transparent,
            0.24f to Color.White.copy(alpha = rimAlpha * 0.45f),
            0.50f to Color.White.copy(alpha = rimAlpha),
            0.76f to Color.White.copy(alpha = rimAlpha * 0.45f),
            1.00f to Color.Transparent
        )

        // 下半内阴影：给玻璃一点厚度
        val shade = Brush.verticalGradient(
            0.0f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = shadeAlpha),
            startY = h * 0.5f,
            endY = h
        )

        val dim = Color.Black.copy(alpha = 0.06f * press)

        onDrawBehind {
            drawRoundRect(brush = body, cornerRadius = radius)
            drawRoundRect(brush = shade, cornerRadius = radius)
            clipRect(0f, 0f, w, specularH) {
                drawRoundRect(brush = specular, cornerRadius = radius)
            }
            clipRect(0f, h - rimH, w, h) {
                drawRoundRect(brush = rim, cornerRadius = radius)
            }
            if (press > 0f) {
                drawRoundRect(color = dim, cornerRadius = radius)
            }
        }
    }
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(borderColors),
        shape = CircleShape
    )

/** 玻璃描边上沿/下沿的取色：深色下压暗，浅色下提亮。 */
fun glassBorderColors(dark: Boolean): List<Color> = if (dark) {
    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))
} else {
    listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.30f))
}
