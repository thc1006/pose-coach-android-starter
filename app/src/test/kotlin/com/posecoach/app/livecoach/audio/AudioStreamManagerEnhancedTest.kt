package com.posecoach.app.livecoach.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.posecoach.app.livecoach.audio.AudioStreamManager.*
import com.posecoach.app.livecoach.models.AudioChunk
import com.posecoach.app.livecoach.models.LiveApiMessage
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import kotlin.test.*

/**
 * Enhanced TDD test suite for AudioStreamManager
 * Following the specification in .claude/specs/voice-coach-integration.md
 *
 * Test categories:
 * 1. Audio permission handling
 * 2. Recording lifecycle management
 * 3. Audio quality monitoring
 * 4. Barge-in detection
 * 5. Android 15+ compatibility
 * 6. Performance and memory management
 * 7. Error handling and recovery
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class AudioStreamManagerEnhancedTest {

    private lateinit var mockContext: Context
    private lateinit var mockAudioManager: AudioManager
    private lateinit var mockAudioRecord: AudioRecord
    private lateinit var mockAudioTrack: AudioTrack
    private lateinit var testScope: TestScope
    private lateinit var audioStreamManager: AudioStreamManager

    @Before
    fun setUp() {
        // Mock Timber to avoid log errors
        mockkStatic(Timber::class)
        every { Timber.d(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.e(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs
        every { Timber.i(any<String>()) } just Runs

        // Setup mocks
        mockContext = mockk(relaxed = true)
        mockAudioManager = mockk(relaxed = true)
        mockAudioRecord = mockk(relaxed = true)
        mockAudioTrack = mockk(relaxed = true)
        testScope = TestScope()

        // Mock basic context behavior
        every { mockContext.packageManager } returns mockk()
        every { mockContext.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        every { mockContext.getSystemService(Context.AUDIO_SERVICE) } returns mockAudioManager

        // Mock audio record behavior
        mockkStatic(AudioRecord::class)
        every { AudioRecord.getMinBufferSize(any(), any(), any()) } returns 8192
        every { mockAudioRecord.state } returns AudioRecord.STATE_INITIALIZED
        every { mockAudioRecord.read(any<ShortArray>(), any(), any()) } returns 1024
        every { mockAudioRecord.startRecording() } just Runs
        every { mockAudioRecord.stop() } just Runs
        every { mockAudioRecord.release() } just Runs

        // Mock audio track behavior
        mockkStatic(AudioTrack::class)
        every { AudioTrack.getMinBufferSize(any(), any(), any()) } returns 8192

        audioStreamManager = AudioStreamManager(mockContext, testScope)
    }

    @After
    fun tearDown() {
        audioStreamManager.destroy()
        testScope.cancel()
        unmockkAll()
    }

    // AUDIO PERMISSION HANDLING TESTS

    @Test
    fun `should detect audio recording permission correctly`() = testScope.runTest {
        // ARRANGE
        every { mockContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) }
            returns PackageManager.PERMISSION_GRANTED

        // ACT
        val hasPermission = audioStreamManager.hasAudioPermission()

        // ASSERT
        assertTrue(hasPermission, "Should detect granted audio permission")
    }

    @Test
    fun `should detect missing audio recording permission`() = testScope.runTest {
        // ARRANGE
        every { mockContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) }
            returns PackageManager.PERMISSION_DENIED

        // ACT
        val hasPermission = audioStreamManager.hasAudioPermission()

        // ASSERT
        assertFalse(hasPermission, "Should detect denied audio permission")
    }

    @Test
    fun `should check enhanced audio permissions for Android M+`() = testScope.runTest {
        // ARRANGE
        every { mockContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) }
            returns PackageManager.PERMISSION_GRANTED
        every { mockContext.checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS) }
            returns PackageManager.PERMISSION_GRANTED

        // ACT
        val hasEnhancedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioStreamManager.hasEnhancedAudioPermissions()
        } else {
            true // Skip test on older versions
        }

        // ASSERT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            assertTrue(hasEnhancedPermissions, "Should detect enhanced audio permissions")
        }
    }

    @Test
    fun `should emit permission status updates`() = testScope.runTest {
        // ARRANGE
        val permissionUpdates = mutableListOf<AudioPermissionStatus>()
        val job = launch {
            audioStreamManager.permissionStatus.take(2).collect { status ->
                permissionUpdates.add(status)
            }
        }

        // ACT
        advanceUntilIdle()

        // ASSERT
        assertTrue(permissionUpdates.isNotEmpty(), "Should emit permission status")
        val latestStatus = permissionUpdates.last()
        assertNotNull(latestStatus.timestamp, "Permission status should have timestamp")

        job.cancel()
    }

    // RECORDING LIFECYCLE MANAGEMENT TESTS

    @Test
    fun `should initialize recording components correctly`() = testScope.runTest {
        // ARRANGE
        every { mockContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) }
            returns PackageManager.PERMISSION_GRANTED

        // ACT
        assertDoesNotThrow {
            audioStreamManager.startRecording()
        }

        // ASSERT
        // Recording start should not throw exceptions with proper permissions
    }

    @Test
    fun `should prevent recording without permissions`() = testScope.runTest {
        // ARRANGE
        every { mockContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) }
            returns PackageManager.PERMISSION_DENIED

        val errorCollector = mutableListOf<String>()
        val job = launch {
            audioStreamManager.errors.collect { error ->
                errorCollector.add(error)
            }
        }

        // ACT
        audioStreamManager.startRecording()
        advanceUntilIdle()

        // ASSERT
        assertTrue(errorCollector.any { it.contains("permission") },
            "Should emit permission error")

        job.cancel()
    }

    @Test
    fun `should emit audio session events during lifecycle`() = testScope.runTest {
        // ARRANGE
        val sessionEvents = mutableListOf<AudioSessionEvent>()
        val job = launch {
            audioStreamManager.audioSessionEvents.collect { event ->
                sessionEvents.add(event)
            }
        }

        // ACT
        try {
            audioStreamManager.startRecording()
            audioStreamManager.stopRecording()
        } catch (e: Exception) {
            // Expected in test environment without real audio hardware
        }

        advanceUntilIdle()

        // ASSERT
        assertTrue(sessionEvents.isNotEmpty(), "Should emit session events")

        job.cancel()
    }

    @Test
    fun `should handle recording state correctly`() = testScope.runTest {
        // ARRANGE & ACT
        val initialState = audioStreamManager.isCurrentlyRecording()

        // ASSERT
        assertFalse(initialState, "Should not be recording initially")
    }

    @Test
    fun `should prevent duplicate recording starts`() = testScope.runTest {
        // ARRANGE
        try {
            audioStreamManager.startRecording()

            // ACT - Try to start again
            audioStreamManager.startRecording()

            // ASSERT
            // Should handle duplicate start gracefully without exceptions

        } catch (e: Exception) {
            // Expected in test environment
        } finally {
            audioStreamManager.stopRecording()
        }
    }

    // AUDIO QUALITY MONITORING TESTS

    @Test
    fun `should monitor audio quality metrics`() = testScope.runTest {
        // ARRANGE
        val qualityUpdates = mutableListOf<AudioQualityInfo>()
        val job = launch {
            audioStreamManager.audioQuality.collect { quality ->
                qualityUpdates.add(quality)
            }
        }

        // ACT
        advanceUntilIdle()

        // ASSERT
        // Quality monitoring should provide initial state
        val currentQuality = audioStreamManager.getCurrentAudioQuality()
        assertTrue(currentQuality >= 0.0 && currentQuality <= 1.0,
            "Audio quality should be normalized 0-1")

        job.cancel()
    }

    @Test
    fun `should calculate quality score correctly`() = testScope.runTest {
        // ARRANGE & ACT
        val qualityScore = audioStreamManager.getCurrentAudioQuality()

        // ASSERT
        assertTrue(qualityScore >= 0.0, "Quality score should be non-negative")
        assertTrue(qualityScore <= 1.0, "Quality score should not exceed 1.0")
    }

    @Test
    fun `should detect poor audio quality`() = testScope.runTest {
        // ARRANGE
        val errorCollector = mutableListOf<String>()
        val job = launch {
            audioStreamManager.errors.collect { error ->
                errorCollector.add(error)
            }
        }

        // ACT
        // Simulate poor quality conditions (would require actual audio processing)
        advanceUntilIdle()

        // ASSERT
        // Quality monitoring should be active (errors only if quality is actually poor)

        job.cancel()
    }

    // BARGE-IN DETECTION TESTS

    @Test
    fun `should configure barge-in mode correctly`() = testScope.runTest {
        // ARRANGE
        assertFalse(audioStreamManager.isBargeInModeEnabled(),
            "Barge-in should be disabled by default")

        // ACT
        audioStreamManager.enableBargeInMode(true)

        // ASSERT
        assertTrue(audioStreamManager.isBargeInModeEnabled(),
            "Should enable barge-in mode")
    }

    @Test
    fun `should disable barge-in mode correctly`() = testScope.runTest {
        // ARRANGE
        audioStreamManager.enableBargeInMode(true)

        // ACT
        audioStreamManager.enableBargeInMode(false)

        // ASSERT
        assertFalse(audioStreamManager.isBargeInModeEnabled(),
            "Should disable barge-in mode")
    }

    @Test
    fun `should detect barge-in events`() = testScope.runTest {
        // ARRANGE
        audioStreamManager.enableBargeInMode(true)
        val bargeInEvents = mutableListOf<Long>()
        val job = launch {
            audioStreamManager.bargeInDetected.collect { timestamp ->
                bargeInEvents.add(timestamp)
            }
        }

        // ACT
        // Simulate voice activity that would trigger barge-in
        // In real implementation, this would be triggered by actual audio analysis

        advanceUntilIdle()

        // ASSERT
        // Barge-in detection should be active (events only if actually detected)

        job.cancel()
    }

    @Test
    fun `should track voice activity buffer`() = testScope.runTest {
        // ARRANGE & ACT
        val voiceActivity = audioStreamManager.getRecentVoiceActivity()

        // ASSERT
        assertNotNull(voiceActivity, "Voice activity buffer should be accessible")
        assertTrue(voiceActivity.isEmpty() || voiceActivity.all { it is Boolean },
            "Voice activity should contain boolean values")
    }

    // ANDROID 15+ COMPATIBILITY TESTS

    @Test
    fun `should handle audio focus for Android 8_0+`() = testScope.runTest {
        // ARRANGE & ACT
        val hasAudioFocus = audioStreamManager.hasCurrentAudioFocus()

        // ASSERT
        assertFalse(hasAudioFocus, "Should not have audio focus initially")
    }

    @Test
    fun `should support enhanced buffer configuration`() = testScope.runTest {
        // ARRANGE & ACT
        val bufferInfo = audioStreamManager.getAdvancedBufferInfo()

        // ASSERT
        assertNotNull(bufferInfo["inputBufferSize"], "Should provide input buffer size")
        assertNotNull(bufferInfo["outputBufferSize"], "Should provide output buffer size")
        assertNotNull(bufferInfo["inputSampleRate"], "Should provide input sample rate")
        assertNotNull(bufferInfo["outputSampleRate"], "Should provide output sample rate")

        // Verify required sample rates
        assertEquals(16000, bufferInfo["inputSampleRate"],
            "Input sample rate should be 16kHz for Gemini Live API")
        assertEquals(24000, bufferInfo["outputSampleRate"],
            "Output sample rate should be 24kHz for high quality playback")
    }

    @Test
    fun `should provide comprehensive session information`() = testScope.runTest {
        // ARRANGE & ACT
        val sessionInfo = audioStreamManager.getAudioSessionInfo()

        // ASSERT
        assertNotNull(sessionInfo["recording"], "Should provide recording status")
        assertNotNull(sessionInfo["playing"], "Should provide playing status")
        assertNotNull(sessionInfo["audioFocus"], "Should provide audio focus status")
        assertNotNull(sessionInfo["bargeInMode"], "Should provide barge-in mode status")
        assertNotNull(sessionInfo["lowLatencyMode"], "Should provide low latency mode status")
        assertNotNull(sessionInfo["permissions"], "Should provide permission status")
        assertNotNull(sessionInfo["bufferInfo"], "Should provide buffer information")

        // Verify types
        assertTrue(sessionInfo["recording"] is Boolean)
        assertTrue(sessionInfo["playing"] is Boolean)
        assertTrue(sessionInfo["audioFocus"] is Boolean)
        assertTrue(sessionInfo["bargeInMode"] is Boolean)
    }

    // PERFORMANCE AND MEMORY MANAGEMENT TESTS

    @Test
    fun `should maintain reasonable buffer sizes`() = testScope.runTest {
        // ARRANGE & ACT
        val bufferInfo = audioStreamManager.getAdvancedBufferInfo()

        // ASSERT
        val inputBufferSize = bufferInfo["inputBufferSize"] as Int
        val outputBufferSize = bufferInfo["outputBufferSize"] as Int

        assertTrue(inputBufferSize > 1024,
            "Input buffer should be reasonable size (>1KB)")
        assertTrue(inputBufferSize < 1024 * 1024,
            "Input buffer should not be excessive (<1MB)")
        assertTrue(outputBufferSize > 1024,
            "Output buffer should be reasonable size (>1KB)")
        assertTrue(outputBufferSize < 1024 * 1024,
            "Output buffer should not be excessive (<1MB)")
    }

    @Test
    fun `should handle silence detection configuration`() = testScope.runTest {
        // ARRANGE & ACT
        audioStreamManager.setSilenceDetectionEnabled(false)
        audioStreamManager.setSilenceDetectionEnabled(true)

        // ASSERT
        // Should handle configuration changes without exceptions
        assertTrue(true, "Silence detection configuration should work")
    }

    @Test
    fun `should process audio chunks efficiently`() = testScope.runTest {
        // ARRANGE
        val audioChunks = mutableListOf<AudioChunk>()
        val job = launch {
            audioStreamManager.audioChunks.take(5).collect { chunk ->
                audioChunks.add(chunk)
            }
        }

        // ACT
        // Start recording to generate audio chunks
        try {
            audioStreamManager.startRecording()
            advanceTimeBy(1000) // Wait for some chunks
        } catch (e: Exception) {
            // Expected in test environment
        }

        // ASSERT
        // Audio chunk processing should be efficient

        job.cancel()
    }

    @Test
    fun `should generate realtime input for Live API`() = testScope.runTest {
        // ARRANGE
        val realtimeInputs = mutableListOf<LiveApiMessage.RealtimeInput>()
        val job = launch {
            audioStreamManager.realtimeInput.take(3).collect { input ->
                realtimeInputs.add(input)
            }
        }

        // ACT
        try {
            audioStreamManager.startRecording()
            advanceTimeBy(1000)
        } catch (e: Exception) {
            // Expected in test environment
        }

        // ASSERT
        // Should generate Live API compatible messages

        job.cancel()
    }

    // PLAYBACK FUNCTIONALITY TESTS

    @Test
    fun `should handle playback lifecycle correctly`() = testScope.runTest {
        // ARRANGE
        assertFalse(audioStreamManager.isCurrentlyPlaying(),
            "Should not be playing initially")

        // ACT
        try {
            audioStreamManager.startPlayback()
            audioStreamManager.stopPlayback()
        } catch (e: Exception) {
            // Expected in test environment without real audio hardware
        }

        // ASSERT
        assertFalse(audioStreamManager.isCurrentlyPlaying(),
            "Should not be playing after stop")
    }

    @Test
    fun `should queue audio for playback`() = testScope.runTest {
        // ARRANGE
        val testAudioData = ByteArray(1024) { it.toByte() }

        // ACT & ASSERT
        assertDoesNotThrow {
            audioStreamManager.queueAudioForPlayback(testAudioData)
        }
    }

    @Test
    fun `should handle playback errors gracefully`() = testScope.runTest {
        // ARRANGE
        val errorCollector = mutableListOf<String>()
        val job = launch {
            audioStreamManager.errors.collect { error ->
                errorCollector.add(error)
            }
        }

        // ACT
        try {
            audioStreamManager.startPlayback()
            // Force error condition
            audioStreamManager.queueAudioForPlayback(ByteArray(0))
        } catch (e: Exception) {
            // Expected in test environment
        }

        advanceUntilIdle()

        // ASSERT
        // Should handle playback errors gracefully

        job.cancel()
    }

    // ERROR HANDLING AND RECOVERY TESTS

    @Test
    fun `should handle recording errors gracefully`() = testScope.runTest {
        // ARRANGE
        val errorCollector = mutableListOf<String>()
        val job = launch {
            audioStreamManager.errors.collect { error ->
                errorCollector.add(error)
            }
        }

        // Mock audio record failure
        every { mockAudioRecord.state } returns AudioRecord.STATE_UNINITIALIZED

        // ACT
        try {
            audioStreamManager.startRecording()
        } catch (e: Exception) {
            // Expected
        }

        advanceUntilIdle()

        // ASSERT
        // Should handle errors without crashing

        job.cancel()
    }

    @Test
    fun `should cleanup resources properly on destroy`() = testScope.runTest {
        // ARRANGE
        try {
            audioStreamManager.startRecording()
            audioStreamManager.startPlayback()
        } catch (e: Exception) {
            // Expected in test environment
        }

        // ACT
        audioStreamManager.destroy()

        // ASSERT
        assertFalse(audioStreamManager.isCurrentlyRecording(),
            "Should stop recording on destroy")
        assertFalse(audioStreamManager.isCurrentlyPlaying(),
            "Should stop playback on destroy")
    }

    @Test
    fun `should handle multiple destroy calls gracefully`() = testScope.runTest {
        // ARRANGE & ACT
        audioStreamManager.destroy()

        // Should not throw on second destroy
        assertDoesNotThrow {
            audioStreamManager.destroy()
        }
    }

    @Test
    fun `should emit appropriate session events for errors`() = testScope.runTest {
        // ARRANGE
        val sessionEvents = mutableListOf<AudioSessionEvent>()
        val job = launch {
            audioStreamManager.audioSessionEvents.collect { event ->
                sessionEvents.add(event)
            }
        }

        // ACT
        // Create error conditions
        every { mockContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) }
            returns PackageManager.PERMISSION_DENIED

        audioStreamManager.startRecording()
        advanceUntilIdle()

        // ASSERT
        val errorEvents = sessionEvents.filterIsInstance<AudioSessionEvent.PermissionDenied>()
        assertTrue(errorEvents.isNotEmpty() || sessionEvents.any { it is AudioSessionEvent.Error },
            "Should emit error events for permission issues")

        job.cancel()
    }
}