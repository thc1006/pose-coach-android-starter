# GitLab Release 建立指引

由於 `glab` CLI 工具未安裝，請手動在 GitLab 建立 Release：

## 📋 步驟

1. **訪問 GitLab 專案**
   - 前往：https://gitlab.com/thc1006/pose-coach-android-starter

2. **建立 Tag**
   - 左側選單：Repository → Tags
   - 點擊「New tag」
   - Tag name: `v1.0.0-rotation-fix`
   - Create from: `main`
   - Message: `v1.0.0 - Rotation Fix & Camera Switching`
   - 點擊「Create tag」

3. **建立 Release**
   - 左側選單：Deployments → Releases
   - 點擊「New release」
   - Select tag: `v1.0.0-rotation-fix`
   - Release title: `v1.0.0 - Rotation Fix & Camera Switching`

4. **填寫 Release Notes**

```markdown
# 🎉 Pose Coach v1.0.0 - 主要修復版本

## 🎯 重大修復

### 1. ✅ 修復骨架旋轉 90° 問題
- **問題**：直立的人顯示為側躺
- **原因**：MediaPipe 和 EnhancedCoordinateMapper 雙重旋轉處理
- **解決**：移除 EnhancedCoordinateMapper 的旋轉處理，MediaPipe 已正確處理旋轉
- **影響**：所有裝置方向（0°/90°/180°/270°）現在都能正確顯示骨架

### 2. 🎥 實作前置/後置相機切換功能
- 新增藍色相機切換按鈕（FloatingActionButton）
- 支援無縫切換前後鏡頭
- 前置相機自動鏡像處理
- 用戶友好的 Toast 提示

### 3. 🎤 修復 Gemini Live API 功能
- API 金鑰管理強化（多層級來源檢查）
- WebSocket 連線增強（詳細錯誤日誌）
- 音訊格式符合官方規範（16kHz, 16-bit PCM）
- MIME type 修正（audio/pcm;rate=16000）
- WebSocket URL 修正（v1alpha）
- 完整的診斷工具和驗證器

## 📦 新增功能

- **座標正規化工具**：支援所有旋轉角度的座標轉換
- **Live API 診斷工具**：自動化診斷所有關鍵配置
- **規範驗證器**：Runtime 合規性檢查
- **詳細文檔**：完整的驗收測試指南和快速修復指南

## 📱 APK 檔案

此 Release 包含兩個 APK 版本：
- **app-arm64-v8a-release-unsigned.apk** - ARM64 裝置（大多數現代 Android 手機）
- **app-x86_64-release-unsigned.apk** - x86_64 裝置（模擬器或特定硬體）

⚠️ **注意**：這些是 unsigned APK，需要自行簽名後才能安裝到裝置上。

## 🔧 技術改進

- 加強錯誤日誌與偵錯資訊
- 符合 Gemini Live API 官方規範
- 改善座標轉換精確度
- 優化相機生命週期管理
- 完整的單元測試覆蓋

## ✅ 測試狀態

- ✅ Debug APK 編譯成功
- ✅ Release APK 編譯成功
- ✅ 所有核心模組測試通過
- ⚠️ 需要實際裝置驗證

## 📚 文檔

- [旋轉修復驗證指南](docs/manual-verification/pose-rotation-fix-verification.md)
- [Live API 診斷報告](docs/LIVE_API_DIAGNOSTIC_REPORT.md)
- [Live API 快速修復指南](docs/LIVE_API_QUICK_FIX_GUIDE.md)
- [完整修復總結](docs/LIVE_API_FIX_SUMMARY.md)

## 🙏 特別感謝

感謝所有測試和回報問題的用戶！

---

**Full Changelog**: https://gitlab.com/thc1006/pose-coach-android-starter/-/compare/ea315ec...cd2f07e
```

5. **上傳 APK 檔案**
   - 在 Release 頁面，找到「Release assets」區塊
   - 點擊「Add another asset」
   - Type: `Package`
   - URL: 上傳 `app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk`
   - Link title: `app-arm64-v8a-release-unsigned.apk`
   - 重複步驟上傳第二個 APK：`app-x86_64-release-unsigned.apk`

6. **發布 Release**
   - 檢查所有資訊無誤
   - 點擊「Create release」

## 📝 APK 檔案位置

APK 檔案位於：
- `app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk`
- `app/build/outputs/apk/release/app-x86_64-release-unsigned.apk`

## ✅ 完成後

Release 將顯示在：
- https://gitlab.com/thc1006/pose-coach-android-starter/-/releases

---

**注意**：如果需要使用 `glab` CLI 工具自動化此流程，可以安裝：
```bash
# Windows (Scoop)
scoop install glab

# macOS (Homebrew)
brew install glab

# Linux
curl -s https://gitlab.com/gitlab-org/cli/-/raw/main/scripts/install.sh | sudo bash
```
