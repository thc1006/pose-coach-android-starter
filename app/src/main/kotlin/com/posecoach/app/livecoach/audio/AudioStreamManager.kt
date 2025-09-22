package com.posecoach.app.livecoach.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.posecoach.app.livecoach.models.AudioChunk
import com.posecoach.app.livecoach.models.LiveApiMessage
import com.posecoach.app.livecoach.models.MediaChunk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class AudioStreamManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    companion object {
        // Input audio configuration (for Gemini Live API)
        private const val INPUT_SAMPLE_RATE = 16000
        private const val INPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val INPUT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Output audio configuration (for playback)
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val OUTPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val OUTPUT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Buffer and streaming configuration
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val CHUNK_DURATION_MS = 1000L // 1 second chunks
        private const val LOW_LATENCY_CHUNK_MS = 100L // Smaller chunks for barge-in responsiveness

        // Voice activity and silence detection
        private const val SILENCE_THRESHOLD = 500 // Adjust based on testing
        private const val SILENCE_DURATION_MS = 2000L // 2 seconds of silence
        private const val VOICE_ACTIVITY_BUFFER_SIZE = 10 // Number of recent chunks to analyze

        // Barge-in detection configuration
        private const val BARGE_IN_THRESHOLD = 800 // Higher threshold for barge-in detection
        private const val BARGE_IN_MIN_DURATION_MS = 300L // Minimum speech duration to trigger barge-in
        private const val BARGE_IN_COOLDOWN_MS = 500L // Cooldown period between barge-ins

        // Quality monitoring
        private const val QUALITY_CHECK_INTERVAL = 5000L // Check audio quality every 5 seconds
        private const val QUALITY_SCORE_THRESHOLD = 0.3 // Threshold for poor quality warning

        // Android 15+ audio session management
        private const val AUDIO_SESSION_TIMEOUT_MS = 30000L // 30 seconds
        private const val PERMISSION_REQUEST_TIMEOUT_MS = 10000L // 10 seconds
    }

    // Audio recording components
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private val inputBufferSize = AudioRecord.getMinBufferSize(
        INPUT_SAMPLE_RATE,
        INPUT_CHANNEL_CONFIG,
        INPUT_AUDIO_FORMAT
    ) * BUFFER_SIZE_MULTIPLIER

    // Audio playback components
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isPlaying = false
    private val outputBufferSize = AudioTrack.getMinBufferSize(
        OUTPUT_SAMPLE_RATE,
        OUTPUT_CHANNEL_CONFIG,
        OUTPUT_AUDIO_FORMAT
    ) * BUFFER_SIZE_MULTIPLIER

    // Audio session and permission management (Android 15+)
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

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

    private var lastSoundTimestamp = 0L
    private var silenceDetectionEnabled = true
    private var bargeInMode = false
    private var voiceActivityBuffer = mutableListOf<Boolean>()
    private var consecutiveSpeechDuration = 0L
    private var lastBargeInTimestamp = 0L
    private var audioQualityMetrics = AudioQualityMetrics()
    private var lowLatencyMode = false

    // Barge-in detection callbacks
    private val _bargeInDetected = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val bargeInDetected: SharedFlow<Long> = _bargeInDetected.asSharedFlow()

    private val _audioQuality = MutableSharedFlow<AudioQualityInfo>(
        replay = 1,
        extraBufferCapacity = 5
    )
    val audioQuality: SharedFlow<AudioQualityInfo> = _audioQuality.asSharedFlow()

    // Audio playback streams
    private val _playbackAudio = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val playbackAudio: SharedFlow<ByteArray> = _playbackAudio.asSharedFlow()

    // Audio session events
    private val _audioSessionEvents = MutableSharedFlow<AudioSessionEvent>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val audioSessionEvents: SharedFlow<AudioSessionEvent> = _audioSessionEvents.asSharedFlow()

    // Permission status updates
    private val _permissionStatus = MutableSharedFlow<AudioPermissionStatus>(
        replay = 1,
        extraBufferCapacity = 3
    )
    val permissionStatus: SharedFlow<AudioPermissionStatus> = _permissionStatus.asSharedFlow()

    init {
        initializeAudioManager()
        checkPermissionsAndEmitStatus()
    }

    private fun initializeAudioManager() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        Timber.d("AudioManager initialized")
    }

    private fun checkPermissionsAndEmitStatus() {
        launch {
            val status = getCurrentPermissionStatus()
            _permissionStatus.emit(status)
        }
    }

    /**
     * Check audio permissions with Android 15+ best practices
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Enhanced permission checking for Android 15+
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun hasEnhancedAudioPermissions(): Boolean {
        val recordPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val modifyPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        return recordPermission && modifyPermission
    }

    /**
     * Get current permission status with details
     */
    private fun getCurrentPermissionStatus(): AudioPermissionStatus {
        val recordPermission = hasAudioPermission()
        val modifyPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        return AudioPermissionStatus(
            recordAudio = recordPermission,
            modifyAudioSettings = modifyPermission,
            isFullyGranted = recordPermission && modifyPermission,
            timestamp = System.currentTimeMillis()
        )
    }

    fun startRecording() {
        if (isRecording) {
            Timber.w("Already recording")
            return
        }

        if (!hasAudioPermission()) {
            val error = "Audio recording permission not granted"
            Timber.e(error)
            launch {
                _errors.emit(error)
                _audioSessionEvents.emit(AudioSessionEvent.PermissionDenied(error))
            }
            return
        }

        try {
            // Request audio focus for recording
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestAudioFocus()
            }

            initializeAudioRecord()
            isRecording = true
            lastSoundTimestamp = System.currentTimeMillis()

            recordingJob = launch {
                recordAudioLoop()
            }

            launch {
                _audioSessionEvents.emit(AudioSessionEvent.RecordingStarted(System.currentTimeMillis()))
            }

            Timber.d("Audio recording started with enhanced session management")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start audio recording")
            launch {
                _errors.emit("Failed to start recording: ${e.message}")
                _audioSessionEvents.emit(AudioSessionEvent.Error("Recording failed: ${e.message}"))
            }
        }
    }

    private fun initializeAudioRecord() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                INPUT_SAMPLE_RATE,
                INPUT_CHANNEL_CONFIG,
                INPUT_AUDIO_FORMAT,
                inputBufferSize
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialized")
                }
                startRecording()
            }

            Timber.d("AudioRecord initialized: sample rate=$INPUT_SAMPLE_RATE, buffer size=$inputBufferSize")
        } catch (e: Exception) {
            audioRecord?.release()
            audioRecord = null
            throw e
        }
    }

    /**
     * Request audio focus for recording (Android 8.0+)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        audioManager?.let { manager ->
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            val result = manager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

            Timber.d("Audio focus request result: $result, hasAudioFocus: $hasAudioFocus")

            launch {
                _audioSessionEvents.emit(
                    if (hasAudioFocus) {
                        AudioSessionEvent.AudioFocusGranted(System.currentTimeMillis())
                    } else {
                        AudioSessionEvent.AudioFocusLost("Failed to gain audio focus", System.currentTimeMillis())
                    }
                )
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                Timber.d("Audio focus gained")
                launch {
                    _audioSessionEvents.emit(AudioSessionEvent.AudioFocusGranted(System.currentTimeMillis()))
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                Timber.d("Audio focus lost: $focusChange")
                launch {
                    _audioSessionEvents.emit(
                        AudioSessionEvent.AudioFocusLost(
                            "Focus change: $focusChange",
                            System.currentTimeMillis()
                        )
                    )
                }

                // If recording, consider pausing or stopping
                if (isRecording && focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    Timber.w("Stopping recording due to audio focus loss")
                    stopRecording()
                }
            }
        }
    }

    private suspend fun recordAudioLoop() {
        val buffer = ShortArray(inputBufferSize / 2) // 16-bit samples
        val baseChunkDuration = if (lowLatencyMode || bargeInMode) LOW_LATENCY_CHUNK_MS else CHUNK_DURATION_MS
        val chunkSamples = (INPUT_SAMPLE_RATE * baseChunkDuration / 1000).toInt()
        val chunkBuffer = mutableListOf<Short>()
        var qualityCheckTime = System.currentTimeMillis()

        try {
            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (bytesRead > 0) {
                    val timestamp = System.currentTimeMillis()

                    // Add to chunk buffer
                    for (i in 0 until bytesRead) {
                        chunkBuffer.add(buffer[i])
                    }

                    // Analyze audio for voice activity and barge-in
                    val audioAnalysis = analyzeAudioBuffer(buffer, bytesRead, timestamp)
                    updateVoiceActivityBuffer(audioAnalysis.hasVoiceActivity)

                    if (audioAnalysis.hasVoiceActivity) {
                        lastSoundTimestamp = timestamp
                        consecutiveSpeechDuration += (timestamp - (audioAnalysis.previousTimestamp ?: timestamp))

                        // Check for barge-in conditions
                        if (bargeInMode && shouldTriggerBargeIn(audioAnalysis)) {
                            handleBargeInDetection(timestamp)
                        }
                    } else {
                        consecutiveSpeechDuration = 0L
                    }

                    // Update audio quality metrics
                    updateAudioQualityMetrics(audioAnalysis)

                    // Send chunk based on various conditions
                    val shouldSend = chunkBuffer.size >= chunkSamples ||
                                   shouldSendDueToSilence(timestamp) ||
                                   (bargeInMode && audioAnalysis.hasVoiceActivity) ||
                                   shouldSendForQuality()

                    if (shouldSend) {
                        sendAudioChunk(chunkBuffer.toShortArray(), timestamp, audioAnalysis)
                        chunkBuffer.clear()
                    }

                    // Periodic quality check
                    if (timestamp - qualityCheckTime > QUALITY_CHECK_INTERVAL) {
                        reportAudioQuality()
                        qualityCheckTime = timestamp
                    }
                } else {
                    // Handle read error
                    if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Timber.e("AudioRecord read error: Invalid operation")
                        launch { _errors.emit("Audio recording failed - microphone may be in use by another app") }
                        break
                    }
                }

                // Adaptive delay based on mode
                val delayMs = if (lowLatencyMode || bargeInMode) 5L else 10L
                delay(delayMs)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in audio recording loop")
            launch { _errors.emit("Recording error: ${e.message}") }
        } finally {
            // Send final chunk if any data remains
            if (chunkBuffer.isNotEmpty()) {
                val finalAnalysis = AudioAnalysis(
                    hasVoiceActivity = false,
                    amplitude = 0.0,
                    signalToNoiseRatio = 0.0
                )
                sendAudioChunk(chunkBuffer.toShortArray(), System.currentTimeMillis(), finalAnalysis)
            }

            // Send audio stream end signal
            sendAudioStreamEnd()
        }
    }

    data class AudioAnalysis(
        val hasVoiceActivity: Boolean,
        val amplitude: Double,
        val signalToNoiseRatio: Double,
        val previousTimestamp: Long? = null
    )

    data class AudioQualityInfo(
        val averageAmplitude: Double,
        val signalToNoiseRatio: Double,
        val clippingPercentage: Double,
        val qualityScore: Double // 0.0 to 1.0
    )

    data class AudioQualityMetrics(
        var totalSamples: Long = 0,
        var amplitudeSum: Double = 0.0,
        var clippedSamples: Long = 0,
        var lastUpdate: Long = System.currentTimeMillis()
    )

    private fun analyzeAudioBuffer(buffer: ShortArray, length: Int, timestamp: Long): AudioAnalysis {
        var amplitudeSum = 0.0
        var maxAmplitude = 0.0
        var clippedCount = 0

        for (i in 0 until length) {
            val amplitude = kotlin.math.abs(buffer[i].toDouble())
            amplitudeSum += amplitude
            maxAmplitude = kotlin.math.max(maxAmplitude, amplitude)

            // Check for clipping (near max 16-bit value)
            if (kotlin.math.abs(buffer[i].toInt()) > 30000) {
                clippedCount++
            }
        }

        val averageAmplitude = amplitudeSum / length
        val hasVoiceActivity = averageAmplitude > SILENCE_THRESHOLD

        // Simple SNR estimation (this is a basic approximation)
        val signalToNoiseRatio = if (averageAmplitude > 0) {
            20.0 * kotlin.math.log10(maxAmplitude / (averageAmplitude + 1.0))
        } else {
            0.0
        }

        return AudioAnalysis(
            hasVoiceActivity = hasVoiceActivity,
            amplitude = averageAmplitude,
            signalToNoiseRatio = signalToNoiseRatio
        )
    }

    private fun detectSound(buffer: ShortArray, length: Int): Boolean {
        return analyzeAudioBuffer(buffer, length, System.currentTimeMillis()).hasVoiceActivity
    }

    private fun shouldSendDueToSilence(currentTimestamp: Long): Boolean {
        return silenceDetectionEnabled &&
               (currentTimestamp - lastSoundTimestamp) >= SILENCE_DURATION_MS
    }

    private suspend fun sendAudioChunk(samples: ShortArray, timestamp: Long, analysis: AudioAnalysis) {
        try {
            // Convert to byte array (16-bit PCM)
            val bytes = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val sample = samples[i].toInt()
                bytes[i * 2] = (sample and 0xFF).toByte()
                bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }

            // Create audio chunk with enhanced metadata
            val audioChunk = AudioChunk(
                data = bytes,
                timestamp = timestamp,
                sampleRate = INPUT_SAMPLE_RATE
            )

            // Emit raw audio chunk
            _audioChunks.emit(audioChunk)

            // Create Live API message with quality indicators
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mimeType = if (analysis.hasVoiceActivity) {
                "audio/pcm;rate=$INPUT_SAMPLE_RATE;voice_activity=true"
            } else {
                "audio/pcm;rate=$INPUT_SAMPLE_RATE;voice_activity=false"
            }

            val mediaChunk = MediaChunk(
                mimeType = mimeType,
                data = base64Data
            )

            val realtimeInputMessage = LiveApiMessage.RealtimeInput(
                mediaChunks = listOf(mediaChunk)
            )

            _realtimeInput.emit(realtimeInputMessage)

            val mode = if (bargeInMode) "barge-in" else "normal"
            Timber.v("Sent audio chunk: ${bytes.size} bytes, mode=$mode, voice=${analysis.hasVoiceActivity}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send audio chunk")
            _errors.emit("Failed to send audio: ${e.message}")
        }
    }

    private suspend fun sendAudioStreamEnd() {
        try {
            val endMessage = LiveApiMessage.RealtimeInput(audioStreamEnd = true)
            _realtimeInput.emit(endMessage)
            Timber.d("Sent audio stream end signal")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send audio stream end")
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            Timber.w("Not currently recording")
            return
        }

        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            // Release audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                releaseAudioFocus()
            }

            launch {
                _audioSessionEvents.emit(AudioSessionEvent.RecordingStopped(System.currentTimeMillis()))
            }

            Timber.d("Audio recording stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio recording")
            launch {
                _audioSessionEvents.emit(AudioSessionEvent.Error("Stop recording failed: ${e.message}"))
            }
        }
    }

    /**
     * Release audio focus (Android 8.0+)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun releaseAudioFocus() {
        audioFocusRequest?.let { request ->
            audioManager?.abandonAudioFocusRequest(request)
            hasAudioFocus = false
            audioFocusRequest = null
            Timber.d("Audio focus released")

            launch {
                _audioSessionEvents.emit(AudioSessionEvent.AudioFocusReleased(System.currentTimeMillis()))
            }
        }
    }

    /**
     * Start audio playback for received audio data (24kHz)
     */
    fun startPlayback() {
        if (isPlaying) {
            Timber.w("Already playing audio")
            return
        }

        try {
            initializeAudioTrack()
            isPlaying = true

            playbackJob = launch {
                playbackAudioLoop()
            }

            launch {
                _audioSessionEvents.emit(AudioSessionEvent.PlaybackStarted(System.currentTimeMillis()))
            }

            Timber.d("Audio playback started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start audio playback")
            launch {
                _errors.emit("Failed to start playback: ${e.message}")
                _audioSessionEvents.emit(AudioSessionEvent.Error("Playback failed: ${e.message}"))
            }
        }
    }

    private fun initializeAudioTrack() {
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setEncoding(OUTPUT_AUDIO_FORMAT)
                        .setChannelMask(OUTPUT_CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(outputBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            Timber.d("AudioTrack initialized: sample rate=$OUTPUT_SAMPLE_RATE, buffer size=$outputBufferSize")
        } catch (e: Exception) {
            audioTrack?.release()
            audioTrack = null
            throw e
        }
    }

    private suspend fun playbackAudioLoop() {
        try {
            playbackAudio.collect { audioData ->
                if (isPlaying && audioTrack != null) {
                    val bytesWritten = audioTrack?.write(audioData, 0, audioData.size) ?: 0
                    if (bytesWritten < 0) {
                        Timber.e("Error writing audio data: $bytesWritten")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in playback loop")
            launch { _errors.emit("Playback error: ${e.message}") }
        }
    }

    fun stopPlayback() {
        if (!isPlaying) {
            Timber.w("Not currently playing")
            return
        }

        isPlaying = false
        playbackJob?.cancel()

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            launch {
                _audioSessionEvents.emit(AudioSessionEvent.PlaybackStopped(System.currentTimeMillis()))
            }

            Timber.d("Audio playback stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio playback")
            launch {
                _audioSessionEvents.emit(AudioSessionEvent.Error("Stop playback failed: ${e.message}"))
            }
        }
    }

    /**
     * Queue audio data for playback
     */
    suspend fun queueAudioForPlayback(audioData: ByteArray) {
        try {
            _playbackAudio.emit(audioData)
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue audio for playback")
            _errors.emit("Failed to queue audio: ${e.message}")
        }
    }

    fun setSilenceDetectionEnabled(enabled: Boolean) {
        silenceDetectionEnabled = enabled
        Timber.d("Silence detection: $enabled")
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    private fun updateVoiceActivityBuffer(hasVoiceActivity: Boolean) {
        voiceActivityBuffer.add(hasVoiceActivity)
        if (voiceActivityBuffer.size > VOICE_ACTIVITY_BUFFER_SIZE) {
            voiceActivityBuffer.removeAt(0)
        }
    }

    private fun shouldTriggerBargeIn(analysis: AudioAnalysis): Boolean {
        val now = System.currentTimeMillis()

        // Check if enough time has passed since last barge-in
        if (now - lastBargeInTimestamp < BARGE_IN_MIN_DURATION_MS) {
            return false
        }

        // Check if voice activity is strong enough
        if (analysis.amplitude < BARGE_IN_THRESHOLD) {
            return false
        }

        // Check if we have consistent speech
        return consecutiveSpeechDuration >= BARGE_IN_MIN_DURATION_MS
    }

    private suspend fun handleBargeInDetection(timestamp: Long) {
        val timeSinceLastBargeIn = timestamp - lastBargeInTimestamp

        if (timeSinceLastBargeIn < BARGE_IN_COOLDOWN_MS) {
            Timber.v("Barge-in detection suppressed (cooldown period)")
            return
        }

        lastBargeInTimestamp = timestamp
        Timber.d("Barge-in detected at $timestamp")
        _bargeInDetected.emit(timestamp)
    }

    private fun updateAudioQualityMetrics(analysis: AudioAnalysis) {
        audioQualityMetrics.totalSamples++
        audioQualityMetrics.amplitudeSum += analysis.amplitude
        audioQualityMetrics.lastUpdate = System.currentTimeMillis()
    }

    private fun shouldSendForQuality(): Boolean {
        // Send more frequently if audio quality is poor
        val qualityScore = calculateQualityScore()
        return qualityScore < 0.5 // Poor quality, send more frequently
    }

    private fun calculateQualityScore(): Double {
        if (audioQualityMetrics.totalSamples == 0L) return 1.0

        val avgAmplitude = audioQualityMetrics.amplitudeSum / audioQualityMetrics.totalSamples
        val clippingRatio = audioQualityMetrics.clippedSamples.toDouble() / audioQualityMetrics.totalSamples

        // Simple quality score based on amplitude and clipping
        val amplitudeScore = when {
            avgAmplitude < 100 -> 0.3 // Too quiet
            avgAmplitude > 20000 -> 0.5 // Too loud
            else -> 1.0 // Good range
        }

        val clippingScore = kotlin.math.max(0.0, 1.0 - (clippingRatio * 10))

        return (amplitudeScore + clippingScore) / 2.0
    }

    private suspend fun reportAudioQuality() {
        val qualityInfo = AudioQualityInfo(
            averageAmplitude = if (audioQualityMetrics.totalSamples > 0)
                audioQualityMetrics.amplitudeSum / audioQualityMetrics.totalSamples else 0.0,
            signalToNoiseRatio = 0.0, // Placeholder for now
            clippingPercentage = if (audioQualityMetrics.totalSamples > 0)
                (audioQualityMetrics.clippedSamples.toDouble() / audioQualityMetrics.totalSamples) * 100.0 else 0.0,
            qualityScore = calculateQualityScore()
        )

        _audioQuality.emit(qualityInfo)

        if (qualityInfo.qualityScore < QUALITY_SCORE_THRESHOLD) {
            _errors.emit("Poor audio quality detected (${String.format("%.1f", qualityInfo.qualityScore * 100)}%) - check microphone positioning")
        }
    }

    // Public API for barge-in functionality
    fun enableBargeInMode(enabled: Boolean) {
        bargeInMode = enabled
        lowLatencyMode = enabled // Enable low latency for barge-in
        Timber.d("Barge-in mode: $enabled, Low latency: $lowLatencyMode")
    }

    fun isBargeInModeEnabled(): Boolean = bargeInMode

    fun getRecentVoiceActivity(): List<Boolean> = voiceActivityBuffer.toList()

    fun getCurrentAudioQuality(): Double = calculateQualityScore()

    fun getBufferInfo(): Pair<Int, Int> {
        return Pair(inputBufferSize, INPUT_SAMPLE_RATE)
    }

    fun getAdvancedBufferInfo(): Map<String, Any> {
        return mapOf(
            "inputBufferSize" to inputBufferSize,
            "outputBufferSize" to outputBufferSize,
            "inputSampleRate" to INPUT_SAMPLE_RATE,
            "outputSampleRate" to OUTPUT_SAMPLE_RATE,
            "inputAudioFormat" to INPUT_AUDIO_FORMAT,
            "outputAudioFormat" to OUTPUT_AUDIO_FORMAT,
            "chunkDurationMs" to if (lowLatencyMode) LOW_LATENCY_CHUNK_MS else CHUNK_DURATION_MS,
            "bargeInMode" to bargeInMode,
            "lowLatencyMode" to lowLatencyMode,
            "qualityScore" to calculateQualityScore(),
            "voiceActivityBufferSize" to voiceActivityBuffer.size,
            "hasAudioFocus" to hasAudioFocus,
            "isRecording" to isRecording,
            "isPlaying" to isPlaying
        )
    }

    fun destroy() {
        Timber.d("Destroying AudioStreamManager")

        stopRecording()
        stopPlayback()

        // Release audio focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            releaseAudioFocus()
        }

        // Clear buffers
        voiceActivityBuffer.clear()

        // Reset metrics
        audioQualityMetrics = AudioQualityMetrics()

        // Clean up audio manager references
        audioManager = null

        cancel() // Cancel coroutine scope

        Timber.i("AudioStreamManager destroyed")
    }

    // Additional utility methods for enhanced functionality
    fun isCurrentlyPlaying(): Boolean = isPlaying

    fun hasCurrentAudioFocus(): Boolean = hasAudioFocus

    fun getAudioSessionInfo(): Map<String, Any> {
        return mapOf(
            "recording" to isRecording,
            "playing" to isPlaying,
            "audioFocus" to hasAudioFocus,
            "bargeInMode" to bargeInMode,
            "lowLatencyMode" to lowLatencyMode,
            "permissions" to getCurrentPermissionStatus(),
            "bufferInfo" to getAdvancedBufferInfo()
        )
    }



    // Data classes for new events and status
    sealed class AudioSessionEvent {
        data class RecordingStarted(val timestamp: Long) : AudioSessionEvent()
        data class RecordingStopped(val timestamp: Long) : AudioSessionEvent()
        data class PlaybackStarted(val timestamp: Long) : AudioSessionEvent()
        data class PlaybackStopped(val timestamp: Long) : AudioSessionEvent()
        data class AudioFocusGranted(val timestamp: Long) : AudioSessionEvent()
        data class AudioFocusLost(val reason: String, val timestamp: Long) : AudioSessionEvent()
        data class AudioFocusReleased(val timestamp: Long) : AudioSessionEvent()
        data class PermissionDenied(val reason: String) : AudioSessionEvent()
        data class Error(val message: String) : AudioSessionEvent()
    }

    data class AudioPermissionStatus(
        val recordAudio: Boolean,
        val modifyAudioSettings: Boolean,
        val isFullyGranted: Boolean,
        val timestamp: Long
    )
}