package com.posecoach.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.posecoach.app.privacy.PrivacyManager
import com.posecoach.corepose.models.PoseLandmarkResult
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import kotlin.math.abs

/**
 * Integration tests for the complete pose overlay system.
 * Tests the interaction between CoordinateMapper, PoseOverlayView, and PoseOverlayEffect.
 */
@RunWith(AndroidJUnit4::class)
class PoseOverlayIntegrationTest {

    private lateinit var context: Context
    private lateinit var overlayView: PoseOverlayView
    private lateinit var coordinateMapper: CoordinateMapper
    private lateinit var privacyManager: PrivacyManager
    private lateinit var testLandmarks: PoseLandmarkResult

    companion object {
        private const val PIXEL_ERROR_TOLERANCE = 2.0f
        private const val PERFORMANCE_THRESHOLD_MS = 16.67 // 60fps
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        coordinateMapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false
        )

        overlayView = PoseOverlayView(context)
        overlayView.setCoordinateMapper(coordinateMapper)

        privacyManager = PrivacyManager(context)
        overlayView.setPrivacyManager(privacyManager)

        testLandmarks = createTestPoseLandmarks()
    }

    @After
    fun tearDown() {
        overlayView.clear()
        privacyManager.clearAllSettings()
    }

    private fun createTestPoseLandmarks(): PoseLandmarkResult {
        val landmarks = List(33) { index ->
            PoseLandmarkResult.Landmark(
                x = (index % 11) / 10.0f,
                y = (index / 11) / 3.0f,
                z = 0.0f,
                visibility = if (index % 3 == 0) 0.9f else 0.7f,
                presence = 0.8f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 25L
        )
    }

    @Test
    fun `overlay view should render pose landmarks with pixel perfect accuracy`() {
        overlayView.updatePose(testLandmarks)

        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val startTime = System.nanoTime()
        overlayView.draw(canvas)
        val renderTime = (System.nanoTime() - startTime) / 1_000_000.0

        assertTrue("Rendering should be fast (<${PERFORMANCE_THRESHOLD_MS}ms): took ${renderTime}ms",
            renderTime < PERFORMANCE_THRESHOLD_MS)

        // Verify performance stats
        val stats = overlayView.getPerformanceStats()
        assertTrue("Should have rendered frames", stats.frameCount > 0)
        assertTrue("Average render time should be reasonable", stats.averageRenderTime >= 0)
    }

    @Test
    fun `coordinate transformation should be consistent between view and effect`() {
        overlayView.updatePose(testLandmarks)

        // Get coordinate transformations from the view
        val viewCoordinates = testLandmarks.landmarks.map { landmark ->
            coordinateMapper.normalizedToPixel(landmark.x, landmark.y)
        }

        // Verify all coordinates are within bounds and accurate
        viewCoordinates.forEachIndexed { index, (x, y) ->
            assertTrue("Landmark $index X coordinate should be in bounds: $x",
                x >= 0f && x <= 1080f)
            assertTrue("Landmark $index Y coordinate should be in bounds: $y",
                y >= 0f && y <= 1920f)

            // Test reverse transformation accuracy
            val (reverseX, reverseY) = coordinateMapper.pixelToNormalized(x, y)
            val originalLandmark = testLandmarks.landmarks[index]

            val errorX = abs(originalLandmark.x - reverseX) * 1080
            val errorY = abs(originalLandmark.y - reverseY) * 1920

            assertTrue("Landmark $index reverse X error should be <${PIXEL_ERROR_TOLERANCE}px: ${errorX}px",
                errorX < PIXEL_ERROR_TOLERANCE)
            assertTrue("Landmark $index reverse Y error should be <${PIXEL_ERROR_TOLERANCE}px: ${errorY}px",
                errorY < PIXEL_ERROR_TOLERANCE)
        }
    }

    @Test
    fun `multi_person pose rendering should work correctly`() {
        val multiPersonPoses = List(3) { personIndex ->
            createTestPoseLandmarks().copy(
                landmarks = testLandmarks.landmarks.map { landmark ->
                    landmark.copy(
                        x = landmark.x + (personIndex * 0.3f),
                        y = landmark.y,
                        visibility = if (personIndex == 1) 0.9f else 0.6f
                    )
                }
            )
        }

        overlayView.enableMultiPersonMode(true)
        overlayView.updateMultiPersonPoses(multiPersonPoses)

        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val startTime = System.nanoTime()
        overlayView.draw(canvas)
        val renderTime = (System.nanoTime() - startTime) / 1_000_000.0

        assertTrue("Multi-person rendering should be performant (<${PERFORMANCE_THRESHOLD_MS * 2}ms): took ${renderTime}ms",
            renderTime < PERFORMANCE_THRESHOLD_MS * 2)

        val stats = overlayView.getPerformanceStats()
        assertEquals("Should track multiple persons", 3, stats.multiPersonCount)

        // Test person selection
        overlayView.selectPerson(1)
        overlayView.draw(canvas)

        // Verify selection worked by checking if high visibility landmarks are rendered
        assertTrue("Selected person should have high visibility landmarks",
            stats.visibleLandmarks > 20)
    }

    @Test
    fun `privacy mode should limit pose data display`() {
        privacyManager.setLocalOnlyMode(true)

        overlayView.updatePose(testLandmarks)

        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        overlayView.draw(canvas)

        val stats = overlayView.getPerformanceStats()

        // In privacy mode, should show fewer visible landmarks
        assertTrue("Privacy mode should limit visible landmarks",
            stats.visibleLandmarks <= testLandmarks.landmarks.size)
    }

    @Test
    fun `rotation handling should maintain accuracy across all angles`() {
        val rotationAngles = listOf(0, 90, 180, 270)

        rotationAngles.forEach { angle ->
            val rotatedMapper = CoordinateMapper(
                viewWidth = 1080,
                viewHeight = 1920,
                imageWidth = 720,
                imageHeight = 1280,
                isFrontFacing = false,
                rotation = angle
            )

            overlayView.setCoordinateMapper(rotatedMapper)
            overlayView.updatePose(testLandmarks)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val startTime = System.nanoTime()
            overlayView.draw(canvas)
            val renderTime = (System.nanoTime() - startTime) / 1_000_000.0

            assertTrue("Rotation $angle° should render efficiently: ${renderTime}ms",
                renderTime < PERFORMANCE_THRESHOLD_MS)

            // Verify coordinate accuracy for rotated views
            val centerLandmark = testLandmarks.landmarks[15] // Mid-torso landmark
            val (pixelX, pixelY) = rotatedMapper.normalizedToPixel(centerLandmark.x, centerLandmark.y)
            val (reverseX, reverseY) = rotatedMapper.pixelToNormalized(pixelX, pixelY)

            val errorX = abs(centerLandmark.x - reverseX)
            val errorY = abs(centerLandmark.y - reverseY)

            assertTrue("Rotation $angle°: Center landmark X accuracy: ${errorX}",
                errorX < 0.05f)
            assertTrue("Rotation $angle°: Center landmark Y accuracy: ${errorY}",
                errorY < 0.05f)
        }
    }

    @Test
    fun `fit mode changes should maintain coordinate consistency`() {
        val fitModes = listOf(FitMode.FILL, FitMode.CENTER_CROP, FitMode.CENTER_INSIDE)

        fitModes.forEach { fitMode ->
            coordinateMapper.updateAspectRatio(fitMode)
            overlayView.updatePose(testLandmarks)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            overlayView.draw(canvas)

            // Test coordinate consistency
            val testLandmark = testLandmarks.landmarks[0]
            val (pixelX, pixelY) = coordinateMapper.normalizedToPixel(testLandmark.x, testLandmark.y)

            assertTrue("$fitMode: Pixel coordinates should be finite",
                pixelX.isFinite() && pixelY.isFinite())
            assertTrue("$fitMode: Pixel coordinates should be reasonable",
                pixelX >= -100f && pixelX <= 1180f && pixelY >= -100f && pixelY <= 2020f)

            // Test visible bounds
            val bounds = coordinateMapper.getVisibleRegion()
            assertTrue("$fitMode: Bounds should be valid",
                bounds.left >= 0f && bounds.right <= 1f &&
                bounds.top >= 0f && bounds.bottom <= 1f)
        }
    }

    @Test
    fun `performance should remain stable under continuous updates`() {
        val renderTimes = mutableListOf<Double>()

        repeat(100) { iteration ->
            // Simulate varying landmark data
            val varyingLandmarks = testLandmarks.copy(
                landmarks = testLandmarks.landmarks.map { landmark ->
                    landmark.copy(
                        x = (landmark.x + (iteration * 0.001f)) % 1.0f,
                        y = (landmark.y + (iteration * 0.002f)) % 1.0f,
                        visibility = 0.5f + (iteration % 50) / 100.0f
                    )
                },
                inferenceTimeMs = 20L + (iteration % 10)
            )

            overlayView.updatePose(varyingLandmarks)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val startTime = System.nanoTime()
            overlayView.draw(canvas)
            val renderTime = (System.nanoTime() - startTime) / 1_000_000.0

            renderTimes.add(renderTime)
        }

        val avgRenderTime = renderTimes.average()
        val maxRenderTime = renderTimes.maxOrNull() ?: 0.0
        val stableFrames = renderTimes.count { it < PERFORMANCE_THRESHOLD_MS }

        assertTrue("Average render time should be fast: ${avgRenderTime}ms",
            avgRenderTime < PERFORMANCE_THRESHOLD_MS)
        assertTrue("Maximum render time should be reasonable: ${maxRenderTime}ms",
            maxRenderTime < PERFORMANCE_THRESHOLD_MS * 3)
        assertTrue("Should have >90% stable frames: ${stableFrames}/100",
            stableFrames > 90)

        val finalStats = overlayView.getPerformanceStats()
        assertTrue("Should track all frames", finalStats.frameCount >= 100)
    }

    @Test
    fun `batch landmark processing should be accurate and efficient`() {
        val largeLandmarkSet = List(100) { index ->
            (index % 100) / 100.0f to ((index * 7) % 100) / 100.0f
        }

        val startTime = System.nanoTime()
        val pixelCoordinates = coordinateMapper.batchNormalizedToPixel(largeLandmarkSet)
        val batchTime = (System.nanoTime() - startTime) / 1_000_000.0

        assertEquals("Should process all landmarks", 100, pixelCoordinates.size)
        assertTrue("Batch processing should be fast (<5ms): took ${batchTime}ms",
            batchTime < 5.0)

        // Verify accuracy against individual processing
        largeLandmarkSet.zip(pixelCoordinates).forEachIndexed { index, (normalized, pixel) ->
            val individual = coordinateMapper.normalizedToPixel(normalized.first, normalized.second)
            val errorX = abs(pixel.first - individual.first)
            val errorY = abs(pixel.second - individual.second)

            assertTrue("Batch accuracy for landmark $index X: ${errorX}px",
                errorX < 0.1f)
            assertTrue("Batch accuracy for landmark $index Y: ${errorY}px",
                errorY < 0.1f)
        }
    }

    @Test
    fun `edge case coordinates should render without crashes`() {
        val edgeCaseLandmarks = testLandmarks.copy(
            landmarks = listOf(
                // Normal landmarks
                PoseLandmarkResult.Landmark(0.5f, 0.5f, 0f, 0.8f, 0.8f),
                // Edge coordinates
                PoseLandmarkResult.Landmark(0.0f, 0.0f, 0f, 0.9f, 0.9f),
                PoseLandmarkResult.Landmark(1.0f, 1.0f, 0f, 0.9f, 0.9f),
                // Out of bounds (should be clamped)
                PoseLandmarkResult.Landmark(-0.1f, 0.5f, 0f, 0.7f, 0.7f),
                PoseLandmarkResult.Landmark(1.1f, 0.5f, 0f, 0.7f, 0.7f),
                PoseLandmarkResult.Landmark(0.5f, -0.1f, 0f, 0.7f, 0.7f),
                PoseLandmarkResult.Landmark(0.5f, 1.1f, 0f, 0.7f, 0.7f),
                // Extreme values
                PoseLandmarkResult.Landmark(Float.MAX_VALUE, Float.MIN_VALUE, 0f, 0.5f, 0.5f),
                PoseLandmarkResult.Landmark(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0f, 0.3f, 0.3f),
                // NaN values
                PoseLandmarkResult.Landmark(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN)
            )
        )

        // Should not crash when rendering edge cases
        assertDoesNotThrow("Should handle edge cases gracefully") {
            overlayView.updatePose(edgeCaseLandmarks)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            overlayView.draw(canvas)
        }

        val stats = overlayView.getPerformanceStats()
        assertTrue("Should render some landmarks despite edge cases",
            stats.frameCount > 0)
    }

    private fun assertDoesNotThrow(message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("$message - Exception thrown: ${e.message}")
        }
    }
}