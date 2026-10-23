package com.neodhizuku.more.data.settings.repo

import kotlinx.coroutines.flow.Flow

interface SettingsRepo {
    fun flowWhitelistMode(): Flow<Boolean>
    fun flowDhizukuEnabled(): Flow<Boolean>
    fun flowDonateButtonHidden(): Flow<Boolean>
    fun flowAllowOwnerTransfer(): Flow<Boolean>
    fun flowServiceStoppedByUser(): Flow<Boolean>
    fun flowDenyMode(): Flow<String>
    /**
     * 向客户端返回上游 Dhizuku 的版本号（默认开启）。
     *
     * 部分客户端会校验版本，返回本项目的版本号可能被判定为不兼容，
     * 因此默认沿用上游值；关闭后返回本项目自身的版本号。
     */
    fun flowUseLegacyVersion(): Flow<Boolean>

    // ------------------------------------------------------------------
    // 实验性功能
    //
    // 与上面的开关不同：这些修复**默认关闭**，关闭时走原有逻辑，
    // 开启后才走修复后的分支，两套逻辑并存以便对照。
    // ------------------------------------------------------------------

    /** 实验性：免删账号激活缺陷修复，默认关闭 */
    fun flowExperimentalQuickActivateFix(): Flow<Boolean>

    /** 实验性：权限委派残留清理，默认关闭 */
    fun flowExperimentalDelegationCleanup(): Flow<Boolean>

    /**
     * 检测更新时把非正式版本（alpha / beta / 灰度）也算作可更新，默认关闭。
     *
     * 关闭时只提示正式版，避免普通用户收到不稳定的构建。
     */
    fun flowUpdateAllowPreRelease(): Flow<Boolean>

    /**
     * 完整运行日志，默认关闭。
     *
     * 关闭时运行日志只保留权限调用、权限变更与激活相关的内容，
     * 开启后不再过滤，全部分类照常记录。
     */
    fun flowExperimentalVerboseLog(): Flow<Boolean>

    /** 服务拒绝模式，见 [DenyMode] */
    var denyMode: String

    /** 能力不足时的处理模式，见 [InsufficientMode] */
    var insufficientMode: String
    var isWhitelistMode: Boolean
    var isDhizukuEnabled: Boolean
    var isDonateButtonHidden: Boolean
    /** 允许第三方应用主动转移设备所有者，默认关闭 */
    var isAllowOwnerTransfer: Boolean

    /** 用户主动停止了守护服务；为 true 时服务被系统重建会立即再次停止 */
    var isServiceStoppedByUser: Boolean

    /** 向客户端返回上游 Dhizuku 的版本号，默认开启 */
    var isUseLegacyVersion: Boolean

    /** 实验性：免删账号激活缺陷修复。默认关闭，关闭时走原逻辑 */
    var isExperimentalQuickActivateFix: Boolean

    /** 实验性：权限委派残留清理。默认关闭，关闭时走原逻辑 */
    var isExperimentalDelegationCleanup: Boolean

    /** 完整运行日志。默认关闭，关闭时只记录权限与激活相关日志 */
    var isExperimentalVerboseLog: Boolean

    /** 检测更新时把非正式版本也算作可更新，默认关闭 */
    var isUpdateAllowPreRelease: Boolean
}