package com.neodhizuku.more.ui.component.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

/**
 * 提饱和。
 * 玻璃透出来的背景通常偏灰，提一点饱和度让它更接近系统玻璃观感。
 * 底栏要更通透时传更小的 saturation，避免颜色被拉浓显得发闷。
 */
fun BackdropEffectScope.vibrancy(saturation: Float = 1.5f) {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = saturation,
    )
}
