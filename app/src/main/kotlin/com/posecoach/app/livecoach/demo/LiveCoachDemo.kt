package com.posecoach.app.livecoach.demo

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.livecoach.config.LiveApiKeyManager
import com.posecoach.app.livecoach.ui.LiveCoachOverlay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 示範如何整合 Live Coach 模組到現有應用中
 */
class LiveCoachDemo(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private lateinit var liveCoachManager: LiveCoachManager
    private lateinit var liveCoachOverlay: LiveCoachOverlay
    private lateinit var apiKeyManager: LiveApiKeyManager

    fun setupLiveCoach() {
        // 1. 初始化 API 金鑰管理器
        apiKeyManager = LiveApiKeyManager(context)

        // 檢查 API 金鑰有效性
        if (!apiKeyManager.hasValidApiKey()) {
            Timber.w("Invalid API key detected: ${apiKeyManager.getObfuscatedApiKey()}")
            // 可選：顯示設定畫面讓用戶輸入金鑰
        }

        // 2. 初始化 Live Coach 管理器
        liveCoachManager = LiveCoachManager(
            context = context,
            lifecycleScope = lifecycleScope
            // 不傳入 apiKey 參數，會自動使用 LiveApiKeyManager 的金鑰
        )

        // 3. 設定 UI 覆蓋層
        setupUI()

        // 4. 觀察回應和狀態
        observeResponses()

        Timber.i("Live Coach initialized with API key: ${apiKeyManager.getObfuscatedApiKey()}")
    }

    private fun setupUI() {
        liveCoachOverlay = LiveCoachOverlay(context)

        liveCoachOverlay.setOnLiveCoachListener(object : LiveCoachOverlay.OnLiveCoachListener {
            override fun onStartSession() {
                Timber.d("Starting Live Coach session")
                liveCoachManager.startPushToTalkSession()
            }

            override fun onStopSession() {
                Timber.d("Stopping Live Coach session")
                liveCoachManager.stopPushToTalkSession()
            }

            override fun onRetryConnection() {
                Timber.d("Retrying connection")
                liveCoachManager.forceReconnect()
            }
        })
    }

    private fun observeResponses() {
        // 觀察連線狀態
        lifecycleScope.launch {
            liveCoachManager.sessionState.collect { state ->
                // Map enum state to connection state for overlay
                val connectionState = when (state) {
                    com.posecoach.gemini.live.models.SessionState.DISCONNECTED -> com.posecoach.app.livecoach.models.ConnectionState.DISCONNECTED
                    com.posecoach.gemini.live.models.SessionState.CONNECTING -> com.posecoach.app.livecoach.models.ConnectionState.CONNECTING
                    com.posecoach.gemini.live.models.SessionState.CONNECTED,
                    com.posecoach.gemini.live.models.SessionState.SETUP_PENDING,
                    com.posecoach.gemini.live.models.SessionState.SETUP_COMPLETE,
                    com.posecoach.gemini.live.models.SessionState.ACTIVE -> com.posecoach.app.livecoach.models.ConnectionState.CONNECTED
                    com.posecoach.gemini.live.models.SessionState.DISCONNECTING -> com.posecoach.app.livecoach.models.ConnectionState.RECONNECTING
                    com.posecoach.gemini.live.models.SessionState.ERROR -> com.posecoach.app.livecoach.models.ConnectionState.ERROR
                }

                val isRecording = state == com.posecoach.gemini.live.models.SessionState.ACTIVE ||
                                  state == com.posecoach.gemini.live.models.SessionState.SETUP_COMPLETE

                liveCoachOverlay.updateConnectionState(connectionState)
                liveCoachOverlay.setRecording(isRecording)

                Timber.d("Session state: $state, Recording: $isRecording")
            }
        }

        // 觀察 AI 教練回應
        lifecycleScope.launch {
            liveCoachManager.coachingResponses.collect { response ->
                liveCoachOverlay.showCoachingResponse(response)
                Timber.i("Coach response: $response")
            }
        }

        // 觀察語音轉錄
        lifecycleScope.launch {
            liveCoachManager.transcriptions.collect { transcription ->
                liveCoachOverlay.showTranscription(transcription)
                Timber.d("Transcription: $transcription")
            }
        }

        // 觀察錯誤
        lifecycleScope.launch {
            liveCoachManager.errors.collect { error ->
                liveCoachOverlay.showError(error)
                Timber.e("Live Coach error: $error")
            }
        }
    }

    fun updateApiKey(newApiKey: String) {
        if (apiKeyManager.validateApiKey(newApiKey)) {
            apiKeyManager.setApiKey(newApiKey)
            Timber.i("API key updated successfully")

            // 重新初始化連線
            liveCoachManager.forceReconnect()
        } else {
            Timber.e("Invalid API key format")
        }
    }

    fun getApiKeyStatus(): String {
        return when {
            !apiKeyManager.hasValidApiKey() -> "未設定或無效的 API 金鑰"
            else -> "API 金鑰已設定: ${apiKeyManager.getObfuscatedApiKey()}"
        }
    }

    fun getSessionInfo(): Map<String, Any?> {
        return liveCoachManager.getSessionInfo()
    }

    fun destroy() {
        liveCoachManager.destroy()
        Timber.d("Live Coach demo destroyed")
    }
}