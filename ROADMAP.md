# Notify+ Roadmap

## 已完成 (v1.0.6)
- [x] 對話串模式 / Apple 分組模式
- [x] 快速回覆
- [x] 個別聊天室開關（社群/群組/個人）
- [x] 取代原始 LINE 通知
- [x] 頭貼顯示
- [x] 點擊跳轉 + 自動消失
- [x] App 內一鍵更新（歷史功能；後因 Play 政策與無網路設計移除）
- [x] 版本更新紀錄

## 短期 (v1.1) — 歷史 closed-testing 階段（vc10）
- [~] 重新設計 App Icon（歷史版已移除文字商標，但目前仍是 LINE 綠＋白色聊天氣泡；既有退件後應視為再次送審 blocker，需建立獨立品牌）
- [x] 移除 in-app updater（改走 Play Store 更新）
- [x] 通知風格說明頁面（附截圖/動圖比較兩種模式）
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
- [x] **權限提示返回後不更新 bug**：`ON_RESUME` 會重新檢查 listener；2026-07-14 在 Android 16 實機
      關閉通知存取後返回，首頁立即顯示「服務未啟用／需要授權」，重新開啟後也立即恢復運行中。
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
- [x] **多語言 i18n**：已將字串抽到 strings.xml（繁中）+ values-en/strings.xml（英），設定頁可選
      「跟隨系統 / 繁中 / English」，用 AppCompatDelegate.setApplicationLocales（per-app language API）。
- [x] **平板支援**：已用 WindowSizeClass 與 sw600dp/sw720dp 變體提供寬螢幕雙欄。
- [x] **通知風格示意圖**：對話串/Apple 分組旁加 info 按鈕，點開 dialog 顯示對比圖/GIF。
- [x] **FAQ / 新手教學**：首次開啟 onboarding，或設定頁加 FAQ，說明 App 能做什麼。
- [x] **分享功能**：share button 帶預設文案 + Play Store 連結推廣（需上架後拿到連結）。
- [x] **權限引導強化**：首次使用、未授權時更明確引導跳轉到權限設定頁。
- [ ] **🎬 拍 App 介紹影片**：錄一支短影片介紹 App（對話串 vs Apple 分組、快速回覆、
      取代原始通知等核心功能）。用途：(1) 上傳 YouTube → 填進 Play Console 的 Video
      欄位（上架時跳過的選填欄）(2) 社群推廣（Threads / FB Nothing 社團）。
      建議 30-60 秒、直式手機畫面、實機操作 demo。
- [x] **問題回報**（整合 LINE 官方帳號或 GitHub Issues）
- [ ] **功能許願池**
  - 贊助排名機制：付越多，許願的功能越優先
  - 可能實現方式：
    - LINE Pay / 綠界金流整合
    - 許願清單 + 贊助金額排序
    - 後端：Supabase（你已有帳號）存許願 + 贊助記錄
    - 前端：App 內嵌 WebView 顯示排行榜
- [x] **通知清除設定**：回覆後清除仍可自由開關；v1.6.0 起已讀後清除固定開啟，升級時會修正舊版
      `false` 偏好，避免聊天室開啟偵測看似啟用卻不會清除。
- [x] **雙開 LINE 來源區分**（2026-06-03 已做，實機雙開驗證通過）：所有 map 改用
      `roomKey = profileKey(getUser()) + 聊天室名`；本人頭貼每帳號一張；偵測到 >1 帳號時通知標題前綴帳號來源。
- [ ] **收回訊息保留**（在通知中顯示已被收回的訊息）

### 使用者回饋修正（2026-07-14）
- [x] **v1.6.0 UI Refresh**：首頁重整服務狀態、主控制與並排通知風格卡；聊天室加入常駐搜尋、分類分區、
      狀態副標與批次入口，並把 Accessibility 聊天室開啟偵測接回新版卡片。Main／Chat／Help／About
      頂欄與背景統一，一般說明文字至少 12sp 並提高淺色次要文字對比。
- [~] **取代模式下直接開啟 LINE 後清除 Notify+**：v1.5.0 加入預設關閉的實驗性聊天室開啟偵測，
      只監聽既有 LINE package、比對頂部聊天室名稱與輸入區，連續兩次確認且 active room 唯一時才清除；
      不保存訊息本文、不模擬操作，同名／雙開歧義與未知畫面一律保留通知。純邏輯已有 JVM 測試，
      2026-08-29 已在 Nothing A065 / Android 16 / LINE 26.13.1 以真實訊息驗證：列表不誤清、進房二次確認後
      約 567ms 清除目標房且保留另一房。仍需同名／雙開、群組／OpenChat、Apple 分組、OPPO／realme 回歸，
      並完成 Play Accessibility declaration。
- [x] **Android 15+ 私密通知占位字重複轉發**：辨識 framework redacted clone；不建立「LINE」假聊天室、
      不重發占位內容、也不取消原始 LINE 通知。FAQ 補上 OPPO／realme 的「增強型通知／智慧通知隱藏」排查。
      系統在 callback 前移除的原文無法由一般 App 還原。
- [~] **個別聊天室完全靜音**：新版明確關閉聊天室後會取消原始 LINE 通知並清掉既有 Notify+ 通知，
      包含 `@all` 與直接標註本人。NotificationListener 是 posted 後才撤掉，部分 OEM 仍可能極短暫閃出提示；
      若 Android 先遮蔽內容，callback 不含聊天室名稱，App 無法判斷該則是否屬於已靜音聊天室。
      舊版 `disabled_chats` 保留「只顯示 LINE 原通知」原語意，避免升級後無預警漏訊息；新版使用獨立 key。
- [ ] **只擋 `@all`、保留直接 `@我`**：需先在 Nothing／OPPO／realme 各擷取兩種真實通知 extras；
      不用可見文字猜測，避免把一般聊天內容誤判為標註事件。
- [x] **社群分類防漂移 + 手動更正**：production 改用受單元測試的 classifier；已確認社群即使下一則缺少
      `line.square.notification` 也不降回群組，聊天室列可手動固定好友／群組／社群。
- [x] **Apple 分組通知安全 budget**：每聊天室最多 8 個 child、全 App 最多 24 個 child；只有在被淘汰房
      仍至少留一個 child 時才提交 eviction。若 24 個房各只有一則、沒有安全目標，本次訊息 fail-open 保留 LINE 原通知。
- [x] **LINE 雙 callback 安全合併**：Nothing／LINE 26.10.1 實測同一訊息會在 24ms 內送出 conversation 與
      legacy 兩個通知。只合併 exact tagged-first 形狀與完整 MessagingStyle／PendingIntent fingerprint；任何欄位、順序、
      時窗或 active source 重驗不符都 fail-open 保留原通知，避免把兩則真訊息誤刪。
- [ ] **同名／雙開聊天室設定隔離**：目前通知顯示以 profile+名稱分房，但分類、靜音、最後活躍與頭貼的
      持久設定仍主要以顯示名稱為 key；同名聊天室可能共用設定。後續需引入 package+profile+shortcut 的 roomId schema。
- [ ] **通知核心的自動化整合測試**：目前 50 個 JVM 測試涵蓋純分類／偏好遷移／mirror 配對／ChatRoom／Accessibility 配對／舊 snooze 遷移，但 listener 的 burst、
      SystemUI active 查詢失敗、快速回覆與 eviction transaction 仍主要靠 code review＋實機；應再抽出純 planner/state machine 測試。
- [ ] **長時間壓力與 I/O**：頭貼 PNG 目前會在通知 callback 同步壓縮寫檔；thread 模式也沒有全域 active room
      budget。需用大量聊天室／訊息做 soak test，再決定背景寫入與 thread budget。

## 建置 / 上架雜項（Play Console 警告，低優先）
> 來源：2026-06-23 上傳 vc12 (1.2.0) 到 Play Console 時跳的 2 個 warning。兩個都是「建議」非 error，不擋上架。

- [實測無解，忽略] **native debug symbols**：AAB 夾帶 androidx 依賴帶進來的 `libandroidx.graphics.path.so`，Console 建議上傳 native debug symbols。**2026-06-30 vc14 實測：加 `ndk { debugSymbolLevel = "FULL" }` 也消不掉** —— 翻開 AAB 確認 BUNDLE-METADATA 無任何 debug symbols，因唯一的 .so 是 androidx 預編譯且已 strip 的，沒符號可抽、AGP 包不進去。**此警告本專案無解、無害、直接忽略**（App 無自家原生碼，不會在原生層當機）；`debugSymbolLevel=FULL` config 已留著，將來真有自家 native code 才有用。
- [預期，不處理] **無 deobfuscation 檔（R8 mapping）**：因 `isMinifyEnabled = false`（鐵則 6，故意關 R8 怕 release crash），沒混淆就沒對照表可傳，這警告本來就會出現。crash 的 stack trace 不混淆、本來就可讀，直接忽略即可。除非哪天決定開 R8，才需要連 mapping 一起傳。

## 長期 (v2.0)
- [~] 上架 Google Play（目前整合候選為 v1.6.0 / vc32；重新送審前仍須完成 Accessibility API 聲明、
      操作示範影片、實機回歸與線上隱私政策部署，並確認 vc31 未被使用；詳見 play-store-assets/play-console-progress.md）
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
