# Roadmap 驗證總表

更新日期：2026-06-02

這份文件整理目前由 Codex 推進的 roadmap PR、驗證方式與剩餘實機測試項目。所有 build 驗證都使用：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
cmd /c gradlew.bat assembleDebug
```

權限驗證都使用 debug APK：

```powershell
aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk
```

期望結果只允許：

- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`
- `android.permission.POST_NOTIFICATIONS`

## 已開 PR

| PR | Branch | Base | 類型 | 內容 | 驗證狀態 |
|---|---|---|---|---|---|
| #1 | `feat/roadmap-ui-i18n-tablet` | `master` | A 層 + B checklist | 服務狀態 bug、堆疊版本文案、i18n、FAQ/onboarding、分享、平板雙欄、B 層實機 checklist | `assembleDebug` 通過；APK 權限只剩允許的 2 個 |
| #2 | `feat/notification-style-visual-guide` | `feat/roadmap-ui-i18n-tablet` | A 層 | 通知風格 info dialog 與 Help 頁靜態示意圖 | `assembleDebug` 通過；APK 權限只剩允許的 2 個 |
| #3 | `feat/permission-guidance` | `feat/roadmap-ui-i18n-tablet` | A 層 | 首次使用 / 未授權時的通知存取與通知發送權限引導 | `assembleDebug` 通過；APK 權限只剩允許的 2 個 |
| #4 | `feat/problem-reporting` | `feat/roadmap-ui-i18n-tablet` | A 層 | Help / About 的 LINE 官方帳號與 GitHub Issues 問題回報入口 | `assembleDebug` 通過；APK 權限只剩允許的 2 個 |
| #5 | `feat/behavior-candidate-checklists` | `feat/roadmap-ui-i18n-tablet` | B 層文件 | 補充需實機驗證的候選修法與測試步驟 | `assembleDebug` 通過；APK 權限只剩允許的 2 個；不宣稱行為已驗 |
| #6 | `feat/roadmap-verification-summary` | `feat/roadmap-ui-i18n-tablet` | 交付文件 | 彙整已 build 驗證、等實機驗、明確跳過與 guardrails | `assembleDebug` 通過；APK 權限只剩允許的 2 個 |

## 已 build 驗證的 A 層項目

| 項目 | 證據 |
|---|---|
| `MainActivity` 服務運行指示器改用 `isListenerEnabled && serviceEnabled` | PR #1 |
| 「增強」使用者可見文案改為「堆疊版本」 | PR #1 |
| 「啟用堆疊版本通知」描述改為指定文案 | PR #1 |
| 繁中 / 英文 i18n 與 `AppCompatDelegate.setApplicationLocales` 語言選擇 | PR #1 |
| FAQ / 新手 onboarding 頁 | PR #1 |
| 分享按鈕與 Play Store placeholder 文案 | PR #1 |
| 平板 `WindowSizeClass` + `sw600dp` / `sw720dp` + 聊天室 list-detail 雙欄 | PR #1 |
| 通知風格對話串 vs Apple 分組示意圖 / dialog | PR #2 |
| 權限引導強化 | PR #3 |
| 問題回報入口 | PR #4 |

## 等待實機驗證的 B 層項目

以下項目需要實體 Nothing Phone、已登入 LINE、真實 LINE 訊息與狀態欄通知行為才能驗證。目前只交付候選修法與 on-device 測試步驟，不宣稱已測過。

| 項目 | 文件 |
|---|---|
| 狀態欄浮窗 inline reply 卡住 | `docs/roadmap-b-layer-on-device-checklist.md` |
| 回覆後「我本人的頭貼」不顯示 | `docs/roadmap-b-layer-on-device-checklist.md` |
| 回覆 / 已讀 / 點擊跳轉後通知不自動消失 | `docs/roadmap-b-layer-on-device-checklist.md` |
| 雙開 LINE 來源區分 | `docs/roadmap-b-layer-on-device-checklist.md` |
| 權限設定返回後 UI 是否即時更新 | PR #5 擴充 `docs/roadmap-b-layer-on-device-checklist.md` |
| 通知處理邏輯可自選 | PR #5 擴充 `docs/roadmap-b-layer-on-device-checklist.md` |
| 收回訊息保留 | PR #5 擴充 `docs/roadmap-b-layer-on-device-checklist.md` |

## 明確未處理 / 依指令跳過

| 項目 | 原因 |
|---|---|
| 滑動效能優化 / 開 R8 minify | 使用者要求先跳過，需單獨 PR 驗證 |
| App 介紹影片 | 使用者要求先不要碰 |
| v2.0 項目 | 使用者要求先不要碰 |
| 功能許願池 | 使用者要求先不要碰 |
| 後端 / 金流 / Supabase / LINE Pay / 綠界 | 使用者要求先不要碰，且會引入非零網路架構 |
| Play Console 上傳與 `versionCode` 變更 | AGENTS.md 禁止由 agent 執行 |

## Guardrails 檢查

- 沒有 push `master`。
- 沒有自行 merge PR。
- 沒有改 `app/build.gradle.kts` 的 `versionCode`。
- 沒有新增 `INTERNET`、`REQUEST_INSTALL_PACKAGES` 或其他敏感權限。
- 沒有新增 `AccessibilityService`。
- 沒有加入 app 內網路呼叫、analytics 或自我更新。
- 沒有碰 `keystore/` 或 `keystore.properties`。
