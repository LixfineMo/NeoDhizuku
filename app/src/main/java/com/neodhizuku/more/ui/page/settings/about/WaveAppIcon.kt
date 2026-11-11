package com.neodhizuku.more.ui.page.settings.about

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.neodhizuku.more.BuildConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private val WAVE_BOX_HEIGHT = 176.dp
private val WAVE_CORNER = 28.dp
private val ICON_SIZE = 84.dp

/** 图标中心所在的高度比例，夹在 back 层与 front 层之间。 */
private const val ICON_BASE_RATIO = 0.46f

/** 低于这个读数视为平放：不据此判定方向，只保留极慢的漂移。 */
private const val TILT_DEAD_ZONE = 0.6f

/** 倾斜到这个读数就是满速，大致对应 19 度。 */
private const val TILT_FULL = 3.2f

/** 平放时的漂移速度，只为让液面不至于完全静止。 */
private const val TILT_IDLE = 0.18f

/** 速度趋近目标的时间常数：越大，换向越像"先慢下来再掉头"。 */
private const val TILT_TAU = 0.75f

/**
 * 判定传感器"真的在变化"的窗口内读数跨度。
 * 模拟器与部分虚拟机有加速度计但读数恒定，此时若按倾斜计算方向，结果恒为
 * 0，液面会彻底静止。窗口内跨度超过这个值就认为拿到了真实的倾斜输入。
 * 这个判定是"一次性"的：一旦为真就永久信任该传感器。否则把手机稳定握在一个
 * 角度不动时，窗口跨度会收敛回 0，判定失效，液面又会退回去自动漂移 —— 表现
 * 就是"跟着倾斜走一下，然后卡一下又倒回去"。
 */
private const val TILT_LIVE_SPAN = 0.25f

private const val TILT_SAMPLE_WINDOW_MS = 1_200L

/**
 * 没有可用倾斜输入时，自动漂移一整轮的周期（毫秒）。
 * 用正弦而不是"每隔几秒把方向取反"：正弦会连续经过零点，换向是渐变的，不会
 * 出现到点突然掉头的顿挫感。
 */
private const val AUTO_PERIOD_MS = 11_000f

/** 波浪的一层：振幅、基线高度、波长、速度倍率、相位偏移、配色。 */
private class WaveLayer(
    val ampRatio: Float,
    val baseRatio: Float,
    val waveRatio: Float,
    val speedMul: Float,
    val phaseOffset: Float,
    val colors: List<Color>,
    val alpha: Float
)

/** 图标下方的一层：不透明，构成液体的主体。 */
private fun buildBackLayers(c1: Color, c2: Color, c3: Color): List<WaveLayer> = listOf(
    WaveLayer(0.075f, 0.96f, 0.62f, 1.00f, 0.0f, listOf(c1, c2, c1), 1.00f),
    WaveLayer(0.085f, 0.74f, 0.47f, -0.72f, 1.1f, listOf(c2, c3, c2), 0.92f)
)

/**
 * 图标上方的一层：半透明，盖在图标上，图标因此是"泡在液体里"而不是浮在表面。
 * 三层的 alpha 都压得比较低：叠加后对图标的覆盖约四成，图标仍看得清，
 * 但边缘明显有颜色流过 —— 全部堆到不透明就等于把图标埋掉了。
 */
private fun buildFrontLayers(c1: Color, c2: Color): List<WaveLayer> {
    // 首尾同色，渐变在重复平铺时才不会有接缝
    val rainbow = listOf(
        Color(0xFF4FC3F7), Color(0xFF7C4DFF), Color(0xFFFF6E9C), Color(0xFF4FC3F7)
    )
    val bright = listOf(
        Color(0xFF82E9FF), Color(0xFFB388FF), Color(0xFFFF9EC4), Color(0xFF82E9FF)
    )
    return listOf(
        WaveLayer(0.065f, 0.58f, 0.38f, 1.28f, 2.4f, rainbow, 0.28f),
        WaveLayer(0.050f, 0.36f, 0.30f, -1.62f, 3.7f, bright, 0.18f),
        WaveLayer(0.038f, 0.17f, 0.24f, 1.95f, 5.0f, listOf(c1, c2, c1), 0.11f)
    )
}

private fun DrawScope.drawWaves(phase: Float, layers: List<WaveLayer>) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val step = (w / 72f).coerceAtLeast(2f)

    layers.forEach { layer ->
        val amp = layer.ampRatio * h
        val baseY = layer.baseRatio * h
        val wavelength = (w * layer.waveRatio).coerceAtLeast(1f)
        val ph = phase * layer.speedMul + layer.phaseOffset

        val path = Path()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            val y = baseY - amp * sin((x / wavelength) * 2f * PI.toFloat() + ph)
            path.lineTo(x, y)
            x += step
        }
        path.lineTo(w, h)
        path.close()

        // 渐变跟着相位平移，颜色看起来在随波流动
        val shift = (((phase * 26f * layer.speedMul) % w) + w) % w
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = layer.colors,
                start = Offset(shift - w, 0f),
                end = Offset(shift, 0f),
                tileMode = TileMode.Repeated
            ),
            alpha = layer.alpha
        )
    }
}

/** 加速度计的读数由监听线程写入、动画协程读取，用 volatile 就够。 */
private class TiltHolder {
    @Volatile var value: Float = 0f
    @Volatile var hasEvent: Boolean = false
    @Volatile var minSeen: Float = 0f
    @Volatile var maxSeen: Float = 0f
}

/**
 * 顶部图标：彩色液体充满整块区域，应用图标被波浪推着横穿屏幕。
 * 流向由倾斜角度连续决定：倾斜越大流得越快，液体往低的那侧走；换向时速度按
 * 指数趋近目标，所以会先慢下来再往反方向加速，不会瞬间掉头。传感器拿不到真实
 * 读数时（模拟器、虚拟机）改用正弦自动漂移，同样是连续换向。
 * 液体分两批绘制：图标下方两不透明层构成主体，图标上方三半透明层盖在上面，
 * 图标因此夹在液体中间，而不是浮在表面。
 */
@Composable
fun WaveAppIcon(onTap: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(context) { loadSelfIcon(context) }

    val scheme = MaterialTheme.colorScheme
    val backLayers = remember(scheme.primary, scheme.tertiary, scheme.secondary) {
        buildBackLayers(scheme.primary, scheme.tertiary, scheme.secondary)
    }
    val frontLayers = remember(scheme.primary, scheme.secondary) {
        buildFrontLayers(scheme.primary, scheme.secondary)
    }

    val holder = remember { TiltHolder() }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = if (manager != null && sensor != null) {
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // 低通，避免手抖让方向来回跳
                    val x = event.values.getOrNull(0) ?: return
                    holder.value += (x - holder.value) * 0.12f
                    if (!holder.hasEvent) {
                        holder.hasEvent = true
                        holder.minSeen = holder.value
                        holder.maxSeen = holder.value
                    }
                    if (holder.value < holder.minSeen) holder.minSeen = holder.value
                    if (holder.value > holder.maxSeen) holder.maxSeen = holder.value
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
        } else {
            null
        }
        if (manager != null && sensor != null && listener != null) {
            runCatching {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        onDispose {
            if (manager != null && listener != null) {
                runCatching { manager.unregisterListener(listener) }
            }
        }
    }

    var phase by remember { mutableFloatStateOf(0f) }
    var iconPos by remember { mutableFloatStateOf(0.5f) }
    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        var lastSample = 0L
        var speed = 0f
        var live = false
        var idleDir = 1f

        while (true) {
            withFrameMillis { now ->
                if (lastFrame == 0L) {
                    lastFrame = now
                    lastSample = now
                }
                val dt = ((now - lastFrame) / 1000f).coerceIn(0f, 0.05f)
                lastFrame = now

                // 每隔一段时间看一眼传感器读数是否真的动过。
                // 有传感器但读数恒定（模拟器、虚拟机）时跨度接近 0，
                // 此时读数不可信，只能自己漂。一旦动过就永久信任它，
                // 这样稳定握持在某个角度时判定不会失效。
                if (now - lastSample > TILT_SAMPLE_WINDOW_MS) {
                    if (holder.hasEvent && (holder.maxSeen - holder.minSeen) > TILT_LIVE_SPAN) {
                        live = true
                    }
                    holder.minSeen = holder.value
                    holder.maxSeen = holder.value
                    lastSample = now
                }

                // 加速度计读的是"上"的方向，液体应当往低的那侧流，所以取反。
                // 觉得方向反了就把这里的负号去掉。
                val flow = -holder.value

                val target = if (live) {
                    if (abs(flow) > TILT_DEAD_ZONE) {
                        // 直接按倾斜角度给速度：角度越大流得越快
                        idleDir = if (flow > 0f) 1f else -1f
                        (flow / TILT_FULL).coerceIn(-1f, 1f)
                    } else {
                        // 平放：不判定方向，沿用上一次的方向缓慢漂移
                        idleDir * TILT_IDLE
                    }
                } else {
                    // 没有可用的倾斜输入：正弦漂移，换向连续、不顿挫
                    sin(now / AUTO_PERIOD_MS * 2f * PI.toFloat()) * 0.75f
                }

                // 速度按指数趋近目标，换向时会先慢下来再往反方向走
                speed += (target - speed) * (1f - exp(-dt / TILT_TAU))

                phase += speed * dt * 1.7f
                var next = iconPos + speed * dt * 0.10f
                next %= 1f
                if (next < 0f) next += 1f
                iconPos = next
            }
        }
    }

    // 按压时整块圆形变灰。系统默认的 ripple 是方块形的，所以这里关掉它，
    // 自己叠一层灰 —— 父级已经裁成圆形，遮罩自然跟着是圆的。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressGray by animateFloatAsState(
        targetValue = if (pressed) 0.45f else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "waveIconPress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WAVE_BOX_HEIGHT)
            .clip(RoundedCornerShape(WAVE_CORNER))
            .onSizeChanged { boxW = it.width; boxH = it.height }
    ) {
        // ① 图标下方：不透明的两层，液体的主体
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawWaves(phase, backLayers) }
        )

        // ② 图标：夹在两层液体之间
        Box(
            modifier = Modifier
                .size(ICON_SIZE)
                // placement 阶段读状态，只重新摆放不重组
                .offset {
                    val iconPx = ICON_SIZE.toPx()
                    val w = boxW.toFloat()
                    val h = boxH.toFloat()
                    // 传送门式循环：offset 给的是「左上角」坐标，不是中心。
                    // pos=0 → 右边缘刚好贴左边界；pos=1 → 左边缘刚好贴右边界。
                    // 两端都完全在屏幕外，回绕时不会在左边凭空露出半个图标。
                    val x = iconPos * (w + iconPx) - iconPx
                    val bob = sin(iconPos * 4f * PI.toFloat() + phase * 2f) * 6.dp.toPx()
                    IntOffset(
                        x.roundToInt(),
                        (h * ICON_BASE_RATIO - iconPx * 0.5f + bob).roundToInt()
                    )
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap
                )
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(ICON_SIZE * 0.66f)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.primary
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(alpha = pressGray))
            )
        }

        // ③ 图标上方：半透明的三层，液体从图标身上流过
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawWaves(phase, frontLayers) }
        )
    }
}

private fun loadSelfIcon(context: Context) = try {
    context.packageManager.getApplicationIcon(BuildConfig.APPLICATION_ID)
} catch (_: PackageManager.NameNotFoundException) {
    null
}
