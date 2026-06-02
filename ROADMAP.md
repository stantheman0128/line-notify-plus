# LINE Notify+ Roadmap

## 已完成 (v1.0.6)
- [x] 對話串模式 / Apple 分組模式
- [x] 快速回覆
- [x] 個別聊天室開關（社群/群組/個人）
- [x] 取代原始 LINE 通知
- [x] 頭貼顯示
- [x] 點擊跳轉 + 自動消失
- [x] App 內一鍵更新
- [x] 版本更新紀錄

## 短期 (v1.1) — 上架版（vc10 = 當前 closed testing 版本）
- [x] 重新設計 App Icon（不含 LINE 商標，適合上架 Google Play）
- [x] 移除 in-app updater（改走 Play Store 更新）
- [ ] 通知風格說明頁面（附截圖/動圖比較兩種模式）
- [ ] 滑動效能優化（release build + R8）
- [x] Q&A 問答頁面

## v1.1.1 / closed-testing 期間迭代（小修，可邊測邊改）
> 來源：2026-05-27 上架時 Stan 回報。explore agent 已定位根因。
- [x] **Icon 齒輪被圓形遮罩裁切**（v1.1.0 versionCode 9 已修）：在 foreground 外層加
      `pivot(54,54) scale(0.80)` wrapper，齒輪頂端從 Y=10 移到 ~Y=24，落進安全區。
      實機用 icon-test 專案 (s72/s80/s88 三 flavor) 驗證後採 0.80。512 PNG +
      feature graphic 已同步用 render_play_store_icon.mjs / build_feature_graphic.py 重渲染。
- [x] **「服務運行中」指示器 bug**：`MainActivity.kt:151` 綁的是 `isListenerEnabled`（系統權限），
      關掉「啟用增強通知」開關後指示器不消失。修法：改成 `isListenerEnabled && serviceEnabled`。
- [x] **文案改名「增強」→「堆疊版本」**：4 處（MainActivity.kt:226,239 / AboutActivity.kt:100 /
      LineNotificationListener.kt:385）。
- [x] **「啟用增強通知」描述改寫**：`MainActivity.kt:227` 從「攔截 LINE 通知並重新組合顯示」
      → 改成「將 LINE 訊息堆疊，而不只是顯示最新一條訊息」。
- [ ] **權限提示返回後不更新 bug**：Stan 回報從設定頁返回後不再提示「需要權限」。
      但 explore 發現 `MainActivity.kt:93-94` 有在 ON_RESUME 重新檢查 → **需實機重現才能確診根因**。
- [x] **移除 in-app GitHub updater**（vc10 已完成，commit 1b77c22）：原本 `UpdateChecker.kt` +
      About 頁「手動檢查更新」會從 GitHub Releases 下載 APK 自我安裝。vc9 上傳 Play Console 時
      因 REQUEST_INSTALL_PACKAGES 敏感權限被擋（違反 Google 自我更新政策），故提前移除（原規劃 Production 前才做）。
      已刪 UpdateChecker.kt + file_paths.xml + FileProvider；manifest 移除 REQUEST_INSTALL_PACKAGES + INTERNET。
      clean build 後 APK 權限只剩 BIND_NOTIFICATION_LISTENER_SERVICE + POST_NOTIFICATIONS（aapt2 驗證）。

### 🐛 tester 回報 bug（2026-06-01 closed testing 期間）
- [ ] **狀態欄浮窗回覆「卡住」**：用狀態欄 inline reply（RemoteInput）直接回覆時，有**很大機率卡住**
      （送不出去 / 一直轉圈）。⚠️ 注意：v1.0.6→v1.1.0 changelog 已宣稱用 `ReplyRelayReceiver`
      取代 `NotificationDismissReceiver` 修過「回覆後卡轉圈」，此回報代表**未完全修好或有 regression**。
      查根因方向：`ReplyRelayReceiver` 的 RemoteInput 取值 + reply action 的 PendingIntent
      回送 LINE 流程，需實機重現。
- [ ] **回覆訊息「我本人的頭貼」不顯示**：在狀態欄回覆後，重組通知會把我回覆的文字接進對話串，
      但傳送者（我自己）的頭貼沒正確帶上。可能是 MessagingStyle 的 reply `Message` 沒設
      `Person.icon`，或抓不到自己的 avatar。
- [ ] **回覆/已讀後通知不自動消失**：(a) 回覆某則訊息後通知不消失。(c) 開 App 點掉訊息（或已讀）後
      通知仍在；從通知點進訊息頁面後，通知似乎也還留著。需釐清 cancel 時機（回覆成功後 /
      App 標記已讀後 / 點擊跳轉後）與 `NotificationListenerService.cancelNotification()` 的呼叫點。

## 中期 (v1.2)
- [x] **多語言 i18n**：目前所有字串 hardcoded 在 Kotlin（res/ 只有 values/，無 values-en）。
      做法：抽字串到 strings.xml（繁中）+ values-en/strings.xml（英）+ 設定頁讓用戶選
      「跟隨系統 / 繁中 / English」，用 AppCompatDelegate.setApplicationLocales（per-app language API）。
- [x] **平板支援**：目前 App 能在平板上跑但未最佳化（單欄拉寬）。做法：用 WindowSizeClass
      判斷寬螢幕、加 sw600dp/sw720dp layout 變體、list-detail 雙欄。約半天～一天工。
      投報率低（通知工具 99% 手機使用），故排 v1.2。上架時平板截圖用手機圖置中代用。
- [ ] **通知風格示意圖**：對話串/Apple 分組旁加 info 按鈕，點開 dialog 顯示對比圖/GIF。
- [x] **FAQ / 新手教學**：首次開啟 onboarding，或設定頁加 FAQ，說明 App 能做什麼。
- [x] **分享功能**：share button 帶預設文案 + Play Store 連結推廣（需上架後拿到連結）。
- [ ] **權限引導強化**：首次使用、未授權時更明確引導跳轉到權限設定頁。
- [ ] **🎬 拍 App 介紹影片**：錄一支短影片介紹 App（對話串 vs Apple 分組、快速回覆、
      取代原始通知等核心功能）。用途：(1) 上傳 YouTube → 填進 Play Console 的 Video
      欄位（上架時跳過的選填欄）(2) 社群推廣（Threads / FB Nothing 社團）。
      建議 30-60 秒、直式手機畫面、實機操作 demo。
- [ ] **問題回報**（整合 LINE 官方帳號或 GitHub Issues）
- [ ] **功能許願池**
  - 贊助排名機制：付越多，許願的功能越優先
  - 可能實現方式：
    - LINE Pay / 綠界金流整合
    - 許願清單 + 贊助金額排序
    - 後端：Supabase（你已有帳號）存許願 + 贊助記錄
    - 前端：App 內嵌 WebView 顯示排行榜
- [ ] **通知處理邏輯可自選**（tester 建議 2026-06-01）：讓用戶自行選擇通知處理策略
      （例如「回覆後是否自動清除通知」），用設定頁開關取代寫死的行為。與 v1.1.1
      「回覆/已讀後通知不消失」bug 相關 — 先當 feature 規劃，給用戶選擇權。
- [ ] **雙開 LINE 來源區分**（tester 回報 2026-06-01）：用戶雙開（工作分身 / Dual Apps）兩個 LINE 時，
      目前無法區分訊息來自哪一個 LINE 帳號。雙開通常是不同 user profile 但 package 相同，
      可從 `StatusBarNotification` 的 `UserHandle` / `getUser()` 判斷來源，並在通知標題標示帳號。
- [ ] **收回訊息保留**（在通知中顯示已被收回的訊息）

## 長期 (v2.0)
- [~] 上架 Google Play（v1.1.0 進行中：closed testing 階段）
- [ ] 訊息搜尋（跨聊天室搜尋通知歷史）
- [ ] 自訂通知音效 / 震動模式
- [ ] Widget（桌面小工具顯示最近訊息）

## 許願池商業模式構想

```
使用者開啟 App → 功能許願池頁面
  → 看到其他人的許願 + 目前贊助金額排名
  → 自己許願 + 贊助（最低 $30 TWD）
  → 贊助越多排名越前
  → 開發者按排名優先實作

技術實現：
  前端：App 內 WebView → 你的網站
  後端：Supabase
    - wishes 表：wish_id, title, description, total_amount, status
    - sponsors 表：sponsor_id, wish_id, amount, line_user_id, created_at
  金流：LINE Pay 或綠界 ECPay
  通知：透過 LINE 官方帳號推播進度更新
```
