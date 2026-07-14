# Project Handoff — Notify+

## Latest Session: 2026-07-14（Codex：使用者通知回饋與實機稽核）

### 狀態

- branch：`fix/notification-feedback-2026-07-14`
- repo 版本維持 `versionName 1.2.1 / versionCode 15`；**沒有 bump、commit、push、merge 或上傳 Play**。
- 所有修改仍在 working tree，包含新測試目錄 `app/src/test/java/com/stanslab/linenotify/model/`。
- 使用者既有 untracked 檔完全保留：`.claude/`、`build-install.bat`、`design/.claude/`、
  `design/editor-advanced.png`、`design/editor-preview.png`、`design/text-editor.html`、`tools/`。

### 本輪完成

1. **Android 私密通知占位字**
   - 精確辨識 Android framework 的「系統已隱藏含有私密資訊的通知內容」clone。
   - 不建立假聊天室、不重發占位字、不取消 LINE 原通知；Notify+ 自己的通知改為 private visibility，
     public version 只顯示通用內容。
   - FAQ 補 OPPO／realme 排查與隱私取捨。Android 在 listener callback 前移除的原文，App 無法還原。
2. **聊天室完全靜音**
   - 新版關閉聊天室會清除 Notify+ 與可辨識的 LINE 原通知，包含 `@all` 和直接標註本人。
   - 舊 `disabled_chats` 維持「不增強、保留 LINE 原通知」，避免升級後無預警漏訊息；使用者先開再關才遷移到
     `fully_muted_chats_v2`。
   - 若 framework 已遮蔽聊天室名稱，或 OEM 在 listener 撤掉前先顯示，仍是平台限制；UI 已明確說明。
3. **社群分類**
   - production 統一走受測 classifier；`line.square.notification=true` 會 sticky 確認社群，不因下一則缺 extra 降回群組。
   - 聊天室列可手動固定「好友／群組／社群」，並標示「手動」。
4. **通知安全與穩定性**
   - 只處理 LINE `NewMessages`，通話／付款／好友邀請等 fail-open 保留。
   - 只有確認 Notify+ replacement 已 active 才取消原 LINE；active query 失敗一律保留原通知。
   - Apple 模式每房 8、全域 24 個 child，以 transaction/rollback 方式提交 eviction；沒有安全 victim 時保留 LINE。
   - 快速回覆只有 LINE PendingIntent 成功後才更新 UI；thread active query 失敗不清 buffer。
   - 2026-07-14 實機抓到 LINE 26.10.1 對同一私訊於 24ms 內送出 tagged conversation + legacy 兩個 callback。
     現在只合併 exact tagged-first 形狀、完整 MessagingStyle／interaction fingerprint、500ms 內且來源仍 active 的配對；
     任一條件不符就保留兩則，優先避免漏訊息。對抗性 review 無 P1/P2 blocker。
   - ChatRoom buffer 上限 25，移除手動 recycle，補 exact object identity／cap 測試。
5. **隱私與文件**
   - `allowBackup=false`；隱私政策與商店文案修正為實際資料生命週期。
   - `ROADMAP.md`、`README.md`、`AGENTS.md` 更新為 38 個測試；權限返回 bug 已在實機驗證完成。

### 驗證結果（目前 source）

- `lintDebug testDebugUnitTest assembleDebug assembleRelease bundleRelease --rerun-tasks`：`BUILD SUCCESSFUL`。
- JVM：**38 passed / 0 failed / 0 errors / 0 skipped**；lint：**0 errors / 60 warnings**。
- release APK：`7,595,943 bytes`，SHA-256
  `69500B611EE10AA3D4E6E361C6EF16096139B1EBA2046E1BB8B0B4A09C7D2D3E`。
- release AAB：`7,113,665 bytes`，SHA-256
  `7769C4A073E111790E153BB44A977117C8767AA34899B3F0831F60A1E7C22CFE`；`jarsigner -verify` exit 0。
- APK manifest：minSdk 26 / targetSdk 35、`allowBackup=false`、權限只有
  `BIND_NOTIFICATION_LISTENER_SERVICE` + `POST_NOTIFICATIONS`。
- APK：16K zipalign 驗證成功；v2 signature=true、1 signer。
- ADB：Nothing A059P、Android 16 / API 36、serial `001701527000969`。
  - 最新 debug `install -r` 成功，listener 已恢復 enabled/bound。
  - 關閉 listener → 首頁立即顯示「服務未啟用／需要授權」；恢復 →「服務運行中／正在接聽」。
  - Help 敏感通知 FAQ、聊天室管理說明皆可見；Help／ChatManagement／About 連跑 2 輪，共 6/6 導覽成功。
  - 本輪 logcat：0 fatal exception、0 app ANR、0 app process error；導覽後 PSS 約 100 MB。

### 尚未完成／不可誤報

- 最終 debug 安裝後尚未再收到一則真實 LINE 訊息，所以 strict mirror 合併只有純函式測試、對抗性 review，及
  修正前抓到的真實雙 callback 結構；後續收到測試訊息時確認 log 有「合併 LINE conversation/legacy mirror callback」且只留一則。
- 沒有 OPPO Find X8 Ultra／realme GT8 Pro 實機；私密通知只能確認平台根因、fail-open 與 Nothing UI，仍需兩台回歸。
- 「只擋 `@all`、保留直接 `@我`」刻意未用可見文字猜。需 Nothing／OPPO／realme 各抓兩種真實 extras 後再設獨立開關。
- 持久設定多數仍以顯示名稱為 key，同名聊天室可能碰撞；listener 很大、頭貼 PNG 仍在 callback 同步寫入，
  thread 模式也缺全域 room budget。見 `ROADMAP.md`。
- LINE 沒提供 stable message ID，且 Android 沒有 query-and-cancel 原子 API；極端完全相同 payload 仍只能 fail-open。
- GitHub Pages 線上隱私政策尚未部署；不要把 repo 已更新誤寫成線上已更新。

### 上架 blocker

- launcher／512 icon 仍像 LINE 綠＋白氣泡，屬再次 Impersonation 退件風險。
- 4 張手機圖有舊品牌／舊功能／私密內容／debug 痕跡且 3 張比例不合；8 張 tablet 圖是假平板畫面；全部重做。
- `line-oa-cover.png` 仍是舊品牌；feature graphic 可保留，但換 icon 後需再確認整套一致。
- 發版前先部署/複查 Pages、確認 vc15 是否已被 Console 使用，再由 Stan 決定版號三件套；目前產物只供驗證，
  **不是可直接上傳的 release**。

## Previous Session: 2026-07-14（Claude Code → 交接給 Codex：收編懸空分支）

> 給接手的 AI（Codex）：**先讀 `AGENTS.md`（鐵則，你會自動載入），再讀這一節。**
> 本節取代 2026-06-07 那份的「下一步」，舊記錄保留在下方僅供追溯，**它的待辦已經全部過期，別照著做。**

### 一、這一輪要你做什麼（主線任務）

**把 3 條有價值的 `rebased/*` 分支整合起來，開一條 integration 分支 + PR 給 Stan。**

專案目前殘留 9 條 branch（其中 8 條沒進 master，1 條已在 master 但沒清掉），懸在那裡沒人收。
它們是 2026-07-02 那一輪的產物，堆到現在造成 master 不是乾淨基準，任何新功能疊上去都會撞。
這件事阻塞其他所有工作，所以優先做它。

我（Claude）已經把 9 條全部驗過一遍，事實如下。**這些是 `git merge-tree` / `git cherry` / patch-id
實測結果，不是推測，你可以直接採信，不必重查。**（另有一隻獨立 verifier agent 重跑過一遍全部指令，
含「模擬依序合三條」的最終樹檢查：strings 零重複 key、無重複 import／函式宣告、`R.string.*` 全部解得到定義。）

| Branch | tip | 對 master | 處置 |
|---|---|---|---|
| `rebased/problem-reporting` | `7e09c9f` | 領先 2 / 落後 2，**merge 乾淨** | ✅ **合**（第 1 順位，最小 +58 行） |
| `rebased/permission-guidance` | `169372e` | 領先 2 / 落後 2，**merge 乾淨** | ✅ **合**（第 2 順位，MainActivity +140） |
| `rebased/notification-style-visual-guide` | `9332a77` | 領先 2 / 落後 2，**merge 乾淨** | ✅ **合**（第 3 順位，含 223 行新檔） |
| `preview/all-three-2026-07-02` | `1f7d3a6` | 領先 4，merge 乾淨但**內容與上面三條重複** | ❌ 別合，合完三條後刪 |
| `feat/problem-reporting` | `7c09702` | **落後 21**，merge **會衝突** | ❌ 刪 |
| `feat/permission-guidance` | `2c3c610` | **落後 21**，merge **會衝突** | ❌ 刪 |
| `feat/notification-style-visual-guide` | `a480542` | **落後 21**，merge **會衝突且是回歸** | ❌ 刪 |
| `fix/notification-behavior` | `0d7a4d5` | 空殼，樹是 master 真子集 | ❌ 刪 |
| `test/unit-scaffolding-2026-07-02` | `6f72fb3` | 已在 master | ❌ 刪 |

三條 `rebased/*` 彼此**兩兩不衝突**（pairwise merge-tree 全 exit=0），所以依序合不會有二次衝突。

### 二、關鍵決策（附證據，別推翻）

- **一律用 `rebased/*`，`feat/*` 全部丟掉。**
  `feat/*` 是 `rebased/*` 的過期前身，分支點停在 2026-06-02，落後 master 21 個 commit。
  `git merge-tree master feat/*` 三條**全部 exit=1（衝突）**；`rebased/*` 三條**全部 exit=0（乾淨）**。
  `rebased/*` 就是把同樣的衝突解過一次的版本。
  最毒的是 `feat/notification-style-visual-guide`：它的 `HelpActivity.kt +2` 是插在
  `NotificationStyleGuideCard()` 這個函式**內部**，而 master 的 `309cf52` 已經把整個函式連同呼叫處刪掉了。
  合下去是 modify/delete 級的內容衝突（實測 exit=1）；**它不會自動把卡救回來，但你如果在解衝突時取了
  它那邊，就會把 master 刻意移除的卡片復活，變成回歸。** 別去解這個衝突，直接用 `rebased/` 版
  （它完全沒碰 HelpActivity，已正確丟掉那 2 行）。

- **`preview/all-three-2026-07-02` 不要合。**
  它不是 merge commit，是把三個 feature **線性堆疊**的整合預覽（全單親、零分岔），內容就是那三條。
  證據：其中兩顆 commit 與 rebased 版 **patch-id 完全相同**（`42c2040`≡`169372e`、`5fa64db`≡`7e09c9f`）；
  第三顆（`1f7d3a6` vs `9332a77`）patch-id 不同，但那只是因為它疊在前兩個 feature 之上、`ROADMAP.md`
  的 context 行已被打勾改過，**`--stat` 完全一樣、新檔 blob 也相同**（`NotificationStyleVisualGuide.kt`
  兩邊都是 `502ab48`）。實質內容重複。
  合它 = 製造重複 commit。它的價值只在於「證明三條可以共存」，這點已經驗過了。

- **`fix/notification-behavior` 是空殼，直接刪。**
  舊 HANDOFF 說它是 2026-06-07 的工作分支，**那已經不成立**：它後來被拿去掛別的東西，tip 現在是
  2026-07-02 的一個 docs commit。`git cherry -v master fix/notification-behavior` 回 `-`
  （= master 已有等價 commit），patch-id `1cfb999…` 與 master `45e5e07` 完全相同，而且那是它**唯一**
  領先 master 的 commit。它 6/07 的通知行為程式碼早就進 master 了。
  反過來它比 master **少了 2 個檔案**（`NotificationClassifier.kt` + `NotificationClassifierTest.kt`），
  `build.gradle.kts` 也少了測試依賴。合它進 master 得不到任何東西。零價值。

- **你不能自己合進 master。** 見下方「三、你的邊界」。

### 三、你的邊界（AGENTS.md 鐵則與本任務的交界，讀清楚再動手）

`AGENTS.md` 明令：**禁止碰 `master`、別自己 merge、別 push master、別自己改 versionCode。**
這跟「收編分支」看似矛盾，實際不矛盾。**你的交付物是一條準備好的整合分支 + PR，不是一個被合掉的 master。**

- ✅ **你做**：開 `integration/rebased-three-2026-07-14`（從 master 切），把三條依序 merge 進去，
  跑驗證，開 PR 給 Stan。
- ❌ **你不做**：merge 進 master、push master、刪除任何 branch、bump versionCode、上傳 Play Console。
  **刪 branch 和合 master 是 Stan 的動作**，你只在 PR 描述裡列出「建議刪除這 5 條」讓他執行。
- **發版三件套先不要碰。** 三條分支沒有任何一條動過 `app/build.gradle.kts`（master 現在
  vc15 / 1.2.1）。合併 feature 進 master **不等於發版**，所以現在不需要 bump。
  等 Stan 決定要出版本時，他自己補三件套（versionCode +1、versionName、AboutActivity changelog）。
  你只要在 PR 裡提醒他「這批合完後若要發版，記得補三件套」。

### 四、目前狀態（2026-07-14 實測，不是抄的）

- **能跑**：`./gradlew.bat assembleDebug` 綠燈。
- **測試能跑而且會過**：`./gradlew.bat testDebugUnitTest --rerun` → **19 passed / 0 failed**
  （`app/src/test/.../NotificationClassifierTest.kt`）。
  ⚠️ 不加 `--rerun` 的話 Gradle 會回 `UP-TO-DATE` 跳過測試、卻仍印 `BUILD SUCCESSFUL`。**那不代表測試跑過。**
  要拿真數字讀 `app/build/test-results/testDebugUnitTest/*.xml` 的 `tests=" " failures=" "`。
- **branch**：`master`，與 `origin/master` 同步（`45e5e07`）。
- **版本**：versionCode 15 / versionName 1.2.1。**尚未上架**（vc12 因 Impersonation policy 退件，
  已改名 Notify+ 待重交，見 `play-store-assets/play-console-progress.md`）。
- **權限**：8 條 branch **沒有任何一條動過 `AndroidManifest.xml`**，鐵則 2 沒被破壞。
  （`rebased/permission-guidance` 只加 `request_post_notifications` 這個**字串**和 runtime 請求 UI，
  POST_NOTIFICATIONS 權限本來就在。安全。）
- **i18n / 平板已完成**：`values-en`、`values-sw600dp`、`values-sw720dp` 都在。
  舊文件說「無 i18n」是錯的，我已經修掉 `AGENTS.md`。

### 五、工作區有未追蹤的雜物，別掃進 commit

`git status` 目前有這些 untracked：
```
.claude/  build-install.bat  design/.claude/  design/editor-advanced.png
design/editor-preview.png  design/text-editor.html  tools/
```
這些**不屬於這次任務**。commit 時一律 `git add <明確路徑>`，**禁用 `git add -A` / `git add .`**，
否則會把它們掃進 PR。

### 六、下一步（照順序做）

1. `git checkout -b integration/rebased-three-2026-07-14 master`
2. `git merge rebased/problem-reporting`（`7e09c9f`，最小，先合方便 review）
3. `git merge rebased/permission-guidance`（`169372e`）
4. `git merge rebased/notification-style-visual-guide`（`9332a77`）
   - 三條的 `ROADMAP.md` 各自打勾**不同的** checkbox、`strings.xml` 加的 key **完全不重名**，git 會自動併。
   - 三條都含同一個 docs commit `0d7a4d5`，git 認得它與 master `45e5e07` 是同一個 patch，會自動去重。
5. **每合完一條就跑一次**：`./gradlew.bat assembleDebug` + `./gradlew.bat testDebugUnitTest --rerun`。
   （不是最後才跑一次。中途壞掉要能指出是哪一條造成的。）
6. `git diff master...HEAD -- app/src/main/AndroidManifest.xml` 確認**權限零變更**（應為空輸出）。
   - ⚠️ 用**三個點** `master...HEAD`。two-dot 的 `git diff master..rebased/*` 會**假性列出**
     `app/build.gradle.kts`，那是因為 rebased 的基底 `ec66140` 少了 master 的 `6f72fb3`（加測試依賴的
     那顆），是 diff 方向造成的錯覺，**不是分支真的動過它**。別被騙去「修」一個不存在的問題。
7. 開 PR 給 Stan，PR 描述要包含：
   - 三條合了什麼、改了哪些檔
   - build + test 的**實際輸出**（不是「應該會過」）
   - **建議 Stan 刪除的 6 條廢 branch**：`feat/notification-style-visual-guide`、`feat/permission-guidance`、
     `feat/problem-reporting`、`preview/all-three-2026-07-02`、`fix/notification-behavior`、
     `test/unit-scaffolding-2026-07-02`（最後這條已在 master，純殘留）。建議刪前先打 `archive/*` tag 保險。
   - **實機驗證清單**：三條都是 UI/行為改動，你驗不了（無手機），列出 Stan 該在實機上點哪些地方看哪些結果
   - 提醒：若要發版，記得補三件套

### 七、給下一個 AI 的提示

- **行為類改動你驗證不了，不准宣稱測過。** 這是 `AGENTS.md` 的硬規定。`BUILD SUCCESSFUL` 只證明編得過，
  不證明通知行為正確。歷史教訓：`painterResource(R.mipmap.ic_launcher)` 編譯完全正常，一啟動就 crash
  （adaptive icon 載不動）。
- **build 環境**：Windows 路徑含空格，直接跑 `gradlew.bat` 會炸。先
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`。
- **實機 / adb / rebind listener 的雷點**：見下方 2026-06-07 那節的第七項（`install -r` 後一定要
  rebind notification listener，否則訊息不會跳）。那部分仍然有效。
- **設計決策仍然有效**：導航 B 模型、混合配色（主要按鈕鎖品牌綠 `Green40 #06C755`，其餘跟系統動態取色）、
  元件命名照 `design/COMPONENTS.md`。詳見下方 2026-06-07 那節的第四項。
- **Compose BOM 2024.12.01 / material3 1.3.1**，用穩定 API。
- **回應一律繁體中文。**

### 八、建議用的 skill

- 合併衝突真的發生 → `resolving-merge-conflicts`
- 收尾分支、決定 merge/PR/清理 → `superpowers:finishing-a-development-branch`
- 動 Compose UI → `impeccable`（前端視覺主力）
- 宣稱完成前 → `superpowers:verification-before-completion`

---

## Previous Session: 2026-06-07（Claude Code → 交接給 Codex 接手 UI/設計）

> ⚠️ **本節的「六、還沒做」已全部過期**（社群分類 bug 已於 commit `09088de` 修掉；
> Figma / GIF 進度見 `ROADMAP.md`）。**第四項（設計決策）、第五項（設計參考檔）、第七項（build/adb 雷點）
> 仍然有效**，其餘僅供追溯。

### 一、App 是什麼
LINE Notify+：`NotificationListenerService` 攔截 LINE 通知，重組成「堆疊 / 對話串」通知，支援快速回覆、雙開區分、通話守門。Kotlin + Jetpack Compose + Material3。**只用 2 個權限**（BIND_NOTIFICATION_LISTENER_SERVICE + POST_NOTIFICATIONS），無網路。

### 二、目前狀態（❌ 已過期，見上方最新一節）
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

### 四、關鍵設計決策（✅ 仍然有效，務必沿用，別推翻）
- **導航 = B 模型**（乾淨首頁 + 推送子頁），**不走底部 Tabs**（功能量還不夠；等「許願池/訊息搜尋」變大再升級）。
- **配色 = 混合**：主要按鈕/關鍵強調**鎖品牌綠** `Green40 (#06C755)`；其餘元素**跟系統動態取色（Material You）**。⚠️ 實機主色會依手機桌布變（這台是藍灰）—— 不是 bug。
- **元件命名**：一律用 `design/COMPONENTS.md` 的名稱（主要按鈕/外框按鈕/文字按鈕/圖示按鈕、開關/單選鈕/篩選標籤/下拉選單、狀態卡/白卡/灰卡、開關列/導航列/單選列/聊天室列…）。
- **刪除功能**：已移除（用每列開關停用即可）。**已讀後清除**：固定永遠開啟（不給開關）。

### 五、設計參考檔（✅ 仍然有效，全在 `design/`）
- `COMPONENTS.md` / `COMPONENTS.html` / `components-visual.png` — 元件目錄（含實機截圖 + 品牌綠對照）
- `STRUCTURE-template.md`（user 已填）/ `nav-diagram.png` — 結構樹 + Mermaid 導航圖
- `preview-b.html` / `preview-b.png` — B 模型預覽
- `real_home_v2.png` / `r2_advanced.png` / `r2_chat.png` / `r2_help.png` / `real_about_v2.png` — **現況實機截圖**（可當 Figma 起稿底圖）
- `../play-store-assets/play-store-icon-512.png` — App icon（Figma logo 用）

### 六、還沒做（❌ 已過期，別照做）
1. ~~**社群分類 bug**~~ → **已修**，commit `09088de`（改用 `line.square.notification` 旗標判社群）。
2. **GIF 示範**：進階 ⓘ 目前是 placeholder。用 `adb screenrecord` 錄實機 → ffmpeg 轉 GIF → 放進對話框。（仍未做，見 `ROADMAP.md`）
3. **Figma 視覺定稿**：user 會在 Figma 排版，排好匯出 PNG 給你照著實作。

### 七、怎麼 build / 裝 / 測（✅ 仍然有效，會踩雷，務必照做）
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
