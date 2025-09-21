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

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.posecoach.gemini.live.models.AudioConfig
import com.posecoach.gemini.live.models.LiveApiError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.math.*

/**
 * Adaptive audio quality management and optimization for Gemini Live API
 * Handles dynamic quality adjustment, noise reduction, and audio enhancement
 */
class AdaptiveAudioManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : CoroutineScope {

    companion object {
        private const val QUALITY_ASSESSMENT_INTERVAL_MS = 5000L
        private const val ADAPTATION_HYSTERESIS = 3 // Require 3 consecutive poor samples before adapting
        private const val NOISE_FLOOR_ALPHA = 0.1f // Exponential smoothing factor
        private const val SIGNAL_FLOOR_ALPHA = 0.3f
        private const val MIN_SNR_THRESHOLD = 10.0f // dB
        private const val EXCELLENT_SNR_THRESHOLD = 25.0f // dB
        private const val VOLUME_ADAPTATION_STEP = 0.1f
        private const val MAX_VOLUME_BOOST = 2.0f
        private const val MIN_VOLUME_LEVEL = 0.1f
    }

    override val coroutineContext: CoroutineContext = scope.coroutineContext

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Audio enhancement components
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    // Adaptive settings
    private val currentQualityLevel = AtomicReference(AudioQualityLevel.MEDIUM)
    private val currentVolumeBoost = AtomicReference(1.0f)
    private val adaptationEnabled = AtomicBoolean(true)

    // Quality tracking
    private var noiseFloor = 0.0f
    private var signalLevel = 0.0f
    private var snrHistory = mutableListOf<Float>()
    private var qualityHistory = mutableListOf<AudioQuality>()
    private var consecutivePoorSamples = 0

    // Flows
    private val _qualityLevel = MutableStateFlow(AudioQualityLevel.MEDIUM)
    val qualityLevel: StateFlow<AudioQualityLevel> = _qualityLevel.asStateFlow()

    private val _adaptationEvents = MutableSharedFlow<AudioAdaptationEvent>()
    val adaptationEvents: SharedFlow<AudioAdaptationEvent> = _adaptationEvents.asSharedFlow()

    private val _enhancementStatus = MutableStateFlow(AudioEnhancementStatus())
    val enhancementStatus: StateFlow<AudioEnhancementStatus> = _enhancementStatus.asStateFlow()

    // Jobs
    private var qualityAssessmentJob: Job? = null
    private var environmentMonitoringJob: Job? = null

    init {
        initializeAudioEnhancements()
        startQualityAssessment()
        startEnvironmentMonitoring()
    }

    /**
     * Process audio frame and apply adaptive enhancements
     */
    fun processAudioFrame(audioData: ByteArray): ByteArray {
        try {
            // Update quality metrics
            updateQualityMetrics(audioData)

            // Apply enhancements based on current quality level
            var processedData = audioData

            if (adaptationEnabled.get()) {
                processedData = applyVolumeAdaptation(processedData)
                processedData = applyNoiseReduction(processedData)
                processedData = applyDynamicRangeCompression(processedData)
            }

            return processedData

        } catch (e: Exception) {
            Timber.e(e, "Error processing audio frame")
            return audioData // Return original data on error
        }
    }

    /**
     * Adapt quality settings based on current conditions
     */
    suspend fun adaptQualitySettings(currentQuality: AudioQuality) {
        if (!adaptationEnabled.get()) return

        qualityHistory.add(currentQuality)
        if (qualityHistory.size > 10) {
            qualityHistory.removeAt(0)
        }

        val currentLevel = currentQualityLevel.get()

        when (currentQuality) {
            AudioQuality.POOR -> {
                consecutivePoorSamples++
                if (consecutivePoorSamples >= ADAPTATION_HYSTERESIS && currentLevel != AudioQualityLevel.LOW) {
                    adaptToLowerQuality()
                }
            }

            AudioQuality.EXCELLENT -> {
                consecutivePoorSamples = 0
                if (currentLevel != AudioQualityLevel.HIGH && canUpgradeQuality()) {
                    adaptToHigherQuality()
                }
            }

            else -> {
                consecutivePoorSamples = max(0, consecutivePoorSamples - 1)
                if (currentLevel == AudioQualityLevel.LOW && currentQuality != AudioQuality.POOR) {
                    adaptToMediumQuality()
                }
            }
        }
    }

    /**
     * Configure audio enhancements
     */
    fun configureEnhancements(
        enableNoiseSuppression: Boolean = true,
        enableGainControl: Boolean = true,
        enableEchoCancellation: Boolean = true,
        enableAdaptation: Boolean = true
    ) {
        try {
            noiseSuppressor?.enabled = enableNoiseSuppression
            automaticGainControl?.enabled = enableGainControl
            acousticEchoCanceler?.enabled = enableEchoCancellation
            adaptationEnabled.set(enableAdaptation)

            updateEnhancementStatus()

            Timber.d("Audio enhancements configured: NS=$enableNoiseSuppression, AGC=$enableGainControl, AEC=$enableEchoCancellation, Adaptive=$enableAdaptation")

        } catch (e: Exception) {
            Timber.e(e, "Error configuring audio enhancements")
        }
    }

    /**
     * Handle poor audio conditions
     */
    suspend fun handlePoorAudioConditions() {
        _adaptationEvents.emit(
            AudioAdaptationEvent.QualityDegradation(
                "Poor audio conditions detected",
                getCurrentAudioMetrics()
            )
        )

        // Apply emergency audio optimizations
        applyEmergencyOptimizations()
    }

    /**
     * Get current audio metrics
     */
    fun getCurrentAudioMetrics(): AudioMetrics {
        val currentSNR = if (noiseFloor > 0) {
            20 * log10(signalLevel / noiseFloor)
        } else {
            Float.MAX_VALUE
        }

        return AudioMetrics(
            snrDb = currentSNR,
            signalLevel = signalLevel,
            noiseLevel = noiseFloor,
            qualityLevel = currentQualityLevel.get(),
            volumeBoost = currentVolumeBoost.get(),
            enhancementsActive = _enhancementStatus.value.anyEnabled(),
            adaptationEnabled = adaptationEnabled.get()
        )
    }

    private fun initializeAudioEnhancements() {
        try {
            // Initialize noise suppressor
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(0) // AudioSession ID 0 for global
                noiseSuppressor?.enabled = true
            } else {
                Timber.w("Noise suppressor not available on this device")
            }

            // Initialize automatic gain control
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(0)
                automaticGainControl?.enabled = true
            } else {
                Timber.w("Automatic gain control not available on this device")
            }

            // Initialize acoustic echo canceler
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(0)
                acousticEchoCanceler?.enabled = true
            } else {
                Timber.w("Acoustic echo canceler not available on this device")
            }

            updateEnhancementStatus()

        } catch (e: Exception) {
            Timber.e(e, "Error initializing audio enhancements")
        }
    }

    private fun updateQualityMetrics(audioData: ByteArray) {
        val energy = calculateRMSEnergy(audioData)

        // Update noise floor with exponential smoothing
        if (energy < signalLevel * 0.1f) { // Likely noise
            noiseFloor = noiseFloor * (1 - NOISE_FLOOR_ALPHA) + energy * NOISE_FLOOR_ALPHA
        }

        // Update signal level
        signalLevel = signalLevel * (1 - SIGNAL_FLOOR_ALPHA) + energy * SIGNAL_FLOOR_ALPHA

        // Calculate SNR
        val currentSNR = if (noiseFloor > 0) {
            20 * log10(signalLevel / noiseFloor)
        } else {
            Float.MAX_VALUE
        }

        snrHistory.add(currentSNR)
        if (snrHistory.size > 50) { // Keep last 50 samples
            snrHistory.removeAt(0)
        }
    }

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

    private fun applyVolumeAdaptation(audioData: ByteArray): ByteArray {
        val volumeBoost = currentVolumeBoost.get()
        if (abs(volumeBoost - 1.0f) < 0.01f) return audioData

        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        val processedData = ByteArray(audioData.size)
        val processedBuffer = ByteBuffer.wrap(processedData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until audioData.size / 2) {
            val sample = buffer.getShort(i * 2)
            val boostedSample = (sample * volumeBoost).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            processedBuffer.putShort(i * 2, boostedSample.toInt().toShort())
        }

        return processedData
    }

    private fun applyNoiseReduction(audioData: ByteArray): ByteArray {
        // Simple spectral subtraction noise reduction
        if (noiseFloor <= 0) return audioData

        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        val processedData = ByteArray(audioData.size)
        val processedBuffer = ByteBuffer.wrap(processedData).order(ByteOrder.LITTLE_ENDIAN)

        val reductionFactor = when (currentQualityLevel.get()) {
            AudioQualityLevel.HIGH -> 0.1f
            AudioQualityLevel.MEDIUM -> 0.2f
            AudioQualityLevel.LOW -> 0.3f
        }

        for (i in 0 until audioData.size / 2) {
            val sample = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE
            val magnitude = abs(sample)

            val processedSample = if (magnitude > noiseFloor * 2) {
                // Signal likely present
                sample
            } else {
                // Likely noise - reduce
                sample * (1 - reductionFactor)
            }

            val finalSample = (processedSample * Short.MAX_VALUE).coerceIn(
                Short.MIN_VALUE.toFloat(),
                Short.MAX_VALUE.toFloat()
            )
            processedBuffer.putShort(i * 2, finalSample.toInt().toShort())
        }

        return processedData
    }

    private fun applyDynamicRangeCompression(audioData: ByteArray): ByteArray {
        // Simple dynamic range compression to improve consistency
        val compressionRatio = when (currentQualityLevel.get()) {
            AudioQualityLevel.HIGH -> 2.0f
            AudioQualityLevel.MEDIUM -> 3.0f
            AudioQualityLevel.LOW -> 4.0f
        }

        val threshold = 0.7f // Compression threshold
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        val processedData = ByteArray(audioData.size)
        val processedBuffer = ByteBuffer.wrap(processedData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until audioData.size / 2) {
            val sample = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE
            val magnitude = abs(sample)

            val compressedSample = if (magnitude > threshold) {
                val excess = magnitude - threshold
                val compressedExcess = excess / compressionRatio
                val sign = if (sample >= 0) 1 else -1
                sign * (threshold + compressedExcess)
            } else {
                sample
            }

            val finalSample = (compressedSample * Short.MAX_VALUE).coerceIn(
                Short.MIN_VALUE.toFloat(),
                Short.MAX_VALUE.toFloat()
            )
            processedBuffer.putShort(i * 2, finalSample.toInt().toShort())
        }

        return processedData
    }

    private suspend fun adaptToLowerQuality() {
        val newLevel = when (currentQualityLevel.get()) {
            AudioQualityLevel.HIGH -> AudioQualityLevel.MEDIUM
            AudioQualityLevel.MEDIUM -> AudioQualityLevel.LOW
            AudioQualityLevel.LOW -> return // Already at lowest
        }

        currentQualityLevel.set(newLevel)
        _qualityLevel.value = newLevel

        // Increase volume boost to compensate
        val newVolumeBoost = min(MAX_VOLUME_BOOST, currentVolumeBoost.get() + VOLUME_ADAPTATION_STEP)
        currentVolumeBoost.set(newVolumeBoost)

        _adaptationEvents.emit(
            AudioAdaptationEvent.QualityReduction(
                "Adapted to lower quality: $newLevel",
                newLevel,
                newVolumeBoost
            )
        )

        Timber.i("Adapted to lower audio quality: $newLevel, volume boost: $newVolumeBoost")
    }

    private suspend fun adaptToHigherQuality() {
        val newLevel = when (currentQualityLevel.get()) {
            AudioQualityLevel.LOW -> AudioQualityLevel.MEDIUM
            AudioQualityLevel.MEDIUM -> AudioQualityLevel.HIGH
            AudioQualityLevel.HIGH -> return // Already at highest
        }

        currentQualityLevel.set(newLevel)
        _qualityLevel.value = newLevel

        // Reduce volume boost if conditions are good
        val newVolumeBoost = max(MIN_VOLUME_LEVEL, currentVolumeBoost.get() - VOLUME_ADAPTATION_STEP)
        currentVolumeBoost.set(newVolumeBoost)

        _adaptationEvents.emit(
            AudioAdaptationEvent.QualityImprovement(
                "Adapted to higher quality: $newLevel",
                newLevel,
                newVolumeBoost
            )
        )

        Timber.i("Adapted to higher audio quality: $newLevel, volume boost: $newVolumeBoost")
    }

    private suspend fun adaptToMediumQuality() {
        currentQualityLevel.set(AudioQualityLevel.MEDIUM)
        _qualityLevel.value = AudioQualityLevel.MEDIUM

        val newVolumeBoost = 1.0f + (currentVolumeBoost.get() - 1.0f) * 0.5f // Reduce boost by half
        currentVolumeBoost.set(newVolumeBoost)

        _adaptationEvents.emit(
            AudioAdaptationEvent.QualityStabilization(
                "Stabilized at medium quality",
                AudioQualityLevel.MEDIUM,
                newVolumeBoost
            )
        )

        Timber.i("Stabilized at medium audio quality, volume boost: $newVolumeBoost")
    }

    private fun canUpgradeQuality(): Boolean {
        // Check if conditions are stable enough for upgrade
        val recentQualities = qualityHistory.takeLast(5)
        return recentQualities.size >= 3 &&
               recentQualities.none { it == AudioQuality.POOR } &&
               recentQualities.count { it == AudioQuality.EXCELLENT || it == AudioQuality.GOOD } >= 3
    }

    private suspend fun applyEmergencyOptimizations() {
        // Force lowest quality and maximum enhancements
        currentQualityLevel.set(AudioQualityLevel.LOW)
        _qualityLevel.value = AudioQualityLevel.LOW

        // Enable all available enhancements
        configureEnhancements(
            enableNoiseSuppression = true,
            enableGainControl = true,
            enableEchoCancellation = true,
            enableAdaptation = true
        )

        // Apply maximum volume boost
        currentVolumeBoost.set(MAX_VOLUME_BOOST)

        _adaptationEvents.emit(
            AudioAdaptationEvent.EmergencyOptimization(
                "Applied emergency audio optimizations",
                getCurrentAudioMetrics()
            )
        )

        Timber.w("Applied emergency audio optimizations")
    }

    private fun startQualityAssessment() {
        qualityAssessmentJob = scope.launch {
            while (true) {
                delay(QUALITY_ASSESSMENT_INTERVAL_MS)

                try {
                    val avgSNR = snrHistory.takeLastWhile { it.isFinite() }.average().toFloat()
                    val quality = when {
                        avgSNR >= EXCELLENT_SNR_THRESHOLD -> AudioQuality.EXCELLENT
                        avgSNR >= MIN_SNR_THRESHOLD -> AudioQuality.GOOD
                        avgSNR >= MIN_SNR_THRESHOLD * 0.7f -> AudioQuality.FAIR
                        else -> AudioQuality.POOR
                    }

                    adaptQualitySettings(quality)

                } catch (e: Exception) {
                    Timber.e(e, "Error in quality assessment")
                }
            }
        }
    }

    private fun startEnvironmentMonitoring() {
        environmentMonitoringJob = scope.launch {
            while (true) {
                delay(10_000) // Check every 10 seconds

                try {
                    // Monitor audio environment changes
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val volumePercent = currentVolume.toFloat() / maxVolume.toFloat()

                    // Adapt to system volume changes
                    if (volumePercent < 0.3f && currentVolumeBoost.get() < MAX_VOLUME_BOOST * 0.8f) {
                        // Low system volume - increase boost
                        currentVolumeBoost.set(min(MAX_VOLUME_BOOST, currentVolumeBoost.get() + VOLUME_ADAPTATION_STEP))
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Error in environment monitoring")
                }
            }
        }
    }

    private fun updateEnhancementStatus() {
        _enhancementStatus.value = AudioEnhancementStatus(
            noiseSuppression = noiseSuppressor?.enabled ?: false,
            automaticGainControl = automaticGainControl?.enabled ?: false,
            echoCancellation = acousticEchoCanceler?.enabled ?: false,
            adaptationEnabled = adaptationEnabled.get()
        )
    }

    fun cleanup() {
        qualityAssessmentJob?.cancel()
        environmentMonitoringJob?.cancel()

        try {
            noiseSuppressor?.release()
            automaticGainControl?.release()
            acousticEchoCanceler?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing audio effects")
        }

        scope.cancel()
    }
}

enum class AudioQualityLevel {
    LOW,
    MEDIUM,
    HIGH
}

sealed class AudioAdaptationEvent {
    data class QualityReduction(val message: String, val newLevel: AudioQualityLevel, val volumeBoost: Float) : AudioAdaptationEvent()
    data class QualityImprovement(val message: String, val newLevel: AudioQualityLevel, val volumeBoost: Float) : AudioAdaptationEvent()
    data class QualityStabilization(val message: String, val level: AudioQualityLevel, val volumeBoost: Float) : AudioAdaptationEvent()
    data class QualityDegradation(val message: String, val metrics: AudioMetrics) : AudioAdaptationEvent()
    data class EmergencyOptimization(val message: String, val metrics: AudioMetrics) : AudioAdaptationEvent()
}

data class AudioMetrics(
    val snrDb: Float,
    val signalLevel: Float,
    val noiseLevel: Float,
    val qualityLevel: AudioQualityLevel,
    val volumeBoost: Float,
    val enhancementsActive: Boolean,
    val adaptationEnabled: Boolean
)

data class AudioEnhancementStatus(
    val noiseSuppression: Boolean = false,
    val automaticGainControl: Boolean = false,
    val echoCancellation: Boolean = false,
    val adaptationEnabled: Boolean = false
) {
    fun anyEnabled(): Boolean = noiseSuppression || automaticGainControl || echoCancellation
}