package com.posecoach.app.multimodal

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.test.core.app.ApplicationProvider
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
 * Comprehensive test suite for MultiModalFusionEngine
 */
@RunWith(RobolectricTestRunner::class)
class MultiModalFusionEngineTest {

    @Mock
    private lateinit var mockPrivacyManager: EnhancedPrivacyManager

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var fusionEngine: MultiModalFusionEngine

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()

        // Setup privacy manager mocks
        whenever(mockPrivacyManager.isLandmarkUploadAllowed()).thenReturn(true)
        whenever(mockPrivacyManager.isImageUploadAllowed()).thenReturn(true)
        whenever(mockPrivacyManager.isAudioUploadAllowed()).thenReturn(true)
        whenever(mockPrivacyManager.isOfflineModeEnabled()).thenReturn(false)

        fusionEngine = MultiModalFusionEngine(
            context = context,
            lifecycleScope = testScope,
            privacyManager = mockPrivacyManager
        )
    }

    @After
    fun tearDown() {
        fusionEngine.shutdown()
    }

    @Test
    fun `test pose landmarks processing`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()

        // When
        fusionEngine.processPoseLandmarks(landmarks)

        // Then
        val insight = fusionEngine.insights.first()
        assertNotNull(insight)
        assertTrue(insight.insights.isNotEmpty())
        assertTrue(insight.confidence > 0f)
    }

    @Test
    fun `test pose landmarks with associated image`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()
        val bitmap = createTestBitmap()

        // When
        fusionEngine.processPoseLandmarks(landmarks, bitmap)

        // Then
        val insight = fusionEngine.insights.first()
        assertNotNull(insight)
        assertTrue(insight.insights.any { it.modality == "vision" })
    }

    @Test
    fun `test audio signal processing`() = testScope.runTest {
        // Given
        val audioData = createTestAudioData()
        val sampleRate = 44100
        val channels = 1

        // When
        fusionEngine.processAudioSignal(audioData, sampleRate, channels)

        // Then
        val insight = fusionEngine.insights.first()
        assertNotNull(insight)
        assertTrue(insight.insights.any { it.modality == "audio" })
    }

    @Test
    fun `test visual scene processing`() = testScope.runTest {
        // Given
        val image = createTestBitmap()
        val landmarks = createTestPoseLandmarks()

        // When
        fusionEngine.processVisualScene(image, landmarks)

        // Then
        val insight = fusionEngine.insights.first()
        assertNotNull(insight)
        assertTrue(insight.insights.any { it.modality == "vision" })
    }

    @Test
    fun `test multi-modal fusion with multiple inputs`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()
        val image = createTestBitmap()
        val audioData = createTestAudioData()

        // When
        fusionEngine.processPoseLandmarks(landmarks, image)
        fusionEngine.processAudioSignal(audioData, 44100, 1)

        // Then
        val insights = fusionEngine.insights.take(2).toList()
        assertEquals(2, insights.size)

        // Check that fusion combines multiple modalities
        val lastInsight = insights.last()
        assertTrue(lastInsight.insights.size >= 2) // Multiple modalities should be present
    }

    @Test
    fun `test privacy restrictions on pose processing`() = testScope.runTest {
        // Given
        whenever(mockPrivacyManager.isLandmarkUploadAllowed()).thenReturn(false)
        whenever(mockPrivacyManager.isOfflineModeEnabled()).thenReturn(true)

        val landmarks = createTestPoseLandmarks()

        // When
        fusionEngine.processPoseLandmarks(landmarks)

        // Then - should not process or generate insights due to privacy restrictions
        val insights = try {
            withTimeout(1000L) {
                fusionEngine.insights.first()
            }
        } catch (e: TimeoutCancellationException) {
            null
        }

        // Should either be null or have very low confidence
        if (insights != null) {
            assertTrue(insights.confidence < 0.3f)
        }
    }

    @Test
    fun `test privacy restrictions on image processing`() = testScope.runTest {
        // Given
        whenever(mockPrivacyManager.isImageUploadAllowed()).thenReturn(false)

        val image = createTestBitmap()
        val landmarks = createTestPoseLandmarks()

        // When
        fusionEngine.processVisualScene(image, landmarks)

        // Then
        val insight = fusionEngine.insights.first()
        assertNotNull(insight)
        // Should not contain visual insights
        assertFalse(insight.insights.any { it.modality == "vision" })
    }

    @Test
    fun `test performance metrics collection`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()

        // When
        repeat(5) {
            fusionEngine.processPoseLandmarks(landmarks)
        }

        // Then
        val metrics = fusionEngine.getPerformanceMetrics()
        assertNotNull(metrics)
        assertTrue(metrics["processedInputs"] as Long >= 5L)
        assertTrue(metrics["averageLatencyMs"] as Long >= 0L)
    }

    @Test
    fun `test temporal fusion of sequential inputs`() = testScope.runTest {
        // Given
        val landmarks1 = createTestPoseLandmarks(confidence = 0.8f)
        val landmarks2 = createTestPoseLandmarks(confidence = 0.9f)
        val landmarks3 = createTestPoseLandmarks(confidence = 0.7f)

        // When - process inputs in sequence
        fusionEngine.processPoseLandmarks(landmarks1)
        delay(100L)
        fusionEngine.processPoseLandmarks(landmarks2)
        delay(100L)
        fusionEngine.processPoseLandmarks(landmarks3)

        // Then - should generate temporal insights
        val insights = fusionEngine.insights.take(3).toList()
        assertTrue(insights.size >= 2)

        // Check for temporal pattern analysis
        val temporalInsight = insights.find { insight ->
            insight.insights.any { it.modality == "temporal" }
        }
        assertNotNull(temporalInsight)
    }

    @Test
    fun `test error handling with invalid input`() = testScope.runTest {
        // Given - invalid landmarks with empty data
        val invalidLandmarks = PoseLandmarkResult(
            landmarks = emptyList(),
            confidence = 0f,
            timestamp = System.currentTimeMillis()
        )

        // When
        fusionEngine.processPoseLandmarks(invalidLandmarks)

        // Then - should handle gracefully
        val insight = fusionEngine.insights.first()
        assertNotNull(insight)
        assertTrue(insight.confidence >= 0f) // Should not crash
    }

    @Test
    fun `test concurrent processing`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()
        val audioData = createTestAudioData()
        val image = createTestBitmap()

        // When - process multiple inputs concurrently
        val jobs = listOf(
            async { fusionEngine.processPoseLandmarks(landmarks) },
            async { fusionEngine.processAudioSignal(audioData, 44100, 1) },
            async { fusionEngine.processVisualScene(image) }
        )

        jobs.awaitAll()

        // Then - should handle concurrent processing without errors
        val insights = fusionEngine.insights.take(3).toList()
        assertTrue(insights.isNotEmpty())
        assertTrue(insights.all { it.confidence >= 0f })
    }

    @Test
    fun `test processing state management`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()

        // When
        assertEquals(MultiModalFusionEngine.ProcessingState.IDLE, fusionEngine.processingState.value)

        fusionEngine.processPoseLandmarks(landmarks)

        // Wait for processing to complete
        fusionEngine.insights.first()

        // Then
        // Processing state should return to IDLE after completion
        assertEquals(MultiModalFusionEngine.ProcessingState.IDLE, fusionEngine.processingState.value)
    }

    @Test
    fun `test insight confidence calculation`() = testScope.runTest {
        // Given
        val highConfidenceLandmarks = createTestPoseLandmarks(confidence = 0.95f)
        val lowConfidenceLandmarks = createTestPoseLandmarks(confidence = 0.3f)

        // When
        fusionEngine.processPoseLandmarks(highConfidenceLandmarks)
        val highConfidenceInsight = fusionEngine.insights.first()

        fusionEngine.processPoseLandmarks(lowConfidenceLandmarks)
        val lowConfidenceInsight = fusionEngine.insights.first()

        // Then
        assertTrue(highConfidenceInsight.confidence > lowConfidenceInsight.confidence)
        assertTrue(highConfidenceInsight.confidence > 0.7f)
        assertTrue(lowConfidenceInsight.confidence < 0.5f)
    }

    @Test
    fun `test buffer management and cleanup`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()

        // When - process many inputs to test buffer management
        repeat(20) { index ->
            val timestampedLandmarks = landmarks.copy(
                timestamp = System.currentTimeMillis() + index * 100L
            )
            fusionEngine.processPoseLandmarks(timestampedLandmarks)
        }

        // Then - should not crash and should manage memory properly
        val metrics = fusionEngine.getPerformanceMetrics()
        assertNotNull(metrics)
        assertTrue(metrics["bufferSize"] as Int >= 0)
    }

    @Test
    fun `test shutdown behavior`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()

        // When
        fusionEngine.processPoseLandmarks(landmarks)
        fusionEngine.shutdown()

        // Then - should stop processing and clean up resources
        val finalMetrics = fusionEngine.getPerformanceMetrics()
        assertNotNull(finalMetrics)

        // Attempting to process after shutdown should not crash
        fusionEngine.processPoseLandmarks(landmarks)
    }

    // Helper methods

    private fun createTestPoseLandmarks(confidence: Float = 0.8f): PoseLandmarkResult {
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
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createTestBitmap(): Bitmap {
        return Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
    }

    private fun createTestAudioData(): ByteArray {
        // Create test audio data (1 second of 44.1kHz mono audio)
        val sampleCount = 44100
        val audioData = ByteArray(sampleCount * 2) // 16-bit samples

        // Generate a simple sine wave
        for (i in 0 until sampleCount) {
            val sample = (32767 * kotlin.math.sin(2 * kotlin.math.PI * 440 * i / 44100)).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }
}

/**
 * Integration tests for MultiModalFusionEngine with real components
 */
@RunWith(RobolectricTestRunner::class)
class MultiModalFusionEngineIntegrationTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var privacyManager: EnhancedPrivacyManager
    private lateinit var fusionEngine: MultiModalFusionEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        privacyManager = EnhancedPrivacyManager(context)

        // Set balanced privacy level for testing
        privacyManager.setPrivacyLevel(EnhancedPrivacyManager.PrivacyLevel.BALANCED)

        fusionEngine = MultiModalFusionEngine(
            context = context,
            lifecycleScope = testScope,
            privacyManager = privacyManager
        )
    }

    @After
    fun tearDown() {
        fusionEngine.shutdown()
    }

    @Test
    fun `test end-to-end multi-modal processing`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()
        val image = createTestBitmap()
        val audioData = createTestAudioData()

        // When - process complete multi-modal scenario
        fusionEngine.processPoseLandmarks(landmarks, image)
        fusionEngine.processAudioSignal(audioData, 44100, 1)

        // Then
        val insights = fusionEngine.insights.take(2).toList()

        // Verify multi-modal insights are generated
        assertTrue(insights.isNotEmpty())
        assertTrue(insights.any { it.insights.any { insight -> insight.modality == "pose" } })

        // Verify performance is acceptable
        val metrics = fusionEngine.getPerformanceMetrics()
        assertTrue((metrics["averageLatencyMs"] as Long) < 1000L) // Less than 1 second
    }

    @Test
    fun `test privacy level changes during processing`() = testScope.runTest {
        // Given
        val landmarks = createTestPoseLandmarks()

        // When - start with balanced privacy
        fusionEngine.processPoseLandmarks(landmarks)
        val balancedInsight = fusionEngine.insights.first()

        // Change to maximum privacy
        privacyManager.setPrivacyLevel(EnhancedPrivacyManager.PrivacyLevel.MAXIMUM_PRIVACY)

        fusionEngine.processPoseLandmarks(landmarks)
        val maxPrivacyInsight = fusionEngine.insights.first()

        // Then
        assertTrue(balancedInsight.confidence >= maxPrivacyInsight.confidence)
        // Maximum privacy should significantly limit insights
        assertTrue(maxPrivacyInsight.insights.size <= balancedInsight.insights.size)
    }

    // Helper methods (same as above)
    private fun createTestPoseLandmarks(confidence: Float = 0.8f): PoseLandmarkResult {
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
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createTestBitmap(): Bitmap {
        return Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
    }

    private fun createTestAudioData(): ByteArray {
        val sampleCount = 44100
        val audioData = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val sample = (32767 * kotlin.math.sin(2 * kotlin.math.PI * 440 * i / 44100)).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }
}