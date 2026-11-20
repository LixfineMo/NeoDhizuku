package com.neodhizuku.more.ui.component

import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 底栏整体高度，药丸比它矮一圈，上下各留出 4dp。 */
private val NavBarHeight: Dp = 64.dp

/** 选中指示器的高度，也是内容区高度。 */
private val NavPillHeight: Dp = 56.dp

/** 底栏左右的内缩，药丸就在这个内缩区里滑动。 */
private val NavBarInset: Dp = 4.dp

/** 按压时药丸整体放大的幅度。 */
private const val PILL_PRESS_SCALE = 0.08f

/** 速度增益：把「每帧移动了多少页」换算成挤压拉伸的强度。 */
private const val PILL_VELOCITY_GAIN = 2.2f

/**
 * 药丸与 Pager 位置相差多少页以内，才允许切到「逐帧跟随」。
 * 取得小是为了避免跳变：刚拖完底栏时药丸在弹簧收敛中，与 Pager 差着好几页，
 * 此时直接跟随会让药丸瞬间跳到 Pager 的位置。
 */
private const val FOLLOW_ENTER_TOLERANCE = 0.06f

/** 底部悬浮导航栏的四个目标页。 */
enum class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** 仅在已激活时显示（设备管理员也算已激活） */
    val requiresOwner: Boolean = false
) {
    Home(
        route = "home",
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    AppManagement(
        route = "app_management",
        labelRes = R.string.nav_app_management,
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
        requiresOwner = true
    ),
    CallLog(
        route = "call_log",
        labelRes = R.string.nav_call_log,
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List
    ),
    Settings(
        route = "settings",
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

/**
 * 悬浮底栏：整条是胶囊形玻璃，选中项由一个**完整包住图标和文字**的药丸指示。
 * 药丸里放的是**整条页签**，按药丸自身的位移反向平移，因此药丸里的每一项
 * 与底栏里同一项逐像素对齐；底栏那一份按「被药丸覆盖的比例」连续淡出，
 * 两者叠加的总墨量恒定，拖动/翻页途中不会出现空位或重影。
 * 药丸表面先铺一层不透明的容器色再叠主色，底栏内容不会从玻璃里透出来。
 * 手势：在底栏任意位置按下即预览，横向拖动连续跟手，松手落到最近的一页。
 * 位移与缩放全部走 `offset {}` / `graphicsLayer {}` 延迟读取，拖动中不产生重组。
 */
@Composable
fun FloatingNavBar(
    tabs: List<NavDestination>,
    selectedIndex: () -> Int,
    onNavigate: (String) -> Unit,
    /**
     * 连续页码（含小数），通常是 Pager 的 `currentPage + currentPageOffsetFraction`。
     * 药丸逐帧跟随这个值，而不是对每个整数页重启动画 —— 后者在跨多页翻页时
     * 会依次经过中间页、每次都打断上一次动画，看起来就是一顿一顿的「卡」。
     */
    selectedPosition: () -> Float = { selectedIndex().toFloat() },
    /** 拖动松手后让 Pager 立即对齐到落点，避免药丸与 Pager 位置对不上。 */
    onSettleTab: ((Int) -> Unit)? = null,
    liquidGlass: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    // 背景图模式下 surface 是透明的（RGB 全 0），改用不透明容器色判断
    val surface = scheme.surfaceContainerHigh
    // 用背景亮度判断深浅，避免把 isSystemInDarkTheme 一路传进来
    val dark = (surface.red + surface.green + surface.blue) < 1.5f
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()

    val count = tabs.size
    val maxIndex = (count - 1).coerceAtLeast(0).toFloat()

    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val tabWidthPx by remember(totalWidthPx, count) {
        derivedStateOf {
            val inner = totalWidthPx - with(density) { (NavBarInset * 2).toPx() }
            if (inner <= 0f) 0f else inner / count
        }
    }

    // 初值取当前页而不是写死 0：底栏进入二级页面时会被整块移出组合，
    // 返回后 remember 重建，写死 0 会让药丸停在第一页。
    val drag = remember(scope, count) {
        NavBarDragState(
            scope = scope,
            initialValue = selectedIndex().coerceIn(0, count - 1).toFloat(),
            valueRange = 0f..maxIndex,
            touchSlop = with(density) { 6.dp.toPx() }
        )
    }

    // 按下位置落在第几页。只在松手瞬间读一次，用数组装避免触发重组。
    val pressIndexBox = remember { intArrayOf(0) }
    // 拖动落点用本地累计值，不读异步写入的 Animatable.targetValue。
    val dragPosBox = remember { floatArrayOf(0f) }

    val selectedPositionState by rememberUpdatedState(selectedPosition)
    val onNavigateState by rememberUpdatedState(onNavigate)
    val onSettleTabState by rememberUpdatedState(onSettleTab)
    var currentIndex by remember { mutableIntStateOf(selectedIndex().coerceIn(0, count - 1)) }

    // 逐帧跟随 Pager 的连续位置，页面与药丸共用同一个驱动源。
    LaunchedEffect(drag, count) {
        // 首帧必须无条件对齐一次：下面只在「位置与已知位置不同」时才动，
        // 而组合重建时 currentIndex 的初值已经等于当前页，会被直接跳过。
        var aligned = false
        // 是否处于「逐帧跟随 Pager」状态。进入要严格：偏差足够小才接手，
        // 否则刚拖完底栏（药丸正在弹簧收敛）会被瞬间拽到 Pager 的位置。
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
                    // 药丸还差得远（刚拖完在收敛），先让它自己追上来
                    return@collectLatest
                }
                following = true
                val index = pos.roundToInt().coerceIn(0, count - 1)
                if (index != currentIndex) currentIndex = index
                drag.followToSync(pos)
            }
    }

    // 手势回调依赖会变的量，依赖变化时重装即可
    LaunchedEffect(drag, tabWidthPx, isLtr, count, tabs) {
        drag.onPress = { position ->
            // 页面瞬时切换，药丸由 Pager 位置驱动。
            // 慢弹簧预览是「切页要等半秒」的观感来源之一。
            pressIndexBox[0] = indexAt(
                position.x, tabWidthPx, count, isLtr, totalWidthPx,
                with(density) { NavBarInset.toPx() }
            )
            dragPosBox[0] = currentIndex.toFloat()
        }
        drag.onMove = { amount ->
            if (tabWidthPx > 0f && amount.x != 0f) {
                val delta = amount.x / tabWidthPx * if (isLtr) 1f else -1f
                dragPosBox[0] = (dragPosBox[0] + delta).coerceIn(0f, maxIndex)
                // snapTo 直接落位，避免每帧重启弹簧或起 velocity 协程。
                drag.dragTo(dragPosBox[0])
            }
        }
        drag.onRelease = { cancelled ->
            val current = currentIndex.coerceIn(0, count - 1)
            val target = when {
                cancelled -> current
                !drag.didDrag -> pressIndexBox[0]
                else -> dragPosBox[0].roundToInt()
            }.coerceIn(0, count - 1)
            if (!cancelled && target != current) {
                currentIndex = target
                NeoLog.d(NeoLog.NAV) { "底栏选择 -> ${tabs[target].route} ($target)" }
                // 页面与药丸都瞬时对齐。
                drag.land(target.toFloat())
                val settle = onSettleTabState
                if (settle != null) settle(target)
                else onNavigateState(tabs[target].route)
            } else {
                drag.land(current.toFloat())
            }
        }
    }

    // 橡皮筋位移：底栏整条与药丸一起承担，两者的相对位置不变
    val shiftPx: () -> Float = {
        if (totalWidthPx <= 0f) {
            0f
        } else {
            val frac = (drag.shift.value / totalWidthPx).coerceIn(-1f, 1f)
            with(density) { 4.dp.toPx() } * sign(frac) * EaseOut.transform(abs(frac))
        }
    }

    // 药丸相对底栏左端的位移。RTL 下第 0 项在最右，页码要镜像。
    val pillBasePx: () -> Float = {
        val v = drag.value.value
        (if (isLtr) v else (maxIndex - v)) * tabWidthPx
    }
    val pillOffsetPx: () -> Float = { pillBasePx() + shiftPx() }
    // 药丸里的整条页签反向平移同样的量，位置才能和底栏逐像素对齐
    val stripOffsetPx: () -> Float = { -pillBasePx() }

    // 第 i 项被药丸覆盖的比例：药丸中心离它越近接管得越多。
    // 用 smoothstep 让两端收得更快，中途仍保持对称。
    val coverageAt: (Int) -> Float = { index ->
        val c = (1f - abs(drag.value.value - index.toFloat())).coerceIn(0f, 1f)
        c * c * (3f - 2f * c)
    }

    val barSurface: Modifier = if (liquidGlass) {
        Modifier
            .shadow(elevation = 16.dp, shape = CircleShape, clip = false)
            .glassSurface(
                // 明显低于不透明：滚动时能看到内容从底栏后面穿过，这是玻璃和实心最直观的区别。
                // 与真玻璃底栏同一套通透口径：薄底色 + 淡高光，不糊成一块雾面板。
                tintTop = scheme.surfaceContainer.copy(alpha = if (dark) 0.20f else 0.26f),
                tintBottom = scheme.surfaceContainer.copy(alpha = if (dark) 0.13f else 0.18f),
                specularAlpha = if (dark) 0.26f else 0.44f,
                rimAlpha = if (dark) 0.08f else 0.14f,
                shadeAlpha = if (dark) 0.12f else 0.04f,
                borderColors = glassBorderColors(dark).map {
                    it.copy(alpha = it.alpha * 0.45f)
                }
            )
    } else {
        Modifier
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
    }

    val inactiveContent = scheme.onSurfaceVariant
    val activeContent = scheme.primary
    // 不做图标放大：与玻璃底栏一致，只保留边缘效果。
    val tabScale: () -> Float = { 1f }

    Box(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        CompositionLocalProvider(LocalContentColor provides inactiveContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { totalWidthPx = it.size.width.toFloat() }
                    .offset { IntOffset(shiftPx().roundToInt(), 0) }
                    .then(barSurface)
                    .then(drag.modifier)
                    .height(NavBarHeight)
                    .padding(NavBarInset),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, destination ->
                    NavTab(
                        destination = destination,
                        current = index == currentIndex,
                        activeLook = false,
                        scale = tabScale,
                        // 被药丸接管的那一份按比例让出去，任何时刻都不会凭空消失
                        alpha = { 1f - coverageAt(index) },
                        modifier = Modifier.weight(1f)
                    ) {
                        NeoLog.d(NeoLog.NAV) { "底栏点击 -> ${destination.route}" }
                        onNavigateState(destination.route)
                    }
                }
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            // 直接用内容区总宽，避免"除以页数再乘回去"的浮点累积误差
            val contentWidthDp = with(density) { (totalWidthPx - (NavBarInset * 2).toPx()).toDp() }

            // 药丸底色只能铺很淡一层：铺不透明容器色就等于盖了一块板子，
            // 背后什么都透不出来，折射与模糊都看不见。
            // 玻璃感来自上面几层，底栏内容不会从这里透出来形成重影。
            val pillSurface: Modifier = if (liquidGlass) {
                Modifier
                    .background(
                        scheme.surfaceContainer.copy(alpha = if (dark) 0.10f else 0.14f),
                        CircleShape
                    )
                    .glassSurface(
                        tintTop = scheme.primary.copy(alpha = if (dark) 0.16f else 0.12f),
                        tintBottom = scheme.primary.copy(alpha = if (dark) 0.10f else 0.07f),
                        specularAlpha = if (dark) 0.24f else 0.42f,
                        rimAlpha = if (dark) 0.06f else 0.12f,
                        shadeAlpha = if (dark) 0.10f else 0.03f,
                        borderColors = glassBorderColors(dark).map {
                            it.copy(alpha = it.alpha * 0.45f)
                        },
                        specularHeight = 2.dp,
                        pressProgress = { drag.press.value }
                    )
            } else {
                Modifier.background(scheme.secondaryContainer, CircleShape)
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = NavBarInset)
                    .offset { IntOffset(pillOffsetPx().roundToInt(), 0) }
                    .height(NavPillHeight)
                    .width(tabWidthDp)
                    .clip(CircleShape),
                contentAlignment = Alignment.CenterStart
            ) {
                // 气泡本体：按压时整体放大
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val pressScale = 1f + PILL_PRESS_SCALE * drag.press.value
                            // 速度驱动的挤压拉伸：移动越快横向拉得越长、纵向压得越扁，
                            // 停下瞬间回弹 —— 就是药丸左右移动时那点「果冻感」。
                            val v = (drag.velocity.value * PILL_VELOCITY_GAIN).coerceIn(-0.2f, 0.2f)
                            scaleX = pressScale / (1f - v * 0.75f)
                            scaleY = pressScale * (1f - v * 0.25f)
                        }
                        .then(pillSurface)
                )

                // 气泡内容：整条页签反向平移，与底栏逐像素对齐。
                // wrapContentWidth(unbounded = true) 是必需的：它把测量约束的
                // maxWidth 放开，requiredWidth 才能真正撑到整条页签的宽度。
                // 少了它，Row 会被父级（药丸只有一页宽）钳制，四个页签挤成一页宽，
                // 于是拖动到后面的页时药丸里就整段平移出可视区、变成空白。
                CompositionLocalProvider(LocalContentColor provides activeContent) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics { }
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .requiredWidth(contentWidthDp)
                            .height(NavPillHeight)
                            .graphicsLayer { translationX = stripOffsetPx() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { destination ->
                            NavTab(
                                destination = destination,
                                current = true,
                                activeLook = true,
                                scale = tabScale,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * indexAt
 * (x: Float, tabWidthPx: Float, count: Int, isLtr: Boolean, totalWidthPx: Float, insetPx: Float)
 * 作用：按下位置落在第几页。RTL 下横坐标要镜像。
 * 备注：仅内部使用
 */
private fun indexAt(
    x: Float,
    tabWidthPx: Float,
    count: Int,
    isLtr: Boolean,
    totalWidthPx: Float,
    insetPx: Float
): Int {
    if (tabWidthPx <= 0f) return 0
    val logicalX = if (isLtr) x else totalWidthPx - x
    return ((logicalX - insetPx) / tabWidthPx).toInt().coerceIn(0, count - 1)
}

/**
 * NavTab
 * (destination: NavDestination, current: Boolean = false, activeLook: Boolean = false, scale: () -> Float = { 1f }, alpha: () -> Float = { 1f }, modifier: Modifier = Modifier, onSelect: (() -> Unit)? = null)
 * 作用：单个页签。底栏与药丸里各渲染一份，都用 `weight(1f)` 均分，宽度完全一致。 `onSelect` 为空表示这是药丸内的镜像内容，不参与无障碍语义。 图标只由 [activeLook] 决定：底栏一份恒用描边图标，药丸一份恒用填充图标， 中途半覆盖时不会出现同一项两种描边混在一起。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun NavTab(
    destination: NavDestination,
    current: Boolean = false,
    activeLook: Boolean = false,
    scale: () -> Float = { 1f },
    alpha: () -> Float = { 1f },
    modifier: Modifier = Modifier,
    onSelect: (() -> Unit)? = null
) {
    val semanticsModifier = if (onSelect != null) {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Tab
            selected = current
            onClick {
                onSelect()
                true
            }
        }
    } else {
        Modifier.clearAndSetSemantics { }
    }

    Column(
        modifier = modifier
            .then(semanticsModifier)
            .fillMaxHeight()
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
                this.alpha = alpha()
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (activeLook) destination.selectedIcon else destination.unselectedIcon,
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
