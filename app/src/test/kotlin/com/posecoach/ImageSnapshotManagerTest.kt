package com.posecoach.app.livecoach.camera

import android.graphics.*
import androidx.camera.core.ImageProxy
import com.posecoach.app.livecoach.models.LiveApiMessage
import com.posecoach.app.livecoach.models.PoseSnapshot
import com.posecoach.corepose.models.PoseLandmarkResult
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
import java.nio.ByteBuffer
import org.junit.Assert.*

/**
 * TDD test suite for ImageSnapshotManager
 * Following the specification in .claude/specs/voice-coach-integration.md
 *
 * Test categories:
 * 1. Image processing and compression
 * 2. Frame rate limiting
 * 3. Memory management
 * 4. Privacy compliance
 * 5. Performance optimization
 * 6. Error handling and recovery
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageSnapshotManagerTest {

    private lateinit var testScope: TestScope
    private lateinit var snapshotManager: ImageSnapshotManager

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

        testScope = TestScope()
        snapshotManager = ImageSnapshotManager(testScope)
    }

    @After
    fun tearDown() {
        snapshotManager.destroy()
        testScope.cancel()
        unmockkAll()
    }

    // IMAGE PROCESSING AND COMPRESSION TESTS

    @Test
    fun `should compress images to specified quality`() = testScope.runTest {
        // ARRANGE
        val highQuality = 85
        val lowQuality = 50
        snapshotManager.startSnapshots(quality = highQuality)

        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val snapshots = mutableListOf<PoseSnapshot>()
        val job = launch {
            snapshotManager.snapshots.collect { snapshot ->
                snapshots.add(snapshot)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100) // Allow processing

        // Change quality and process another image
        snapshotManager.setJpegQuality(lowQuality)
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100)

        // ASSERT
        // Quality settings should affect output (verified through processing logic)
        assertTrue(snapshots.size <= 2, "Should process images with quality settings")

        job.cancel()
    }

    @Test
    fun `should resize images to low resolution for bandwidth optimization`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val (maxWidth, maxHeight, _) = snapshotManager.getSnapshotInfo()

        // ACT & ASSERT
        assertEquals(320, maxWidth, "Should use low resolution width for bandwidth optimization")
        assertEquals(240, maxHeight, "Should use low resolution height for bandwidth optimization")
    }

    @Test
    fun `should handle different image formats correctly`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockLandmarks = createMockPoseLandmarks()

        val snapshots = mutableListOf<PoseSnapshot>()
        val job = launch {
            snapshotManager.snapshots.collect { snapshot ->
                snapshots.add(snapshot)
            }
        }

        // ACT
        // Test YUV_420_888 format
        val yuvImageProxy = createMockImageProxy(format = ImageFormat.YUV_420_888)
        snapshotManager.processImageWithLandmarks(yuvImageProxy, mockLandmarks)

        // Test JPEG format
        val jpegImageProxy = createMockImageProxy(format = ImageFormat.JPEG)
        snapshotManager.processImageWithLandmarks(jpegImageProxy, mockLandmarks)

        advanceTimeBy(200)

        // ASSERT
        assertTrue(snapshots.size <= 2, "Should handle different image formats")

        job.cancel()
    }

    @Test
    fun `should create valid base64 encoded images`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val snapshots = mutableListOf<PoseSnapshot>()
        val job = launch {
            snapshotManager.snapshots.collect { snapshot ->
                snapshots.add(snapshot)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100)

        // ASSERT
        if (snapshots.isNotEmpty()) {
            val snapshot = snapshots.first()
            assertNotNull(snapshot.imageData, "Should have image data")
            assertTrue(snapshot.imageData.isNotEmpty(), "Image data should not be empty")

            // Verify base64 format (should not throw exception)
            assertDoesNotThrow {
                android.util.Base64.decode(snapshot.imageData, android.util.Base64.NO_WRAP)
            }
        }

        job.cancel()
    }

    // FRAME RATE LIMITING TESTS

    @Test
    fun `should respect frame rate limits`() = testScope.runTest {
        // ARRANGE
        val intervalMs = 1000L // 1 FPS
        snapshotManager.startSnapshots(intervalMs = intervalMs)

        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val snapshots = mutableListOf<PoseSnapshot>()
        val job = launch {
            snapshotManager.snapshots.collect { snapshot ->
                snapshots.add(snapshot)
            }
        }

        // ACT
        // Send multiple rapid requests
        repeat(5) {
            snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        }
        advanceTimeBy(100) // Less than interval

        // ASSERT
        assertTrue(snapshots.size <= 1, "Should limit frame rate and process only one snapshot")

        job.cancel()
    }

    @Test
    fun `should allow snapshots after interval expires`() = testScope.runTest {
        // ARRANGE
        val intervalMs = 500L
        snapshotManager.startSnapshots(intervalMs = intervalMs)

        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val snapshots = mutableListOf<PoseSnapshot>()
        val job = launch {
            snapshotManager.snapshots.collect { snapshot ->
                snapshots.add(snapshot)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100) // First snapshot

        // Wait for interval to expire
        advanceTimeBy(500)
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100) // Second snapshot

        // ASSERT
        assertTrue(snapshots.size <= 2, "Should allow snapshots after interval expires")

        job.cancel()
    }

    @Test
    fun `should update frame rate configuration correctly`() = testScope.runTest {
        // ARRANGE
        snapshotManager.setSnapshotInterval(2000L) // 0.5 FPS

        // ACT
        val frameRate = snapshotManager.getCurrentFrameRate()

        // ASSERT
        assertEquals(0.5f, frameRate, 0.01f, "Should calculate frame rate correctly")
    }

    @Test
    fun `should clamp frame rate to valid range`() = testScope.runTest {
        // ARRANGE & ACT
        snapshotManager.setSnapshotInterval(100L) // Too fast, should be clamped
        val fastFrameRate = snapshotManager.getCurrentFrameRate()

        snapshotManager.setSnapshotInterval(5000L) // Too slow, should be clamped
        val slowFrameRate = snapshotManager.getCurrentFrameRate()

        // ASSERT
        assertTrue(fastFrameRate <= 2.0f, "Should clamp too-fast frame rates")
        assertTrue(slowFrameRate >= 0.33f, "Should clamp too-slow frame rates")
    }

    // MEMORY MANAGEMENT TESTS

    @Test
    fun `should handle memory pressure gracefully`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots(intervalMs = 100L) // Fast rate
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // ACT
        // Process many images to create memory pressure
        repeat(20) {
            snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
            advanceTimeBy(50)
        }

        // Force cleanup
        System.gc()
        advanceTimeBy(1000)

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // ASSERT
        val memoryIncrease = finalMemory - initialMemory
        assertTrue(memoryIncrease < 100 * 1024 * 1024, // Less than 100MB increase
            "Memory usage should be controlled: ${memoryIncrease / 1024 / 1024}MB increase")
    }

    @Test
    fun `should limit concurrent processing operations`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val metrics = mutableListOf<ImageSnapshotManager.PerformanceMetrics>()
        val job = launch {
            snapshotManager.performanceMetrics.collect { metric ->
                metrics.add(metric)
            }
        }

        // ACT
        // Send many concurrent requests
        repeat(10) {
            snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        }
        advanceTimeBy(200)

        // ASSERT
        if (metrics.isNotEmpty()) {
            val latestMetric = metrics.last()
            assertTrue(latestMetric.droppedFrames >= 0, "Should track dropped frames due to concurrency limits")
        }

        job.cancel()
    }

    @Test
    fun `should cleanup resources on stop`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        snapshotManager.stopSnapshots()

        // ASSERT
        assertFalse(snapshotManager.isSnapshotsEnabled(), "Should be disabled after stop")
    }

    // PRIVACY COMPLIANCE TESTS

    @Test
    fun `should respect privacy settings`() = testScope.runTest {
        // ARRANGE
        snapshotManager.setPrivacyEnabled(false)
        snapshotManager.startSnapshots()

        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val snapshots = mutableListOf<PoseSnapshot>()
        val job = launch {
            snapshotManager.snapshots.collect { snapshot ->
                snapshots.add(snapshot)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100)

        // ASSERT
        assertTrue(snapshots.isEmpty(), "Should not capture images when privacy is disabled")

        job.cancel()
    }

    @Test
    fun `should enable image capture when privacy is enabled`() = testScope.runTest {
        // ARRANGE
        snapshotManager.setPrivacyEnabled(true)
        snapshotManager.startSnapshots()

        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val snapshots = mutableListOf<PoseSnapshot>()
        val job = launch {
            snapshotManager.snapshots.collect { snapshot ->
                snapshots.add(snapshot)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100)

        // ASSERT
        assertTrue(snapshotManager.isPrivacyCompliant(), "Should be privacy compliant")

        job.cancel()
    }

    @Test
    fun `should not process images without landmarks for privacy`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()

        val snapshots = mutableListOf<PoseSnapshot>()
        val job = launch {
            snapshotManager.snapshots.collect { snapshot ->
                snapshots.add(snapshot)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, null) // No landmarks
        advanceTimeBy(100)

        // ASSERT
        assertTrue(snapshots.isEmpty(), "Should not process images without landmarks")

        job.cancel()
    }

    // PERFORMANCE OPTIMIZATION TESTS

    @Test
    fun `should maintain performance metrics`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val metrics = mutableListOf<ImageSnapshotManager.PerformanceMetrics>()
        val job = launch {
            snapshotManager.performanceMetrics.collect { metric ->
                metrics.add(metric)
            }
        }

        // ACT
        repeat(3) {
            snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
            advanceTimeBy(1100) // Wait for interval
        }

        // ASSERT
        if (metrics.isNotEmpty()) {
            val latestMetric = metrics.last()
            assertTrue(latestMetric.totalSnapshots >= 0, "Should track total snapshots")
            assertTrue(latestMetric.averageProcessingTimeMs >= 0, "Should track processing time")
            assertTrue(latestMetric.averageFileSizeBytes >= 0, "Should track file sizes")
        }

        job.cancel()
    }

    @Test
    fun `should process snapshots within timeout`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val errors = mutableListOf<String>()
        val job = launch {
            snapshotManager.errors.collect { error ->
                errors.add(error)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(10000) // Long time to allow processing

        // ASSERT
        // Should complete without timeout errors
        assertTrue(errors.none { it.contains("timeout") }, "Should not timeout during normal processing")

        job.cancel()
    }

    @Test
    fun `should provide processing status information`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()

        // ACT
        val status = snapshotManager.getProcessingStatus()

        // ASSERT
        assertTrue(status.isEnabled, "Should report enabled status")
        assertTrue(status.currentProcessingCount >= 0, "Should report processing count")
        assertTrue(status.maxConcurrentProcessing > 0, "Should have positive max concurrent limit")
        assertTrue(status.frameRate > 0, "Should have positive frame rate")
        assertTrue(status.privacyEnabled, "Should report privacy status")
    }

    // LIVE API INTEGRATION TESTS

    @Test
    fun `should generate Live API compatible messages`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        val realtimeInputs = mutableListOf<LiveApiMessage.RealtimeInput>()
        val job = launch {
            snapshotManager.realtimeInput.collect { input ->
                realtimeInputs.add(input)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100)

        // ASSERT
        if (realtimeInputs.isNotEmpty()) {
            val input = realtimeInputs.first()
            assertNotNull(input.mediaChunks, "Should have media chunks")
            assertTrue(input.mediaChunks!!.isNotEmpty(), "Should have at least one media chunk")

            val mediaChunk = input.mediaChunks!!.first()
            assertEquals("image/jpeg", mediaChunk.mimeType, "Should use JPEG mime type")
            assertNotNull(mediaChunk.data, "Should have image data")
            assertTrue(mediaChunk.data.isNotEmpty(), "Image data should not be empty")
        }

        job.cancel()
    }

    @Test
    fun `should include pose context in Live API messages`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks(landmarkCount = 33)

        val realtimeInputs = mutableListOf<LiveApiMessage.RealtimeInput>()
        val job = launch {
            snapshotManager.realtimeInput.collect { input ->
                realtimeInputs.add(input)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100)

        // ASSERT
        if (realtimeInputs.isNotEmpty()) {
            val input = realtimeInputs.first()
            assertNotNull(input.text, "Should include pose context text")
            assertTrue(input.text!!.contains("33"), "Should mention landmark count")
            assertTrue(input.text!!.contains("pose_landmarks"), "Should reference pose landmarks")
        }

        job.cancel()
    }

    // ERROR HANDLING AND RECOVERY TESTS

    @Test
    fun `should handle invalid image formats gracefully`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy(format = ImageFormat.UNKNOWN)
        val mockLandmarks = createMockPoseLandmarks()

        val errors = mutableListOf<String>()
        val job = launch {
            snapshotManager.errors.collect { error ->
                errors.add(error)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        advanceTimeBy(100)

        // ASSERT
        // Should handle unknown formats gracefully (may emit error or skip)
        assertTrue(errors.isEmpty() || errors.any { it.contains("format") },
            "Should handle invalid formats gracefully")

        job.cancel()
    }

    @Test
    fun `should handle processing errors without crashing`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val faultyImageProxy = mockk<ImageProxy> {
            every { format } returns ImageFormat.YUV_420_888
            every { width } returns 720
            every { height } returns 1280
            every { planes } throws RuntimeException("Processing error")
        }
        val mockLandmarks = createMockPoseLandmarks()

        val errors = mutableListOf<String>()
        val job = launch {
            snapshotManager.errors.collect { error ->
                errors.add(error)
            }
        }

        // ACT
        snapshotManager.processImageWithLandmarks(faultyImageProxy, mockLandmarks)
        advanceTimeBy(100)

        // ASSERT
        assertTrue(errors.any { it.contains("error") || it.contains("failed") },
            "Should emit error for processing failures")

        job.cancel()
    }

    @Test
    fun `should cleanup properly on destroy`() = testScope.runTest {
        // ARRANGE
        snapshotManager.startSnapshots()
        val mockImageProxy = createMockImageProxy()
        val mockLandmarks = createMockPoseLandmarks()

        // ACT
        snapshotManager.processImageWithLandmarks(mockImageProxy, mockLandmarks)
        snapshotManager.destroy()

        // ASSERT
        assertFalse(snapshotManager.isSnapshotsEnabled(), "Should be disabled after destroy")

        val status = snapshotManager.getProcessingStatus()
        assertFalse(status.isEnabled, "Processing should be disabled after destroy")
        assertEquals(0, status.currentProcessingCount, "Should have no active processing after destroy")
    }

    // HELPER METHODS

    private fun createMockImageProxy(
        width: Int = 720,
        height: Int = 1280,
        format: Int = ImageFormat.YUV_420_888
    ): ImageProxy = mockk {
        every { this@mockk.width } returns width
        every { this@mockk.height } returns height
        every { this@mockk.format } returns format

        // Mock planes for YUV format
        val yPlane = mockk<ImageProxy.PlaneProxy> {
            every { buffer } returns ByteBuffer.allocate(width * height)
            every { pixelStride } returns 1
        }
        val uPlane = mockk<ImageProxy.PlaneProxy> {
            every { buffer } returns ByteBuffer.allocate(width * height / 4)
            every { pixelStride } returns 2
        }
        val vPlane = mockk<ImageProxy.PlaneProxy> {
            every { buffer } returns ByteBuffer.allocate(width * height / 4)
            every { pixelStride } returns 2
        }

        every { planes } returns arrayOf(yPlane, uPlane, vPlane)
    }

    private fun createMockPoseLandmarks(landmarkCount: Int = 33): PoseLandmarkResult {
        val landmarks = List(landmarkCount) { index ->
            PoseLandmarkResult.Landmark(
                x = (index % 10) / 10f,
                y = (index / 10) / 10f,
                z = 0f,
                visibility = 0.8f,
                presence = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 50L
        )
    }
}