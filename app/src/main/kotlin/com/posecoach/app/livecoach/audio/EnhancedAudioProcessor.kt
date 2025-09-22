package com.posecoach.app.livecoach.audio

import android.content.Context
import android.media.AudioFormat
import android.util.Base64
import androidx.annotation.IntRange
import com.posecoach.app.livecoach.models.AudioChunk
import com.posecoach.app.livecoach.models.LiveApiMessage
import com.posecoach.app.livecoach.models.MediaChunk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext
import kotlin.math.*

/**
 * Enhanced audio processor with adaptive compression, quality optimization, and WebRTC-style features
 */
class EnhancedAudioProcessor(
    private val context: Context,
    private val parentScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        parentScope.coroutineContext + SupervisorJob()

    companion object {
        private const val DEFAULT_SAMPLE_RATE = 16000
        private const val HIGH_QUALITY_SAMPLE_RATE = 24000
        private const val LOW_QUALITY_SAMPLE_RATE = 8000

        private const val DEFAULT_BITRATE = 32000 // 32 kbps
        private const val HIGH_QUALITY_BITRATE = 64000 // 64 kbps
        private const val LOW_QUALITY_BITRATE = 16000 // 16 kbps

        private const val FRAME_SIZE_MS = 20 // 20ms frames for optimal latency/quality
        private const val NETWORK_QUALITY_CHECK_INTERVAL = 5000L
        private const val QUALITY_ADAPTATION_THRESHOLD = 3

        // Audio processing constants
        private const val SILENCE_THRESHOLD_DB = -30.0
        private const val NOISE_GATE_THRESHOLD_DB = -40.0
        private const val AGC_TARGET_LEVEL_DB = -16.0
        private const val COMPRESSOR_RATIO = 3.0f

        // Adaptive thresholds
        private const val NETWORK_POOR_THRESHOLD = 0.3
        private const val NETWORK_GOOD_THRESHOLD = 0.8
        private const val BATTERY_LOW_THRESHOLD = 20

        // Audio utility functions
        private fun dbToLinear(db: Double): Float {
            return 10.0.pow(db / 20.0).toFloat()
        }

        private fun linearToDb(linear: Double): Double {
            return 20.0 * log10(maxOf(linear, 1e-10))
        }
    }

    // Quality levels
    enum class AudioQuality(
        val sampleRate: Int,
        val bitrate: Int,
        val complexity: Int,
        val description: String
    ) {
        LOW(LOW_QUALITY_SAMPLE_RATE, LOW_QUALITY_BITRATE, 0, "Low Quality (Battery Saver)"),
        MEDIUM(DEFAULT_SAMPLE_RATE, DEFAULT_BITRATE, 5, "Medium Quality (Balanced)"),
        HIGH(HIGH_QUALITY_SAMPLE_RATE, HIGH_QUALITY_BITRATE, 10, "High Quality (Best Audio)")
    }

    // Network conditions
    enum class NetworkCondition(val score: Double) {
        POOR(0.2),
        FAIR(0.5),
        GOOD(0.8),
        EXCELLENT(1.0)
    }

    private var currentQuality = AudioQuality.MEDIUM
    private var networkCondition = NetworkCondition.GOOD
    private var batteryOptimizationEnabled = false
    private var adaptiveQualityEnabled = true

    // Audio processing components
    private var noiseGate: NoiseGate? = null
    private var compressor: AudioCompressor? = null
    private var agc: AutomaticGainControl? = null
    private var vadDetector: VoiceActivityDetector? = null

    // Metrics tracking
    private var totalBytesProcessed = 0L
    private var compressionRatio = 1.0
    private var averageLatency = 0.0
    private var qualityAdaptationCount = 0

    private val _processedAudio = MutableSharedFlow<LiveApiMessage.RealtimeInput>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val processedAudio: SharedFlow<LiveApiMessage.RealtimeInput> = _processedAudio.asSharedFlow()

    private val _qualityMetrics = MutableSharedFlow<AudioQualityMetrics>(
        replay = 1,
        extraBufferCapacity = 5
    )
    val qualityMetrics: SharedFlow<AudioQualityMetrics> = _qualityMetrics.asSharedFlow()

    private val _adaptationEvents = MutableSharedFlow<QualityAdaptationEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val adaptationEvents: SharedFlow<QualityAdaptationEvent> = _adaptationEvents.asSharedFlow()

    init {
        initializeAudioProcessing()
        startQualityMonitoring()
    }

    private fun initializeAudioProcessing() {
        try {
            noiseGate = NoiseGate(NOISE_GATE_THRESHOLD_DB)
            compressor = AudioCompressor(COMPRESSOR_RATIO, AGC_TARGET_LEVEL_DB)
            agc = AutomaticGainControl(AGC_TARGET_LEVEL_DB)
            vadDetector = VoiceActivityDetector(SILENCE_THRESHOLD_DB)

            Timber.d("Enhanced audio processing initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize audio processing")
        }
    }

    /**
     * Process raw audio chunk with enhanced algorithms
     */
    suspend fun processAudioChunk(
        audioChunk: AudioChunk,
        networkQuality: Double = 1.0,
        batteryLevel: Int = 100
    ) {
        val startTime = System.currentTimeMillis()

        try {
            // Update conditions
            updateNetworkCondition(networkQuality)
            updateBatteryOptimization(batteryLevel)

            // Adaptive quality adjustment
            if (adaptiveQualityEnabled) {
                adaptQualityBasedOnConditions()
            }

            // Process audio through pipeline
            val processedData = processAudioPipeline(audioChunk.data)

            // Compress if needed
            val compressedData = if (shouldCompress()) {
                compressAudio(processedData, audioChunk.sampleRate)
            } else {
                processedData
            }

            // Create enhanced media chunk
            val mediaChunk = createEnhancedMediaChunk(
                compressedData,
                audioChunk.timestamp,
                audioChunk.sampleRate
            )

            val realtimeInput = LiveApiMessage.RealtimeInput(
                mediaChunks = listOf(mediaChunk)
            )

            _processedAudio.emit(realtimeInput)

            // Update metrics
            updateProcessingMetrics(
                inputSize = audioChunk.data.size,
                outputSize = compressedData.size,
                processingTime = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            Timber.e(e, "Error processing audio chunk")
        }
    }

    private fun processAudioPipeline(rawData: ByteArray): ByteArray {
        try {
            // Convert to float samples for processing
            val samples = convertToFloatSamples(rawData)

            // Apply audio processing chain
            val processedSamples = samples
                .let { noiseGate?.process(it) ?: it }
                .let { agc?.process(it) ?: it }
                .let { compressor?.process(it) ?: it }

            // Convert back to byte array
            return convertToByteArray(processedSamples)

        } catch (e: Exception) {
            Timber.e(e, "Error in audio pipeline")
            return rawData // Return original data if processing fails
        }
    }

    private fun convertToFloatSamples(byteData: ByteArray): FloatArray {
        val samples = FloatArray(byteData.size / 2)
        val buffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in samples.indices) {
            samples[i] = buffer.short.toFloat() / 32768.0f
        }

        return samples
    }

    private fun convertToByteArray(samples: FloatArray): ByteArray {
        val byteArray = ByteArray(samples.size * 2)
        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)

        for (sample in samples) {
            val shortSample = (sample * 32767.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
            buffer.putShort(shortSample)
        }

        return byteArray
    }

    private fun shouldCompress(): Boolean {
        return when {
            networkCondition == NetworkCondition.POOR -> true
            batteryOptimizationEnabled -> true
            currentQuality == AudioQuality.LOW -> true
            else -> false
        }
    }

    private suspend fun compressAudio(data: ByteArray, sampleRate: Int): ByteArray {
        return try {
            // Simulate Opus-like compression
            // In a real implementation, you would use an actual Opus encoder
            val compressionFactor = when (currentQuality) {
                AudioQuality.LOW -> 0.3
                AudioQuality.MEDIUM -> 0.5
                AudioQuality.HIGH -> 0.8
            }

            val compressedSize = (data.size * compressionFactor).toInt()
            compressionRatio = data.size.toDouble() / compressedSize

            // Placeholder compression (in real implementation, use Opus)
            data.sliceArray(0 until minOf(compressedSize, data.size))

        } catch (e: Exception) {
            Timber.e(e, "Audio compression failed")
            data
        }
    }

    private fun createEnhancedMediaChunk(
        data: ByteArray,
        timestamp: Long,
        sampleRate: Int
    ): MediaChunk {
        val base64Data = Base64.encodeToString(data, Base64.NO_WRAP)

        // Enhanced MIME type with quality indicators
        val qualityParams = buildString {
            append("rate=$sampleRate")
            append(";quality=${currentQuality.name.lowercase()}")
            append(";bitrate=${currentQuality.bitrate}")
            append(";processed=true")

            if (shouldCompress()) {
                append(";compression=opus")
                append(";ratio=${String.format("%.2f", compressionRatio)}")
            }

            // Add VAD information if available
            vadDetector?.let { vad ->
                val hasVoice = vad.detectVoiceActivity(convertToFloatSamples(data))
                append(";voice_activity=$hasVoice")
            }
        }

        return MediaChunk(
            mimeType = "audio/pcm;$qualityParams",
            data = base64Data
        )
    }

    private fun updateNetworkCondition(quality: Double) {
        val newCondition = when {
            quality >= NETWORK_GOOD_THRESHOLD -> NetworkCondition.EXCELLENT
            quality >= 0.6 -> NetworkCondition.GOOD
            quality >= NETWORK_POOR_THRESHOLD -> NetworkCondition.FAIR
            else -> NetworkCondition.POOR
        }

        if (newCondition != networkCondition) {
            Timber.d("Network condition changed: ${networkCondition.name} -> ${newCondition.name}")
            networkCondition = newCondition
        }
    }

    private fun updateBatteryOptimization(batteryLevel: Int) {
        val shouldOptimize = batteryLevel <= BATTERY_LOW_THRESHOLD
        if (shouldOptimize != batteryOptimizationEnabled) {
            batteryOptimizationEnabled = shouldOptimize
            Timber.d("Battery optimization: $batteryOptimizationEnabled (level: $batteryLevel%)")
        }
    }

    private suspend fun adaptQualityBasedOnConditions() {
        val newQuality = determineOptimalQuality()

        if (newQuality != currentQuality) {
            val oldQuality = currentQuality
            currentQuality = newQuality
            qualityAdaptationCount++

            val event = QualityAdaptationEvent(
                fromQuality = oldQuality,
                toQuality = newQuality,
                trigger = getAdaptationTrigger(),
                timestamp = System.currentTimeMillis()
            )

            _adaptationEvents.emit(event)

            Timber.i("Audio quality adapted: ${oldQuality.name} -> ${newQuality.name} (${event.trigger})")
        }
    }

    private fun determineOptimalQuality(): AudioQuality {
        return when {
            batteryOptimizationEnabled -> AudioQuality.LOW
            networkCondition == NetworkCondition.POOR -> AudioQuality.LOW
            networkCondition == NetworkCondition.FAIR -> AudioQuality.MEDIUM
            networkCondition in listOf(NetworkCondition.GOOD, NetworkCondition.EXCELLENT) -> AudioQuality.HIGH
            else -> AudioQuality.MEDIUM
        }
    }

    private fun getAdaptationTrigger(): String {
        return when {
            batteryOptimizationEnabled -> "battery_saving"
            networkCondition == NetworkCondition.POOR -> "poor_network"
            networkCondition == NetworkCondition.FAIR -> "fair_network"
            else -> "optimal_conditions"
        }
    }

    private fun updateProcessingMetrics(inputSize: Int, outputSize: Int, processingTime: Long) {
        totalBytesProcessed += inputSize.toLong()
        averageLatency = (averageLatency * 0.9) + (processingTime * 0.1)

        launch {
            val metrics = AudioQualityMetrics(
                quality = currentQuality,
                networkCondition = networkCondition,
                compressionRatio = compressionRatio,
                averageLatency = averageLatency,
                totalBytesProcessed = totalBytesProcessed,
                adaptationCount = qualityAdaptationCount,
                batteryOptimized = batteryOptimizationEnabled
            )

            _qualityMetrics.emit(metrics)
        }
    }

    private fun startQualityMonitoring() {
        launch {
            while (isActive) {
                delay(NETWORK_QUALITY_CHECK_INTERVAL)

                // Periodic quality check and adaptation
                if (adaptiveQualityEnabled) {
                    adaptQualityBasedOnConditions()
                }
            }
        }
    }

    // Public API methods
    fun setAudioQuality(quality: AudioQuality) {
        currentQuality = quality
        Timber.d("Audio quality manually set to: ${quality.name}")
    }

    fun enableAdaptiveQuality(enabled: Boolean) {
        adaptiveQualityEnabled = enabled
        Timber.d("Adaptive quality: $enabled")
    }

    fun getCurrentQuality(): AudioQuality = currentQuality

    fun getNetworkCondition(): NetworkCondition = networkCondition

    fun getProcessingStats(): Map<String, Any> {
        return mapOf(
            "currentQuality" to currentQuality.name,
            "networkCondition" to networkCondition.name,
            "compressionRatio" to compressionRatio,
            "averageLatency" to averageLatency,
            "totalBytesProcessed" to totalBytesProcessed,
            "adaptationCount" to qualityAdaptationCount,
            "batteryOptimized" to batteryOptimizationEnabled,
            "adaptiveQualityEnabled" to adaptiveQualityEnabled
        )
    }

    fun destroy() {
        Timber.d("Destroying EnhancedAudioProcessor")

        // Clean up resources
        noiseGate = null
        compressor = null
        agc = null
        vadDetector = null

        cancel() // Cancel coroutine scope

        Timber.i("EnhancedAudioProcessor destroyed")
    }

    // Data classes for metrics and events
    data class AudioQualityMetrics(
        val quality: AudioQuality,
        val networkCondition: NetworkCondition,
        val compressionRatio: Double,
        val averageLatency: Double,
        val totalBytesProcessed: Long,
        val adaptationCount: Int,
        val batteryOptimized: Boolean
    )

    data class QualityAdaptationEvent(
        val fromQuality: AudioQuality,
        val toQuality: AudioQuality,
        val trigger: String,
        val timestamp: Long
    )

    // Audio processing components (simplified implementations)
    private class NoiseGate(private val thresholdDb: Double) {
        fun process(samples: FloatArray): FloatArray {
            val threshold = EnhancedAudioProcessor.dbToLinear(thresholdDb)
            return samples.map { sample ->
                if (abs(sample) > threshold) sample else 0.0f
            }.toFloatArray()
        }
    }

    private class AudioCompressor(
        private val ratio: Float,
        private val thresholdDb: Double
    ) {
        fun process(samples: FloatArray): FloatArray {
            val threshold = EnhancedAudioProcessor.dbToLinear(thresholdDb)
            return samples.map { sample ->
                val magnitude = abs(sample)
                if (magnitude > threshold) {
                    val excess = magnitude - threshold
                    val compressedExcess = excess / ratio
                    sign(sample) * (threshold + compressedExcess)
                } else {
                    sample
                }
            }.toFloatArray()
        }
    }

    private class AutomaticGainControl(private val targetLevelDb: Double) {
        private var currentGain = 1.0f

        fun process(samples: FloatArray): FloatArray {
            val rms = sqrt(samples.map { it * it }.average()).toFloat()
            val targetLevel = EnhancedAudioProcessor.dbToLinear(targetLevelDb)

            if (rms > 0.001f) { // Avoid division by zero
                val desiredGain = targetLevel / rms
                currentGain = currentGain * 0.9f + desiredGain * 0.1f // Smooth adaptation
            }

            return samples.map { it * currentGain }.toFloatArray()
        }
    }

    private class VoiceActivityDetector(private val thresholdDb: Double) {
        fun detectVoiceActivity(samples: FloatArray): Boolean {
            val rms = sqrt(samples.map { it * it }.average())
            val levelDb = EnhancedAudioProcessor.linearToDb(rms)
            return levelDb > thresholdDb
        }
    }

}