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
private val GlassBarHeight: Dp = 64.dp

/** 药丸高度，也是内容区高度。 */
private val GlassPillHeight: Dp = 56.dp

/** 底栏左右内缩，药丸在这个内缩区里滑动。 */
private val GlassInset: Dp = 4.dp

/** 页签图标尺寸，随底栏一起收小。 */
private val GlassTabIconSize: Dp = 24.dp

/** 药丸内按钮保持原尺寸，只做边缘折射。 */
private const val PILL_PRESS_SCALE = 0f

/** 速度换算成形变量的倍率，越大变形越明显。 */
private const val PILL_VELOCITY_GAIN = 0.1f

/**
 * 药丸与 Pager 位置相差多少页以内，才允许切到「逐帧跟随」。
 * 取得小是为了避免跳变：刚拖完底栏时药丸在弹簧收敛中，与 Pager 差着好几页，
 * 此时直接跟随会让药丸瞬间跳到 Pager 的位置。
 */
private const val FOLLOW_ENTER_TOLERANCE = 0.06f

internal val LocalGlassTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }
internal val LocalGlassContentColor = staticCompositionLocalOf { Color.Unspecified }

/**
 * 液态玻璃底部选择栏，与 [FloatingNavBar] 共用 [NavDestination] 与拖动状态。
 * 药丸里放整条页签并反向平移，与底栏每一项逐像素对齐；底栏那一份按
 * 「被药丸覆盖的比例」连续淡出，拖动途中不会空位或重影。
 * 传入背景采样层时由 miuix-blur 做真实背景采样与折射，否则退回自绘。
 */
@Composable
fun GlassNavBar(
    tabs: List<NavDestination>,
    selectedIndex: () -> Int,
    onNavigate: (String) -> Unit,
    /**
     * 连续页码（含小数），通常是 Pager 的 `currentPage + currentPageOffsetFraction`。
     * 药丸逐帧跟随这个值，而不是对每个整数页重启动画 —— 后者会让药丸
     * 先收敛到中间页并停住，等下一个整数页到来才继续，看起来就是"卡一下"。
     */
    selectedPosition: () -> Float = { selectedIndex().toFloat() },
    /** 拖动松手后让 Pager 立即对齐到落点，避免药丸与 Pager 位置对不上。 */
    onSettleTab: ((Int) -> Unit)? = null,
    /** 玻璃背景透明度倍率。1f 为默认观感，调小更透、调大更实。 */
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
    // 背景图模式下 surface 是透明的（RGB 全 0），改用不透明容器色判断
    val surface = scheme.surfaceContainerHigh
    val dark = (surface.red + surface.green + surface.blue) < 1.5f
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
    val selfAlphaScale = if (useMiuix) 0.30f else 1f

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
    // 拖动落点用本地累计值，不读异步写入的 Animatable.targetValue。
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
                // 页面瞬时切换，药丸由 Pager 位置驱动。
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
                    // 页面与药丸都瞬时对齐。
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
                    // snapTo 直接落位，不每帧重启 animateTo 弹簧。
                    this.dragTo(dragPosBox[0])
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
        // 进入后不因帧间位移退出 —— 快速滚动时单帧位移并不小。
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

    // 不做图标放大：药丸内保持原尺寸，只保留边缘折射。
    val tabScale: () -> Float = { 1f }
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
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    // 静态不折射，按下才从 0 渐显。
                                    val progress = drag.pressProgress
                                    lens(
                                        refractionHeight = 24.dp.toPx() * progress,
                                        refractionAmount = 24.dp.toPx() * progress,
                                    )
                                },
                                onDrawSurface = {
                                    // 中性黑，不用浅色容器色：浅色主题下那层会把整条底栏洗白。
                                    drawRect(Color.Black.copy(alpha = 0.08f))
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
                        // 自绘假折射带/色散边全部去掉：真折射由 miuix 提供，
                        // 两套叠在一起按压时会在边缘打架（重影）。NPatch 也没有。
                        lensAlpha = 0f,
                        dispersionAlpha = 0f,
                        refractionProgress = { drag.pressProgress },
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
                                    // 药丸会重新采样并覆盖底栏那一层，
                                    // 补上同半径 blur 才能保持整条底栏连续。
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    val progress = drag.pressProgress
                                    lens(
                                        // 静态 progress=0 时 lens() 直接 return，
                                        // 按下才渐显折射与色散。
                                        refractionHeight = 16.dp.toPx() * progress,
                                        refractionAmount = 24.dp.toPx() * progress,
                                        depthEffect = true,
                                        chromaticAberration = 0.65f,
                                    )
                                },
                                onDrawSurface = {
                                    // 中性黑，不用浅色容器色：浅色主题下那层会把整条底栏洗白。
                                    drawRect(Color.Black.copy(alpha = 0.08f))
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
                        lensAlpha = 0f,
                        dispersionAlpha = 0f,
                        vibrancy = if (useMiuix) 1f else 1.5f,
                        borderColors = glassBorderColors(dark),
                        // 只保留 miuix 真折射；自绘层在按压时按 glassFade 让路。
                        refractionProgress = { drag.pressProgress },
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
