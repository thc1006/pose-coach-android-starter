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

package com.posecoach.gemini.live

import android.content.Context
import com.posecoach.gemini.live.audio.AdaptiveAudioManager
import com.posecoach.gemini.live.audio.AudioProcessor
import com.posecoach.gemini.live.client.ErrorRecoveryManager
import com.posecoach.gemini.live.models.*
import com.posecoach.gemini.live.session.LiveApiSessionManager
import com.posecoach.gemini.live.session.PoseAnalysisResult
import com.posecoach.gemini.live.session.PoseLandmark
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * Main entry point for Gemini Live API integration
 * Provides a unified interface for pose coaching with real-time audio and AI assistance
 */
class LiveApiManager(
    private val context: Context,
    private val config: LiveApiConfig = LiveApiConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : CoroutineScope {

    companion object {
        private const val POSE_UPDATE_THROTTLE_MS = 100L // 10 FPS max for pose updates
        private const val STATS_UPDATE_INTERVAL_MS = 5000L // Update stats every 5 seconds
    }

    override val coroutineContext: CoroutineContext = scope.coroutineContext

    // Core managers
    private val sessionManager = LiveApiSessionManager(context, config, scope)
    private val audioProcessor = AudioProcessor(scope)
    private val adaptiveAudioManager = AdaptiveAudioManager(context, scope)
    private val errorRecoveryManager = ErrorRecoveryManager(scope)

    // Public flows
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _systemStatus = MutableStateFlow(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    // Delegate flows from session manager
    val sessionState: StateFlow<SessionState> = sessionManager.sessionState
    val audioResponse: SharedFlow<ByteArray> = sessionManager.audioResponse
    val textResponse: SharedFlow<String> = sessionManager.textResponse
    val poseAnalysisResults: SharedFlow<PoseAnalysisResult> = sessionManager.poseAnalysisResults
    val sessionErrors: SharedFlow<LiveApiError> = sessionManager.sessionErrors

    // Audio flows
    val audioQuality = audioProcessor.audioQuality
    val voiceActivity = audioProcessor.voiceActivity
    val adaptiveAudioLevel = adaptiveAudioManager.qualityLevel
    val audioAdaptationEvents = adaptiveAudioManager.adaptationEvents

    // Error recovery flows
    val recoveryState = errorRecoveryManager.recoveryState
    val recoveryActions = errorRecoveryManager.recoveryActions

    // Internal jobs
    private var statusUpdateJob: Job? = null
    private var errorHandlingJob: Job? = null
    private var audioIntegrationJob: Job? = null

    init {
        setupErrorHandling()
        setupAudioIntegration()
        startStatusUpdates()
    }

    /**
     * Start the complete Live API pose coaching session
     */
    suspend fun startPoseCoachingSession(): Result<String> {
        return try {
            if (_isActive.value) {
                return Result.failure(LiveApiError.SessionError("Session already active"))
            }

            Timber.i("Starting Gemini Live API pose coaching session")

            // Start session
            val sessionResult = sessionManager.startSession()
            if (sessionResult.isFailure) {
                return sessionResult
            }

            _isActive.value = true
            updateSystemStatus()

            Timber.i("Pose coaching session started successfully: ${sessionResult.getOrNull()}")
            sessionResult

        } catch (e: Exception) {
            Timber.e(e, "Failed to start pose coaching session")
            Result.failure(LiveApiError.SessionError("Failed to start session: ${e.message}"))
        }
    }

    /**
     * Stop the pose coaching session
     */
    suspend fun stopPoseCoachingSession() {
        try {
            if (!_isActive.value) return

            Timber.i("Stopping pose coaching session")

            sessionManager.stopSession()
            _isActive.value = false
            updateSystemStatus()

            Timber.i("Pose coaching session stopped")

        } catch (e: Exception) {
            Timber.e(e, "Error stopping pose coaching session")
        }
    }

    /**
     * Send text message to the AI coach
     */
    suspend fun sendMessageToCoach(message: String): Result<Unit> {
        return if (_isActive.value) {
            sessionManager.sendMessage(message)
        } else {
            Result.failure(LiveApiError.SessionError("No active session"))
        }
    }

    /**
     * Process pose landmarks for real-time coaching
     */
    suspend fun processPoseLandmarks(landmarks: List<PoseLandmark>) {
        if (!_isActive.value) return

        try {
            // Throttle pose updates to avoid overwhelming the system
            sessionManager.processPoseLandmarks(landmarks)
        } catch (e: Exception) {
            Timber.e(e, "Error processing pose landmarks")
        }
    }

    /**
     * Configure audio settings and quality
     */
    fun configureAudio(
        enableVAD: Boolean = true,
        vadThreshold: Float = 0.02f,
        enableAdaptiveQuality: Boolean = true,
        enableNoiseSuppression: Boolean = true,
        enableGainControl: Boolean = true,
        enableEchoCancellation: Boolean = true
    ) {
        try {
            // Configure VAD
            audioProcessor.configureVAD(
                enabled = enableVAD,
                threshold = vadThreshold
            )

            // Configure adaptive audio
            adaptiveAudioManager.configureEnhancements(
                enableNoiseSuppression = enableNoiseSuppression,
                enableGainControl = enableGainControl,
                enableEchoCancellation = enableEchoCancellation,
                enableAdaptation = enableAdaptiveQuality
            )

            Timber.d("Audio configuration updated")

        } catch (e: Exception) {
            Timber.e(e, "Error configuring audio")
        }
    }

    /**
     * Get comprehensive system statistics
     */
    fun getSystemStatistics(): SystemStatistics {
        val sessionStats = sessionManager.getSessionStats()
        val audioStats = audioProcessor.getAudioStats()
        val audioMetrics = adaptiveAudioManager.getCurrentAudioMetrics()
        val errorStats = errorRecoveryManager.getErrorStatistics()

        return SystemStatistics(
            isActive = _isActive.value,
            sessionId = sessionStats.sessionId,
            sessionDuration = sessionStats.duration,
            sessionState = sessionStats.state,
            audioDataSent = sessionStats.audioDataSent,
            responsesReceived = sessionStats.responsesReceived,
            contextWindowSize = sessionStats.contextWindowSize,
            audioQuality = audioStats.audioQuality,
            voiceActivity = audioStats.voiceActivity,
            signalLevel = audioMetrics.signalLevel,
            noiseLevel = audioMetrics.noiseLevel,
            snrDb = audioMetrics.snrDb,
            adaptiveQualityLevel = audioMetrics.qualityLevel,
            errorCounts = mapOf(
                "connection" to errorStats.connectionErrors,
                "audio" to errorStats.audioErrors,
                "token" to errorStats.tokenErrors,
                "session" to errorStats.sessionErrors,
                "protocol" to errorStats.protocolErrors,
                "rate_limit" to errorStats.rateLimitErrors
            ),
            recoveryState = errorStats.recoveryState,
            degradedFeatures = errorStats.degradedFeatures
        )
    }

    /**
     * Handle emergency situations (poor connection, critical errors)
     */
    suspend fun handleEmergency(emergencyType: EmergencyType) {
        when (emergencyType) {
            EmergencyType.POOR_CONNECTION -> {
                errorRecoveryManager.enableDegradedMode("connection")
                adaptiveAudioManager.handlePoorAudioConditions()
            }

            EmergencyType.AUDIO_FAILURE -> {
                errorRecoveryManager.enableDegradedMode("audio")
                // Try to restart audio processing
                audioProcessor.stopRecording()
                delay(1000)
                audioProcessor.startRecording()
            }

            EmergencyType.SESSION_TIMEOUT -> {
                sessionManager.resumeSession()
            }

            EmergencyType.CRITICAL_ERROR -> {
                stopPoseCoachingSession()
                delay(2000)
                startPoseCoachingSession()
            }
        }
    }

    /**
     * Reset all error states and recover from degraded mode
     */
    suspend fun recoverFromErrors() {
        try {
            errorRecoveryManager.resetErrorState()
            adaptiveAudioManager.configureEnhancements(enableAdaptation = true)

            Timber.i("System recovered from errors")

        } catch (e: Exception) {
            Timber.e(e, "Error during system recovery")
        }
    }

    private fun setupErrorHandling() {
        errorHandlingJob = scope.launch {
            // Handle session errors
            sessionErrors.collect { error ->
                val recoveryResult = when (error) {
                    is LiveApiError.ConnectionError -> errorRecoveryManager.handleConnectionError(error)
                    is LiveApiError.AudioError -> errorRecoveryManager.handleAudioError(error)
                    is LiveApiError.AuthenticationError -> errorRecoveryManager.handleTokenError(error)
                    is LiveApiError.SessionError -> errorRecoveryManager.handleSessionError(error)
                    is LiveApiError.ProtocolError -> errorRecoveryManager.handleProtocolError(error)
                    is LiveApiError.RateLimitError -> errorRecoveryManager.handleRateLimitError(error)
                }

                Timber.d("Error recovery result: $recoveryResult")
            }
        }

        // Handle recovery actions
        scope.launch {
            recoveryActions.collect { action ->
                when (action) {
                    is com.posecoach.gemini.live.client.RecoveryAction.RestartSession -> {
                        stopPoseCoachingSession()
                        delay(2000)
                        startPoseCoachingSession()
                    }

                    is com.posecoach.gemini.live.client.RecoveryAction.WaitAndRetry -> {
                        delay(action.waitTimeMs)
                        // Retry operation
                    }

                    else -> { /* Handle other actions */ }
                }
            }
        }
    }

    private fun setupAudioIntegration() {
        audioIntegrationJob = scope.launch {
            // Integrate adaptive audio with audio processor
            audioProcessor.getProcessedAudioFlow()
                .map { audioData ->
                    adaptiveAudioManager.processAudioFrame(audioData)
                }
                .collect { processedAudio ->
                    // Audio is already sent to session manager through AudioProcessor
                }
        }

        // Monitor audio quality and adapt
        scope.launch {
            audioQuality.collect { quality ->
                adaptiveAudioManager.adaptQualitySettings(quality)
            }
        }
    }

    private fun startStatusUpdates() {
        statusUpdateJob = scope.launch {
            while (true) {
                delay(STATS_UPDATE_INTERVAL_MS)
                updateSystemStatus()
            }
        }
    }

    private fun updateSystemStatus() {
        val stats = getSystemStatistics()
        _systemStatus.value = SystemStatus(
            isHealthy = stats.isActive && stats.errorCounts.values.sum() < 10,
            lastUpdate = System.currentTimeMillis(),
            activeFeatures = buildList {
                if (stats.isActive) add("session")
                if (stats.voiceActivity) add("voice_activity")
                if (stats.adaptiveQualityLevel != null) add("adaptive_audio")
                if (stats.recoveryState == com.posecoach.gemini.live.client.RecoveryState.NORMAL) add("error_recovery")
            },
            warnings = buildList {
                if (stats.audioQuality == com.posecoach.gemini.live.audio.AudioQuality.POOR) add("Poor audio quality")
                if (stats.snrDb < 10f) add("Low signal-to-noise ratio")
                if (stats.errorCounts.values.sum() > 5) add("Multiple errors detected")
                if (stats.degradedFeatures.isNotEmpty()) add("Degraded features: ${stats.degradedFeatures.joinToString()}")
            }
        )
    }

    fun cleanup() {
        runBlocking {
            stopPoseCoachingSession()
        }

        statusUpdateJob?.cancel()
        errorHandlingJob?.cancel()
        audioIntegrationJob?.cancel()

        audioProcessor.cleanup()
        adaptiveAudioManager.cleanup()
        errorRecoveryManager.cleanup()
        sessionManager.cleanup()

        scope.cancel()
    }
}

enum class EmergencyType {
    POOR_CONNECTION,
    AUDIO_FAILURE,
    SESSION_TIMEOUT,
    CRITICAL_ERROR
}

data class SystemStatus(
    val isHealthy: Boolean = false,
    val lastUpdate: Long = 0L,
    val activeFeatures: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class SystemStatistics(
    val isActive: Boolean,
    val sessionId: String?,
    val sessionDuration: Long,
    val sessionState: SessionState,
    val audioDataSent: Long,
    val responsesReceived: Long,
    val contextWindowSize: Long,
    val audioQuality: com.posecoach.gemini.live.audio.AudioQuality,
    val voiceActivity: Boolean,
    val signalLevel: Float,
    val noiseLevel: Float,
    val snrDb: Float,
    val adaptiveQualityLevel: com.posecoach.gemini.live.audio.AudioQualityLevel?,
    val errorCounts: Map<String, Int>,
    val recoveryState: com.posecoach.gemini.live.client.RecoveryState,
    val degradedFeatures: List<String>
)