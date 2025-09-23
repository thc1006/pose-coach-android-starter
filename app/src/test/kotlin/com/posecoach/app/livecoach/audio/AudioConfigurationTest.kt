package com.posecoach.app.livecoach.audio

import android.media.AudioFormat
import com.posecoach.app.livecoach.models.AudioProfile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test suite for AudioConfiguration following TDD principles.
 * Tests configuration management functionality (<150 lines target).
 */
class AudioConfigurationTest {

    private lateinit var audioConfiguration: AudioConfiguration

    @BeforeEach
    fun setUp() {
        // This test will fail until AudioConfiguration is implemented
        audioConfiguration = AudioConfiguration()
    }

    @Test
    fun `should provide default input configuration`() {
        // When
        val inputConfig = audioConfiguration.getInputConfiguration()

        // Then
        assertEquals(16000, inputConfig.sampleRate)
        assertEquals(AudioFormat.CHANNEL_IN_MONO, inputConfig.channelConfig)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, inputConfig.audioFormat)
    }

    @Test
    fun `should provide default output configuration`() {
        // When
        val outputConfig = audioConfiguration.getOutputConfiguration()

        // Then
        assertEquals(24000, outputConfig.sampleRate)
        assertEquals(AudioFormat.CHANNEL_OUT_MONO, outputConfig.channelConfig)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, outputConfig.audioFormat)
    }

    @Test
    fun `should calculate appropriate buffer sizes`() {
        // When
        val inputBufferSize = audioConfiguration.getInputBufferSize()
        val outputBufferSize = audioConfiguration.getOutputBufferSize()

        // Then
        assertTrue(inputBufferSize > 0)
        assertTrue(outputBufferSize > 0)
        assertTrue(inputBufferSize >= audioConfiguration.getMinInputBufferSize())
        assertTrue(outputBufferSize >= audioConfiguration.getMinOutputBufferSize())
    }

    @Test
    fun `should support different audio profiles`() {
        // Given
        audioConfiguration.setAudioProfile(AudioProfile.HIGH_QUALITY)

        // When
        val config = audioConfiguration.getInputConfiguration()

        // Then
        assertTrue(config.sampleRate >= 16000)
        assertTrue(config.bufferSizeMultiplier >= 2)
    }

    @Test
    fun `should support low latency mode`() {
        // Given
        audioConfiguration.enableLowLatencyMode(true)

        // When
        val chunkDuration = audioConfiguration.getChunkDurationMs()
        val isLowLatency = audioConfiguration.isLowLatencyEnabled()

        // Then
        assertTrue(isLowLatency)
        assertTrue(chunkDuration <= 100L) // Low latency chunk size
    }

    @Test
    fun `should validate configuration parameters`() {
        // Given
        val invalidSampleRate = 0

        // When & Then
        assertThrows<IllegalArgumentException> {
            audioConfiguration.setInputSampleRate(invalidSampleRate)
        }
    }

    @Test
    fun `should provide quality monitoring configuration`() {
        // When
        val qualityConfig = audioConfiguration.getQualityConfiguration()

        // Then
        assertTrue(qualityConfig.checkInterval > 0)
        assertTrue(qualityConfig.scoreThreshold > 0.0)
        assertTrue(qualityConfig.scoreThreshold < 1.0)
    }

    @Test
    fun `should support barge-in configuration`() {
        // When
        val bargeInConfig = audioConfiguration.getBargeInConfiguration()

        // Then
        assertTrue(bargeInConfig.threshold > 0)
        assertTrue(bargeInConfig.minDurationMs > 0)
        assertTrue(bargeInConfig.cooldownMs > 0)
    }

    @Test
    fun `should handle Android 15+ specific settings`() {
        // When
        val android15Config = audioConfiguration.getAndroid15Configuration()

        // Then
        assertTrue(android15Config.sessionTimeoutMs > 0)
        assertFalse(android15Config.enableEnhancedFeatures) // Default should be false
    }

    @Test
    fun `should reset to default configuration`() {
        // Given
        audioConfiguration.setAudioProfile(AudioProfile.HIGH_QUALITY)
        audioConfiguration.enableLowLatencyMode(true)

        // When
        audioConfiguration.resetToDefaults()

        // Then
        val config = audioConfiguration.getInputConfiguration()
        assertEquals(16000, config.sampleRate)
        assertFalse(audioConfiguration.isLowLatencyEnabled())
    }
}