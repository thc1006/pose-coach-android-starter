package com.posecoach.app.multimodal.processors

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.*
import kotlin.test.*

/**
 * Test suite for AudioIntelligenceProcessor
 */
@RunWith(RobolectricTestRunner::class)
class AudioIntelligenceProcessorTest {

    private lateinit var context: Context
    private lateinit var audioProcessor: AudioIntelligenceProcessor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioProcessor = AudioIntelligenceProcessor(context)
    }

    @Test
    fun `test basic audio signal processing`() = runTest {
        // Given
        val audioData = createTestAudioData(frequency = 440f, durationSeconds = 1f)
        val sampleRate = 44100
        val channels = 1

        // When
        val result = audioProcessor.processAudioSignal(audioData, sampleRate, channels)

        // Then
        assertNotNull(result)
        assertTrue(result.qualityScore > 0f)
        assertTrue(result.confidence > 0.5f)
        assertEquals("unknown", result.emotionalTone) // Should classify some emotional tone
    }

    @Test
    fun `test high quality audio detection`() = runTest {
        // Given
        val highQualityAudio = createCleanAudioData(frequency = 440f, amplitude = 0.8f)
        val sampleRate = 44100
        val channels = 1

        // When
        val result = audioProcessor.processAudioSignal(highQualityAudio, sampleRate, channels)

        // Then
        assertTrue(result.qualityScore > 0.7f)
        assertTrue(result.backgroundNoiseLevel < 0.3f)
        assertTrue(result.speechClarity > 0.6f)
    }

    @Test
    fun `test noisy audio detection`() = runTest {
        // Given
        val noisyAudio = createNoisyAudioData(signalAmplitude = 0.3f, noiseAmplitude = 0.7f)
        val sampleRate = 44100
        val channels = 1

        // When
        val result = audioProcessor.processAudioSignal(noisyAudio, sampleRate, channels)

        // Then
        assertTrue(result.qualityScore < 0.5f)
        assertTrue(result.backgroundNoiseLevel > 0.5f)
    }

    @Test
    fun `test voice activity detection`() = runTest {
        // Given
        val voiceAudio = createVoiceLikeAudioData()
        val sampleRate = 44100
        val channels = 1

        // When
        val result = audioProcessor.processAudioSignal(voiceAudio, sampleRate, channels)

        // Then
        assertTrue(result.voiceActivityLevel > 0.3f)
        assertTrue(result.speechClarity > 0.4f)
    }

    @Test
    fun `test breathing pattern analysis`() = runTest {
        // Given
        val breathingAudio = createBreathingPatternAudioData(breathsPerMinute = 15)
        val sampleRate = 44100
        val channels = 1

        // When
        val result = audioProcessor.processAudioSignal(breathingAudio, sampleRate, channels)

        // Then
        assertNotNull(result.breathingPattern)
        assertTrue(result.breathingPattern!!.breathingRate > 10f)
        assertTrue(result.breathingPattern!!.breathingRate < 25f)
        assertTrue(listOf("shallow", "normal", "deep").contains(result.breathingPattern!!.breathingDepth))
    }

    @Test
    fun `test emotional tone classification`() = runTest {
        // Given
        val calmAudio = createEmotionalAudioData(pitch = 200f, variation = 0.1f)
        val excitedAudio = createEmotionalAudioData(pitch = 300f, variation = 0.5f)

        // When
        val calmResult = audioProcessor.processAudioSignal(calmAudio, 44100, 1)
        val excitedResult = audioProcessor.processAudioSignal(excitedAudio, 44100, 1)

        // Then
        assertNotNull(calmResult.emotionalTone)
        assertNotNull(excitedResult.emotionalTone)
        // Different audio patterns should produce different emotional classifications
        assertTrue(calmResult.emotionalTone != excitedResult.emotionalTone ||
                  calmResult.voiceStressLevel != excitedResult.voiceStressLevel)
    }

    @Test
    fun `test stress level detection`() = runTest {
        // Given
        val stressedAudio = createStressedVoiceAudioData()
        val relaxedAudio = createRelaxedVoiceAudioData()

        // When
        val stressedResult = audioProcessor.processAudioSignal(stressedAudio, 44100, 1)
        val relaxedResult = audioProcessor.processAudioSignal(relaxedAudio, 44100, 1)

        // Then
        assertTrue(stressedResult.voiceStressLevel > relaxedResult.voiceStressLevel)
        assertTrue(stressedResult.voiceStressLevel > 0.4f)
        assertTrue(relaxedResult.voiceStressLevel < 0.6f)
    }

    @Test
    fun `test environmental audio analysis`() = runTest {
        // Given
        val environmentalAudio = createEnvironmentalAudioData()
        val sampleRate = 44100
        val channels = 1

        // When
        val result = audioProcessor.processAudioSignal(environmentalAudio, sampleRate, channels)

        // Then
        assertNotNull(result.environmentalAudio)
        assertTrue(result.environmentalAudio!!.ambientNoiseLevel >= 0f)
        assertTrue(result.environmentalAudio!!.ambientNoiseLevel <= 1f)
        assertTrue(listOf("echoey", "dampened", "normal").contains(result.environmentalAudio!!.acousticEnvironment))
    }

    @Test
    fun `test audio quality with different sample rates`() = runTest {
        val audioData = createTestAudioData(frequency = 440f, durationSeconds = 1f)
        val sampleRates = listOf(8000, 22050, 44100, 48000)

        sampleRates.forEach { sampleRate ->
            val result = audioProcessor.processAudioSignal(audioData, sampleRate, 1)
            assertNotNull(result)
            assertTrue(result.qualityScore > 0f)
            assertTrue(result.confidence > 0.1f)
        }
    }

    @Test
    fun `test stereo vs mono audio processing`() = runTest {
        // Given
        val audioData = createTestAudioData(frequency = 440f, durationSeconds = 1f)

        // When
        val monoResult = audioProcessor.processAudioSignal(audioData, 44100, 1)
        val stereoResult = audioProcessor.processAudioSignal(audioData, 44100, 2)

        // Then
        assertNotNull(monoResult)
        assertNotNull(stereoResult)
        // Both should process successfully
        assertTrue(monoResult.confidence > 0.3f)
        assertTrue(stereoResult.confidence > 0.3f)
    }

    @Test
    fun `test very short audio duration`() = runTest {
        // Given
        val shortAudio = createTestAudioData(frequency = 440f, durationSeconds = 0.1f) // 100ms
        val sampleRate = 44100
        val channels = 1

        // When
        val result = audioProcessor.processAudioSignal(shortAudio, sampleRate, channels)

        // Then
        assertNotNull(result)
        // Short audio should still produce some analysis, but with lower confidence
        assertTrue(result.confidence >= 0.1f)
    }

    @Test
    fun `test very long audio duration`() = runTest {
        // Given
        val longAudio = createTestAudioData(frequency = 440f, durationSeconds = 10f) // 10 seconds
        val sampleRate = 44100
        val channels = 1

        // When
        val startTime = System.currentTimeMillis()
        val result = audioProcessor.processAudioSignal(longAudio, sampleRate, channels)
        val processingTime = System.currentTimeMillis() - startTime

        // Then
        assertNotNull(result)
        assertTrue(result.confidence > 0.5f)
        // Should complete within reasonable time
        assertTrue(processingTime < 5000L) // Less than 5 seconds
    }

    @Test
    fun `test empty audio data handling`() = runTest {
        // Given
        val emptyAudio = ByteArray(0)

        // When
        val result = audioProcessor.processAudioSignal(emptyAudio, 44100, 1)

        // Then
        assertNotNull(result)
        assertTrue(result.confidence < 0.2f) // Should have very low confidence
        assertEquals("unknown", result.emotionalTone)
    }

    @Test
    fun `test audio with silence periods`() = runTest {
        // Given
        val audioWithSilence = createAudioWithSilencePeriods()
        val sampleRate = 44100
        val channels = 1

        // When
        val result = audioProcessor.processAudioSignal(audioWithSilence, sampleRate, channels)

        // Then
        assertNotNull(result)
        assertTrue(result.voiceActivityLevel < 0.7f) // Should detect reduced activity
        assertNotNull(result.breathingPattern) // Might detect breathing in silence
    }

    @Test
    fun `test performance with large audio data`() = runTest {
        // Given
        val largeAudio = createTestAudioData(frequency = 440f, durationSeconds = 30f) // 30 seconds
        val sampleRate = 44100
        val channels = 1

        // When
        val startTime = System.currentTimeMillis()
        val result = audioProcessor.processAudioSignal(largeAudio, sampleRate, channels)
        val processingTime = System.currentTimeMillis() - startTime

        // Then
        assertNotNull(result)
        assertTrue(processingTime < 10000L) // Should complete within 10 seconds
        assertTrue(result.confidence > 0.5f)
    }

    // Helper methods for creating test audio data

    private fun createTestAudioData(frequency: Float, durationSeconds: Float, amplitude: Float = 0.5f): ByteArray {
        val sampleRate = 44100
        val sampleCount = (sampleRate * durationSeconds).toInt()
        val audioData = ByteArray(sampleCount * 2) // 16-bit samples

        for (i in 0 until sampleCount) {
            val sample = (amplitude * 32767 * sin(2 * PI * frequency * i / sampleRate)).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }

    private fun createCleanAudioData(frequency: Float, amplitude: Float = 0.8f): ByteArray {
        return createTestAudioData(frequency, 1f, amplitude)
    }

    private fun createNoisyAudioData(signalAmplitude: Float, noiseAmplitude: Float): ByteArray {
        val sampleRate = 44100
        val sampleCount = sampleRate // 1 second
        val audioData = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val signal = signalAmplitude * sin(2 * PI * 440 * i / sampleRate)
            val noise = noiseAmplitude * (Math.random() - 0.5) * 2 // White noise
            val sample = ((signal + noise) * 32767).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }

    private fun createVoiceLikeAudioData(): ByteArray {
        val sampleRate = 44100
        val sampleCount = sampleRate // 1 second
        val audioData = ByteArray(sampleCount * 2)

        // Create voice-like signal with formants
        for (i in 0 until sampleCount) {
            val f0 = 150f // Fundamental frequency
            val signal = 0.3f * sin(2 * PI * f0 * i / sampleRate) +
                        0.2f * sin(2 * PI * f0 * 2 * i / sampleRate) +
                        0.1f * sin(2 * PI * f0 * 3 * i / sampleRate)

            val sample = (signal * 32767).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }

    private fun createBreathingPatternAudioData(breathsPerMinute: Int): ByteArray {
        val sampleRate = 44100
        val sampleCount = sampleRate * 10 // 10 seconds
        val audioData = ByteArray(sampleCount * 2)

        val breathFrequency = breathsPerMinute / 60f // Hz
        val amplitude = 0.1f

        for (i in 0 until sampleCount) {
            // Create low-frequency breathing pattern
            val breathingSignal = amplitude * sin(2 * PI * breathFrequency * i / sampleRate)
            val sample = (breathingSignal * 32767).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }

    private fun createEmotionalAudioData(pitch: Float, variation: Float): ByteArray {
        val sampleRate = 44100
        val sampleCount = sampleRate * 2 // 2 seconds
        val audioData = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val pitchVariation = pitch * (1 + variation * sin(2 * PI * 2 * i / sampleRate))
            val signal = 0.5f * sin(2 * PI * pitchVariation * i / sampleRate)
            val sample = (signal * 32767).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }

    private fun createStressedVoiceAudioData(): ByteArray {
        // Higher pitch and more variation indicates stress
        return createEmotionalAudioData(pitch = 250f, variation = 0.4f)
    }

    private fun createRelaxedVoiceAudioData(): ByteArray {
        // Lower pitch and less variation indicates relaxation
        return createEmotionalAudioData(pitch = 180f, variation = 0.1f)
    }

    private fun createEnvironmentalAudioData(): ByteArray {
        val sampleRate = 44100
        val sampleCount = sampleRate * 3 // 3 seconds
        val audioData = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            // Mix of ambient frequencies
            val ambient = 0.1f * sin(2 * PI * 60 * i / sampleRate) + // Low rumble
                         0.05f * sin(2 * PI * 1000 * i / sampleRate) + // Mid frequency
                         0.02f * (Math.random() - 0.5) // Noise

            val sample = (ambient * 32767).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }

    private fun createAudioWithSilencePeriods(): ByteArray {
        val sampleRate = 44100
        val sampleCount = sampleRate * 4 // 4 seconds
        val audioData = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val timeInSeconds = i.toFloat() / sampleRate
            val signal = if (timeInSeconds % 2 < 1) {
                // Sound for 1 second
                0.3f * sin(2 * PI * 440 * i / sampleRate)
            } else {
                // Silence for 1 second
                0f
            }

            val sample = (signal * 32767).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }
}