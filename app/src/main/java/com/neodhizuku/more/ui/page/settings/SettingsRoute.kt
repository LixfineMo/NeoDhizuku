package com.neodhizuku.more.ui.page.settings

sealed class SettingsRoute(val route: String) {
    data object Home : SettingsRoute("home")
    data object AppManagement : SettingsRoute("app_management")
    data object Blocklist : SettingsRoute("blocklist")
    data object CallLog : SettingsRoute("call_log")
    data object Settings : SettingsRoute("settings")
    data object Theme : SettingsRoute("theme")
    data object Activate : SettingsRoute("activate/{mode}") {
        enum class Mode {
            Dhizuku,
            Shizuku;
        }

        fun route(mode: Mode) = "activate/${mode.name}"
    }
}
