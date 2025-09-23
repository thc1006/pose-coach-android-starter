package com.posecoach.app.livecoach.audio

import com.posecoach.app.livecoach.models.AudioQualityInfo
import com.posecoach.app.livecoach.models.AudioQualityMetrics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import kotlin.math.*

/**
 * AudioQualityMonitor handles audio quality analysis and monitoring.
 * Separated from AudioStreamManager for better modularity (<200 lines).
 *
 * Features:
 * - Real-time audio quality assessment
 * - Noise detection and SNR calculation
 * - Audio clipping detection
 * - Long-term quality metrics tracking
 */
class AudioQualityMonitor(
    private val sampleRate: Int,
    private val channelConfig: Int
) {

    companion object {
        private const val SILENCE_THRESHOLD = 500
        private const val CLIPPING_THRESHOLD_RATIO = 0.95 // 95% of max value
        private const val MIN_SNR_GOOD_QUALITY = 15.0
        private const val NOISE_DETECTION_WINDOW = 1024
        private const val TAG = "AudioQualityMonitor"
    }

    private val _qualityUpdates = MutableSharedFlow<AudioQualityInfo>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val qualityUpdates: SharedFlow<AudioQualityInfo> = _qualityUpdates.asSharedFlow()

    private var isMonitoring = false
    private var metrics = AudioQualityMetrics()
    private var processingStartTime = 0L

    /**
     * Analyze audio quality from a buffer
     */
    fun analyzeQuality(buffer: ShortArray): AudioQualityInfo {
        require(buffer.isNotEmpty()) { "Audio buffer cannot be empty" }

        val startTime = System.nanoTime()

        val signalLevel = calculateSignalLevel(buffer)
        val noiseLevel = calculateNoiseLevel(buffer)
        val snr = calculateSNR(buffer)
        val isClipped = detectClipping(buffer)
        val clippingPercentage = calculateClippingPercentage(buffer)

        val hasNoise = noiseLevel > 0.3
        val score = calculateQualityScore(signalLevel, noiseLevel, snr, isClipped)

        val processingTime = (System.nanoTime() - startTime) / 1_000_000.0
        updateMetrics(score, hasNoise, isClipped, processingTime)

        return AudioQualityInfo(
            score = score,
            hasNoise = hasNoise,
            noiseLevel = noiseLevel,
            signalLevel = signalLevel,
            isClipped = isClipped,
            clippingPercentage = clippingPercentage,
            snrRatio = snr
        )
    }

    /**
     * Calculate Signal-to-Noise Ratio
     */
    fun calculateSNR(buffer: ShortArray): Double {
        val signalPower = calculateSignalPower(buffer)
        val noisePower = calculateNoisePower(buffer)

        return if (noisePower > 0) {
            10 * log10(signalPower / noisePower)
        } else {
            Double.MAX_VALUE // Perfect signal, no noise
        }
    }

    /**
     * Start continuous quality monitoring
     */
    fun startMonitoring() {
        isMonitoring = true
        processingStartTime = System.currentTimeMillis()
        Timber.d("$TAG: Quality monitoring started")
    }

    /**
     * Stop quality monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        Timber.d("$TAG: Quality monitoring stopped")
    }

    /**
     * Process audio chunk during monitoring
     */
    suspend fun processAudioChunk(buffer: ShortArray) {
        if (!isMonitoring) return

        val quality = analyzeQuality(buffer)
        _qualityUpdates.emit(quality)
    }

    /**
     * Get current quality metrics
     */
    fun getMetrics(): AudioQualityMetrics = metrics.copy()

    private fun calculateSignalLevel(buffer: ShortArray): Double {
        val rms = sqrt(buffer.map { it.toDouble() * it.toDouble() }.average())
        return rms / Short.MAX_VALUE
    }

    private fun calculateNoiseLevel(buffer: ShortArray): Double {
        // Estimate noise level using high-frequency components
        val windowSize = min(NOISE_DETECTION_WINDOW, buffer.size)
        var highFreqEnergy = 0.0

        for (i in 0 until windowSize - 1) {
            val diff = buffer[i + 1] - buffer[i]
            highFreqEnergy += diff * diff
        }

        return sqrt(highFreqEnergy / windowSize) / Short.MAX_VALUE
    }

    private fun calculateSignalPower(buffer: ShortArray): Double {
        return buffer.map { it.toDouble() * it.toDouble() }.average()
    }

    private fun calculateNoisePower(buffer: ShortArray): Double {
        // Estimate noise power from silent regions
        val threshold = SILENCE_THRESHOLD
        val silentSamples = buffer.filter { abs(it) < threshold }

        return if (silentSamples.isNotEmpty()) {
            silentSamples.map { it.toDouble() * it.toDouble() }.average()
        } else {
            0.0
        }
    }

    private fun detectClipping(buffer: ShortArray): Boolean {
        val clippingThreshold = (Short.MAX_VALUE * CLIPPING_THRESHOLD_RATIO).toInt()
        return buffer.any { abs(it) >= clippingThreshold }
    }

    private fun calculateClippingPercentage(buffer: ShortArray): Double {
        val clippingThreshold = (Short.MAX_VALUE * CLIPPING_THRESHOLD_RATIO).toInt()
        val clippedSamples = buffer.count { abs(it) >= clippingThreshold }
        return clippedSamples.toDouble() / buffer.size
    }

    private fun calculateQualityScore(
        signalLevel: Double,
        noiseLevel: Double,
        snr: Double,
        isClipped: Boolean
    ): Double {
        var score = 1.0

        // Reduce score for low signal level
        if (signalLevel < 0.1) score *= 0.5

        // Reduce score for high noise
        score *= (1.0 - noiseLevel).coerceAtLeast(0.0)

        // Reduce score for poor SNR
        if (snr < MIN_SNR_GOOD_QUALITY) {
            score *= (snr / MIN_SNR_GOOD_QUALITY).coerceAtLeast(0.1)
        }

        // Severe penalty for clipping
        if (isClipped) score *= 0.3

        return score.coerceIn(0.0, 1.0)
    }

    private fun updateMetrics(score: Double, hasNoise: Boolean, isClipped: Boolean, processingTime: Double) {
        metrics = metrics.copy(
            samplesProcessed = metrics.samplesProcessed + 1,
            averageQuality = ((metrics.averageQuality * metrics.samplesProcessed) + score) / (metrics.samplesProcessed + 1),
            minQuality = min(metrics.minQuality, score),
            maxQuality = max(metrics.maxQuality, score),
            noiseDetectionCount = if (hasNoise) metrics.noiseDetectionCount + 1 else metrics.noiseDetectionCount,
            clippingDetectionCount = if (isClipped) metrics.clippingDetectionCount + 1 else metrics.clippingDetectionCount,
            totalProcessingTime = metrics.totalProcessingTime + processingTime.toLong()
        )
    }
}