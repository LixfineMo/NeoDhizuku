package com.neodhizuku.more.ui.page.settings.app_management

/** 软件管理的标签页。黑名单已拆到独立页面，不在这里。 */
enum class AppTab {
    /** 已授权：allowApi = true 且未拉黑 */
    Authorized,

    /** 未授权：有记录但 allowApi = false，或从未请求过（已声明权限） */
    Unauthorized
}

data class AppManagementViewState(
    val loading: Boolean = true,
    val data: List<AppManagementViewData> = emptyList(),
    /** 当前选中的标签 */
    val tab: AppTab = AppTab.Authorized
)

/**
 * 从设置页跳转到软件管理时，指定默认落在哪个标签。
 * 不用导航参数是为了避免改动 NavHost 的 composable(route=...) 签名；
 * 这里用一个进程内的临时变量，页面进入时读一次即可。
 */
data object AppManagementStartTab {
    @Volatile
    var tab: AppTab = AppTab.Authorized
}
