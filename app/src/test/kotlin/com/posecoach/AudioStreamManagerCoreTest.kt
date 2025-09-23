package com.posecoach.app.livecoach.audio

import android.content.Context
import android.media.AudioRecord
import android.media.AudioTrack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.posecoach.app.livecoach.models.AudioChunk
import com.posecoach.app.livecoach.models.LiveApiMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test suite for refactored AudioStreamManager core functionality.
 * Tests core streaming functionality (<300 lines target).
 */
@RunWith(AndroidJUnit4::class)
class AudioStreamManagerCoreTest {

    private lateinit var audioStreamManager: AudioStreamManager
    private val mockContext = mockk<Context>()
    private val testScope = TestScope()
    private val mockAudioConfiguration = mockk<AudioConfiguration>()
    private val mockPermissionManager = mockk<AudioPermissionManager>()
    private val mockQualityMonitor = mockk<AudioQualityMonitor>()

    @Before
    fun setUp() {
        // Setup mocks
        every { mockPermissionManager.hasAudioPermission() } returns true
        every { mockAudioConfiguration.getInputConfiguration() } returns mockk {
            every { sampleRate } returns 16000
            every { channelConfig } returns 16
            every { audioFormat } returns 2
        }
        every { mockAudioConfiguration.getOutputConfiguration() } returns mockk {
            every { sampleRate } returns 24000
            every { channelConfig } returns 4
            every { audioFormat } returns 2
        }

        // This test will fail until AudioStreamManager is refactored
        audioStreamManager = AudioStreamManager(
            context = mockContext,
            coroutineScope = testScope,
            configuration = mockAudioConfiguration,
            permissionManager = mockPermissionManager,
            qualityMonitor = mockQualityMonitor
        )
    }

    @Test
    fun `should initialize with proper configuration`() = runTest {
        // When
        val isInitialized = audioStreamManager.isInitialized()

        // Then
        assertTrue(isInitialized)
        verify { mockAudioConfiguration.getInputConfiguration() }
        verify { mockAudioConfiguration.getOutputConfiguration() }
    }

    @Test
    fun `should start recording when permissions granted`() = runTest {
        // Given
        every { mockPermissionManager.hasAudioPermission() } returns true

        // When
        val result = audioStreamManager.startRecording()

        // Then
        assertTrue(result)
        assertTrue(audioStreamManager.isRecording())
    }

    @Test
    fun `should fail to start recording without permissions`() = runTest {
        // Given
        every { mockPermissionManager.hasAudioPermission() } returns false

        // When
        val result = audioStreamManager.startRecording()

        // Then
        assertFalse(result)
        assertFalse(audioStreamManager.isRecording())
    }

    @Test
    fun `should stop recording successfully`() = runTest {
        // Given
        every { mockPermissionManager.hasAudioPermission() } returns true
        audioStreamManager.startRecording()

        // When
        audioStreamManager.stopRecording()

        // Then
        assertFalse(audioStreamManager.isRecording())
    }

    @Test
    fun `should emit audio chunks when recording`() = runTest {
        // Given
        every { mockPermissionManager.hasAudioPermission() } returns true
        audioStreamManager.startRecording()

        // When
        // Simulate audio data
        val chunk = audioStreamManager.audioChunks.first()

        // Then
        assertNotNull(chunk)
        assertTrue(chunk.data.isNotEmpty())
    }

    @Test
    fun `should start playback for audio output`() = runTest {
        // When
        val result = audioStreamManager.startPlayback()

        // Then
        assertTrue(result)
        assertTrue(audioStreamManager.isPlaying())
    }

    @Test
    fun `should stop playback successfully`() = runTest {
        // Given
        audioStreamManager.startPlayback()

        // When
        audioStreamManager.stopPlayback()

        // Then
        assertFalse(audioStreamManager.isPlaying())
    }

    @Test
    fun `should play audio data through output stream`() = runTest {
        // Given
        audioStreamManager.startPlayback()
        val testAudioData = ByteArray(1024) { it.toByte() }

        // When
        audioStreamManager.playAudio(testAudioData)

        // Then
        verify { mockQualityMonitor.processAudioChunk(any()) }
    }

    @Test
    fun `should enable low latency mode`() = runTest {
        // When
        audioStreamManager.enableLowLatencyMode(true)

        // Then
        assertTrue(audioStreamManager.isLowLatencyEnabled())
        verify { mockAudioConfiguration.enableLowLatencyMode(true) }
    }

    @Test
    fun `should handle barge-in detection`() = runTest {
        // Given
        audioStreamManager.enableBargeInMode(true)
        every { mockPermissionManager.hasAudioPermission() } returns true
        audioStreamManager.startRecording()

        // When
        // Simulate loud audio input that should trigger barge-in
        val bargeInEvent = audioStreamManager.bargeInDetected.first()

        // Then
        assertTrue(bargeInEvent > 0)
    }

    @Test
    fun `should emit realtime input messages`() = runTest {
        // Given
        every { mockPermissionManager.hasAudioPermission() } returns true
        audioStreamManager.startRecording()

        // When
        val realtimeInput = audioStreamManager.realtimeInput.first()

        // Then
        assertEquals(LiveApiMessage.RealtimeInput::class, realtimeInput::class)
        assertNotNull(realtimeInput.mediaChunks)
    }

    @Test
    fun `should handle session lifecycle`() = runTest {
        // When
        audioStreamManager.startSession()
        val sessionActive = audioStreamManager.isSessionActive()
        audioStreamManager.endSession()

        // Then
        assertTrue(sessionActive)
        assertFalse(audioStreamManager.isSessionActive())
    }

    @Test
    fun `should clean up resources on dispose`() = runTest {
        // Given
        every { mockPermissionManager.hasAudioPermission() } returns true
        audioStreamManager.startRecording()
        audioStreamManager.startPlayback()

        // When
        audioStreamManager.dispose()

        // Then
        assertFalse(audioStreamManager.isRecording())
        assertFalse(audioStreamManager.isPlaying())
        assertFalse(audioStreamManager.isInitialized())
    }
}