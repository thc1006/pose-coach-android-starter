package com.posecoach.app.livecoach

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.gson.Gson
import com.posecoach.app.livecoach.audio.AudioStreamManager
import com.posecoach.app.livecoach.camera.ImageSnapshotManager
import com.posecoach.app.livecoach.config.LiveApiKeyManager
import com.posecoach.app.livecoach.models.*
import com.posecoach.app.livecoach.state.LiveCoachStateManager
import com.posecoach.app.livecoach.websocket.LiveApiWebSocketClient
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

class LiveCoachManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    apiKey: String? = null
) {
    private val apiKeyManager = LiveApiKeyManager(context)
    private val privacyManager = EnhancedPrivacyManager(context)
    private val effectiveApiKey = apiKey ?: apiKeyManager.getApiKey()

    private val stateManager = LiveCoachStateManager()
    private val webSocketClient = LiveApiWebSocketClient(effectiveApiKey, stateManager, lifecycleScope)
    private val audioManager = AudioStreamManager(context, lifecycleScope)
    private val imageManager = ImageSnapshotManager(lifecycleScope)
    private val gson = Gson()

    private var collectionJobs = mutableListOf<Job>()
    private var currentLandmarks: PoseLandmarkResult? = null

    private val _coachingResponses = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val coachingResponses: SharedFlow<String> = _coachingResponses.asSharedFlow()

    private val _transcriptions = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val transcriptions: SharedFlow<String> = _transcriptions.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // Expose state for UI
    val sessionState: StateFlow<SessionState> = stateManager.sessionState

    init {
        setupDataFlows()
    }

    private fun setupDataFlows() {
        // Collect audio input and forward to WebSocket (with privacy check)
        lifecycleScope.launch {
            audioManager.realtimeInput.collect { audioInput ->
                if (stateManager.isConnected() &&
                    privacyManager.isAudioUploadAllowed() &&
                    !privacyManager.isOfflineModeEnabled()) {
                    webSocketClient.sendRealtimeInput(audioInput)
                    Timber.v("Audio input sent to Live API")
                } else {
                    Timber.v("Audio input blocked by privacy settings")
                }
            }
        }

        // Handle barge-in detection
        lifecycleScope.launch {
            audioManager.bargeInDetected.collect { timestamp ->
                handleBargeInEvent(timestamp)
            }
        }

        // Monitor audio quality
        lifecycleScope.launch {
            audioManager.audioQuality.collect { qualityInfo ->
                handleAudioQualityUpdate(qualityInfo)
            }
        }

        // Collect image snapshots and forward to WebSocket (with privacy check)
        lifecycleScope.launch {
            imageManager.realtimeInput.collect { imageInput ->
                if (stateManager.isConnected() &&
                    privacyManager.isImageUploadAllowed() &&
                    !privacyManager.isOfflineModeEnabled()) {
                    webSocketClient.sendRealtimeInput(imageInput)
                    Timber.v("Image input sent to Live API")
                } else {
                    Timber.v("Image input blocked by privacy settings")
                }
            }
        }

        // Process WebSocket responses
        lifecycleScope.launch {
            webSocketClient.responses.collect { response ->
                handleLiveApiResponse(response)
            }
        }

        // Handle errors from all components (with core function isolation)
        lifecycleScope.launch {
            merge(
                webSocketClient.errors,
                audioManager.errors,
                imageManager.errors
            ).collect { error ->
                handleCloudError(error)
            }
        }
    }

    fun startPushToTalkSession() {
        // Check if offline mode is enabled
        if (privacyManager.isOfflineModeEnabled()) {
            lifecycleScope.launch {
                _errors.emit("離線模式已啟用，Live Coach 功能暫停使用")
            }
            return
        }

        if (!audioManager.hasAudioPermission()) {
            lifecycleScope.launch {
                _errors.emit("Audio permission required for voice coaching")
            }
            return
        }

        if (stateManager.isConnecting() || stateManager.isConnected()) {
            Timber.w("Session already active")
            return
        }

        Timber.d("Starting push-to-talk session")

        // Connect to Live API
        webSocketClient.connect()

        // Wait for connection before starting audio/video
        lifecycleScope.launch {
            stateManager.sessionState
                .filter { it.connectionState == ConnectionState.CONNECTED }
                .take(1)
                .collect {
                    startRecordingAndSnapshots()
                    sendCurrentLandmarksAsContext()
                }
        }
    }

    private fun startRecordingAndSnapshots() {
        stateManager.setRecording(true)

        // Start audio recording only if allowed by privacy settings
        if (privacyManager.isAudioUploadAllowed()) {
            // Enable barge-in mode for the session
            audioManager.enableBargeInMode(true)
            audioManager.startRecording()
            Timber.d("Audio recording started with barge-in enabled")
        } else {
            Timber.d("Audio recording disabled by privacy settings")
        }

        // Start image snapshots only if allowed by privacy settings
        if (privacyManager.isImageUploadAllowed()) {
            imageManager.startSnapshots()
            Timber.d("Image snapshots started")
        } else {
            Timber.d("Image snapshots disabled by privacy settings")
        }
    }

    private fun sendCurrentLandmarksAsContext() {
        currentLandmarks?.let { landmarks ->
            lifecycleScope.launch {
                sendLandmarksAsTextInput(landmarks)
            }
        }
    }

    private suspend fun sendLandmarksAsTextInput(landmarks: PoseLandmarkResult) {
        // Always send landmarks if allowed (landmark-only mode)
        if (!privacyManager.isLandmarkUploadAllowed() || privacyManager.isOfflineModeEnabled()) {
            Timber.d("Landmark upload disabled by privacy settings")
            return
        }

        try {
            val landmarksJson = gson.toJson(landmarks)
            val contextMessage = """
                Current pose landmarks detected (privacy mode - landmarks only):
                $landmarksJson

                Please provide real-time coaching feedback based ONLY on this pose data.
                Note: Image and audio data may be limited based on user privacy preferences.
            """.trimIndent()

            val textInput = LiveApiMessage.RealtimeInput(text = contextMessage)
            webSocketClient.sendRealtimeInput(textInput)

            Timber.d("Sent landmarks-only context to Live API")
        } catch (e: Exception) {
            handleCloudError("Failed to send pose context: ${e.message}")
        }
    }

    fun stopPushToTalkSession() {
        Timber.d("Stopping push-to-talk session")

        stateManager.setRecording(false)

        // Disable barge-in mode before stopping
        audioManager.enableBargeInMode(false)
        audioManager.stopRecording()
        imageManager.stopSnapshots()

        // Keep connection open for a moment to receive final responses
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000) // 2 second grace period
            webSocketClient.disconnect()
        }
    }

    fun updatePoseLandmarks(landmarks: PoseLandmarkResult) {
        currentLandmarks = landmarks

        // If we're in an active session, process for image snapshots
        if (stateManager.isConnected()) {
            // Note: ImageProxy will be provided by the camera integration
            // This is a placeholder for the actual implementation
        }
    }

    fun processImageWithLandmarks(imageProxy: ImageProxy, landmarks: PoseLandmarkResult) {
        currentLandmarks = landmarks
        imageManager.processImageWithLandmarks(imageProxy, landmarks)
    }

    // Enhanced barge-in functionality with intelligent detection
    fun triggerBargeIn() {
        if (!stateManager.isConnected()) {
            Timber.w("Not connected, cannot trigger barge-in")
            return
        }

        Timber.d("Manual barge-in triggered")
        performBargeIn("manual_trigger")
    }

    private suspend fun handleBargeInEvent(timestamp: Long) {
        if (!stateManager.isConnected() || !stateManager.getCurrentState().isSpeaking) {
            return
        }

        Timber.i("Automatic barge-in detected at $timestamp")
        performBargeIn("voice_detected")
    }

    private fun performBargeIn(trigger: String) {
        // TODO: Fix method name - setSpeaking not available
        // stateManager.setSpeaking(false)

        // Enable barge-in mode for more responsive audio processing
        audioManager.enableBargeInMode(true)

        Timber.d("Barge-in executed (trigger: $trigger)")

        // Schedule to disable barge-in mode after a delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(3000) // 3 seconds
            if (!stateManager.getCurrentState().isSpeaking) {
                audioManager.enableBargeInMode(false)
                Timber.d("Barge-in mode disabled after timeout")
            }
        }
    }

    private suspend fun handleAudioQualityUpdate(qualityInfo: AudioStreamManager.AudioQualityInfo) {
        if (qualityInfo.qualityScore < 0.3) {
            _errors.emit("Audio quality is poor (${String.format("%.1f", qualityInfo.qualityScore * 100)}%) - consider adjusting microphone position")
        }
    }

    private suspend fun handleLiveApiResponse(response: LiveApiResponse) {
        when (response) {
            is LiveApiResponse.SetupComplete -> {
                Timber.d("Live API setup complete")
            }

            is LiveApiResponse.ServerContent -> {
                handleServerContent(response)
            }

            is LiveApiResponse.ToolCall -> {
                handleToolCall(response)
            }

            is LiveApiResponse.ToolCallCancellation -> {
                Timber.d("Tool calls cancelled: ${response.ids}")
            }
        }
    }

    private suspend fun handleServerContent(content: LiveApiResponse.ServerContent) {
        // Handle transcriptions
        content.inputTranscription?.let { transcription ->
            _transcriptions.emit("You: ${transcription.transcribedText}")
        }

        content.outputTranscription?.let { transcription ->
            _transcriptions.emit("Coach: ${transcription.transcribedText}")
        }

        // Handle model responses
        content.modelTurn?.let { modelTurn ->
            val textResponses = modelTurn.parts.filterIsInstance<Part.TextPart>()
            if (textResponses.isNotEmpty()) {
                val combinedText = textResponses.joinToString(" ") { it.text }
                _coachingResponses.emit(combinedText)
            }
        }

        // Handle interruptions
        if (content.interrupted) {
            Timber.d("Model generation interrupted (barge-in detected)")
            stateManager.setSpeaking(false)
            // Disable barge-in mode after interruption is processed
            audioManager.enableBargeInMode(false)
        }

        // Handle turn completion
        if (content.turnComplete) {
            Timber.d("Model turn complete")
            stateManager.setSpeaking(false)
            // Re-enable barge-in mode for next potential response
            if (stateManager.getCurrentState().isRecording) {
                audioManager.enableBargeInMode(true)
            }
        } else if (content.modelTurn != null) {
            stateManager.setSpeaking(true)
            // Keep barge-in enabled while model is speaking
            audioManager.enableBargeInMode(true)
        }
    }

    private suspend fun handleToolCall(toolCall: LiveApiResponse.ToolCall) {
        // Handle any tool calls if needed in the future
        Timber.d("Received tool call: ${toolCall.functionCalls.map { it.name }}")

        // For now, we don't have specific tools, but this is where
        // you would handle fitness-related function calls like:
        // - "analyze_pose"
        // - "suggest_correction"
        // - "set_workout_goal"
        // etc.
    }

    fun forceReconnect() {
        Timber.d("Force reconnecting Live API")
        webSocketClient.forceReconnect()
    }

    fun isRecording(): Boolean = stateManager.getCurrentState().isRecording

    fun isSpeaking(): Boolean = stateManager.getCurrentState().isSpeaking

    fun getConnectionState(): ConnectionState = stateManager.getCurrentState().connectionState

    fun destroy() {
        Timber.d("Destroying LiveCoachManager")

        // Stop all active sessions
        stopPushToTalkSession()

        // Cancel all collection jobs
        collectionJobs.forEach { it.cancel() }
        collectionJobs.clear()

        // Log final performance metrics
        val finalMetrics = getPerformanceMetrics()
        Timber.i("Final session metrics: $finalMetrics")

        // Destroy components
        webSocketClient.destroy()
        audioManager.destroy()
        imageManager.destroy()

        // Reset state
        stateManager.reset()

        Timber.i("LiveCoachManager destroyed successfully")
    }

    // Configuration methods
    fun updateSystemInstruction(instruction: String) {
        // This would require reconnecting with new config
        // For now, log the request
        Timber.d("System instruction update requested: $instruction")
    }

    fun setSilenceDetectionEnabled(enabled: Boolean) {
        audioManager.setSilenceDetectionEnabled(enabled)
    }

    // Diagnostic methods
    /**
     * 處理雲端錯誤，確保不影響核心端上體驗
     */
    private suspend fun handleCloudError(error: String) {
        Timber.e("Cloud service error: $error")

        // 檢查是否應該影響核心功能
        if (privacyManager.shouldCloudErrorsAffectCore()) {
            _errors.emit(error)
        } else {
            // 在離線模式或最大隱私模式下，雲端錯誤不影響核心功能
            Timber.i("Cloud error ignored due to privacy settings: $error")

            // 可選：顯示簡化的錯誤訊息
            if (!privacyManager.isOfflineModeEnabled()) {
                _errors.emit("AI 功能暫時不可用，但本地姿勢分析仍正常運作")
            }
        }
    }

    /**
     * 獲取隱私管理器 (供外部使用)
     */
    fun getPrivacyManager(): EnhancedPrivacyManager = privacyManager

    /**
     * 檢查是否處於地標專用模式
     */
    fun isLandmarkOnlyMode(): Boolean {
        return privacyManager.isLandmarkUploadAllowed() &&
               !privacyManager.isImageUploadAllowed() &&
               !privacyManager.isAudioUploadAllowed()
    }

    /**
     * 檢查核心功能是否可用 (不依賴雲端)
     */
    fun isCoreFunctionAvailable(): Boolean {
        // 核心姿勢偵測功能始終可用，不依賴雲端
        return true
    }

    /**
     * 獲取雲端功能狀態
     */
    fun getCloudFeatureStatus(): Map<String, Boolean> {
        return mapOf(
            "liveCoachAvailable" to (!privacyManager.isOfflineModeEnabled() && stateManager.isConnected()),
            "audioUploadEnabled" to privacyManager.isAudioUploadAllowed(),
            "imageUploadEnabled" to privacyManager.isImageUploadAllowed(),
            "landmarkUploadEnabled" to privacyManager.isLandmarkUploadAllowed(),
            "offlineModeActive" to privacyManager.isOfflineModeEnabled(),
            "landmarkOnlyMode" to isLandmarkOnlyMode()
        )
    }

    fun getSessionInfo(): Map<String, Any> {
        val state = stateManager.getCurrentState()
        val audioInfo = audioManager.getAdvancedBufferInfo()
        val snapshotInfo = imageManager.getSnapshotInfo()
        val cloudStatus = getCloudFeatureStatus()
        val privacyLevel = privacyManager.currentPrivacyLevel.value
        val webSocketMetrics = webSocketClient.getSessionMetrics()

        return mapOf(
            "sessionId" to (state.sessionId ?: "none"),
            "connectionState" to state.connectionState.name,
            "isRecording" to state.isRecording,
            "isSpeaking" to state.isSpeaking,
            "retryCount" to state.retryCount,
            "lastError" to (state.lastError ?: "none"),
            "audioInfo" to audioInfo,
            "audioQuality" to audioManager.getCurrentAudioQuality(),
            "bargeInEnabled" to audioManager.isBargeInModeEnabled(),
            "recentVoiceActivity" to audioManager.getRecentVoiceActivity(),
            "snapshotWidth" to snapshotInfo.first,
            "snapshotHeight" to snapshotInfo.second,
            "snapshotInterval" to snapshotInfo.third,
            "privacyLevel" to privacyLevel.name,
            "cloudFeatures" to cloudStatus,
            "coreFunctionAvailable" to isCoreFunctionAvailable(),
            "webSocketMetrics" to webSocketMetrics,
            "connectionHealthy" to webSocketClient.isHealthy()
        )
    }

    // Additional utility methods for enhanced session management
    fun getConnectionHealth(): Map<String, Any> {
        return mapOf(
            "isHealthy" to webSocketClient.isHealthy(),
            "metrics" to webSocketClient.getSessionMetrics(),
            "audioQuality" to audioManager.getCurrentAudioQuality(),
            "lastError" to (stateManager.getCurrentState().lastError ?: "none")
        )
    }

    fun optimizeForBatteryLife(enable: Boolean) {
        if (enable) {
            // Reduce audio processing frequency
            audioManager.setSilenceDetectionEnabled(true)
            // Disable barge-in for battery savings
            audioManager.enableBargeInMode(false)
            Timber.d("Battery optimization enabled")
        } else {
            // Re-enable full functionality
            if (stateManager.getCurrentState().isRecording) {
                audioManager.enableBargeInMode(true)
            }
            Timber.d("Battery optimization disabled")
        }
    }

    fun getPerformanceMetrics(): Map<String, Any?> {
        val sessionInfo = getSessionInfo()
        val connectionHealth = getConnectionHealth()

        return mapOf(
            "sessionDuration" to (webSocketClient.getSessionMetrics()["sessionDurationMs"] ?: 0),
            "messageLatency" to (webSocketClient.getSessionMetrics()["lastMessageAgoMs"] ?: 0),
            "audioQuality" to sessionInfo["audioQuality"],
            "connectionStability" to connectionHealth["isHealthy"],
            "errorCount" to stateManager.getCurrentState().retryCount,
            "bargeInMode" to sessionInfo["bargeInEnabled"]
        )
    }
}