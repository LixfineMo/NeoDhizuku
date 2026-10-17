# NeoDhizuku

基於 [Dhizuku](https://github.com/iamr0s/Dhizuku) 的二次開發版本，把裝置擁有者（DeviceOwner）權限受控地分享給其他應用程式。

---

## ⚠️ 未完工警告

**目前版本開發中，請勿用於日常裝置或正式環境。**
**部分功能剛上線、部分未充分實機驗證。套件名稱已改為 `com.neodhizuku.more`，與官方版本**無法共存**（會成為不同的裝置擁有者），請先在測試機上驗證。
與官方版本的資料不相容，遷移前請自行備份。

---

## 一、二次修改說明與新功能

所有改動都掛在 `NeoDhizuku.UseNeoDhizuku` 總開關下（`app/src/main/java/com/neodhizuku/more/NeoDhizuku.kt`）。

> 設為 `false` 時所有子項目一律失效，行為**完全退回上游原始實作**；也支援單獨關閉某一子項目，方便對照除錯。

### 基礎識別

| 項目 | 上游 | 本專案 |
|---|---|---|
| 套件名稱 | `com.rosan.dhizuku` | `com.neodhizuku.more` |
| 應用程式名稱 | Dhizuku | NeoDhizuku |
| 系統支援 | Android 8.0+（編譯目標 API 37） | 同上 |

> Manifest 中的 API 權限名稱與 AIDL 套件名稱**維持原樣**，這是為了讓現有第三方客戶端無須修改即可識別本應用程式。

### 安全強化

- **授權入口不再信任用戶端自行回報的 UID**：改由伺服器端用 `Binder.getCallingUid()` 取得真實身分並簽發一次性權杖（10 分鐘有效），Activity 驗證通過後才認可該 UID。舊版用戶端走相容路徑，不會覆蓋既有授權。
- Provider 鑑權、`remoteProcess` 指令稽核、啟動時自身簽名驗證。
- 簽名白名單在**建置期**注入，未設定時自動跳過，方便自行編譯除錯。

### 介面重做

參考 KernelSU 管理端的版面配置思路，以 Material3 實作（**未引入 miuix / coui**）：

- **懸浮底部導覽列**：四頁共用，圖示 + 文字，**不使用 emoji**。未啟用時只顯示「首頁 / 設定」；子頁面不顯示底欄。
- **首頁狀態卡**：啟用後為「左側狀態卡 + 右側上下兩張小卡」——勾選圖示 + 已啟用 + 擁有者類型 + 版本號（點擊進入啟用頁），右上為已授權應用程式數量，右下為服務執行時間（`HH:MM:SS`，服務重啟歸零）。**未啟用時維持上游原樣式不變**。
- **軟體管理**：不分頁籤，所有與 Dhizuku 相關的應用程式都在同一清單中，開關直接顯示授權狀態。未在清單宣告權限但曾發出呼叫的應用程式（如部分安裝器）會自動登記進來，可直接開啟授權。
- **黑名單獨立頁面**：自軟體管理拆出，可逐項解除封鎖。
- **授權對話框**：無進度條；「允許」淺綠、「拒絕」紅色突出、「加入黑名單」黑色，均為純文字無背景框；倒數計時小字置於按鈕列下方。
- **呼叫記錄**：記錄各應用程式呼叫了哪些能力，全部以中文短句描述。**持久化保存，預設保留 30 天**；每筆記錄附帶當時的伺服器端執行日誌片段。另含「執行日誌」頁籤，可檢視伺服器端自身日誌，**未啟用時也能檢視**。
- **服務啟停**：右上角按鈕在執行中顯示為叉號，停止後變為重新啟動圖示；執行時間卡停止時整卡純紅顯示「拒絕服務」。停止後仍保留檢視授權與呼叫記錄的能力，其餘操作一律拒絕。
- **裝置擁有者轉移攔截**：第三方應用程式取得授權後可藉 `binderWrapper` 以裝置擁有者身分呼叫 DPM 任意方法，若其不提供授權入口，轉移後擁有者無法取回。設定項「允許第三方應用程式主動轉移裝置擁有者」**預設關閉**，關閉時直接拒絕並發出通知。
- **服務策略頁**：設定頁內的二級選單，集中管理 API 服務總開關、白名單模式、關閉服務後的處理方式，以及裝置擁有者轉移開關。
- **免刪帳號臨時啟用**：免去了上游「必須先移除全部帳號」的要求，詳見後文專章。
- **主題頁**：深色模式四檔（跟隨系統 / 淺色 / 深色 / 深色純黑 AMOLED）+ 動態取色開關，深色模式使用摺疊選擇器。
- **設定頁**：新增「管理」分區入口，**未啟用時整個分區隱藏**。
- **啟用錯誤提示**：英文原始錯誤對應為中文原因 + 處理建議 + 可複製診斷資訊。

### 偵錯日誌

統一 TAG `NeoDhizuku`，帶分類子標籤（`Nav` / `Theme` / `Perm` / `Home` / `Apps` / `Daemon` / `Server` / `Activate` / `UI` / `Boot`）。開關 `NeoDhizuku.DebugLog`，關閉時完全靜默且無字串串接開銷。

```bash
adb logcat -s NeoDhizuku:V              # 全部
adb logcat -s NeoDhizuku:V | grep Nav   # 只看導覽
```

---

## 二、備註與使用方法

### 啟用

與上游一致，支援 Shizuku、ADB、免刪帳號模式成為裝置擁有者。
**注意上游提示：建議在裝置上沒有任何帳號時設定**；若已有帳號可能需要先移除。

成為裝置擁有者後，第三方應用程式即可透過 Dhizuku API 申請使用裝置擁有者能力。

### 免刪帳號臨時啟用

上游建議在**裝置上沒有任何帳號時**設定，否則需先移除帳號。本方案繞開了這條限制。

**原理**：`DevicePolicyManagerService` 中，帳號檢查 `hasIncompatibleAccountsOrNonAdb` 只在 `isAdb` 分支內被讀取。原版走 ADB（`dpm set-device-owner`）即 `isAdb=true`，三道閘全讀；本方案以**應用程式自身 uid** 直接呼叫，`isAdb=false`，只校驗 `hasUserSetupCompleted`，**完全不讀取帳號**。

**七步驟流程**：

| 步驟 | 執行者 | 內容 |
|---|---|---|
| ① | shell | `cmd role set-bypassing-role-qualification true` |
| ② | shell | `cmd role add-role-holder --user 0 android.app.role.DEVICE_POLICY_MANAGEMENT <pkg>` |
| ③ | shell | `settings put secure user_setup_complete 0` |
| ④ | 應用程式自身 | 反射 `forceUpdateUserSetupComplete(0)`，把③的值搬進 DPM 記憶體狀態 |
| ⑤ | 應用程式自身 | 反射 `setActiveAdmin` |
| ⑥ | 應用程式自身 | 反射 `setDeviceOwner` |
| ⑦ | shell | `settings put secure user_setup_complete 1` |

③ 與 ④ **必須配對**：只做③無效，記憶體狀態不變、閘仍關閉。④ 的參數是 userId 而非狀態值，它把 Settings 的真實值雙向搬進記憶體，所以③不可省略。

**三個前提條件**：

- Shizuku 或 root 權限（執行 ①③⑦ 的 shell 指令，一般應用程式拿不到 `WRITE_SECURE_SETTINGS`）
- Android 13（API 33）及以上（`DEVICE_POLICY_MANAGEMENT` 角色自該版本引入）
- 尚未成為裝置擁有者

**副作用**：設為 0 期間三鍵導覽的首頁與最近任務鍵失效（返回鍵可用，手勢導覽不受影響）。程式碼在 `finally` 中必定還原，不會把裝置留在「未完成設定精靈」狀態。

**失敗排查**：角色若回傳 `RC=0` 但權限仍為 DENIED，多為清單未宣告 `MANAGE_DEVICE_ADMINS` / `MANAGE_PROFILE_AND_DEVICE_OWNERS`——這兩個是 signature 級權限，必須先宣告系統才會在授予角色時一併給予。頁面會直接給出該提示。

若設定精靈未還原，頁面會顯示紅色提示並停用前兩個步驟，只保留「手動還原」。

### 授權管理

- 第三方應用程式首次呼叫時彈出授權請求，可選擇允許、拒絕或加入黑名單。
- **加入黑名單後不再處理該應用程式的授權請求。**
- 「軟體管理」中可隨時開關某個應用程式的授權；「黑名單」頁可解除封鎖。
- **白名單模式**開啟後，僅白名單內的應用程式可呼叫高風險介面。

### 開關用法

編輯 `NeoDhizuku.kt`：

```kotlin
const val UseNeoDhizuku: Boolean = true   // 總開關，false = 全部退回上游行為
const val UseThemePage: Boolean = true    // 子項目：主題頁
const val DebugLog: Boolean = true        // 子項目：偵錯日誌
```

排查問題建議：先設 `UseNeoDhizuku = false` 編一版基準，再切成 `true` 對比；哪個子項目出問題就單獨關掉它定位。

### 服務策略

設定頁 →「服務策略」，三個分區：

**① API 服務**：總開關（允許其他應用程式使用 API 服務）、白名單模式（僅允許白名單應用程式使用 API 服務）。

**② 關閉服務後的處理方式**（抽屜式選擇，僅在服務被關閉後生效）：

| 選項 | 行為 |
|---|---|
| 拒絕危險服務並回傳失敗 | 可查詢到服務，非唯讀操作一律拒絕 |
| 拒絕危險服務並回傳成功 | 放行但不實際執行，只回傳成功 |
| 完全拒絕服務並回傳失敗 | 回傳 null，其他應用程式查不到服務 |
| 完全拒絕服務並回傳成功 | 回傳空結果 |
| 不做任何處理 | 掛起授權請求，用戶端無限等待 |

「不做任何處理」透過**不回呼**實作而非阻塞執行緒，因此不會影響服務處理其他呼叫。

**③ 裝置擁有者轉移**：即上文「裝置擁有者轉移攔截」的開關。

### 已知限制

- 懸浮底欄未改成左右滑動切頁的 Pager 結構，只能點擊切換。
- 主題使用 Material3 原生 `dynamicColorScheme`（Android 12+ 系統支援），未引入第三方配色庫。
- **與官方 Dhizuku 共存時**：硬編碼官方套件名稱 authority 的用戶端（如 amarok、爱玩机工具箱）會連到原版而非本應用程式，出現「原版彈窗、本應用程式不回應」的現象。ContentProvider authority 全域唯一，無法搶註，出路是移除原版後改用 `neoCompat` 或 `official` 分支。
- **免刪帳號啟用僅支援 Android 13+**，低版本頁面會顯示不支援並隱藏步驟。
- Shizuku API 14 將移除 `newProcess`，屆時免刪啟用中基於它的 shell 執行需遷移至 User Service。

---

## 三、建置方法

### 環境需求

Android Studio **Quail 4（2026.1.4）** 及以上（AGP 9.4.1 的最低要求），JDK 21。
SDK Manager 中需安裝 SDK Platform 37、Build Tools 37.0.0、NDK 29.0.14206865。

### 建置

專案有三個分支（product flavor），`namespace` 與原始碼套件名稱固定為 `com.neodhizuku.more`，
只有 `applicationId` 與 provider 的 authorities 隨分支變化：

| 分支 | applicationId | 註冊原版 authority | 與原版 Dhizuku 共存 | 相容硬編碼官方套件名稱的用戶端 |
|---|---|---|---|---|
| `neo` | `com.neodhizuku.more` | 否 | **能** | 否 |
| `neoCompat` | `com.neodhizuku.more` | 是 | 不能 | **能** |
| `official` | `com.rosan.dhizuku` | — 本身就是 | 不能（同套件名稱） | **能** |

`neo` 與 `neoCompat` 的 applicationId 相同，只能二選一安裝，可各編一份用於對比測試。

```
./gradlew assembleNeoDebug        共存版（建議日常使用）
./gradlew assembleNeoCompatDebug  接管版（需先移除原版 Dhizuku）
./gradlew assembleOfficialDebug   官方套件名稱版（需先移除原版 Dhizuku）

./gradlew assembleBothDebug       一次產出 neo + neoCompat 兩個偵錯包
./gradlew assembleAllDebug        一次產出三個偵錯包
```

發行版把 `Debug` 換成 `Release`（另有 `assembleAllRelease` 一次產出三個）。

> **重要**：裝置擁有者同時只能屬於一個套件名稱，`neoCompat` / `official` 都需要
> **先移除原版 Dhizuku**，否則 provider authority 衝突導致裝不上
> （`INSTALL_FAILED_CONFLICTING_PROVIDER`）。安裝後需重新啟用。

儲存庫內預設是偵錯簽章（`keystore/`），正式散佈前請替換為自己的簽章設定。

---

## 四、後期規劃（完成並測試後正式發布 Release 版本）

- [-] **授權入口改造**：讓所有第三方應用程式都能穩定發起授權請求。現況是部分用戶端（多為依賴舊版 API 的）既不彈窗也不發起請求；嘗試過的伺服器端接管方案實測無效，需先定位真實失敗點（用戶端是否呼叫 `requestPermission`、listener 能否收到回呼、通知是否真的發出）再重新設計。
- [√] ~~免刪帳號的裝置擁有者啟用~~ —— 已實作（見「免刪帳號臨時啟用」）
- [-] 授權入口「UID 自行回報」的實際可利用性以 Demo 實測（靜態上攻擊面存在，實際影響未確認）。
- [-] 授權彈窗權限範圍展示。
- [-] 委派作用域（delegated scopes）設定入口 —— API 已支援但介面無入口。

---

## 開源授權

本專案是 Dhizuku (GPL-3.0) 的衍生作品，整體繼續以 GPL-3.0 散佈，完整授權見儲存庫根目錄 `LICENSE`。

本專案修改自 [Dhizuku](https://github.com/iamr0s/Dhizuku)，在其基礎上修改了套件名稱、授權與啟用流程、安全強化與介面實作。完整修改記錄見儲存庫提交歷史。

| 上游專案 | 授權 | 說明 |
|---|---|---|
| Dhizuku | GPL-3.0 | 直接上游，本專案整體沿用其授權 |
| Dhizuku-API | MIT | 本地子模組引入，版權與授權聲明保留於 `Dhizuku-API/LICENSE` |
| KernelSU | GPL-3.0-or-later（`kernel/` 為 GPL-2.0-only） | 僅提供介面與互動靈感，未複用程式碼 |
| Shizuku API | Apache-2.0 | Maven 依賴 |

**致謝**：Dhizuku（iamr0s）、Dhizuku-API（R0S）、KernelSU、Shizuku（RikkaApps），以及所有為上游貢獻過程式碼與翻譯的參與者。
