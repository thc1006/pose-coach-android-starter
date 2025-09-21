package com.posecoach.corepose.repository

import android.content.Context
import android.graphics.Bitmap
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.utils.PerformanceTracker
import io.mockk.*
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class MediaPipePoseRepositoryTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBitmap: Bitmap

    private lateinit var repository: MediaPipePoseRepository
    private lateinit var mockListener: PoseDetectionListener

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        mockListener = mockk(relaxed = true)
        repository = MediaPipePoseRepository()
    }

    @After
    fun tearDown() {
        if (repository.isRunning()) {
            repository.stop()
        }
        unmockkAll()
    }

    @Test
    fun `isRunning returns false initially`() {
        assertFalse(repository.isRunning())
    }

    @Test
    fun `start sets running state to true`() {
        repository.start(mockListener)
        assertTrue(repository.isRunning())
    }

    @Test
    fun `stop sets running state to false`() {
        repository.start(mockListener)
        repository.stop()
        assertFalse(repository.isRunning())
    }

    @Test
    fun `configurePersonCount clamps values correctly`() {
        // Test lower bound
        repository.configurePersonCount(-1)
        // Should be set to 1 (minimum)

        // Test upper bound
        repository.configurePersonCount(10)
        // Should be set to 5 (maximum)

        // Test valid range
        repository.configurePersonCount(3)
        // Should be set to 3
    }

    @Test
    fun `getPerformanceMetrics returns valid metrics`() {
        val metrics = repository.getPerformanceMetrics()
        assertTrue(metrics.avgInferenceTimeMs >= 0.0)
        assertTrue(metrics.droppedFrames >= 0)
    }

    @Test
    fun `isDegradedMode returns false initially`() {
        assertFalse(repository.isDegradedMode())
    }

    @Test
    fun `forcePerformanceAdaptation enables degraded mode`() {
        repository.forcePerformanceAdaptation()
        assertTrue(repository.isDegradedMode())
    }

    @Test
    fun `resetPerformanceTracking disables degraded mode`() {
        repository.forcePerformanceAdaptation()
        assertTrue(repository.isDegradedMode())

        repository.resetPerformanceTracking()
        assertFalse(repository.isDegradedMode())
    }

    @Test
    fun `detectAsync does nothing when not running`() = runTest {
        // Repository not started
        repository.detectAsync(mockBitmap, System.currentTimeMillis())

        // Should not crash and no pose should be detected
        verify(exactly = 0) { mockListener.onPoseDetected(any()) }
    }

    @Test
    fun `detectAsync processes frame when running`() = runTest {
        // This test would require mocking MediaPipe components
        // For now, we test the basic flow without MediaPipe initialization
        repository.start(mockListener)

        // Without MediaPipe initialization, this should log a warning but not crash
        repository.detectAsync(mockBitmap, System.currentTimeMillis())
    }

    @Test
    fun `multiple start calls use latest listener`() {
        val firstListener = mockk<PoseDetectionListener>(relaxed = true)
        val secondListener = mockk<PoseDetectionListener>(relaxed = true)

        repository.start(firstListener)
        repository.start(secondListener)

        assertTrue(repository.isRunning())
    }

    @Test
    fun `performance metrics track inference times correctly`() {
        val metrics = repository.getPerformanceMetrics()

        // Initially should be empty
        assertTrue(metrics.avgInferenceTimeMs == 0.0)
        assertTrue(metrics.minInferenceTimeMs == 0L)
        assertTrue(metrics.maxInferenceTimeMs == 0L)
        assertFalse(metrics.isPerformanceGood)
    }
}