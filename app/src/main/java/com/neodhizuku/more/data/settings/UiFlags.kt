package com.neodhizuku.more.data.settings

import android.content.Context

/**
 * 纯界面标记位。与 SettingsRepo 分开存，避免为几个调试用的开关
 * 去动 33 语资源与仓库接口。
 *
 * 关闭 TEST ONLY 药丸：调试 → 实时参数修改 → UiFlags.testOnlyLabel
 */
object UiFlags {
    private const val PREF = "ui_flags"
    private const val KEY_TEST_ONLY = "testOnlyLabel"
    private lateinit var app: Context

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    private fun prefs() = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    var testOnlyLabel: Boolean
        get() = if (::app.isInitialized) prefs().getBoolean(KEY_TEST_ONLY, true) else true
        set(value) {
            if (::app.isInitialized) prefs().edit().putBoolean(KEY_TEST_ONLY, value).apply()
        }
}
