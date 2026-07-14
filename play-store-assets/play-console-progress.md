# Play Console 上架進度（Notify+）

最後更新：2026-07-14
App ID: 4975318820563729104 (com.stanslab.linenotify)
Dev account ID: 7824252807370180483（URL 用得到）

## ⚠️ 2026-06-23 Impersonation 退件 → 改名 Notify+

- vc12 (1.2.0) 上傳 Production 後被 **Impersonation policy** 退件。證據：App name「LINE Notify+」（撞 LINE 官方舊服務名 LINE Notify）+ feature graphic 模仿 LINE 識別（綠氣泡 icon + LINE 綠 + LINE 字 + 「重新定義你的 LINE 通知體驗」）。
- 處置：App 改名 **Notify+**（app_name 中英已改 + 待改 Console「App name」欄位）；feature graphic 重做（靛藍中性、堆疊通知卡、無任何 LINE 元素）；商店描述去 LINE 主打、開頭即聲明第三方非官方、刪掉過時的「安裝套件權限」段。
- vc13、vc14 已在 Play Console 使用過，不可重用；repo 目前是 **vc15 / 1.2.1**，權限仍只有 2 個。
- 🔴 **再次送審 blocker**：launcher／Play icon 仍是 LINE 綠＋白色聊天氣泡，與既有退件原因高度相近；必須先建立獨立品牌識別，並同步更新 App、512 icon、功能圖與對外封面。
- 🔴 **再次送審 blocker**：4 張手機截圖全部仍顯示舊名「LINE Notify+」，其中一張仍宣稱已移除的 App 內更新；多張含真實聊天室、頭像、SSID、其他 App 通知與 USB debugging，且 3 張比例不合 Play 規格。8 張 tablet 圖只是把舊手機圖置中，不是平板 UI，也不可使用。
- 🔴 **再次送審 blocker**：GitHub Pages 線上隱私政策仍是舊版內容；repo 的 `docs/` 已修正，但尚未部署。部署後要實際開 URL 確認，再填 Play Console。
- Console 待辦：確認 App name、feature graphic、商店描述、全新 icon／截圖與已部署的隱私政策，再用**尚未被 Console 使用的 versionCode** 送審。若 vc15 已上傳過，下一次必須從 vc16 起，並同步完成版號三件套。

## 帳號狀態
- ✅ Google identity verification 通過
- ✅ App entry 已建立（Free, 繁中 zh-TW）

## ⭐ Repo 目前版本：versionCode 15 / 1.2.1
- 2026-07-14 22:08（UTC+8）已用目前 source clean rerun `lintDebug`、38 個 JVM 測試、debug/release APK 與 AAB：全部成功。
- release APK：7,595,943 bytes，SHA-256 `69500B611EE10AA3D4E6E361C6EF16096139B1EBA2046E1BB8B0B4A09C7D2D3E`；
  release AAB：7,113,665 bytes，SHA-256 `7769C4A073E111790E153BB44A977117C8767AA34899B3F0831F60A1E7C22CFE`。
- APK 已驗 minSdk 26 / targetSdk 35、allowBackup=false、16K zipalign、v2 signature（1 signer）；AAB `jarsigner -verify` exit 0。
- 這些產物是目前 source 的驗證包，但因 icon／截圖／線上政策／versionCode 狀態仍未解，**不可直接上傳 Play**。
- 簽章 SHA1: F4:51:BA:A8:...（同 keystore）
- **權限只剩 2 個**：BIND_NOTIFICATION_LISTENER_SERVICE + POST_NOTIFICATIONS
- vc10 曾完成實機驗證；這不代表 vc15 或之後版本已完成 release 回歸。每次送審前仍須重跑 build、權限 dump 與實機通知流程。

## 上傳遇到的問題與解法（已解）
- ⚠️ 2026-05-27 Console 一度跳 Google 端 loading 錯誤 590B3ACE/741585AE（暫時性，重整即可）
- 🔴 vc9 上傳時跳 **REQUEST_INSTALL_PACKAGES sensitive-permission error**
  → 根因：in-app GitHub updater 用此權限自我安裝 APK，違反 Google 政策
  → **解法：已移除整個 in-app updater（commit 1b77c22），vc10 不再有此權限** ✅
  → 所以改用 vc10 上傳，不會再出現此 error

## App content 答案卡（逐項填寫指南）

| 表單 | 答案 | 性質 |
|---|---|---|
| **Privacy policy** | `https://stantheman0128.github.io/line-notify-plus/privacy-policy.html`；目前線上仍是舊版，部署 repo `docs/` 後才可提交 | 🔴待部署與複查 |
| **App access** | All functionality available without special access（無登入機制；通知存取權是用戶授權，不算 login gate） | 純事實 |
| **Ads** | No, my app does not contain ads | 純事實 |
| **Content rating** | 開發者 email: stan@stan-shih.com；類別: Utility/Communication；所有暴力/性/毒品/賭博問題皆答 No → 預期 Everyone/3+ | ⚠️法律聲明 |
| **Target audience** | 建議 13 歲以上（避開 Families policy 額外合規）；不針對兒童 | ⚠️決策 |
| **Data safety** | No data collected / No data shared（純本機處理，無 analytics、無上傳、無網路）。v1.1.0 已移除 in-app updater，App 現在完全無網路存取 | ⚠️法律聲明（最重要） |
| **Government apps** | No | 純事實 |
| **Financial features** | No | 純事實 |
| **Health** | No | 純事實 |
| **App category** | Communication（或 Tools）；聯絡 email stan@stan-shih.com | 純事實 |

## Store listing 素材（文案在 store-listing.md）
- 文案現況：短描述 42 字、完整描述約 720 字、release notes 已更新。
- ✅ `feature-graphic.png`：1024×500、無 LINE 品牌元素，格式可用；新 icon 完成後建議統一靛藍視覺。
- 🔴 `play-store-icon-512.png` 與 launcher icon：格式合格、品牌不合格，必須重做。
- 🔴 `screenshots/*.png` 4 張手機圖：全數重拍；使用虛構資料、繁中系統，移除 SSID／其他通知／USB debugging，並符合 Play 比例。
- 🔴 `screenshots/tablet/` 8 張：全數不可用。若要刊登平板素材，需在真平板或模擬器拍實際 UI；否則不要上傳。
- 🔴 `line-oa-cover.png` 仍是舊名與舊品牌；若仍對外使用也要重做。
- ⚠️ `render_play_store_icon.mjs`、`build_line_oa_cover.py`、`build_tablet_screenshots.py` 會重生問題素材；前者的舊 resvg import 路徑也已不存在。新品牌完成後要一起修或淘汰腳本。
- 圖檔上傳建議手動拖檔（瀏覽器自動化不穩）。

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
- **vc10: 移除 in-app updater** ← 權限只剩 2 個
- vc12: 1.2.0，Production 因 Impersonation policy 退件
- vc13–14: Play Console 已使用，不可重用
- **vc15: repo 目前版本 1.2.1**；是否已被 Console 使用，上傳前須在 Console 確認

## Keystore（勿失）
keystore/line-notify-release.jks，密碼在 1Password，SHA1 見 git tag v1.1.0
