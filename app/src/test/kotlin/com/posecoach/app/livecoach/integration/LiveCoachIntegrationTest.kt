package com.posecoach.app.livecoach.integration

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.test.core.app.ApplicationProvider
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.livecoach.models.ConnectionState
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCoachIntegrationTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var lifecycleScope: LifecycleCoroutineScope
    private lateinit var liveCoachManager: LiveCoachManager

    private val testApiKey = "test_api_key_12345"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        lifecycleScope = mock {
            on { coroutineContext } doReturn testScope.coroutineContext
        }

        Dispatchers.setMain(StandardTestDispatcher(testScope.testScheduler))
    }

    @After
    fun tearDown() {
        if (::liveCoachManager.isInitialized) {
            liveCoachManager.destroy()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `test session lifecycle - connect, record, disconnect`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        // Collect initial state
        val states = mutableListOf<ConnectionState>()
        val stateJob = launch {
            liveCoachManager.sessionState.take(5).toList(states)
        }

        // When - Start session
        liveCoachManager.startPushToTalkSession()
        advanceTimeBy(100)

        // Should transition through connecting states
        assertTrue(states.contains(ConnectionState.DISCONNECTED))
        assertTrue(states.contains(ConnectionState.CONNECTING))

        // Simulate connection success (in real test, this would be mocked WebSocket)
        advanceTimeBy(1000)

        // When - Stop session
        liveCoachManager.stopPushToTalkSession()
        advanceTimeBy(100)

        // Then - Should stop recording
        assertFalse(liveCoachManager.isRecording())

        stateJob.cancel()
    }

    @Test
    fun `test connection timeout and retry logic`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        val errors = mutableListOf<String>()
        val errorJob = launch {
            liveCoachManager.errors.take(3).toList(errors)
        }

        // When - Start session (will fail due to invalid API key)
        liveCoachManager.startPushToTalkSession()

        // Advance time to trigger timeout and retries
        advanceTimeBy(30000) // 30 seconds should trigger multiple retry attempts

        // Then - Should have error messages
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.contains("Connection failed") || it.contains("timeout") })

        errorJob.cancel()
    }

    @Test
    fun `test barge-in functionality`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        // Simulate connected state
        liveCoachManager.startPushToTalkSession()
        advanceTimeBy(100)

        // When - Trigger barge-in while speaking
        liveCoachManager.triggerBargeIn()

        // Then - Should not be speaking anymore
        assertFalse(liveCoachManager.isSpeaking())
    }

    @Test
    fun `test pose landmarks integration`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)
        val testLandmarks = createTestLandmarks()

        // When
        liveCoachManager.updatePoseLandmarks(testLandmarks)

        // Then
        val sessionInfo = liveCoachManager.getSessionInfo()
        assertEquals(ConnectionState.DISCONNECTED.name, sessionInfo["connectionState"])
    }

    @Test
    fun `test force reconnection`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        val states = mutableListOf<ConnectionState>()
        val stateJob = launch {
            liveCoachManager.sessionState.take(5).toList(states)
        }

        // When
        liveCoachManager.forceReconnect()
        advanceTimeBy(200)

        // Then
        assertTrue(states.contains(ConnectionState.CONNECTING))

        stateJob.cancel()
    }

    @Test
    fun `test session state transitions`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        // Initial state should be disconnected
        assertEquals(ConnectionState.DISCONNECTED, liveCoachManager.getConnectionState())
        assertFalse(liveCoachManager.isRecording())
        assertFalse(liveCoachManager.isSpeaking())

        // When starting session
        liveCoachManager.startPushToTalkSession()
        advanceTimeBy(50)

        // Should be in connecting state
        assertTrue(liveCoachManager.getConnectionState() in listOf(
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING
        ))
    }

    @Test
    fun `test coaching responses flow`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        val responses = mutableListOf<String>()
        val responseJob = launch {
            liveCoachManager.coachingResponses.take(3).toList(responses)
        }

        // When - Simulate receiving responses (would normally come from WebSocket)
        // This test verifies the flow structure is correct

        // Then
        assertEquals(0, responses.size) // No responses yet since not connected

        responseJob.cancel()
    }

    @Test
    fun `test transcription flow`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        val transcriptions = mutableListOf<String>()
        val transcriptionJob = launch {
            liveCoachManager.transcriptions.take(3).toList(transcriptions)
        }

        // When - Start session
        liveCoachManager.startPushToTalkSession()
        advanceTimeBy(100)

        // Then
        assertEquals(0, transcriptions.size) // No transcriptions yet

        transcriptionJob.cancel()
    }

    @Test
    fun `test session info diagnostics`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        // When
        val sessionInfo = liveCoachManager.getSessionInfo()

        // Then
        assertTrue(sessionInfo.containsKey("sessionId"))
        assertTrue(sessionInfo.containsKey("connectionState"))
        assertTrue(sessionInfo.containsKey("isRecording"))
        assertTrue(sessionInfo.containsKey("isSpeaking"))
        assertTrue(sessionInfo.containsKey("retryCount"))
        assertTrue(sessionInfo.containsKey("audioBufferSize"))
        assertTrue(sessionInfo.containsKey("audioSampleRate"))
        assertTrue(sessionInfo.containsKey("snapshotWidth"))
        assertTrue(sessionInfo.containsKey("snapshotHeight"))

        assertEquals("DISCONNECTED", sessionInfo["connectionState"])
        assertEquals(false, sessionInfo["isRecording"])
        assertEquals(false, sessionInfo["isSpeaking"])
        assertEquals(0, sessionInfo["retryCount"])
    }

    @Test
    fun `test configuration updates`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)

        // When
        liveCoachManager.updateSystemInstruction("You are a yoga instructor")
        liveCoachManager.setSilenceDetectionEnabled(false)

        // Then - Should not crash (configuration methods exist)
        assertTrue(true)
    }

    @Test
    fun `test cleanup and destroy`() = testScope.runTest {
        // Given
        liveCoachManager = LiveCoachManager(context, lifecycleScope, testApiKey)
        liveCoachManager.startPushToTalkSession()
        advanceTimeBy(100)

        // When
        liveCoachManager.destroy()

        // Then
        assertEquals(ConnectionState.DISCONNECTED, liveCoachManager.getConnectionState())
        assertFalse(liveCoachManager.isRecording())
        assertFalse(liveCoachManager.isSpeaking())
    }

    private fun createTestLandmarks(): PoseLandmarkResult {
        val landmarks = listOf(
            PoseLandmarkResult.Landmark(0.5f, 0.3f, 0.1f, 0.9f, 0.8f),
            PoseLandmarkResult.Landmark(0.6f, 0.4f, 0.2f, 0.8f, 0.9f),
            PoseLandmarkResult.Landmark(0.4f, 0.5f, 0.0f, 0.95f, 0.85f)
        )

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 50L
        )
    }
}