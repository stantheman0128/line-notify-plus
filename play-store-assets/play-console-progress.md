# Play Console 上架進度（Notify+）

最後更新：2026-07-14
App ID: 4975318820563729104 (com.stanslab.linenotify)
Dev account ID: 7824252807370180483（URL 用得到）

## ⚠️ 2026-06-23 Impersonation 退件 → 改名 Notify+

- vc12 (1.2.0) 上傳 Production 後被 **Impersonation policy** 退件。證據：App name「LINE Notify+」（撞 LINE 官方舊服務名 LINE Notify）+ feature graphic 模仿 LINE 識別（綠氣泡 icon + LINE 綠 + LINE 字 + 「重新定義你的 LINE 通知體驗」）。
- 處置：App 改名 **Notify+**（app_name 中英已改 + 待改 Console「App name」欄位）；feature graphic 重做（靛藍中性、堆疊通知卡、無任何 LINE 元素）；商店描述去 LINE 主打、開頭即聲明第三方非官方、刪掉過時的「安裝套件權限」段。
- vc13、vc14 已在 Play Console 使用過，不可重用。

### ✅ Impersonation 已解除（2026-07-15 Stan 於 Play Console 確認）

**改名 Notify+ 後重新送審已通過，icon 維持原樣（綠底白氣泡）沒有問題。**
本節先前列的三個 🔴 blocker 已全部作廢，逐一結案：

- ~~icon 需重做~~ → **不需要。** Stan 確認 Console 端已放行，維持現行 icon。
  ⚠️ 給未來的 AI／接手者：**別再依「icon 像 LINE」推論它是 blocker**。那是 2026-06-23 退件當下的推測，
  Console 的實際裁決推翻了它。Play Console 的狀態只有 Stan 看得到，不要用讀 repo 的方式去猜。
- ~~截圖需全部重拍~~ → 隨 Impersonation 解除一併結案（listing 已通過審查）。
- ~~線上隱私政策是舊版~~ → **已部署且已驗證**（2026-07-15）：push master 後 GitHub Pages 自動更新，
  `curl` 線上頁面與 repo `docs/privacy-policy.html` 內容逐字一致（差異僅 CRLF/LF）。

## ⭐ 待送審版本：versionCode 17 / 1.3.1（2026-07-15）

release 產物已建好並驗證：
- AAB：`app/build/outputs/bundle/release/app-release.aab`，7,112,501 bytes
  SHA-256 `0a91222c75e10ab9ebf18ea27a4dac6e3fed0388c8592d9d3dda394ccc0474e6`
- `jarsigner -verify` → `jar verified`（exit 0）
- `aapt2 dump badging` → `versionCode='17' versionName='1.3.1'`
- **權限只有 2 個**（`aapt2 dump permissions` 實測）：BIND_NOTIFICATION_LISTENER_SERVICE、POST_NOTIFICATIONS。無 INTERNET。
- 42 個 JVM 單元測試全過（`testDebugUnitTest --rerun`，非 UP-TO-DATE 假綠燈）。
- 實機驗證（Nothing A059P / Android 16 / LINE 26.10.1）：雙 callback 合併命中、一則訊息一張卡。

⚠️ **上傳前 Stan 要在 Console 確認 vc17 尚未被使用**（vc15、vc16 是否已佔用，repo 這邊無從得知）。

⚠️ **本版有一項未經真實驗證的行為**：Android 15+ 私密通知的遮蔽偵測。Stan 的手機重現不出遮蔽通知
（連刻意傳驗證碼格式也沒觸發系統的敏感內容分類器），所以四個判斷條件裡只有「系統字串比對」拿到實證，
其餘三條沒有真實資料佐證。上線後請留意回報。

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
