# Gemini Live API 快速修復指南

## 🚀 快速啟動檢查清單

執行以下步驟可在 **5 分鐘內** 讓 Live API 正常運作。

---

## 步驟 1：配置 API 金鑰 ⭐ **最重要**

### 1.1 取得 Gemini API 金鑰

1. 前往 [Google AI Studio](https://makersuite.google.com/app/apikey)
2. 登入您的 Google 帳戶
3. 點擊 "Get API Key" 或 "建立 API 金鑰"
4. 複製產生的 API 金鑰（格式：`AIza...`）

### 1.2 在專案中配置金鑰

在專案根目錄建立或編輯 `local.properties` 檔案：

```properties
# Gemini API Keys
gemini.api.key=YOUR_ACTUAL_API_KEY_HERE
gemini.live.api.key=YOUR_ACTUAL_API_KEY_HERE

# 範例（請替換成您的真實金鑰）:
# gemini.api.key=AIzaSyABC123...xyz789
# gemini.live.api.key=AIzaSyABC123...xyz789
```

**重要提示：**
- ✅ API 金鑰必須以 `AIza` 開頭
- ✅ 長度至少 35 個字元
- ✅ 只包含字母、數字、底線和連字符
- ⚠️ **絕對不要** 將 `local.properties` 提交到 Git！

### 1.3 驗證配置

確保 `.gitignore` 包含：
```gitignore
local.properties
```

---

## 步驟 2：檢查權限配置

### 2.1 驗證 AndroidManifest.xml

確認 `app/src/main/AndroidManifest.xml` 包含以下權限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

### 2.2 在應用中請求權限

在您的 Activity 中加入權限請求：

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class YourActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 請求麥克風權限
        requestAudioPermission()
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 權限已授予，可以開始使用 Live API
                    startLiveApi()
                } else {
                    // 權限被拒絕
                    Toast.makeText(
                        this,
                        "需要麥克風權限才能使用語音功能",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
```

---

## 步驟 3：檢查隱私設定

如果您的應用使用 `EnhancedPrivacyManager`，確保 Live API 功能未被阻擋：

```kotlin
val privacyManager = EnhancedPrivacyManager(context)

// 檢查是否處於離線模式
if (privacyManager.isOfflineModeEnabled()) {
    // 關閉離線模式以使用 Live API
    privacyManager.setOfflineMode(false)
}

// 檢查音訊上傳是否允許
if (!privacyManager.isAudioUploadAllowed()) {
    // 允許音訊上傳
    privacyManager.setAudioUploadAllowed(true)
}
```

---

## 步驟 4：清理並重新建構專案

```bash
# Windows (命令提示字元或 PowerShell)
.\gradlew clean assembleDebug

# macOS/Linux
./gradlew clean assembleDebug
```

---

## 步驟 5：測試連線

### 5.1 啟用詳細日誌

在您的 `Application` 類別中：

```kotlin
class PoseCoachApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 啟用 Timber 日誌（僅 Debug 版本）
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

### 5.2 初始化 Live Coach

```kotlin
import com.posecoach.app.livecoach.LiveCoachManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var liveCoachManager: LiveCoachManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Live Coach Manager
        liveCoachManager = LiveCoachManager(
            context = applicationContext,
            lifecycleScope = lifecycleScope
        )

        // 啟動 Live API 連線
        lifecycleScope.launch {
            startLiveCoach()
        }
    }

    private fun startLiveCoach() {
        liveCoachManager.start()

        // 監聽錯誤
        lifecycleScope.launch {
            liveCoachManager.errors.collect { error ->
                Timber.e("Live API Error: $error")
                // 在 UI 顯示錯誤訊息
                runOnUiThread {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        }

        // 監聽 AI 回應
        lifecycleScope.launch {
            liveCoachManager.coachingResponses.collect { response ->
                Timber.d("AI Response: $response")
                // 處理 AI 回應
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        liveCoachManager.destroy()
    }
}
```

### 5.3 檢查 Logcat 輸出

執行應用後，在 Android Studio 的 Logcat 中尋找：

**✅ 成功的標誌：**
```
📡 Live API WebSocket Connection
🔗 URL: wss://generativelanguage.googleapis.com/ws/...
🔑 API Key present: ✓
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ WebSocket Connected Successfully!
📊 Response Code: 101
```

**❌ 失敗的標誌：**
```
❌ WebSocket Connection FAILED
📊 HTTP Code: 401
🔑 Authentication Error: Invalid or expired API key
💡 Solution: Check your API key in local.properties
```

---

## 常見問題排除

### Q1: "Cannot connect: API key is empty"

**原因：** `local.properties` 未配置或未正確讀取

**解決方案：**
1. 確認 `local.properties` 存在於專案根目錄
2. 確認檔案包含 `gemini.api.key=YOUR_KEY`
3. 執行 `File > Invalidate Caches / Restart`
4. 重新建構專案：`.\gradlew clean assembleDebug`

---

### Q2: "API key format is invalid"

**原因：** API 金鑰格式不正確

**解決方案：**
1. 確認金鑰以 `AIza` 開頭
2. 確認沒有多餘的空格或引號
3. 確認長度至少 35 個字元
4. 從 Google AI Studio 重新產生金鑰

---

### Q3: "Audio permission required"

**原因：** 麥克風權限未授予

**解決方案：**
1. 在應用中主動請求權限（見步驟 2.2）
2. 在裝置設定中手動授予權限：
   - 設定 > 應用程式 > Pose Coach > 權限 > 麥克風 > 允許

---

### Q4: "離線模式已啟用，Live Coach 功能暫停使用"

**原因：** 隱私設定阻擋 Live API

**解決方案：**
```kotlin
val privacyManager = EnhancedPrivacyManager(context)
privacyManager.setOfflineMode(false)
privacyManager.setAudioUploadAllowed(true)
```

---

### Q5: WebSocket 連線成功但無 AI 回應

**可能原因：**
1. Setup 訊息發送失敗
2. 音訊數據未正確編碼
3. Live API quota 已用盡

**診斷步驟：**
```kotlin
// 檢查連線狀態
val sessionInfo = liveCoachManager.getSessionInfo()
Timber.d("Session Info: $sessionInfo")

// 檢查是否有音訊數據發送
Timber.d("Is Recording: ${liveCoachManager.isRecording()}")
```

---

## 驗證成功的指標

您可以確認 Live API 正常運作，如果看到：

1. ✅ Logcat 顯示 "WebSocket Connected Successfully"
2. ✅ 沒有錯誤訊息在 `liveCoachManager.errors` flow
3. ✅ `liveCoachManager.sessionState` 顯示 `CONNECTED` 或 `ACTIVE`
4. ✅ 說話時 `liveCoachManager.transcriptions` 收到文字轉錄
5. ✅ `liveCoachManager.coachingResponses` 收到 AI 回應

---

## 完整診斷腳本

如果以上步驟仍無法解決問題，執行以下診斷：

```kotlin
fun diagnoseConnection() {
    lifecycleScope.launch {
        val diagnostics = buildString {
            appendLine("━━━━━━ Gemini Live API 診斷 ━━━━━━")

            // 1. API 金鑰檢查
            val apiKeyManager = LiveApiKeyManager(applicationContext)
            appendLine("1. API Key Valid: ${apiKeyManager.hasValidApiKey()}")
            appendLine("   API Key: ${apiKeyManager.getObfuscatedApiKey()}")

            // 2. 權限檢查
            val hasAudioPerm = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            appendLine("2. Audio Permission: $hasAudioPerm")

            // 3. 網路檢查
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val hasNetwork = cm.activeNetwork != null
            appendLine("3. Network Available: $hasNetwork")

            // 4. 隱私設定
            val privacyManager = EnhancedPrivacyManager(applicationContext)
            appendLine("4. Offline Mode: ${privacyManager.isOfflineModeEnabled()}")
            appendLine("   Audio Upload Allowed: ${privacyManager.isAudioUploadAllowed()}")

            // 5. Live Coach 狀態
            appendLine("5. Live Coach State: ${liveCoachManager.getConnectionState()}")
            appendLine("   Is Recording: ${liveCoachManager.isRecording()}")
            appendLine("   Is Speaking: ${liveCoachManager.isSpeaking()}")

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }

        Timber.i(diagnostics)
        Toast.makeText(this@MainActivity, "診斷已完成，請查看 Logcat", Toast.LENGTH_SHORT).show()
    }
}
```

---

## 取得協助

如果問題仍未解決：

1. 📋 執行完整診斷腳本
2. 📸 截圖 Logcat 錯誤訊息
3. 📝 提供 `local.properties` 配置（隱藏金鑰）
4. 📄 參考完整診斷報告：`docs/LIVE_API_DIAGNOSTIC_REPORT.md`

---

**最後更新：** 2025-10-07
**預計修復時間：** 5-10 分鐘
