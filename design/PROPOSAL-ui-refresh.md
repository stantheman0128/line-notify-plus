# PROPOSAL — UI Refresh（功能零變動的介面改版）

給接手實作的 AI agent。**先讀 `AGENTS.md`（鐵則）與 `HANDOFF.md`，再讀這份。**
視覺參考：`design/ui-refresh.html`（用瀏覽器開，內含 before/after 對照與設計 token）

- 提案日期：2026-08-29
- 提案範圍：`MainActivity.kt`、`ChatManagementActivity.kt`、`ui/theme/*`
- **不動**：`LineNotificationListener.kt`、`NotificationClassifier.kt`、`ReplyRelayReceiver.kt`、`AndroidManifest.xml`

---

## 0. 這份提案的核心主張

Notify+ 的功能已經完整，問題出在**排版**：

- 首頁所有元素都用 `Arrangement.spacedBy(16.dp)` 排成等寬等重的方塊，沒有層級。
- 產品最核心的選擇（對話串 vs Apple 分組）被摺在 `AdvancedSettingsCard` 的收合區，新使用者不會展開。
- 全頁視覺最強的是綠色實心的「加入官方帳號」按鈕，但那是最不重要的動作。
- `dynamicColor = true` 讓 Android 12+ 用桌布取色蓋掉品牌綠——大多數使用者看到的根本不是 LINE 綠。

**這次只重排、只換色，不新增能力。**

---

## 1. ⛔ 硬約束（違反就是做錯）

除了 `AGENTS.md` 的六條鐵則，這個任務額外約束：

1. **不新增任何設定項、頁面或功能。** 唯一例外見 §5.1（那是修復既有 orphan，不是新功能，且需 Stan 拍板）。
2. **不新增權限。** 完成後權限必須仍只有 `BIND_NOTIFICATION_LISTENER_SERVICE` + `POST_NOTIFICATIONS`。
3. **不動通知組裝邏輯。** 這次不需要跑 `testDebugUnitTest` 以外的驗證，但也**不准**改到 `service/` 底下任何檔案。
4. **保留所有既有的 enabled/disabled 連動。** 特別是 `featuresEnabled = serviceEnabled` 對通知風格與清除開關的 gating。
5. **保留 `onNotificationStyleChange` 裡的 `clearAllEnhancedNotifications()` 呼叫。** 切換風格時若沒清掉舊通知，同聊天室會重複顯示。這是既有的正確行為，重構時很容易弄丟。
6. **⚠️ 別把「通知風格說明」卡加回 `HelpActivity`。** master 的 `309cf52` 已刻意移除（見 `AGENTS.md` 程式碼地圖）。本提案把風格選擇放在**首頁**，兩者不衝突，別搞混。
7. **新增字串一律進 `values/` 與 `values-en/` 兩份。**
8. **開自己的 branch，帶日期**，例：`feat/ui-refresh-2026-08-29`。禁止碰 `master`、禁止自己 merge、禁止上傳 Play。

---

## 2. ⚠️ repo 內的資訊落差（動手前先確認）

`AGENTS.md` 有幾處已過期，**以程式碼為準**：

| 項目 | `AGENTS.md` 寫的 | 實際（權威來源） |
|---|---|---|
| versionCode | vc16 | **17**（`app/build.gradle.kts:27`） |
| versionName | 1.2.1 | **1.3.1**（`app/build.gradle.kts:28`） |
| 單元測試數 | 38 | 42（`play-console-progress.md`） |

另外 **`design/` 底下的 `r2_*.png`、`real_*_v2.png` 全部過期**——still「LINE Notify+ / 啟用增強通知」，
跟現在的 `strings.xml` 對不上。**不要拿那些截圖當現況依據**，要看現況請讀 source。

---

## 3. Phase 1 — Theme（先做這個，投報率最高）

**檔案**：`ui/theme/Color.kt`、`ui/theme/Theme.kt`
**單獨一個 commit。** 這一步做完就能實機看到差異。

### 3.1 關掉 dynamicColor

```kotlin
fun LineNotifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // ← 從 true 改成 false
    content: @Composable () -> Unit
)
```

**理由**：目前 Android 12+ 會用使用者桌布取色，`Green40` 根本沒被套用。關掉之後綠色才會真的出現，
Play 截圖也才會跟實機一致。

**這是使用者可感知的變化**（原本跟著桌布變色的人會發現顏色固定了）。這是刻意的取捨，
請在 changelog 用白話寫清楚。

### 3.2 綠色拆成四階

單一個 `#06C755` 撐不起整個介面：**白字放在上面對比只有 2.26:1**，遠低於 WCAG AA 的 4.5:1。
現在那顆綠色實心按鈕就是這個狀況。解法不是換掉綠色，是分階。

```kotlin
// 品牌四階綠。括號內為對白底的對比值，已驗算。
val BrandGreen  = Color(0xFF06C755) // 標誌、狀態點。裝飾用，不承載文字
val ActionGreen = Color(0xFF05A847) // 開關軌道、選取邊框（3.1:1，達 UI 元件門檻）
val FillGreen   = Color(0xFF05873C) // 實心按鈕底，白字（4.6:1，過 AA）
val InkGreen    = Color(0xFF04672C) // 白底上的綠色文字（7.0:1）
val DarkGreen40 = Color(0xFF3DDC84) // 深色模式用，#06C755 在深底上會顯髒
```

既有的 `LightGreen #F0F9F2` + `DarkGreen #1B3A21` 這組 container 對比 11.7:1，直接沿用。
`Green30 #059B43` 可保留或併入 `ActionGreen`。

### 3.3 套用位置

| 元件 | 用哪一階 |
|---|---|
| App logo mark、「服務運行中」狀態點 | `BrandGreen` |
| `Switch` 開啟態、選取卡邊框 | `ActionGreen` |
| 實心按鈕（`PermissionButton` 等） | `FillGreen` + 白字 |
| 綠色文字（聊天室狀態、「前往設定」） | `InkGreen` |

`MainActivity.kt` 目前有兩處硬寫 `Green40`：`PermissionButton`（L445）與 `OfficialAccountButton`（L904）。
前者改用 `FillGreen`；後者依 §4.4 會變成列表列，不再需要按鈕色。

### 3.4 深色模式

定義自己的 `DarkColorScheme`，別依賴 `dynamicDarkColorScheme`。深色底上的綠用 `DarkGreen40`。

---

## 4. Phase 2 — 首頁層級重構

**檔案**：`MainActivity.kt`
**單獨一個 commit。**

目標的視覺層級（由重到輕）：**主開關 → 通知風格 → 清除時機 → 入口列表 → 狀態**。
對照圖見 `design/ui-refresh.html` 的「首頁（已授權）」。

### 4.1 `ServiceStatusCard` → 依狀態改變份量

現況：不論正常或異常，都是一整塊 `primaryContainer`（或 `errorContainer`），佔掉首頁最上方最大的空間。
但服務正常時它不需要說話。

改成兩種形態：

- **正常（`isListenerEnabled && serviceEnabled`）**：縮成一行 `ServiceStatusStrip`
  ——中性灰底 + `BrandGreen` 小圓點 + 「服務運行中」+ 右側淡色「正在接聽 LINE 通知」。
  **注意**：品牌色已經是綠色，狀態條不要再用綠底，否則兩個綠互相稀釋、失去訊號強度。用中性底配綠點。
- **異常**：不要另外畫狀態卡，直接由 §4.2 的權限卡承擔。

保留既有三種文案：`service_running` / `service_disabled` / `service_stack_disabled` / `service_needs_permission`。
（「已授權但主開關關閉」這個中間態仍要顯示 `service_stack_disabled`。）

### 4.2 狀態卡 + 權限卡合併

現況未授權時會**同時出現兩張卡講同一件事**：紅色的「✗ 服務未啟用」（不帶任何資訊或動作）
和底下的 `PermissionGuideCard`「完成 2 個權限設定」。第一張純粹是重複。

合併成單一張警示卡：

- 沿用 `PermissionGuideCard` 為基礎，保留兩個 `PermissionStepRow` 與 `StatusPill`。
- 已完成的步驟收成綠勾，待辦的步驟直接掛上前往設定的動作（目前使用者要自己把卡片和底下的按鈕對應起來）。
- 主要按鈕（`PermissionButton` / `PostNotificationsButton`）維持在卡片下方，用 `FillGreen`。
- **維持既有邏輯**：`isListenerEnabled == false` 時不 render `SettingsCard`（L357、L383）。這條不要改。

### 4.3 `SettingsCard` → 主控制卡 + 從屬子項

現況兩個 toggle 用 `HorizontalDivider` 平行並列，視覺份量一樣。但「取代原始通知」在邏輯上依賴主開關
（已經有 `enabled = serviceEnabled`），後果也更大（會蓋掉 LINE 自己的通知）。

改成：

- 「啟用完整通知」獨立成一張白卡，是全頁唯一有陰影、字級最大的元件。移除「設定」這個標題
  （`settings_title` 在新版沒有位置，卡片本身就是設定）。
- 「取代原始通知」縮排在其下方，左側加一條垂直分隔線表示從屬，`Switch` 用較小尺寸。
- **保留 ⓘ 圖示與 `replace_original_info_*` dialog**，那段說明（解釋為什麼在 LINE 內讀不會自動消失）是重要的客服前置。
- 主開關關閉時，子項照既有行為淡化（`enabled = serviceEnabled`）。

### 4.4 `AdvancedSettingsCard` → 拆解

這是這次改版的重點。摺疊區整個拿掉，內容依重要性重新安置：

**a. 通知風格 → 提到首頁第二順位**

用 `notification_style_title` 當分組標題，兩個選項從 `StyleOption`（radio 列）改成**兩張並排的選擇卡**，
卡內放一張極簡的通知縮圖示意（對話串 = 一則含多行；Apple 分組 = 多則堆疊）。

- 保留 `SectionLabelWithInfo` 的 ⓘ → `NotificationStyleGuideDialog`。
- 保留每個選項自己的 ⓘ → `style_help_thread_body` / `style_help_apple_body` + `info_demo_pending`。
- 保留 `enabled = featuresEnabled` 的 gating。
- **保留 `onNotificationStyleChange` 內的 `clearAllEnhancedNotifications()`**（見硬約束 5）。

**b. 通知清除時機 → 獨立分組**

`strings.xml` 裡 **`clear_timing_title`（「通知清除時機」）已定義但從未被使用**。用它當分組標題，
把清除相關的開關收在同一組，不要繼續平鋪。

**c. 語言 → 移進入口列表**

`LanguageDropdown` 目前是一顆滿版 `OutlinedButton`，份量過重。改成入口列表中的一列，
右側顯示目前值（系統語言 / 繁中 / English），點擊展開既有的 `DropdownMenu`。

### 4.5 三顆按鈕 → 一組入口列表

`ManageChatsRow`、`HelpEntryButton`、`OfficialAccountButton` 現在是三顆等寬按鈕，
其中官方帳號還是最搶眼的綠色實心——視覺重量完全倒置。

改成單一個列表容器，四列：管理個別聊天室 / 語言 / 教學與 FAQ / 加入 Notify+ 官方帳號。
`OfficialAccountButton` 的 `openExternalUri(line_official_url)` 行為不變。

### 4.6 平板雙欄

`useTwoPane` 分支（L270-336）要跟著改，維持相同分組：
左欄放狀態 + 主控制 + 通知風格，右欄放清除時機 + 入口列表。別讓兩欄的層級規則不一致。

---

## 5. Phase 3 — 聊天室管理

**檔案**：`ChatManagementActivity.kt`
**單獨一個 commit。**

### 5.1 列的副標：顯示狀態，而不是分類

現況（L497-507）副標的優先序是：`legacyOriginalOnly` → `manuallyClassified` → 分類標籤。
問題是**分類已經由上方的 filter chip 和分區標題表達過了**，在列上重複佔位；
而 **`chat_status_enabled`（「已啟用完整通知」）與 `chat_status_disabled`（「已完全靜音」）兩個字串已定義但從未使用**。

建議副標改為：

1. `legacyOriginalOnly` → 維持 `chat_legacy_original_only`，並改用**琥珀色**強調。
   這是 v1.3.0 最容易讓人困惑的狀態（關了卻還會跳 LINE 原通知），要能一眼掃出哪幾間需要重開再關。
2. 否則 → `chat_status_enabled` / `chat_status_disabled`，依 `chat.enabled` 決定，啟用時用 `InkGreen`。
3. `manuallyClassified` → 從副標移到名稱旁的小 tag（`chat_type_manual` 的「手動」）。

### 5.2 依分類分區

長清單改成依分類分區並顯示數量（好友 · 34 / 群組 · 12 / 社群 · 8）。
幾百間聊天室時這是唯一能維持可讀性的做法。分區資料用既有的 `chat_section_*` 字串。

### 5.3 搜尋常駐、批次選取給明顯入口

- 搜尋從放大鏡後面拉出來變成常駐輸入框——聊天室多時搜尋是主要導覽方式。
- 批次選取（`chat_select_all` / `chat_bulk_enable` / `chat_bulk_disable`）**已經實作**，
  但目前只能靠長按進入。在 top bar 補一個明顯的入口。**不要重寫既有的選取邏輯**，只補入口。

---

## 6. 🔴 需要 Stan 拍板的兩件事

實作前先問，不要自己決定。

### 6.1 `clearAfterRead` 是 orphan，要不要補回 UI？

**現況是一個 bug**：`MainActivity.kt` 讀取了 `clearAfterRead`（L151）、有 `onClearAfterReadChange`（L183）、
一路傳進 `AdvancedSettingsCard`（L322、L373）、也宣告成參數（L611、L616）——
**但 `AdvancedSettingsCard` 內從來沒有 render 對應的 toggle**。
`clear_after_read_title` / `clear_after_read_subtitle` 兩個字串同樣定義了但沒被用。

也就是說「已讀後自動清除通知」這個設定**存在於 prefs（預設 true）、有行為、但使用者無法關掉**。
`ROADMAP.md` 把它列為 2026-06-03 已完成，但 UI 那一半掉了。

- 選項 A：補上 toggle（放進 §4.4b 的「通知清除時機」分組）。這是**修復**不是新增功能，但會讓使用者看到一個「新」開關。
- 選項 B：這次不碰，只重排，另開 issue 處理。

**預設走 A**，因為 §4.4b 本來就要做那個分組，而且 prefs 與 handler 都已存在，成本近乎零。但請先確認。

### 6.2 文案要不要一起調整？

以下屬於 copy 而非功能，效果好但會動到 `strings.xml`（兩份都要加）。**列為選配 Phase 4**，可以整段跳過：

- 未授權卡標題從失敗導向的「服務未啟用」改成進度導向的「還差 1 步就會開始運作」。
- 首頁底部常駐一行隱私聲明。這句話 `chat_info_privacy_body` 裡已經有了，只是埋在聊天室說明的 dialog 深處
  ——「Notify+ 沒有網路權限，訊息不會離開這台裝置」是這個 App 最強的賣點，不該只出現在深層 dialog。

---

## 7. 收工：版本三件套（鐵則 1，缺一不可）

三件必須在**同一個 commit**：

1. `app/build.gradle.kts`：`versionCode = 17` → **18**
2. `app/build.gradle.kts`：`versionName = "1.3.1"` → **"1.4.0"**
   （介面全面改版屬於 minor，不是 patch）
3. `AboutActivity.kt` 加一條 `ChangelogEntry("v1.4.0", …)` + 對應 `changelog_*` 字串，**中英兩份**

changelog 用白話寫，別寫技術術語。建議內容：

- 介面全面改版，重要的設定不再被摺疊起來
- 通知風格（對話串 / Apple 分組）移到首頁，一眼就能切換
- 聊天室清單改成顯示目前狀態，並依好友／群組／社群分區
- App 顏色固定為品牌綠，不再跟著手機桌布變色

> ⚠️ bump 版號 ≠ 發版到 Play。前者是你收工的義務，後者只有 Stan 能做。

---

## 8. 驗收標準

- [ ] `./gradlew.bat assembleDebug` 綠燈
- [ ] `./gradlew.bat testDebugUnitTest --rerun` 全過（本次不該影響測試，但要確認沒弄壞）
- [ ] `service/` 底下**零改動**（`git diff --stat` 自證）
- [ ] `AndroidManifest.xml` 零改動，權限仍只有 2 個
- [ ] 沒有 hardcode 中文在 `.kt`，新字串都進了 `values/` + `values-en/`
- [ ] §9 的功能對照清單逐項確認，沒有任何控制項消失
- [ ] 版本三件套在同一個 commit
- [ ] 在 `feat/ui-refresh-2026-08-29` 之類的自建 branch 上，master 未被碰過

---

## 9. 功能對照清單（防止改版時弄丟東西）

實作完成後逐項核對，每一項都必須還在：

| 控制項 | 改版後位置 |
|---|---|
| 啟用完整通知（主開關） | 首頁主控制卡 |
| 取代原始通知 + ⓘ dialog | 主開關的縮排子項 |
| 對話串 / Apple 分組切換 | 首頁通知風格選擇卡 |
| 風格總說明 dialog（`NotificationStyleGuideDialog`） | 分組標題旁 ⓘ |
| 各風格個別說明 dialog | 各選擇卡上的 ⓘ |
| 切換風格時清除舊通知 | 行為不變（硬約束 5） |
| 回覆後清除通知 | 「通知清除時機」分組 |
| 已讀後自動清除通知 | 同上（**視 §6.1 決議**） |
| 語言（系統 / 繁中 / English） | 入口列表，右側顯示現值 |
| 管理個別聊天室 | 入口列表 |
| 教學與 FAQ | 入口列表 |
| 加入 Notify+ 官方帳號 | 入口列表 |
| 分享 App / App 資訊 | 頂欄右側，不變 |
| 服務狀態三種文案 | 正常＝狀態條，異常＝警示卡 |
| 權限兩步驟 + StatusPill | 合併後的警示卡內 |
| 開啟通知存取 / 允許通知發送 | 警示卡下方主要按鈕 |
| 聊天室 filter chip / 排序 / 搜尋 | 搜尋改常駐，其餘不變 |
| 聊天室開關 / 批次選取 / 刪除 / 更正分類 | 邏輯不變，僅重繪與補入口 |
| 平板雙欄 | 同分組規則（§4.6） |

---

## 10. 實機檢查清單（交給 Stan）

UI 改版可用 build 驗證，但下列項目請 Stan 在 Nothing Phone 上實際確認：

1. 主開關關閉時，「取代原始通知」與通知風格是否正確淡化。
2. 切換對話串 ↔ Apple 分組後，舊通知有被清掉，同聊天室沒有重複卡片。
3. 從系統設定關閉通知存取權後返回 App，首頁是否立即變成警示卡（`ON_RESUME` 既有行為）。
4. 深色模式下四階綠是否都看得清楚，特別是聊天室列的狀態文字。
5. 平板 / 橫向雙欄排版沒有破版。
6. 切換語言為 English 後，所有新字串都有英文，沒有掉回中文。
