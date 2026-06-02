# AGENTS.md — LINE Notify+

給 AI agent 的工作守則（Claude Code、ChatGPT/Codex 等通用）。
人類向的結構說明在 `README.md`；規劃與已知 bug 在 `ROADMAP.md`。
**回應一律繁體中文。**

## 這個 App 是什麼

Android App，攔截原始 LINE 通知並重新組合顯示（對話串 / Apple 分組），支援狀態欄快速回覆。
Package `com.stanslab.linenotify`，Kotlin + Jetpack Compose，minSdk 26 / targetSdk 35。
目前 versionName `1.1.0` / versionCode `11`，狀態：Google Play **Closed Testing 進行中**（testers 已 opt-in，14 天觀察期計時中）。

## ⛔ 鐵則（違反 = 上架被拒 / App 永久壞掉，先讀這段）

1. **versionCode 一旦上傳 Play Console 就永久燒掉、不能重用。** 每次要上傳就 `app/build.gradle.kts` 裡 +1。目前 vc11，下一個 vc12。
2. **權限只能有兩個**：`BIND_NOTIFICATION_LISTENER_SERVICE` + `POST_NOTIFICATIONS`。加任何敏感權限（尤其 `REQUEST_INSTALL_PACKAGES`、`INTERNET`）都會害 Play Console 退件。
3. **絕不加 AccessibilityService。** 本 App 用 `NotificationListenerService` 讀通知（正解），不是無障礙。加無障礙 = 觸發 Google 嚴格審查。
4. **App 完全無網路存取**（沒有 `INTERNET` 權限）。別加任何網路呼叫 / analytics / 自我更新——會打破 Play Console「No data collected / shared」的聲明。in-app updater 就是為此被移除的。
5. **別碰 `keystore/` 和 `keystore.properties`。** 那是 release 簽章金鑰，弄丟 = 這個 App 永遠無法再更新。密碼在 1Password，git-ignored。
6. **R8 / minify 故意關著**（`isMinifyEnabled = false`）。想開要單獨開 PR 驗證 release 不 crash。

## 給 autonomous agent（「把 roadmap 全做完」類大任務先讀這段）

- **一律在 feature branch 上做，禁止碰 `master`。** master 是 closed-testing 的發布線，動到會污染正在跑的 14 天測試。流程：`git checkout -b feat/<主題>` → 做 → 開 PR 給 Stan review。**別自己 merge、別 push master。**
- **別自己改 versionCode、別自己上傳 Play Console。** 那是 Stan 的發布動作（見鐵則 1）。
- **⚠️ 行為類 bug 在無實機環境「做得到、但驗證不了」。** ROADMAP 的「浮窗回覆卡住 / 我的頭貼不顯示 / 回覆或已讀後通知不消失 / 雙開 LINE 區分」都需要：**實體 Nothing Phone + 已登入的 LINE + 真實收到的訊息**，才能觀察 `NotificationListenerService` 收到的 `StatusBarNotification` 內容與通知行為。雲端 sandbox 沒有手機、沒有 LINE、沒有真實訊息 → **做不到驗證**。對這類任務只能「產出候選修法 + 附 on-device 測試步驟」，**不准宣稱已測試通過**。
- **無手機也能完成、且能用 build 驗證的**（適合放手讓 agent 做）：i18n 抽字串到 strings.xml、文案改名「增強」→「堆疊版本」、FAQ / onboarding 頁、分享按鈕、平板 layout。完成後跑 `./gradlew.bat assembleDebug` 確認編得過即可。
- **完成標準**：`assembleDebug` 綠燈 ＋ 沒新增任何權限（鐵則 2）＋ 行為類附 on-device 測試清單交給 Stan 實機驗。

## Build / Run 指令（Windows）

```bash
# 跑 gradle 前需設好 JDK（用 Android Studio 內建的 jbr）
# Windows cmd:  set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
# Git-Bash:     export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew.bat installDebug      # 編譯 + 裝到連線的手機（debug）
./gradlew.bat bundleRelease     # 出 AAB → app/build/outputs/bundle/release/app-release.aab（需 keystore.properties）
./gradlew.bat assembleRelease   # 出 APK（本機驗證用）
```

- Android SDK: `C:\Users\stans\AppData\Local\Android\Sdk`（已寫在 `local.properties`，git-ignored）。
- **沒有單元測試 / instrumented test**（無 `test/`、`androidTest/` 目錄）。別假裝有 `./gradlew test` 可跑；驗證靠實機安裝 + 手動測通知行為。
- 驗證 release APK 權限乾淨：`aapt2 dump permissions app/build/outputs/apk/release/app-release.apk` → 應只有那 2 個權限。

## 程式碼地圖

| 檔案 | 職責 |
|---|---|
| `app/src/main/java/.../service/LineNotificationListener.kt` | ⭐核心：攔截 LINE 通知、重組成對話串、發新通知 |
| `app/src/main/java/.../service/ReplyRelayReceiver.kt` | 快速回覆的 RemoteInput 中繼（**有回報卡住 bug，見 ROADMAP**） |
| `app/src/main/java/.../MainActivity.kt` | 主畫面、權限開關。⚠️ `:151` 的「服務運行中」指示器綁錯變數（見 ROADMAP） |
| `app/src/main/java/.../ChatManagementActivity.kt` | 個別聊天室開關 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | ⭐icon 向量唯一正解（0.80 縮放修齒輪裁切） |
| `app/src/main/res/values/strings.xml` | 字串（**目前繁中為主、無 i18n**，未抽完，部分還 hardcode 在 Kotlin） |

## 慣例

- 字串尚未完全 i18n 化（res 只有預設 values，無 values-en）。改 UI 文案時注意可能 hardcode 在 .kt。
- icon 改了之後，512 上架圖要用 `play-store-assets/render_play_store_icon.mjs` 重渲染（該腳本 resvg-js 依賴已隨 archive 移出，要先 `npm i @resvg/resvg-js`）。
- 上架相關的填表答案、tester 流程、進度，全記在 `play-store-assets/play-console-progress.md`。

## 接手前先看

- `ROADMAP.md` 的「🐛 tester 回報 bug」——目前 closed testing 期間待修：浮窗回覆卡住、回覆後「我的頭貼」不顯示、回覆/已讀後通知不消失、雙開 LINE 無法區分。
