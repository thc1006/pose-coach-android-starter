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

package com.posecoach.gemini.live.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.posecoach.gemini.live.models.AudioConfig
import com.posecoach.gemini.live.models.LiveApiError
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/**
 * Comprehensive audio processing pipeline for Gemini Live API
 * Handles recording, encoding, VAD, and playback with quality adaptation
 */
class AudioProcessor(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    companion object {
        const val FRAME_SIZE_MS = 20
        const val VAD_THRESHOLD = 0.02f
        const val VAD_HANGOVER_MS = 500
        const val NOISE_FLOOR_ESTIMATION_FRAMES = 50
        const val AUDIO_QUALITY_CHECK_INTERVAL_MS = 1000L
        const val MAX_AUDIO_BUFFER_SIZE = 8192
    }

    private val vadProcessor = VoiceActivityDetector()
    private val qualityAnalyzer = AudioQualityAnalyzer()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)

    private val audioBufferSizeInput = AudioRecord.getMinBufferSize(
        AudioConfig.SAMPLE_RATE_INPUT,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(AudioConfig.BYTES_PER_FRAME * 4)

    private val audioBufferSizeOutput = AudioTrack.getMinBufferSize(
        AudioConfig.SAMPLE_RATE_OUTPUT,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(AudioConfig.BYTES_PER_FRAME * 4)

    // Audio streams
    private val _audioInput = MutableSharedFlow<AudioFrame>()
    val audioInput: SharedFlow<AudioFrame> = _audioInput.asSharedFlow()

    private val _voiceActivity = MutableStateFlow(false)
    val voiceActivity: StateFlow<Boolean> = _voiceActivity.asStateFlow()

    private val _audioQuality = MutableStateFlow(AudioQuality.GOOD)
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private val _audioErrors = MutableSharedFlow<LiveApiError.AudioError>()
    val audioErrors: SharedFlow<LiveApiError.AudioError> = _audioErrors.asSharedFlow()

    private var recordingJob: Job? = null
    private var qualityMonitorJob: Job? = null

    /**
     * Start audio recording with VAD
     */
    suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRecording.get()) {
                return@withContext Result.success(Unit)
            }

            initializeAudioRecord()?.let { record ->
                audioRecord = record

                try {
                    record.startRecording()
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        LiveApiError.AudioError("Failed to start recording: ${e.message}")
                    )
                }

                isRecording.set(true)
                startRecordingLoop()
                startQualityMonitoring()

                Timber.d("Audio recording started")
                Result.success(Unit)

            } ?: Result.failure(
                LiveApiError.AudioError("Failed to initialize AudioRecord")
            )

        } catch (e: Exception) {
            Timber.e(e, "Error starting audio recording")
            Result.failure(LiveApiError.AudioError("Recording start failed", e))
        }
    }

    /**
     * Stop audio recording
     */
    suspend fun stopRecording() = withContext(Dispatchers.IO) {
        try {
            isRecording.set(false)
            recordingJob?.cancel()
            qualityMonitorJob?.cancel()

            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null

            Timber.d("Audio recording stopped")

        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio recording")
        }
    }

    /**
     * Start audio playback
     */
    suspend fun startPlayback(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isPlaying.get()) {
                return@withContext Result.success(Unit)
            }

            initializeAudioTrack()?.let { track ->
                audioTrack = track
                track.play()
                isPlaying.set(true)

                Timber.d("Audio playback started")
                Result.success(Unit)

            } ?: Result.failure(
                LiveApiError.AudioError("Failed to initialize AudioTrack")
            )

        } catch (e: Exception) {
            Timber.e(e, "Error starting audio playback")
            Result.failure(LiveApiError.AudioError("Playback start failed", e))
        }
    }

    /**
     * Stop audio playback
     */
    suspend fun stopPlayback() = withContext(Dispatchers.IO) {
        try {
            isPlaying.set(false)

            audioTrack?.apply {
                stop()
                release()
            }
            audioTrack = null

            Timber.d("Audio playback stopped")

        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio playback")
        }
    }

    /**
     * Play received audio data
     */
    suspend fun playAudioData(audioData: ByteArray) {
        if (!isPlaying.get() || audioTrack == null) {
            startPlayback()
        }

        try {
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio data")
            _audioErrors.emit(LiveApiError.AudioError("Playback error", e))
        }
    }

    private fun initializeAudioRecord(): AudioRecord? {
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConfig.SAMPLE_RATE_INPUT,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSizeInput
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                record
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create AudioRecord")
            null
        }
    }

    private fun initializeAudioTrack(): AudioTrack? {
        return try {
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                AudioConfig.SAMPLE_RATE_OUTPUT,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSizeOutput,
                AudioTrack.MODE_STREAM
            )

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                null
            } else {
                track
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create AudioTrack")
            null
        }
    }

    private fun startRecordingLoop() {
        recordingJob = scope.launch {
            val buffer = ByteArray(AudioConfig.BYTES_PER_FRAME)

            while (isRecording.get() && audioRecord != null) {
                try {
                    val bytesRead = audioRecord!!.read(buffer, 0, buffer.size)

                    if (bytesRead > 0) {
                        val audioFrame = AudioFrame(
                            data = buffer.copyOf(bytesRead),
                            timestamp = System.currentTimeMillis(),
                            sampleRate = AudioConfig.SAMPLE_RATE_INPUT,
                            channels = AudioConfig.CHANNELS
                        )

                        // Process with VAD
                        val hasVoice = vadProcessor.processFrame(audioFrame)
                        _voiceActivity.value = hasVoice

                        // Emit audio frame
                        _audioInput.emit(audioFrame)

                        // Update quality analyzer
                        qualityAnalyzer.processFrame(audioFrame)

                    } else if (bytesRead < 0) {
                        Timber.w("AudioRecord read error: $bytesRead")
                        break
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Error in recording loop")
                    _audioErrors.emit(LiveApiError.AudioError("Recording loop error", e))
                    break
                }

                // Small delay to prevent busy waiting
                delay(1)
            }
        }
    }

    private fun startQualityMonitoring() {
        qualityMonitorJob = scope.launch {
            while (isRecording.get()) {
                delay(AUDIO_QUALITY_CHECK_INTERVAL_MS)

                val quality = qualityAnalyzer.getCurrentQuality()
                _audioQuality.value = quality

                if (quality == AudioQuality.POOR) {
                    Timber.w("Poor audio quality detected")
                    _audioErrors.emit(
                        LiveApiError.AudioError("Poor audio quality detected")
                    )
                }
            }
        }
    }

    /**
     * Get processed audio frames suitable for Live API
     */
    fun getProcessedAudioFlow(): Flow<ByteArray> {
        return audioInput
            .filter { vadProcessor.hasVoiceActivity() || !vadProcessor.isEnabled }
            .map { frame -> frame.data }
            .buffer(Channel.UNLIMITED)
    }

    /**
     * Configure Voice Activity Detection
     */
    fun configureVAD(
        enabled: Boolean = true,
        threshold: Float = VAD_THRESHOLD,
        hangoverMs: Int = VAD_HANGOVER_MS
    ) {
        vadProcessor.configure(enabled, threshold, hangoverMs)
    }

    /**
     * Get current audio statistics
     */
    fun getAudioStats(): AudioStats {
        return AudioStats(
            isRecording = isRecording.get(),
            isPlaying = isPlaying.get(),
            voiceActivity = _voiceActivity.value,
            audioQuality = _audioQuality.value,
            vadEnabled = vadProcessor.isEnabled,
            signalLevel = qualityAnalyzer.getCurrentSignalLevel(),
            noiseLevel = qualityAnalyzer.getCurrentNoiseLevel()
        )
    }

    fun cleanup() {
        scope.launch {
            stopRecording()
            stopPlayback()
        }
    }
}

/**
 * Voice Activity Detection implementation
 */
class VoiceActivityDetector {

    var isEnabled: Boolean = true
        private set

    private var threshold = AudioProcessor.VAD_THRESHOLD
    private var hangoverMs = AudioProcessor.VAD_HANGOVER_MS
    private var voiceActive = false
    private var hangoverFrames = 0
    private val hangoverFrameCount get() = hangoverMs / AudioProcessor.FRAME_SIZE_MS

    private var noiseFloor = 0f
    private var frameCount = 0
    private val energyHistory = mutableListOf<Float>()

    fun configure(enabled: Boolean, threshold: Float, hangoverMs: Int) {
        this.isEnabled = enabled
        this.threshold = threshold
        this.hangoverMs = hangoverMs
    }

    fun processFrame(frame: AudioFrame): Boolean {
        if (!isEnabled) return true

        val energy = calculateFrameEnergy(frame.data)
        energyHistory.add(energy)

        // Keep only recent history for noise floor estimation
        if (energyHistory.size > AudioProcessor.NOISE_FLOOR_ESTIMATION_FRAMES) {
            energyHistory.removeAt(0)
        }

        // Update noise floor (use median of recent low-energy frames)
        if (frameCount < AudioProcessor.NOISE_FLOOR_ESTIMATION_FRAMES) {
            frameCount++
            noiseFloor = energyHistory.sorted()[energyHistory.size / 4] // 25th percentile
        }

        // Voice activity detection
        val isVoiceFrame = energy > (noiseFloor + threshold)

        if (isVoiceFrame) {
            voiceActive = true
            hangoverFrames = hangoverFrameCount
        } else if (hangoverFrames > 0) {
            hangoverFrames--
        } else {
            voiceActive = false
        }

        return voiceActive
    }

    fun hasVoiceActivity(): Boolean = voiceActive

    private fun calculateFrameEnergy(audioData: ByteArray): Float {
        var sum = 0.0
        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val sample = (audioData[i].toInt() and 0xFF) or
                            ((audioData[i + 1].toInt() and 0xFF) shl 8)
                val normalizedSample = sample.toShort().toFloat() / Short.MAX_VALUE
                sum += normalizedSample * normalizedSample
            }
        }
        return sqrt(sum / (audioData.size / 2)).toFloat()
    }
}

/**
 * Audio quality analysis
 */
class AudioQualityAnalyzer {

    private var signalLevel = 0f
    private var noiseLevel = 0f
    private var qualityHistory = mutableListOf<Float>()

    fun processFrame(frame: AudioFrame) {
        val energy = calculateRMSEnergy(frame.data)
        val snr = calculateSNR(frame.data)

        signalLevel = energy

        // Simple quality assessment based on SNR and signal level
        val qualityScore = when {
            snr > 20f && energy > 0.01f -> 1.0f  // Excellent
            snr > 10f && energy > 0.005f -> 0.7f // Good
            snr > 5f && energy > 0.002f -> 0.4f  // Fair
            else -> 0.1f                         // Poor
        }

        qualityHistory.add(qualityScore)
        if (qualityHistory.size > 10) {
            qualityHistory.removeAt(0)
        }
    }

    fun getCurrentQuality(): AudioQuality {
        if (qualityHistory.isEmpty()) return AudioQuality.UNKNOWN

        val avgQuality = qualityHistory.average()
        return when {
            avgQuality > 0.8 -> AudioQuality.EXCELLENT
            avgQuality > 0.6 -> AudioQuality.GOOD
            avgQuality > 0.3 -> AudioQuality.FAIR
            else -> AudioQuality.POOR
        }
    }

    fun getCurrentSignalLevel(): Float = signalLevel
    fun getCurrentNoiseLevel(): Float = noiseLevel

    private fun calculateRMSEnergy(audioData: ByteArray): Float {
        var sum = 0.0
        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val sample = (audioData[i].toInt() and 0xFF) or
                            ((audioData[i + 1].toInt() and 0xFF) shl 8)
                val normalizedSample = sample.toShort().toFloat() / Short.MAX_VALUE
                sum += normalizedSample * normalizedSample
            }
        }
        return sqrt(sum / (audioData.size / 2)).toFloat()
    }

    private fun calculateSNR(audioData: ByteArray): Float {
        // Simplified SNR calculation
        val energy = calculateRMSEnergy(audioData)
        val estimatedNoise = energy * 0.1f // Assume 10% noise
        noiseLevel = estimatedNoise

        return if (estimatedNoise > 0) {
            20 * log10(energy / estimatedNoise)
        } else {
            Float.MAX_VALUE
        }
    }
}

data class AudioFrame(
    val data: ByteArray,
    val timestamp: Long,
    val sampleRate: Int,
    val channels: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioFrame

        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        return result
    }
}

enum class AudioQuality {
    UNKNOWN,
    POOR,
    FAIR,
    GOOD,
    EXCELLENT
}

data class AudioStats(
    val isRecording: Boolean,
    val isPlaying: Boolean,
    val voiceActivity: Boolean,
    val audioQuality: AudioQuality,
    val vadEnabled: Boolean,
    val signalLevel: Float,
    val noiseLevel: Float
)