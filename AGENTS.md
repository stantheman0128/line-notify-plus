# AGENTS.md — Notify+

給 AI agent 的工作守則（Claude Code、ChatGPT/Codex 等通用）。
人類向的結構說明在 `README.md`；規劃與已知 bug 在 `ROADMAP.md`。
**回應一律繁體中文。**

## 這個 App 是什麼

Android App，攔截原始 LINE 通知並重新組合顯示（對話串 / Apple 分組），支援狀態欄快速回覆。
Package `com.stanslab.linenotify`，Kotlin + Jetpack Compose，minSdk 26 / targetSdk 35。
目前 versionName `1.6.1` / versionCode `33`（權威來源：`app/build.gradle.kts:27-28`）。

狀態：**尚未上架**。2026-06-23 上傳的 vc12 因 Google Play 的 Impersonation policy 被退件，已改名為
「Notify+」、重做 feature graphic、商店描述改成不主打 LINE，**待 Stan 重新提交 Production**。
細節看 `play-store-assets/play-console-progress.md`。

## ⛔ 鐵則（違反 = 上架被拒 / App 永久壞掉，先讀這段）

1. **任何會改變 App 行為的一批改動，收工時一定要補版本「三件套」，三件同一個 commit。**
   Stan 的硬性要求：**他裝到手機上時，必須能在「關於」頁看到版號變了、看到這次改了什麼。**
   沒有版本足跡的改動 = 沒交付。漏改過兩次雷：(1) 只 bump `versionCode`、App 內版號卻停在舊的
   v1.2.0（2026-06-30）；(2) 1957 行通知核心改動零版本足裡、連 commit 都沒有（2026-07-14）。
   - **① `versionCode` +1**（`app/build.gradle.kts`）——一旦**上傳 Play** 就永久燒掉、不能重用
     （草稿/退件也算）。目前 source 已到 **vc33**；是否已上傳仍以 Play Console 為準。沒上傳過的版號可以自由改。
   - **② `versionName` 也要 bump**——App「關於」頁顯示的就是它（`AboutActivity` 讀
     `getPackageInfo().versionName`）。純修 bug 走 patch（1.2.1 → 1.2.2）；改到通知核心行為
     或加新功能走 minor（1.2.x → 1.3.0）。
   - **③ `AboutActivity.kt` 加一條 `ChangelogEntry("vX.Y.Z", …)` + 對應 `changelog_*` 中英字串**
     （`values/` 與 `values-en/` 兩份都要）。**用使用者看得懂的白話寫，不要寫技術術語。**
   - ⚠️ **「bump 版號」跟「發版到 Play」是兩件事，別混。** 前者是你收工的義務，後者只有 Stan 能做
     （見下方 autonomous 守則）。**曾經因為把這兩句寫成一句，導致 agent 以為版號不能碰。**
2. **`<uses-permission>` 只能有兩個**：`BIND_NOTIFICATION_LISTENER_SERVICE` + `POST_NOTIFICATIONS`。加任何其他敏感權限（尤其 `REQUEST_INSTALL_PACKAGES`、`INTERNET`）都會害 Play Console 退件。
3. **AccessibilityService 只有一個窄範圍例外。** Stan 已於 2026-08-29 明確同意用選配的 AccessibilityService 偵測「LINE 聊天室已開啟」並清除 Notify+ 通知。服務只能監聽兩個既有 LINE package、讀聊天室標題與必要畫面結構；禁止讀取／保存訊息本文、監聽其他 App、模擬點擊／手勢／輸入或新增網路傳輸。功能預設關閉，開啟前必須顯示獨立的資料揭露與同意畫面，並以 `isAccessibilityTool=false` 誠實申報。任何擴大用途都要 Stan 重新明確核准。
4. **App 完全無網路存取**（沒有 `INTERNET` 權限）。別加任何網路呼叫 / analytics / 自我更新——會打破 Play Console「No data collected / shared」的聲明。in-app updater 就是為此被移除的。
5. **別碰 `keystore/` 和 `keystore.properties`。** 那是 release 簽章金鑰，弄丟 = 這個 App 永遠無法再更新。密碼在 1Password，git-ignored。
6. **R8 / minify 故意關著**（`isMinifyEnabled = false`）。想開要單獨開 PR 驗證 release 不 crash。

## 給 autonomous agent（「把 roadmap 全做完」類大任務先讀這段）

- **一律在自己開的 branch 上做，禁止碰 `master`。** master 是發布線，也是 Stan review 的基準。
  流程：`git checkout -b <前綴>/<主題>` → 做 → 開 PR 給 Stan review。**別自己 merge、別 push master、別刪任何 branch。**
  - 前綴：新功能用 `feat/`、修 bug 用 `fix/`、合併既有分支用 `integration/`。
  - ⚠️ 目前 repo 裡殘留一批 **2026-07-02 的舊 `feat/*` 分支正在待刪清單上**（見 `HANDOFF.md`）。
    開新分支時**帶上日期**（例：`feat/xxx-2026-07-14`）避免跟廢分支撞名或被誤刪。
- **邊做邊 commit，別攢著。** 每完成一個可獨立編譯的小主題就 commit 一次（跑得過
  `assembleDebug` 就可以送）。**禁止把一整批工作留在工作區不 commit。**
  2026-07-14 踩過：一次交出 1957 行未 commit 的改動，19 個檔糊成一團，8 個主題交錯在同一批
  函式裡，事後幾乎拆不開（最後只能靠分層硬切）。工作區不是儲存空間。
- **收工一定要 bump 版本三件套**（鐵則 1）。這不是「發版」，這是讓 Stan 裝到手機時看得出你改了什麼。
- **但別自己上傳 Play Console、別自己 merge 進 master、別 push master、別刪 branch。**
  那些是 Stan 的動作。**注意這跟上一條不衝突：bump 版號要做，上傳 Play 不做。**
- **⚠️ 行為類 bug 在無實機環境「做得到、但驗證不了」。** ROADMAP 的「浮窗回覆卡住 / 我的頭貼不顯示 / 回覆或已讀後通知不消失 / 雙開 LINE 區分」都需要：**實體 Nothing Phone + 已登入的 LINE + 真實收到的訊息**，才能觀察 `NotificationListenerService` 收到的 `StatusBarNotification` 內容與通知行為。雲端 sandbox 沒有手機、沒有 LINE、沒有真實訊息 → **做不到驗證**。對這類任務只能「產出候選修法 + 附 on-device 測試步驟」，**不准宣稱已測試通過**。
- **無手機也能完成、且能用 build 驗證的**（適合放手讓 agent 做）：i18n 抽字串到 strings.xml、文案改名「增強」→「堆疊版本」、FAQ / onboarding 頁、分享按鈕、平板 layout。完成後跑 `./gradlew.bat assembleDebug` 確認編得過即可。
- **完成標準**：`assembleDebug` 綠燈 ＋ 沒新增任何權限（鐵則 2）＋ 行為類附 on-device 測試清單交給 Stan 實機驗。

## Build / Run 指令（Windows）

```bash
# 跑 gradle 前需設好 JDK（用 Android Studio 內建的 jbr）
# Windows cmd:  set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
# Git-Bash:     export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew.bat assembleDebug        # 只編譯（無手機也能跑，agent 的主要驗證手段）
./gradlew.bat testDebugUnitTest    # 跑 JVM 單元測試（無手機也能跑）
./gradlew.bat installDebug         # 編譯 + 裝到連線的手機（debug）
./gradlew.bat bundleRelease        # 出 AAB → app/build/outputs/bundle/release/app-release.aab（需 keystore.properties）
./gradlew.bat assembleRelease      # 出 APK（本機驗證用）
```

- Android SDK: `C:\Users\stans\AppData\Local\Android\Sdk`（已寫在 `local.properties`；雖有 ignore 規則，
  目前仍是歷史 tracked 檔，日後應另開清理變更停止追蹤）。
- **有 JVM 單元測試**：`app/src/test/java/...`（55 個案例，涵蓋
  聊天分類、社群 sticky/手動 override、敏感通知辨識、legacy/mute migration、頻道過濾、
  LINE conversation/legacy mirror 配對、長訊息全文選擇、ChatRoom 邊界、Accessibility 房間配對與舊 snooze 釋放）。
  跑 `./gradlew.bat testDebugUnitTest`，**改動 `NotificationClassifier.kt` 或通知核心邏輯後必跑**。
  2026-07-14 實測 38 passed / 0 failed / 0 errors / 0 skipped。
  - ⚠️ Gradle 會對沒變動的 task 回 `UP-TO-DATE` 直接跳過、卻仍印 `BUILD SUCCESSFUL`——**那不代表測試跑過**。
    要拿真結果加 `--rerun`，或直接讀 `app/build/test-results/testDebugUnitTest/*.xml` 裡的
    `tests=" " failures=" "` 數字。
  - 沒有 instrumented test（無 `androidTest/`）。通知**行為**仍只能實機驗（見上方 autonomous 守則）。
- 驗證 release APK 權限乾淨：`aapt2 dump permissions app/build/outputs/apk/release/app-release.apk` → 應只有那 2 個權限。

## 程式碼地圖

| 檔案 | 職責 |
|---|---|
| `app/src/main/java/.../service/LineNotificationListener.kt` | ⭐核心：攔截 LINE 通知、重組成對話串、發新通知 |
| `app/src/main/java/.../service/NotificationClassifier.kt` | 通知分類（好友/群組/社群/官方帳號）。**有單元測試罩著，改這裡必跑 `testDebugUnitTest`** |
| `app/src/main/java/.../service/ReplyRelayReceiver.kt` | 快速回覆的 RemoteInput 中繼 |
| `app/src/main/java/.../MainActivity.kt` | 主畫面、權限開關、設定 |
| `app/src/main/java/.../ChatManagementActivity.kt` | 個別聊天室開關、搜尋、長按多選 |
| `app/src/main/java/.../AboutActivity.kt` | 關於頁 + **App 內更新紀錄**（發版三件套的第 ③ 項改這裡） |
| `app/src/main/java/.../HelpActivity.kt` | 教學 / FAQ 頁。⚠️ master 的 `309cf52` 已移除「通知風格說明」卡，**別把它加回來** |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | ⭐icon 向量唯一正解（0.80 縮放修齒輪裁切） |
| `app/src/main/res/values/strings.xml` | 繁中字串（預設） |
| `app/src/main/res/values-en/strings.xml` | 英文字串。**加任何新字串，兩份都要加** |

## 慣例

- **i18n 已完成**：`values/`（繁中）+ `values-en/`（英），用 per-app language API
  （`AppCompatDelegate.setApplicationLocales`）讓使用者在設定頁選語言。新增 UI 文案一律進 strings.xml
  兩份，別 hardcode 在 .kt。
- **平板 layout 已做**：`values-sw600dp/` + `values-sw720dp/`。
- icon 改了之後，512 上架圖要用 `play-store-assets/render_play_store_icon.mjs` 重渲染（該腳本 resvg-js 依賴已隨 archive 移出，要先 `npm i @resvg/resvg-js`）。
- 上架相關的填表答案、tester 流程、進度，全記在 `play-store-assets/play-console-progress.md`。

## 接手前先看

1. **`HANDOFF.md`** — 當前這一輪要做什麼、branch 現況、已驗證過的事實。**先讀這份。**
2. `ROADMAP.md` — 功能規劃與已知 bug。注意：2026-06-01 那批 tester 回報的行為 bug
   （浮窗回覆卡住 / 我的頭貼不顯示 / 回覆後通知不消失 / 雙開 LINE 無法區分）**已於 2026-06-03 全部實機修復**，
   別再去修一次。現有待辦以 `ROADMAP.md` 為準，包括 `@all` 與直接標註分流、同名/雙開聊天室設定隔離，
   以及 OPPO/realme 的真機回歸；不要再用「唯一未解」描述。
