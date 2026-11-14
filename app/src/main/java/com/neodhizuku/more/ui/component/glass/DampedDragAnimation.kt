package com.neodhizuku.more.ui.component.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    // 速度衰减必须单调：0.5 阻尼会让 velocity 来回振荡，layerBlock 每帧读它
    // 就会把药丸缩放抖成「底栏按钮在颤抖」。
    private val velocitySpec = spring<Float>(1f, 300f, visibilityThreshold * 10f)
    private val pressSpec = spring<Float>(1f, 1000f, visibilityThreshold)
    private val scaleXSpec = spring<Float>(0.6f, 250f, visibilityThreshold)
    private val scaleYSpec = spring<Float>(0.7f, 250f, visibilityThreshold)

    private val valueAnimation =
        Animatable(initialValue.coerceIn(valueRange), visibilityThreshold)
    // 速度值域被钳在 ±0.25，阈值沿用参考的 5f 会让衰减动画瞬间结束，
    // 形变「啪」地归零；用小阈值才能留下可见的回弹尾巴。
    private val velocityAnimation = Animatable(0f, visibilityThreshold)
    private val pressProgressAnimation = Animatable(0f, visibilityThreshold)
    private val scaleXAnimation = Animatable(initialScale, visibilityThreshold)
    private val scaleYAnimation = Animatable(initialScale, visibilityThreshold)

    private val velocityTracker = VelocityTracker()
    private var dragJob: Job? = null
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var decaying = false
    private var lastFeedbackMs = 0L

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
    /**
     * 形变速度统一是快照状态：普通字段的变化不会让 layerBlock 重绘，
     * 药丸的挤压拉伸就会一顿一顿；写进 Animatable 才能逐帧连续。
     */
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
        // 新一轮按下顶掉上一轮回弹，避免两组弹簧同时写同一组 Animatable；
        // 放大本身要跑满，松手后由 release() 接管回弹。
        releaseJob?.cancel()
        pressJob?.cancel()
        velocityTracker.resetTracking()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
        }
    }

    fun release() {
        // 不能取消 pressJob：放大要跑满，回弹才会从接近 pressedScale 起步，
        // 被 0.6 / 0.7 阻尼弹簧越过 1 再收回来 —— 这就是 Q 弹。
        // KSU 靠「等位置弹簧收敛」给放大留时间，但本项目位置完全由 Pager 逐帧
        // snapTo（始终跟手），value 恒等于 targetValue，那段等待永远不成立。
        // 改成直接等缩放到达目标 92%，再启动回弹；超时兜底避免极端帧率下卡死。
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            val reached = initialScale + (pressedScale - initialScale) * 0.92f
            runCatching {
                withTimeout(300L) {
                    snapshotFlow { scaleXAnimation.value }.first { it >= reached }
                }
            }
            // 放大已到位：立刻回弹。三个 animateTo 会各自取消同一 Animatable 上
            // 仍在跑的放大子协程；spring(0.6 / 0.7) 从 ~1.39 回 1 会越过 1 再
            // 收回，这就是 Q 弹。绝不能 join pressJob：spring 的
            // visibilityThreshold=0.001f 会把收敛拖到 0.6s 以上，回弹被推迟到
            // 松手后近一秒才启动，观感上就等于「没有 Q 弹」。
            launch { pressProgressAnimation.animateTo(0f, pressSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYSpec) }
        }
    }

    /**
     * 点击 / 跨整数页只跑缩放反馈。位置绝不在这里驱动：始终由 Pager 的连续页码
     * （followToSync）或手指（dragTo）逐帧写入，药丸才不会先被弹簧拽一下、
     * 下一帧又被 snapTo 硬拉回来（那就是切页发卡的原因）。
     */
    fun feedback() {
        // onSelect 与跨整数页会在同一次点击里先后调用，不节流的话第二次
        // press() 会 cancel 掉第一次刚起步的放大与回弹，Q 弹被自己掐断。
        val now = System.currentTimeMillis()
        if (now - lastFeedbackMs < 160L) return
        lastFeedbackMs = now
        press()
        release()
    }

    /** 拖动中连续更新位置。 */
    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.animateTo(target, valueSpec) { updateVelocity() }
        }
    }

    /** 拖动中直接落位：不重启弹簧，也不每帧堆协程。 */
    fun dragTo(value: Float) {
        val target = value.coerceIn(valueRange)
        dragJob?.cancel()
        dragJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.snapTo(target)
        }
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(target, 0f))
        val span = valueRange.endInclusive - valueRange.start
        if (span > 0f) {
            val raw = (velocityTracker.calculateVelocity().x / span).coerceIn(-0.25f, 0.25f)
            // 低通一次：瞬时速度在相邻帧会跳，形变跟着跳就成了抖动。
            val smoothed = velocityAnimation.value * 0.6f + raw * 0.4f
            animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                velocityAnimation.snapTo(smoothed)
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
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.snapTo(target)
            velocityAnimation.snapTo(0f)
        }
    }

    /**
     * 逐帧跟随外部连续位置（Pager 的滚动偏移），不做弹簧、不碰按压与缩放。
     * 这是药丸位置的唯一驱动源（手指拖动时是 [dragTo]），因此点击切页不会
     * 出现「弹簧先拽一下、下一帧又被 snapTo 硬拉回」的顿挫，始终跟手。
     * 位置必须同步写入 snapTo，否则协程调度会让药丸滞后页面一帧。
     */
    suspend fun followToSync(value: Float) {
        val target = value.coerceIn(valueRange)
        // 用当前值判断：snapTo 之后 targetValue 恒等于 target，
        // 旧写法会让速度衰减永远不启动。
        if (abs(valueAnimation.value - target) < visibilityThreshold) {
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
            val raw = (velocityTracker.calculateVelocity().x / span).coerceIn(-0.25f, 0.25f)
            velocityAnimation.snapTo(velocityAnimation.value * 0.6f + raw * 0.4f)
        }
    }


    private fun updateVelocity() {
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val span = valueRange.endInclusive - valueRange.start
        val targetVelocity = (velocityTracker.calculateVelocity().x / span)
            .coerceIn(-0.25f, 0.25f)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            velocityAnimation.snapTo(targetVelocity)
        }
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
