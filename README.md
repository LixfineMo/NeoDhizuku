# NeoDhizuku
中文 | [English](https://github.com/Qingyu-ch/NeoDhizuku/blob/master/docs/README.en.md) | [繁體中文](https://github.com/Qingyu-ch/NeoDhizuku/blob/master/docs/README.zh-rTW.md) | [日本語](https://github.com/Qingyu-ch/NeoDhizuku/blob/master/docs/README.ja.md) | [한국어](https://github.com/Qingyu-ch/NeoDhizuku/blob/master/docs/README.ko.md) | [Deutsch](https://github.com/Qingyu-ch/NeoDhizuku/blob/master/docs/README.de.md)

基于 [Dhizuku](https://github.com/iamr0s/Dhizuku) 的二次开发版本，把设备所有者（DeviceOwner）权限受控地共享给其他应用。

---

## ⚠️ 未完工警告

**当前版本开发中，请勿用于日常设备或生产环境，且部分功能刚落地、部分未充分真机验证。**

包名已改为 `com.neodhizuku.more`，与官方版本**不共存**（会成为不同的设备所有者），请先在测试机上验证。
不与官方版本的数据兼容，迁移前请自行备份。

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

### 安全加固

- **授权入口不再信任客户端自报的 UID**：改由服务端用 `Binder.getCallingUid()` 取真实身份并签发一次性令牌（10 分钟有效），Activity 校验通过后才认可该 UID。旧版客户端走兼容路径，不会覆盖已有授权。
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
- **服务策略页**：设置页内的二级菜单，集中管理 API 服务总开关、白名单模式、关闭服务后的处理方式，以及设备所有者转移开关。
- **免删账号临时激活**：免去了上游「必须先移除全部账号」的要求，详见后文专章。
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

与上游一致，支持 Shizuku、ADB、免删账号模式成为设备所有者。
**注意上游提示：建议在设备上没有任何账号时配置**；若已有账号可能需要先移除。

成为设备所有者后，第三方应用即可通过 Dhizuku API 申请使用设备所有者能力。

### 免删账号临时激活

上游建议在**设备上没有任何账号时**配置，否则需先移除账号。本方案绕开了这条限制。

**原理**：`DevicePolicyManagerService` 中，账户检查 `hasIncompatibleAccountsOrNonAdb` 只在 `isAdb` 分支内被读取。原版走 ADB（`dpm set-device-owner`）即 `isAdb=true`，三道闸全读；本方案以**应用自身 uid** 直接调用，`isAdb=false`，只校验 `hasUserSetupCompleted`，**完全不读账户**。

**七步链路**：

| 步骤 | 执行者 | 内容 |
|---|---|---|
| ① | shell | `cmd role set-bypassing-role-qualification true` |
| ② | shell | `cmd role add-role-holder --user 0 android.app.role.DEVICE_POLICY_MANAGEMENT <pkg>` |
| ③ | shell | `settings put secure user_setup_complete 0` |
| ④ | 应用自身 | 反射 `forceUpdateUserSetupComplete(0)`，把③的值搬进 DPM 内存态 |
| ⑤ | 应用自身 | 反射 `setActiveAdmin` |
| ⑥ | 应用自身 | 反射 `setDeviceOwner` |
| ⑦ | shell | `settings put secure user_setup_complete 1` |

③ 与 ④ **必须配对**：只做③无效，内存态不变、闸仍关闭。④ 的参数是 userId 而非状态值，它把 Settings 的真实值双向搬进内存，所以③不可省。

**三个前提条件**：

- Shizuku 或 root 权限（执行 ①③⑦ 的 shell 命令，普通应用拿不到 `WRITE_SECURE_SETTINGS`）
- Android 13（API 33）及以上（`DEVICE_POLICY_MANAGEMENT` 角色自该版本引入）
- 尚未成为设备所有者

**副作用**：置 0 期间三键导航的 Home 与最近任务键失效（返回键可用，手势导航不受影响）。代码在 `finally` 中必定恢复，不会把设备留在「未完成向导」状态。

**失败排查**：角色若返回 `RC=0` 但权限仍为 DENIED，多为清单未声明 `MANAGE_DEVICE_ADMINS` / `MANAGE_PROFILE_AND_DEVICE_OWNERS`——这两个是 signature 级权限，必须先声明系统才会在授予角色时 grant。页面会直接给出该提示。

若向导未恢复，页面会显示红色提示并禁用前两步，只保留「手动恢复」。

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

### 服务策略

设置页 →「服务策略」，三个分区：

**① API 服务**：总开关（允许其他应用使用 API 服务）、白名单模式（仅允许白名单应用使用 API 服务）。

**② 关闭服务后的处理方式**（抽屉式选择，仅在服务被关闭后生效）：

| 选项 | 行为 |
|---|---|
| 拒绝危险服务并返回失败 | 可查询到服务，非只读操作一律拒绝 |
| 拒绝危险服务并返回成功 | 放行但不实际执行，只回成功 |
| 完全拒绝服务并返回失败 | 返回 null，其他应用查不到服务 |
| 完全拒绝服务并返回成功 | 返回空结果 |
| 不做任何处理 | 挂起授权请求，客户端无限等待 |

「不做任何处理」通过**不回调**实现而非阻塞线程，因此不会影响服务处理其他调用。

**③ 设备所有者转移**：即上文「设备所有者转移拦截」的开关。

### 已知限制

- 悬浮底栏未改成左右滑动切页的 Pager 结构，只能点击切换。
- 主题用 Material3 原生 `dynamicColorScheme`（Android 12+ 系统支持），未引入第三方配色库。
- **与原版 Dhizuku 共存时**：硬编码官方包名 authority 的客户端（如 amarok、爱玩机工具箱）会连到原版而非本应用，出现「原版弹窗、本应用不响应」的现象。ContentProvider authority 全局唯一，无法抢注，出路是卸载原版后改用 `neoCompat` 或 `official` 分支。
- **免删账号激活仅支持 Android 13+**，低版本页面会显示不支持并隐藏步骤。
- Shizuku API 14 将移除 `newProcess`，届时免删激活中基于它的 shell 执行需迁移到 User Service。

---

## 三、构建方法

### 环境要求

Android Studio **Quail 4（2026.1.4）** 及以上（AGP 9.4.1 的最低要求），JDK 21。
SDK Manager 中需安装 SDK Platform 37、Build Tools 37.0.0、NDK 29.0.14206865。

### 构建

项目有三个分支（product flavor），`namespace` 与源码包名固定为 `com.neodhizuku.more`，
只有 `applicationId` 与 provider 的 authorities 随分支变化：

| 分支 | applicationId | 注册原版 authority | 与原版 Dhizuku 共存 | 兼容硬编码官方包名的客户端 |
|---|---|---|---|---|
| `neo` | `com.neodhizuku.more` | 否 | **能** | 否 |
| `neoCompat` | `com.neodhizuku.more` | 是 | 不能 | **能** |
| `official` | `com.rosan.dhizuku` | — 本身就是 | 不能（同包名） | **能** |

`neo` 与 `neoCompat` 的 applicationId 相同，只能二选一安装，可各编一份用于对比测试。

```
./gradlew assembleNeoDebug        共存版（推荐日常使用）
./gradlew assembleNeoCompatDebug  接管版（需先卸载原版 Dhizuku）
./gradlew assembleOfficialDebug   官方包名版（需先卸载原版 Dhizuku）

./gradlew assembleBothDebug       一次出 neo + neoCompat 两个调试包
./gradlew assembleAllDebug        一次出三个调试包
```

发布版把 `Debug` 换成 `Release`（另有 `assembleAllRelease` 一次出三个）。

> **重要**：设备所有者同时只能属于一个包名，`neoCompat` / `official` 都需要
> **先卸载原版 Dhizuku**，否则 provider authority 冲突导致装不上
> （`INSTALL_FAILED_CONFLICTING_PROVIDER`）。装后需重新激活。

仓库内默认是调试签名（`keystore/`），正式分发前请替换为自己的签名配置。

---

## 四、后期规划（完成并测试后正式发布 Release 版本）

- [-] **授权入口改造**：让所有第三方应用都能稳定发起授权请求。现状是部分客户端（多为依赖旧版 API 的）既不弹窗也不发起请求；尝试过的服务端接管方案实测无效，需先定位真实失败点（客户端是否调用 `requestPermission`、listener 能否收到回调、通知是否真的发出）再重新设计。
- [√] ~~免删账号的设备所有者激活~~ —— 已实现（见「免删账号临时激活」）
- [-] 授权入口「UID 自报」的实际可利用性用 Demo 实测（静态上攻击面存在，实际影响未确认）。
- [-] 授权弹窗权限范围展示。
- [-] 委托作用域（delegated scopes）配置入口 —— API 已支持但界面无入口。

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