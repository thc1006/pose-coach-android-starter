package com.posecoach.app.livecoach.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
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

class AudioStreamManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val CHUNK_DURATION_MS = 1000L // 1 second chunks
        private const val SILENCE_THRESHOLD = 500 // Adjust based on testing
        private const val SILENCE_DURATION_MS = 2000L // 2 seconds of silence
        private const val BARGE_IN_THRESHOLD = 800 // Higher threshold for barge-in detection
        private const val BARGE_IN_MIN_DURATION_MS = 300L // Minimum speech duration to trigger barge-in
        private const val VOICE_ACTIVITY_BUFFER_SIZE = 10 // Number of recent chunks to analyze
        private const val LOW_LATENCY_CHUNK_MS = 100L // Smaller chunks for barge-in responsiveness
        private const val QUALITY_CHECK_INTERVAL = 5000L // Check audio quality every 5 seconds
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_MULTIPLIER

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

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording() {
        if (isRecording) {
            Timber.w("Already recording")
            return
        }

        if (!hasAudioPermission()) {
            val error = "Audio recording permission not granted"
            Timber.e(error)
            launch { _errors.emit(error) }
            return
        }

        try {
            initializeAudioRecord()
            isRecording = true
            lastSoundTimestamp = System.currentTimeMillis()

            recordingJob = launch {
                recordAudioLoop()
            }

            Timber.d("Audio recording started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start audio recording")
            launch { _errors.emit("Failed to start recording: ${e.message}") }
        }
    }

    private fun initializeAudioRecord() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialized")
                }
                startRecording()
            }
        } catch (e: Exception) {
            audioRecord?.release()
            audioRecord = null
            throw e
        }
    }

    private suspend fun recordAudioLoop() {
        val buffer = ShortArray(bufferSize / 2) // 16-bit samples
        val baseChunkDuration = if (lowLatencyMode || bargeInMode) LOW_LATENCY_CHUNK_MS else CHUNK_DURATION_MS
        val chunkSamples = (SAMPLE_RATE * baseChunkDuration / 1000).toInt()
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
            if (kotlin.math.abs(buffer[i]) > 30000) {
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
                sampleRate = SAMPLE_RATE
            )

            // Emit raw audio chunk
            _audioChunks.emit(audioChunk)

            // Create Live API message with quality indicators
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mimeType = if (analysis.hasVoiceActivity) {
                "audio/pcm;rate=$SAMPLE_RATE;voice_activity=true"
            } else {
                "audio/pcm;rate=$SAMPLE_RATE;voice_activity=false"
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
            Timber.d("Audio recording stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio recording")
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

        if (qualityInfo.qualityScore < 0.3) {
            _errors.emit("Poor audio quality detected - check microphone positioning")
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
        return Pair(bufferSize, SAMPLE_RATE)
    }

    fun getAdvancedBufferInfo(): Map<String, Any> {
        return mapOf(
            "bufferSize" to bufferSize,
            "sampleRate" to SAMPLE_RATE,
            "audioFormat" to AUDIO_FORMAT,
            "chunkDurationMs" to if (lowLatencyMode) LOW_LATENCY_CHUNK_MS else CHUNK_DURATION_MS,
            "bargeInMode" to bargeInMode,
            "lowLatencyMode" to lowLatencyMode,
            "qualityScore" to calculateQualityScore(),
            "voiceActivityBufferSize" to voiceActivityBuffer.size
        )
    }

    fun destroy() {
        Timber.d("Destroying AudioStreamManager")
        stopRecording()

        // Clear buffers
        voiceActivityBuffer.clear()

        // Reset metrics
        audioQualityMetrics = AudioQualityMetrics()

        cancel() // Cancel coroutine scope

        Timber.i("AudioStreamManager destroyed")
    }
}