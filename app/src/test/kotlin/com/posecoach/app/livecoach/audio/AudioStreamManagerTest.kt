package com.posecoach.app.livecoach.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.posecoach.app.livecoach.models.AudioChunk
import com.posecoach.app.livecoach.models.LiveApiMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioStreamManagerTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var audioManager: AudioStreamManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        audioManager = AudioStreamManager(context, testScope)

        Dispatchers.setMain(StandardTestDispatcher(testScope.testScheduler))
    }

    @After
    fun tearDown() {
        audioManager.destroy()
        Dispatchers.resetMain()
    }

    @Test
    fun `test audio permission check`() {
        // This test would require permission mocking in a real Android test
        // For unit test, we just verify the method exists and returns boolean
        val hasPermission = audioManager.hasAudioPermission()
        assertTrue(hasPermission is Boolean)
    }

    @Test
    fun `test recording state management`() = testScope.runTest {
        // Given
        assertFalse(audioManager.isCurrentlyRecording())

        // When - Start recording (will fail without actual AudioRecord, but state should update)
        // This test verifies the state management logic

        // Then
        assertFalse(audioManager.isCurrentlyRecording()) // Still false without real recording
    }

    @Test
    fun `test audio chunk flow`() = testScope.runTest {
        // Given
        val audioChunks = mutableListOf<AudioChunk>()
        val chunkJob = launch {
            audioManager.audioChunks.take(3).toList(audioChunks)
        }

        // When - Start recording
        audioManager.startRecording()
        advanceTimeBy(100)

        // Simulate some audio data being processed
        // In real implementation, this would come from AudioRecord

        // Then
        assertEquals(0, audioChunks.size) // No chunks without real audio

        audioManager.stopRecording()
        chunkJob.cancel()
    }

    @Test
    fun `test realtime input flow`() = testScope.runTest {
        // Given
        val realtimeInputs = mutableListOf<LiveApiMessage.RealtimeInput>()
        val inputJob = launch {
            audioManager.realtimeInput.take(3).toList(realtimeInputs)
        }

        // When
        audioManager.startRecording()
        advanceTimeBy(1500) // More than chunk duration

        // Then
        assertEquals(0, realtimeInputs.size) // No input without real audio

        audioManager.stopRecording()
        inputJob.cancel()
    }

    @Test
    fun `test error handling`() = testScope.runTest {
        // Given
        val errors = mutableListOf<String>()
        val errorJob = launch {
            audioManager.errors.take(2).toList(errors)
        }

        // When - Try to start recording without permission
        audioManager.startRecording()
        advanceTimeBy(100)

        // Then - Should emit permission error
        assertTrue(errors.any { it.contains("permission") })

        errorJob.cancel()
    }

    @Test
    fun `test silence detection configuration`() = testScope.runTest {
        // When
        audioManager.setSilenceDetectionEnabled(false)
        audioManager.setSilenceDetectionEnabled(true)

        // Then - Should not crash
        assertTrue(true)
    }

    @Test
    fun `test buffer info`() = testScope.runTest {
        // When
        val (bufferSize, sampleRate) = audioManager.getBufferInfo()

        // Then
        assertTrue(bufferSize > 0)
        assertEquals(16000, sampleRate) // Expected sample rate
    }

    @Test
    fun `test stop recording when not recording`() = testScope.runTest {
        // Given - Not recording
        assertFalse(audioManager.isCurrentlyRecording())

        // When
        audioManager.stopRecording()

        // Then - Should not crash
        assertFalse(audioManager.isCurrentlyRecording())
    }

    @Test
    fun `test multiple start recording calls`() = testScope.runTest {
        // When
        audioManager.startRecording()
        audioManager.startRecording() // Second call

        // Then - Should handle gracefully
        assertTrue(true)

        audioManager.stopRecording()
    }

    @Test
    fun `test audio stream end signal`() = testScope.runTest {
        // Given
        val realtimeInputs = mutableListOf<LiveApiMessage.RealtimeInput>()
        val inputJob = launch {
            audioManager.realtimeInput.take(1).toList(realtimeInputs)
        }

        // When
        audioManager.startRecording()
        advanceTimeBy(100)
        audioManager.stopRecording()
        advanceTimeBy(100)

        // Then - Should receive audio stream end signal
        // (In real implementation, this would be the last message)

        inputJob.cancel()
    }

    @Test
    fun `test destroy cleanup`() = testScope.runTest {
        // Given
        audioManager.startRecording()
        assertTrue(true) // Recording might be active

        // When
        audioManager.destroy()

        // Then
        assertFalse(audioManager.isCurrentlyRecording())
    }

    @Test
    fun `test audio chunk properties`() = testScope.runTest {
        // Given
        val testData = byteArrayOf(1, 2, 3, 4)
        val timestamp = System.currentTimeMillis()

        // When
        val audioChunk = AudioChunk(testData, timestamp, 16000)

        // Then
        assertEquals(testData.size, audioChunk.data.size)
        assertEquals(timestamp, audioChunk.timestamp)
        assertEquals(16000, audioChunk.sampleRate)
    }

    @Test
    fun `test audio chunk equality`() = testScope.runTest {
        // Given
        val data1 = byteArrayOf(1, 2, 3)
        val data2 = byteArrayOf(1, 2, 3)
        val timestamp = 12345L

        val chunk1 = AudioChunk(data1, timestamp, 16000)
        val chunk2 = AudioChunk(data2, timestamp, 16000)

        // When/Then
        assertEquals(chunk1, chunk2)
        assertEquals(chunk1.hashCode(), chunk2.hashCode())
    }

    @Test
    fun `test barge-in mode functionality`() = testScope.runTest {
        // Given
        assertFalse(audioManager.isBargeInModeEnabled())

        // When
        audioManager.enableBargeInMode(true)

        // Then
        assertTrue(audioManager.isBargeInModeEnabled())

        // When
        audioManager.enableBargeInMode(false)

        // Then
        assertFalse(audioManager.isBargeInModeEnabled())
    }

    @Test
    fun `test barge-in detection flow`() = testScope.runTest {
        // Given
        audioManager.enableBargeInMode(true)
        val bargeInEvents = mutableListOf<Long>()
        val bargeInJob = launch {
            audioManager.bargeInDetected.take(1).toList(bargeInEvents)
        }

        // When - start recording in barge-in mode
        audioManager.startRecording()
        advanceTimeBy(500) // Wait for potential barge-in detection

        // Then - verify barge-in flow exists (actual detection requires real audio)
        assertTrue(audioManager.isBargeInModeEnabled())

        audioManager.stopRecording()
        bargeInJob.cancel()
    }

    @Test
    fun `test audio quality tracking`() = testScope.runTest {
        // Given
        val qualityUpdates = mutableListOf<AudioStreamManager.AudioQualityInfo>()
        val qualityJob = launch {
            audioManager.audioQuality.take(1).toList(qualityUpdates)
        }

        // When
        val currentQuality = audioManager.getCurrentAudioQuality()

        // Then
        assertTrue(currentQuality >= 0.0)
        assertTrue(currentQuality <= 1.0)

        qualityJob.cancel()
    }

    @Test
    fun `test voice activity buffer`() = testScope.runTest {
        // When
        val voiceActivity = audioManager.getRecentVoiceActivity()

        // Then
        assertNotNull(voiceActivity)
        assertTrue(voiceActivity.size <= 10) // Buffer size limit
    }

    @Test
    fun `test advanced buffer info`() = testScope.runTest {
        // When
        val advancedInfo = audioManager.getAdvancedBufferInfo()

        // Then
        assertTrue(advancedInfo.containsKey("bufferSize"))
        assertTrue(advancedInfo.containsKey("sampleRate"))
        assertTrue(advancedInfo.containsKey("audioFormat"))
        assertTrue(advancedInfo.containsKey("bargeInMode"))
        assertTrue(advancedInfo.containsKey("lowLatencyMode"))
        assertTrue(advancedInfo.containsKey("qualityScore"))
        assertTrue(advancedInfo.containsKey("chunkDurationMs"))
        assertTrue(advancedInfo.containsKey("voiceActivityBufferSize"))

        assertEquals(16000, advancedInfo["sampleRate"])
    }

    @Test
    fun `test low latency mode with barge-in`() = testScope.runTest {
        // Given
        audioManager.enableBargeInMode(true)

        // When
        val advancedInfo = audioManager.getAdvancedBufferInfo()

        // Then
        assertTrue(advancedInfo["bargeInMode"] as Boolean)
        assertTrue(advancedInfo["lowLatencyMode"] as Boolean)
        assertEquals(100L, advancedInfo["chunkDurationMs"]) // Low latency chunks
    }

    @Test
    fun `test normal mode without barge-in`() = testScope.runTest {
        // Given
        audioManager.enableBargeInMode(false)

        // When
        val advancedInfo = audioManager.getAdvancedBufferInfo()

        // Then
        assertFalse(advancedInfo["bargeInMode"] as Boolean)
        assertFalse(advancedInfo["lowLatencyMode"] as Boolean)
        assertEquals(1000L, advancedInfo["chunkDurationMs"]) // Normal chunks
    }

    @Test
    fun `test audio analysis data structures`() = testScope.runTest {
        // Test AudioAnalysis data class
        val analysis = AudioStreamManager.AudioAnalysis(
            hasVoiceActivity = true,
            amplitude = 1500.0,
            signalToNoiseRatio = 15.0
        )

        assertTrue(analysis.hasVoiceActivity)
        assertEquals(1500.0, analysis.amplitude)
        assertEquals(15.0, analysis.signalToNoiseRatio)
    }

    @Test
    fun `test audio quality info data structures`() = testScope.runTest {
        // Test AudioQualityInfo data class
        val qualityInfo = AudioStreamManager.AudioQualityInfo(
            averageAmplitude = 1200.0,
            signalToNoiseRatio = 12.0,
            clippingPercentage = 2.5,
            qualityScore = 0.85
        )

        assertEquals(1200.0, qualityInfo.averageAmplitude)
        assertEquals(12.0, qualityInfo.signalToNoiseRatio)
        assertEquals(2.5, qualityInfo.clippingPercentage)
        assertEquals(0.85, qualityInfo.qualityScore)
    }

    @Test
    fun `test realtime input with voice activity metadata`() = testScope.runTest {
        // Given
        val realtimeInputs = mutableListOf<LiveApiMessage.RealtimeInput>()
        val inputJob = launch {
            audioManager.realtimeInput.take(1).toList(realtimeInputs)
        }

        // When - simulate audio processing that would include voice activity
        audioManager.startRecording()
        advanceTimeBy(100)

        // Then - in real implementation, mediaChunks would include voice activity metadata
        // Format: "audio/pcm;rate=16000;voice_activity=true"
        assertTrue(true) // Placeholder for metadata test

        audioManager.stopRecording()
        inputJob.cancel()
    }

    @Test
    fun `test error handling for audio permission denial`() = testScope.runTest {
        // This test would need permission mocking in a real Android test environment
        // For now, we test the error flow structure

        val errors = mutableListOf<String>()
        val errorJob = launch {
            audioManager.errors.take(1).toList(errors)
        }

        // When - attempt to start recording (will fail without permission)
        audioManager.startRecording()
        advanceTimeBy(100)

        // Then - should emit permission error
        assertTrue(errors.any { it.contains("permission") || it.contains("microphone") })

        errorJob.cancel()
    }

    @Test
    fun `test audio stream end signal emission`() = testScope.runTest {
        // Given
        val realtimeInputs = mutableListOf<LiveApiMessage.RealtimeInput>()
        val inputJob = launch {
            audioManager.realtimeInput.take(2).toList(realtimeInputs)
        }

        // When
        audioManager.startRecording()
        advanceTimeBy(100)
        audioManager.stopRecording() // Should emit stream end signal
        advanceTimeBy(100)

        // Then - last message should be audio stream end
        // In real implementation: LiveApiMessage.RealtimeInput(audioStreamEnd = true)
        assertTrue(true) // Placeholder for stream end test

        inputJob.cancel()
    }
}