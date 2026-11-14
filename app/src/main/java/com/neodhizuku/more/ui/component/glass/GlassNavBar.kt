package com.neodhizuku.more.ui.component.glass

import android.os.Build

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.ui.component.NavDestination
import com.neodhizuku.more.ui.component.liquid.lens
import com.neodhizuku.more.ui.component.liquid.rememberCombinedBackdrop
import com.neodhizuku.more.ui.component.liquid.vibrancy

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 底栏整体高度；药丸比它矮一圈，上下各留 4dp。 */
private val GlassBarHeight: Dp = 64.dp

/** 药丸高度，也是内容区高度。 */
private val GlassPillHeight: Dp = 56.dp

/** 底栏左右内缩，药丸在这个内缩区里滑动。 */
private val GlassInset: Dp = 4.dp

/** 页签图标尺寸。 */
private val GlassTabIconSize: Dp = 24.dp

/**
 * 药丸与 Pager 位置相差多少页以内，才允许切到「逐帧跟随」。
 * 取得小是为了避免跳变：刚拖完底栏时药丸在弹簧收敛中，与 Pager 差着好几页，
 * 此时直接跟随会让药丸瞬间跳到 Pager 的位置。
 */
private const val FOLLOW_ENTER_TOLERANCE = 0.06f

internal val LocalGlassTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }
internal val LocalGlassContentColor = staticCompositionLocalOf { Color.Unspecified }
internal val LocalGlassActiveLook = staticCompositionLocalOf { false }

/**
 * 液态玻璃底部选择栏，采样链与 KernelSU / NPatch 的 FloatingBottomBar 一致：
 *
 * 1. 底栏 Row：从页面层取背景，常驻 vibrancy + 4dp 模糊 + 24dp 边缘折射，
 *    并用 BloomStroke 描边；按下时整条底栏轻微鼓起。
 * 2. 不可见采样行：把「玻璃 + 主色页签」录进 [tabsBackdrop]，自身 alpha=0 不显示。
 * 3. 药丸：只从 [rememberCombinedBackdrop]（页面层 + 页签层）采样，
 *    effects 里只有随按压渐显的 lens，中心原样透出、边缘倒转；
 *    药丸内部不渲染任何可见内容，页签是采样进来的，所以不会有两份图标重影。
 *
 * 拖动状态仍沿用项目原有的 [DampedDragAnimation]：位置直接落位、逐帧跟随 Pager，
 * 保证跟手与瞬时切页；只把渲染结构换成参考实现。
 */
@Composable
fun GlassNavBar(
    tabs: List<NavDestination>,
    selectedIndex: () -> Int,
    onNavigate: (String) -> Unit,
    /** 连续页码（含小数），通常是 Pager 的 `currentPage + currentPageOffsetFraction`。 */
    selectedPosition: () -> Float = { selectedIndex().toFloat() },
    /** 拖动松手后让 Pager 立即对齐到落点。 */
    onSettleTab: ((Int) -> Unit)? = null,
    /** 玻璃底色透明度倍率。1f 为参考观感，调小更透、调大更实。 */
    backgroundTranslucency: Float = 1f,
    /** 页面层背景；为空或低版本时退回纯色药丸。 */
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    val isInDark = isSystemInDarkTheme()
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = scheme.onSurface
    val containerColor = MiuixTheme.colorScheme.surface
        .copy(alpha = 0.4f * backgroundTranslucency)
    val baseSurfaceColor = MiuixTheme.colorScheme.surface
    // 页面过渡时页面层会压一层暗 scrim，被 vibrancy 拉浓后底栏会「突然变黑」。
    // 底色透明度在过渡里走一段渐变，而不是逐帧跟着暗层突变。
    val surfaceAlpha = remember { Animatable(0.4f) }
    // 页面过渡渐变的唯一持有者：跨页密集时先取消上一段，避免多个 tween
    // 同时写同一个 Animatable 互相取消，底色就会乱跳成「突然变黑」。
    var surfaceAlphaJob by remember { mutableStateOf<Job?>(null) }
    val pillShape = remember { CircleShape }

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    // miuix-blur 依赖 RuntimeShader（API 33 起），低版本退回纯色药丸。
    val miuixBackdrop =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) backdrop else null
    val useGlass = miuixBackdrop != null

    // 不可见采样行：只录「玻璃 + 主色页签」，不铺黑底、不叠页面内容。
    val tabsBackdrop = rememberLayerBackdrop()
    // 药丸从页面层 + 页签层一起取样，边缘折射才会倒转底栏与其上下背景。
    val combinedBackdrop = if (miuixBackdrop != null) {
        rememberCombinedBackdrop(miuixBackdrop, tabsBackdrop)
    } else {
        null
    }

    val scope = rememberCoroutineScope()
    val count = tabs.size
    val maxIndex = (count - 1).coerceAtLeast(0).toFloat()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * sign(fraction) * EaseOut.transform(abs(fraction))
            }
        }
    }

    val initialIndex = selectedIndex().coerceIn(0, count - 1)
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    val selectedPositionUpdated by rememberUpdatedState(selectedPosition)
    val onNavigateUpdated by rememberUpdatedState(onNavigate)
    val onSettleTabUpdated by rememberUpdatedState(onSettleTab)
    val gestureIndices = remember { IntArray(2) }
    val dragPosBox = remember { floatArrayOf(0f) }

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val insetPx = with(density) { GlassInset.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - insetPx) / tabWidthPx).toInt().coerceIn(0, count - 1)
    }

    val drag = remember(scope, count, density, isLtr) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..maxIndex,
            canDrag = { position -> position.x in 0f..totalWidthPx },
            onDragStarted = { position ->
                gestureIndices[0] = currentIndex
                gestureIndices[1] = indexAt(position.x)
                dragPosBox[0] = currentIndex.toFloat()
            },
            onDragStopped = {
                val target = if (didDrag) {
                    dragPosBox[0].roundToInt().coerceIn(0, count - 1)
                } else {
                    gestureIndices[1]
                }
                if (currentIndex != target) {
                    currentIndex = target
                    NeoLog.d(NeoLog.NAV) { "底栏(玻璃) 选择 -> ${tabs[target].route} ($target)" }
                    this.snapTo(target.toFloat())
                    val settle = onSettleTabUpdated
                    if (settle != null) settle(target)
                    else onNavigateUpdated(tabs[target].route)
                } else {
                    this.snapTo(target.toFloat())
                }
                scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDragCancelled = {
                currentIndex = gestureIndices[0]
                this.snapTo(gestureIndices[0].toFloat())
                scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    val delta = dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f
                    dragPosBox[0] = (dragPosBox[0] + delta).coerceIn(0f, maxIndex)
                    this.dragTo(dragPosBox[0])
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            },
            onTap = { }
        )
    }

    // 逐帧跟随 Pager 的连续页码，避免对每个整数页重启动画导致中间页停住。
    LaunchedEffect(drag, count) {
        var aligned = false
        var following = false
        snapshotFlow {
            selectedPositionUpdated().coerceIn(0f, maxIndex)
        }.collectLatest { pos ->
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
            if (!following && abs(drag.value - pos) > FOLLOW_ENTER_TOLERANCE) {
                return@collectLatest
            }
            following = true
            val index = pos.roundToInt().coerceIn(0, count - 1)
            if (index != currentIndex) {
                currentIndex = index
                // 跨页只给缩放反馈，位置仍由下面的 followToSync 逐帧写入。
                // 节流统一放在 feedback() 内部（onSelect 与跨页共用），
                // 避免同一次点击两次调用互相 cancel，把 Q 弹掐断。
                drag.feedback()
                surfaceAlphaJob?.cancel()
                surfaceAlphaJob = scope.launch {
                    surfaceAlpha.animateTo(0.22f, tween(150))
                    surfaceAlpha.animateTo(0.4f, tween(220))
                }
            }
            drag.followToSync(pos)
        }
    }

    // 高光主光源跟着设备重力旋转；底栏与药丸各偏一个角度。
    val tilt = rememberDeviceTilt()
    val baseHighlight = rememberGravityRotatedHighlight(GlassIndicatorSpecular, tilt, -45f)
    val pillHighlight = rememberGravityRotatedHighlight(GlassIndicatorSpecular, tilt, 90f)

    // 按压处光斑：只在真玻璃分支存在。
    val interactiveHighlight = if (useGlass) {
        remember(scope, tabWidthPx, drag) {
            GlassInteractiveHighlight(
                animationScope = scope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (drag.value + 0.5f) * tabWidthPx + panelOffset
                        else size.width - (drag.value + 0.5f) * tabWidthPx + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }
    } else {
        null
    }

    // 药丸相对底栏起点的位移。RTL 下页码方向镜像。
    val progressOffsetPx: () -> Float = { drag.value * tabWidthPx }

    // 底栏与不可见采样行共用同一份页签内容：靠 CompositionLocal 切换主色与图标。
    val tabsContent: @Composable RowScope.() -> Unit = {
        val scale = LocalGlassTabScale.current
        tabs.forEachIndexed { index, destination ->
            GlassTab(
                destination = destination,
                current = index == currentIndex,
                scale = scale,
                onSelect = {
                    NeoLog.d(NeoLog.NAV) { "底栏(玻璃) 点击 -> ${destination.route}" }
                    // 点击只触发缩放反馈；位置由 Pager 连续页码驱动，始终跟手。
                    drag.feedback()
                    onNavigateUpdated(destination.route)
                },
            )
        }
    }

    Box(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        // ── 1. 底栏：真实玻璃 + 普通色页签 ──────────────────────────────
        CompositionLocalProvider(
            LocalGlassTabScale provides { 1f },
            LocalGlassContentColor provides tabContentColor,
            LocalGlassActiveLook provides false
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        val contentPx = totalWidthPx - with(density) { (GlassInset * 2).toPx() }
                        tabWidthPx = (contentPx / count).coerceAtLeast(0f)
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .shadow(elevation = 10.dp, shape = pillShape, clip = false)
                    .then(
                        if (miuixBackdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = miuixBackdrop,
                                shape = { pillShape },
                                effects = {
                                    padding = maxOf(padding, 40.dp.toPx())
                                    vibrancy(1.15f)
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    lens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx(),
                                    )
                                },
                                highlight = { baseHighlight.copy(alpha = 0.75f) },
                                layerBlock = {
                                    val width = size.width.coerceAtLeast(1f)
                                    val s = lerp(
                                        1f,
                                        1f + 16.dp.toPx() / width,
                                        drag.pressProgress,
                                    )
                                    scaleX = s
                                    scaleY = s
                                },
                                onDrawSurface = {
    drawRect(baseSurfaceColor.copy(alpha = surfaceAlpha.value * backgroundTranslucency))
},
                            )
                        } else {
                            Modifier.background(containerColor, pillShape)
                        }
                    )
                    .then(
                        if (interactiveHighlight != null) {
                            interactiveHighlight.modifier
                                .then(interactiveHighlight.gestureModifier)
                        } else {
                            Modifier
                        }
                    )
                    .then(drag.modifier)
                    .height(GlassBarHeight)
                    .padding(GlassInset),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent
            )
        }

        // ── 2. 不可见采样行：把玻璃 + 主色页签录进 tabsBackdrop ──────────
        if (miuixBackdrop != null) {
            CompositionLocalProvider(
                // 采样行里的页签固定 1f：它属于「药丸后面的内容」，随按压
                // 1.0↔1.2 缩放会与底栏可见图标错开，透过折射看就是按钮在颤抖。
                LocalGlassTabScale provides { 1f },
                LocalGlassContentColor provides accentColor,
                LocalGlassActiveLook provides true
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics { }
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = miuixBackdrop,
                            shape = { pillShape },
                            effects = {
                                padding = maxOf(padding, 40.dp.toPx())
                                vibrancy(1.15f)
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(
                                    refractionHeight = 24.dp.toPx(),
                                    refractionAmount = 24.dp.toPx(),
                                )
                            },
                            onDrawSurface = {
    drawRect(baseSurfaceColor.copy(alpha = surfaceAlpha.value * backgroundTranslucency))
},
                        )
                        .then(interactiveHighlight?.modifier ?: Modifier)
                        .height(GlassPillHeight)
                        .padding(horizontal = GlassInset),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent
                )
            }
        }

        // ── 3. 药丸：只折射，不渲染可见内容 ─────────────────────────────
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val contentWidthDp = with(density) {
                (totalWidthPx - (GlassInset * 2).toPx()).toDp()
            }

            if (combinedBackdrop != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = GlassInset)
                        .graphicsLayer {
                            val offset = progressOffsetPx()
                            translationX = if (isLtr) offset + panelOffset
                            else -offset + panelOffset
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = drag.pressProgress
                                lens(
                                    refractionHeight = 10.dp.toPx() * progress,
                                    refractionAmount = 14.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = { pillHighlight.copy(alpha = drag.pressProgress) },
                            layerBlock = {
                                scaleX = drag.scaleX
                                scaleY = drag.scaleY
                                val velocity = drag.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = drag.pressProgress
                                drawRect(
                                    color = if (!isInDark) Color.Black.copy(alpha = 0.1f)
                                    else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .glassInnerShadow(shape = pillShape) {
                            GlassInnerShadow(
                                radius = 8.dp * drag.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = drag.pressProgress,
                            )
                        }
                        .height(GlassPillHeight)
                        .width(tabWidthDp)
                )
            } else {
                // 非玻璃兜底：主色药丸 + 可见内容反向平移（与参考的 None 分支一致）。
                Box(
                    modifier = Modifier
                        .padding(horizontal = GlassInset)
                        .graphicsLayer {
                            val offset = progressOffsetPx()
                            translationX = if (isLtr) offset + panelOffset
                            else -offset + panelOffset
                        }
                        .clip(pillShape)
                        .background(accentColor.copy(alpha = 0.15f), pillShape)
                        .height(GlassPillHeight)
                        .width(tabWidthDp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    CompositionLocalProvider(
                        LocalGlassTabScale provides { 1f },
                        LocalGlassContentColor provides accentColor,
                        LocalGlassActiveLook provides true
                    ) {
                        Row(
                            modifier = Modifier
                                .clearAndSetSemantics { }
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(contentWidthDp)
                                .height(GlassPillHeight)
                                .graphicsLayer {
                                    val offset = progressOffsetPx()
                                    translationX = if (isLtr) -offset else offset
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            content = tabsContent
                        )
                    }
                }
            }
        }
    }
}

/** 单个页签。底栏与采样行各渲染一份，都用 `weight(1f)` 均分，宽度完全一致。 */
@Composable
private fun RowScope.GlassTab(
    destination: NavDestination,
    current: Boolean,
    scale: () -> Float,
    onSelect: () -> Unit
) {
    val activeLook = LocalGlassActiveLook.current
    val contentColor = LocalGlassContentColor.current
    val tint = if (contentColor == Color.Unspecified) {
        LocalContentColor.current
    } else {
        contentColor
    }

    Column(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = current
                onClick {
                    onSelect()
                    true
                }
            }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (activeLook) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = stringResource(destination.labelRes),
            tint = tint,
            modifier = Modifier.size(GlassTabIconSize)
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
