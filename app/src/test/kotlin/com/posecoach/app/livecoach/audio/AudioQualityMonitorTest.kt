package com.posecoach.app.livecoach.audio

import com.posecoach.app.livecoach.models.AudioQualityInfo
import com.posecoach.app.livecoach.models.AudioQualityMetrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test suite for AudioQualityMonitor following TDD principles.
 * Tests audio quality analysis functionality (<200 lines target).
 */
class AudioQualityMonitorTest {

    private lateinit var audioQualityMonitor: AudioQualityMonitor
    private val testSampleRate = 16000
    private val testChannelConfig = 1 // Mono

    @BeforeEach
    fun setUp() {
        // This test will fail until AudioQualityMonitor is implemented
        audioQualityMonitor = AudioQualityMonitor(testSampleRate, testChannelConfig)
    }

    @Test
    fun `should analyze audio quality from buffer`() = runTest {
        // Given
        val audioBuffer = generateTestAudioBuffer(1000) // Clean audio

        // When
        val quality = audioQualityMonitor.analyzeQuality(audioBuffer)

        // Then
        assertTrue(quality.score > 0.5)
        assertFalse(quality.hasNoise)
        assertTrue(quality.signalLevel > 0.0)
    }

    @Test
    fun `should detect noise in audio buffer`() = runTest {
        // Given
        val noisyBuffer = generateNoisyAudioBuffer(1000)

        // When
        val quality = audioQualityMonitor.analyzeQuality(noisyBuffer)

        // Then
        assertTrue(quality.hasNoise)
        assertTrue(quality.noiseLevel > 0.3)
    }

    @Test
    fun `should monitor quality continuously`() = runTest {
        // Given
        audioQualityMonitor.startMonitoring()
        val cleanBuffer = generateTestAudioBuffer(1000)

        // When
        audioQualityMonitor.processAudioChunk(cleanBuffer)
        val qualityUpdate = audioQualityMonitor.qualityUpdates.first()

        // Then
        assertTrue(qualityUpdate.score > 0.0)
        audioQualityMonitor.stopMonitoring()
    }

    @Test
    fun `should calculate signal to noise ratio`() = runTest {
        // Given
        val buffer = generateTestAudioBuffer(1000)

        // When
        val snr = audioQualityMonitor.calculateSNR(buffer)

        // Then
        assertTrue(snr > 10.0) // Good SNR for clean audio
    }

    @Test
    fun `should detect clipping in audio`() = runTest {
        // Given
        val clippedBuffer = generateClippedAudioBuffer(1000)

        // When
        val quality = audioQualityMonitor.analyzeQuality(clippedBuffer)

        // Then
        assertTrue(quality.isClipped)
        assertTrue(quality.clippingPercentage > 0.0)
    }

    @Test
    fun `should throw exception for invalid buffer`() {
        // Given
        val emptyBuffer = ShortArray(0)

        // When & Then
        assertThrows<IllegalArgumentException> {
            audioQualityMonitor.analyzeQuality(emptyBuffer)
        }
    }

    @Test
    fun `should update metrics over time`() = runTest {
        // Given
        audioQualityMonitor.startMonitoring()
        repeat(5) {
            val buffer = generateTestAudioBuffer(1000)
            audioQualityMonitor.processAudioChunk(buffer)
        }

        // When
        val metrics = audioQualityMonitor.getMetrics()

        // Then
        assertEquals(5, metrics.samplesProcessed)
        assertTrue(metrics.averageQuality > 0.0)
        audioQualityMonitor.stopMonitoring()
    }

    private fun generateTestAudioBuffer(size: Int): ShortArray {
        return ShortArray(size) { (Math.sin(it * 0.1) * 1000).toInt().toShort() }
    }

    private fun generateNoisyAudioBuffer(size: Int): ShortArray {
        return ShortArray(size) {
            (Math.sin(it * 0.1) * 1000 + Math.random() * 2000 - 1000).toInt().toShort()
        }
    }

    private fun generateClippedAudioBuffer(size: Int): ShortArray {
        return ShortArray(size) { Short.MAX_VALUE }
    }
}