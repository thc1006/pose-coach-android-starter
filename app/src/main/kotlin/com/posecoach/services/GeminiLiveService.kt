package com.posecoach.services

import android.app.*
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.posecoach.R
import com.posecoach.auth.TokenManager
import com.posecoach.network.GeminiLiveClient
import com.posecoach.ui.activities.ConnectionStatus
import com.posecoach.ui.components.*
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * Foreground service for Gemini Live API integration
 * Features:
 * - 15-minute session management with automatic resumption
 * - Real-time audio streaming with VAD
 * - WebSocket connection management
 * - Tool execution for pose analysis
 * - Ephemeral token authentication
 */
class GeminiLiveService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "gemini_live_channel"
        private const val SESSION_DURATION_MS = 15 * 60 * 1000L // 15 minutes
        private const val RESUMPTION_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
    }

    // Binder for activity communication
    private val binder = LocalBinder()

    // Core components
    private var geminiClient: GeminiLiveClient? = null
    private var tokenManager: TokenManager? = null
    private var audioRecord: AudioRecord? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Audio configuration
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Session management
    private var sessionStartTime = 0L
    private var isSessionActive = false
    private var sessionResumeJob: Job? = null

    // Voice Activity Detection
    private var vadThreshold = 0.02f
    private var isVoiceInputActive = false

    // Callbacks
    private var connectionStatusCallback: ((ConnectionStatus) -> Unit)? = null
    private var transcriptionCallback: ((String) -> Unit)? = null
    private var voiceActivityCallback: ((VoiceActivity) -> Unit)? = null
    private var toolExecutionCallback: ((ToolExecution) -> Unit)? = null
    private var sessionStateCallback: ((SessionState) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): GeminiLiveService = this@GeminiLiveService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gemini Live Coaching",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live AI coaching session"
            setSound(null, null)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Coaching Active")
            .setContentText("AI coach is listening and analyzing your pose")
            .setSmallIcon(R.drawable.ic_coaching_active)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun initializeComponents() {
        tokenManager = TokenManager(this)
        geminiClient = GeminiLiveClient()

        // Setup client callbacks
        geminiClient?.apply {
            setConnectionStatusListener { status ->
                connectionStatusCallback?.invoke(status)
            }

            setMessageListener { message ->
                handleGeminiMessage(message)
            }

            setToolExecutionListener { execution ->
                toolExecutionCallback?.invoke(execution)
            }

            setErrorListener { error ->
                errorCallback?.invoke(error)
            }
        }
    }

    // Public API methods
    fun startSession() {
        serviceScope.launch {
            try {
                connectionStatusCallback?.invoke(ConnectionStatus.CONNECTING)

                // Get ephemeral token
                val token = tokenManager?.getEphemeralToken()
                if (token == null) {
                    errorCallback?.invoke("Failed to obtain authentication token")
                    return@launch
                }

                // Connect to Gemini Live API
                geminiClient?.connect(token)

                // Initialize audio recording
                initializeAudioRecording()

                // Start session tracking
                sessionStartTime = System.currentTimeMillis()
                isSessionActive = true
                sessionStateCallback?.invoke(SessionState.ACTIVE)

                // Schedule session resumption
                scheduleSessionResumption()

                connectionStatusCallback?.invoke(ConnectionStatus.CONNECTED)

            } catch (e: Exception) {
                errorCallback?.invoke("Failed to start session: ${e.message}")
                connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
            }
        }
    }

    fun stopSession() {
        isSessionActive = false
        sessionResumeJob?.cancel()

        stopAudioRecording()
        geminiClient?.disconnect()

        sessionStateCallback?.invoke(SessionState.INACTIVE)
        connectionStatusCallback?.invoke(ConnectionStatus.DISCONNECTED)
    }

    fun startVoiceInput() {
        if (isSessionActive && !isVoiceInputActive) {
            isVoiceInputActive = true
            startAudioStreaming()
        }
    }

    fun stopVoiceInput() {
        isVoiceInputActive = false
        stopAudioStreaming()
    }

    fun pauseVoiceInput() {
        isVoiceInputActive = false
    }

    fun resumeVoiceInput() {
        if (isSessionActive) {
            isVoiceInputActive = true
        }
    }

    fun reconnect() {
        serviceScope.launch {
            connectionStatusCallback?.invoke(ConnectionStatus.CONNECTING)
            try {
                geminiClient?.reconnect()
                connectionStatusCallback?.invoke(ConnectionStatus.CONNECTED)
            } catch (e: Exception) {
                errorCallback?.invoke("Reconnection failed: ${e.message}")
                connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
            }
        }
    }

    fun restartSession() {
        serviceScope.launch {
            stopSession()
            delay(1000) // Brief pause
            startSession()
        }
    }

    fun analyzePoseData(poses: List<PoseData>) {
        serviceScope.launch {
            try {
                val execution = ToolExecution(
                    toolName = "pose_analysis",
                    status = ToolExecutionStatus.STARTING
                )
                toolExecutionCallback?.invoke(execution)

                // Send pose data to Gemini for analysis
                val result = geminiClient?.executePoseAnalysisTool(poses)

                val completedExecution = execution.copy(
                    status = ToolExecutionStatus.COMPLETED,
                    endTime = System.currentTimeMillis(),
                    result = result
                )
                toolExecutionCallback?.invoke(completedExecution)

            } catch (e: Exception) {
                val failedExecution = ToolExecution(
                    toolName = "pose_analysis",
                    status = ToolExecutionStatus.FAILED,
                    endTime = System.currentTimeMillis(),
                    error = e.message
                )
                toolExecutionCallback?.invoke(failedExecution)
            }
        }
    }

    // Audio processing
    private fun initializeAudioRecording() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }

        } catch (e: Exception) {
            errorCallback?.invoke("Failed to initialize audio recording: ${e.message}")
        }
    }

    private fun startAudioStreaming() {
        serviceScope.launch {
            try {
                audioRecord?.startRecording()

                val buffer = ShortArray(bufferSize)

                while (isVoiceInputActive && isSessionActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (bytesRead > 0) {
                        // Voice Activity Detection
                        val amplitude = calculateAmplitude(buffer)
                        val isVoiceDetected = amplitude > vadThreshold

                        voiceActivityCallback?.invoke(
                            VoiceActivity(
                                isActive = isVoiceDetected,
                                amplitude = amplitude,
                                confidence = if (isVoiceDetected) 0.8f else 0.2f
                            )
                        )

                        // Stream audio to Gemini if voice is detected
                        if (isVoiceDetected) {
                            geminiClient?.sendAudioData(buffer)
                        }
                    }

                    delay(10) // Small delay to prevent overwhelming the system
                }
            } catch (e: Exception) {
                errorCallback?.invoke("Audio streaming error: ${e.message}")
            }
        }
    }

    private fun stopAudioStreaming() {
        audioRecord?.stop()
    }

    private fun stopAudioRecording() {
        audioRecord?.release()
        audioRecord = null
    }

    private fun calculateAmplitude(buffer: ShortArray): Float {
        var sum = 0.0
        for (sample in buffer) {
            sum += abs(sample.toDouble())
        }
        return (sum / buffer.size / Short.MAX_VALUE).toFloat()
    }

    // Session management
    private fun scheduleSessionResumption() {
        sessionResumeJob = serviceScope.launch {
            delay(RESUMPTION_INTERVAL_MS)

            while (isSessionActive) {
                // Check if we need to resume the session
                val elapsedTime = System.currentTimeMillis() - sessionStartTime

                if (elapsedTime >= SESSION_DURATION_MS) {
                    // Session expired, restart
                    sessionStateCallback?.invoke(SessionState.EXPIRED)
                    restartSession()
                    return@launch
                } else if (elapsedTime >= RESUMPTION_INTERVAL_MS) {
                    // Resume session
                    resumeSession()
                    sessionStartTime = System.currentTimeMillis() // Reset timer
                }

                delay(30000) // Check every 30 seconds
            }
        }
    }

    private suspend fun resumeSession() {
        connectionStatusCallback?.invoke(ConnectionStatus.RESUMING)
        sessionStateCallback?.invoke(SessionState.RESUMING)

        try {
            // Refresh token and reconnect
            val token = tokenManager?.getEphemeralToken()
            if (token != null) {
                geminiClient?.reconnectWithToken(token)
                connectionStatusCallback?.invoke(ConnectionStatus.CONNECTED)
                sessionStateCallback?.invoke(SessionState.ACTIVE)
            } else {
                throw Exception("Failed to refresh token")
            }
        } catch (e: Exception) {
            errorCallback?.invoke("Session resumption failed: ${e.message}")
            connectionStatusCallback?.invoke(ConnectionStatus.ERROR)
        }
    }

    // Message handling
    private fun handleGeminiMessage(message: GeminiMessage) {
        when (message.type) {
            GeminiMessageType.TRANSCRIPTION -> {
                transcriptionCallback?.invoke(message.content)
            }
            GeminiMessageType.COACHING_SUGGESTION -> {
                // Parse coaching suggestions from Gemini response
                // This would be handled by the UI components
            }
            GeminiMessageType.TOOL_RESULT -> {
                // Handle tool execution results
                val execution = message.toolExecution
                if (execution != null) {
                    toolExecutionCallback?.invoke(execution)
                }
            }
            GeminiMessageType.ERROR -> {
                errorCallback?.invoke(message.content)
            }
        }
    }

    // Callback setters
    fun setConnectionStatusCallback(callback: (ConnectionStatus) -> Unit) {
        connectionStatusCallback = callback
    }

    fun setTranscriptionCallback(callback: (String) -> Unit) {
        transcriptionCallback = callback
    }

    fun setVoiceActivityCallback(callback: (VoiceActivity) -> Unit) {
        voiceActivityCallback = callback
    }

    fun setToolExecutionCallback(callback: (ToolExecution) -> Unit) {
        toolExecutionCallback = callback
    }

    fun setSessionStateCallback(callback: (SessionState) -> Unit) {
        sessionStateCallback = callback
    }

    fun setErrorCallback(callback: (String) -> Unit) {
        errorCallback = callback
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSession()
        serviceScope.cancel()
    }
}

// Supporting data classes
data class GeminiMessage(
    val type: GeminiMessageType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolExecution: ToolExecution? = null
)

enum class GeminiMessageType {
    TRANSCRIPTION,
    COACHING_SUGGESTION,
    TOOL_RESULT,
    ERROR
}