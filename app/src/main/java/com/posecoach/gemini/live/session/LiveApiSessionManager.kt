/*
 * Copyright 2024 Pose Coach Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.posecoach.gemini.live.session

import android.content.Context
import com.posecoach.gemini.live.audio.AudioProcessor
import com.posecoach.gemini.live.client.LiveApiWebSocketClient
import com.posecoach.gemini.live.models.*
import com.posecoach.gemini.live.security.EphemeralTokenManager
import com.posecoach.gemini.live.tools.PoseAnalysisTools
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * Comprehensive session management for Gemini Live API
 * Handles complete lifecycle including audio, pose analysis, and error recovery
 */
class LiveApiSessionManager(
    private val context: Context,
    private val config: LiveApiConfig = LiveApiConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : CoroutineScope {

    companion object {
        private const val SESSION_SETUP_TIMEOUT_MS = 30_000L
        private const val AUDIO_STREAM_TIMEOUT_MS = 5_000L
        private const val MAX_CONTEXT_WINDOW_SIZE = 50_000 // Approximate token count
        private const val CONTEXT_COMPRESSION_THRESHOLD = 0.8f
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    override val coroutineContext: CoroutineContext = scope.coroutineContext

    // Core components
    private val tokenManager = EphemeralTokenManager(context, scope)
    private val audioProcessor = AudioProcessor(scope)
    private val poseTools = PoseAnalysisTools()

    private var webSocketClient: LiveApiWebSocketClient? = null
    private val isSessionActive = AtomicBoolean(false)
    private val sessionId = AtomicReference<String?>(null)
    private val contextWindowSize = AtomicLong(0)

    // Session flows
    private val _sessionState = MutableStateFlow(SessionState.DISCONNECTED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _audioResponse = MutableSharedFlow<ByteArray>()
    val audioResponse: SharedFlow<ByteArray> = _audioResponse.asSharedFlow()

    private val _textResponse = MutableSharedFlow<String>()
    val textResponse: SharedFlow<String> = _textResponse.asSharedFlow()

    private val _poseAnalysisResults = MutableSharedFlow<PoseAnalysisResult>()
    val poseAnalysisResults: SharedFlow<PoseAnalysisResult> = _poseAnalysisResults.asSharedFlow()

    private val _sessionErrors = MutableSharedFlow<LiveApiError>()
    val sessionErrors: SharedFlow<LiveApiError> = _sessionErrors.asSharedFlow()

    // Jobs
    private var audioStreamingJob: Job? = null
    private var responseProcessingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var contextCompressionJob: Job? = null

    // Session metadata
    private var sessionStartTime = 0L
    private var totalAudioSent = 0L
    private var totalResponsesReceived = 0L

    init {
        setupSessionMonitoring()
    }

    /**
     * Start a complete Live API session with audio and pose coaching
     */
    suspend fun startSession(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (isSessionActive.get()) {
                return@withContext Result.failure(
                    LiveApiError.SessionError("Session already active")
                )
            }

            _sessionState.value = SessionState.CONNECTING
            sessionStartTime = System.currentTimeMillis()
            val newSessionId = generateSessionId()
            sessionId.set(newSessionId)

            // Initialize WebSocket client with pose tools
            val clientConfig = config.copy(
                tools = listOf(poseTools.getFunctionDeclarations()),
                systemInstruction = createSystemInstruction()
            )

            webSocketClient = LiveApiWebSocketClient(tokenManager, clientConfig, scope)

            // Connect and setup
            webSocketClient!!.connect().getOrThrow()
            webSocketClient!!.setupSession().getOrThrow()

            // Start audio processing
            audioProcessor.startRecording().getOrThrow()
            audioProcessor.startPlayback().getOrThrow()

            // Start session components
            startAudioStreaming()
            startResponseProcessing()
            startHeartbeat()
            startContextWindowMonitoring()

            isSessionActive.set(true)
            _sessionState.value = SessionState.ACTIVE

            Timber.i("Live API session started: $newSessionId")
            Result.success(newSessionId)

        } catch (e: Exception) {
            Timber.e(e, "Failed to start session")
            cleanup()
            _sessionState.value = SessionState.ERROR
            Result.failure(e as? LiveApiError ?: LiveApiError.SessionError("Session start failed: ${e.message}"))
        }
    }

    /**
     * Stop the current session gracefully
     */
    suspend fun stopSession() = withContext(Dispatchers.IO) {
        try {
            if (!isSessionActive.get()) {
                return@withContext
            }

            _sessionState.value = SessionState.DISCONNECTING

            // Stop streaming
            audioStreamingJob?.cancel()
            responseProcessingJob?.cancel()
            heartbeatJob?.cancel()
            contextCompressionJob?.cancel()

            // Stop audio
            audioProcessor.stopRecording()
            audioProcessor.stopPlayback()

            // Disconnect WebSocket
            webSocketClient?.disconnect()

            isSessionActive.set(false)
            _sessionState.value = SessionState.DISCONNECTED

            val stats = getSessionStats()
            Timber.i("Session stopped. Duration: ${stats.duration}ms, Audio sent: ${stats.audioDataSent}bytes")

        } catch (e: Exception) {
            Timber.e(e, "Error stopping session")
        } finally {
            cleanup()
        }
    }

    /**
     * Send text message during active session
     */
    suspend fun sendMessage(text: String): Result<Unit> {
        val client = webSocketClient ?: return Result.failure(
            LiveApiError.SessionError("No active session")
        )

        return client.sendText(text).also { result ->
            if (result.isSuccess) {
                updateContextWindow(text.length * 4) // Estimate tokens
            }
        }
    }

    /**
     * Process pose landmarks for coaching analysis
     */
    suspend fun processPoseLandmarks(landmarks: List<PoseLandmark>) {
        if (!isSessionActive.get()) return

        try {
            val analysisResult = poseTools.analyzePose(landmarks)
            _poseAnalysisResults.emit(analysisResult)

            // Send pose analysis to model if needed
            if (analysisResult.needsCorrection) {
                val poseData = poseTools.createPoseAnalysisData(landmarks)
                webSocketClient?.sendToolResponse(
                    listOf(
                        FunctionResponse(
                            name = "analyze_pose",
                            response = mapOf(
                                "pose_data" to poseData,
                                "analysis" to analysisResult
                            )
                        )
                    )
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing pose landmarks")
            _sessionErrors.emit(LiveApiError.SessionError("Pose processing error: ${e.message}"))
        }
    }

    /**
     * Resume session after connection loss
     */
    suspend fun resumeSession(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isSessionActive.get()) {
                return@withContext Result.success(Unit)
            }

            val currentSessionId = sessionId.get() ?: return@withContext Result.failure(
                LiveApiError.SessionError("No session to resume")
            )

            // Check if token allows new session
            if (!tokenManager.canStartNewSession()) {
                return@withContext Result.failure(
                    LiveApiError.AuthenticationError("Cannot start new session with current token")
                )
            }

            Timber.i("Resuming session: $currentSessionId")

            // Restart with same session ID
            return@withContext startSession()

        } catch (e: Exception) {
            Timber.e(e, "Failed to resume session")
            Result.failure(LiveApiError.SessionError("Resume failed: ${e.message}"))
        }
    }

    private fun startAudioStreaming() {
        audioStreamingJob = scope.launch {
            audioProcessor.getProcessedAudioFlow()
                .sample(50) // Sample every 50ms
                .collect { audioData ->
                    try {
                        webSocketClient?.sendAudioChunk(audioData)?.getOrThrow()
                        totalAudioSent += audioData.size
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send audio chunk")
                        _sessionErrors.emit(
                            LiveApiError.AudioError("Audio streaming error", e)
                        )
                    }
                }
        }
    }

    private fun startResponseProcessing() {
        responseProcessingJob = scope.launch {
            webSocketClient?.serverContent?.collect { response ->
                try {
                    processServerResponse(response)
                    totalResponsesReceived++
                } catch (e: Exception) {
                    Timber.e(e, "Error processing server response")
                    _sessionErrors.emit(
                        LiveApiError.ProtocolError("Response processing error: ${e.message}")
                    )
                }
            }
        }

        // Also handle GoAway messages
        scope.launch {
            webSocketClient?.goAway?.collect { goAwayMessage ->
                Timber.w("Received GoAway: ${goAwayMessage.reason}")
                handleGoAway(goAwayMessage)
            }
        }
    }

    private suspend fun processServerResponse(response: BidiGenerateContentServerContent) {
        response.serverContent.modelTurn?.parts?.forEach { part ->
            when (part) {
                is TextPart -> {
                    _textResponse.emit(part.text)
                    updateContextWindow(part.text.length * 4) // Estimate tokens
                }

                is InlineDataPart -> {
                    if (part.inlineData.mimeType.startsWith("audio/")) {
                        val audioData = part.inlineData.data.fromBase64()
                        _audioResponse.emit(audioData)
                        audioProcessor.playAudioData(audioData)
                    }
                }

                is FunctionCallPart -> {
                    handleFunctionCall(part.functionCall)
                }

                else -> {
                    Timber.v("Unhandled part type: ${part::class.simpleName}")
                }
            }
        }
    }

    private suspend fun handleFunctionCall(functionCall: FunctionCall) {
        when (functionCall.name) {
            "analyze_pose" -> {
                val response = poseTools.handleFunctionCall(functionCall)
                webSocketClient?.sendToolResponse(listOf(response))
            }

            "provide_pose_feedback" -> {
                val response = poseTools.handleFeedbackCall(functionCall)
                webSocketClient?.sendToolResponse(listOf(response))
            }

            else -> {
                Timber.w("Unknown function call: ${functionCall.name}")
            }
        }
    }

    private suspend fun handleGoAway(goAwayMessage: BidiGenerateContentGoAway) {
        when {
            goAwayMessage.reason.contains("session_limit", ignoreCase = true) ||
            goAwayMessage.reason.contains("timeout", ignoreCase = true) -> {
                // Attempt session resumption
                delay(1000) // Brief delay before resuming
                resumeSession()
            }

            else -> {
                // Other reasons - stop session
                stopSession()
                _sessionErrors.emit(
                    LiveApiError.SessionError("Session terminated: ${goAwayMessage.reason}")
                )
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isSessionActive.get()) {
                delay(HEARTBEAT_INTERVAL_MS)

                try {
                    // Send periodic heartbeat message
                    webSocketClient?.sendText("heartbeat", turnComplete = true)
                } catch (e: Exception) {
                    Timber.w(e, "Heartbeat failed")
                }
            }
        }
    }

    private fun startContextWindowMonitoring() {
        contextCompressionJob = scope.launch {
            while (isSessionActive.get()) {
                delay(60_000) // Check every minute

                if (shouldCompressContext()) {
                    compressContextWindow()
                }
            }
        }
    }

    private fun shouldCompressContext(): Boolean {
        return contextWindowSize.get() > MAX_CONTEXT_WINDOW_SIZE * CONTEXT_COMPRESSION_THRESHOLD
    }

    private suspend fun compressContextWindow() {
        try {
            // Send context compression instruction
            webSocketClient?.sendText(
                "Please summarize our conversation so far, focusing on the key pose coaching points and current exercise progress.",
                turnComplete = true
            )

            // Reset context window size estimation
            contextWindowSize.set(0)

            Timber.d("Context window compression requested")

        } catch (e: Exception) {
            Timber.e(e, "Failed to compress context window")
        }
    }

    private fun updateContextWindow(estimatedTokens: Long) {
        contextWindowSize.addAndGet(estimatedTokens)
    }

    private fun setupSessionMonitoring() {
        // Monitor token state
        scope.launch {
            tokenManager.tokenState.collect { state ->
                when (state) {
                    TokenState.EXPIRED, TokenState.ERROR -> {
                        if (isSessionActive.get()) {
                            _sessionErrors.emit(
                                LiveApiError.AuthenticationError("Token issue: $state")
                            )
                        }
                    }
                    else -> { /* Continue */ }
                }
            }
        }

        // Monitor audio quality
        scope.launch {
            audioProcessor.audioQuality.collect { quality ->
                if (quality == com.posecoach.gemini.live.audio.AudioQuality.POOR) {
                    _sessionErrors.emit(
                        LiveApiError.AudioError("Poor audio quality detected")
                    )
                }
            }
        }
    }

    private fun createSystemInstruction(): String {
        return """
            You are an expert pose coaching assistant integrated with real-time audio and pose analysis.

            Your capabilities:
            - Provide real-time audio feedback for pose corrections
            - Analyze pose landmarks and provide specific guidance
            - Offer encouragement and motivation
            - Adapt coaching style based on user progress

            Guidelines:
            - Keep responses concise and actionable
            - Use encouraging and supportive tone
            - Focus on safety and proper form
            - Provide specific corrections when pose analysis indicates issues
            - Use audio responses for immediate feedback during exercises

            You have access to pose analysis tools that provide real-time pose data.
            Use this information to give targeted coaching advice.
        """.trimIndent()
    }

    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    private fun cleanup() {
        audioStreamingJob?.cancel()
        responseProcessingJob?.cancel()
        heartbeatJob?.cancel()
        contextCompressionJob?.cancel()
        webSocketClient?.cleanup()
        webSocketClient = null
        audioProcessor.cleanup()
        contextWindowSize.set(0)
        sessionId.set(null)
    }

    /**
     * Get current session statistics
     */
    fun getSessionStats(): SessionStats {
        return SessionStats(
            sessionId = sessionId.get(),
            duration = if (sessionStartTime > 0) System.currentTimeMillis() - sessionStartTime else 0,
            audioDataSent = totalAudioSent,
            responsesReceived = totalResponsesReceived,
            state = _sessionState.value,
            contextWindowSize = contextWindowSize.get(),
            isAudioActive = audioProcessor.getAudioStats().isRecording,
            voiceActivity = audioProcessor.getAudioStats().voiceActivity
        )
    }

    /**
     * Check if session needs renewal
     */
    fun shouldRenewSession(): Boolean {
        val client = webSocketClient ?: return false
        return client.isSessionNearTimeout() ||
               contextWindowSize.get() > MAX_CONTEXT_WINDOW_SIZE ||
               !tokenManager.canStartNewSession()
    }

    fun cleanup() {
        cleanup()
        scope.cancel()
        tokenManager.cleanup()
    }
}

data class SessionStats(
    val sessionId: String?,
    val duration: Long,
    val audioDataSent: Long,
    val responsesReceived: Long,
    val state: SessionState,
    val contextWindowSize: Long,
    val isAudioActive: Boolean,
    val voiceActivity: Boolean
)

data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

data class PoseAnalysisResult(
    val confidence: Float,
    val needsCorrection: Boolean,
    val feedback: String,
    val keyPoints: Map<String, String>
)