# LINE Notify+ Roadmap

## 已完成 (v1.0.6)
- [x] 對話串模式 / Apple 分組模式
- [x] 快速回覆
- [x] 個別聊天室開關（社群/群組/個人）
- [x] 取代原始 LINE 通知
- [x] 頭貼顯示
- [x] 點擊跳轉 + 自動消失
- [x] App 內一鍵更新
- [x] 版本更新紀錄

## 短期 (v1.1)
- [ ] 重新設計 App Icon（不含 LINE 商標，適合上架 Google Play）
- [ ] 通知風格說明頁面（附截圖/動圖比較兩種模式）
- [ ] 滑動效能優化（release build + R8）
- [ ] Q&A 問答頁面

## 中期 (v1.2)
- [ ] 問題回報（整合 LINE 官方帳號或 GitHub Issues）
- [ ] 功能許願池
  - 贊助排名機制：付越多，許願的功能越優先
  - 可能實現方式：
    - LINE Pay / 綠界金流整合
    - 許願清單 + 贊助金額排序
    - 後端：Supabase（你已有帳號）存許願 + 贊助記錄
    - 前端：App 內嵌 WebView 顯示許願排行榜
- [ ] 收回訊息保留（在通知中顯示已被收回的訊息）

## 長期 (v2.0)
- [ ] 上架 Google Play
- [ ] 訊息搜尋（跨聊天室搜尋通知歷史）
- [ ] 自訂通知音效 / 震動模式
- [ ] Widget（桌面小工具顯示最近訊息）
- [ ] 多語言支援（英文/日文）

## 許願池商業模式構想

```
使用者開啟 App → 功能許願池頁面
  → 看到其他人的許願 + 目前贊助金額排名
  → 自己許願 + 贊助（最低 $30 TWD）
  → 贊助越多排名越前
  → 開發者按排名優先實作

技術實現：
  前端：App 內 WebView → 你的網站
  後端：Supabase
    - wishes 表：wish_id, title, description, total_amount, status
    - sponsors 表：sponsor_id, wish_id, amount, line_user_id, created_at
  金流：LINE Pay 或綠界 ECPay
  通知：透過 LINE 官方帳號推播進度更新
```
