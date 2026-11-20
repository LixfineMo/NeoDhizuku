package com.neodhizuku.more.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 主题页的「UI 引擎」。
 * - [ORIGINAL]：现有 Material 3 + 玻璃底栏 + 卡片模糊，一切照旧。
 * - [MIUIX]：全局套 MiuixTheme，卡片/下拉走 miuix 组件，配色与字号对齐 miuix。
 * 两套引擎共享同一份页面代码，只通过 [LocalUiEngine] 在公共组件里分流，
 * 不需要为每个页面复制一份实现。
 */
enum class UiEngine(val key: String) {
    ORIGINAL("original"),
    MIUIX("miuix");

    companion object {
        /**
         * from
         * (key: String?)
         * 作用：执行 from
         * 备注：—
         */
        fun from(key: String?): UiEngine = when (key) {
            // 旧版本把该引擎存成 kernelsu，升级后要继续识别，避免退回原版。
            "miuix", "kernelsu" -> MIUIX
            else -> ORIGINAL
        }
    }
}

val LocalUiEngine = staticCompositionLocalOf { UiEngine.ORIGINAL }

/**
 * 轻量持久化。切换引擎要立刻重建整棵 Compose 树，用 StateFlow 让根 Activity
 * 直接观察即可，不必走 Koin / Room，也不污染 SettingsRepo 接口。
 */
object UiEngineStore {
    private const val PREFS = "neo_ui"
    private const val KEY = "engine"

    private val _engine = MutableStateFlow(UiEngine.ORIGINAL)
    val engine: StateFlow<UiEngine> = _engine

    /**
     * init
     * (context: Context)
     * 作用：初始化
     * 备注：—
     */
    fun init(context: Context) {
        val app = context.applicationContext
        _engine.value = UiEngine.from(
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        )
    }

    /**
     * set
     * (context: Context, value: UiEngine)
     * 作用：设置
     * 备注：—
     */
    fun set(context: Context, value: UiEngine) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY, value.key)
        }
        _engine.value = value
    }
}
