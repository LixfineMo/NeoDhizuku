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
     * 更严格的服务校验，默认关闭。
     * 关闭时只按包名放行 —— 只认上游权限名的硬编码客户端同样可用；
     * 开启后额外要求调用方持有本应用自身声明的权限，仅持有上游权限名的会被拒绝。
     */
    fun flowStrictServiceCheck(): Flow<Boolean>

    /** 向客户端返回上游 Dhizuku 的版本号，默认开启。见 [isUseLegacyVersion] */
    fun flowUseLegacyVersion(): Flow<Boolean>

    /** 实验性：免删账号激活缺陷修复，默认关闭。见 [isExperimentalQuickActivateFix] */
    fun flowExperimentalQuickActivateFix(): Flow<Boolean>

    /** 实验性：权限委派残留清理，默认关闭。见 [isExperimentalDelegationCleanup] */
    fun flowExperimentalDelegationCleanup(): Flow<Boolean>

    /** 完整运行日志，默认关闭。见 [isExperimentalVerboseLog] */
    fun flowExperimentalVerboseLog(): Flow<Boolean>

    /** 检测更新时把非正式版本也算作可更新，默认关闭。见 [isUpdateAllowPreRelease] */
    fun flowUpdateAllowPreRelease(): Flow<Boolean>

    /** 每次打开应用时从官网检查更新，默认开启。见 [isStartupUpdateCheck] */
    fun flowStartupUpdateCheck(): Flow<Boolean>

    /** 更新拉取来源，见 [updateSource] */
    fun flowUpdateSource(): Flow<String>

    /** 允许明文 HTTP 的更新地址，见 [isUpdateAllowInsecure] */
    fun flowUpdateAllowInsecure(): Flow<Boolean>

    /** 自定义来源的版本清单地址，见 [updateCustomUrl] */
    fun flowUpdateCustomUrl(): Flow<String>

    /** 调试：官方服务器地址，见 [debugOfficialBase] */
    fun flowDebugOfficialBase(): Flow<String>

    /** 调试：Build 索引地址，留空时由官方地址推导。见 [debugBuildIndex] */
    fun flowDebugBuildIndex(): Flow<String>

    /** 调试：网络超时（毫秒），见 [debugTimeoutMs] */
    fun flowDebugTimeoutMs(): Flow<Int>

    /** 服务拒绝模式，见 [DenyMode] */
    var denyMode: String

    /** 能力不足时的处理模式，见 [InsufficientMode] */
    var insufficientMode: String
    var isWhitelistMode: Boolean
    var isDhizukuEnabled: Boolean
    var isDonateButtonHidden: Boolean
    /**
     * 是否允许第三方应用主动转移设备所有者，默认关闭。
     * 只作用于第三方经共享服务发起的请求；本应用内发起的转移不受此开关影响。
     */
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

    /**
     * 实验性：使用更严格的白名单校验，默认关闭。
     * 关闭时判定白名单只比对包名；开启后连加入白名单时记录的签名一并比对，
     * 同一个包名被重新签名后即不在白名单内。
     */
    fun flowExperimentalStrictWhitelist(): Flow<Boolean>

    /** 检测更新时把非正式版本也算作可更新，默认关闭 */
    var isUpdateAllowPreRelease: Boolean

    /** 每次打开应用时从官网检查更新，默认开启 */
    var isStartupUpdateCheck: Boolean

    /** 更新拉取来源。见 [flowUpdateSource] */
    var updateSource: String

    /** 允许明文 HTTP 的更新地址。见 [flowUpdateAllowInsecure] */
    var isUpdateAllowInsecure: Boolean

    /** 自定义来源的版本清单地址。见 [flowUpdateCustomUrl] */
    var updateCustomUrl: String

    /** 调试：官方服务器地址 */
    var debugOfficialBase: String

    /** 调试：官方服务器 Build 索引地址，留空时由官方地址推导 */
    var debugBuildIndex: String

    /** 调试：网络超时（毫秒） */
    var debugTimeoutMs: Int

    /** 实验性：使用更严格的白名单校验，默认关闭。见 [flowExperimentalStrictWhitelist] */
    var isExperimentalStrictWhitelist: Boolean

    /**
     * 更严格的服务校验，默认关闭。见 [flowStrictServiceCheck]。
     */
    var isStrictServiceCheck: Boolean
}