# Notify+ — Play Store 文案素材

最後更新：2026-08-29
版本：v1.5.0 (versionCode 31，實驗候選，尚未實機驗證或上傳)

> ⚠️ 2026-06-23 因 Impersonation policy 被退件（舊名「LINE Notify+」+ 舊 feature graphic 模仿 LINE 識別）。
> 本檔已改名為 Notify+、去掉 LINE 品牌主打、開頭即聲明第三方非官方。商店描述可以「描述功能會用到 LINE 通知」，但名稱與識別不可暗示官方關聯。

---

## 短描述（80 字以內）

```
同一人連發十則訊息，自動合併成一條通知，一眼看完、不漏訊息。整理你手機上的訊息通知。
```

字數：42 / 80

---

## 完整描述（4000 字以內）

```
Notify+ 把手機上洗版的訊息通知，整理成乾淨好讀的一條。

Notify+ 是獨立開發的第三方通知整理工具，與 LINE Corporation、LY Corporation 沒有任何關聯，也不是官方產品。它的用途是接手並重新整理 LINE 在你手機上跳出的通知。

如果你被通知顯示的方式困擾過——同一個聊天室一次傳了十則訊息，你卻只看得到最新一條——這個工具就是為你做的。

【核心功能】

▸ 對話串合併
同一個聊天室的多則訊息會自動合併成一條通知，依時間順序排列。一眼看完，不需要展開、不需要滾動。

▸ Apple 分組模式
偏好每則訊息獨立顯示？切換到分組模式，同聊天室的訊息會自動收合堆疊，跟 iPhone 的通知中心體驗一致。

▸ 取代原始通知
Notify+ 會接管原始通知顯示，避免一則訊息出現兩個通知卡的混亂狀況。

▸ 快速回覆
從通知卡直接打字回覆，不需要切換回原本的聊天 App。

▸ 頭貼顯示
每個通知都會帶上聯絡人頭貼，群組訊息可以一眼辨識誰在說話。

▸ 個別聊天室開關
不想被某個群組打擾？單獨關掉後會撤掉 Notify+ 與原始 LINE 通知，包含 @all 與直接標註。Android 若已先隱藏通知內容，App 可能無法辨識聊天室；部分手機也可能在撤掉前短暫顯示提示。

▸ 雙開帳號區分
同時登入兩個帳號時，會分清楚每則訊息是哪個帳號收到的。

▸ 點擊跳轉與自動消失
點 Notify+ 通知會直接打開對應聊天室；也可設定快速回覆後清除。保留 LINE 原通知時，LINE 通知被處理後可同步清除 Notify+。取代模式可選擇啟用實驗性的聊天室開啟偵測，在你直接進入 LINE 聊天室時嘗試清除對應通知。

【適合誰】

- 每天收訊息通知超過 50 則的重度使用者
- 有多個群組要管理的工作者
- 對通知整潔有強迫症的人
- Nothing Phone 用戶（為 Nothing OS 通知系統最佳化）

【權限說明】

- 通知存取權：用來讀取通知並重組顯示。所有資料只在你手機本機處理，不上傳任何伺服器。
- 無障礙服務（選配、預設關閉）：只用來辨識目前開啟的 LINE 聊天室並清除對應 Notify+ 通知；不保存訊息內容、不操作 LINE，其他功能不需啟用。
- App 完全沒有網路權限，不會上傳資料。完整訊息歷史不會寫入 App 自己的檔案；通知內容會交給 Android 顯示，並可能依系統通知紀錄設定保留。聊天室名稱、分類、開關、最後活躍時間與頭貼會保存在裝置本機。
- 只有你主動使用快速回覆時，輸入文字才會交給 LINE 的回覆介面完成傳送。

【關於資料隱私】

Notify+ 是純本機處理工具，沒有網路存取。我們不把通知資料上傳至開發者或自有伺服器；聊天室管理資料只保存在裝置本機，快速回覆則依你的操作交給 LINE。詳見隱私權政策：
https://stantheman0128.github.io/line-notify-plus/privacy-policy.html

【聯絡與支援】

GitHub：https://github.com/stantheman0128/line-notify-plus
有 bug 或建議歡迎回報。

---
本 App 為獨立第三方工具，與 LINE Corporation、LY Corporation 及 LINE 株式會社沒有任何關聯、合作或贊助關係。LINE 是其各自所有者的商標。
```

字數：約 720 / 4000

---

## Release notes v1.5.0 / vc31（實驗候選）

### 繁體中文

```
新增選配的 LINE 聊天室開啟偵測：直接進入聊天室時，嘗試清除對應的 Notify+ 通知。同名、雙開或辨識不完整時會保留通知。另修正從舊版升級時，先前隱藏的 LINE 通知可能再次出現的問題。
```

### English

```
Added optional LINE chat-open detection to try clearing the matching Notify+ notification when you enter a chat. Notifications stay when a chat is ambiguous or cannot be confirmed. Also fixed previously hidden LINE notifications possibly reappearing after an upgrade.
```

> 送審前仍須完成真實 LINE 實機回歸、Accessibility declaration 與示範影片。

---

## Release notes v1.3.1 / vc17（Play Console 上限 500 字元，已用 `wc -m` 實測）

### 繁中（470 / 500）

```
修正使用者回報的通知問題：

• 同一則訊息不再跳出兩張通知卡（群組甚至四張）
• 系統隱藏私密訊息時，不再重發空白的占位通知
• 聊天室關閉改為完全靜音，連 LINE 原通知一起收掉。之前關掉的要重開再關一次才套用
• 新通知送不出去時會保留 LINE 原通知，不漏訊息
• 聊天室分類可手動更正為好友、群組或社群
• 鎖定畫面不再顯示訊息內容
```

### English（469 / 500）

```
Fixes for the notification problems you reported:

• One message no longer shows as two cards (four in a group)
• No empty placeholder card when the system hides private content
• Turning a chat off now fully mutes it and clears LINE's notification. Chats turned off before need one on/off cycle
• If Notify+ cannot post its card, it keeps LINE's so you still see it
• You can correct a chat's type by hand
• The lock screen no longer shows message content
```

> 「之前關掉的要重開再關一次才套用」這句別刪。舊的 `disabled_chats` 語意（只是不增強、LINE 原通知照跳）
> 刻意保留不動，避免升級後無預警開始漏訊息；使用者必須手動重新開關一次才會切到新的完全靜音語意。
> 不講清楚，使用者會以為靜音壞掉。

### 上一版（v1.2.1）

```
這版改善使用者回報的通知行為與穩定性：

• Android 隱藏敏感通知內容時，不再重發系統占位文字
• 聊天室關閉後會連原始通知一起撤掉，包含 @all 與直接標註
• 社群分類更穩定，並可手動更正好友、群組或社群
• 限制分組通知數量，降低通知過多造成的不穩定
• 補強權限引導、問題回報與常見問題說明
```

---

## 開發者聯絡資訊（Play Console 必填）

- 開發者名稱：Stan Shih
- 聯絡 Email：stan@stan-shih.com
- 網站 URL：https://stantheman0128.github.io/line-notify-plus/
- 隱私權政策 URL：https://stantheman0128.github.io/line-notify-plus/privacy-policy.html

---

## Impersonation 退件後待辦（2026-06-23）

- [x] App 名稱改 Notify+（strings.xml app_name 中英 + 待改 Play Console「App name」欄位）
- [x] feature graphic 重做（去綠氣泡 / LINE 綠 / LINE 字）
- [x] 商店描述去 LINE 主打 + 開頭聲明第三方非官方 + 刪過時的安裝權限段
- [ ] Play Console 後台：確認 App name、feature graphic、商店描述與目前 vc15 Production 狀態
- [ ] **送審 blocker**：重做 launcher／Play icon；目前的 LINE 綠＋白色氣泡仍高度近似 LINE 品牌
- [ ] **送審 blocker**：全數重拍手機截圖；現有圖仍是舊品牌／舊功能，且含私密資料與不合規比例
- [ ] **送審 blocker**：部署新版 `docs/` 到 GitHub Pages，實際確認線上隱私政策已更新
- [ ] 若要刊登平板素材，改用真平板／模擬器的實際 UI；不可使用目前置中的手機截圖
- [ ] 「Notify+」名稱通用，提交前確認 Play Console 是否撞名
