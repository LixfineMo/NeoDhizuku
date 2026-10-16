package com.neodhizuku.more.ui.page.settings.settings

data class SettingsViewState(
    val whitelistMode: Boolean = false,
    val dhizukuEnabled: Boolean = true,
    /** 允许第三方应用主动转移设备所有者，默认关闭 */
    val allowOwnerTransfer: Boolean = false
)
