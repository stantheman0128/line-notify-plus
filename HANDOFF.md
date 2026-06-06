# Project Handoff — LINE Notify+

## Latest Session: 2026-06-07（Claude Code → 交接給 Codex 接手 UI/設計）

> 給接手的 AI（Codex/其他）：**先讀 `AGENTS.md`（鐵則），再讀本檔，再看 `design/` 資料夾。**
> 這份聚焦「UI / 視覺設計 + 後續實作」的交接。

### 一、App 是什麼
LINE Notify+：`NotificationListenerService` 攔截 LINE 通知，重組成「堆疊 / 對話串」通知，支援快速回覆、雙開區分、通話守門。Kotlin + Jetpack Compose + Material3。**只用 2 個權限**（BIND_NOTIFICATION_LISTENER_SERVICE + POST_NOTIFICATIONS），無網路。

### 二、目前狀態
- **能跑**：debug build 正常，已裝在測試機（Nothing A059P，serial `001701527000969`）。
- **branch**：`fix/notification-behavior`，**工作區有大量未 commit 變更**（建議先 commit 一個乾淨基準再接手）。
- **versionCode = 11**（鐵則：不可重用；要上架再 bump）。

### 三、最近做了什麼（這次 session，皆已實機驗證）
- **首頁**：管理個別聊天室＝**外框按鈕**；官方帳號＋開啟權限＝**綠色主要按鈕**；頂列加 **logo + 標題**；移除「已讀後清除」開關（改永遠開啟）；「回覆後清除通知」改名＋新文案。
- **進階功能（灰色摺疊）**：通知風格、回覆後清除 各加 **ⓘ → 說明對話框**（GIF 目前是 placeholder 文字）。
- **聊天室**：**移除刪除鍵**；分類改 **全部/好友/群組/社群**（個人→好友）；加 **🔍 搜尋** + **ⓘ 說明 popup**（含「🔒 我們如何處理你的資料」隱私超連結）+ **長按多選**（✕/已選N/全選/啟用/停用）。
- **教學與 FAQ**：名稱統一；介紹改「LINE Notify+ 是什麼」（無破折號）；移除「通知風格說明」卡；FAQ 改**可展開**，加 **小米/OPPO/vivo/三星/華為** 後台常駐教學。
- **關於**：更新紀錄卡改**白底**（不再灰）。
- **通話守門**：來電/通話中/未接 通知 **直接放行不處理**（不存清單、不堆疊、不取消）— 已實機驗證。

### 四、關鍵設計決策（務必沿用，別推翻）
- **導航 = B 模型**（乾淨首頁 + 推送子頁），**不走底部 Tabs**（功能量還不夠；等「許願池/訊息搜尋」變大再升級）。
- **配色 = 混合**：主要按鈕/關鍵強調**鎖品牌綠** `Green40 (#06C755)`；其餘元素**跟系統動態取色（Material You）**。⚠️ 實機主色會依手機桌布變（這台是藍灰）—— 不是 bug。
- **元件命名**：一律用 `design/COMPONENTS.md` 的名稱（主要按鈕/外框按鈕/文字按鈕/圖示按鈕、開關/單選鈕/篩選標籤/下拉選單、狀態卡/白卡/灰卡、開關列/導航列/單選列/聊天室列…）。
- **刪除功能**：已移除（用每列開關停用即可）。**已讀後清除**：固定永遠開啟（不給開關）。

### 五、設計參考檔（全在 `design/`）
- `COMPONENTS.md` / `COMPONENTS.html` / `components-visual.png` — 元件目錄（含實機截圖 + 品牌綠對照）
- `STRUCTURE-template.md`（user 已填）/ `nav-diagram.png` — 結構樹 + Mermaid 導航圖
- `preview-b.html` / `preview-b.png` — B 模型預覽
- `real_home_v2.png` / `r2_advanced.png` / `r2_chat.png` / `r2_help.png` / `real_about_v2.png` — **現況實機截圖**（可當 Figma 起稿底圖）
- `../play-store-assets/play-store-icon-512.png` — App icon（Figma logo 用）

### 六、還沒做（下一步，依優先序）
1. **社群分類 bug**：LINE 社群通知 `android.subText = null` 且 `android.title = "發送者 · 社群名：…"`（用「 · 」分隔）。現在掉進「個人」分支被歸錯。修法：在 `LineNotificationListener.kt` 的 `onNotificationPosted` 類型判斷，`subText == null` 時若 `title` 含「 · 」→ 判為社群（社群名取「 · 」後段）。**請先實機抓一筆「測試用途」社群通知**確認結構（看 logcat `Log.v` 的「通知結構 … shortcutId= chatType=」）。
2. **GIF 示範**：進階 ⓘ 目前是 placeholder。用 `adb screenrecord` 錄實機（對話串 vs Apple、回覆後清除）→ ffmpeg 轉 GIF → 放進對話框。
3. **Figma 視覺定稿**：user 會在 Figma 排版，排好匯出 PNG 給你照著實作。

### 七、怎麼 build / 裝 / 測（會踩雷，務必照做）
- **build**（Windows 路徑含空格，直接跑 `gradlew.bat` 會炸）：
  `set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` 然後
  `java -cp gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug`
- **adb**：`C:\Users\stans\AppData\Local\Android\Sdk\platform-tools\adb.exe`，測試機 `001701527000969`
- **install -r 後一定要 rebind listener**（否則訊息不會跳）：
  `cmd notification disallow_listener <COMP>` 再 `allow_listener <COMP>`
  COMP = `com.stanslab.linenotify/com.stanslab.linenotify.service.LineNotificationListener`
- **行為類改動一定要實機驗證**：只看 `BUILD SUCCESSFUL` 會被騙 —— 這次就踩過 `painterResource(R.mipmap.ic_launcher)` 一啟動就 crash（adaptive icon 載不動）。

### 八、給接手 AI 的提示
- 鐵則看 `AGENTS.md`：versionCode 不可重用、只准 2 權限、別加無障礙/網路、別碰 keystore、R8 關著、在 branch 上做。
- Compose BOM **2024.12.01** / material3 **1.3.1**：用穩定 API。
- `painterResource` **不能載 adaptive icon**（`<adaptive-icon>` XML）；要 VectorDrawable 或 PNG/JPG/WEBP。logo 已改用 `res/drawable-nodpi/app_logo.png`。
- 主要按鈕鎖綠：`import com.stanslab.linenotify.ui.theme.Green40`，用 `ButtonDefaults.buttonColors(containerColor = Green40, contentColor = Color.White)`。

---
<!-- 之後的 session 把新記錄加在這行上方，舊的保留 -->
