# Play Console 上架進度 (LINE Notify+)

最後更新：2026-05-27
App ID: 4975318820563729104 (com.stanslab.linenotify)

## 帳號狀態
- ✅ Google identity verification 通過
- ✅ App entry 已建立（Free, 繁中 zh-TW）

## 進行中
- Claude-in-Chrome 連線成功、可到達 Play Console
- ⚠️ 2026-05-27 嘗試填表時，Play Console 跳 Google 端錯誤「590B3ACE」卡在 loading
  → 還沒有任何表單填成功（privacy policy 尚未存）
  → 解法：重新整理頁面 / 稍後再試（590B3ACE 通常是暫時性）
- Dev account ID: 7824252807370180483（URL 用得到）

## App content 答案卡（逐項填寫指南）

| 表單 | 答案 | 性質 |
|---|---|---|
| **Privacy policy** | `https://stantheman0128.github.io/line-notify-plus/privacy-policy.html` | 純事實 |
| **App access** | All functionality available without special access（無登入機制；通知存取權是用戶授權，不算 login gate） | 純事實 |
| **Ads** | No, my app does not contain ads | 純事實 |
| **Content rating** | 開發者 email: stan@stan-shih.com；類別: Utility/Communication；所有暴力/性/毒品/賭博問題皆答 No → 預期 Everyone/3+ | ⚠️法律聲明 |
| **Target audience** | 建議 13 歲以上（避開 Families policy 額外合規）；不針對兒童 | ⚠️決策 |
| **Data safety** | No data collected / No data shared（純本機處理，無 analytics、無上傳）。In-app update 下載 APK 不算資料收集 | ⚠️法律聲明（最重要） |
| **Government apps** | No | 純事實 |
| **Financial features** | No | 純事實 |
| **Health** | No | 純事實 |
| **App category** | Communication（或 Tools）；聯絡 email stan@stan-shih.com | 純事實 |

## Store listing 素材（都在 store-listing.md）
- 短描述 46 字、完整描述 ~750 字、release notes
- 圖檔：play-store-assets/feature-graphic.png (1024x500)、screenshots/*.png (3張)
- ⭐ Icon 512（唯一正確）: **play-store-assets/play-store-icon-512.png**
  - 由 render_play_store_icon.mjs 從「手機實際用的」foreground 向量渲染
  - 指紋 4e05c9b15b1b == app/src/main/res shipped == v1.1-icon-assets
  - ⚠️ 不要用 index/*/icons/exports/play_store_icon.png（那些是錯的舊版本！）
- ⚠️ 圖檔上傳建議手動拖檔（瀏覽器自動化不穩）
- Tablet 截圖：optional，可跳過（只需手機截圖 ≥2 張）

## Tester 流程（critical path，14天時鐘瓶頸）
1. 收 **15-18 個 Gmail**（buffer，需求 12）— 必須是對方**手機 Play 商店登入的帳號**
2. 貼進 Closed Testing tester 名單
3. Play Console 產生 **opt-in 連結**（不會自動寄信！自己用 LINE/Threads 發）
4. testers 點連結 → 成為測試員 → Play 商店安裝
5. 14 天不退出、不刪 App → 之後申請 Production

## 下一步順序
1. 填完 App content 9 個表單（用上面答案卡）
2. App category + Store listing（貼文案 + 上傳圖檔）
3. Closed testing：建 release + 上傳 AAB (app/build/outputs/bundle/release/app-release.aab) + 啟用 Play App Signing
4. 加 testers + 發 opt-in 連結
5. 等 14 天 → 申請 Production

## Keystore（勿失）
keystore/line-notify-release.jks，密碼在 1Password，SHA1 見 git tag v1.1.0
