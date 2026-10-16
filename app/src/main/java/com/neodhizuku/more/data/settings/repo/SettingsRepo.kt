package com.neodhizuku.more.data.settings.repo

import kotlinx.coroutines.flow.Flow

interface SettingsRepo {
    fun flowWhitelistMode(): Flow<Boolean>
    fun flowDhizukuEnabled(): Flow<Boolean>
    fun flowDonateButtonHidden(): Flow<Boolean>
    fun flowAllowOwnerTransfer(): Flow<Boolean>
    fun flowServiceStoppedByUser(): Flow<Boolean>
    var isWhitelistMode: Boolean
    var isDhizukuEnabled: Boolean
    var isDonateButtonHidden: Boolean
    /** 允许第三方应用主动转移设备所有者，默认关闭 */
    var isAllowOwnerTransfer: Boolean

    /** 用户主动停止了守护服务；为 true 时服务被系统重建会立即再次停止 */
    var isServiceStoppedByUser: Boolean
}