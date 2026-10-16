package com.neodhizuku.more

/**
 * NeoDhizuku —— 优化开关中心。
 *
 * 设计约定（后续所有修改都遵循这套约定）：
 * 1. [UseNeoDhizuku] 是总开关，置为 false 时所有改动点一律回退到原始实现，
 *    行为与本次修改之前的版本完全一致（原有逻辑被完整保留在 else 分支中）。
 * 2. 每个改动点另有独立的分项开关（FixXxx / HardenXxx），默认全部为 true。
 *    当总开关为 false 时，分项开关不再生效（见 [on]）；
 *    当总开关为 true 时，可以单独把某一项置为 false 来做对照调试。
 * 3. 改动点的写法统一为：
 *      if (NeoDhizuku.on(NeoDhizuku.FixXxx)) {
 *          // 优化后的实现
 *      } else {
 *          // 原始实现（保持逐字不变）
 *      }
 * 4. 需要跨进程 / 跨模块（server_api，Java 侧）使用的开关，
 *    由 com.rosan.dhizuku.server_api.Variables.UseNeoDhizuku 承载，
 *    并在 App.onCreate 中与本类的值同步。
 */
object NeoDhizuku {
    /** 总开关：false = 完全回退到原始逻辑 */
    const val UseNeoDhizuku: Boolean = true

    // ---------------- A 类：静态可证的问题 ----------------

    /** getVersionName() 返回版本名而非版本码 */
    const val FixVersionName: Boolean = true

    /** UserServiceConnections.bind() 的 uid/pid 传参顺序 */
    const val FixUserServiceBindArgs: Boolean = true

    /** UserService 缓存条目生命周期化 */
    const val FixUserServiceLifecycle: Boolean = true

    /** 白名单模式判定死代码 */
    const val FixWhitelistMode: Boolean = true

    /** 授权结果回写（多次回调 / 异步落库 / 无冲突策略） */
    const val FixPermissionResultWrite: Boolean = true

    /** onPause 无条件判拒绝 */
    const val FixPermissionOnPause: Boolean = true

    /** 授权弹窗状态重建丢失 */
    const val FixPermissionSavedState: Boolean = true

    /** 组合阶段调用 finish() */
    const val FixPermissionFinishInCompose: Boolean = true

    /** 激活失败重试 comp!! NPE */
    const val FixActivateRetryNpe: Boolean = true

    /** 激活失败只显示异常 toString */
    const val FixActivateErrorMapping: Boolean = true

    /** Shizuku 入口静默失败 */
    const val FixShizukuEntryFeedback: Boolean = true

    /** ActivityResultLauncher 注册时序 */
    const val FixLauncherRegisterOrder: Boolean = true

    /** Provisioning / Finalize 流程收尾 */
    const val FixProvisioningFinalize: Boolean = true

    /** AppIconCache sizeOf 单位错误 */
    const val FixAppIconCache: Boolean = true

    /** 应用管理列表 O(n·m) */
    const val FixAppManagementPerf: Boolean = true

    /** 未授权但发起过调用的应用，自动在软件管理页显示为「未授权」卡片 */
    const val FixUnauthorizedCallerEntry: Boolean = true

    /** checkCallingPermission 的 runBlocking 阻塞 Binder 线程 */
    const val FixPermissionMemoryCache: Boolean = true

    /** Shizuku 激活硬编码 1.5s 等待 */
    const val FixAccountWait: Boolean = true

    /** 「停止 Dhizuku」使用 exitProcess */
    const val FixShutdownAction: Boolean = true

    /** 取消所有者后不刷新 */
    const val FixDeactivateRefresh: Boolean = true

    // ---------------- B 类：条件触发的问题 ----------------

    /** DaemonService 清理逻辑误删黑名单 */
    const val FixBlacklistPurge: Boolean = true

    /** 覆盖安装误丢授权 */
    const val FixPackageReplaceBroadcast: Boolean = true

    /** startService / startForegroundService 冗余双调用 */
    const val FixDuplicateStartService: Boolean = true

    /** 只校验第一个签名者 */
    const val FixSignatureSetCheck: Boolean = true

    /** DhizukuState 全局可变状态无保护 */
    const val FixStateThreadSafety: Boolean = true

    /** sync() 主线程做 IPC */
    const val FixSyncOffMainThread: Boolean = true

    /** DB 降级崩溃 / 设置存储分区不一致 */
    const val FixDbDowngradeAndDeStorage: Boolean = true

    // ---------------- C 类：规范与工程化 ----------------

    /** 反射替换 DevicePolicyManager.mService */
    const val FixReflectionDpm: Boolean = true

    /** 前台服务类型 remoteMessaging 误用 */
    const val FixFgsType: Boolean = true

    /** Manifest 权限与备份清理 */
    const val FixManifestCleanup: Boolean = true

    /** 依赖收敛与 ProGuard */
    const val FixDependencies: Boolean = true

    /** Compose 使用规范 */
    const val FixComposeLint: Boolean = true

    /** 字符串内嵌 HTML */
    const val FixStringsHtml: Boolean = true

    /** 冷启动强弹电池优化 */
    const val FixBatteryOptimizationPrompt: Boolean = true

    /** SharedPreferences.asFlow 全量反序列化 */
    const val FixPrefsFlow: Boolean = true

    /** server_api 缺少版本名常量 */
    const val FixApiVersionName: Boolean = true

    // ---------------- D 类：安全加固（需真机验证后再独立关闭） ----------------

    /** 授权 Activity 导出且 UID 由客户端自报 */
    const val HardenPermissionEntry: Boolean = true

    /** DhizukuProvider.call() 无鉴权 */
    const val HardenProviderAuth: Boolean = true

    /** remoteProcess 无审计 / 超时 / 开关 */
    const val HardenRemoteProcessAudit: Boolean = true

    /** 无自身完整性校验 */
    const val HardenIntegrityCheck: Boolean = true

    // ---------------- 界面相关 ----------------

    /** 悬浮底部导航栏 */
    const val UseFloatingNavBar: Boolean = true

    /** 首页激活态状态卡，未激活时保持原版样式 */
    const val UseHomeStatusCard: Boolean = true

    /** 软件管理只显示声明了 Dhizuku 权限的应用 + 三标签分页 */
    const val UseAppManagementTabs: Boolean = true

    /** 授权弹窗去掉进度条、允许浅绿/拒绝突出/加黑名单按钮 */
    const val UsePermissionDialogV2: Boolean = true

    /** 新增「调用记录」页（内存审计，非持久） */
    const val UseCallLogPage: Boolean = true

    /** 设置页新增「管理」分区（应用管理 / 黑名单 / 调用记录跳转） */
    const val UseSettingsManageSection: Boolean = true

    /** 新增独立的「主题」页面（深色模式 / 动态取色 / 纯黑） */
    const val UseThemePage: Boolean = true

    /** 调用记录与运行日志落库（保留 30 天） */
    const val UseCallLogPersist: Boolean = true

    /** 运行日志落库（保留 30 天） */
    const val UseRuntimeLogPersist: Boolean = true

    /**
     * 拦截第三方应用通过 remote_transact 转移设备所有者。
     * 关闭（默认）时直接拒绝并通知；开启时放行但发警示通知。
     */
    const val HardenOwnerTransferGuard: Boolean = true

    /**
     * 调试日志总开关。
     *
     * 打开后 `NeoDhizuku.DebugLog` 为真，NeoLog 才会输出。
     * 建议排查问题时打开：
     *   adb logcat -s NeoDhizuku:V
     * 发布版本可置为 false。
     */
    const val DebugLog: Boolean = true

    /**
     * 判断某个改动点是否启用。
     * 总开关关闭时，所有分项一律视为关闭。
     */
    @JvmStatic
    fun on(feature: Boolean): Boolean = UseNeoDhizuku && feature

    /** 便于日志输出当前生效的开关状态 */
    @JvmStatic
    fun describe(): String = buildString {
        append("UseNeoDhizuku=").append(UseNeoDhizuku)
        if (!UseNeoDhizuku) return@buildString
        append(", FixVersionName=").append(FixVersionName)
        append(", FixWhitelistMode=").append(FixWhitelistMode)
        append(", FixPermissionResultWrite=").append(FixPermissionResultWrite)
        append(", FixBlacklistPurge=").append(FixBlacklistPurge)
        append(", HardenPermissionEntry=").append(HardenPermissionEntry)
        append("…（共 ")
        append(
            listOf(
                FixVersionName, FixUserServiceBindArgs, FixUserServiceLifecycle, FixWhitelistMode,
                FixPermissionResultWrite, FixPermissionOnPause, FixPermissionSavedState,
                FixPermissionFinishInCompose, FixActivateRetryNpe, FixActivateErrorMapping,
                FixShizukuEntryFeedback, FixLauncherRegisterOrder, FixProvisioningFinalize,
                FixAppIconCache, FixAppManagementPerf, FixPermissionMemoryCache, FixAccountWait,
                FixShutdownAction, FixDeactivateRefresh, FixBlacklistPurge,
                FixPackageReplaceBroadcast, FixDuplicateStartService, FixSignatureSetCheck,
                FixStateThreadSafety, FixSyncOffMainThread, FixDbDowngradeAndDeStorage,
                FixReflectionDpm, FixFgsType, FixManifestCleanup, FixDependencies,
                FixComposeLint, FixStringsHtml, FixBatteryOptimizationPrompt, FixPrefsFlow,
                FixApiVersionName, HardenPermissionEntry, HardenProviderAuth,
                HardenRemoteProcessAudit, HardenIntegrityCheck
            ).size
        ).append(" 项分项开关）")
    }
}
