# NeoDhizuku

基于 [Dhizuku](https://github.com/iamr0s/Dhizuku) 的二次开发版本，把设备所有者（DeviceOwner）权限受控地共享给其他应用。

---

## ⚠️ 未完工警告

**当前版本开发中，请勿用于日常设备或生产环境。** 部分功能刚落地、未充分真机验证。包名已改为 `com.neodhizuku.more`，与官方版本**不共存**（会成为不同的设备所有者），请先在测试机上验证。不保证与官方版本的数据兼容，迁移前请自行备份。

---

## 一、二次修改说明与新特性

所有改动挂在 `NeoDhizuku.UseNeoDhizuku` 总开关下（`app/src/main/java/com/neodhizuku/more/NeoDhizuku.kt`）。

> 置为 `false` 时所有分项一律失效，行为**完全回退到上游原始实现**；也支持单独关闭某一分项，便于对照调试。

### 基础标识

| 项 | 上游 | 本项目 |
|---|---|---|
| 包名 | `com.rosan.dhizuku` | `com.neodhizuku.more` |
| 应用名 | Dhizuku | NeoDhizuku |
| 系统支持 | Android 8.0+（编译目标 API 37） | 同左 |

> Manifest 中的 API 权限名与 AIDL 包名**保持原样**，这是为了让现有第三方客户端无需修改即可识别本应用。

### 功能修复

版本信息返回错误、用户服务绑定参数与连接泄漏、白名单模式死代码、授权结果未落库、授权弹窗切后台误判拒绝与旋转丢状态、黑名单被守护服务误删、覆盖安装丢失授权、服务重复启动、签名集合比对、前台服务类型、DE 存储统一、数据库降级重建、图标缓存容量、应用列表查询性能、账户等待改为轮询、Provisioning 完成后刷新状态等。

### 安全加固

- **授权入口不再信任客户端自报的 UID**：改由服务端用 `Binder.getCallingUid()` 取真实身份并签发一次性令牌（10 分钟有效），Activity 校验通过后才认可该 UID。旧版客户端走兼容路径，不会覆盖已有授权。
- **服务端接管授权请求**：弹窗改由服务端发起，Android 10+ 后台启动 Activity 限制导致弹窗静默失败时不再回调拒绝，改为发通知由用户点击拉起；老版本客户端（只查询 `isPermissionGranted()`、从不发起请求）首次被拒时会登记并发出授权通知，作为其唯一入口。
- Provider 鉴权、`remoteProcess` 命令审计、启动时自身签名校验。
- 签名白名单在**构建期**注入，未配置时自动跳过，方便自行编译调试。

### 界面重做

参考 KernelSU 管理端的布局思路，用 Material3 实现（**未引入 miuix / coui**）：

- **悬浮底部导航栏**：四页共用，图标 + 文字，**不使用 emoji**。未激活时只显示「首页 / 设置」；子页面不显示底栏。
- **首页状态卡**：激活后为「左侧状态卡 + 右侧上下两张小卡」——对勾图标 + 已激活 + 所有者类型 + 版本号（点击进入激活页），右上授权应用数，右下服务运行时间（`HH:MM:SS`，服务重启归零）。**未激活时保持上游原样式不变**。
- **软件管理**：不分标签，所有与 Dhizuku 相关的应用都在同一列表中，开关直接显示授权状态。未在清单声明权限但发起过调用的应用（如部分安装器）会自动登记进来，可直接开启授权。
- **黑名单独立页面**：从软件管理拆出，可逐项解除拉黑。
- **授权弹窗**：无进度条；「允许」浅绿、「拒绝」红色突出、「加入黑名单」黑色，均为纯文字无背景框；倒计时小字置于按钮行下方。
- **调用记录**：记录各应用调用了哪些能力，全部中文短句描述。**持久化保存，默认保留 30 天**；每条记录附带当时的服务端运行日志片段。另含「运行日志」标签，可查看服务端自身日志，**未激活时也能查看**。
- **服务启停**：右上角按钮在运行中显示为叉号，停止后变为重启图标；运行时间卡停止时整卡纯红显示「拒绝服务」。停止后仍保留查看授权与调用记录的能力，其余操作一律拒绝。
- **设备所有者转移拦截**：第三方应用拿到授权后可借 `binderWrapper` 以设备所有者身份调用 DPM 任意方法，若其不提供授权入口，转移后所有者无法取回。设置项「允许第三方应用主动转移设备所有者」**默认关闭**，关闭时直接拒绝并发出通知。
- **主题页**：深色模式四档（跟随系统 / 浅色 / 深色 / 深色纯黑 AMOLED）+ 动态取色开关，深色模式用折叠选择器。
- **设置页**：新增「管理」分区入口，**未激活时整个分区隐藏**。
- **激活错误提示**：英文原始报错映射为中文原因 + 处理建议 + 复制诊断信息。

### 调试日志

统一 TAG `NeoDhizuku`，带分类子标签（`Nav` / `Theme` / `Perm` / `Home` / `Apps` / `Daemon` / `Server` / `Activate` / `UI` / `Boot`）。开关 `NeoDhizuku.DebugLog`，关闭时完全静默且无字符串拼接开销。

```bash
adb logcat -s NeoDhizuku:V              # 全部
adb logcat -s NeoDhizuku:V | grep Nav   # 只看导航
```

---

## 二、备注与使用方法

### 激活

与上游一致，支持 Shizuku、ADB、QR 码等方式成为设备所有者。**注意上游提示：建议在设备上没有任何账号时配置**；若已有账号可能需要先移除。

成为设备所有者后，第三方应用即可通过 Dhizuku API 申请使用设备所有者能力。

### 授权管理

- 第三方应用首次调用时弹出授权请求，可选择允许、拒绝或加入黑名单。
- **加入黑名单后不再处理该应用的授权请求**。
- 「软件管理」中可随时开关某个应用的授权；「黑名单」页可解除拉黑。
- **白名单模式**开启后，仅白名单内的应用可调用高危接口。

### 开关用法

编辑 `NeoDhizuku.kt`：

```kotlin
const val UseNeoDhizuku: Boolean = true   // 总开关，false = 全部回退到上游行为
const val UseThemePage: Boolean = true    // 分项：主题页
const val DebugLog: Boolean = true        // 分项：调试日志
```

排查问题建议：先置 `UseNeoDhizuku = false` 编一版基线，再切成 `true` 对比；哪个分项出问题就单独关掉它定位。

### 已知限制

- 悬浮底栏未改成左右滑动切页的 Pager 结构，只能点击切换。
- 主题用 Material3 原生 `dynamicColorScheme`（Android 12+ 系统支持），未引入第三方配色库。

---

## 三、构建方法

### 环境要求

Android Studio **Quail 4（2026.1.4）** 及以上（AGP 9.4.1 的最低要求），JDK 21。
SDK Manager 中需安装 SDK Platform 37、Build Tools 37.0.0、NDK 29.0.14206865。

### 构建

项目有两个包名分支（product flavor），`namespace` 与源码包名固定为 `com.neodhizuku.more`，
只有 `applicationId` 随分支变化：

| 分支 | applicationId | 用途 |
|---|---|---|
| `neo` | `com.neodhizuku.more` | 本项目自身包名 |
| `official` | `com.rosan.dhizuku` | 原版包名，用于兼容硬编码官方包名的第三方客户端 |

```
./gradlew assembleNeoDebug        # neo 分支调试版
./gradlew assembleOfficialDebug   # official 分支调试版
./gradlew assembleNeoRelease      # neo 分支发布版
./gradlew assembleOfficialRelease # official 分支发布版
```

> **重要**：两个分支是**不同的应用**，设备所有者同时只能属于一个包名。
> 装 `official` 版后必须重新激活，且会抢走 `neo` 版的所有者身份。
> 建议日常只用 `neo` 分支，确有客户端硬编码官方包名时再单独出 `official` 版实测。

仓库内默认是调试签名（`keystore/`），正式分发前请替换为自己的签名配置。

---

## 四、后期规划

- [ ] **免删账号的设备所有者激活**：借助 Shizuku 将 `user_setup_complete` 临时置 0，绕过「必须无账号」的限制完成激活，**不删除设备上已有的账号**，激活后恢复原值。需处理 `DEVICE_POLICY_MANAGEMENT` 角色的版本限制（自 Android 13 / API 33 引入，低版本需给出明确判定与降级提示）。
- [ ] 授权入口「UID 自报」的实际可利用性用 Demo 实测（静态上攻击面存在，实际影响未确认）。
- [ ] 授权弹窗权限范围展示。
- [ ] 委托作用域（delegated scopes）配置入口 —— API 已支持但界面无入口。
- [ ] **已授权状态下更新应用**：成为设备所有者后无法直接安装更新（`INSTALL_FAILED_...` / 包安装器拒绝覆盖设备所有者）。需研究能否在保持所有者身份的前提下完成自更新，例如借助 Dhizuku 自身的 `remote_transact` 走 `IPackageInstaller` 静默安装，或临时委托/转移后再取回。需实测各 Android 版本行为差异。

---

## 开源协议

本项目是 Dhizuku (GPL-3.0) 的衍生作品，整体继续以 GPL-3.0 分发，完整协议见根目录 `LICENSE`。

本项目修改自 [Dhizuku](https://github.com/iamr0s/Dhizuku)，在其基础上修改了包名、授权与激活流程、安全加固与界面实现。完整修改记录见仓库提交历史。

| 上游项目 | 协议 | 说明 |
|---|---|---|
| Dhizuku | GPL-3.0 | 直接上游，本项目整体沿用其协议 |
| Dhizuku-API | MIT | 本地子模块引入，版权与许可声明保留于 `Dhizuku-API/LICENSE` |
| KernelSU | GPL-3.0-or-later（`kernel/` 为 GPL-2.0-only） | 仅提供界面与交互灵感，未复用代码 |
| Shizuku API | Apache-2.0 | Maven 依赖 |

**致谢**：Dhizuku（iamr0s）、Dhizuku-API（R0S）、KernelSU、Shizuku（RikkaApps），以及所有为上游贡献过代码与翻译的参与者。
