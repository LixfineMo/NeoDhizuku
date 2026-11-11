package com.neodhizuku.more.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 跟随 Pager 时，把「每帧移动了多少页」换算成速度形变强度的倍率。 */
private const val FOLLOW_VELOCITY_GAIN = 2.2f

/** 判定为「拖动」而非「点击」的位移阈值倍数（相对 touchSlop）。 */
private const val DRAG_SLOP_RATIO = 1f

/** 拖动轴：底栏横向，宽屏侧栏纵向。侧栏必须按 Y 计位移，否则纵向拖动会被判成点击。 */
internal enum class DragAxis { X, Y }

/**
 * 底栏的「按住拖动」手势状态。
 * 手指按下只记录按压态，**不提前把药丸挪走**：点击的位移交给 Pager 的
 * 翻页动画，药丸逐帧跟随它的连续位置，两者始终同步。
 * 真正拖动时药丸才连续跟手，松手落到最近的一页并回调。
 * 所有动画值都是快照状态，读取一律放在 `offset {}` / `graphicsLayer {}` /
 * `drawWithCache {}` 这些延迟读取的位置，拖动过程中不触发重组。
 */
internal class NavBarDragState(
    private val scope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    /** 判定拖动的位移阈值（像素）。 */
    private val touchSlop: Float = 0f,
    /** 位移计在哪个轴上。默认 X，宽屏侧栏传 Y。 */
    private val axis: DragAxis = DragAxis.X
) {

    private val valueSpec = spring<Float>(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.001f)
    /**
     * 与 Pager 翻页动画规格一致的慢弹簧。
     * 点击时药丸用它自己跑一段，和 Pager 的 animateScrollToPage 同时起、同时到。
     */
    private val glideSpec = spring<Float>(dampingRatio = 1f, stiffness = 200f, visibilityThreshold = 0.001f)
    private val pressSpec = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val scaleSpecX = spring<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val scaleSpecY = spring<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val releaseSpec = spring<Float>(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val velocitySpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)

    /** 当前所在的页（小数，拖动时连续变化）。 */
    val value = Animatable(initialValue.coerceIn(valueRange), 0.001f)

    /** 按压进度 0→1，用于气泡高光与放大。 */
    val press = Animatable(0f, 0.001f)

    /** 药丸横向缩放。 */
    val scale = Animatable(1f, 0.001f)

    /** 整体回弹位移的累积量，松手后回到 0。 */
    val shift = Animatable(0f, 0.001f)

    /**
     * 药丸横向移动速度的衰减值（单位：页/秒）。
     * 每次拖动打一个瞬时值进去，随后自动衰减回 0 —— 移动越快这个值越大，
     * 药丸就被压得越扁、拉得越长，停下的瞬间回弹，即「果冻感」。
     */
    val velocity = Animatable(0f, 0.001f)

    private val pressedScale = 1.18f

    var onPress: (Offset) -> Unit = {}
    var onMove: (Offset) -> Unit = {}
    var onRelease: (cancelled: Boolean) -> Unit = {}

    /** 手指是否还按在底栏上。按下期间不让外部页码回写位置，否则会打架。 */
    var isDragging: Boolean = false
        private set

    /** 本次手势是否真的拖动过（超过阈值）。点击为 false，拖动为 true。 */
    var didDrag: Boolean = false
        private set

    /** 上一次跟随的目标位置，用于算出每帧位移。 */
    private var lastFollowTarget: Float? = null

    private var dragJob: Job? = null
    private var decaying = false

    /** 本次手势累计的横向位移，用于判定是点击还是拖动。 */
    private var dragDistance = 0f

    fun updateValue(target: Float) {
        val v = target.coerceIn(valueRange)
        if (value.targetValue == v) return
        scope.launch { value.animateTo(v, valueSpec) }
    }

    /**
     * 拖动中直接落位，不走弹簧。
     * [updateValue] 每收到一个 move 事件就重启一次 animateTo，药丸会稳定落后
     * 手指一截；侧栏要求严格跟手，这里只做一次同步 snap。
     */
    fun dragTo(target: Float) {
        val v = target.coerceIn(valueRange)
        dragJob?.cancel()
        dragJob = scope.launch { value.snapTo(v) }
    }

    /** 松手落位：位置立即对齐到目标页，同时解除按压与缩放。 */
    fun land(target: Float) {
        snapTo(target)
        release()
    }

    fun addShift(delta: Float) {
        scope.launch { shift.snapTo(shift.value + delta) }
    }

    /** 打一次瞬时速度，随后自动衰减回 0。 */
    fun addVelocity(delta: Float) {
        scope.launch {
            velocity.stop()
            velocity.snapTo(delta)
            velocity.animateTo(0f, velocitySpec)
        }
    }

    /** 松手：药丸落到目标页，整体位移回弹，按压态解除。 */
    fun settle(target: Float) {
        val v = target.coerceIn(valueRange)
        scope.launch { value.animateTo(v, valueSpec) }
        scope.launch { shift.animateTo(0f, releaseSpec) }
        scope.launch { velocity.animateTo(0f, velocitySpec) }
        release()
    }

    /**
     * 只解除按压与回弹，不碰位置。
     * 纯点击时用它：药丸的位置交给 Pager 的翻页动画逐帧驱动（[followTo]），
     * 这里再跑一段弹簧反而会和 Pager 抢位置，两边都不到位。
     */
    fun releaseOnly() {
        scope.launch { shift.animateTo(0f, releaseSpec) }
        scope.launch { velocity.animateTo(0f, velocitySpec) }
        release()
    }

    /** 外部页码变化时平滑跟随。 */
    fun animateToValue(target: Float) {
        val v = target.coerceIn(valueRange)
        scope.launch { value.animateTo(v, valueSpec) }
    }

    /**
     * 点击后药丸自己滑向目标页，与 Pager 的翻页动画同时启动、规格一致。
     * 这是兜底驱动源：若 Pager 逐帧回传位置，[followToSync] 会在下一帧接管并
     * 把这段弹簧取消（Animatable.snapTo 会停掉正在跑的动画），两者不会打架；
     * 若 Pager 不逐帧回传，这段弹簧就是唯一的驱动源，药丸照样走到目标页。
     */
    fun glideTo(target: Float, stiffness: Float = 200f) {
        val v = target.coerceIn(valueRange)
        val spec = if (stiffness == 200f) glideSpec else
            spring<Float>(dampingRatio = 1f, stiffness = stiffness, visibilityThreshold = 0.001f)
        scope.launch { value.animateTo(v, spec) }
    }

    /**
     * 宽屏侧栏专用：只写位置，不做速度形变、不起协程。
     * [followToSync] 每帧为 velocity 另起两个协程，翻页 300ms 里会起几十个
     * 互相取消的协程；侧栏还叠着阴影/玻璃，平板上会明显掉帧。侧栏不需要
     * 果冻形变（拖动时由 [addVelocity] 单独驱动），这里只保留同步落位。
     */
    suspend fun followToLite(target: Float) {
        value.snapTo(target.coerceIn(valueRange))
        lastFollowTarget = null
    }

    /**
     * 逐帧跟随外部连续位置（Pager 的滚动偏移），不做弹簧、不碰按压与缩放。
     * 与 [animateToValue] 的区别：后者是「目标变了，自己跑一段动画过去」，
     * 每来一个新整数页就要重启一次动画，跨多页翻页时会在中间页一顿一顿；
     * 本方法是「位置就是外部给的」，逐帧写入，因此与页面完全同步。
     * 必须同步写入 snapTo，否则协程调度会让药丸滞后页面一帧。
     */
    suspend fun followToSync(target: Float) {
        val v = target.coerceIn(valueRange)
        val previous = lastFollowTarget
        lastFollowTarget = v
        value.snapTo(v)
        val delta = if (previous == null) 0f else v - previous
        if (abs(delta) < 1e-4f) {
            // 停止跟随：只起一次衰减协程，不要每帧重复启动。
            if (velocity.value != 0f && !decaying) {
                decaying = true
                scope.launch {
                    velocity.animateTo(0f, velocitySpec)
                    decaying = false
                }
            }
            return
        }
        // 形变速度同步写入，避免每帧启停动画堆积协程。
        velocity.snapTo((delta * FOLLOW_VELOCITY_GAIN).coerceIn(-0.2f, 0.2f))
    }

    /**
     * 无动画对齐到指定页。
     * 底栏进入二级页面时会被整块移出组合，返回后 `remember` 重建，
     * 此时不能用动画补一段位移（会看到药丸从第 0 项滑过来），直接落位。
     */
    fun snapTo(target: Float) {
        val v = target.coerceIn(valueRange)
        scope.launch { value.snapTo(v) }
        scope.launch { shift.snapTo(0f) }
        scope.launch { velocity.snapTo(0f) }
    }

    private fun press() {
        scope.launch {
            launch { press.animateTo(1f, pressSpec) }
            launch { scale.animateTo(pressedScale, scaleSpecX) }
        }
    }

    private fun release() {
        scope.launch {
            launch { press.animateTo(0f, pressSpec) }
            launch { scale.animateTo(1f, scaleSpecY) }
        }
    }

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            isDragging = true
            didDrag = false
            dragDistance = 0f
            lastFollowTarget = null
            press()
            onPress(down.position)

            var id = down.id
            var previous = down.position
            var up: PointerInputChange? = null
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == id } ?: break
                if (!change.pressed) {
                    up = change
                    break
                }
                // 自己算位移，不用 positionChange()：它在部分版本里是属性而非函数
                val amount = change.position - previous
                previous = change.position
                if (amount != Offset.Zero) {
                    dragDistance += abs(if (axis == DragAxis.X) amount.x else amount.y)
                    if (!didDrag && dragDistance > touchSlop * DRAG_SLOP_RATIO) didDrag = true
                    onMove(amount)
                }
            }
            isDragging = false
            onRelease(up == null)
        }
    }
}
