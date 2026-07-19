# Project Handoff — Notify+

## Latest Session: 2026-07-19（Claude Code：LINE 26.11.0 結構改變三症狀診斷 + vc18 修復，branch `fix/line-26110-summary-redaction-2026-07-19`）

### 起因（1.3.1 上線後的回報）

1. **realme 用戶（GT 8 Pro / realme UI 7 / Android 16 / LINE 26.11.0）**：LINE 原通知沒被取代、
   跟 Notify+ 一併出現；開關重開＋重啟無效。另回報私密占位通知又出現（內容是行事曆訊息）。
2. **Stan（Nothing A065 / LINE 26.11.0）**：同一則訊息 heads-up 跳兩次（下滑只剩一則）；
   一次目擊 LINE 原通知沒被取消。

### 根因（附證據）

- **LINE 26.11.0 的 `id=16880000 tag=null` 是雙形態**（兩者都在 A065 實證）：
  單聊天室新訊息時仍是舊式 legacy mirror（2026-07-19 18:12 watcher 實測「合併 mirror
  callback」有觸發、取代流程綠燈）；未讀累積/彙總狀態時變成 `GROUP_SUMMARY`
  （同日 dumpsys mArchive 實證 flags=…|GROUP_SUMMARY，26.10.1 時代沒觀察過此形態）。
  vc17 對 summary 的策略是「永遠保留」＋堆疊摘要 title 也不再取消（vc13 舊版兩者都會
  cancel）→ summary 態下每則訊息更新那張彙總卡、永遠沒人清 → 在 realme UI 以
  「N則新訊息＋訊息預覽」整卡殘留 = 用戶看到的「沒被取代」。用戶兩張截圖的 LINE 卡
  title 都是「28xx則新訊息」= summary，非 child，吻合；她 2868 未讀 = 常駐 summary 態，
  Stan 未讀少 = 多半 mirror 態 → 頻率差異也對上。
- **`scheduleOriginalCancellation` 是 200ms 一次性檢查、失敗不重試**，且依賴
  `activeNotifications` 即時可見（程式碼自註「部分 OEM 不是同步可見」）→ 慢一拍就永久留雙份。
- **遮蔽偵測四條件 AND 過嚴**：realme 的 redaction clone 保留原 title/subText，只有
  text=占位字串成立 → 漏判 → 占位字被原樣轉貼、原通知被取消（比不修還糟）。
  占位字串本身與用戶截圖逐字相同，錯的不是字串比對，是附加條件。
  舊測試 `ordinary_message_matching_only_part_of_signature_is_not_redacted` 把這個過嚴
  行為當規格鎖住了，已改寫。

### 修復（三 commit + 三件套，vc18 / 1.3.2）

- `4acd58f` classifier：遮蔽偵測改為只比對 text==系統占位字串（誤中代價=該則不增強，
  fail-open）；新增 `shouldCancelLineSummary`（取代開啟且內容已由我方副本或 LINE child
  承載才取消 summary，唯一殘留則保留）。測試 45 全綠（XML 實讀 43+2 / 0 failures）。
- `f26b640` listener：summary/堆疊摘要接管（延遲 350ms 守門取消；檢查移到 title/text
  空值 return 之前——summary 不保證帶 android.text）；原通知取消改 200/500/900ms 重試階梯。
- `e695b96` 三件套 vc18 / 1.3.2 + 中英 changelog。

### 驗證狀態（2026-07-19 晚間已實機驗證）

- ✅ JVM：45 tests 0 failures（`--rerun` + XML 實讀）。assembleDebug / assembleRelease /
  bundleRelease 全綠。
- ✅ **A065 實機驗證（vc18 本地簽章版，Stan 同意移除 Play 版重裝、設定歸零）**：
  watcher（`tools/diag/notifwatch.py`）全程錄，多輪真實訊息 capture 全綠零紅燈：
  - T1 單則取代 ✅（LINE child+mirror 全取消，終態只剩 Notify+ 卡）
  - T2 同室連發堆疊 ✅（count 1→5 正確，無重複卡）
  - T3 多聊天室並存 ✅（兩張 Notify+ 卡並存、LINE 側 16880000 全清、零殘留）
  - T4 滑除全清 ✅（「本機通知被移除，清整組」）
  - 雙開分身（user 999）流量 ✅ 正常走完整流程
  - **全程零「重試」log = 每則都在第一檔 200ms 取消，速度與 vc13 相同**；
    重試階梯只在首查失敗才啟動（舊版該情況=永久殘留，新版=最多多活 1.4s 後被清）
  - vc17 紅燈另有 17:19 mArchive 三卡並存實證＋realme 用戶截圖
  - 未直接命中：GROUP_SUMMARY 形態的「接管 LINE summary」分支（本機 16880000 都在
    mirror 形態就被清掉、升不上 summary 態）——決策邏輯有 JVM 測試罩著，實地效果等
    realme 用戶回報
- ⏳ T5 點擊跳轉 / T6 快速回覆：Stan 手動測。
- 🔍 **競品「通知優化 for LINE」dex 掃描**：同為 listener+cancelNotification+
  getActiveNotifications+postDelayed，無 snooze/隱藏 API——「LINE heads-up 先彈」
  的物理限制對它同樣成立，雙響不是我們獨有的缺陷。
- 產物：AAB（⚠️ 此輪產物已被下方「加固輪」重建取代，勿上傳 `ca129ebb` 版）。
  上傳前照慣例先確認 vc18 未被 Console 佔用。
  ⚠️ A065 目前裝的是**本地簽章 vc18**——之後要換回 Play 軌道版本時需再次移除重裝。

### 下一步

1. watcher 抓到 vc17 紅燈（等訊息進場）→ 對照確認假說。
2. Stan 決定 vc18 驗證路徑（internal testing 上傳 / 本機重裝）。
3. 發布後請 realme 用戶確認：LINE 卡片是否只剩 Notify+ 一張、私密占位是否改為顯示 LINE 原通知。
4. 「heads-up 跳兩次」機制已實錘（2026-07-19 18:12 watcher：LINE child buzz →
   278ms 後我方 buzz，跨 package 雙響；LINE NewMessages 頻道 importance=4 全響）。
   **就算取代流程完美運作也會雙響**——我們是 listener，LINE 的第一響攔不住。**未修**，
   屬產品取捨：候選 (a) onboarding 引導使用者把 LINE 訊息頻道靜音（但 Notify+ fail-open
   時訊息會無聲）、(b) 偵測 LINE 剛響過就把我方那則設 silent（heads-up 就不彈）、
   (c) 接受現狀。等 Stan 拍板。

### 加固輪（2026-07-20 凌晨：獨立審查後三處守門收緊，同分支 +2 commits）

Codex 獨立驗證（verify-only）判「不可上架」；逐行複核後它指出的程式碼事實全部屬實，同分支加固：

- **反例 1｜summary 守門不分 profile**：`replacementActive` 原是「有任何一張我方卡就算」
  （連我方 Aggregate 聚合卡都算）、`lineChildActive` 沒比對 `sbn.user` → 雙開或跨 profile
  的卡可替別 profile 的 summary 背書，理論上可砍掉唯一內容載體。
  修：`roomKeyBelongsToProfile`（我方卡 roomKey 取自 `EXTRA_ROOM_KEY` extras、fallback
  `findRoomKeyByNotification`，比對 profileKey+`:` 前綴；null 不算背書）＋ LINE child 改比
  `profileKeyOf(active) == summaryProfileKey`。
- **反例 2｜redacted GROUP_SUMMARY 繞過遮蔽守門**：summary 分支排在 redaction 檢查之前
  （summary 不保證帶 text，檢查搬不進去）→ 遮蔽版 summary 可能被接管取消。
  修：`textMatchesRedactionPlaceholder`（null text 不算遮蔽），排程前命中即保留。
- **反例 3｜check-then-cancel TOCTOU**：**API 固有、vc13/vc17 歷代同款**——listener 只有
  按 key 取消、沒有比對內容的原子取消；handler 綁主執行緒＋callback 同線程，進程內無交錯，
  殘餘只剩 binder 飛行窗口（~ms）。窄化：`currentNotificationIsRedacted`——兩個取消點送出
  cancel 前最後重讀該 key 現行 text，已是占位字就放手；看不到通知時照取消（歷代 fail 方向）。
  **殘餘窗口記為固有限制；「絕不被取消」級別的保證做不到，文件與驗證單不得再這樣宣稱。**
- commits：`1c3e40c`（classifier+tests）、`f46e3a5`（listener）。JVM 47/0（XML 實讀 2+45）。
  版號維持 vc18/1.3.2（未曾發佈、不佔新 vc，changelog 文案仍準確）。
- 新 AAB：7,114,612 bytes，SHA-256 前綴 `0c3c54125d998b04`。
- ⏳ 待辦：A065 實機煙霧驗證（加固版同簽名 `install -r` + watcher 一輪自然訊息）——
  加固當下手機未連 ADB；之後 Codex 重審（驗證單 v2：宣稱措辭修正、commit 數 8、測試數 47）。

## Latest Session: 2026-07-15（Claude Code：修好「跳兩則」、收編全部工作、vc17 待上架）

> 接手的第一件事：**先讀 `AGENTS.md`（鐵則），再讀本節。** 下面兩節是歷史，僅供追溯。

### 一句話狀態

**所有工作已收編進 `master` 並 push（本地與 origin 同步，且都只剩 `master` 一條 branch）。
版本 vc17 / 1.3.1。工作區乾淨。唯一在跑的事：Stan 要把 release AAB 上傳 Play Console。**

> tip sha 別寫死在文件裡（寫死了每 commit 一次就爛一次）。要現況跑 `git log --oneline -1 master`。

### 做了什麼

從 Codex 交出一堆未 commit 的改動開始，到 vc17 可送審為止：

1. **收編 Codex 的 1957 行未 commit 改動**（19 個檔全躺在工作區，沒有版本足跡）。
   按相依層次切成 7 個 commit（`20febc3`..`9c10a30`），**每一個都 checkout 出來實際編譯 + 跑測試驗過**。
   不是按「8 個功能主題」切——那 8 個主題交錯在同一批函式裡，真要那樣切得憑空改寫 Codex 的程式碼、
   生出 8 個從沒被測試過的中間狀態當還原點。那是偽造歷史。
2. **修好「同一則訊息跳兩張卡」**（`f4f2b46`）。這是本次最實質的修復，詳見下方「關鍵決策」。
3. **修根因：AGENTS.md 的版本規則**（`e67273c`）。
4. **合併進 master + push**，並校正 `play-console-progress.md` 裡過期的 Impersonation blocker（`ddb5c6c`）。
5. 產出 vc17 / 1.3.1 的 release AAB 與中英 release notes（都在 500 字元上限內，已用 `wc -m` 實測）。

### 關鍵決策（附證據，別推翻）

- **雙 callback 去重的指紋不准放 `PendingIntent` 或 `MessagingStyle`。**
  Codex 的 v1.3.0 宣稱修好了「跳兩則」，**實機上一次都沒生效過**。實機 logcat 逐欄位比對後找到兩個原因：
  - `contentIntent.hashCode()` 在 tagged 與 legacy mirror 兩邊**必然不同**（實測 250458503 vs 131309470，
    而且是兩者唯一不同的欄位），卻被算進指紋 → 配對保證失敗。
  - legacy mirror **有時整個抽不到 MessagingStyle** → 指紋退回 null → 連配對都不試。

  修法是把指紋改由「兩邊必然相同」的結構性身分組成，並把組裝抽成純函式
  `NotificationClassifier.mirrorFingerprint(...)`——**參數列就是欄位白名單，型別層面傳不進 PendingIntent**。
  光寫註解擋不住下一個人再犯。**想加欄位，先在實機證明它兩邊一致。**

- **版號 bump 是收工義務，不是發版動作。**
  舊 AGENTS.md 把「別自己改 versionCode」和「別自己上傳 Play」寫成一句，agent 字面執行的結果是
  「版號不能碰」，於是 Codex 交出 1957 行零版本足跡的改動，Stan 裝到手機上完全看不出裝了什麼。
  規則已拆開重寫（`e67273c`）。**Stan 的硬性要求：他裝到手機時必須能在「關於」頁看到版號變了、
  看到這次改了什麼。**

- **Play Console 的狀態不准用讀 repo 的方式去猜。**
  我曾照 `play-console-progress.md` 判定「icon 像 LINE = Impersonation blocker」，Stan 說那是錯的:
  改名後重新送審已通過，icon 維持原樣沒問題。**那份文件的紅燈是 2026-06-23 退件當下的推測，
  被 Console 的實際裁決推翻了。** Console 只有 Stan 看得到。

### 目前狀態

- **能跑**：`assembleDebug` / `assembleRelease` / `bundleRelease` 全綠。
- **測試 42 個全過**（40 classifier + 2 ChatRoom）。跑 `./gradlew.bat testDebugUnitTest --rerun`。
  ⚠️ 不加 `--rerun` 會回 `UP-TO-DATE` 跳過測試卻仍印 `BUILD SUCCESSFUL`，那不是綠燈。
  拿真數字讀 `app/build/test-results/testDebugUnitTest/*.xml` 的 `tests=` / `failures=`。
- **實機驗證通過**（Nothing A059P / Android 16 / LINE 26.10.1）：一則訊息一張卡，群組也是。
  手機上目前裝著 vc17 / 1.3.1。
- **release 產物已備妥**：`app/build/outputs/bundle/release/app-release.aab`
  （7,112,501 bytes，SHA-256 `0a91222c75e10ab9…`，`jarsigner -verify` 通過，權限實測只有 2 個）。
- **線上隱私政策已部署且驗證**：push master 後 GitHub Pages 自動更新，`curl` 線上頁與 repo `docs/` 逐字一致。

### 已知問題 / 未驗證的東西

- 🔴 **Android 15+ 私密通知的遮蔽偵測，從來沒有真正執行過一次。**
  這是最重要的未結項。`isSystemRedactedNotification()` 有四個判斷條件，**只有「系統字串比對」拿到實證**
  （實機查到 `Resources.getSystem()` 回傳的繁中字串與用戶截圖逐字相同，且該查詢會跟著系統語系走，
  所以不需要為英文另外寫程式碼）。其餘三條（`subText == null`、`title == LINE 的 app label`、
  app label 讀得到）**零真實資料佐證**。
  **Stan 的手機重現不出遮蔽通知**（連刻意傳驗證碼格式也沒觸發系統的敏感內容分類器）。
  **唯一驗證路徑：等真的會遇到的用戶裝到 1.3.1 之後回報。**
  ⚠️ 別因為「程式碼看起來很完美、有單元測試、有詳細註解」就當它會動——Codex 的雙 callback 合併
  就是這樣，實機上一次都沒命中。**讀程式碼推不出行為。**

- ⚠️ **如果有人回報「私密訊息現在完全看不到通知了」，那不是 bug，是設計。** 遮蔽的訊息 Notify+ 會退場，
  讓 LINE 自己的通知顯示（LINE 看得到完整內容）。FAQ 裡有寫（`faq_q_sensitive_hidden`）。

- ℹ️ **Play Console 的兩個 warning 不用修**（已在 vc17 上重新驗證）：
  - *native debug symbols*：AAB 裡唯一的 `.so` 是 androidx 帶進來的 `libandroidx.graphics.path.so`，
    `file` 顯示它 **stripped**——符號表根本不存在，抽不出東西。`ndk { debugSymbolLevel = "FULL" }`
    早就設了（`app/build.gradle.kts:47`），AAB 的 `BUNDLE-METADATA/` 裡依然一個 symbol 都沒有。
    專案零自家原生程式碼，不可能在原生層當機。**修不了也不需要修。**
  - *沒有 deobfuscation 檔*：因為 R8 故意關著（鐵則 6）。沒混淆就沒對照表，這警告本來就會出現。

### 下一步

1. **Stan：上傳 vc17 到 Play Console。**
   - ⚠️ **先確認 vc17 沒被 Console 佔用過**（Release → App bundles 看歷史）。文件只確認 vc13、vc14 已燒掉，
     vc15、vc16 狀態不明。若 vc17 已被用掉，要 bump 到下一個可用號碼並重建 AAB（三件套一起改）。
   - AAB：`app/build/outputs/bundle/release/app-release.aab`
   - Release notes（中英，已壓進 500 字元）：`play-store-assets/store-listing.md`
2. ~~清掉殘留 branch~~ ✅ **已完成**（2026-07-15，見下方「branch 清理」）。
3. **上線後盯遮蔽偵測的回報。** 這是唯一能驗證它的路徑。

### branch 清理 ✅ 已完成（2026-07-15）

**本地與 origin 現在都只剩 `master`。** 原本殘留的 11 條全部刪除：7 條已併入 master（`-d` 乾淨刪），
4 條過期產物（`feat/*` ×3 落後 master 21 個 commit、`preview/all-three-2026-07-02` 是三條的重複預覽）
用 `-D` 強制刪。

刪除前已打 tag 釘住那 4 條的 commit，**本地與 origin 都有**，永遠救得回來：

```
archive/feat/notification-style-visual-guide      → a480542
archive/feat/permission-guidance                  → 2c3c610
archive/feat/problem-reporting                    → 7c09702
archive/preview/all-three-2026-07-02              → 1f7d3a6
```

要復原任何一條：`git checkout -b <名字> archive/<原分支名>`

> 為什麼刪：`feat/*` 是 `rebased/*` 的過期前身，內容 100% 被 master 覆蓋，而且合下去會衝突
> （`feat/notification-style-visual-guide` 解錯衝突還會把 master 刻意移除的 HelpActivity
> 「通知風格說明」卡復活）。`preview/` 與那三條 patch-id 重複。留著只會誤導下一個接手的人。

### 給下一個 AI 的提示

- **行為類改動你驗證不了，不准宣稱測過。** `BUILD SUCCESSFUL` 只證明編得過。這個 repo 有兩次血淋淋的例子：
  (1) `painterResource(R.mipmap.ic_launcher)` 編譯正常、一啟動就 crash；
  (2) Codex 的雙 callback 合併程式碼完美、測試齊全、實機一次都沒生效。
- **要看通知行為，用 logcat 不要用推論**：
  ```bash
  export ADB="C:/Users/stans/AppData/Local/Android/Sdk/platform-tools/adb.exe"
  export MSYS2_ARG_CONV_EXCL="*"        # 不加這行，Git Bash 會把 /system/... 轉成 Windows 路徑
  "$ADB" logcat -c
  "$ADB" logcat -v time LineNotify:V "*:S"
  ```
  關鍵 log：`收到訊息`（每則訊息應只出現一次）、`對話串通知 count=`（應只到 1）、
  `合併 LINE conversation/legacy mirror callback`（每則訊息應命中一次）、
  `系統已遮蔽敏感通知`（遮蔽偵測命中）。
- **`install -r` 之後一定要重綁 listener**，否則訊息不會跳：
  ```bash
  COMP="com.stanslab.linenotify/com.stanslab.linenotify.service.LineNotificationListener"
  "$ADB" shell cmd notification disallow_listener "$COMP"
  "$ADB" shell cmd notification allow_listener "$COMP"
  ```
  驗證有沒有綁上：`"$ADB" shell settings get secure enabled_notification_listeners | grep linenotify`
  （`cmd notification allowed_listeners` 不是有效指令，別用它判斷）
- **build 環境**：Windows 路徑含空格，先
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`。
- **commit 一律用明確路徑**（`git add <path>`），禁 `git add -A`：工作區有 `.claude/`、`tools/`、
  `build-install.bat`、`design/*.png` 等未追蹤雜物，會被掃進去。
- **設計決策仍然有效**：導航 B 模型、主要按鈕鎖品牌綠 `Green40 (#06C755)` 其餘跟系統動態取色、
  元件命名照 `design/COMPONENTS.md`。
- **回應一律繁體中文。**

### 建議下個 AI 用的 skill

- 通知行為出問題 → `diagnosing-bugs`（先建可重現的紅燈迴圈，別直接猜）
- 動 Compose UI → `impeccable`
- 切 commit / 版號策略 → `agent-skills:git-workflow-and-versioning`
- 宣稱完成前 → `superpowers:verification-before-completion`
- 對外文字（商店文案、release notes）→ `humanizer-zh-tw` / `humanizer`

---

## Previous Session: 2026-07-14（Codex：使用者通知回饋與實機稽核）

> ⚠️ 本節的成果**已全部收編進 master**（切成 `20febc3`..`9c10a30` 七個 commit）。
> 但本節宣稱「已修好」的**雙 callback 合併，實機上從未生效過**，已於 2026-07-15 重修（`f4f2b46`）。
> 本節的「沒有 bump、commit」是照當時 HANDOFF 的錯誤指令做的，不是 Codex 的錯。

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
