package com.neodhizuku.more.ui.component

import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.ui.component.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 侧栏胶囊自身宽度（不含左右外边距）。 */
private val RailWidth: Dp = 88.dp

/** 侧栏整体占位宽度：胶囊 + 左右外边距，内容区按这个宽度让开。 */
val RailTotalWidth: Dp = 116.dp

/** 每一页签占的高度，也是药丸高度。 */
private val RailItemHeight: Dp = 64.dp

/** 胶囊与药丸之间的内缩。 */
private val RailInset: Dp = 4.dp

/** 速度增益：把「每帧移动了多少页」换算成挤压拉伸的强度。 */
private const val PILL_VELOCITY_GAIN = 2.2f

/** 药丸与 Pager 位置相差多少页以内，才允许切到「逐帧跟随」。 */
private const val FOLLOW_ENTER_TOLERANCE = 0.06f

/**
 * 宽屏（平板 / 横屏）下替代底部悬浮底栏的竖排导航栏。
 * 与 [FloatingNavBar] 是同一套机制：同一个 [NavDestination] 列表、同一个
 * [NavBarDragState]（只把拖动轴从 X 换成 Y）、同一个 Pager 连续页码驱动。
 * 因此长按预览、纵向拖动跟手、松手落页、点击逐帧跟随这些行为与手机端完全一致。
 * 手势挂在「胶囊 + 药丸 + 页签」的共同父节点上，确保点击与长按拖动都能收到事件。
 * 胶囊本体与卡片共用同一份背景采样源：有背景图时同样是模糊玻璃，
 * 只是压一层中性黑让四周比卡片略深；没有背景图时退回纯色，零额外开销。
 * 位置与形变一律走 `offset {}` / `graphicsLayer {}` 延迟读取快照状态。
 * RTL 下由容器把本控件放到右侧，药丸本身水平居中，无需镜像。
 */
@Composable
fun SideNavRail(
    tabs: List<NavDestination>,
    selectedIndex: () -> Int,
    /** 连续页码（含小数），逐帧跟随 Pager，而不是对每个整数页重启动画。 */
    selectedPosition: () -> Float = { selectedIndex().toFloat() },
    onNavigate: (String) -> Unit,
    /** 拖动松手后让 Pager 立即对齐到落点。 */
    onSettleTab: ((Int) -> Unit)? = null,
    /**
     * 是否显示悬浮胶囊与选中药丸。关闭时导航条贴屏幕边缘铺满高度，
     * 只靠图标 / 文字颜色区分当前页 —— 对应主题页「平板模式仍然显示药丸」。
     */
    showPill: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    if (!showPill) {
        PlainSideNavRail(
            tabs = tabs,
            selectedIndex = selectedIndex,
            onNavigate = onNavigate,
            onSettleTab = onSettleTab,
            modifier = modifier
        )
        return
    }

    val scheme = MaterialTheme.colorScheme
    // background/surface 在设了背景图时是透明的，RGB 全 0 会被误判成深色主题；
    // 用不透明的容器色判断明暗才准。
    val surface = scheme.surfaceContainerHigh
    val dark = (surface.red + surface.green + surface.blue) < 1.5f
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val count = tabs.size
    val maxIndex = (count - 1).coerceAtLeast(0).toFloat()
    val itemHeightPx = with(density) { RailItemHeight.toPx() }
    val insetPx = with(density) { RailInset.toPx() }
    val rubberPx = with(density) { 4.dp.toPx() }
    val touchSlopPx = with(density) { 6.dp.toPx() }

    // 初值取当前页：二级页面返回后 remember 重建，写死 0 会让药丸滑回第一项。
    val drag = remember(scope, count) {
        NavBarDragState(
            scope = scope,
            initialValue = selectedIndex().coerceIn(0, count - 1).toFloat(),
            valueRange = 0f..maxIndex,
            touchSlop = touchSlopPx,
            axis = DragAxis.Y
        )
    }

    // 按下落在第几项；用数组装，避免写状态触发重组。
    val pressIndexBox = remember { intArrayOf(0) }
    // 拖动中已经同步给 Pager 的整数页，避免每帧重复 scrollToPage。
    val reportedPageBox = remember { intArrayOf(-1) }

    val selectedPositionState by rememberUpdatedState(selectedPosition)
    val selectedIndexState by rememberUpdatedState(selectedIndex)
    val onNavigateState by rememberUpdatedState(onNavigate)
    val onSettleTabState by rememberUpdatedState(onSettleTab)
    var currentIndex by remember { mutableIntStateOf(selectedIndex().coerceIn(0, count - 1)) }
    // 拖动落点用本地累计值，不读 Animatable.targetValue（它由协程异步写入，
    // 逐帧读会慢一拍，就是「不跟手」的根因）。
    val dragPosBox = remember { floatArrayOf(0f) }
    // 页签自己的点击入口，不依赖父级手势的 onRelease。
    val selectTab: (Int) -> Unit = { index ->
        val target = index.coerceIn(0, count - 1)
        val current = selectedIndexState().coerceIn(0, count - 1)
        if (target != current) {
            currentIndex = target
            drag.land(target.toFloat())
            NeoLog.d(NeoLog.NAV) { "侧栏选择 -> ${tabs[target].route} ($target)" }
            val settle = onSettleTabState
            if (settle != null) settle(target)
            else onNavigateState(tabs[target].route)
        } else {
            drag.releaseOnly()
        }
    }

    // 逐帧跟随 Pager 的连续位置，与底栏完全相同的接管策略。
    LaunchedEffect(drag, count) {
        var aligned = false
        var following = false
        snapshotFlow { selectedPositionState().coerceIn(0f, maxIndex) }
            .collectLatest { pos ->
                if (drag.isDragging) {
                    following = false
                    return@collectLatest
                }
                if (!aligned) {
                    aligned = true
                    following = true
                    currentIndex = pos.roundToInt().coerceIn(0, count - 1)
                    drag.snapTo(pos)
                    return@collectLatest
                }
                if (!following && abs(drag.value.value - pos) > FOLLOW_ENTER_TOLERANCE) {
                    return@collectLatest
                }
                following = true
                val index = pos.roundToInt().coerceIn(0, count - 1)
                if (index != currentIndex) currentIndex = index
                drag.followToLite(pos)
            }
    }

    LaunchedEffect(drag, itemHeightPx, count, tabs) {
        drag.onPress = { position ->
            // 只记录落点与拖动起点：点击由页签自己的 clickable 处理，
            // 父级手势只负责拖动，避免 onClick 与 onRelease 抢同一次抬手。
            pressIndexBox[0] = ((position.y - insetPx) / itemHeightPx)
                .toInt().coerceIn(0, count - 1)
            dragPosBox[0] = selectedIndexState().coerceIn(0, count - 1).toFloat()
            reportedPageBox[0] = selectedIndexState().coerceIn(0, count - 1)
        }
        drag.onMove = { amount ->
            if (itemHeightPx > 0f && amount.y != 0f) {
                val delta = amount.y / itemHeightPx
                dragPosBox[0] = (dragPosBox[0] + delta).coerceIn(0f, maxIndex)
                drag.dragTo(dragPosBox[0])
                // 避免每帧起 velocity 协程：拖动 300ms 会堆几十个互相取消的动画。
                // 页面跟着药丸一起走，松手前即切换。
                val page = dragPosBox[0].roundToInt().coerceIn(0, count - 1)
                if (page != reportedPageBox[0]) {
                    reportedPageBox[0] = page
                    onSettleTabState?.invoke(page)
                }
            }
        }
        drag.onRelease = { cancelled ->
            if (cancelled || !drag.didDrag) {
                // 点击由页签的 clickable 负责导航；这里只解除按压，
                // 不动位置，避免两个入口互相覆盖。
                drag.releaseOnly()
            } else {
                val target = dragPosBox[0].roundToInt().coerceIn(0, count - 1)
                val current = selectedIndexState().coerceIn(0, count - 1)
                drag.land(target.toFloat())
                if (target != current) {
                    currentIndex = target
                    NeoLog.d(NeoLog.NAV) { "侧栏拖动 -> ${tabs[target].route} ($target)" }
                    val settle = onSettleTabState
                    if (settle != null) settle(target)
                    else onNavigateState(tabs[target].route)
                }
            }
        }
    }

    // 橡皮筋位移：胶囊与药丸一起承担，两者相对位置不变。
    val shiftPx: () -> Float = {
        val total = itemHeightPx * count
        if (total <= 0f) 0f else {
            val frac = (drag.shift.value / total).coerceIn(-1f, 1f)
            rubberPx * sign(frac) * EaseOut.transform(abs(frac))
        }
    }
    val pillOffsetPx: () -> Float = { insetPx + drag.value.value * itemHeightPx + shiftPx() }
    val tabScale: () -> Float = { 1f + 0.14f * drag.press.value }

    val contentHeight = RailItemHeight * count + RailInset * 2

    // 与卡片共用背景采样源：四周同样是模糊玻璃，只比卡片多压一层中性黑。
    // 没设背景图 / 不支持真模糊时退回纯色，长按拖动零额外开销。
    val cardBackdrop = LocalCardGlassBackdrop.current
    val cardBlurDp = LocalCardBlurDp.current
    // 导航条不是卡片：卡片模糊默认 18dp 会把整条侧栏糊成一片雾。
    // 保留滑杆的低段响应，但钳在 3dp，与底栏的通透口径一致。
    val railRadiusPx = with(density) { cardBlurDp.coerceIn(0f, 3f).dp.toPx() }
    val railBackdrop = cardBackdrop?.takeIf { cardBlurDp > 0f }
    val outline = scheme.outlineVariant.copy(alpha = if (dark) 0.12f else 0.24f)
    val barSurface: Modifier = Modifier
        .clip(CircleShape)
        .then(
            if (railBackdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = railBackdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy(1.15f)
                        blur(railRadiusPx, railRadiusPx)
                    },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.05f))
                    }
                )
            } else {
                Modifier.background(scheme.surfaceContainer)
            }
        )
        .border(1.dp, outline, CircleShape)

    val pillSurface: Modifier = Modifier.background(scheme.secondaryContainer, CircleShape)

    Box(
        modifier = modifier
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            .fillMaxHeight()
            .width(RailWidth),
        contentAlignment = Alignment.Center
    ) {
        // 手势必须挂在三者的共同父节点：胶囊、药丸、页签都是它的子节点，
        // 命中测试沿父链下传，点在哪一层都能收到 down/move/up。
        Box(
            modifier = Modifier
                .width(RailWidth)
                .height(contentHeight)
                .then(drag.modifier)
        ) {
            // 竖排胶囊本体
            Box(
                modifier = Modifier
                    .width(RailWidth)
                    .height(contentHeight)
                    .offset { IntOffset(0, shiftPx().roundToInt()) }
                    .then(barSurface)
            )

            // 选中药丸：沿 Y 轴按连续页码移动，与 Pager 同一驱动源。
            Box(
                modifier = Modifier
                    .padding(horizontal = RailInset)
                    .offset { IntOffset(0, pillOffsetPx().roundToInt()) }
                    .width(RailWidth - RailInset * 2)
                    .height(RailItemHeight)
                    .clip(CircleShape)
                    .graphicsLayer {
                        val pressScale = 1f + 0.08f * drag.press.value
                        val v = (drag.velocity.value * PILL_VELOCITY_GAIN).coerceIn(-0.2f, 0.2f)
                        scaleX = pressScale * (1f - v * 0.25f)
                        scaleY = pressScale / (1f - v * 0.75f)
                    }
                    .then(pillSurface)
            )

            // 页签内容铺在药丸之上；颜色只在整数页变化时重组。
            CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
                Column(
                    modifier = Modifier
                        .width(RailWidth)
                        .height(contentHeight)
                        .padding(vertical = RailInset),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    tabs.forEachIndexed { i, destination ->
                        RailItem(
                            destination = destination,
                            current = i == currentIndex,
                            scale = tabScale,
                            pillMode = true,
                            onSelect = { selectTab(i) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    destination: NavDestination,
    current: Boolean,
    scale: () -> Float,
    pillMode: Boolean,
    onSelect: () -> Unit
) {
    val contentColor = if (current) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = Modifier
                .width(
                    if (pillMode) RailWidth - RailInset * 2
                    else RailTotalWidth - RailInset * 2
                )
                .height(RailItemHeight)
                .then(if (pillMode) Modifier.clip(CircleShape) else Modifier)
                .graphicsLayer {
                    val s = scale()
                    scaleX = s
                    scaleY = s
                }
                // 页签自己是真正的点击入口：父级手势只管拖动，
                // 点击不会被 drag/onRelease 的状态机吞掉。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSelect() }
                .semantics(mergeDescendants = true) {
                    role = Role.Tab
                    selected = current
                },
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (current) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = stringResource(destination.labelRes)
            )
            Text(
                text = stringResource(destination.labelRes),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 贴边导航条：没有悬浮胶囊、没有选中药丸，整条贴屏幕边缘铺满高度。
 * 背景与卡片共用同一份采样源，因此同样是模糊玻璃，只压一层中性黑让它比
 * 卡片略深；没设背景图 / 不支持真模糊时退回半透明纯色。当前页只靠主色区分。
 */
@Composable
private fun PlainSideNavRail(
    tabs: List<NavDestination>,
    selectedIndex: () -> Int,
    onNavigate: (String) -> Unit,
    onSettleTab: ((Int) -> Unit)?,
    modifier: Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val cardBackdrop = LocalCardGlassBackdrop.current
    val cardBlurDp = LocalCardBlurDp.current
    // 同胶囊分支：导航条只留极轻磨砂，不跟着卡片模糊一路糊到 18dp。
    val radiusPx = with(density) { cardBlurDp.coerceIn(0f, 3f).dp.toPx() }
    val railBackdrop = cardBackdrop?.takeIf { cardBlurDp > 0f }
    val outline = scheme.outlineVariant.copy(alpha = 0.16f)

    val railSurface = if (railBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = railBackdrop,
            shape = { RectangleShape },
            effects = {
                vibrancy(1.15f)
                blur(radiusPx, radiusPx)
            },
            onDrawSurface = {
                // 贴边条比卡片略深一点即可，压太厚就成了一块灰板。
                drawRect(Color.Black.copy(alpha = 0.05f))
            }
        )
    } else {
        Modifier.background(scheme.surfaceContainer.copy(alpha = 0.16f))
    }

    val count = tabs.size
    val contentHeight = RailItemHeight * count + RailInset * 2
    val current = selectedIndex().coerceIn(0, count - 1)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(RailTotalWidth)
            .then(railSurface)
            .border(1.dp, outline, RectangleShape),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
            Column(
                modifier = Modifier
                    .width(RailTotalWidth - RailInset * 2)
                    .height(contentHeight)
                    .padding(vertical = RailInset),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                tabs.forEachIndexed { i, destination ->
                    RailItem(
                        destination = destination,
                        current = i == current,
                        scale = { 1f },
                        pillMode = false,
                        onSelect = {
                            val settle = onSettleTab
                            if (settle != null) settle(i)
                            else onNavigate(destination.route)
                        }
                    )
                }
            }
        }
    }
}
