package com.neodhizuku.more.ui.page.settings

sealed class SettingsRoute(val route: String) {
    /** 底部四页的容器：内部用 HorizontalPager 承载，支持左右拖动切换 */
    data object Tabs : SettingsRoute("tabs")

    data object Home : SettingsRoute("home")
    data object AppManagement : SettingsRoute("app_management")
    data object Blocklist : SettingsRoute("blocklist")
    data object CallLog : SettingsRoute("call_log")
    data object Settings : SettingsRoute("settings")
    data object Security : SettingsRoute("security")
    data object Whitelist : SettingsRoute("whitelist")
    /** 单项接口配置：逐个接口决定每个应用能用到哪一步 */
    data object ApiPolicy : SettingsRoute("api_policy")
    /** DAX 化 Xposed 模块：作用域与行为配置 */
    data object HookConfig : SettingsRoute("hook_config")
    data object Experimental : SettingsRoute("experimental")
    /** 内核漏洞实验室：导入 boot.img、环境自检、执行日志 */
    data object Cve98432 : SettingsRoute("cve_98432")
    data object Delegation : SettingsRoute("delegation")
    data object Theme : SettingsRoute("theme")
    data object About : SettingsRoute("about")
    /** 设备身份：动态包名的移除 / 转移 */
    data object OwnerIdentity : SettingsRoute("owner_identity")
    data object UpdateSettings : SettingsRoute("update_settings")
    data object Debug : SettingsRoute("debug")
    data object DebugUpdateConfig : SettingsRoute("debug_update_config")
    data object DebugRuntimeParams : SettingsRoute("debug_runtime_params")
    data object DebugRuntimeInfo : SettingsRoute("debug_runtime_info")
    data object DebugServiceFiles : SettingsRoute("debug_service_files")
    /** [key] 是私有文件相对路径的 URL-safe Base64，不含斜杠 */
    data object DebugServiceFileEdit : SettingsRoute("debug_service_file/{key}") {
        fun route(key: String) = "debug_service_file/$key"
    }
    data object QuickActivate : SettingsRoute("quick_activate")
    data object Activate : SettingsRoute("activate/{mode}") {
        enum class Mode {
            Dhizuku,
            Shizuku;
        }

        fun route(mode: Mode) = "activate/${mode.name}"
    }
}
