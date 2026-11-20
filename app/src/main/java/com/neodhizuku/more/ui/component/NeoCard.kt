package com.neodhizuku.more.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
 * MiuixGlassLayer
 * (backdrop: Backdrop, shape: Shape, radiusPx: Float, tint: Color = Color.Transparent, modifier: Modifier = Modifier)
 * 作用：miuix 引擎下卡片的玻璃层。 组件库的 Card 会在自己的 modifier 之后绘制底色，把 drawBackdrop 挂在 Card 外层 会被这层底色盖掉，所以玻璃必须画在「内容的最底层」，压住底色、托住文字。 [tint] 用于状态卡这类需要保留配色的卡片：半透明色叠在模糊结果之上。
 * 备注：Compose 组件
 */
@Composable
fun MiuixGlassLayer(
    backdrop: Backdrop,
    shape: Shape,
    radiusPx: Float,
    tint: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(radiusPx, radiusPx)
            },
            onDrawSurface = {
                drawRect(Color.Black.copy(alpha = 0.04f))
                if (tint.alpha > 0f) drawRect(tint)
            }
        )
    )
}

/**
 * NeoCard
 * (modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp), colors: CardColors = CardDefaults.elevatedCardColors(), onClick: (() -> Unit)? = null, enabled: Boolean = true, interactionSource: MutableInteractionSource? = null, content: @Composable ColumnScope.() -> Unit)
 * 作用：全项目统一卡片。设置背景图且开启局部模糊时，从 [LocalCardGlassBackdrop] 采样背景再按半径模糊，卡片内容始终清晰。
 * 备注：Compose 组件
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
    val backdrop = LocalCardGlassBackdrop.current
    val blurDp = LocalCardBlurDp.current
    val solidFallback = LocalCardSolidFallback.current
    val glassBackdrop = backdrop?.takeIf { blurDp > 0f && !solidFallback }
    val radiusPx = with(density) { blurDp.coerceAtLeast(0f).dp.toPx() }
    // 两套引擎共用同一份卡片采样层：miuix 引擎也要响应「卡片模糊」滑杆。
    val glassModifier = if (glassBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = glassBackdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(radiusPx, radiusPx)
            },
            onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.04f)) }
        )
    } else {
        Modifier
    }

    // miuix 引擎用组件库自己的 Card：交互式卡片带按压下沉反馈，静态卡不加。
    if (LocalUiEngine.current == UiEngine.MIUIX) {
        // 卡片底色也交给组件库，危险色/自定义色才不会在 miuix 引擎下丢失。
        // 背景图模式下 MaterialTheme 的容器色是半透明的，这里补成不透明；
        // 玻璃层改画在内容最底层（见 MiuixGlassLayer），不受底色覆盖影响。
        val container = colors.containerColor
        val miuixColor = when {
            // 局部模糊生效时卡片本身必须透明，否则组件库底色会把模糊盖住。
            glassBackdrop != null -> Color.Transparent
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
        val glassLayer = glassBackdrop
        val glassShape = shape
        val glassRadiusPx = radiusPx
        val wrapped: @Composable ColumnScope.() -> Unit = {
            if (glassLayer != null) {
                Box(Modifier.fillMaxWidth()) {
                    MiuixGlassLayer(
                        backdrop = glassLayer,
                        shape = glassShape,
                        radiusPx = glassRadiusPx,
                        modifier = Modifier.matchParentSize()
                    )
                    Column { content() }
                }
            } else {
                content()
            }
        }
        if (onClick != null && enabled) {
            MiuixCard(
                modifier = modifier,
                onClick = onClick,
                showIndication = true,
                pressFeedbackType = PressFeedbackType.Tilt,
                cornerRadius = cornerRadius,
                insideMargin = cardPadding,
                colors = miuixColors,
                content = wrapped
            )
        } else {
            MiuixCard(
                modifier = modifier,
                cornerRadius = cornerRadius,
                insideMargin = cardPadding,
                colors = miuixColors,
                content = wrapped
            )
        }
        return
    }

    val glassActive = glassBackdrop != null

    val surface = MaterialTheme.colorScheme.surfaceContainer
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

/**
 * solidTint
 * (color: Color, fallback: Color)
 * 作用：执行 solidTint
 * 备注：仅内部使用
 */
private fun solidTint(color: Color, fallback: Color): Color {
    val base = if (color == Color.Unspecified) fallback else color
    return base.copy(alpha = 0.92f)
}
