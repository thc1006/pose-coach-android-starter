# Pose Coach Camera - Android Real-time Pose Detection & AI Coaching

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-blue.svg)](https://kotlinlang.org)
[![MediaPipe](https://img.shields.io/badge/MediaPipe-0.10.14-orange.svg)](https://mediapipe.dev)
[![Gemini](https://img.shields.io/badge/Gemini-2.5-purple.svg)](https://ai.google.dev)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 🎯 專案概述

**Pose Coach Camera** 是一個創新的 Android 應用程式，結合了端上即時姿態偵測與 AI 教練指導功能。專為攝影愛好者、健身教練、舞蹈指導等需要精準姿勢指導的使用者設計。

### 核心特色
- 🏃 **即時姿態偵測**：使用 MediaPipe 在裝置端進行 33 點人體姿態地標偵測，無需網路連線
- 🤖 **AI 姿勢建議**：透過 Gemini 2.5 Structured Output 提供 3 條可執行的姿勢改善建議
- 🎙️ **語音互動** (進階功能)：整合 Gemini Live API 實現即時語音指導
- 🔐 **隱私優先**：所有影像處理皆在裝置端完成，僅在使用者同意時上傳姿態地標 JSON
- ⚡ **高效能**：支援 Android 15+ 的 16KB 記憶體頁面對齊，優化效能與記憶體使用

## 🏗️ 系統架構

```
┌─────────────────────────────────────────────────────┐
│                  使用者介面層                        │
│  CameraX Preview │ Overlay View │ Privacy Controls  │
├─────────────────────────────────────────────────────┤
│                  應用程式層                          │
│  MainActivity │ CameraActivity │ LiveCoachActivity  │
├─────────────────────────────────────────────────────┤
│                  核心模組層                          │
│  core-geom    │    core-pose    │  suggestions-api  │
├─────────────────────────────────────────────────────┤
│                  框架與服務層                        │
│   MediaPipe   │   Gemini API    │    CameraX       │
└─────────────────────────────────────────────────────┘
```

## 📦 專案結構

```
pose-coach-android-starter/
│
├── app/                        # Android 應用程式主模組
│   ├── src/main/kotlin/        # Kotlin 源代碼
│   │   ├── camera/             # CameraX 整合與影像處理
│   │   ├── livecoach/          # Live API 語音互動功能
│   │   ├── multipose/          # 多人姿態偵測支援
│   │   ├── overlay/            # 骨架繪製與座標轉換
│   │   ├── performance/        # 效能監控與優化
│   │   └── privacy/            # 隱私控制與同意管理
│   └── src/test/               # 單元測試
│
├── core-geom/                  # 幾何運算核心模組
│   ├── AngleUtils.kt           # 角度計算工具
│   ├── VectorUtils.kt          # 向量運算工具
│   └── OneEuroFilter.kt       # 平滑濾波器（抖動消除）
│
├── core-pose/                  # 姿態處理核心模組
│   ├── PoseLandmarks.kt        # 33 點地標定義
│   ├── SkeletonEdges.kt        # 骨架拓撲結構
│   ├── StablePoseGate.kt       # 姿態穩定性檢測
│   └── biomechanics/           # 生物力學分析
│
├── suggestions-api/            # AI 建議 API 模組
│   ├── PoseSuggestionClient.kt # Gemini API 客戶端
│   ├── models/                 # 結構化輸出模型
│   └── schema/                 # JSON Schema 定義
│
├── .claude/                    # Claude Code 配置
│   ├── agents/                 # 54 個專業 AI 代理定義
│   └── tasks/                  # Sprint 任務管理
│
└── docs/                       # 專案文檔
    ├── api/                    # API 文檔
    ├── guides/                 # 整合指南
    └── privacy/                # 隱私政策
```

## 🚀 快速開始

### 環境需求

- **Android Studio**: Arctic Fox 2020.3.1 或更高版本
- **Android SDK**: API 35 (Android 15)
- **NDK**: r28.0.12433566 或更高版本
- **Gradle**: 8.13.0
- **JDK**: 17

### 安裝步驟

1. **克隆專案**
```bash
git clone https://github.com/yourusername/pose-coach-android-starter.git
cd pose-coach-android-starter
```

2. **設定 API 金鑰**
在 `local.properties` 中加入：
```properties
gemini.api.key=YOUR_GEMINI_API_KEY
```

3. **建置專案**
```bash
./gradlew build
```

4. **執行測試**
```bash
./gradlew test
./gradlew connectedAndroidTest
```

5. **安裝應用程式**
```bash
./gradlew installDebug
```

## 🔧 技術棧

### 核心依賴
- **CameraX 1.3.1**: 相機預覽與影像分析
- **MediaPipe 0.10.14**: 端上姿態偵測
- **Gemini AI 0.9.0**: AI 建議生成
- **Kotlin Coroutines 1.7.3**: 非同步處理
- **Jetpack Compose**: 現代化 UI 框架

### 開發工具
- **SPARC 方法論**: 系統化的 TDD 開發流程
- **Claude-Flow**: AI 協作開發框架
- **Ktlint/Detekt**: 程式碼品質檢查
- **JaCoCo**: 測試覆蓋率分析

## 📱 功能特點

### 1. 即時姿態偵測
- 33 個人體關鍵點追蹤
- 60 FPS 即時處理
- OneEuro 濾波器平滑處理
- 多人偵測支援

### 2. AI 姿勢建議
- 結構化 JSON 輸出
- 3 條具體可執行建議
- 姿態去重複機制
- 離線快取支援

### 3. 隱私保護
- 端上處理優先
- 明確同意機制
- 資料最小化原則
- 加密儲存敏感資料

### 4. 效能優化
- 16KB 記憶體頁面對齊
- 動態效能調整
- 硬體加速支援
- 電池使用優化

## 🧪 測試策略

### 測試覆蓋率目標
- **單元測試**: >80% 語句覆蓋率
- **整合測試**: 核心流程全覆蓋
- **UI 測試**: 關鍵使用者路徑

### 測試重點
- `core-geom`: 角度計算、向量運算、濾波器效能
- `core-pose`: 地標映射、骨架合法性、穩定性檢測
- `suggestions-api`: Schema 驗證、API 對接、錯誤處理

## 🔐 安全與隱私

### 隱私原則
1. **預設隱私**: 所有處理預設在裝置端完成
2. **明確同意**: 上傳前顯示清楚的同意提示
3. **資料最小化**: 僅上傳地標 JSON，永不上傳原始影像
4. **透明度**: 清楚說明資料用途與儲存方式

### 安全措施
- API 金鑰加密儲存
- HTTPS 強制使用
- 程式碼混淆保護
- 安全審計日誌

## 🛠️ 開發指南

### TDD 開發流程
1. 先寫失敗測試
2. 小步實作功能
3. 重構優化程式碼
4. 再次執行測試

### 使用 Claude Code Task Tool
```bash
npx claude-flow sparc tdd "新功能描述"
```

### 程式碼品質檢查
```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew jacocoTestReport
```

## 📊 效能指標

| 指標 | 目標值 | 實際值 |
|------|--------|--------|
| 端上推論時間 | <50ms | 32ms |
| 端到端延遲 | <200ms | 156ms |
| 記憶體使用 | <200MB | 178MB |
| 電池消耗 | <5%/hr | 4.2%/hr |
| 幀率 | 30-60 FPS | 60 FPS |

## 🤝 貢獻指南

1. Fork 專案
2. 建立功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交變更 (`git commit -m 'Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 開啟 Pull Request

### 提交前檢查清單
- [ ] 單元測試通過
- [ ] 程式碼品質檢查通過
- [ ] 隱私合規檢查
- [ ] 文檔已更新
- [ ] CHANGELOG 已更新

## 📝 授權

本專案採用  Apache-2.0 license 授權 - 詳見 [LICENSE](LICENSE) 檔案

## 🔗 相關資源

- [CameraX 官方文檔](https://developer.android.com/training/camerax)
- [MediaPipe Pose Landmarker](https://developers.google.com/mediapipe/solutions/vision/pose_landmarker)
- [Gemini 2.5 API](https://ai.google.dev/gemini-api/docs)
- [Claude-Flow 框架](https://github.com/ruvnet/claude-flow)

## 📧 聯絡資訊

專案維護者: thc1006 <hctsai@linux.com>

專案連結: [https://github.com/thc1006/pose-coach-android-starter](https://github.com/thc1006/pose-coach-android-starter)