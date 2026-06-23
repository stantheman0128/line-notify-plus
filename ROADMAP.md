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

### 🐛 tester 回報 bug（2026-06-01）→ 2026-06-03 實機修復完成（分支 fix/notification-behavior）
- [x] **狀態欄浮窗回覆「卡住」**：根因 = `ReplyRelayReceiver` 只「轉發+取消」，跟系統的樂觀回覆搶輸。
      改成回覆交給 service 的 `handleUserReply`：先「重貼(update)」接管通知並停 spinner。實機不再卡。
- [x] **回覆「我本人的頭貼」不顯示**：先給綠色預設頭貼 `ic_self_avatar`，再從 LINE 的 MessagingStyle 通知
      `extractMessagingStyleFromNotification().user.icon` 自動抓「真實本人頭貼」，且**每帳號一張**。
- [x] **回覆/已讀後通知不自動消失**：`onNotificationRemoved` 統一處理「本機通知被移除 → 清整組 + buffer」
      （滑掉/點掉/回覆都清）；加「回覆後清除」「已讀後清除」兩開關。
      ⚠️ 唯一先天限制：取代模式下「**直接在 LINE App 讀**」收不到已讀訊號（我們已殺掉 LINE 原通知），無法自動清。

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
- [x] **通知處理邏輯可自選**（2026-06-03 已做）：設定頁加「回覆後自動清除」「已讀後自動清除」兩開關，
      取代寫死行為，給用戶選擇權。
- [x] **雙開 LINE 來源區分**（2026-06-03 已做，實機雙開驗證通過）：所有 map 改用
      `roomKey = profileKey(getUser()) + 聊天室名`；本人頭貼每帳號一張；偵測到 >1 帳號時通知標題前綴帳號來源。
- [ ] **收回訊息保留**（在通知中顯示已被收回的訊息）

## 建置 / 上架雜項（Play Console 警告，低優先）
> 來源：2026-06-23 上傳 vc12 (1.2.0) 到 Play Console 時跳的 2 個 warning。兩個都是「建議」非 error，不擋上架。

- [ ] **native debug symbols**：AAB 夾帶 androidx 依賴帶進來的 `libandroidx.graphics.path.so`，Console 建議上傳 native debug symbols，方便分析原生層 crash/ANR。下一版（vc13+）在 `app/build.gradle.kts` 的 `release {}` 加一行 `ndk { debugSymbolLevel = "FULL" }` 即可消除。投報率低（這種小工具幾乎不會在原生層當機），有空再做。
- [預期，不處理] **無 deobfuscation 檔（R8 mapping）**：因 `isMinifyEnabled = false`（鐵則 6，故意關 R8 怕 release crash），沒混淆就沒對照表可傳，這警告本來就會出現。crash 的 stack trace 不混淆、本來就可讀，直接忽略即可。除非哪天決定開 R8，才需要連 mapping 一起傳。

## 長期 (v2.0)
- [~] 上架 Google Play（v1.2.0 / vc13 進行中：2026-06-23 vc12 因 Impersonation policy 退件 → 已改名「Notify+」、重做 feature graphic、商店描述去 LINE 主打，待重交 Production；詳見 play-store-assets/play-console-progress.md）
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
