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

## 短期 (v1.1) — 已上架版
- [x] 重新設計 App Icon（不含 LINE 商標，適合上架 Google Play）
- [ ] 通知風格說明頁面（附截圖/動圖比較兩種模式）
- [ ] 滑動效能優化（release build + R8）
- [ ] Q&A 問答頁面

## v1.1.1 / closed-testing 期間迭代（小修，可邊測邊改）
> 來源：2026-05-27 上架時 Stan 回報。explore agent 已定位根因。
- [x] **Icon 齒輪被圓形遮罩裁切**（v1.1.0 versionCode 9 已修）：在 foreground 外層加
      `pivot(54,54) scale(0.80)` wrapper，齒輪頂端從 Y=10 移到 ~Y=24，落進安全區。
      實機用 icon-test 專案 (s72/s80/s88 三 flavor) 驗證後採 0.80。512 PNG +
      feature graphic 已同步用 render_play_store_icon.mjs / build_feature_graphic.py 重渲染。
- [ ] **「服務運行中」指示器 bug**：`MainActivity.kt:151` 綁的是 `isListenerEnabled`（系統權限），
      關掉「啟用增強通知」開關後指示器不消失。修法：改成 `isListenerEnabled && serviceEnabled`。
- [ ] **文案改名「增強」→「堆疊版本」**：4 處（MainActivity.kt:226,239 / AboutActivity.kt:100 /
      LineNotificationListener.kt:385）。
- [ ] **「啟用增強通知」描述改寫**：`MainActivity.kt:227` 從「攔截 LINE 通知並重新組合顯示」
      → 改成「將 LINE 訊息堆疊，而不只是顯示最新一條訊息」。
- [ ] **權限提示返回後不更新 bug**：Stan 回報從設定頁返回後不再提示「需要權限」。
      但 explore 發現 `MainActivity.kt:93-94` 有在 ON_RESUME 重新檢查 → **需實機重現才能確診根因**。
- [ ] **⚠️ 上 Production 前必處理：移除/停用 in-app GitHub updater**：`UpdateChecker.kt` +
      About 頁的「手動檢查更新」會從 GitHub Releases 下載 APK 自我安裝。Play Store 上架後
      這會：(1) 與 Play 更新形成雙頭馬車 (2) 可能違反 Google「Device and Network Abuse」政策
      （正式版不得用非 Play 管道自我更新）。closed testing 階段不會觸發（GitHub 仍停 v1.0.6），
      但**推 Production 前一定要把這功能的 UI 拿掉或停用**。
      備註：updater 認 versionName 字串（isNewer 比 "1.1.0"），不認 versionCode。

## 中期 (v1.2)
- [ ] **多語言 i18n**：目前所有字串 hardcoded 在 Kotlin（res/ 只有 values/，無 values-en）。
      做法：抽字串到 strings.xml（繁中）+ values-en/strings.xml（英）+ 設定頁讓用戶選
      「跟隨系統 / 繁中 / English」，用 AppCompatDelegate.setApplicationLocales（per-app language API）。
- [ ] **平板支援**：目前 App 能在平板上跑但未最佳化（單欄拉寬）。做法：用 WindowSizeClass
      判斷寬螢幕、加 sw600dp/sw720dp layout 變體、list-detail 雙欄。約半天～一天工。
      投報率低（通知工具 99% 手機使用），故排 v1.2。上架時平板截圖用手機圖置中代用。
- [ ] **通知風格示意圖**：對話串/Apple 分組旁加 info 按鈕，點開 dialog 顯示對比圖/GIF。
- [ ] **FAQ / 新手教學**：首次開啟 onboarding，或設定頁加 FAQ，說明 App 能做什麼。
- [ ] **分享功能**：share button 帶預設文案 + Play Store 連結推廣（需上架後拿到連結）。
- [ ] **權限引導強化**：首次使用、未授權時更明確引導跳轉到權限設定頁。
- [ ] 問題回報（整合 LINE 官方帳號或 GitHub Issues）
- [ ] **🎬 拍 App 介紹影片**：錄一支短影片介紹 App（對話串 vs Apple 分組、快速回覆、
      取代原始通知等核心功能）。用途：(1) 上傳 YouTube → 填進 Play Console 的 Video
      欄位（上架時跳過的選填欄）(2) 社群推廣（Threads / FB Nothing 社團）。
      建議 30-60 秒、直式手機畫面、實機操作 demo。

## 中期 (v1.2)
- [ ] 問題回報（整合 LINE 官方帳號或 GitHub Issues）
- [ ] 功能許願池
  - 贊助排名機制：付越多，許願的功能越優先
  - 可能實現方式：
    - LINE Pay / 綠界金流整合
    - 許願清單 + 贊助金額排序
    - 後端：Supabase（你已有帳號）存許願 + 贊助記錄
    - 前端：App 內嵌 WebView 顯示許願排行榜
- [ ] 收回訊息保留（在通知中顯示已被收回的訊息）

## 長期 (v2.0)
- [ ] 上架 Google Play
- [ ] 訊息搜尋（跨聊天室搜尋通知歷史）
- [ ] 自訂通知音效 / 震動模式
- [ ] Widget（桌面小工具顯示最近訊息）
- [ ] 多語言支援（英文/日文）

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
