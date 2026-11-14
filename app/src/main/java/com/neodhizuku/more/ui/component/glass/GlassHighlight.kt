package com.neodhizuku.more.ui.component.glass

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 玻璃描边高光：白色细描边 + 双峰柔光，与 miuix 液态玻璃示例一致。 */
internal val GlassIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

/** 高光主光源跟着重力方向旋转，再叠一个固定偏转，手机倾斜时光斑会跟着走。 */
@Composable
internal fun rememberGravityRotatedHighlight(
    base: Highlight,
    tilt: DeviceTilt,
    extraDegrees: Float,
): Highlight {
    val baseStyle = base.style as BloomStroke
    return remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val gMagSq = gx * gx + gy * gy
        val (lx0, ly0) = if (gMagSq > GRAVITY_DIR_THRESHOLD_SQ) {
            val inv = 1f / sqrt(gMagSq)
            (gx * inv) to (gy * inv)
        } else {
            0f to -1f
        }
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val rotated = basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + (c * lx0 - s * ly0),
                y = LIGHT_REF_Y + (s * lx0 + c * ly0),
                z = basePrimary.position.z,
            ),
        )
        base.copy(style = baseStyle.copy(primaryLight = rotated))
    }
}

/**
 * 按压处的交互光斑。只在 RuntimeShader 可用（Android 13+）的真玻璃分支里创建，
 * 与拖动状态的手势互不消费事件，可以同时挂在底栏上。
 */
@SuppressLint("NewApi")
internal class GlassInteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressSpec = spring(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressAnim = Animatable(0f, 0.001f)
    private val positionAnim =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    private val shader = RuntimeShader(
        """
    uniform float2 size;
    layout(color) uniform half4 color;
    uniform float radius;
    uniform float2 position;

    half4 main(float2 coord) {
        float dist = distance(coord, position);
        float intensity = smoothstep(radius, radius * 0.5, dist);
        return color * intensity;
    }"""
    )

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressAnim.value
        if (progress > 0f) {
            drawRect(Color.White.copy(0.06f * progress), blendMode = BlendMode.Plus)
            shader.apply {
                val p = position(size, positionAnim.value)
                setFloatUniform("size", size.width, size.height)
                setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                setFloatUniform("radius", size.minDimension * 1.2f)
                setFloatUniform(
                    "position",
                    p.x.fastCoerceIn(0f, size.width),
                    p.y.fastCoerceIn(0f, size.height),
                )
            }
            drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectHighlightGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressAnim.animateTo(1f, pressSpec) }
                    launch { positionAnim.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressAnim.animateTo(0f, pressSpec) }
                    launch { positionAnim.animateTo(startPosition, positionSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressAnim.animateTo(0f, pressSpec) }
                    launch { positionAnim.animateTo(startPosition, positionSpec) }
                }
            },
        ) { change, _ ->
            animationScope.launch { positionAnim.snapTo(change.position) }
        }
    }
}

/** 与拖动检测同样的非消费式手势观察，只用来驱动光斑位置。 */
private suspend fun PointerInputScope.inspectHighlightGestures(
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        val id = down.id
        var previous = down.position
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == id }
            if (change == null) {
                cancelled = true
                break
            }
            if (!change.pressed) break
            val amount = change.position - previous
            previous = change.position
            if (amount != Offset.Zero) onDrag(change, amount)
        }
        if (cancelled) onDragCancel() else onDragEnd()
    }
}
