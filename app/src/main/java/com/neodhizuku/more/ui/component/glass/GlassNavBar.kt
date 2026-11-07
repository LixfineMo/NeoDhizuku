package com.neodhizuku.more.ui.component.glass

import android.os.Build

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.neodhizuku.more.ui.component.liquid.lens
import com.neodhizuku.more.ui.component.liquid.rememberCombinedBackdrop
import com.neodhizuku.more.ui.component.liquid.vibrancy

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.ui.component.NavDestination
import com.neodhizuku.more.ui.component.glassBorderColors

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 底栏整体高度；药丸比它矮一圈，上下各留 4dp。 */
private val GlassBarHeight: Dp = 54.dp

/** 药丸高度，也是内容区高度。 */
private val GlassPillHeight: Dp = 46.dp

/** 底栏左右内缩，药丸在这个内缩区里滑动。 */
private val GlassInset: Dp = 4.dp

/** 页签图标尺寸，随底栏一起收小。 */
private val GlassTabIconSize: Dp = 22.dp

/** 按压时药丸的额外放大比例。 */
private const val PILL_PRESS_SCALE = 0.10f

/** 速度换算成形变量的倍率，越大变形越明显。 */
private const val PILL_VELOCITY_GAIN = 0.1f

/**
 * 药丸与 Pager 位置相差多少页以内，才允许切到「逐帧跟随」。
 *
 * 取得小是为了避免跳变：刚拖完底栏时药丸在弹簧收敛中，与 Pager 差着好几页，
 * 此时直接跟随会让药丸瞬间跳到 Pager 的位置。
 */
private const val FOLLOW_ENTER_TOLERANCE = 0.06f

/**
 * 「暂停跟随」的最长时长。超过它说明页面那边没有如期滑动，此时强制接手，
 * 让药丸回到页面所在位置，保证两者最终一致。
 */
private const val FOLLOW_BLOCK_TIMEOUT_MS = 1_200L

internal val LocalGlassTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }
internal val LocalGlassContentColor = staticCompositionLocalOf { Color.Unspecified }

/**
 * 液态玻璃底部选择栏。
 *
 * 与 [com.neodhizuku.more.ui.component.FloatingNavBar] 是两套并存的实现：
 * 这一套的手势是「按下即预览 + 阻尼弹簧跟手 + 松手落位」，位移用
 * stiffness 1000 的高刚度弹簧收敛，缩放的横纵阻尼不同（0.6 / 0.7），
 * 回弹时带轻微横向拉伸；玻璃高光随设备姿态移动。
 *
 * 药丸里放整条页签并反向平移，与底栏每一项逐像素对齐；底栏那一份按
 * 「被药丸覆盖的比例」连续淡出，两者墨量互补，拖动途中不会空位或重影。
 *
 * 玻璃完全由 [glassPanel] 自绘：底色压到极低让背后页面直接透出来，
 * 再叠顶沿高光、边缘折射、色散与内阴影形成厚度感。
 * 没有引入外部模糊库——真实背景采样需要 GPU 侧离屏抓取，
 * 自绘方案在任意设备上表现一致，不依赖驱动能力。
 */
@Composable
fun GlassNavBar(
    tabs: List<NavDestination>,
    selectedIndex: () -> Int,
    onNavigate: (String) -> Unit,
    /**
     * 连续页码（含小数），通常是 Pager 的 `currentPage + currentPageOffsetFraction`。
     *
     * 药丸逐帧跟随这个值，而不是对每个整数页重启动画 —— 后者会让药丸
     * 先收敛到中间页并停住，等下一个整数页到来才继续，看起来就是"卡一下"。
     */
    selectedPosition: () -> Float = { selectedIndex().toFloat() },
    /** 拖动松手后让 Pager 立即对齐到落点，避免药丸与 Pager 位置对不上。 */
    onSettleTab: ((Int) -> Unit)? = null,
    /**
     * 玻璃背景透明度倍率。1f 为默认观感，调小更透、调大更实。
     *
     * 玻璃质感靠 [glassPanel] 自绘得到：极低透明度让背后页面直接透出来，
     * 再叠顶端高光、边缘折射、色散与内阴影形成厚度感。
     */
    backgroundTranslucency: Float = 1f,
    /**
     * miuix-blur 的背景层。传了就走「真实背景采样 + 折射」，
     * 需要调用方在页面内容上挂 `Modifier.layerBackdrop(backdrop)`；
     * 传 null 则退回纯自绘（任意设备表现一致）。
     */
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    val dark = (scheme.surface.red + scheme.surface.green + scheme.surface.blue) < 1.5f
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    // miuix-blur 内部依赖 RuntimeShader（API 33 起）。低版本一律退回自绘，
    // 否则访问不到 android.graphics.RuntimeShader 会直接崩溃。
    val miuixBackdrop =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) backdrop else null
    val useMiuix = miuixBackdrop != null

    // 药丸内部内容的采样层：图标与文字先被录取到这里，再被药丸折射出去。
    // 药丸里"能看见内容被扭曲"就是这么来的：不是画上去的，是折射出来的。
    val tabsBackdrop = if (useMiuix) rememberLayerBackdrop() else null

    // 药丸要透出两层：自己的内容 + 页面内容。
    val combinedBackdrop: Backdrop? =
        if (miuixBackdrop != null && tabsBackdrop != null)
            // 顺序不能反：页面层先铺底（它带一张不透明的 surface 兜底矩形），
            // 页签层再叠在上面。反过来的话页签会被那张不透明矩形整个盖住，
            // 折射就只剩一片纯色 —— 这正是「药丸里看不到折射」的直接原因。
            rememberCombinedBackdrop(miuixBackdrop, tabsBackdrop)
        else null

    // 折射由 miuix 画在下层，自绘的每一层都必须再压一档，否则会把折射成果盖住。
    // 这里不只压底色：高光 / 边缘 / 内阴影 / 色散同样是铺满整块面板画上去的，
    // 原样叠加等于在真折射上糊一层自绘的高光 —— 那就是"开了折射却看不到"。
    val selfAlphaScale = if (useMiuix) 0.34f else 1f

    LaunchedEffect(useMiuix) {
        NeoLog.d(NeoLog.NAV) {
            "底栏(玻璃) 折射源 miuix=$useMiuix 页面层=${miuixBackdrop != null} " +
                    "页签层=${tabsBackdrop != null}"
        }
    }

    val scope = rememberCoroutineScope()

    val count = tabs.size
    val maxIndex = (count - 1).coerceAtLeast(0).toFloat()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    // 拖到边界时整条底栏的橡皮筋位移
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
    // 纯点击松手后暂停「逐帧跟随 Pager」：此时药丸已因按下预览而领先，
    // 若立刻接管会把药丸拽回旧位置重滑，看着就是跳一下。等页面追上来再接手。
    val followBlock = remember { booleanArrayOf(false) }
    /** [followBlock] 的起始时刻，超时后无条件解除，避免跟随被永久阻塞。 */
    val followBlockAt = remember { longArrayOf(0L) }

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
                // 按下即预览：手指一落药丸就开始移动，点击才是"跟手"的。
                // 必须用与 Pager 翻页同规格的慢弹簧（stiffness 200），不能用
                // 高刚度那根（1000，约 150ms 到位）—— 后者药丸先到、页面才
                // 开始滑，正是「药丸瞬移、页面慢半拍」的来源。
                this.glideTo(gestureIndices[1].toFloat())
            },
            onDragStopped = {
                // 拖动过：落点取拖动位置；纯点击：落点是按下时算出的那一页。
                // 不能用 targetValue —— 按下不预览后它仍停在当前页。
                val target = if (didDrag) {
                    targetValue.roundToInt().coerceIn(0, count - 1)
                } else {
                    gestureIndices[1]
                }
                if (currentIndex != target) {
                    currentIndex = target
                    NeoLog.d(NeoLog.NAV) { "底栏(玻璃) 拖动 -> ${tabs[target].route} ($target)" }
                    val settle = onSettleTabUpdated
                    if (settle != null && didDrag) {
                        // 拖动过：页面没跟着动，药丸自己落位、页面直接对齐，
                        // 不再滑一次——否则 Pager 会从旧页一路滑过中间页
                        settle(target)
                        updateValue(target.toFloat())
                    } else {
                        // 纯点击：药丸在按下时已经用同规格慢弹簧起步，页面从现在
                        // 开始滑。中途不能让 Pager 的逐帧回传接管 —— 那会把已经
                        // 跑到目标页的药丸拽回旧位置重新滑，看起来是跳一下。
                        // 由跟随循环等页面追上来后再无缝接手。
                        followBlock[0] = true
                        followBlockAt[0] = System.currentTimeMillis()
                        onNavigateUpdated(tabs[target].route)
                    }
                } else {
                    updateValue(target.toFloat())
                }
                scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDragCancelled = {
                currentIndex = gestureIndices[0]
                updateValue(gestureIndices[0].toFloat())
                scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    val delta = dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f
                    updateValue((targetValue + delta).coerceIn(0f, maxIndex))
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            },
            onTap = { }
        )
    }

    // 首帧必须无条件对齐一次：下面只在「页码与已知页不同」时才动，
    // 而组合重建时 currentIndex 的初值已经等于当前页，会被直接跳过。
    LaunchedEffect(drag, count) {
        var aligned = false
        // 是否处于「逐帧跟随 Pager」状态。
        // 进入要严格：偏差足够小才接手，否则会看到药丸直接跳过去。
        // 进入后不再因帧间位移退出 —— 快速滚动时单帧位移并不小。
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
            // 纯点击松手后药丸领先于页面（按下即预览），此时若逐帧接管会把药丸
            // 拽回页面所在位置重滑。等页面追到几乎同位再接手，中间没有跳变。
            if (followBlock[0]) {
                // 页面若始终没动（极端情况），超时后也要解除并交回页面位置，
                // 否则 followBlock 会一直挡着，之后的页码变化药丸都不再跟随
                val expired =
                    System.currentTimeMillis() - followBlockAt[0] > FOLLOW_BLOCK_TIMEOUT_MS
                if (!expired && abs(drag.value - pos) > FOLLOW_ENTER_TOLERANCE) {
                    return@collectLatest
                }
                followBlock[0] = false
                following = true
            }
            if (!following && abs(drag.value - pos) > FOLLOW_ENTER_TOLERANCE) {
                // 药丸与 Pager 差得还远（刚拖完底栏，药丸正在弹簧收敛），
                // 让它先自己收敛，追上后下面的赋值会无缝接手
                return@collectLatest
            }
            following = true
            val index = pos.roundToInt().coerceIn(0, count - 1)
            if (index != currentIndex) currentIndex = index
            drag.followToSync(pos)
        }
    }

    val tilt = rememberDeviceTilt()
    val specularShift = tilt.directionX

    // 第 i 项被药丸覆盖的比例：药丸中心离它越近接管得越多。
    val coverageAt: (Int) -> Float = { index ->
        val c = (1f - abs(drag.value - index.toFloat())).coerceIn(0f, 1f)
        c * c * (3f - 2f * c)
    }

    // 药丸相对底栏左端的位移。RTL 下第 0 项在最右，页码要镜像。
    val pillBasePx: () -> Float = {
        val v = drag.value
        (if (isLtr) v else (maxIndex - v)) * tabWidthPx
    }
    val pillOffsetPx: () -> Float = { pillBasePx() + panelOffset }

    val tabScale: () -> Float = { 1f + 0.14f * drag.pressProgress }
    val inactiveContent = scheme.onSurfaceVariant
    val activeContent = scheme.primary

    // 底栏与药丸各渲染一份。inPill 为 true 时是药丸里的镜像内容：
    // 恒不透明、不参与无障碍语义，图标用填充版。
    val tabsContent: @Composable RowScope.(inPill: Boolean) -> Unit = { inPill ->
        val scale = LocalGlassTabScale.current
        tabs.forEachIndexed { index, destination ->
            GlassTab(
                destination = destination,
                current = index == currentIndex,
                activeLook = inPill,
                scale = scale,
                // 底栏那一份按被覆盖的比例让出去，药丸那一份恒为 1
                alpha = { if (inPill) 1f else 1f - coverageAt(index) },
                onSelect = {
                    NeoLog.d(NeoLog.NAV) { "底栏(玻璃) 点击 -> ${destination.route}" }
                    onNavigateUpdated(destination.route)
                }
            )
        }
    }

    Box(
        modifier = modifier
            // bottom 由 12.dp 提到 20.dp：离屏幕底边更远，配合上面的阴影更像悬浮
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        CompositionLocalProvider(
            LocalGlassTabScale provides tabScale,
            LocalGlassContentColor provides inactiveContent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        val contentPx = totalWidthPx - with(density) { (GlassInset * 2).toPx() }
                        tabWidthPx = (contentPx / count).coerceAtLeast(0f)
                        NeoLog.d(NeoLog.NAV) {
                            "底栏(玻璃) 测量 total=${totalWidthPx.roundToInt()} " +
                                    "tab=${tabWidthPx.roundToInt()} count=$count"
                        }
                    }
                    .offset { IntOffset(panelOffset.roundToInt(), 0) }
                    .shadow(elevation = 16.dp, shape = CircleShape, clip = false)
                    .then(
                        if (miuixBackdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = miuixBackdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(5.dp.toPx(), 5.dp.toPx())
                                    lens(
                                        // 折射强度随按压增强：平时只是淡淡一层，
                                        // 手指按下才明显起来 —— 这是"点起来才有折射"的来源。
                                        refractionHeight =
                                            (14f + 16f * drag.pressProgress).dp.toPx(),
                                        refractionAmount =
                                            (6f + 26f * drag.pressProgress).dp.toPx(),
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(scheme.surfaceContainer.copy(alpha = 0.10f))
                                },
                            )
                        } else {
                            Modifier
                        }
                    )
                    .glassPanel(
                        containerColor = scheme.surfaceContainer,
                        // 底色必须压到很低：观感上的"玻璃"来自背后页面透出来，
                        // 铺厚底色就变成一块实心板子。厚度感交给下面的
                        // 高光 / 边缘折射 / 色散 / 内阴影去表达。
                        containerAlpha = (if (dark) 0.16f else 0.22f) * backgroundTranslucency * selfAlphaScale,
                        // 真折射生效时，自绘的高光/色散/内阴影必须一起让路。
                        // 只压 containerAlpha 不够：这几层同样铺满整块面板，
                        // 原样画上去会把 miuix 折射出来的成果整个盖掉。
                        specularAlpha = (if (dark) 0.45f else 0.80f) * selfAlphaScale,
                        specularShift = specularShift,
                        rimAlpha = (if (dark) 0.14f else 0.26f) * selfAlphaScale,
                        shadeAlpha = (if (dark) 0.22f else 0.08f) * selfAlphaScale,
                        lensAlpha = (if (dark) 0.10f else 0.22f) * selfAlphaScale,
                        dispersionAlpha = (if (dark) 0.10f else 0.18f) * selfAlphaScale,
                        // miuix 的 vibrancy() 已经提过一次饱和，再提一次会发假
                        vibrancy = if (useMiuix) 1f else 1.35f,
                        borderColors = glassBorderColors(dark)
                    )
                    .then(drag.modifier)
                    .height(GlassBarHeight)
                    .padding(GlassInset),
                verticalAlignment = Alignment.CenterVertically,
                content = { tabsContent(false) }
            )
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val contentWidthDp = with(density) {
                (totalWidthPx - (GlassInset * 2).toPx()).toDp()
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = GlassInset)
                    .offset { IntOffset(pillOffsetPx().roundToInt(), 0) }
                    .height(GlassPillHeight)
                    .width(tabWidthDp)
                    .graphicsLayer {
                        val pressScale = 1f + PILL_PRESS_SCALE * drag.pressProgress
                        // 速度驱动的挤压拉伸：移动越快横向拉得越长、纵向压得越扁，
                        // 停下的瞬间回弹 —— 就是药丸左右移动时的那点"果冻感"。
                        val v = (drag.velocity * PILL_VELOCITY_GAIN).coerceIn(-0.2f, 0.2f)
                        scaleX = drag.scaleX * pressScale / (1f - v * 0.75f)
                        scaleY = drag.scaleY * pressScale * (1f - v * 0.25f)
                    }
                    .then(
                        if (combinedBackdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = combinedBackdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    // 模糊压到 3.5dp：模糊越强，折射出来的图标越糊，
                                    // 看着就是"折射不清晰"
                                    blur(3.5.dp.toPx(), 3.5.dp.toPx())
                                    lens(
                                        // 折射高度：从边缘往里算多少像素的带状区域参与折射。
                                        // 药丸高 46dp，位移在边缘最大、中心归零，所以按钮本体
                                        // 不会被放大，被折弯的是贴近轮廓的那一圈。
                                        refractionHeight =
                                            (16f + 14f * drag.pressProgress).dp.toPx(),
                                        // 折射量：边缘最大位移。随按压从 8dp 升到 38dp，
                                        // 平时只留很淡的一层，按下才明显"点起来"。
                                        refractionAmount =
                                            (8f + 30f * drag.pressProgress).dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = 0.5f,
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(scheme.surfaceContainer.copy(alpha = 0.10f))
                                },
                            )
                        } else {
                            Modifier
                        }
                    )
                    .glassPanel(
                        // 药丸比底栏更透一档，让"隔着玻璃看背后"的层次更明显。
                        // 防重影不靠不透明：底栏那一份已按覆盖比例淡出，
                        // 药丸透出来的只是底栏的淡影，不会形成清晰重影。
                        containerColor = scheme.surfaceContainer,
                        containerAlpha = (if (dark) 0.12f else 0.17f) * backgroundTranslucency * selfAlphaScale,
                        // 同底栏：真折射生效时自绘的这几层必须一起压下去，
                        // 否则折射的成果被自己的高光盖住，看上去就是"没有折射"。
                        specularAlpha = (if (dark) 0.55f else 0.90f) * selfAlphaScale,
                        specularShift = specularShift,
                        rimAlpha = (if (dark) 0.18f else 0.30f) * selfAlphaScale,
                        shadeAlpha = (if (dark) 0.20f else 0.10f) * selfAlphaScale,
                        lensAlpha = (if (dark) 0.16f else 0.34f) * selfAlphaScale,
                        dispersionAlpha = (if (dark) 0.16f else 0.28f) * selfAlphaScale,
                        vibrancy = if (useMiuix) 1f else 1.5f,
                        borderColors = glassBorderColors(dark),
                        pressProgress = { drag.pressProgress }
                    )
                    .glassInnerShadow(CircleShape) {
                        GlassInnerShadow(
                            radius = 8.dp * drag.pressProgress,
                            color = Color.Black.copy(alpha = 0.15f),
                            alpha = drag.pressProgress
                        )
                    }
                    .clip(CircleShape),
                contentAlignment = Alignment.CenterStart
            ) {
                // ① 采样层：整条页签画在完全透明的一行上，唯一目的是被录进
                //    tabsBackdrop。药丸边缘那圈"按钮被折弯"就来自这一层 ——
                //    它不是画上去的，是折射出来的。
                //
                //    alpha(0f) 必须写在 layerBackdrop 之前（即作为外层）：
                //    这样录取到的是不带透明度的原始内容，而这一层本身不可见。
                //    反过来的话录进去的就是一片全透明，等于没录。
                if (tabsBackdrop != null) {
                    CompositionLocalProvider(
                        LocalGlassTabScale provides { 1f },
                        LocalGlassContentColor provides activeContent
                    ) {
                        Row(
                            modifier = Modifier
                                .clearAndSetSemantics { }
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(contentWidthDp)
                                .height(GlassPillHeight)
                                .alpha(0f)
                                .layerBackdrop(tabsBackdrop)
                                .graphicsLayer { translationX = -pillBasePx() },
                            verticalAlignment = Alignment.CenterVertically,
                            content = { tabsContent(true) }
                        )
                    }
                }
                // ② 可见层：清晰、不放大，压在折射层之上。
                //    折射的位移在边缘最大、中心几乎为零（着色器里
                //    circleMap(1 - 距边距离/折射高度)），所以按钮本体不会变大，
                //    被"放大"的只有贴近药丸轮廓的那一圈。
                CompositionLocalProvider(
                    LocalGlassTabScale provides tabScale,
                    LocalGlassContentColor provides activeContent
                ) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics { }
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .requiredWidth(contentWidthDp)
                            .height(GlassPillHeight)
                            .graphicsLayer { translationX = -pillBasePx() },
                        verticalAlignment = Alignment.CenterVertically,
                        content = { tabsContent(true) }
                    )
                }
            }
        }
    }
}

/**
 * 单个页签。底栏与药丸里各渲染一份，都用 `weight(1f)` 均分，宽度完全一致。
 *
 * 图标只由 [activeLook] 决定：底栏一份恒用描边图标，药丸一份恒用填充图标，
 * 中途半覆盖时不会出现同一项两种描边混在一起。
 */
@Composable
private fun RowScope.GlassTab(
    destination: NavDestination,
    current: Boolean,
    activeLook: Boolean,
    scale: () -> Float,
    alpha: () -> Float,
    onSelect: () -> Unit
) {
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
                this.alpha = alpha()
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
