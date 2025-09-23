package com.posecoach.app.livecoach.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import com.posecoach.app.livecoach.models.*
import timber.log.Timber

/**
 * AudioConfiguration manages audio settings and configuration.
 * Separated from AudioStreamManager for better modularity (<150 lines).
 *
 * Features:
 * - Input/output audio configuration management
 * - Audio profile support (quality levels)
 * - Low latency mode configuration
 * - Buffer size calculations
 * - Android 15+ specific settings
 */
class AudioConfiguration {

    companion object {
        // Default input configuration (for Gemini Live API)
        private const val DEFAULT_INPUT_SAMPLE_RATE = 16000
        private const val DEFAULT_INPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val DEFAULT_INPUT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Default output configuration (for playback)
        private const val DEFAULT_OUTPUT_SAMPLE_RATE = 24000
        private const val DEFAULT_OUTPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val DEFAULT_OUTPUT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Default buffer and streaming configuration
        private const val DEFAULT_BUFFER_SIZE_MULTIPLIER = 4
        private const val DEFAULT_CHUNK_DURATION_MS = 1000L
        private const val LOW_LATENCY_CHUNK_MS = 100L

        private const val TAG = "AudioConfiguration"
    }

    // Current configuration state
    private var currentProfile = AudioProfile.STANDARD
    private var isLowLatencyEnabled = false

    // Input configuration
    private var inputSampleRate = DEFAULT_INPUT_SAMPLE_RATE
    private var inputChannelConfig = DEFAULT_INPUT_CHANNEL_CONFIG
    private var inputAudioFormat = DEFAULT_INPUT_AUDIO_FORMAT
    private var inputBufferMultiplier = DEFAULT_BUFFER_SIZE_MULTIPLIER

    // Output configuration
    private var outputSampleRate = DEFAULT_OUTPUT_SAMPLE_RATE
    private var outputChannelConfig = DEFAULT_OUTPUT_CHANNEL_CONFIG
    private var outputAudioFormat = DEFAULT_OUTPUT_AUDIO_FORMAT
    private var outputBufferMultiplier = DEFAULT_BUFFER_SIZE_MULTIPLIER

    /**
     * Get current input configuration
     */
    fun getInputConfiguration(): AudioInputConfiguration {
        return AudioInputConfiguration(
            sampleRate = inputSampleRate,
            channelConfig = inputChannelConfig,
            audioFormat = inputAudioFormat,
            bufferSizeMultiplier = inputBufferMultiplier
        )
    }

    /**
     * Get current output configuration
     */
    fun getOutputConfiguration(): AudioOutputConfiguration {
        return AudioOutputConfiguration(
            sampleRate = outputSampleRate,
            channelConfig = outputChannelConfig,
            audioFormat = outputAudioFormat,
            bufferSizeMultiplier = outputBufferMultiplier
        )
    }

    /**
     * Calculate input buffer size
     */
    fun getInputBufferSize(): Int {
        val minBufferSize = AudioRecord.getMinBufferSize(
            inputSampleRate,
            inputChannelConfig,
            inputAudioFormat
        )
        return minBufferSize * inputBufferMultiplier
    }

    /**
     * Calculate output buffer size
     */
    fun getOutputBufferSize(): Int {
        val minBufferSize = AudioTrack.getMinBufferSize(
            outputSampleRate,
            outputChannelConfig,
            outputAudioFormat
        )
        return minBufferSize * outputBufferMultiplier
    }

    /**
     * Get minimum input buffer size
     */
    fun getMinInputBufferSize(): Int {
        return AudioRecord.getMinBufferSize(inputSampleRate, inputChannelConfig, inputAudioFormat)
    }

    /**
     * Get minimum output buffer size
     */
    fun getMinOutputBufferSize(): Int {
        return AudioTrack.getMinBufferSize(outputSampleRate, outputChannelConfig, outputAudioFormat)
    }

    /**
     * Set audio profile
     */
    fun setAudioProfile(profile: AudioProfile) {
        currentProfile = profile
        applyProfileSettings(profile)
        Timber.d("$TAG: Audio profile set to $profile")
    }

    /**
     * Enable/disable low latency mode
     */
    fun enableLowLatencyMode(enabled: Boolean) {
        isLowLatencyEnabled = enabled
        if (enabled) {
            inputBufferMultiplier = 2 // Smaller buffers for low latency
            outputBufferMultiplier = 2
        } else {
            resetBufferMultipliers()
        }
        Timber.d("$TAG: Low latency mode ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Check if low latency is enabled
     */
    fun isLowLatencyEnabled(): Boolean = isLowLatencyEnabled

    /**
     * Get chunk duration based on current settings
     */
    fun getChunkDurationMs(): Long {
        return if (isLowLatencyEnabled) LOW_LATENCY_CHUNK_MS else DEFAULT_CHUNK_DURATION_MS
    }

    /**
     * Set input sample rate with validation
     */
    fun setInputSampleRate(sampleRate: Int) {
        require(sampleRate > 0) { "Sample rate must be positive" }
        inputSampleRate = sampleRate
    }

    /**
     * Get quality monitoring configuration
     */
    fun getQualityConfiguration(): QualityConfiguration {
        return QualityConfiguration()
    }

    /**
     * Get barge-in configuration
     */
    fun getBargeInConfiguration(): BargeInConfiguration {
        return BargeInConfiguration()
    }

    /**
     * Get Android 15+ specific configuration
     */
    fun getAndroid15Configuration(): Android15Configuration {
        return Android15Configuration()
    }

    /**
     * Reset configuration to defaults
     */
    fun resetToDefaults() {
        currentProfile = AudioProfile.STANDARD
        isLowLatencyEnabled = false
        inputSampleRate = DEFAULT_INPUT_SAMPLE_RATE
        outputSampleRate = DEFAULT_OUTPUT_SAMPLE_RATE
        resetBufferMultipliers()
        Timber.d("$TAG: Configuration reset to defaults")
    }

    private fun applyProfileSettings(profile: AudioProfile) {
        when (profile) {
            AudioProfile.LOW_QUALITY -> {
                inputSampleRate = 8000
                outputSampleRate = 16000
                inputBufferMultiplier = 2
            }
            AudioProfile.STANDARD -> {
                inputSampleRate = DEFAULT_INPUT_SAMPLE_RATE
                outputSampleRate = DEFAULT_OUTPUT_SAMPLE_RATE
                inputBufferMultiplier = DEFAULT_BUFFER_SIZE_MULTIPLIER
            }
            AudioProfile.HIGH_QUALITY -> {
                inputSampleRate = 24000
                outputSampleRate = 48000
                inputBufferMultiplier = 6
            }
            AudioProfile.ULTRA_LOW_LATENCY -> {
                enableLowLatencyMode(true)
                inputBufferMultiplier = 1
                outputBufferMultiplier = 1
            }
        }
    }

    private fun resetBufferMultipliers() {
        inputBufferMultiplier = DEFAULT_BUFFER_SIZE_MULTIPLIER
        outputBufferMultiplier = DEFAULT_BUFFER_SIZE_MULTIPLIER
    }
}