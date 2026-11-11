package com.neodhizuku.more.ui.component.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 阻尼拖动手势：按下即预览到按下位置对应的项并整体放大，
 * 拖动时连续跟手，松手用弹簧落到最近的一项。
 * 与「拖动到哪就跳到哪」的写法区别在两点：位移用高刚度弹簧（stiffness 1000）
 * 收敛，松手后仍有惯性尾巴；缩放的 X/Y 用不同阻尼（0.6 / 0.7），
 * 回弹时会有轻微的横向拉伸，这是手感来源。
 * 所有动画值都是快照状态，读取一律放在 `offset {}` / `graphicsLayer {}` /
 * `drawWithCache {}` 这些延迟读取的位置，拖动过程中不触发重组。
 */
internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val initialScale: Float = 1f,
    private val pressedScale: Float = 78f / 56f,
    val canDrag: (Offset) -> Boolean = { true },
    private val onDragStarted: DampedDragAnimation.(Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDragCancelled: DampedDragAnimation.() -> Unit = onDragStopped,
    private val onDrag: DampedDragAnimation.(IntSize, Offset) -> Unit,
    private val onTap: DampedDragAnimation.() -> Unit = {},
    private val onLongPress: DampedDragAnimation.() -> Boolean = { false }
) {

    private val visibilityThreshold = 0.001f

    private val valueSpec = spring<Float>(1f, 1000f, visibilityThreshold)
    /** 与 Pager 翻页动画规格一致的慢弹簧，点击兜底用。 */
    private val glideSpec = spring<Float>(1f, 200f, visibilityThreshold)
    private val velocitySpec = spring<Float>(0.5f, 300f, visibilityThreshold * 10f)
    private val pressSpec = spring<Float>(1f, 1000f, visibilityThreshold)
    private val scaleXSpec = spring<Float>(0.6f, 250f, visibilityThreshold)
    private val scaleYSpec = spring<Float>(0.7f, 250f, visibilityThreshold)

    private val valueAnimation =
        Animatable(initialValue.coerceIn(valueRange), visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, visibilityThreshold)
    private val scaleXAnimation = Animatable(initialScale, visibilityThreshold)
    private val scaleYAnimation = Animatable(initialScale, visibilityThreshold)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    private var dragJob: Job? = null
    private var decaying = false

    /** 当前位置（小数，拖动中连续变化） */
    val value: Float get() = valueAnimation.value

    /**
     * 是否正被手指拖动。
     * 普通字段而非快照状态：只在协程里读，用于判断要不要让位给手势，
     * 不需要触发重组。
     */
    @Volatile
    var isDragging: Boolean = false
        private set

    /**
     * 本次手势是否真的拖动过（越过 touch slop）。
     * 用来区分「点一下」和「拖一段」：点一下时页面应该滑过去，
     * 拖动过则页面没跟着动、松手要直接对齐。
     */
    @Volatile
    var didDrag: Boolean = false
        private set

    /** 松手后将落到哪里 */
    val targetValue: Float get() = valueAnimation.targetValue

    /** 按压进度 0→1 */
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        val touchSlopSquared = viewConfiguration.touchSlop.let { it * it }
        val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
        var downPosition = Offset.Zero
        var movedBeyondTouchSlop = false
        var longPressTriggered = false
        var longPressConsumed = false
        var longPressJob: Job? = null

        inspectDragGestures(
            onDragStart = { down ->
                isDragging = true
                didDrag = false
                downPosition = down.position
                movedBeyondTouchSlop = false
                longPressTriggered = false
                longPressConsumed = false
                onDragStarted(down.position)
                press()
                longPressJob = animationScope.launch {
                    delay(longPressTimeoutMillis)
                    longPressTriggered = true
                    if (onLongPress()) {
                        longPressConsumed = true
                        onDragCancelled()
                        release()
                    }
                }
            },
            onDragEnd = {
                isDragging = false
                longPressJob?.cancel()
                longPressJob = null
                if (!longPressConsumed) {
                    onDragStopped()
                    release()
                    if (!longPressTriggered && !movedBeyondTouchSlop) {
                        onTap()
                    }
                }
            },
            onDragCancel = {
                isDragging = false
                longPressJob?.cancel()
                longPressJob = null
                if (!longPressConsumed) {
                    onDragCancelled()
                    release()
                }
            }
        ) { change, dragAmount ->
            if (longPressConsumed) return@inspectDragGestures

            val position = change.position
            val previousPosition = change.previousPosition

            if (!movedBeyondTouchSlop) {
                val displacement = position - downPosition
                movedBeyondTouchSlop = displacement.x * displacement.x +
                    displacement.y * displacement.y > touchSlopSquared
                if (movedBeyondTouchSlop) {
                    didDrag = true
                    longPressJob?.cancel()
                    longPressJob = null
                }
            }

            // 只在按下点与当前点都落在底栏范围内时才推进，避免拖出边界还跟着动
            if (canDrag(position) && canDrag(previousPosition)) {
                onDrag(size, dragAmount)
            }
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYSpec) }
        }
    }

    /** 拖动中连续更新位置。 */
    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(target, valueSpec) { updateVelocity() }
        }
    }

    /** 拖动中直接落位：不重启弹簧，也不每帧堆协程。 */
    fun dragTo(value: Float) {
        val target = value.coerceIn(valueRange)
        dragJob?.cancel()
        dragJob = animationScope.launch { valueAnimation.snapTo(target) }
    }

    /** 外部页码变化时平滑跟随：带一次按压，落位后解除。 */
    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocitySpec) }
                }
                release()
            }
        }
    }

    /**
     * 无动画落位。
     * 底栏随二级页面移出组合后 remember 会重建，此时不能用动画补一段位移
     * （会看到药丸从第一项滑过来），必须直接落位。
     */
    fun snapTo(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.snapTo(target) }
            launch { velocityAnimation.snapTo(0f) }
        }
    }

    /**
     * 点击后药丸自己滑向目标页，与 Pager 的翻页动画同时启动、规格一致。
     * 兜底驱动源：Pager 若逐帧回传位置，[followToSync] 下一帧就接管并取消它
     * （Animatable.snapTo 会停掉正在跑的动画），两者不打架；若没回传，
     * 它就是唯一驱动源，药丸不会停在原地。
     */
    fun glideTo(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(target, glideSpec) { updateVelocity() }
        }
    }

    /**
     * 逐帧跟随外部连续位置（Pager 的滚动偏移），不做弹簧、不碰按压与缩放。
     * 与 [animateToValue] 的区别：后者是「目标变了，自己跑一段动画过去」，
     * 每来一个新整数页就要重启一次动画；本方法是「位置就是外部给的」，
     * 逐帧写入，因此不会在中间页停住。
     * 位置必须同步写入 snapTo，否则协程调度会让药丸滞后页面一帧。
     */
    suspend fun followToSync(value: Float) {
        val target = value.coerceIn(valueRange)
        // 先判再写：snapTo 之后 targetValue 必然等于 target，判断就没意义了
        if (valueAnimation.targetValue == target) {
            // 停止跟随：只起一次衰减协程，不要每帧重复启动。
            if (velocityAnimation.value != 0f && !decaying) {
                decaying = true
                animationScope.launch {
                    velocityAnimation.animateTo(0f, velocitySpec)
                    decaying = false
                }
            }
            return
        }
        // 位置同步写入：这是药丸与页面同步的关键，绝不能另起协程去写
        valueAnimation.snapTo(target)
        // 形变速度同步打点，避免翻页期间堆积互相取消的协程。
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(target, 0f))
        val span = valueRange.endInclusive - valueRange.start
        if (span > 0f) {
            velocityAnimation.snapTo(velocityTracker.calculateVelocity().x / span)
        }
    }


    private fun updateVelocity() {
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val span = valueRange.endInclusive - valueRange.start
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocitySpec) }
    }
}

/**
 * 带长按与边界检查的拖动检测。
 * 与 `detectDragGestures` 的区别：不消费事件，且抬起/取消分开回调，
 * 这样底栏的手势不会把 Pager 的横向滑动吃掉。
 */
private suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
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
                // 指针被父级拿走，算取消
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
