package com.posecoach.app.overlay

import android.graphics.RectF
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Comprehensive test suite for CoordinateMapper with sub-pixel accuracy verification.
 * Tests all rotation angles, crop modes, and edge cases with <2px error tolerance.
 */
class EnhancedCoordinateMapperTest {

    private lateinit var standardMapper: CoordinateMapper
    private lateinit var frontFacingMapper: CoordinateMapper
    private lateinit var rotatedMapper: CoordinateMapper

    companion object {
        private const val PIXEL_ERROR_TOLERANCE = 2.0f
        private const val SUB_PIXEL_TOLERANCE = 1.0f
        private const val PERFORMANCE_THRESHOLD_MS = 5L
    }

    @Before
    fun setUp() {
        standardMapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false
        )

        frontFacingMapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = true
        )

        rotatedMapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false,
            rotation = 90
        )
    }

    @After
    fun tearDown() {
        // Verify performance metrics
        val metrics = standardMapper.getPerformanceMetrics()
        assertTrue("Average error should be within tolerance", metrics.averageError <= PIXEL_ERROR_TOLERANCE)
    }

    @Test
    fun `normalized to pixel conversion should be accurate within 2px for standard mapper`() {
        val testCases = listOf(
            0.0f to 0.0f,
            0.5f to 0.5f,
            1.0f to 1.0f,
            0.25f to 0.75f,
            0.33f to 0.67f,
            0.1f to 0.9f,
            0.99f to 0.01f
        )

        testCases.forEach { (normalizedX, normalizedY) ->
            val (pixelX, pixelY) = standardMapper.normalizedToPixel(normalizedX, normalizedY)

            val expectedX = normalizedX * 1080
            val expectedY = normalizedY * 1920

            val errorX = abs(pixelX - expectedX)
            val errorY = abs(pixelY - expectedY)

            assertTrue("X error should be <${PIXEL_ERROR_TOLERANCE}px: got ${errorX}px for input ($normalizedX, $normalizedY)",
                errorX < PIXEL_ERROR_TOLERANCE)
            assertTrue("Y error should be <${PIXEL_ERROR_TOLERANCE}px: got ${errorY}px for input ($normalizedX, $normalizedY)",
                errorY < PIXEL_ERROR_TOLERANCE)
        }
    }

    @Test
    fun `sub_pixel accuracy should be maintained for precise landmarks`() {
        // Test high-precision coordinates that require sub-pixel accuracy
        val preciseTestCases = listOf(
            0.1234f to 0.5678f,
            0.9876f to 0.4321f,
            0.5555f to 0.7777f,
            0.3333f to 0.6666f
        )

        preciseTestCases.forEach { (normalizedX, normalizedY) ->
            val (pixelX, pixelY) = standardMapper.normalizedToPixel(normalizedX, normalizedY)

            // Verify reverse transformation accuracy
            val (reverseX, reverseY) = standardMapper.pixelToNormalized(pixelX, pixelY)

            val errorX = abs(normalizedX - reverseX) * 1080
            val errorY = abs(normalizedY - reverseY) * 1920

            assertTrue("Sub-pixel X error should be <${SUB_PIXEL_TOLERANCE}px: got ${errorX}px",
                errorX < SUB_PIXEL_TOLERANCE)
            assertTrue("Sub-pixel Y error should be <${SUB_PIXEL_TOLERANCE}px: got ${errorY}px",
                errorY < SUB_PIXEL_TOLERANCE)
        }
    }

    @Test
    fun `front camera should mirror X coordinate accurately`() {
        val testCases = listOf(
            0.0f to 0.0f,
            0.3f to 0.5f,
            0.7f to 0.2f,
            1.0f to 1.0f
        )

        testCases.forEach { (normalizedX, normalizedY) ->
            val (pixelX, pixelY) = frontFacingMapper.normalizedToPixel(normalizedX, normalizedY)

            val expectedX = 1080 * (1.0f - normalizedX)
            val expectedY = 1920 * normalizedY

            val errorX = abs(pixelX - expectedX)
            val errorY = abs(pixelY - expectedY)

            assertTrue("Front camera X mirroring error should be <${PIXEL_ERROR_TOLERANCE}px: got ${errorX}px",
                errorX < PIXEL_ERROR_TOLERANCE)
            assertTrue("Front camera Y error should be <${PIXEL_ERROR_TOLERANCE}px: got ${errorY}px",
                errorY < PIXEL_ERROR_TOLERANCE)
        }
    }

    @Test
    fun `aspect ratio correction should maintain proportions for all fit modes`() {
        val squareImageMapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 720,
            isFrontFacing = false
        )

        // Test FILL mode
        squareImageMapper.updateAspectRatio(FitMode.FILL)
        val (fillX, fillY) = squareImageMapper.normalizedToPixel(0.5f, 0.5f)
        assertEquals("FILL mode center X", 540f, fillX, PIXEL_ERROR_TOLERANCE)
        assertEquals("FILL mode center Y", 960f, fillY, PIXEL_ERROR_TOLERANCE)

        // Test CENTER_CROP mode
        squareImageMapper.updateAspectRatio(FitMode.CENTER_CROP)
        val (cropX, cropY) = squareImageMapper.normalizedToPixel(0.5f, 0.5f)
        assertEquals("CENTER_CROP mode center X", 540f, cropX, PIXEL_ERROR_TOLERANCE)
        assertEquals("CENTER_CROP mode center Y", 960f, cropY, PIXEL_ERROR_TOLERANCE)

        // Test CENTER_INSIDE mode
        squareImageMapper.updateAspectRatio(FitMode.CENTER_INSIDE)
        val (insideX, insideY) = squareImageMapper.normalizedToPixel(0.5f, 0.5f)
        assertEquals("CENTER_INSIDE mode center X", 540f, insideX, PIXEL_ERROR_TOLERANCE)
        assertEquals("CENTER_INSIDE mode center Y", 960f, insideY, PIXEL_ERROR_TOLERANCE)
    }

    @Test
    fun `center crop mode should scale correctly for different aspect ratios`() {
        val wideImageMapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 1920,
            imageHeight = 1080,
            isFrontFacing = false
        )

        wideImageMapper.updateAspectRatio(FitMode.CENTER_CROP)
        val (centerX, centerY) = wideImageMapper.normalizedToPixel(0.5f, 0.5f)

        // Should be centered in the view
        assertEquals("Wide image center X", 540f, centerX, PIXEL_ERROR_TOLERANCE)
        assertEquals("Wide image center Y", 960f, centerY, PIXEL_ERROR_TOLERANCE)

        // Test corner cases
        val (topLeftX, topLeftY) = wideImageMapper.normalizedToPixel(0f, 0f)
        val (bottomRightX, bottomRightY) = wideImageMapper.normalizedToPixel(1f, 1f)

        assertTrue("Top-left should be within view bounds", topLeftX >= -PIXEL_ERROR_TOLERANCE)
        assertTrue("Top-left should be within view bounds", topLeftY >= -PIXEL_ERROR_TOLERANCE)
        assertTrue("Bottom-right should be within extended bounds", bottomRightX <= 1080 + PIXEL_ERROR_TOLERANCE)
        assertTrue("Bottom-right should be within extended bounds", bottomRightY <= 1920 + PIXEL_ERROR_TOLERANCE)
    }

    @Test
    fun `batch conversion should be efficient and accurate`() {
        val landmarks = List(33) { index ->
            val normalizedX = (index % 11) / 10.0f
            val normalizedY = (index / 11) / 3.0f
            normalizedX to normalizedY
        }

        val startTime = System.currentTimeMillis()
        val batchPixels = standardMapper.batchNormalizedToPixel(landmarks)
        val batchElapsed = System.currentTimeMillis() - startTime

        // Compare with individual conversions for accuracy
        val individualPixels = landmarks.map { (x, y) ->
            standardMapper.normalizedToPixel(x, y)
        }

        assertEquals("Batch size should match input", 33, batchPixels.size)
        assertTrue("Batch conversion should be fast (<${PERFORMANCE_THRESHOLD_MS}ms)", batchElapsed < PERFORMANCE_THRESHOLD_MS)

        // Verify accuracy matches individual conversions
        batchPixels.zip(individualPixels).forEachIndexed { index, (batch, individual) ->
            val errorX = abs(batch.first - individual.first)
            val errorY = abs(batch.second - individual.second)

            assertTrue("Batch X accuracy for landmark $index: error ${errorX}px",
                errorX < 0.1f)
            assertTrue("Batch Y accuracy for landmark $index: error ${errorY}px",
                errorY < 0.1f)
        }
    }

    @Test
    fun `edge cases should be handled correctly with proper clamping`() {
        val edgeCases = listOf(
            -0.1f to 0.5f,   // Left boundary
            1.1f to 0.5f,    // Right boundary
            0.5f to -0.1f,   // Top boundary
            0.5f to 1.1f,    // Bottom boundary
            -0.5f to -0.5f,  // Top-left corner
            1.5f to 1.5f,    // Bottom-right corner
            Float.NaN to 0.5f, // NaN handling
            0.5f to Float.POSITIVE_INFINITY // Infinity handling
        )

        edgeCases.forEach { (x, y) ->
            val (pixelX, pixelY) = standardMapper.normalizedToPixel(x, y)

            // Check that coordinates are finite and within bounds
            assertTrue("X should be finite", pixelX.isFinite())
            assertTrue("Y should be finite", pixelY.isFinite())
            assertTrue("X should be clamped to view: got $pixelX", pixelX >= 0 && pixelX <= 1080)
            assertTrue("Y should be clamped to view: got $pixelY", pixelY >= 0 && pixelY <= 1920)
        }
    }

    @Test
    fun `rotation handling should transform coordinates correctly for all angles`() {
        val rotationAngles = listOf(0, 90, 180, 270)
        val testPoint = 0.3f to 0.7f

        rotationAngles.forEach { angle ->
            val rotatedMapper = CoordinateMapper(
                viewWidth = 1080,
                viewHeight = 1920,
                imageWidth = 720,
                imageHeight = 1280,
                isFrontFacing = false,
                rotation = angle
            )

            val (pixelX, pixelY) = rotatedMapper.normalizedToPixel(testPoint.first, testPoint.second)

            assertTrue("Rotation $angle°: X should be finite", pixelX.isFinite())
            assertTrue("Rotation $angle°: Y should be finite", pixelY.isFinite())
            assertTrue("Rotation $angle°: X should be in bounds", pixelX >= -100 && pixelX <= 1180) // Allow some tolerance for rotation
            assertTrue("Rotation $angle°: Y should be in bounds", pixelY >= -100 && pixelY <= 2020)

            // Test reverse transformation accuracy
            val (reverseX, reverseY) = rotatedMapper.pixelToNormalized(pixelX, pixelY)
            val errorX = abs(testPoint.first - reverseX)
            val errorY = abs(testPoint.second - reverseY)

            assertTrue("Rotation $angle°: Reverse X error should be small: ${errorX}",
                errorX < 0.1f)
            assertTrue("Rotation $angle°: Reverse Y error should be small: ${errorY}",
                errorY < 0.1f)
        }
    }

    @Test
    fun `visible bounds calculation should be accurate for all fit modes`() {
        val fitModes = listOf(FitMode.FILL, FitMode.CENTER_CROP, FitMode.CENTER_INSIDE)

        fitModes.forEach { fitMode ->
            val mapper = CoordinateMapper(
                viewWidth = 1080,
                viewHeight = 1920,
                imageWidth = 720,
                imageHeight = 1280,
                isFrontFacing = false
            )

            mapper.updateAspectRatio(fitMode)
            val bounds = mapper.getVisibleRegion()

            // Bounds should be valid
            assertTrue("$fitMode: Left bound should be valid", bounds.left >= 0f && bounds.left <= 1f)
            assertTrue("$fitMode: Top bound should be valid", bounds.top >= 0f && bounds.top <= 1f)
            assertTrue("$fitMode: Right bound should be valid", bounds.right >= 0f && bounds.right <= 1f)
            assertTrue("$fitMode: Bottom bound should be valid", bounds.bottom >= 0f && bounds.bottom <= 1f)
            assertTrue("$fitMode: Left should be less than right", bounds.left <= bounds.right)
            assertTrue("$fitMode: Top should be less than bottom", bounds.top <= bounds.bottom)
        }
    }

    @Test
    fun `point visibility detection should work correctly`() {
        standardMapper.updateAspectRatio(FitMode.CENTER_INSIDE)

        val testPoints = listOf(
            0.5f to 0.5f to true,  // Center should always be visible
            0.0f to 0.0f to true,  // Top-left corner
            1.0f to 1.0f to true,  // Bottom-right corner
            -0.1f to 0.5f to false, // Outside left
            1.1f to 0.5f to false,  // Outside right
            0.5f to -0.1f to false, // Outside top
            0.5f to 1.1f to false   // Outside bottom
        )

        testPoints.forEach { (point, expectedVisible) ->
            val isVisible = standardMapper.isPointVisible(point.first, point.second)
            assertEquals("Point (${point.first}, ${point.second}) visibility",
                expectedVisible, isVisible)
        }
    }

    @Test
    fun `performance metrics should be tracked accurately`() {
        // Perform several transformations
        repeat(100) {
            val x = (it % 10) / 10.0f
            val y = (it / 10) / 10.0f
            standardMapper.normalizedToPixel(x, y)
        }

        val metrics = standardMapper.getPerformanceMetrics()

        assertEquals("Transformation count should be 100", 100L, metrics.transformationCount)
        assertTrue("Average error should be reasonable", metrics.averageError >= 0f)
        assertTrue("Average error should be within tolerance", metrics.averageError <= PIXEL_ERROR_TOLERANCE)
        assertNotNull("Scale should be set", metrics.currentScale)
        assertNotNull("Offset should be set", metrics.currentOffset)
        assertEquals("Rotation angle should match", 0, metrics.rotationAngle)
    }

    @Test
    fun `stress test with random coordinates should maintain accuracy`() {
        val randomTestCases = List(1000) {
            Math.random().toFloat() to Math.random().toFloat()
        }

        var maxError = 0f
        randomTestCases.forEach { (x, y) ->
            val (pixelX, pixelY) = standardMapper.normalizedToPixel(x, y)
            val (reverseX, reverseY) = standardMapper.pixelToNormalized(pixelX, pixelY)

            val errorX = abs(x - reverseX) * 1080
            val errorY = abs(y - reverseY) * 1920
            val error = maxOf(errorX, errorY)

            maxError = maxOf(maxError, error)
        }

        assertTrue("Maximum error in stress test should be <${PIXEL_ERROR_TOLERANCE}px: got ${maxError}px",
            maxError < PIXEL_ERROR_TOLERANCE)
    }

    @Test
    fun `all rotation angles should preserve coordinate accuracy`() {
        val rotationTestAngles = listOf(0, 45, 90, 135, 180, 225, 270, 315, 360)
        val testCoordinate = 0.3f to 0.7f

        rotationTestAngles.forEach { angle ->
            val mapper = CoordinateMapper(
                viewWidth = 1080,
                viewHeight = 1920,
                imageWidth = 720,
                imageHeight = 1280,
                isFrontFacing = false,
                rotation = angle
            )

            val (pixelX, pixelY) = mapper.normalizedToPixel(testCoordinate.first, testCoordinate.second)
            val (reverseX, reverseY) = mapper.pixelToNormalized(pixelX, pixelY)

            val errorX = abs(testCoordinate.first - reverseX)
            val errorY = abs(testCoordinate.second - reverseY)

            assertTrue("Rotation $angle°: X round-trip error should be minimal: ${errorX}",
                errorX < 0.05f)
            assertTrue("Rotation $angle°: Y round-trip error should be minimal: ${errorY}",
                errorY < 0.05f)
        }
    }

    @Test
    fun `multi_pose landmark batch processing should be performant`() {
        val multiPersonLandmarks = List(3) { personIndex ->
            List(33) { landmarkIndex ->
                val baseX = (personIndex * 0.3f) + (landmarkIndex % 11) * 0.03f
                val baseY = (landmarkIndex / 11) * 0.33f
                baseX to baseY
            }
        }.flatten()

        val startTime = System.nanoTime()
        val pixelLandmarks = standardMapper.batchNormalizedToPixel(multiPersonLandmarks)
        val elapsed = (System.nanoTime() - startTime) / 1_000_000.0

        assertEquals("Should process all landmarks", 99, pixelLandmarks.size)
        assertTrue("Multi-pose processing should be fast (<10ms): took ${elapsed}ms", elapsed < 10.0)

        // Verify all coordinates are valid
        pixelLandmarks.forEach { (x, y) ->
            assertTrue("X coordinate should be finite", x.isFinite())
            assertTrue("Y coordinate should be finite", y.isFinite())
            assertTrue("X coordinate should be in bounds", x >= 0f && x <= 1080f)
            assertTrue("Y coordinate should be in bounds", y >= 0f && y <= 1920f)
        }
    }
}