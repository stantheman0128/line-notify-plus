# B 層行為 bug：候選修法與實機測試 checklist

> 這份文件只整理候選修法與 on-device 測試步驟。以下項目需要實體 Nothing Phone、已登入 LINE、真實 incoming LINE 訊息才能驗證；目前未宣稱已在實機測過。

## 1. 狀態欄浮窗回覆卡住

候選修法：

- 在 `ReplyRelayReceiver` 轉送 LINE 的 reply `PendingIntent` 時，保留原始 `Notification.Action.remoteInputs` 的完整 metadata，而不是只用 `resultKey` 重建單一 `RemoteInput`。
- 在 `RemoteInput.addResultsToIntent(...)` 後補 `RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)`，讓 LINE 收到明確的 free-form reply source。
- `lineActionIntent.send(...)` 成功後不要只取消單一 `notifId`；應同步取消該聊天室的 thread / apple child / summary notification，並清掉該聊天室 in-memory buffer，避免系統 spinner 或舊通知殘留。

實機測試：

- 安裝 debug APK，授權通知存取權限，啟用堆疊版本與取代原始通知。
- 執行 `adb logcat -s LineNotify`。
- 讓另一個 LINE 帳號傳 10 則訊息，其中至少 5 次直接從狀態欄 inline reply 回覆。
- 每次回覆後確認：spinner 會結束、LINE 真的送出訊息、LINE Notify+ 通知不殘留、log 沒有 `PendingIntent 已失效`。

## 2. 回覆後「我本人的頭貼」不顯示

候選修法：

- 目前 `MessagingStyle` 的 `me` 只有 `Person.name`，沒有 `Person.icon`。可新增 `selfIcon` cache，讓 reply optimistic message 與後續 MessagingStyle `me` 都帶 `Person.icon`。
- 若 LINE 不在 reply callback 提供本人頭貼，候選來源是 LINE 回送的本人訊息通知、`android.messages` extras 裡的 sender `Person`，或使用者可手動設定的本機頭貼。
- 沒抓到本人 icon 時，要保留現行 fallback，不應錯用對方 sender icon 當本人頭貼。

實機測試：

- 對個人聊天室與群組聊天室各做一次 inline reply。
- 回覆後查看 LINE Notify+ 重組通知：本人回覆訊息應顯示本人頭貼；對方訊息仍顯示對方頭貼。
- 重啟 App / 重啟手機後再次回覆，確認 cache 或 fallback 行為穩定。

## 3. 回覆 / 已讀 / 點擊跳轉後通知不自動消失

候選修法：

- 新增 content intent relay receiver：LINE Notify+ 通知被點擊時，先取消自己的 thread / apple child / summary，再轉發原始 LINE `contentIntent`。
- reply 成功後以 chat key 取消整組通知，而不是只取消當次 `notifId`。
- `onNotificationRemoved` 目前靠 `subText ?: title` 推回 `chatTitle`；可保存 `sbn.key -> chatKey` 對照，避免 LINE summary、群組、分身帳號造成錯誤匹配。

實機測試：

- Thread mode：收到訊息後點通知進 LINE，返回狀態欄，確認 LINE Notify+ 通知已消失。
- Apple grouping mode：收到同聊天室 3 則訊息後點其中一則，確認 child 與 summary 都不殘留。
- 在 LINE App 內直接讀取該聊天室，確認 LINE Notify+ 通知同步消失。
- inline reply 成功後確認同聊天室通知整組清除。

## 4. 雙開 LINE 無法區分來源

候選修法：

- chat key 從 `chatTitle` 改為 `profileKey + chatTitle`，其中 `profileKey` 來自 `StatusBarNotification.getUser()` / `UserHandle.identifier` 可取得的穩定值。
- 通知標題顯示帳號來源，例如「工作帳號 · 聊天室名稱」；若無法取得友善帳號名，先顯示「LINE 分身 1 / LINE 分身 2」。
- `known_chats`、`disabled_chats`、dedupe key、thread notification id、apple summary id 都要包含 profile key，避免兩個 LINE 帳號互相覆蓋。

實機測試：

- 在 Nothing Phone 上啟用 Dual Apps / 工作分身，登入兩個 LINE 帳號。
- 讓兩個帳號收到同名聊天室或同一人傳來的訊息。
- 確認 LINE Notify+ 通知標題可區分來源，兩邊訊息不合併，聊天室管理頁也能分別開關。
- 分別測 thread mode 與 Apple grouping mode。
