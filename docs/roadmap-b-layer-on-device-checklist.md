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

## 5. 權限提示返回後不更新

候選修法：

- `MainActivity` 已在 `Lifecycle.Event.ON_RESUME` 重新讀取 notification listener 狀態；若實機仍重現，下一步應加入短期診斷 log，確認從系統設定返回時是否真的觸發 `ON_RESUME`。
- 若 `Settings.Secure.enabled_notification_listeners` 在返回當下有延遲，可在 `ON_RESUME` 後用 condition polling 重新讀取 1-2 秒，直到狀態改變或逾時。
- 若使用者在設定頁授權後，系統尚未 bind listener，可補一個「已授權，等待系統啟動服務」狀態，避免 UI 仍顯示成未授權。

實機測試：

- 清掉 App 資料後首次開啟，確認顯示需要通知存取權限。
- 點「開啟通知存取權限」，在 Android 設定中授權 LINE Notify+。
- 直接返回 App，觀察主畫面是否自動切成服務狀態與設定區，不需要殺 App 重開。
- 重複測試取消授權再返回 App，確認 UI 也會回到未授權狀態。

## 6. 通知處理邏輯可自選

候選修法：

- 先不要直接改 cancel 行為；新增設定前要把現有「取代原始通知」「回覆後清除」「已讀同步清除」「點擊後清除」拆成可描述的策略。
- 候選設定可分為三個開關：回覆成功後清除通知、從通知點進 LINE 後清除通知、LINE 內已讀後同步清除通知。
- 每個開關都應只影響 LINE Notify+ 自己發出的通知，不應阻止 LINE 原始 PendingIntent 執行。
- 需要實機確認 LINE 不同版本的 `contentIntent` / reply `PendingIntent` 是否在通知被取消後仍能穩定送出。

實機測試：

- Thread mode 與 Apple grouping mode 各測一次。
- 分別切換三個候選開關，對每個開關測：收到訊息、點通知、inline reply、進 LINE 已讀。
- 確認關閉某項自動清除時，對應通知保留；開啟時，child / summary / thread notification 都清除。
- 確認不論開關狀態，點擊跳轉與 inline reply 都仍會送到 LINE。

## 7. 收回訊息保留

候選修法：

- 先記錄 LINE 收回訊息時的 `StatusBarNotification` extras，確認是否會送出「已收回訊息」類型的新通知、或只是原訊息被移除。
- 若 LINE 送出收回事件，候選做法是在原訊息 text 上標記「已收回」但保留原 notification history 中已堆疊的文字。
- 若 LINE 只是移除通知，必須避免把 `onNotificationRemoved` 誤判成使用者已讀並清空整個聊天室 buffer。
- 此功能涉及隱私預期，實作前應在 FAQ 說明「收回訊息可能仍出現在通知歷史」並提供關閉選項。

實機測試：

- 個人聊天室、群組、社群各測一次：對方傳訊息後收回。
- 確認 LINE Notify+ 通知是否保留原訊息，並以明確標籤表示該訊息已被收回。
- 確認關閉此候選功能時，收回訊息不再保留。
- 確認收回事件不會清掉同聊天室中其他未讀訊息。
