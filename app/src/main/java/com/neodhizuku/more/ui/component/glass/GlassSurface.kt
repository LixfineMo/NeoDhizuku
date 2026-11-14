package com.neodhizuku.more.ui.component.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp

/**
 * 玻璃面板：底色 + 折射 + 轮廓双峰高光 + 内阴影 + 色散 + 细边。
 * 高光沿整个轮廓画一圈 bloom 描边：主光源在上方强度 1、副光源在下方 0.4、
 * 内侧模糊 2dp，上下双峰。光源位置随重力旋转（[specularShift]）。
 * 叠加顺序：
 * 1. **底色**（[containerAlpha]）：半透明，背后内容隐约可见。
 * 2. **vibrancy**：把底色的饱和度抬高，透过玻璃看东西颜色更浓。
 * 3. **几何折射**（[lensAlpha]）：两端窄亮带。API 33 上另有
 *    [glassLens] 的真折射着色器，这里只是低版本的兜底近似。
 * 4. **轮廓 bloom**（[specularAlpha]）：上强下弱的双峰描边。
 * 5. **内阴影**（[shadeAlpha]）：下半压暗，给厚度。
 * 6. **色散**（[dispersionAlpha]）：左右边缘分别偏青、偏橙的细边，
 *    是色散在几何上的廉价近似；真色散走 [glassLens] 的着色器。
 * 全部走 `drawWithCache`，按压进度与重力偏移在缓存块里读取，
 * 拖动过程中不触发重组。
 */
internal fun Modifier.glassPanel(
    containerColor: Color,
    containerAlpha: Float,
    specularAlpha: Float,
    specularShift: Float = 0f,
    rimAlpha: Float = 0.10f,
    shadeAlpha: Float = 0.06f,
    lensAlpha: Float = 0.0f,
    dispersionAlpha: Float = 0f,
    vibrancy: Float = 1f,
    borderColors: List<Color>? = null,
    pressProgress: () -> Float = { 0f },
    /** 自绘的假折射 / 色散层的可见度。0 表示静态完全不画，按下才渐显。 */
    refractionProgress: () -> Float = { 0f }
): Modifier = this
    .drawWithCache {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(h * 0.5f, h * 0.5f)
        val press = pressProgress()

        // 上半比下半亮一点，模拟光从上方来
        val body = Brush.verticalGradient(
            listOf(
                containerColor.copy(alpha = (containerAlpha * 1.15f).coerceAtMost(1f)),
                containerColor.copy(alpha = (containerAlpha * 0.72f).coerceAtMost(1f))
            ),
            startY = 0f,
            endY = h
        )

        // 透过玻璃看背景，颜色更浓一点
        val vibrancyFilter = if (vibrancy > 1f) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(vibrancy) })
        } else {
            null
        }

        // 边缘折射：两端窄亮带，圆角附近最强
        val lens = if (lensAlpha > 0f) {
            Brush.horizontalGradient(
                0.00f to Color.White.copy(alpha = lensAlpha),
                0.10f to Color.White.copy(alpha = lensAlpha * 0.35f),
                0.22f to Color.Transparent,
                0.78f to Color.Transparent,
                0.90f to Color.White.copy(alpha = lensAlpha * 0.25f),
                1.00f to Color.White.copy(alpha = lensAlpha * 0.7f)
            )
        } else {
            null
        }

        // 轮廓 bloom：沿整圈描边，上强下弱
        val specW = 1.dp.toPx()
        val specCenter = (0.5f + (specularShift * 0.30f).coerceIn(-0.30f, 0.30f))
            .coerceIn(0.20f, 0.80f)
        val specular = Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = specularAlpha),
            0.30f to Color.White.copy(alpha = specularAlpha * 0.30f),
            0.62f to Color.White.copy(alpha = specularAlpha * 0.12f),
            1.00f to Color.White.copy(alpha = specularAlpha * 0.40f)
        )
        // 内侧再糊一层更宽更淡的，近似 BloomStroke 的 innerBlurRadius
        val bloom = Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = specularAlpha * 0.28f),
            0.45f to Color.White.copy(alpha = specularAlpha * 0.06f),
            1.00f to Color.White.copy(alpha = specularAlpha * 0.10f)
        )

        // 顶沿镜面亮点：横向位置随重力移动，转动设备时会看到它挪
        val glint = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = specularAlpha * 0.45f),
                Color.White.copy(alpha = specularAlpha * 0.12f),
                Color.Transparent
            ),
            center = Offset(w * specCenter, 0f),
            radius = maxOf(w * 0.55f, h * 1.2f)
        )

        // 底沿回光：环境光从下方打回来
        val rimH = minOf(2.5.dp.toPx(), h * 0.25f)
        val rim = Brush.horizontalGradient(
            0.00f to Color.Transparent,
            0.24f to Color.White.copy(alpha = rimAlpha * 0.45f),
            0.50f to Color.White.copy(alpha = rimAlpha),
            0.76f to Color.White.copy(alpha = rimAlpha * 0.45f),
            1.00f to Color.Transparent
        )

        // 色散：左偏青、右偏橙，只在最外侧极窄一条
        val dispersionW = maxOf(1.5.dp.toPx(), w * 0.012f)
        val dispersion = if (dispersionAlpha > 0f) {
            Brush.horizontalGradient(
                0.00f to Color.Cyan.copy(alpha = dispersionAlpha),
                0.05f to Color.Transparent,
                0.95f to Color.Transparent,
                1.00f to Color(0xFFFFB060).copy(alpha = dispersionAlpha)
            )
        } else {
            null
        }

        val shade = Brush.verticalGradient(
            0.0f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = shadeAlpha),
            startY = h * 0.45f,
            endY = h
        )

        // 按压时整体压暗一点，像手指按在玻璃上
        val dim = Color.Black.copy(alpha = 0.08f * press)

        onDrawBehind {
            // 按压时真折射接管，自绘高光/轮廓按 fade 让路，否则把折射成果糊住。
            // 在 draw 阶段读 state，不触发重组。
            val glassFade = 1f - 0.5f * refractionProgress().coerceIn(0f, 1f)
            drawRoundRect(brush = body, cornerRadius = radius, colorFilter = vibrancyFilter)
            if (lens != null) {
                // alpha 在 draw 阶段读 state：不会触发重组，只触发重绘。
                drawRoundRect(
                    brush = lens,
                    cornerRadius = radius,
                    alpha = refractionProgress().coerceIn(0f, 1f)
                )
            }
            drawRoundRect(brush = shade, cornerRadius = radius, alpha = glassFade)

            // 顶沿镜面亮点只在上一半画，落到下半就变成整块发白
            clipRect(0f, 0f, w, h * 0.5f) {
                drawRoundRect(brush = glint, cornerRadius = radius, alpha = glassFade)
            }

            // 轮廓光：内层宽而淡，外层窄而亮
            drawRoundRect(
                brush = bloom,
                cornerRadius = radius,
                style = Stroke(width = specW * 4f),
                alpha = glassFade
            )
            drawRoundRect(
                brush = specular,
                cornerRadius = radius,
                style = Stroke(width = specW),
                alpha = glassFade
            )

            clipRect(0f, h - rimH, w, h) {
                drawRoundRect(brush = rim, cornerRadius = radius, alpha = glassFade)
            }
            if (dispersion != null) {
                val refractionAlpha = refractionProgress().coerceIn(0f, 1f)
                clipRect(0f, 0f, dispersionW, h) {
                    drawRoundRect(
                        brush = dispersion,
                        cornerRadius = radius,
                        alpha = refractionAlpha
                    )
                }
                clipRect(w - dispersionW, 0f, w, h) {
                    drawRoundRect(
                        brush = dispersion,
                        cornerRadius = radius,
                        alpha = refractionAlpha
                    )
                }
            }
            if (press > 0f) {
                drawRoundRect(color = dim, cornerRadius = radius)
            }
        }
    }
    .then(
        if (borderColors == null) {
            Modifier
        } else {
            Modifier.border(
                width = 1.dp,
                brush = Brush.verticalGradient(borderColors),
                shape = CircleShape
            )
        }
    )
