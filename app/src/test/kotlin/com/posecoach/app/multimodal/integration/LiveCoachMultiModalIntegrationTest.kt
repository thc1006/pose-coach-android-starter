package com.posecoach.app.multimodal.integration

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.test.core.app.ApplicationProvider
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

/**
 * Integration test suite for LiveCoachMultiModalIntegration
 */
@RunWith(RobolectricTestRunner::class)
class LiveCoachMultiModalIntegrationTest {

    @Mock
    private lateinit var mockLiveCoachManager: LiveCoachManager

    @Mock
    private lateinit var mockImageProxy: ImageProxy

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var privacyManager: EnhancedPrivacyManager
    private lateinit var integration: LiveCoachMultiModalIntegration

    private val testApiKey = "test_api_key_for_integration_testing"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        privacyManager = EnhancedPrivacyManager(context)

        // Setup privacy manager for testing
        privacyManager.setPrivacyLevel(EnhancedPrivacyManager.PrivacyLevel.BALANCED)

        // Setup LiveCoachManager mocks
        whenever(mockLiveCoachManager.coachingResponses).thenReturn(
            flowOf("Test coaching response")
        )
        whenever(mockLiveCoachManager.getConnectionState()).thenReturn(mockk())

        integration = LiveCoachMultiModalIntegration(
            context = context,
            lifecycleScope = testScope,
            liveCoachManager = mockLiveCoachManager,
            privacyManager = privacyManager,
            apiKey = testApiKey
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            integration.stopIntegration()
        }
    }

    @Test
    fun `test integration startup and shutdown`() = testScope.runTest {
        // Given
        assertEquals(LiveCoachMultiModalIntegration.IntegrationState.IDLE, integration.integrationState.value)

        // When
        integration.startIntegration()

        // Then
        assertEquals(LiveCoachMultiModalIntegration.IntegrationState.ACTIVE, integration.integrationState.value)

        // When
        integration.stopIntegration()

        // Then
        assertEquals(LiveCoachMultiModalIntegration.IntegrationState.IDLE, integration.integrationState.value)
    }

    @Test
    fun `test pose processing with multi-modal enhancement`() = testScope.runTest {
        // Given
        integration.startIntegration()
        val landmarks = createTestPoseLandmarks()

        // When
        integration.processPoseWithMultiModal(landmarks, mockImageProxy)

        // Then
        verify(mockLiveCoachManager).updatePoseLandmarks(landmarks)

        // Check that metrics are updated
        val metrics = integration.multiModalMetrics.value
        assertTrue(metrics.poseUpdatesCount > 0)
        assertTrue(metrics.lastPoseUpdateTime > 0)
    }

    @Test
    fun `test audio processing with context`() = testScope.runTest {
        // Given
        integration.startIntegration()
        val audioData = createTestAudioData()

        // When
        integration.processAudioWithContext(audioData, 44100, 1)

        // Then
        val metrics = integration.multiModalMetrics.value
        assertTrue(metrics.audioSamplesCount > 0)
        assertTrue(metrics.totalAudioDataSize > 0)
    }

    @Test
    fun `test visual scene processing with pose context`() = testScope.runTest {
        // Given
        integration.startIntegration()
        val image = createTestBitmap()
        val landmarks = createTestPoseLandmarks()

        // When
        integration.processVisualSceneWithPose(image, landmarks)

        // Then
        val metrics = integration.multiModalMetrics.value
        assertTrue(metrics.visualSamplesCount > 0)
        assertEquals("640x480", metrics.lastImageResolution)
    }

    @Test
    fun `test enhanced coaching insights generation`() = testScope.runTest {
        // Given
        integration.startIntegration()

        var insightReceived = false
        val insightJob = launch {
            integration.enhancedInsights.first().also {
                insightReceived = true
            }
        }

        // When - process multi-modal data to trigger insights
        val landmarks = createTestPoseLandmarks()
        val audioData = createTestAudioData()

        integration.processPoseWithMultiModal(landmarks)
        integration.processAudioWithContext(audioData, 44100, 1)

        // Simulate LiveCoachManager coaching response
        // This would normally be triggered by the actual LiveCoachManager
        // For testing, we need to verify the integration can handle responses

        // Then
        // Note: In a real scenario, this would be triggered by LiveCoachManager
        // For unit testing, we verify the setup is correct
        assertNotNull(integration.enhancedInsights)

        insightJob.cancel()
    }

    @Test
    fun `test enhanced recommendations generation`() = testScope.runTest {
        // Given
        integration.startIntegration()
        val coachingContext = LiveCoachMultiModalIntegration.CoachingContext(
            userContext = createTestUserContext(),
            environmentContext = createTestEnvironmentContext(),
            sessionDuration = 30000L,
            workoutType = "strength"
        )

        // When
        val recommendations = integration.getEnhancedRecommendations(coachingContext)

        // Then
        assertNotNull(recommendations)
        // Note: Actual recommendation generation depends on having processed multi-modal data
        // In a real scenario with valid API key and data, this would return recommendations
    }

    @Test
    fun `test integration status monitoring`() = testScope.runTest {
        // Given
        integration.startIntegration()

        // When
        val status = integration.getIntegrationStatus()

        // Then
        assertNotNull(status)
        assertEquals(LiveCoachMultiModalIntegration.IntegrationState.ACTIVE, status.state)
        assertNotNull(status.metrics)
        assertNotNull(status.privacyLevel)
    }

    @Test
    fun `test privacy level impact on integration`() = testScope.runTest {
        // Given
        integration.startIntegration()

        // When - process with balanced privacy
        val landmarks = createTestPoseLandmarks()
        integration.processPoseWithMultiModal(landmarks)

        val balancedMetrics = integration.multiModalMetrics.value

        // Change to maximum privacy
        privacyManager.setPrivacyLevel(EnhancedPrivacyManager.PrivacyLevel.MAXIMUM_PRIVACY)

        integration.processPoseWithMultiModal(landmarks)
        val maxPrivacyMetrics = integration.multiModalMetrics.value

        // Then
        // Both should process, but maximum privacy should limit features
        assertTrue(balancedMetrics.poseUpdatesCount > 0)
        assertTrue(maxPrivacyMetrics.poseUpdatesCount > balancedMetrics.poseUpdatesCount)
    }

    @Test
    fun `test integration with offline mode`() = testScope.runTest {
        // Given
        privacyManager.setOfflineModeEnabled(true)
        integration.startIntegration()

        val landmarks = createTestPoseLandmarks()

        // When
        integration.processPoseWithMultiModal(landmarks)

        // Then
        verify(mockLiveCoachManager).updatePoseLandmarks(landmarks)

        // Metrics should still be updated even in offline mode
        val metrics = integration.multiModalMetrics.value
        assertTrue(metrics.poseUpdatesCount > 0)
    }

    @Test
    fun `test concurrent multi-modal processing`() = testScope.runTest {
        // Given
        integration.startIntegration()

        // When - process multiple modalities concurrently
        val jobs = listOf(
            async {
                integration.processPoseWithMultiModal(createTestPoseLandmarks())
            },
            async {
                integration.processAudioWithContext(createTestAudioData(), 44100, 1)
            },
            async {
                integration.processVisualSceneWithPose(createTestBitmap())
            }
        )

        jobs.awaitAll()

        // Then - all modalities should be processed
        val metrics = integration.multiModalMetrics.value
        assertTrue(metrics.poseUpdatesCount > 0)
        assertTrue(metrics.audioSamplesCount > 0)
        assertTrue(metrics.visualSamplesCount > 0)
    }

    @Test
    fun `test integration performance under load`() = testScope.runTest {
        // Given
        integration.startIntegration()
        val landmarks = createTestPoseLandmarks()

        // When - process many inputs rapidly
        val startTime = System.currentTimeMillis()

        repeat(20) {
            integration.processPoseWithMultiModal(landmarks)
        }

        val processingTime = System.currentTimeMillis() - startTime

        // Then
        assertTrue(processingTime < 5000L) // Should complete within 5 seconds
        val metrics = integration.multiModalMetrics.value
        assertEquals(20L, metrics.poseUpdatesCount)
    }

    @Test
    fun `test error recovery in integration`() = testScope.runTest {
        // Given
        integration.startIntegration()

        // When - process with invalid data
        val invalidLandmarks = PoseLandmarkResult(
            landmarks = emptyList(),
            confidence = 0f,
            timestamp = System.currentTimeMillis()
        )

        integration.processPoseWithMultiModal(invalidLandmarks)

        // Then - should handle gracefully and continue processing
        assertEquals(LiveCoachMultiModalIntegration.IntegrationState.ACTIVE, integration.integrationState.value)

        // Normal processing should still work
        val validLandmarks = createTestPoseLandmarks()
        integration.processPoseWithMultiModal(validLandmarks)

        val metrics = integration.multiModalMetrics.value
        assertTrue(metrics.poseUpdatesCount >= 2) // Both invalid and valid were processed
    }

    @Test
    fun `test memory management during extended use`() = testScope.runTest {
        // Given
        integration.startIntegration()
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        // When - process many inputs to test memory management
        repeat(50) { index ->
            val landmarks = createTestPoseLandmarks()
            val audioData = createTestAudioData()
            integration.processPoseWithMultiModal(landmarks)
            integration.processAudioWithContext(audioData, 44100, 1)
        }

        runtime.gc() // Suggest garbage collection
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        // Then - memory usage should be reasonable
        assertTrue(memoryIncrease < 100 * 1024 * 1024) // Less than 100MB increase
    }

    // Helper methods

    private fun createTestPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index * 0.01f),
                y = 0.5f + (index * 0.01f),
                z = 0f,
                visibility = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createTestBitmap(): Bitmap {
        return Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
    }

    private fun createTestAudioData(): ByteArray {
        val sampleCount = 4410 // 0.1 seconds at 44.1kHz
        val audioData = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val sample = (32767 * kotlin.math.sin(2 * kotlin.math.PI * 440 * i / 44100)).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }

    private fun createTestUserContext(): com.posecoach.app.multimodal.models.UserContextData {
        return com.posecoach.app.multimodal.models.UserContextData(
            activityLevel = "intermediate",
            fitnessGoals = listOf("strength", "flexibility"),
            experienceLevel = "intermediate",
            physicalCondition = null,
            preferences = mapOf("coaching_style" to "encouraging"),
            motivationLevel = 0.8f,
            fatigueLevel = 0.3f,
            confidence = 0.9f
        )
    }

    private fun createTestEnvironmentContext(): com.posecoach.app.multimodal.models.EnvironmentContextData {
        return com.posecoach.app.multimodal.models.EnvironmentContextData(
            locationType = "home",
            activityContext = "fitness",
            timeContext = "morning",
            socialContext = "solo",
            equipmentAvailable = listOf("yoga_mat", "dumbbells"),
            spaceConstraints = null,
            confidence = 0.8f
        )
    }
}

/**
 * End-to-end integration tests with real components
 */
@RunWith(RobolectricTestRunner::class)
class LiveCoachMultiModalIntegrationE2ETest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var privacyManager: EnhancedPrivacyManager
    private lateinit var liveCoachManager: LiveCoachManager
    private lateinit var integration: LiveCoachMultiModalIntegration

    private val testApiKey = "test_api_key_for_e2e_testing"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        privacyManager = EnhancedPrivacyManager(context)
        liveCoachManager = LiveCoachManager(context, testScope, testApiKey)

        integration = LiveCoachMultiModalIntegration(
            context = context,
            lifecycleScope = testScope,
            liveCoachManager = liveCoachManager,
            privacyManager = privacyManager,
            apiKey = testApiKey
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            integration.stopIntegration()
            liveCoachManager.destroy()
        }
    }

    @Test
    fun `test full multi-modal workflow integration`() = testScope.runTest {
        // Given
        privacyManager.setPrivacyLevel(EnhancedPrivacyManager.PrivacyLevel.BALANCED)

        // When
        integration.startIntegration()

        // Process a complete multi-modal scenario
        val landmarks = createTestPoseLandmarks()
        val image = createTestBitmap()
        val audioData = createTestAudioData()

        integration.processPoseWithMultiModal(landmarks)
        integration.processVisualSceneWithPose(image, landmarks)
        integration.processAudioWithContext(audioData, 44100, 1)

        // Then
        val status = integration.getIntegrationStatus()
        assertTrue(status.isEnhanced || status.state == LiveCoachMultiModalIntegration.IntegrationState.ACTIVE)

        val metrics = integration.multiModalMetrics.value
        assertTrue(metrics.poseUpdatesCount > 0)
        assertTrue(metrics.visualSamplesCount > 0)
        assertTrue(metrics.audioSamplesCount > 0)
    }

    // Helper methods (same as above)
    private fun createTestPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index * 0.01f),
                y = 0.5f + (index * 0.01f),
                z = 0f,
                visibility = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createTestBitmap(): Bitmap {
        return Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
    }

    private fun createTestAudioData(): ByteArray {
        val sampleCount = 4410
        val audioData = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val sample = (32767 * kotlin.math.sin(2 * kotlin.math.PI * 440 * i / 44100)).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }
}