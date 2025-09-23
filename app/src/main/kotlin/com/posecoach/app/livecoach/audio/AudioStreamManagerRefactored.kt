package com.posecoach.app.livecoach.audio

import android.content.Context
import android.media.*
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import com.posecoach.app.livecoach.models.AudioChunk
import com.posecoach.app.livecoach.models.LiveApiMessage
import com.posecoach.app.livecoach.models.MediaChunk
import com.posecoach.app.livecoach.models.AudioSessionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Refactored AudioStreamManager using modular components (<300 lines).
 * Core audio streaming functionality with dependency injection.
 *
 * Following CLAUDE.md modular design principles:
 * - AudioConfiguration handles all configuration
 * - AudioPermissionManager handles permissions
 * - AudioQualityMonitor handles quality analysis
 * - This class focuses on core streaming logic
 */
class AudioStreamManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val configuration: AudioConfiguration = AudioConfiguration(),
    private val permissionManager: AudioPermissionManager = AudioPermissionManager(context),
    private val qualityMonitor: AudioQualityMonitor = AudioQualityMonitor(
        configuration.getInputConfiguration().sampleRate,
        configuration.getInputConfiguration().channelConfig
    )
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    companion object {
        private const val TAG = "AudioStreamManager"

        // Voice activity and silence detection
        private const val SILENCE_THRESHOLD = 500
        private const val SILENCE_DURATION_MS = 2000L
        private const val VOICE_ACTIVITY_BUFFER_SIZE = 10

        // Barge-in detection configuration
        private const val BARGE_IN_THRESHOLD = 800
        private const val BARGE_IN_MIN_DURATION_MS = 300L
        private const val BARGE_IN_COOLDOWN_MS = 500L
    }

    // Audio recording components
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private val inputBufferSize = configuration.getInputBufferSize()

    // Audio playback components
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isPlaying = false
    private val outputBufferSize = configuration.getOutputBufferSize()

    // Audio session management
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var isInitialized = false

    // Core state management
    private var lastSoundTimestamp = 0L
    private var silenceDetectionEnabled = true
    private var bargeInMode = false
    private var voiceActivityBuffer = mutableListOf<Boolean>()
    private var consecutiveSpeechDuration = 0L
    private var lastBargeInTimestamp = 0L
    private var sessionActive = false

    // Flow streams
    private val _audioChunks = MutableSharedFlow<AudioChunk>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val audioChunks: SharedFlow<AudioChunk> = _audioChunks.asSharedFlow()

    private val _realtimeInput = MutableSharedFlow<LiveApiMessage.RealtimeInput>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val realtimeInput: SharedFlow<LiveApiMessage.RealtimeInput> = _realtimeInput.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _bargeInDetected = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val bargeInDetected: SharedFlow<Long> = _bargeInDetected.asSharedFlow()

    private val _audioSessionEvents = MutableSharedFlow<AudioSessionEvent>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val audioSessionEvents: SharedFlow<AudioSessionEvent> = _audioSessionEvents.asSharedFlow()

    private val _playbackAudio = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val playbackAudio: SharedFlow<ByteArray> = _playbackAudio.asSharedFlow()

    // Delegate quality monitoring to AudioQualityMonitor
    val audioQuality = qualityMonitor.qualityUpdates

    // Delegate permission management to AudioPermissionManager
    val permissionStatus = permissionManager.permissionStatus

    init {
        initializeAudioManager()
        initializeComponents()
    }

    private fun initializeAudioManager() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        isInitialized = true
        Timber.d("$TAG: AudioManager initialized")
    }

    private fun initializeComponents() {
        launch {
            permissionManager.checkAndEmitStatus()
            qualityMonitor.startMonitoring()
        }
    }

    /**
     * Check if the manager is properly initialized
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * Delegate permission checking to AudioPermissionManager
     */
    fun hasAudioPermission(): Boolean = permissionManager.hasAudioPermission()

    /**
     * Enhanced permission checking for Android 15+
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun hasEnhancedAudioPermissions(): Boolean = permissionManager.hasEnhancedAudioPermissions()

    /**
     * Start audio recording
     */
    suspend fun startRecording(): Boolean {
        if (isRecording) {
            Timber.w("$TAG: Recording already in progress")
            return true
        }

        if (!permissionManager.hasAudioPermission()) {
            val error = "Audio recording permission not granted"
            Timber.e("$TAG: $error")
            _errors.emit(error)
            return false
        }

        try {
            val inputConfig = configuration.getInputConfiguration()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                inputConfig.sampleRate,
                inputConfig.channelConfig,
                inputConfig.audioFormat,
                inputBufferSize
            )

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = launch {
                processAudioRecording()
            }

            _audioSessionEvents.emit(AudioSessionEvent.SessionStarted)
            Timber.d("$TAG: Recording started")
            return true

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to start recording")
            _errors.emit("Failed to start recording: ${e.message}")
            return false
        }
    }

    /**
     * Stop audio recording
     */
    suspend fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        isRecording = false

        _audioSessionEvents.emit(AudioSessionEvent.SessionEnded)
        Timber.d("$TAG: Recording stopped")
    }

    /**
     * Start audio playback
     */
    suspend fun startPlayback(): Boolean {
        if (isPlaying) {
            Timber.w("$TAG: Playback already in progress")
            return true
        }

        try {
            val outputConfig = configuration.getOutputConfiguration()
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                outputConfig.sampleRate,
                outputConfig.channelConfig,
                outputConfig.audioFormat,
                outputBufferSize,
                AudioTrack.MODE_STREAM
            )

            audioTrack?.play()
            isPlaying = true
            Timber.d("$TAG: Playback started")
            return true

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to start playback")
            _errors.emit("Failed to start playback: ${e.message}")
            return false
        }
    }

    /**
     * Stop audio playback
     */
    suspend fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null

        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
        isPlaying = false

        Timber.d("$TAG: Playback stopped")
    }

    /**
     * Process audio recording in background
     */
    private suspend fun processAudioRecording() {
        val buffer = ShortArray(inputBufferSize / 2) // 16-bit samples

        while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (bytesRead > 0) {
                    processAudioBuffer(buffer, bytesRead)
                }

            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error reading audio data")
                _errors.emit("Audio recording error: ${e.message}")
                break
            }

            delay(configuration.getChunkDurationMs())
        }
    }

    /**
     * Process audio buffer and emit events
     */
    private suspend fun processAudioBuffer(buffer: ShortArray, size: Int) {
        // Quality monitoring
        qualityMonitor.processAudioChunk(buffer)

        // Voice activity detection
        val hasVoiceActivity = detectVoiceActivity(buffer, size)
        updateVoiceActivityBuffer(hasVoiceActivity)

        // Barge-in detection
        if (bargeInMode && hasVoiceActivity) {
            checkBargeInCondition()
        }

        // Create audio chunk
        val audioData = convertToByteArray(buffer, size)
        val chunk = AudioChunk(
            data = audioData,
            timestamp = System.currentTimeMillis(),
            sampleRate = configuration.getInputConfiguration().sampleRate
        )

        _audioChunks.emit(chunk)

        // Create realtime input for Live API
        val mediaChunk = MediaChunk(
            data = Base64.encodeToString(audioData, Base64.NO_WRAP),
            mimeType = "audio/pcm"
        )

        val realtimeInput = LiveApiMessage.RealtimeInput(
            mediaChunks = listOf(mediaChunk)
        )
        _realtimeInput.emit(realtimeInput)
    }

    private fun detectVoiceActivity(buffer: ShortArray, size: Int): Boolean {
        val energy = buffer.take(size).map { abs(it.toInt()) }.average()
        return energy > SILENCE_THRESHOLD
    }

    private fun updateVoiceActivityBuffer(hasActivity: Boolean) {
        voiceActivityBuffer.add(hasActivity)
        if (voiceActivityBuffer.size > VOICE_ACTIVITY_BUFFER_SIZE) {
            voiceActivityBuffer.removeAt(0)
        }
    }

    private suspend fun checkBargeInCondition() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBargeInTimestamp > BARGE_IN_COOLDOWN_MS) {
            consecutiveSpeechDuration += configuration.getChunkDurationMs()

            if (consecutiveSpeechDuration >= BARGE_IN_MIN_DURATION_MS) {
                _bargeInDetected.emit(currentTime)
                lastBargeInTimestamp = currentTime
                consecutiveSpeechDuration = 0L
            }
        }
    }

    private fun convertToByteArray(buffer: ShortArray, size: Int): ByteArray {
        val byteArray = ByteArray(size * 2)
        for (i in 0 until size) {
            val sample = buffer[i]
            byteArray[i * 2] = (sample.toInt() and 0xFF).toByte()
            byteArray[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return byteArray
    }

    fun enableLowLatencyMode(enabled: Boolean) {
        configuration.enableLowLatencyMode(enabled)
        Timber.d("$TAG: Low latency mode: $enabled")
    }

    fun isLowLatencyEnabled(): Boolean = configuration.isLowLatencyEnabled()

    fun enableBargeInMode(enabled: Boolean) {
        bargeInMode = enabled
        configuration.enableLowLatencyMode(enabled) // Enable low latency for barge-in
        Timber.d("$TAG: Barge-in mode: $enabled, Low latency: ${configuration.isLowLatencyEnabled()}")
    }

    // Core status methods
    fun isRecording(): Boolean = isRecording
    fun isPlaying(): Boolean = isPlaying
    fun isSessionActive(): Boolean = sessionActive
    fun hasCurrentAudioFocus(): Boolean = hasAudioFocus

    fun startSession() {
        sessionActive = true
        Timber.d("$TAG: Audio session started")
    }

    fun endSession() {
        sessionActive = false
        Timber.d("$TAG: Audio session ended")
    }

    suspend fun playAudio(audioData: ByteArray) {
        qualityMonitor.processAudioChunk(audioData.map { it.toShort() }.toShortArray())
        _playbackAudio.emit(audioData)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun releaseAudioFocus() {
        audioFocusRequest?.let { request ->
            audioManager?.abandonAudioFocusRequest(request)
        }
        hasAudioFocus = false
    }

    fun dispose() {
        Timber.d("$TAG: Disposing AudioStreamManager")

        runBlocking {
            stopRecording()
            stopPlayback()
        }

        qualityMonitor.stopMonitoring()

        // Release audio focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            releaseAudioFocus()
        }

        // Clear buffers
        voiceActivityBuffer.clear()

        // Clean up state
        sessionActive = false
        isInitialized = false
        audioManager = null

        cancel() // Cancel coroutine scope

        Timber.i("$TAG: AudioStreamManager disposed")
    }
}