# Play Console 上架進度 (LINE Notify+)

最後更新：2026-05-30
App ID: 4975318820563729104 (com.stanslab.linenotify)
Dev account ID: 7824252807370180483（URL 用得到）

## 帳號狀態
- ✅ Google identity verification 通過
- ✅ App entry 已建立（Free, 繁中 zh-TW）

## ⭐ 目前要上傳的版本：versionCode 10 / 1.1.0
- 檔案：app/build/outputs/bundle/release/app-release.aab（6.16 MB）
- 簽章 SHA1: F4:51:BA:A8:...（同 keystore）
- **權限只剩 2 個**：BIND_NOTIFICATION_LISTENER_SERVICE + POST_NOTIFICATIONS
- 手機已實機驗證 vc10 運作正常（通知監聽 + 對話串合併皆正常）

## 上傳遇到的問題與解法（已解）
- ⚠️ 2026-05-27 Console 一度跳 Google 端 loading 錯誤 590B3ACE/741585AE（暫時性，重整即可）
- 🔴 vc9 上傳時跳 **REQUEST_INSTALL_PACKAGES sensitive-permission error**
  → 根因：in-app GitHub updater 用此權限自我安裝 APK，違反 Google 政策
  → **解法：已移除整個 in-app updater（commit 1b77c22），vc10 不再有此權限** ✅
  → 所以改用 vc10 上傳，不會再出現此 error

## App content 答案卡（逐項填寫指南）

| 表單 | 答案 | 性質 |
|---|---|---|
| **Privacy policy** | `https://stantheman0128.github.io/line-notify-plus/privacy-policy.html` | 純事實 |
| **App access** | All functionality available without special access（無登入機制；通知存取權是用戶授權，不算 login gate） | 純事實 |
| **Ads** | No, my app does not contain ads | 純事實 |
| **Content rating** | 開發者 email: stan@stan-shih.com；類別: Utility/Communication；所有暴力/性/毒品/賭博問題皆答 No → 預期 Everyone/3+ | ⚠️法律聲明 |
| **Target audience** | 建議 13 歲以上（避開 Families policy 額外合規）；不針對兒童 | ⚠️決策 |
| **Data safety** | No data collected / No data shared（純本機處理，無 analytics、無上傳、無網路）。v1.1.0 已移除 in-app updater，App 現在完全無網路存取 | ⚠️法律聲明（最重要） |
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

## 版本演進備忘
- vc8: 第一版 1.1.0（齒輪會被圓形遮罩切）
- vc9: icon 縮 0.80 修正齒輪
- **vc10: 移除 in-app updater（當前要上傳的版本）** ← 權限只剩 2 個

## Keystore（勿失）
keystore/line-notify-release.jks，密碼在 1Password，SHA1 見 git tag v1.1.0
