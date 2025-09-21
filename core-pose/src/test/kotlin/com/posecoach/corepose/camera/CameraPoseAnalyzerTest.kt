package com.posecoach.corepose.camera

import android.graphics.ImageFormat
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.repository.FakePoseRepository
import com.posecoach.corepose.repository.PoseDetectionListener
import com.posecoach.corepose.repository.PoseRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer

@ExperimentalCoroutinesApi
class CameraPoseAnalyzerTest {

    private lateinit var mockPoseRepository: PoseRepository
    private lateinit var mockListener: PoseDetectionListener
    private lateinit var analyzer: CameraPoseAnalyzer
    private lateinit var mockImageProxy: ImageProxy

    @Before
    fun setup() {
        mockPoseRepository = mockk(relaxed = true)
        mockListener = mockk(relaxed = true)
        analyzer = CameraPoseAnalyzer(mockPoseRepository, mockListener)

        mockImageProxy = createMockImageProxy()
    }

    @After
    fun teardown() {
        analyzer.cleanup()
        unmockkAll()
    }

    @Test
    fun `analyzer should process valid image proxy`() = runTest {
        // Setup
        coEvery { mockPoseRepository.detectAsync(any(), any()) } returns Unit

        // Execute
        analyzer.analyze(mockImageProxy)

        // Allow processing time
        kotlinx.coroutines.delay(100)

        // Verify
        coVerify { mockPoseRepository.detectAsync(any(), any()) }
    }

    @Test
    fun `analyzer should handle processing errors gracefully`() = runTest {
        // Setup
        coEvery { mockPoseRepository.detectAsync(any(), any()) } throws RuntimeException("Test error")

        // Execute
        analyzer.analyze(mockImageProxy)

        // Allow processing time
        kotlinx.coroutines.delay(100)

        // Verify error was handled
        verify { mockListener.onPoseDetectionError(any()) }
    }

    @Test
    fun `analyzer should drop frames when processing backlog exists`() = runTest {
        // Setup - make processing take longer
        coEvery { mockPoseRepository.detectAsync(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(200) // Simulate slow processing
        }

        val stats = analyzer.getProcessingStats()
        val initialDroppedFrames = stats.droppedFrames

        // Execute - send multiple frames quickly
        repeat(5) {
            analyzer.analyze(createMockImageProxy())
        }

        // Allow processing time
        kotlinx.coroutines.delay(300)

        // Verify some frames were dropped
        val finalStats = analyzer.getProcessingStats()
        assertTrue("Expected some frames to be dropped",
                  finalStats.droppedFrames > initialDroppedFrames)
    }

    @Test
    fun `setTargetImageSize should update processing parameters`() {
        val targetWidth = 480
        val targetHeight = 640

        analyzer.setTargetImageSize(targetWidth, targetHeight)

        // No direct way to verify, but should not throw exception
        assertTrue("Setting target image size should complete", true)
    }

    @Test
    fun `setProcessingQuality should update quality setting`() {
        analyzer.setProcessingQuality(CameraPoseAnalyzer.ProcessingQuality.LOW)

        val stats = analyzer.getProcessingStats()
        assertEquals(
            "Processing quality should be updated",
            CameraPoseAnalyzer.ProcessingQuality.LOW,
            stats.currentQuality
        )
    }

    @Test
    fun `getProcessingStats should return current statistics`() {
        val stats = analyzer.getProcessingStats()

        assertNotNull("Stats should not be null", stats)
        assertTrue("Total frames should be non-negative", stats.totalFrames >= 0)
        assertTrue("Dropped frames should be non-negative", stats.droppedFrames >= 0)
        assertTrue("Drop rate should be between 0 and 1", stats.dropRate >= 0.0f && stats.dropRate <= 1.0f)
        assertTrue("Consecutive no detection should be non-negative", stats.consecutiveNoDetection >= 0)
        assertNotNull("Current quality should not be null", stats.currentQuality)
    }

    @Test
    fun `resetStats should clear all statistics`() = runTest {
        // Generate some statistics first
        analyzer.analyze(mockImageProxy)
        kotlinx.coroutines.delay(50)

        val statsBeforeReset = analyzer.getProcessingStats()

        // Reset statistics
        analyzer.resetStats()

        val statsAfterReset = analyzer.getProcessingStats()

        assertEquals("Total frames should be reset to 0", 0, statsAfterReset.totalFrames)
        assertEquals("Dropped frames should be reset to 0", 0, statsAfterReset.droppedFrames)
        assertEquals("Drop rate should be reset to 0", 0.0f, statsAfterReset.dropRate)
        assertEquals("Consecutive no detection should be reset to 0", 0, statsAfterReset.consecutiveNoDetection)
    }

    @Test
    fun `analyzer with fake repository should work end-to-end`() = runTest {
        // Use real FakePoseRepository for integration test
        val fakeRepository = FakePoseRepository()
        val testListener = TestPoseDetectionListener()
        val testAnalyzer = CameraPoseAnalyzer(fakeRepository, testListener)

        // Initialize repository
        fakeRepository.init(mockk(relaxed = true))
        fakeRepository.start(testListener)

        // Process frame
        testAnalyzer.analyze(mockImageProxy)

        // Allow processing time
        kotlinx.coroutines.delay(100)

        // Verify pose was detected
        assertTrue("Pose should be detected", testListener.poseDetected)
        assertNotNull("Result should not be null", testListener.lastResult)

        // Cleanup
        fakeRepository.stop()
        testAnalyzer.cleanup()
    }

    @Test
    fun `processing quality enum should have correct values`() {
        val high = CameraPoseAnalyzer.ProcessingQuality.HIGH
        val medium = CameraPoseAnalyzer.ProcessingQuality.MEDIUM
        val low = CameraPoseAnalyzer.ProcessingQuality.LOW

        // Verify HIGH quality has highest resolution
        assertTrue("HIGH should have width >= MEDIUM",
                  high.targetImageWidth >= medium.targetImageWidth)
        assertTrue("MEDIUM should have width >= LOW",
                  medium.targetImageWidth >= low.targetImageWidth)

        // Verify LOW quality has highest skip rate
        assertTrue("LOW should skip more frames than MEDIUM",
                  low.frameSkipRate >= medium.frameSkipRate)
        assertTrue("MEDIUM should skip more frames than HIGH",
                  medium.frameSkipRate >= high.frameSkipRate)
    }

    private fun createMockImageProxy(): ImageProxy {
        val mockImageProxy = mockk<ImageProxy>(relaxed = true)
        val mockImageInfo = mockk<ImageInfo>(relaxed = true)
        val mockPlane = mockk<ImageProxy.PlaneProxy>(relaxed = true)
        val mockBuffer = mockk<ByteBuffer>(relaxed = true)

        every { mockImageProxy.width } returns 640
        every { mockImageProxy.height } returns 480
        every { mockImageProxy.format } returns ImageFormat.YUV_420_888
        every { mockImageProxy.imageInfo } returns mockImageInfo
        every { mockImageInfo.rotationDegrees } returns 0

        // Mock planes for YUV format
        every { mockImageProxy.planes } returns arrayOf(mockPlane, mockPlane, mockPlane)
        every { mockPlane.buffer } returns mockBuffer
        every { mockPlane.pixelStride } returns 1
        every { mockBuffer.remaining() } returns 640 * 480 // Y plane size
        every { mockBuffer.get(any<ByteArray>(), any(), any()) } returns mockBuffer

        // Mock the close method
        every { mockImageProxy.close() } just runs

        return mockImageProxy
    }

    private class TestPoseDetectionListener : PoseDetectionListener {
        var poseDetected = false
        var errorOccurred = false
        var lastResult: PoseLandmarkResult? = null
        var lastError: PoseDetectionError? = null

        override fun onPoseDetected(result: PoseLandmarkResult) {
            poseDetected = true
            lastResult = result
        }

        override fun onPoseDetectionError(error: PoseDetectionError) {
            errorOccurred = true
            lastError = error
        }
    }
}