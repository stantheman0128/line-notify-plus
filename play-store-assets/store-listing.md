# Notify+ — Play Store 文案素材

最後更新：2026-06-23
版本：v1.2.0 (versionCode 13)

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
不想被某個群組打擾？單獨關掉那個聊天室的通知整理，其他聊天室不受影響。

▸ 雙開帳號區分
同時登入兩個帳號時，會分清楚每則訊息是哪個帳號收到的。

▸ 點擊跳轉與自動消失
點通知會直接打開對應的聊天室；訊息看過後，通知會自動清掉（可在設定切換清除時機）。

【適合誰】

- 每天收訊息通知超過 50 則的重度使用者
- 有多個群組要管理的工作者
- 對通知整潔有強迫症的人
- Nothing Phone 用戶（為 Nothing OS 通知系統最佳化）

【權限說明】

- 通知存取權：用來讀取通知並重組顯示。所有資料只在你手機本機處理，不上傳任何伺服器。
- App 完全沒有網路權限，不收集、不傳輸、不儲存任何訊息內容或聯絡人資料。

【關於資料隱私】

Notify+ 是純本機處理工具，沒有網路存取。我們不收集、不傳輸、不儲存你的任何訊息內容或聯絡人資料。詳見隱私權政策：
https://stantheman0128.github.io/line-notify-plus/privacy-policy.html

【聯絡與支援】

GitHub：https://github.com/stantheman0128/line-notify-plus
有 bug 或建議歡迎回報。

---
本 App 為獨立第三方工具，與 LINE Corporation、LY Corporation 及 LINE 株式會社沒有任何關聯、合作或贊助關係。LINE 是其各自所有者的商標。
```

字數：約 720 / 4000

---

## Release notes (v1.2.0，500 字以內)

```
這版主要修通知行為，也順手改了介面：

• App 更名為 Notify+
• 修正狀態欄快速回覆送不出、一直轉圈的問題
• 可以設定回覆後或已讀後自動清掉通知，不再堆一排
• 雙開帳號時會分清楚哪則訊息是哪個帳號的
• 回覆時會顯示你自己的頭貼
• 介面和用字重新整理過，看起來更好懂
```

字數：約 100 / 500

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
- [ ] Play Console 後台：改 App name 欄位、換 feature graphic、貼新描述、重傳 vc13 AAB
- [ ] 殘餘風險：icon 仍是綠氣泡，二次提交可能再被抓；「Notify+」名稱通用，留意是否撞名
