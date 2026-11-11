package com.neodhizuku.more.ui.component.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * 内阴影：让玻璃有厚度。
 * 阴影落在 [GlassInnerShadow.offset] 的**反方向**内侧：[offset] 向下为正时
 * 可见的是上沿一弯，与 CSS `box-shadow: inset` 的语义一致。
 * 这里没有沿用「离屏挖洞」的常见做法。那条路子把「填满的轮廓」与
 * 「偏移后的镂空」录进同一离屏层，靠 [androidx.compose.ui.graphics.BlendMode.Clear]
 * 挖出月牙，再模糊后贴回 —— 那条路径依赖 `GraphicsLayer.record` 与
 * `CompositingStrategy.Offscreen`，一旦离屏层的尺寸或合成策略与预期不符，
 * 结果不是阴影变淡而是**整层被清成透明**，内容一起消失。
 * 采用纯几何绘制：把描边画在**内缩半个描边宽度**的轮廓上，于是描边的外沿
 * 正好贴住形状边缘，不会溢出到圆角外面。不申请离屏层、不做混合挖洞，
 * 也就没有把内容清掉的可能。视觉上少一点模糊的柔和感，换来的是确定性。
 */
@Immutable
data class GlassInnerShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
) {
    companion object {
        @Stable
        val Default: GlassInnerShadow = GlassInnerShadow()
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun Modifier.glassInnerShadow(
    shape: Shape,
    shadow: () -> GlassInnerShadow?,
): Modifier = this.drawWithCache {
    val s = shadow()
    val a = (s?.alpha ?: 0f).coerceIn(0f, 1f)

    if (s == null || a <= 0f) {
        return@drawWithCache onDrawWithContent { drawContent() }
    }

    val w = size.width
    val h = size.height

    // 阴影厚度取半径的一半，并压在高度的四分之一以内，
    // 免得在小控件上整块被涂黑。
    val strokeW = (s.radius.toPx() * 0.5f)
        .coerceAtLeast(1f)
        .coerceAtMost((h * 0.25f).coerceAtLeast(1f))

    // 描边内缩半个宽度：drawRoundRect 的 Stroke 是居中描边，
    // 不内缩的话有一半会画到圆角外面去。
    val inset = strokeW * 0.5f
    val innerW = (w - strokeW).coerceAtLeast(0f)
    val innerH = (h - strokeW).coerceAtLeast(0f)
    val corner = CornerRadius(innerH * 0.5f, innerH * 0.5f)

    // offset 向下为正时上沿最重；向上为负时反过来。
    val down = s.offset.y.value >= 0f
    val baseAlpha = s.color.alpha * a
    val brush = Brush.verticalGradient(
        0.00f to s.color.copy(alpha = if (down) baseAlpha else baseAlpha * 0.20f),
        0.45f to s.color.copy(alpha = baseAlpha * 0.10f),
        1.00f to s.color.copy(alpha = if (down) baseAlpha * 0.28f else baseAlpha),
    )

    onDrawBehind {
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size(innerW, innerH),
            cornerRadius = corner,
            style = Stroke(width = strokeW)
        )
    }
}
