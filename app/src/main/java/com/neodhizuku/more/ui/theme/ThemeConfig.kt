package com.neodhizuku.more.ui.theme

import android.content.Context
import android.os.Build

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.common.util.asFlow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 主题配置。
 * 存储独立于业务配置的 `theme_settings`。
 * 动态取色直接用 Material3 的 `dynamicColorScheme`（Android 12+ 系统原生支持），
 */
enum class ThemeMode(val value: Int) {
    /** 跟随系统 */
    SYSTEM(0),

    /** 强制浅色 */
    LIGHT(1),

    /** 强制深色 */
    DARK(2),

    /** 深色 + 纯黑背景（AMOLED） */
    AMOLED(3);

    companion object {
        fun fromValue(value: Int): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }

    val isDark: Boolean get() = this == DARK || this == AMOLED
    val isAmoled: Boolean get() = this == AMOLED

    /** 是否强制（非跟随系统） */
    val isForced: Boolean get() = this != SYSTEM
}

data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 动态取色（Android 12+） */
    val useMonet: Boolean = true,
    /** 液态玻璃底部选择栏 */
    val useLiquidGlass: Boolean = true,
    /** 管理器页面不出现在多任务（最近任务）列表 */
    val hideFromRecents: Boolean = false,
    /** 关闭动态取色时使用的种子色（ARGB） */
    val customColor: Int = DEFAULT_CUSTOM_COLOR,
    /** 是否已设置自定义背景图 */
    val backgroundImageSet: Boolean = false,
    /** 背景图模糊半径（dp） */
    val backgroundBlur: Float = DEFAULT_BACKGROUND_BLUR,
    /** 背景图暗化程度（0~1） */
    val backgroundDim: Float = DEFAULT_BACKGROUND_DIM,
    /** 卡片背后的磨砂玻璃模糊半径（dp） */
    val cardBlur: Float = DEFAULT_CARD_BLUR,
    /** 平板（宽屏）下是否仍显示选中胶囊药丸；默认关闭，导航栏贴边 */
    val tabletShowPill: Boolean = false
)

/** 默认种子色沿用品牌紫，关闭动态取色后不会突然换风格。 */
const val DEFAULT_CUSTOM_COLOR = 0xFF6650A4.toInt()

/** 背景图默认模糊半径与暗化程度。 */
const val DEFAULT_BACKGROUND_BLUR = 24f
const val DEFAULT_BACKGROUND_DIM = 0.35f

/** 卡片模糊默认半径与上限（dp）。 */
const val DEFAULT_CARD_BLUR = 18f

object ThemeConfig {

    /** 背景图模糊半径上限（dp） */
    const val MAX_BACKGROUND_BLUR = 60f

    /** 卡片模糊上限（dp） */
    const val MAX_CARD_BLUR = 48f

    private const val PREFS_NAME = "theme_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_USE_MONET = "use_monet"
    private const val KEY_USE_LIQUID_GLASS = "use_liquid_glass"
    private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
    private const val KEY_CUSTOM_COLOR = "custom_color"
    private const val KEY_BACKGROUND_IMAGE_SET = "background_image_set"
    private const val KEY_BACKGROUND_BLUR = "background_blur"
    private const val KEY_BACKGROUND_DIM = "background_dim"
    private const val KEY_CARD_BLUR = "card_blur"
    private const val KEY_TABLET_SHOW_PILL = "tablet_show_pill"

    /** 动态取色的最低系统版本（Android 12） */
    fun isMonetSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.fromValue(prefs(context).getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.value))

    fun getUseMonet(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_MONET, true)

    fun getUseLiquidGlass(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_LIQUID_GLASS, true)

    fun getHideFromRecents(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_FROM_RECENTS, false)

    fun getCustomColor(context: Context): Int =
        prefs(context).getInt(KEY_CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR)

    fun getBackgroundImageSet(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKGROUND_IMAGE_SET, false)

    fun getBackgroundBlur(context: Context): Float =
        prefs(context).getFloat(KEY_BACKGROUND_BLUR, DEFAULT_BACKGROUND_BLUR)

    fun getBackgroundDim(context: Context): Float =
        prefs(context).getFloat(KEY_BACKGROUND_DIM, DEFAULT_BACKGROUND_DIM)

    fun getCardBlur(context: Context): Float =
        prefs(context).getFloat(KEY_CARD_BLUR, DEFAULT_CARD_BLUR)

    fun getTabletShowPill(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TABLET_SHOW_PILL, false)

    fun setTabletShowPill(context: Context, enabled: Boolean) {
        NeoLog.d(NeoLog.THEME) { "tabletShowPill -> $enabled" }
        prefs(context).edit().putBoolean(KEY_TABLET_SHOW_PILL, enabled).apply()
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        NeoLog.d(NeoLog.THEME) { "setThemeMode -> $mode (value=${mode.value})" }
        prefs(context).edit().putInt(KEY_THEME_MODE, mode.value).apply()
    }

    fun setUseMonet(context: Context, enabled: Boolean) {
        if (enabled && !isMonetSupported()) {
            NeoLog.w(NeoLog.THEME) { "Monet 不支持（SDK ${Build.VERSION.SDK_INT} < 31），忽略开启请求" }
            return
        }
        NeoLog.d(NeoLog.THEME) { "setUseMonet -> $enabled" }
        prefs(context).edit().putBoolean(KEY_USE_MONET, enabled).apply()
    }

    fun setUseLiquidGlass(context: Context, enabled: Boolean) {
        NeoLog.d(NeoLog.THEME) { "setUseLiquidGlass -> $enabled" }
        prefs(context).edit().putBoolean(KEY_USE_LIQUID_GLASS, enabled).apply()
    }

    fun setHideFromRecents(context: Context, enabled: Boolean) {
        NeoLog.d(NeoLog.THEME) { "setHideFromRecents -> $enabled" }
        prefs(context).edit().putBoolean(KEY_HIDE_FROM_RECENTS, enabled).apply()
    }

    fun setCustomColor(context: Context, color: Int) {
        NeoLog.d(NeoLog.THEME) { "setCustomColor -> #${Integer.toHexString(color)}" }
        prefs(context).edit().putInt(KEY_CUSTOM_COLOR, color).apply()
    }

    /** 导入或清除背景图后调用，用来把「是否有背景图」变成一个可观察的状态。 */
    fun setBackgroundImageSet(context: Context, exists: Boolean) {
        NeoLog.d(NeoLog.THEME) { "backgroundImageSet -> $exists" }
        prefs(context).edit().putBoolean(KEY_BACKGROUND_IMAGE_SET, exists).apply()
    }

    fun setBackgroundBlur(context: Context, radius: Float) {
        val value = radius.coerceIn(0f, MAX_BACKGROUND_BLUR)
        prefs(context).edit().putFloat(KEY_BACKGROUND_BLUR, value).apply()
    }

    fun setBackgroundDim(context: Context, amount: Float) {
        val value = amount.coerceIn(0f, 1f)
        prefs(context).edit().putFloat(KEY_BACKGROUND_DIM, value).apply()
    }

    fun setCardBlur(context: Context, radius: Float) {
        val value = radius.coerceIn(0f, MAX_CARD_BLUR)
        prefs(context).edit().putFloat(KEY_CARD_BLUR, value).apply()
    }

    /** 组合成单个 Flow；主题开关关闭时直接发射系统默认 */
    fun getThemeFlow(context: Context): Flow<ThemeSettings> {
        val p = prefs(context)
        val modeFlow = p.asFlow(KEY_THEME_MODE, ThemeMode.SYSTEM.value)
        val monetFlow = p.asFlow(KEY_USE_MONET, true)
        val glassFlow = p.asFlow(KEY_USE_LIQUID_GLASS, true)
        val colorFlow = p.asFlow(KEY_CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR)
        val base = combine(modeFlow, monetFlow, glassFlow, colorFlow) { modeValue, monet, glass, color ->
            ThemeSettings(
                themeMode = ThemeMode.fromValue(modeValue),
                useMonet = monet,
                useLiquidGlass = glass,
                customColor = color
            )
        }

        val bgSetFlow = p.asFlow(KEY_BACKGROUND_IMAGE_SET, false)
        val blurFlow = p.asFlow(KEY_BACKGROUND_BLUR, DEFAULT_BACKGROUND_BLUR)
        val dimFlow = p.asFlow(KEY_BACKGROUND_DIM, DEFAULT_BACKGROUND_DIM)
        val hideFlow = p.asFlow(KEY_HIDE_FROM_RECENTS, false)

        val cardFlow = p.asFlow(KEY_CARD_BLUR, DEFAULT_CARD_BLUR)
        val pillFlow = p.asFlow(KEY_TABLET_SHOW_PILL, false)
        return combine(
            combine(base, bgSetFlow, blurFlow, dimFlow, hideFlow) { s, imageSet, blur, dim, hide ->
                s.copy(
                    backgroundImageSet = imageSet,
                    backgroundBlur = blur,
                    backgroundDim = dim,
                    hideFromRecents = hide
                )
            },
            cardFlow,
            pillFlow
        ) { settings, cardBlur, tabletShowPill ->
            val final = settings.copy(
                cardBlur = cardBlur,
                tabletShowPill = tabletShowPill
            )
            NeoLog.v(NeoLog.THEME) { "themeFlow emit -> $final" }
            final
        }
    }
}

/**
 * 根据 [ThemeSettings] 计算是否使用深色。
 * [systemDark] 传入 `isSystemInDarkTheme()`，便于预览与测试。
 */
@Composable
fun shouldUseDarkTheme(settings: ThemeSettings, systemDark: Boolean = isSystemInDarkTheme()): Boolean =
    when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        else -> settings.themeMode.isDark
    }
