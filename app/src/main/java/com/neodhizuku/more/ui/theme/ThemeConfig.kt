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
 *
 * 存储独立于业务配置的 `theme_settings`。
 * 动态取色直接用 Material3 的 `dynamicColorScheme`（Android 12+ 系统原生支持），
 * 不再引入第三方配色库。
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
    val useMonet: Boolean = true
)

object ThemeConfig {

    private const val PREFS_NAME = "theme_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_USE_MONET = "use_monet"

    /** 动态取色的最低系统版本（Android 12） */
    fun isMonetSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.fromValue(prefs(context).getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.value))

    fun getUseMonet(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_MONET, true)

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

    /** 组合成单个 Flow；主题开关关闭时直接发射系统默认 */
    fun getThemeFlow(context: Context): Flow<ThemeSettings> {
        val p = prefs(context)
        val modeFlow = p.asFlow(KEY_THEME_MODE, ThemeMode.SYSTEM.value)
        val monetFlow = p.asFlow(KEY_USE_MONET, true)
        return combine(modeFlow, monetFlow) { modeValue, monet ->
            val settings = ThemeSettings(
                themeMode = ThemeMode.fromValue(modeValue),
                useMonet = monet
            )
            NeoLog.v(NeoLog.THEME) { "themeFlow emit -> $settings" }
            settings
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
