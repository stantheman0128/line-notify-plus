# LINE Notify+

Nothing Phone 上的 LINE 通知替代 App — 攔截原始 LINE 通知並重新組合（對話串 / Apple 分組），支援狀態欄快速回覆、個別聊天室開關、取代原始通知。

| | |
|---|---|
| **Package** | `com.stanslab.linenotify` |
| **目前版本** | versionName `1.2.1` / versionCode `15` |
| **狀態** | **尚未上架**（vc12 因 Impersonation policy 退件 → 已改名「Notify+」，待重交 Production；見 `play-store-assets/play-console-progress.md`） |
| **GitHub** | https://github.com/stantheman0128/line-notify-plus |
| **技術** | Kotlin · Jetpack Compose · Gradle（minSdk 26 / targetSdk 35） |
| **核心機制** | `NotificationListenerService`（**非** AccessibilityService）讀通知；`POST_NOTIFICATIONS` 重發 |

## 資料夾結構

```
line-notify/
├── app/                      ← ⭐ App 原始碼（唯一 source of truth）
│   └── src/
│       ├── main/
│       │   ├── java/com/stanslab/linenotify/   核心邏輯
│       │   │   ├── MainActivity.kt / AboutActivity.kt / ChatManagementActivity.kt / HelpActivity.kt
│       │   │   └── service/  LineNotificationListener.kt · ReplyRelayReceiver.kt · NotificationClassifier.kt
│       │   └── res/
│       │       ├── drawable/ic_launcher_foreground.xml  ← ⭐ icon 向量唯一正解（0.80 縮放、齒輪不裁切）
│       │       ├── values/ · values-en/          ← i18n（繁中 / 英，新字串兩份都要加）
│       │       └── values-sw600dp/ · values-sw720dp/  ← 平板 layout
│       └── test/             ← JVM 單元測試：NotificationClassifierTest.kt（19 案例）
│                                跑 ./gradlew.bat testDebugUnitTest
├── docs/                     ← GitHub Pages：privacy-policy.html（Play Console 用的隱私權 URL）
├── play-store-assets/        ← 上架素材（見下方說明）
├── keystore/                 ← 🔒 release 簽章金鑰（git-ignored，本機 only，弄丟 = App 無法再更新）
├── keystore.properties       ← 🔒 簽章密碼（git-ignored）
├── AGENTS.md                 ← 🤖 AI agent 鐵則（單一正本；CLAUDE.md 只是指向它的指標）
├── HANDOFF.md                ← 🤖 交接：當前任務、branch 現況、已驗證的事實
├── ROADMAP.md                ← 規劃 + 🐛 已知 bug（tester 回報都記這）
└── README.md                 ← 你在看的這份
```

> **探索殘骸已搬出**：5-AI 模型 icon 設計探索（`index/` 23MB）、`icon-exports/`、icon HTML 預覽
> 已於 2026-06-02 整理時移到 repo 外 `../line-notify-archive/`。要找舊設計過程去那裡。

## play-store-assets/ 內容

| 檔案 | 用途 |
|---|---|
| `play-store-icon-512.png` | ⭐ 上架用 512 icon（唯一正確，從 app foreground 渲染） |
| `feature-graphic.png` | 1024×500 功能圖 |
| `screenshots/` | 手機截圖（≥2 張必填；平板 optional） |
| `store-listing.md` | 短描述 / 完整描述 / release notes 文案 |
| `play-console-progress.md` | ⭐ 上架進度 + App content 答案卡 + tester 流程 |
| `*.mjs` / `*.py` | 一次性產生器（產物已 commit）。⚠️ `render_play_store_icon.mjs` 的 resvg-js 依賴原在 `index/`、已隨 archive 移出 → 要重跑先 `npm i @resvg/resvg-js` |

## Build / Release

```bash
# Debug 安裝到手機
./gradlew.bat installDebug

# Release AAB（簽章來自 keystore.properties，產物在 app/build/outputs/bundle/release/）
./gradlew.bat bundleRelease

# 驗證 APK 權限（應只有 BIND_NOTIFICATION_LISTENER_SERVICE + POST_NOTIFICATIONS）
# aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```

- **R8 / minify 沒開**（`isMinifyEnabled = false`）— App 小、避免 release crash 風險。想開要單獨 PR 驗證。
- **versionCode 一旦上傳 Play Console 就永久燒掉**，不能重用 → 每次上傳前 +1。

## 重要提醒

- 🔒 **keystore 弄丟 = 永遠無法更新此 App**。密碼 + .jks 備份在 1Password。務必啟用 Play App Signing 當保險。
- 🐛 **已知問題看 `ROADMAP.md`**。2026-06-01 那批 tester 回報的行為 bug（浮窗回覆卡住、回覆後通知不消失、
  雙開 LINE 無法區分、本人頭貼不顯示）**已於 2026-06-03 全部實機修復**。目前唯一未解的是「權限提示返回後不更新」。
- 🤖 **AI agent 接手看 `AGENTS.md`（鐵則）+ `HANDOFF.md`（當前任務與分支現況）**。`CLAUDE.md` 只是指向 `AGENTS.md` 的指標。
- 🌐 App **完全無網路存取**（v1.1.0 已移除 in-app updater）→ Data safety 可誠實填「No data collected / shared」。
