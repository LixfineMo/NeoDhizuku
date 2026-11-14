package com.neodhizuku.more.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * 二级页面的进出场动画，按 UI 引擎分流。
 *
 * Navigation Compose 会按预见式返回手势的进度逐帧定位 popEnter / popExit，
 * 因此这里只用可定位的 tween，不用 spring，也不加 delay。
 */
// 动画本身不依赖 AnimatedContentTransitionScope 的状态，存成无 receiver 的函数：
// 由 NavHost 的 transition lambda 提供作用域，避免绑定某个导航库版本的泛型签名。
data class NavMotion(
    val enter: () -> EnterTransition,
    val exit: () -> ExitTransition,
    val popEnter: () -> EnterTransition,
    val popExit: () -> ExitTransition,
)

fun navMotion(engine: UiEngine, isLtr: Boolean): NavMotion {
    // 新页从右侧进入；RTL 下整条方向翻转
    val dir = if (isLtr) 1 else -1
    return when (engine) {
        UiEngine.ORIGINAL -> originalMotion(dir)
        UiEngine.MIUIX -> miuixMotion(dir)
    }
}

/** 原版：Material 共享轴 X，位移为页宽的 1/6，上下层同步淡入淡出。 */
private fun originalMotion(dir: Int): NavMotion {
    val fade = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)
    return NavMotion(
        enter = {
            fadeIn(fade) + slideInHorizontally(slide) { (it / 6f * dir).roundToInt() }
        },
        exit = {
            fadeOut(fade) + slideOutHorizontally(slide) { (-it / 6f * dir).roundToInt() }
        },
        popEnter = {
            fadeIn(fade) + slideInHorizontally(slide) { (-it / 6f * dir).roundToInt() }
        },
        popExit = {
            fadeOut(fade) + slideOutHorizontally(slide) { (it / 6f * dir).roundToInt() }
        },
    )
}

/**
 * miuix：MIUI 的整页推入。新页整幅滑入，下层只左移 1/4、微缩，保持接近不透明；
 * 返回时上层整幅右移 + 缩小 + 淡出，下层反向归位。
 * 淡出只做很浅的渐变：下层一旦明显变暗，底栏从页面层 drawBackdrop 取到的就是暗场，
 * vibrancy 再拉浓就成了「突然一块黑」而不是渐变。
 * 这条 popExit 同时也是预见式返回的预览动画，跟随手势进度逐帧定位。
 */
private fun miuixMotion(dir: Int): NavMotion {
    val fade = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)
    val scale = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    return NavMotion(
        enter = {
            slideInHorizontally(slide) { it * dir } + fadeIn(fade, initialAlpha = 0.85f)
        },
        exit = {
            slideOutHorizontally(slide) { (-it / 4f * dir).roundToInt() } +
                fadeOut(fade, targetAlpha = 0.92f) +
                scaleOut(scale, targetScale = 0.94f)
        },
        popEnter = {
            slideInHorizontally(slide) { (-it / 4f * dir).roundToInt() } +
                fadeIn(fade, initialAlpha = 0.92f) +
                scaleIn(scale, initialScale = 0.94f)
        },
        popExit = {
            slideOutHorizontally(slide) { it * dir } +
                fadeOut(fade, targetAlpha = 0.90f) +
                scaleOut(scale, targetScale = 0.92f)
        },
    )
}
