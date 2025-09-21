package com.posecoach.app.multimodal.processors

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.posecoach.app.multimodal.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Audio Intelligence Processor
 *
 * Provides comprehensive audio analysis including:
 * - Voice emotion analysis and stress detection
 * - Breathing pattern analysis from audio signals
 * - Environmental audio analysis (background noise, music)
 * - Real-time voice quality assessment
 * - Motivation and engagement level detection
 */
class AudioIntelligenceProcessor(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2

        // Audio analysis thresholds
        private const val VOICE_ACTIVITY_THRESHOLD = 0.02f
        private const val BREATHING_FREQUENCY_MIN = 0.2f // Hz (12 breaths/min)
        private const val BREATHING_FREQUENCY_MAX = 0.8f // Hz (48 breaths/min)
        private const val STRESS_FREQUENCY_RANGE = 3000..6000 // Hz
        private const val EMOTIONAL_ANALYSIS_WINDOW = 3.0f // seconds
        private const val QUALITY_ANALYSIS_WINDOW = 1.0f // seconds
    }

    private var audioRecord: AudioRecord? = null
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR

    /**
     * Process raw audio signal and extract intelligence features
     */
    suspend fun processAudioSignal(
        audioData: ByteArray,
        sampleRate: Int,
        channels: Int
    ): AudioSignalData = withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()

            // Convert byte array to float array for processing
            val audioSamples = convertBytesToFloats(audioData)

            // Parallel analysis of different audio aspects
            val qualityScore = analyzeAudioQuality(audioSamples, sampleRate)
            val voiceActivity = detectVoiceActivity(audioSamples, sampleRate)
            val backgroundNoise = analyzeBackgroundNoise(audioSamples, sampleRate)
            val emotionalTone = analyzeEmotionalTone(audioSamples, sampleRate)
            val breathingPattern = analyzeBreathingPattern(audioSamples, sampleRate)
            val stressLevel = analyzeVoiceStress(audioSamples, sampleRate)
            val speechClarity = analyzeSpeechClarity(audioSamples, sampleRate)
            val environmentalAudio = analyzeEnvironmentalAudio(audioSamples, sampleRate)

            val processingTime = System.currentTimeMillis() - startTime
            Timber.d("Audio intelligence analysis completed in ${processingTime}ms")

            AudioSignalData(
                qualityScore = qualityScore,
                voiceActivityLevel = voiceActivity,
                backgroundNoiseLevel = backgroundNoise,
                emotionalTone = emotionalTone,
                breathingPattern = breathingPattern,
                voiceStressLevel = stressLevel,
                speechClarity = speechClarity,
                environmentalAudio = environmentalAudio,
                confidence = calculateOverallConfidence(qualityScore, voiceActivity, speechClarity)
            )

        } catch (e: Exception) {
            Timber.e(e, "Error in audio intelligence processing")
            // Return minimal audio data on error
            AudioSignalData(
                qualityScore = 0.1f,
                voiceActivityLevel = 0f,
                backgroundNoiseLevel = 0.5f,
                emotionalTone = "unknown",
                breathingPattern = null,
                voiceStressLevel = 0.5f,
                speechClarity = 0.1f,
                environmentalAudio = null,
                confidence = 0.1f
            )
        }
    }

    /**
     * Analyze audio quality for optimal voice processing
     */
    private fun analyzeAudioQuality(samples: FloatArray, sampleRate: Int): Float {
        val signalPower = calculateSignalPower(samples)
        val snrRatio = calculateSNR(samples)
        val dynamicRange = calculateDynamicRange(samples)
        val distortion = analyzeDistortion(samples)

        // Combine quality metrics
        val powerScore = minOf(signalPower / 0.1f, 1.0f) // Normalize to 0.1 as good power
        val snrScore = minOf(snrRatio / 20f, 1.0f) // 20dB SNR as good quality
        val rangeScore = minOf(dynamicRange / 60f, 1.0f) // 60dB dynamic range as good
        val distortionScore = maxOf(1.0f - distortion, 0.0f)

        return (powerScore + snrScore + rangeScore + distortionScore) / 4f
    }

    /**
     * Detect voice activity and speech presence
     */
    private fun detectVoiceActivity(samples: FloatArray, sampleRate: Int): Float {
        val windowSize = (sampleRate * 0.025f).toInt() // 25ms windows
        val stepSize = windowSize / 2

        var activeFrames = 0
        var totalFrames = 0

        for (i in 0 until samples.size - windowSize step stepSize) {
            val window = samples.sliceArray(i until i + windowSize)
            val energy = calculateRMSEnergy(window)
            val zeroCrossingRate = calculateZeroCrossingRate(window)

            // Voice activity detection based on energy and ZCR
            if (energy > VOICE_ACTIVITY_THRESHOLD && zeroCrossingRate < 0.3f) {
                activeFrames++
            }
            totalFrames++
        }

        return if (totalFrames > 0) activeFrames.toFloat() / totalFrames else 0f
    }

    /**
     * Analyze background noise levels and characteristics
     */
    private fun analyzeBackgroundNoise(samples: FloatArray, sampleRate: Int): Float {
        // Identify non-speech segments for noise analysis
        val noiseSegments = identifyNoiseSegments(samples, sampleRate)

        if (noiseSegments.isEmpty()) {
            return 0.1f // Very low noise
        }

        val noiseLevel = noiseSegments.map { segment ->
            calculateRMSEnergy(segment)
        }.average().toFloat()

        // Normalize noise level (0.0 = silent, 1.0 = very noisy)
        return minOf(noiseLevel * 10f, 1.0f)
    }

    /**
     * Analyze emotional tone from voice characteristics
     */
    private fun analyzeEmotionalTone(samples: FloatArray, sampleRate: Int): String {
        val fundamentalFreq = estimateFundamentalFrequency(samples, sampleRate)
        val energyVariation = calculateEnergyVariation(samples)
        val spectralCentroid = calculateSpectralCentroid(samples, sampleRate)
        val formantAnalysis = analyzeFormants(samples, sampleRate)

        return classifyEmotionalTone(fundamentalFreq, energyVariation, spectralCentroid, formantAnalysis)
    }

    /**
     * Analyze breathing patterns from audio signal
     */
    private fun analyzeBreathingPattern(samples: FloatArray, sampleRate: Int): BreathingPatternData? {
        try {
            // Extract low-frequency components for breathing analysis
            val breathingSignal = extractBreathingComponent(samples, sampleRate)

            if (breathingSignal.isEmpty()) return null

            val breathingRate = calculateBreathingRate(breathingSignal, sampleRate)
            val breathingDepth = analyzeBreathingDepth(breathingSignal)
            val breathingRhythm = analyzeBreathingRhythm(breathingSignal)
            val oxygenationLevel = estimateOxygenationLevel(breathingSignal, breathingRate)

            return BreathingPatternData(
                breathingRate = breathingRate,
                breathingDepth = breathingDepth,
                breathingRhythm = breathingRhythm,
                oxygenationLevel = oxygenationLevel,
                confidence = 0.7f
            )

        } catch (e: Exception) {
            Timber.w(e, "Error analyzing breathing pattern")
            return null
        }
    }

    /**
     * Analyze voice stress indicators
     */
    private fun analyzeVoiceStress(samples: FloatArray, sampleRate: Int): Float {
        val jitter = calculateJitter(samples, sampleRate)
        val shimmer = calculateShimmer(samples)
        val microtremor = detectMicrotremor(samples, sampleRate)
        val harmoicNoiseRatio = calculateHarmonicNoiseRatio(samples, sampleRate)

        // Combine stress indicators
        val jitterScore = minOf(jitter * 2f, 1.0f)
        val shimmerScore = minOf(shimmer * 3f, 1.0f)
        val tremorScore = minOf(microtremor * 5f, 1.0f)
        val hnrScore = maxOf(1.0f - harmoicNoiseRatio / 20f, 0.0f)

        return (jitterScore + shimmerScore + tremorScore + hnrScore) / 4f
    }

    /**
     * Analyze speech clarity and intelligibility
     */
    private fun analyzeSpeechClarity(samples: FloatArray, sampleRate: Int): Float {
        val consonantClarity = analyzeConsonantClarity(samples, sampleRate)
        val vowelClarity = analyzeVowelClarity(samples, sampleRate)
        val articulationRate = calculateArticulationRate(samples, sampleRate)
        val spectralBalance = analyzeSpectralBalance(samples, sampleRate)

        return (consonantClarity + vowelClarity + articulationRate + spectralBalance) / 4f
    }

    /**
     * Analyze environmental audio context
     */
    private fun analyzeEnvironmentalAudio(samples: FloatArray, sampleRate: Int): EnvironmentalAudioData {
        val musicType = detectBackgroundMusic(samples, sampleRate)
        val ambientNoise = calculateAmbientNoiseLevel(samples)
        val acousticEnvironment = classifyAcousticEnvironment(samples, sampleRate)
        val distractions = identifyAudioDistractions(samples, sampleRate)

        return EnvironmentalAudioData(
            backgroundMusicType = musicType,
            ambientNoiseLevel = ambientNoise,
            acousticEnvironment = acousticEnvironment,
            potentialDistractions = distractions
        )
    }

    // Helper methods for audio processing

    private fun convertBytesToFloats(audioData: ByteArray): FloatArray {
        val byteBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(audioData.size / 2) // 16-bit samples

        for (i in floatArray.indices) {
            floatArray[i] = byteBuffer.short.toFloat() / Short.MAX_VALUE
        }

        return floatArray
    }

    private fun calculateSignalPower(samples: FloatArray): Float {
        return samples.map { it * it }.average().toFloat()
    }

    private fun calculateSNR(samples: FloatArray): Float {
        val signalPower = calculateSignalPower(samples)
        val noisePower = estimateNoisePower(samples)
        return 10f * log10(signalPower / maxOf(noisePower, 1e-10f))
    }

    private fun calculateDynamicRange(samples: FloatArray): Float {
        val maxLevel = samples.maxOrNull() ?: 0f
        val minLevel = samples.minOrNull() ?: 0f
        return 20f * log10(maxLevel / maxOf(abs(minLevel), 1e-10f))
    }

    private fun analyzeDistortion(samples: FloatArray): Float {
        // THD (Total Harmonic Distortion) estimation
        val fundamental = estimateFundamentalFrequency(samples, SAMPLE_RATE)
        return if (fundamental > 0) {
            calculateTHD(samples, fundamental, SAMPLE_RATE)
        } else 0.1f
    }

    private fun calculateRMSEnergy(samples: FloatArray): Float {
        val sumSquares = samples.map { it * it }.sum()
        return sqrt(sumSquares / samples.size)
    }

    private fun calculateZeroCrossingRate(samples: FloatArray): Float {
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0) != (samples[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / (samples.size - 1)
    }

    private fun identifyNoiseSegments(samples: FloatArray, sampleRate: Int): List<FloatArray> {
        val segments = mutableListOf<FloatArray>()
        val windowSize = sampleRate / 10 // 100ms windows

        for (i in 0 until samples.size - windowSize step windowSize) {
            val window = samples.sliceArray(i until i + windowSize)
            val energy = calculateRMSEnergy(window)
            val zcr = calculateZeroCrossingRate(window)

            // Identify as noise if low energy and high ZCR (non-speech characteristics)
            if (energy < VOICE_ACTIVITY_THRESHOLD * 0.5f && zcr > 0.3f) {
                segments.add(window)
            }
        }

        return segments
    }

    private fun estimateFundamentalFrequency(samples: FloatArray, sampleRate: Int): Float {
        // Autocorrelation-based pitch detection
        val autocorr = autocorrelation(samples)
        val minPeriod = sampleRate / 500 // 500 Hz max
        val maxPeriod = sampleRate / 50  // 50 Hz min

        var maxCorr = 0f
        var bestPeriod = 0

        for (period in minPeriod..minPeriod(maxPeriod, autocorr.size - 1)) {
            if (autocorr[period] > maxCorr) {
                maxCorr = autocorr[period]
                bestPeriod = period
            }
        }

        return if (bestPeriod > 0) sampleRate.toFloat() / bestPeriod else 0f
    }

    private fun calculateEnergyVariation(samples: FloatArray): Float {
        val windowSize = 1024
        val energies = mutableListOf<Float>()

        for (i in 0 until samples.size - windowSize step windowSize / 2) {
            val window = samples.sliceArray(i until i + windowSize)
            energies.add(calculateRMSEnergy(window))
        }

        if (energies.size < 2) return 0f

        val mean = energies.average().toFloat()
        val variance = energies.map { (it - mean).pow(2) }.average().toFloat()
        return sqrt(variance) / mean
    }

    private fun calculateSpectralCentroid(samples: FloatArray, sampleRate: Int): Float {
        val fft = performFFT(samples)
        val magnitudes = fft.map { it.magnitude() }

        var weightedSum = 0f
        var totalMagnitude = 0f

        for (i in magnitudes.indices) {
            val frequency = i * sampleRate.toFloat() / magnitudes.size
            weightedSum += frequency * magnitudes[i]
            totalMagnitude += magnitudes[i]
        }

        return if (totalMagnitude > 0) weightedSum / totalMagnitude else 0f
    }

    private fun analyzeFormants(samples: FloatArray, sampleRate: Int): List<Float> {
        // Simplified formant analysis - would use LPC in production
        val spectrum = performFFT(samples).map { it.magnitude() }
        val formants = mutableListOf<Float>()

        // Find peaks in spectrum (simplified formant detection)
        for (i in 1 until spectrum.size - 1) {
            if (spectrum[i] > spectrum[i - 1] && spectrum[i] > spectrum[i + 1]) {
                val frequency = i * sampleRate.toFloat() / spectrum.size
                if (frequency > 200 && frequency < 4000) { // Typical formant range
                    formants.add(frequency)
                }
            }
        }

        return formants.take(3) // First three formants
    }

    private fun classifyEmotionalTone(
        fundamentalFreq: Float,
        energyVariation: Float,
        spectralCentroid: Float,
        formants: List<Float>
    ): String {
        return when {
            fundamentalFreq > 200 && energyVariation > 0.3f -> "excited"
            fundamentalFreq < 100 && energyVariation < 0.1f -> "tired"
            spectralCentroid > 2000 && energyVariation > 0.2f -> "motivated"
            energyVariation > 0.5f -> "frustrated"
            fundamentalFreq in 120f..180f && energyVariation < 0.2f -> "confident"
            else -> "neutral"
        }
    }

    private fun extractBreathingComponent(samples: FloatArray, sampleRate: Int): FloatArray {
        // Low-pass filter to extract breathing component (typically 0.1-2 Hz)
        return lowPassFilter(samples, 2f, sampleRate)
    }

    private fun calculateBreathingRate(breathingSignal: FloatArray, sampleRate: Int): Float {
        // Find peaks in breathing signal to count breaths
        val peaks = findPeaks(breathingSignal, sampleRate / 4) // Minimum 250ms between peaks
        val duration = breathingSignal.size.toFloat() / sampleRate
        return (peaks.size * 60f) / duration // Convert to breaths per minute
    }

    private fun analyzeBreathingDepth(breathingSignal: FloatArray): String {
        val amplitude = breathingSignal.map { abs(it) }.maxOrNull() ?: 0f
        return when {
            amplitude > 0.1f -> "deep"
            amplitude > 0.05f -> "normal"
            else -> "shallow"
        }
    }

    private fun analyzeBreathingRhythm(breathingSignal: FloatArray): String {
        val peaks = findPeaks(breathingSignal, breathingSignal.size / 20)
        if (peaks.size < 3) return "irregular"

        val intervals = peaks.zipWithNext { a, b -> b - a }
        val avgInterval = intervals.average()
        val variance = intervals.map { (it - avgInterval).pow(2) }.average()
        val cv = sqrt(variance) / avgInterval

        return when {
            cv < 0.1 -> "regular"
            cv < 0.3 -> "slightly_irregular"
            else -> "irregular"
        }
    }

    private fun estimateOxygenationLevel(breathingSignal: FloatArray, breathingRate: Float): String {
        return when {
            breathingRate < 12 -> "concerning" // Too slow
            breathingRate > 40 -> "concerning" // Too fast
            breathingRate in 12f..20f -> "good"
            else -> "moderate"
        }
    }

    // Voice stress analysis methods
    private fun calculateJitter(samples: FloatArray, sampleRate: Int): Float {
        val periods = extractPitchPeriods(samples, sampleRate)
        if (periods.size < 3) return 0f

        val avgPeriod = periods.average()
        val jitterVariation = periods.map { abs(it - avgPeriod) / avgPeriod }.average()
        return jitterVariation.toFloat()
    }

    private fun calculateShimmer(samples: FloatArray): Float {
        val amplitudes = extractAmplitudeVariations(samples)
        if (amplitudes.size < 3) return 0f

        val avgAmplitude = amplitudes.average()
        val shimmerVariation = amplitudes.map { abs(it - avgAmplitude) / avgAmplitude }.average()
        return shimmerVariation.toFloat()
    }

    private fun detectMicrotremor(samples: FloatArray, sampleRate: Int): Float {
        // Detect 4-12 Hz tremor in voice
        val tremorBand = bandpassFilter(samples, 4f, 12f, sampleRate)
        return calculateRMSEnergy(tremorBand)
    }

    private fun calculateHarmonicNoiseRatio(samples: FloatArray, sampleRate: Int): Float {
        val harmonicPower = calculateHarmonicPower(samples, sampleRate)
        val noisePower = calculateNoisePower(samples, sampleRate)
        return 10f * log10(harmonicPower / maxOf(noisePower, 1e-10f))
    }

    // Utility methods for audio analysis

    private fun autocorrelation(samples: FloatArray): FloatArray {
        val result = FloatArray(samples.size)
        for (lag in result.indices) {
            var sum = 0f
            for (i in 0 until samples.size - lag) {
                sum += samples[i] * samples[i + lag]
            }
            result[lag] = sum
        }
        return result
    }

    private fun performFFT(samples: FloatArray): Array<ComplexNumber> {
        // Simplified FFT implementation - would use proper FFT library in production
        val n = samples.size
        val result = Array(n) { ComplexNumber(0f, 0f) }

        for (k in 0 until n) {
            var real = 0f
            var imag = 0f
            for (j in 0 until n) {
                val angle = -2f * PI.toFloat() * k * j / n
                real += samples[j] * cos(angle)
                imag += samples[j] * sin(angle)
            }
            result[k] = ComplexNumber(real, imag)
        }

        return result
    }

    private fun lowPassFilter(samples: FloatArray, cutoffHz: Float, sampleRate: Int): FloatArray {
        // Simple low-pass filter implementation
        val alpha = cutoffHz / (cutoffHz + sampleRate / (2 * PI.toFloat()))
        val filtered = FloatArray(samples.size)
        filtered[0] = samples[0]

        for (i in 1 until samples.size) {
            filtered[i] = alpha * samples[i] + (1 - alpha) * filtered[i - 1]
        }

        return filtered
    }

    private fun bandpassFilter(samples: FloatArray, lowHz: Float, highHz: Float, sampleRate: Int): FloatArray {
        val lowPass = lowPassFilter(samples, highHz, sampleRate)
        val highPass = highPassFilter(lowPass, lowHz, sampleRate)
        return highPass
    }

    private fun highPassFilter(samples: FloatArray, cutoffHz: Float, sampleRate: Int): FloatArray {
        val alpha = (sampleRate / (2 * PI.toFloat())) / (cutoffHz + sampleRate / (2 * PI.toFloat()))
        val filtered = FloatArray(samples.size)
        filtered[0] = samples[0]

        for (i in 1 until samples.size) {
            filtered[i] = alpha * (filtered[i - 1] + samples[i] - samples[i - 1])
        }

        return filtered
    }

    private fun findPeaks(signal: FloatArray, minDistance: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        var lastPeak = -minDistance

        for (i in 1 until signal.size - 1) {
            if (signal[i] > signal[i - 1] && signal[i] > signal[i + 1] && i - lastPeak >= minDistance) {
                peaks.add(i)
                lastPeak = i
            }
        }

        return peaks
    }

    private fun calculateOverallConfidence(quality: Float, voiceActivity: Float, clarity: Float): Float {
        return (quality + voiceActivity + clarity) / 3f
    }

    // Placeholder implementations for complex audio analysis
    private fun estimateNoisePower(samples: FloatArray): Float = 0.01f
    private fun calculateTHD(samples: FloatArray, fundamental: Float, sampleRate: Int): Float = 0.05f
    private fun extractPitchPeriods(samples: FloatArray, sampleRate: Int): List<Double> = emptyList()
    private fun extractAmplitudeVariations(samples: FloatArray): List<Double> = emptyList()
    private fun calculateHarmonicPower(samples: FloatArray, sampleRate: Int): Float = 1.0f
    private fun calculateNoisePower(samples: FloatArray, sampleRate: Int): Float = 0.1f
    private fun analyzeConsonantClarity(samples: FloatArray, sampleRate: Int): Float = 0.7f
    private fun analyzeVowelClarity(samples: FloatArray, sampleRate: Int): Float = 0.8f
    private fun calculateArticulationRate(samples: FloatArray, sampleRate: Int): Float = 0.6f
    private fun analyzeSpectralBalance(samples: FloatArray, sampleRate: Int): Float = 0.7f
    private fun detectBackgroundMusic(samples: FloatArray, sampleRate: Int): String? = null
    private fun calculateAmbientNoiseLevel(samples: FloatArray): Float = 0.3f
    private fun classifyAcousticEnvironment(samples: FloatArray, sampleRate: Int): String = "normal"
    private fun identifyAudioDistractions(samples: FloatArray, sampleRate: Int): List<String> = emptyList()

    // Supporting data class for complex numbers
    private data class ComplexNumber(val real: Float, val imag: Float) {
        fun magnitude(): Float = sqrt(real * real + imag * imag)
    }
}