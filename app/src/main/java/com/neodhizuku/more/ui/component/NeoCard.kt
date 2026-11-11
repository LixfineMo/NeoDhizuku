package com.neodhizuku.more.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import com.neodhizuku.more.ui.component.liquid.vibrancy
import com.neodhizuku.more.ui.theme.LocalUiEngine
import com.neodhizuku.more.ui.theme.UiEngine
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 卡片玻璃效果的背景采样源。
 * 只挂到 AppBackground 上，不包含任何页面内容；卡片从这里取像素后做局部模糊，
 * 因此不会把卡片自己的文字也一起糊掉。
 */
val LocalCardGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 卡片背后的模糊半径（dp）。0 表示关闭局部模糊。 */
val LocalCardBlurDp = staticCompositionLocalOf { 0f }

/** 不支持真模糊时，用接近实色的卡片兜底，避免背景图直接透进文字。 */
val LocalCardSolidFallback = staticCompositionLocalOf { false }

/**
 * 全项目统一卡片。
 * 普通状态下与 ElevatedCard 行为一致；设置背景图且开启「卡片模糊」时，
 * 先从 [LocalCardGlassBackdrop] 采样背景，再按半径模糊，最后叠一层薄色遮罩。
 * 卡片内容永远清晰，模糊只发生在卡片背后。
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: CardColors = CardDefaults.elevatedCardColors(),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    // miuix 引擎用组件库自己的 Card：交互式卡片带按压反馈（与新版 KernelSU
    // 首页状态卡一致：Tilt，按下/长按时卡片朝触点下沉）。静态卡片不加反馈。
    // 不叠加原版的背景采样模糊 —— 那是「原版」引擎独有的玻璃效果。
    if (LocalUiEngine.current == UiEngine.MIUIX) {
        // 卡片底色也交给组件库，危险色/自定义色才不会在 miuix 引擎下丢失。
        // 背景图模式下 MaterialTheme 的容器色是半透明的，这里补成不透明，
        // miuix 引擎不叠加原版的玻璃采样，半透明反而会让卡片发灰。
        val container = colors.containerColor
        val miuixColor = when {
            container == Color.Unspecified -> MiuixTheme.colorScheme.surface
            container.alpha < 1f -> container.copy(alpha = 1f)
            else -> container
        }
        val miuixColors = MiuixCardDefaults.defaultColors(color = miuixColor)
        // miuix Card 用 cornerRadius(Dp) 而不是 Shape；把调用方传的圆角等值搬过去，
        // 避免「所有卡片换成 miuix」后首页那几张 20dp 圆角卡被压回 16dp。
        val cornerRadius = (shape as? RoundedCornerShape)?.let { s ->
            with(density) { s.topStart.toPx(Size.Zero, this).toDp() }
        } ?: 16.dp
        val cardPadding = PaddingValues(0.dp)
        if (onClick != null && enabled) {
            MiuixCard(
                modifier = modifier,
                onClick = onClick,
                showIndication = true,
                pressFeedbackType = PressFeedbackType.Tilt,
                cornerRadius = cornerRadius,
                insideMargin = cardPadding,
                colors = miuixColors,
                content = content
            )
        } else {
            MiuixCard(
                modifier = modifier,
                cornerRadius = cornerRadius,
                insideMargin = cardPadding,
                colors = miuixColors,
                content = content
            )
        }
        return
    }

    val backdrop = LocalCardGlassBackdrop.current
    val blurDp = LocalCardBlurDp.current
    val solidFallback = LocalCardSolidFallback.current
    // 判空只做一次：K2 会把非空结果收窄给下面的 drawBackdrop，
    // 不必再写一遍 backdrop != null（那一遍恒为真，会触发警告）。
    val glassBackdrop = backdrop?.takeIf { blurDp > 0f && !solidFallback }
    val glassActive = glassBackdrop != null

    val surface = MaterialTheme.colorScheme.surfaceContainer
    val radiusPx = with(density) { blurDp.coerceAtLeast(0f).dp.toPx() }
    // 玻璃态只留极薄一层中性黑：浅色主题下用 surface / containerColor
    // 做遮罩会把卡片中间洗成一片白，四周也因此看着比卡片干净。
    val overlayTint = Color.Black.copy(alpha = 0.04f)
    // 玻璃态下卡片自身必须完全透明：模糊结果由 drawBackdrop 绘制，
    // 再叠一层半透明 surface / elevation 会把中间盖白，只剩边缘看得出颜色。
    val cardElevation = CardDefaults.cardElevation(
        defaultElevation = if (glassActive) 0.dp else 1.dp
    )

    val cardColors = when {
        glassActive -> CardDefaults.elevatedCardColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        )
        solidFallback -> CardDefaults.elevatedCardColors(
            containerColor = solidTint(colors.containerColor, surface),
            disabledContainerColor = solidTint(colors.disabledContainerColor, surface)
        )
        else -> colors
    }

    val glassModifier = if (glassBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = glassBackdrop,
            shape = { shape },
            effects = {
                // 提饱和，避免背景被 dim 洗白后透出来是一片灰白
                vibrancy()
                blur(radiusPx, radiusPx)
            },
            onDrawSurface = {
                drawRect(overlayTint)
            }
        )
    } else {
        Modifier
    }

    val cardModifier = modifier.then(glassModifier)

    if (onClick == null) {
        ElevatedCard(
            modifier = cardModifier,
            shape = shape,
            colors = cardColors,
            elevation = cardElevation,
            content = content
        )
    } else {
        ElevatedCard(
            onClick = onClick,
            modifier = cardModifier,
            enabled = enabled,
            shape = shape,
            colors = cardColors,
            elevation = cardElevation,
            interactionSource = interactionSource,
            content = content
        )
    }
}

private fun solidTint(color: Color, fallback: Color): Color {
    val base = if (color == Color.Unspecified) fallback else color
    return base.copy(alpha = 0.92f)
}
