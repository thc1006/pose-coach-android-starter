# Gemini Live API 診斷報告

## 執行時間
2025-10-07

## 診斷摘要

經過全面掃描和分析，已識別出 Gemini Live API 功能失效的多個根本原因。

---

## 🔴 已識別的關鍵問題

### 1. **雙重實作架構衝突** ⚠️ 嚴重

**問題描述：**
專案中存在兩套獨立的 Live API 實作，造成功能混亂和維護困難：

- **Kotlin 實作**：`app/src/main/kotlin/com/posecoach/app/livecoach/`
  - `LiveCoachManager.kt`
  - `LiveApiWebSocketClient.kt`
  - `AudioStreamManagerRefactored.kt`

- **Java 實作**：`app/src/main/java/com/posecoach/gemini/live/`
  - `LiveApiManager.kt`
  - `LiveApiWebSocketClient.kt`
  - `AudioProcessor.kt`

**影響：**
- 兩套系統互相衝突
- 不清楚哪一套被實際使用
- 依賴注入混亂
- 錯誤無法正確追蹤

**建議修復：**
1. 確定使用哪一套實作（建議使用 Kotlin 實作，因為更完整）
2. 移除或封存另一套實作
3. 統一 API 介面

---

### 2. **API 金鑰配置缺失** 🔑 嚴重

**問題描述：**
`LiveApiKeyManager.kt` 中的預設 API 金鑰為空字串：

```kotlin
private const val DEFAULT_API_KEY = "" // API key must be set via setApiKey() or local.properties
```

**根本原因：**
- `local.properties` 檔案未正確配置 `gemini.live.api.key`
- 應用啟動時無法取得有效的 API 金鑰
- WebSocket 連線請求包含空白或無效的金鑰

**影響：**
- 所有 Live API 請求立即失敗
- WebSocket 連線無法建立
- 返回 401 Unauthorized 錯誤

**修復步驟：**
1. 在專案根目錄建立或更新 `local.properties`
2. 加入有效的 Gemini API 金鑰：
   ```properties
   gemini.api.key=YOUR_GEMINI_API_KEY
   gemini.live.api.key=YOUR_GEMINI_API_KEY
   ```
3. 確保 `.gitignore` 包含 `local.properties`
4. 重新建構專案

---

### 3. **WebSocket 連線配置問題** 🌐 高

**問題描述：**
在 `LiveApiWebSocketClient.kt` (kotlin 版本) 中發現：

```kotlin
val url = "${ConnectionConfig.WEBSOCKET_URL}?key=$apiKey"
```

**潛在問題：**
- 使用 API Key 而非 Ephemeral Token（不符合 Live API 規範）
- Java 版本使用 `EphemeralTokenManager`，但 Kotlin 版本沒有
- 端點 URL 可能不正確或過時

**正確做法（參考官方文檔）：**
```kotlin
// 應該使用 ephemeral token
val token = tokenManager.getValidToken()
val url = "$WEBSOCKET_URL?key=$token"

// WebSocket URL (from Java implementation)
private const val WEBSOCKET_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
```

---

### 4. **音訊權限運行時檢查不足** 🎤 中

**問題描述：**
雖然 `AndroidManifest.xml` 正確聲明了權限：
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

但 `LiveCoachManager.kt` 中的權限檢查可能在應用啟動時失敗：

```kotlin
if (!audioManager.hasAudioPermission()) {
    lifecycleScope.launch {
        _errors.emit("Audio permission required for voice coaching")
    }
    return
}
```

**影響：**
- 用戶可能從未看到權限請求對話框
- Live API 功能靜默失敗
- 錯誤訊息不夠明確

**建議修復：**
1. 在 Activity 中主動請求權限
2. 提供清晰的權限拒絕說明
3. 加入權限狀態追蹤日誌

---

### 5. **隱私設定阻擋功能** 🔒 中

**問題描述：**
在 `LiveCoachManager.kt` 中發現多處隱私檢查：

```kotlin
// 離線模式檢查
if (privacyManager.isOfflineModeEnabled()) {
    lifecycleScope.launch {
        _errors.emit("離線模式已啟用，Live Coach 功能暫停使用")
    }
    return
}

// 音訊上傳檢查
if (stateManager.isConnected() &&
    privacyManager.isAudioUploadAllowed() &&
    !privacyManager.isOfflineModeEnabled()) {
    webSocketClient.sendRealtimeInput(audioInput)
}
```

**影響：**
- 如果用戶啟用了離線模式，Live API 完全不可用
- 音訊/影像上傳被阻擋時，Live API 無法正常工作
- 隱私設定可能在用戶不知情的情況下預設為最高級別

**建議檢查：**
1. 確認隱私設定的預設值
2. 在 UI 中清楚顯示隱私狀態
3. 提供一鍵啟用 Live API 的選項

---

### 6. **錯誤處理和日誌不足** 📝 中

**問題描述：**
雖然有基本的錯誤處理，但缺少：
- 連線失敗的詳細原因
- WebSocket 錯誤代碼的解析
- API 回應錯誤的完整記錄
- 連線狀態變化的追蹤日誌

**影響：**
- 難以診斷問題根本原因
- 用戶只看到模糊的錯誤訊息
- 開發者無法快速定位問題

**建議改進：**
```kotlin
Timber.d("Live API connecting to: $endpoint")
Timber.d("API key present: ${apiKey.isNotEmpty()}")
Timber.d("Audio format: sampleRate=$sampleRate, channels=$channels")
Timber.d("WebSocket state: $state")
Timber.e(e, "Live API connection failed with code: $code")
```

---

## 📋 完整問題清單

| # | 問題 | 嚴重性 | 狀態 |
|---|------|--------|------|
| 1 | 雙重實作架構衝突 | 嚴重 | 待修復 |
| 2 | API 金鑰配置缺失 | 嚴重 | 待修復 |
| 3 | WebSocket 使用 API Key 而非 Token | 高 | 待修復 |
| 4 | 音訊權限運行時檢查不足 | 中 | 待改進 |
| 5 | 隱私設定阻擋功能 | 中 | 待確認 |
| 6 | 錯誤處理和日誌不足 | 中 | 待改進 |
| 7 | 缺少完整的連線重試機制 | 低 | 待改進 |
| 8 | 音訊串流配置未驗證 | 低 | 待測試 |

---

## 🔧 立即修復計劃

### 階段 1：解決配置問題 (最高優先級)

1. **建立 `local.properties` 檔案**
   ```properties
   # Gemini API Keys
   gemini.api.key=YOUR_ACTUAL_GEMINI_API_KEY
   gemini.live.api.key=YOUR_ACTUAL_GEMINI_API_KEY
   ```

2. **驗證 API 金鑰格式**
   - 確保金鑰以 `AIza` 開頭
   - 長度至少 35 個字元
   - 使用 `LiveApiKeyManager.validateApiKey()` 驗證

3. **測試金鑰是否有效**
   ```bash
   curl "https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_API_KEY"
   ```

### 階段 2：統一實作架構

1. **選擇主要實作**
   - 建議使用：`app/src/main/kotlin/com/posecoach/app/livecoach/`
   - 原因：更完整、更新、與專案架構一致

2. **移除或封存次要實作**
   - 移動 `app/src/main/java/com/posecoach/gemini/live/` 到 `app/src/archive/`
   - 或完全刪除（確保先備份）

3. **更新所有依賴引用**
   - 檢查 Activity 中的導入
   - 更新 DI 配置（如果有）

### 階段 3：修復 WebSocket 連線

1. **實作 Ephemeral Token Manager**
   ```kotlin
   class EphemeralTokenManager {
       suspend fun getValidToken(): String? {
           // 從後端 API 取得 ephemeral token
           // 或在開發階段暫時使用 API key
           return apiKey
       }
   }
   ```

2. **更新 WebSocket URL**
   ```kotlin
   private const val WEBSOCKET_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
   ```

3. **加入連線狀態詳細日誌**

### 階段 4：改進錯誤處理

1. **加入診斷模式**
   ```kotlin
   fun enableDiagnosticMode(enabled: Boolean) {
       if (enabled) {
           Timber.plant(Timber.DebugTree())
       }
   }
   ```

2. **實作健康檢查端點**
   ```kotlin
   suspend fun checkLiveApiHealth(): HealthStatus {
       return HealthStatus(
           apiKeyValid = apiKeyManager.hasValidApiKey(),
           permissionsGranted = hasAudioPermission(),
           networkAvailable = networkManager.isConnected(),
           privacyModeAllowed = !privacyManager.isOfflineModeEnabled()
       )
   }
   ```

---

## 🧪 測試檢查清單

### 配置驗證
- [ ] `local.properties` 包含有效的 API 金鑰
- [ ] API 金鑰格式正確（`AIza...`）
- [ ] API 金鑰可訪問 Gemini API

### 權限檢查
- [ ] 應用啟動時請求麥克風權限
- [ ] 權限被拒絕時顯示清晰說明
- [ ] 權限授予後可以開始錄音

### 連線測試
- [ ] WebSocket 可以成功建立連線
- [ ] Setup 訊息成功發送
- [ ] 收到 `setupComplete` 回應
- [ ] 音訊數據可以發送到 Live API
- [ ] 收到 AI 回應（文字或音訊）

### 錯誤處理
- [ ] 連線失敗時顯示具體錯誤
- [ ] API 金鑰無效時給出明確提示
- [ ] 網路斷線時自動重連
- [ ] 日誌包含足夠的診斷資訊

---

## 📝 診斷腳本

建立以下檔案以快速診斷問題：

```kotlin
// DiagnosticActivity.kt
class DiagnosticActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runDiagnostics()
    }

    private fun runDiagnostics() {
        val results = mutableListOf<String>()

        // 1. 檢查 API 金鑰
        val apiKey = BuildConfig.GEMINI_LIVE_API_KEY
        results.add("API Key Present: ${apiKey.isNotEmpty()}")
        results.add("API Key Valid Format: ${apiKey.startsWith("AIza")}")

        // 2. 檢查權限
        val audioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        results.add("Audio Permission: $audioPermission")

        // 3. 檢查網路
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork != null
        results.add("Network Available: $network")

        // 4. 檢查隱私設定
        val privacyManager = EnhancedPrivacyManager(this)
        results.add("Offline Mode: ${privacyManager.isOfflineModeEnabled()}")
        results.add("Audio Upload Allowed: ${privacyManager.isAudioUploadAllowed()}")

        // 顯示結果
        results.forEach { Timber.d("DIAGNOSTIC: $it") }
    }
}
```

---

## 🎯 預期結果

修復所有問題後，應該能夠：

1. ✅ 成功建立 WebSocket 連線到 Gemini Live API
2. ✅ 發送音訊數據並接收 AI 回應
3. ✅ 在 Logcat 中看到清晰的連線狀態日誌
4. ✅ 權限被拒絕時得到明確提示
5. ✅ 網路問題時自動重連
6. ✅ 所有錯誤都有詳細的日誌記錄

---

## 📞 下一步行動

1. **立即執行**：修復 API 金鑰配置
2. **短期目標**：統一實作架構
3. **中期目標**：完善錯誤處理和日誌
4. **長期目標**：建立完整的整合測試

---

## 附錄：相關檔案路徑

### Kotlin 實作（建議使用）
- `C:\Users\thc1006\Desktop\dev\pose-coach-android-starter\app\src\main\kotlin\com\posecoach\app\livecoach\LiveCoachManager.kt`
- `C:\Users\thc1006\Desktop\dev\pose-coach-android-starter\app\src\main\kotlin\com\posecoach\app\livecoach\websocket\LiveApiWebSocketClient.kt`
- `C:\Users\thc1006\Desktop\dev\pose-coach-android-starter\app\src\main\kotlin\com\posecoach\app\livecoach\config\LiveApiKeyManager.kt`

### Java 實作（待處理）
- `C:\Users\thc1006\Desktop\dev\pose-coach-android-starter\app\src\main\java\com\posecoach\gemini\live\LiveApiManager.kt`
- `C:\Users\thc1006\Desktop\dev\pose-coach-android-starter\app\src\main\java\com\posecoach\gemini\live\client\LiveApiWebSocketClient.kt`

### 配置檔案
- `C:\Users\thc1006\Desktop\dev\pose-coach-android-starter\app\build.gradle.kts`
- `C:\Users\thc1006\Desktop\dev\pose-coach-android-starter\app\src\main\AndroidManifest.xml`
- `C:\Users\thc1006\Desktop\dev\pose-coach-android-starter\local.properties` (需要建立)

---

**診斷完成日期：** 2025-10-07
**報告版本：** 1.0
