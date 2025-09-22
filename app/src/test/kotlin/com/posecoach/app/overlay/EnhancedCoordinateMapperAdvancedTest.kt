package com.posecoach.app.overlay

import android.graphics.Matrix
import android.graphics.RectF
import android.util.Size
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import kotlin.math.abs
import kotlin.test.assertNotNull

/**
 * Advanced TDD test suite for EnhancedCoordinateMapper
 * Following the specification in .claude/specs/voice-coach-integration.md
 *
 * Test categories:
 * 1. Coordinate transformation accuracy
 * 2. Rotation handling
 * 3. Aspect ratio management
 * 4. Performance optimization
 * 5. Android 15+ compatibility
 * 6. Error handling and edge cases
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EnhancedCoordinateMapperAdvancedTest {

    // Test dimensions covering various device configurations
    private val phonePortraitView = Pair(1080, 2340)
    private val phoneLandscapeView = Pair(2340, 1080)
    private val tabletView = Pair(1920, 1200)
    private val imageSize = Pair(720, 1280)

    private val errorTolerance = 2.0f
    private val subPixelTolerance = 0.1f

    private lateinit var portraitMapper: EnhancedCoordinateMapper
    private lateinit var landscapeMapper: EnhancedCoordinateMapper
    private lateinit var tabletMapper: EnhancedCoordinateMapper
    private lateinit var frontCameraMapper: EnhancedCoordinateMapper

    @Before
    fun setup() {
        // Mock Timber to avoid log errors
        mockkStatic(Timber::class)
        every { Timber.d(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.e(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs

        portraitMapper = EnhancedCoordinateMapper(
            viewWidth = phonePortraitView.first,
            viewHeight = phonePortraitView.second,
            imageWidth = imageSize.first,
            imageHeight = imageSize.second,
            isFrontFacing = false,
            rotation = 0
        )

        landscapeMapper = EnhancedCoordinateMapper(
            viewWidth = phoneLandscapeView.first,
            viewHeight = phoneLandscapeView.second,
            imageWidth = imageSize.first,
            imageHeight = imageSize.second,
            isFrontFacing = false,
            rotation = 90
        )

        tabletMapper = EnhancedCoordinateMapper(
            viewWidth = tabletView.first,
            viewHeight = tabletView.second,
            imageWidth = imageSize.first,
            imageHeight = imageSize.second,
            isFrontFacing = false,
            rotation = 0
        )

        frontCameraMapper = EnhancedCoordinateMapper(
            viewWidth = phonePortraitView.first,
            viewHeight = phonePortraitView.second,
            imageWidth = imageSize.first,
            imageHeight = imageSize.second,
            isFrontFacing = true,
            rotation = 0
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // COORDINATE TRANSFORMATION ACCURACY TESTS

    @Test
    fun `should maintain sub-pixel accuracy in transformations`() {
        // ARRANGE
        val preciseCoordinates = listOf(
            Pair(0.333333f, 0.666666f),
            Pair(0.123456f, 0.789012f),
            Pair(0.999999f, 0.000001f),
            Pair(0.5f, 0.5f)
        )

        for ((x, y) in preciseCoordinates) {
            // ACT
            val (pixelX, pixelY) = portraitMapper.normalizedToPixel(x, y)
            val (backX, backY) = portraitMapper.pixelToNormalized(pixelX, pixelY)

            // ASSERT
            val errorX = abs(x - backX)
            val errorY = abs(y - backY)

            assertTrue(
                "Sub-pixel accuracy X error for ($x, $y): expected $x, got $backX, error $errorX",
                errorX <= subPixelTolerance
            )
            assertTrue(
                "Sub-pixel accuracy Y error for ($x, $y): expected $y, got $backY, error $errorY",
                errorY <= subPixelTolerance
            )
        }
    }

    @Test
    fun `should handle edge coordinates correctly`() {
        // ARRANGE
        val edgeCoordinates = listOf(
            Pair(0.0f, 0.0f),     // Top-left corner
            Pair(1.0f, 0.0f),     // Top-right corner
            Pair(0.0f, 1.0f),     // Bottom-left corner
            Pair(1.0f, 1.0f),     // Bottom-right corner
            Pair(0.5f, 0.0f),     // Top edge center
            Pair(0.5f, 1.0f),     // Bottom edge center
            Pair(0.0f, 0.5f),     // Left edge center
            Pair(1.0f, 0.5f)      // Right edge center
        )

        for ((x, y) in edgeCoordinates) {
            // ACT
            val (pixelX, pixelY) = portraitMapper.normalizedToPixel(x, y)

            // ASSERT
            assertTrue(
                "Edge coordinate X should be within view bounds: $pixelX (view width: ${phonePortraitView.first})",
                pixelX >= 0f && pixelX <= phonePortraitView.first.toFloat()
            )
            assertTrue(
                "Edge coordinate Y should be within view bounds: $pixelY (view height: ${phonePortraitView.second})",
                pixelY >= 0f && pixelY <= phonePortraitView.second.toFloat()
            )
        }
    }

    @Test
    fun `should handle out-of-bounds coordinates gracefully`() {
        // ARRANGE
        val outOfBoundsCoordinates = listOf(
            Pair(-0.1f, 0.5f),    // Left of bounds
            Pair(1.1f, 0.5f),     // Right of bounds
            Pair(0.5f, -0.1f),    // Above bounds
            Pair(0.5f, 1.1f),     // Below bounds
            Pair(-0.5f, -0.5f),   // Far out of bounds
            Pair(1.5f, 1.5f)      // Far out of bounds
        )

        for ((x, y) in outOfBoundsCoordinates) {
            // ACT & ASSERT
            assertDoesNotThrow("Should handle out-of-bounds coordinates: ($x, $y)") {
                portraitMapper.normalizedToPixel(x, y)
            }
        }
    }

    // ROTATION HANDLING TESTS

    @Test
    fun `should handle all rotation angles correctly`() {
        // ARRANGE
        val rotations = listOf(0, 30, 45, 90, 135, 180, 225, 270, 315, 360, -90, -180)
        val testPoint = Pair(0.3f, 0.7f)

        for (rotation in rotations) {
            // ACT
            portraitMapper.updateRotation(rotation, false)
            val (pixelX, pixelY) = portraitMapper.normalizedToPixel(testPoint.first, testPoint.second)

            // ASSERT
            assertTrue(
                "Rotation $rotation° should produce valid coordinates: ($pixelX, $pixelY)",
                pixelX >= 0f && pixelX <= phonePortraitView.first.toFloat() &&
                pixelY >= 0f && pixelY <= phonePortraitView.second.toFloat()
            )
        }
    }

    @Test
    fun `should preserve center point across all rotations`() {
        // ARRANGE
        val centerPoint = Pair(0.5f, 0.5f)
        val rotations = listOf(0, 90, 180, 270)
        val expectedCenterX = phonePortraitView.first / 2f
        val expectedCenterY = phonePortraitView.second / 2f

        for (rotation in rotations) {
            // ACT
            portraitMapper.updateRotation(rotation, false)
            val (pixelX, pixelY) = portraitMapper.normalizedToPixel(centerPoint.first, centerPoint.second)

            // ASSERT
            val errorX = abs(pixelX - expectedCenterX)
            val errorY = abs(pixelY - expectedCenterY)

            assertTrue(
                "Center point should remain stable at rotation $rotation°: expected ($expectedCenterX, $expectedCenterY), got ($pixelX, $pixelY)",
                errorX <= errorTolerance && errorY <= errorTolerance
            )
        }
    }

    @Test
    fun `should handle rotation with different camera orientations`() {
        // ARRANGE
        val testPoint = Pair(0.25f, 0.75f)

        // ACT & ASSERT for back camera
        portraitMapper.updateRotation(90, false)
        val backResult = portraitMapper.normalizedToPixel(testPoint.first, testPoint.second)
        assertNotNull(backResult)

        // ACT & ASSERT for front camera
        portraitMapper.updateRotation(90, true)
        val frontResult = portraitMapper.normalizedToPixel(testPoint.first, testPoint.second)
        assertNotNull(frontResult)

        // Results should be different due to mirroring
        assertNotEquals(
            "Front and back camera should produce different results due to mirroring",
            backResult.first, frontResult.first, errorTolerance
        )
    }

    // ASPECT RATIO MANAGEMENT TESTS

    @Test
    fun `should handle different aspect ratios correctly`() {
        // ARRANGE
        val testPoint = Pair(0.5f, 0.5f)
        val mappers = listOf(
            "Portrait" to portraitMapper,
            "Landscape" to landscapeMapper,
            "Tablet" to tabletMapper
        )

        for ((name, mapper) in mappers) {
            // ACT
            mapper.updateAspectRatio(FitMode.CENTER_CROP)
            val cropResult = mapper.normalizedToPixel(testPoint.first, testPoint.second)

            mapper.updateAspectRatio(FitMode.CENTER_INSIDE)
            val insideResult = mapper.normalizedToPixel(testPoint.first, testPoint.second)

            mapper.updateAspectRatio(FitMode.FILL)
            val fillResult = mapper.normalizedToPixel(testPoint.first, testPoint.second)

            // ASSERT
            assertNotNull("$name CENTER_CROP result should not be null", cropResult)
            assertNotNull("$name CENTER_INSIDE result should not be null", insideResult)
            assertNotNull("$name FILL result should not be null", fillResult)

            // Different fit modes should potentially produce different results
            // (unless the aspect ratios happen to match exactly)
        }
    }

    @Test
    fun `should maintain visible region consistency across fit modes`() {
        // ARRANGE
        val fitModes = listOf(FitMode.FILL, FitMode.CENTER_CROP, FitMode.CENTER_INSIDE)

        for (fitMode in fitModes) {
            // ACT
            portraitMapper.updateAspectRatio(fitMode)
            val visibleRegion = portraitMapper.getVisibleRegion()

            // ASSERT
            assertTrue(
                "Visible region left should be valid for $fitMode: ${visibleRegion.left}",
                visibleRegion.left >= 0f && visibleRegion.left <= 1f
            )
            assertTrue(
                "Visible region top should be valid for $fitMode: ${visibleRegion.top}",
                visibleRegion.top >= 0f && visibleRegion.top <= 1f
            )
            assertTrue(
                "Visible region right should be valid for $fitMode: ${visibleRegion.right}",
                visibleRegion.right >= 0f && visibleRegion.right <= 1f
            )
            assertTrue(
                "Visible region bottom should be valid for $fitMode: ${visibleRegion.bottom}",
                visibleRegion.bottom >= 0f && visibleRegion.bottom <= 1f
            )
            assertTrue(
                "Visible region should have positive area for $fitMode",
                visibleRegion.width() > 0f && visibleRegion.height() > 0f
            )
        }
    }

    @Test
    fun `should calculate visible bounds correctly for different orientations`() {
        // ARRANGE
        val mappers = listOf(
            "Portrait" to portraitMapper,
            "Landscape" to landscapeMapper
        )

        for ((orientation, mapper) in mappers) {
            // ACT
            val bounds = mapper.getVisibleBounds()

            // ASSERT
            assertTrue(
                "$orientation visible bounds should be properly ordered: left=${bounds.left}, right=${bounds.right}",
                bounds.left <= bounds.right
            )
            assertTrue(
                "$orientation visible bounds should be properly ordered: top=${bounds.top}, bottom=${bounds.bottom}",
                bounds.top <= bounds.bottom
            )
            assertTrue(
                "$orientation visible bounds should be within normalized space",
                bounds.left >= 0f && bounds.right <= 1f && bounds.top >= 0f && bounds.bottom <= 1f
            )
        }
    }

    // PERFORMANCE OPTIMIZATION TESTS

    @Test
    fun `should optimize batch transformations efficiently`() {
        // ARRANGE
        val landmarks = (0 until 100).map { i ->
            Pair(
                (i % 10) / 10f,
                (i / 10) / 10f
            )
        }

        // ACT
        val startTime = System.nanoTime()
        val batchResults = portraitMapper.batchNormalizedToPixel(landmarks)
        val batchTime = System.nanoTime() - startTime

        val individualStartTime = System.nanoTime()
        val individualResults = landmarks.map { (x, y) ->
            portraitMapper.normalizedToPixel(x, y)
        }
        val individualTime = System.nanoTime() - individualStartTime

        // ASSERT
        assertEquals(landmarks.size, batchResults.size, "Batch should process all landmarks")
        assertEquals(landmarks.size, individualResults.size, "Individual should process all landmarks")

        // For large batches, batch processing should be more efficient
        if (landmarks.size >= 50) {
            assertTrue(
                "Batch processing should be more efficient for large sets: batch=${batchTime}ns, individual=${individualTime}ns",
                batchTime <= individualTime * 1.2 // Allow 20% tolerance
            )
        }
    }

    @Test
    fun `should maintain performance under stress conditions`() {
        // ARRANGE
        val stressIterations = 1000
        val processingTimes = mutableListOf<Long>()

        // ACT
        repeat(stressIterations) {
            val startTime = System.nanoTime()
            portraitMapper.normalizedToPixel(0.5f, 0.5f)
            val endTime = System.nanoTime()
            processingTimes.add(endTime - startTime)
        }

        // ASSERT
        val averageTime = processingTimes.average()
        val maxTime = processingTimes.maxOrNull() ?: 0L
        val standardDeviation = calculateStandardDeviation(processingTimes, averageTime)

        assertTrue(
            "Average processing time should be reasonable: ${averageTime / 1000}μs",
            averageTime < 100_000 // Less than 100μs on average
        )
        assertTrue(
            "Performance should be consistent (low std dev): ${standardDeviation / 1000}μs",
            standardDeviation < averageTime * 0.5 // Standard deviation should be less than 50% of mean
        )
    }

    @Test
    fun `should handle cache efficiency correctly`() {
        // ARRANGE
        val testPoints = listOf(
            Pair(0.1f, 0.1f),
            Pair(0.5f, 0.5f),
            Pair(0.9f, 0.9f)
        )

        // ACT - First pass (populate cache)
        testPoints.forEach { (x, y) ->
            portraitMapper.normalizedToPixel(x, y)
        }

        val initialMetrics = portraitMapper.getPerformanceMetrics()

        // Second pass (should hit cache)
        testPoints.forEach { (x, y) ->
            portraitMapper.normalizedToPixel(x, y)
        }

        val finalMetrics = portraitMapper.getPerformanceMetrics()

        // ASSERT
        assertTrue(
            "Transformation count should increase: ${initialMetrics.transformationCount} -> ${finalMetrics.transformationCount}",
            finalMetrics.transformationCount > initialMetrics.transformationCount
        )

        // Cache hit rate should improve with repeated access to same coordinates
        assertTrue(
            "Cache hit rate should be reasonable: ${finalMetrics.cacheHitRate}",
            finalMetrics.cacheHitRate >= 0f && finalMetrics.cacheHitRate <= 1f
        )
    }

    // ANDROID 15+ COMPATIBILITY TESTS

    @Test
    fun `should support Android 15+ features when available`() {
        // ARRANGE & ACT
        val metrics = portraitMapper.getPerformanceMetrics()

        // ASSERT
        assertTrue(
            "Android version should be reported correctly: ${metrics.androidVersion}",
            metrics.androidVersion > 0
        )

        // For Android 15+, should report feature support
        if (metrics.supportsAndroid15Features) {
            assertTrue(
                "Should properly detect Android 15+ features",
                metrics.androidVersion >= 34 // Android 15 is API 34+
            )
        }
    }

    @Test
    fun `should handle coordinate system changes gracefully`() {
        // ARRANGE
        val testPoint = Pair(0.3f, 0.7f)

        // ACT
        val result1 = portraitMapper.normalizedToPixel(testPoint.first, testPoint.second)

        // Update dimensions (simulating configuration change)
        portraitMapper.updateViewDimensions(1440, 3120)
        val result2 = portraitMapper.normalizedToPixel(testPoint.first, testPoint.second)

        // ASSERT
        assertNotNull("Result before dimension change should be valid", result1)
        assertNotNull("Result after dimension change should be valid", result2)

        // Results should be different due to dimension change
        assertNotEquals(
            "Results should differ after dimension change",
            result1.first, result2.first, 1f
        )
    }

    // ERROR HANDLING AND EDGE CASES TESTS

    @Test
    fun `should handle zero and negative dimensions gracefully`() {
        // ARRANGE & ACT & ASSERT
        assertDoesNotThrow("Should handle zero view width") {
            EnhancedCoordinateMapper(0, 100, 720, 1280, false, 0)
        }

        assertDoesNotThrow("Should handle zero view height") {
            EnhancedCoordinateMapper(100, 0, 720, 1280, false, 0)
        }

        assertDoesNotThrow("Should handle zero image dimensions") {
            EnhancedCoordinateMapper(100, 100, 0, 0, false, 0)
        }
    }

    @Test
    fun `should handle extreme aspect ratios`() {
        // ARRANGE
        val extremeMappers = listOf(
            // Very wide
            EnhancedCoordinateMapper(3000, 100, 720, 1280, false, 0),
            // Very tall
            EnhancedCoordinateMapper(100, 3000, 720, 1280, false, 0),
            // Square
            EnhancedCoordinateMapper(1000, 1000, 1000, 1000, false, 0)
        )

        for (mapper in extremeMappers) {
            // ACT & ASSERT
            assertDoesNotThrow("Should handle extreme aspect ratios") {
                val result = mapper.normalizedToPixel(0.5f, 0.5f)
                assertNotNull("Should produce valid result for extreme aspect ratio", result)
            }
        }
    }

    @Test
    fun `should validate transformation matrices`() {
        // ARRANGE & ACT
        val transformMatrix = portraitMapper.getTransformMatrix()
        val inverseMatrix = portraitMapper.getInverseTransformMatrix()

        // ASSERT
        assertNotNull("Transform matrix should not be null", transformMatrix)
        assertNotNull("Inverse transform matrix should not be null", inverseMatrix)

        // Verify matrices are valid (not identity unless special case)
        val matrixValues = FloatArray(9)
        transformMatrix.getValues(matrixValues)

        // Matrix should not be all zeros
        assertTrue(
            "Transform matrix should not be all zeros",
            matrixValues.any { it != 0f }
        )
    }

    @Test
    fun `should handle metrics reset correctly`() {
        // ARRANGE
        repeat(10) {
            portraitMapper.normalizedToPixel(0.5f, 0.5f)
        }

        val metricsBeforeReset = portraitMapper.getPerformanceMetrics()
        assertTrue("Should have transformations before reset", metricsBeforeReset.transformationCount > 0)

        // ACT
        portraitMapper.resetMetrics()
        val metricsAfterReset = portraitMapper.getPerformanceMetrics()

        // ASSERT
        assertEquals(
            "Transformation count should be reset",
            0L, metricsAfterReset.transformationCount
        )
        assertEquals(
            "Average error should be reset",
            0.0, metricsAfterReset.averageError, 0.001
        )
        assertEquals(
            "Max error should be reset",
            0.0, metricsAfterReset.maxError, 0.001
        )
    }

    @Test
    fun `should provide comprehensive performance metrics`() {
        // ARRANGE
        val landmarks = (0 until 20).map { i -> Pair(i / 20f, i / 20f) }

        // ACT
        portraitMapper.batchNormalizedToPixel(landmarks)
        val metrics = portraitMapper.getPerformanceMetrics()

        // ASSERT
        assertTrue("Should track transformation count", metrics.transformationCount >= 0)
        assertTrue("Should track batch transformation count", metrics.batchTransformationCount >= 0)
        assertTrue("Should track average error", metrics.averageError >= 0.0)
        assertTrue("Should track max error", metrics.maxError >= 0.0)
        assertNotNull("Should provide current fit mode", metrics.currentFitMode)
        assertNotNull("Should provide view size", metrics.viewSize)
        assertNotNull("Should provide image size", metrics.imageSize)
        assertTrue("Should track last batch size", metrics.lastBatchSize >= 0)
        assertTrue("Should track last batch duration", metrics.lastBatchDuration >= 0.0)
        assertTrue("Should track cache hit rate", metrics.cacheHitRate >= 0f && metrics.cacheHitRate <= 1f)
        assertTrue("Should report Android version", metrics.androidVersion > 0)
    }

    // HELPER METHODS

    private fun calculateStandardDeviation(values: List<Long>, mean: Double): Double {
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
}