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

/**
 * TDD test suite for EnhancedCoordinateMapper rotation and transformation issues.
 * These tests are designed to FAIL initially to expose bugs in coordinate mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EnhancedCoordinateMapperTest {

    // Test dimensions
    private val viewWidth = 1080
    private val viewHeight = 2340
    private val imageWidth = 720
    private val imageHeight = 1280

    private val errorTolerance = 2.0f

    private lateinit var portraitMapper: EnhancedCoordinateMapper
    private lateinit var landscapeMapper: EnhancedCoordinateMapper
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
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isFrontFacing = false,
            rotation = 0
        )

        landscapeMapper = EnhancedCoordinateMapper(
            viewWidth = viewHeight, // Swapped for landscape
            viewHeight = viewWidth,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isFrontFacing = false,
            rotation = 90
        )

        frontCameraMapper = EnhancedCoordinateMapper(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isFrontFacing = true,
            rotation = 0
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test FILL mode - normalized coordinates map to exact pixel positions`() {
        // ARRANGE: Use FILL mode for direct mapping
        portraitMapper.updateAspectRatio(FitMode.FILL)

        val testCases = listOf(
            Triple(0.0f, 0.0f, Pair(0f, 0f)),                                    // Top-left
            Triple(1.0f, 0.0f, Pair(viewWidth.toFloat(), 0f)),                   // Top-right
            Triple(0.5f, 0.5f, Pair(viewWidth / 2f, viewHeight / 2f)),          // Center
            Triple(1.0f, 1.0f, Pair(viewWidth.toFloat(), viewHeight.toFloat())), // Bottom-right
            Triple(0.0f, 1.0f, Pair(0f, viewHeight.toFloat()))                   // Bottom-left
        )

        for ((normX, normY, expected) in testCases) {
            // ACT: Transform coordinates
            val (actualX, actualY) = portraitMapper.normalizedToPixel(normX, normY)

            // ASSERT: Should map exactly in FILL mode
            val errorX = abs(actualX - expected.first)
            val errorY = abs(actualY - expected.second)

            assertTrue(
                "FILL mode X error for ($normX, $normY): expected ${expected.first}, got $actualX, error $errorX",
                errorX <= errorTolerance
            )
            assertTrue(
                "FILL mode Y error for ($normX, $normY): expected ${expected.second}, got $actualY, error $errorY",
                errorY <= errorTolerance
            )
        }
    }

    @Test
    fun `test CENTER_CROP mode maintains aspect ratio`() {
        // ARRANGE: Use CENTER_CROP mode
        portraitMapper.updateAspectRatio(FitMode.CENTER_CROP)

        // Calculate expected behavior for center crop
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()

        // ACT: Transform center point
        val (centerX, centerY) = portraitMapper.normalizedToPixel(0.5f, 0.5f)

        // ASSERT: Center should still map to center
        val expectedCenterX = viewWidth / 2f
        val expectedCenterY = viewHeight / 2f

        val errorX = abs(centerX - expectedCenterX)
        val errorY = abs(centerY - expectedCenterY)

        assertTrue(
            "CENTER_CROP center X error: expected $expectedCenterX, got $centerX, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "CENTER_CROP center Y error: expected $expectedCenterY, got $centerY, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test CENTER_INSIDE mode fits entire image`() {
        // ARRANGE: Use CENTER_INSIDE mode
        portraitMapper.updateAspectRatio(FitMode.CENTER_INSIDE)

        // All normalized coordinates should be visible
        val testPoints = listOf(
            Pair(0.0f, 0.0f),   // Corners should all be visible
            Pair(1.0f, 0.0f),
            Pair(0.0f, 1.0f),
            Pair(1.0f, 1.0f),
            Pair(0.5f, 0.5f)    // Center
        )

        for ((x, y) in testPoints) {
            // ACT: Check visibility
            val isVisible = portraitMapper.isPointVisible(x, y)

            // ASSERT: All points should be visible in CENTER_INSIDE
            assertTrue(
                "Point ($x, $y) should be visible in CENTER_INSIDE mode",
                isVisible
            )
        }
    }

    @Test
    fun `test front camera mirroring works correctly`() {
        // ARRANGE: Front camera mapper
        val testCases = listOf(
            Triple(0.0f, 0.5f, 1.0f), // Left side becomes right side
            Triple(0.25f, 0.5f, 0.75f), // Quarter from left becomes quarter from right
            Triple(0.5f, 0.5f, 0.5f), // Center stays center
            Triple(0.75f, 0.5f, 0.25f), // Quarter from right becomes quarter from left
            Triple(1.0f, 0.5f, 0.0f)  // Right side becomes left side
        )

        for ((inputX, inputY, expectedMirroredX) in testCases) {
            // ACT: Transform with front camera
            val (actualX, actualY) = frontCameraMapper.normalizedToPixel(inputX, inputY)

            // Calculate expected pixel position with mirroring
            val expectedPixelX = expectedMirroredX * viewWidth
            val expectedPixelY = inputY * viewHeight

            // ASSERT: X should be mirrored, Y should be unchanged
            val errorX = abs(actualX - expectedPixelX)
            val errorY = abs(actualY - expectedPixelY)

            assertTrue(
                "Front camera mirroring X error for input ($inputX, $inputY): expected $expectedPixelX, got $actualX, error $errorX",
                errorX <= errorTolerance
            )
            assertTrue(
                "Front camera Y should be unchanged for input ($inputX, $inputY): expected $expectedPixelY, got $actualY, error $errorY",
                errorY <= errorTolerance
            )
        }
    }

    @Test
    fun `test batch transformation performance and accuracy`() {
        // ARRANGE: Large set of landmarks (simulating full body pose)
        val landmarks = (0..32).map { i ->
            Pair(
                (i % 10) / 10f,  // X varies from 0.0 to 0.9
                (i / 10) / 10f   // Y varies from 0.0 to 0.3
            )
        }

        // ACT: Transform individually and in batch
        val startTime = System.nanoTime()
        val individualResults = landmarks.map { (x, y) ->
            portraitMapper.normalizedToPixel(x, y)
        }
        val individualTime = System.nanoTime() - startTime

        val batchStartTime = System.nanoTime()
        val batchResults = portraitMapper.batchNormalizedToPixel(landmarks)
        val batchTime = System.nanoTime() - batchStartTime

        // ASSERT: Results should match
        assertEquals("Result count mismatch", individualResults.size, batchResults.size)

        for (i in individualResults.indices) {
            val (indX, indY) = individualResults[i]
            val (batchX, batchY) = batchResults[i]

            val errorX = abs(indX - batchX)
            val errorY = abs(indY - batchY)

            assertTrue(
                "Batch accuracy X error at index $i: error $errorX",
                errorX <= errorTolerance
            )
            assertTrue(
                "Batch accuracy Y error at index $i: error $errorY",
                errorY <= errorTolerance
            )
        }

        // Performance check (batch should be faster for large sets)
        assertTrue(
            "Batch processing should be more efficient for large sets. Individual: ${individualTime}ns, Batch: ${batchTime}ns",
            batchTime <= individualTime || landmarks.size < 10 // Allow for small overhead on small sets
        )
    }

    @Test
    fun `test coordinate round-trip maintains precision`() {
        // ARRANGE: Various normalized coordinates
        val testPoints = listOf(
            Pair(0.0f, 0.0f),
            Pair(0.1f, 0.1f),
            Pair(0.5f, 0.5f),
            Pair(0.9f, 0.9f),
            Pair(1.0f, 1.0f),
            Pair(0.333f, 0.666f),
            Pair(0.125f, 0.875f)
        )

        for ((originalX, originalY) in testPoints) {
            // ACT: Round-trip transformation
            val (pixelX, pixelY) = portraitMapper.normalizedToPixel(originalX, originalY)
            val (roundTripX, roundTripY) = portraitMapper.pixelToNormalized(pixelX, pixelY)

            // ASSERT: Should return to original coordinates within tolerance
            val errorX = abs(originalX - roundTripX)
            val errorY = abs(originalY - roundTripY)

            assertTrue(
                "Round-trip X precision error for ($originalX, $originalY): error $errorX",
                errorX <= 0.01f // 1% tolerance in normalized space
            )
            assertTrue(
                "Round-trip Y precision error for ($originalX, $originalY): error $errorY",
                errorY <= 0.01f
            )
        }
    }

    @Test
    fun `test rotation update preserves center mapping`() {
        // ARRANGE: Start with no rotation
        val centerX = 0.5f
        val centerY = 0.5f
        val (originalPixelX, originalPixelY) = portraitMapper.normalizedToPixel(centerX, centerY)

        // Test different rotations
        val rotations = listOf(0, 90, 180, 270)

        for (rotation in rotations) {
            // ACT: Update rotation
            portraitMapper.updateRotation(rotation, false)
            val (rotatedPixelX, rotatedPixelY) = portraitMapper.normalizedToPixel(centerX, centerY)

            // ASSERT: Center point should remain close to screen center regardless of rotation
            val expectedX = viewWidth / 2f
            val expectedY = viewHeight / 2f

            val errorX = abs(rotatedPixelX - expectedX)
            val errorY = abs(rotatedPixelY - expectedY)

            assertTrue(
                "Center mapping with ${rotation}° rotation X error: expected $expectedX, got $rotatedPixelX, error $errorX",
                errorX <= errorTolerance
            )
            assertTrue(
                "Center mapping with ${rotation}° rotation Y error: expected $expectedY, got $rotatedPixelY, error $errorY",
                errorY <= errorTolerance
            )
        }
    }

    @Test
    fun `test visible region calculation accuracy`() {
        // ARRANGE: Different fit modes
        val fitModes = listOf(FitMode.FILL, FitMode.CENTER_CROP, FitMode.CENTER_INSIDE)

        for (fitMode in fitModes) {
            // ACT: Update fit mode and get visible region
            portraitMapper.updateAspectRatio(fitMode)
            val visibleRegion = portraitMapper.getVisibleRegion()

            // ASSERT: Visible region should be valid
            assertTrue(
                "Visible region left should be >= 0 for $fitMode",
                visibleRegion.left >= 0f
            )
            assertTrue(
                "Visible region top should be >= 0 for $fitMode",
                visibleRegion.top >= 0f
            )
            assertTrue(
                "Visible region right should be <= 1 for $fitMode",
                visibleRegion.right <= 1f
            )
            assertTrue(
                "Visible region bottom should be <= 1 for $fitMode",
                visibleRegion.bottom <= 1f
            )
            assertTrue(
                "Visible region should have positive width for $fitMode",
                visibleRegion.width() > 0f
            )
            assertTrue(
                "Visible region should have positive height for $fitMode",
                visibleRegion.height() > 0f
            )
        }
    }

    @Test
    fun `test dimension update triggers recalculation`() {
        // ARRANGE: Initial state
        val (initialX, initialY) = portraitMapper.normalizedToPixel(0.5f, 0.5f)

        // ACT: Update view dimensions
        val newViewWidth = 1440
        val newViewHeight = 3120
        portraitMapper.updateViewDimensions(newViewWidth, newViewHeight)

        val (updatedX, updatedY) = portraitMapper.normalizedToPixel(0.5f, 0.5f)

        // ASSERT: Center mapping should reflect new dimensions
        val expectedX = newViewWidth / 2f
        val expectedY = newViewHeight / 2f

        val errorX = abs(updatedX - expectedX)
        val errorY = abs(updatedY - expectedY)

        assertTrue(
            "Updated view dimensions X mapping error: expected $expectedX, got $updatedX, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "Updated view dimensions Y mapping error: expected $expectedY, got $updatedY, error $errorY",
            errorY <= errorTolerance
        )

        // Should be different from initial mapping
        assertNotEquals(
            "Mapping should change after dimension update",
            initialX,
            updatedX,
            errorTolerance
        )
    }

    @Test
    fun `test performance metrics tracking`() {
        // ARRANGE: Perform some transformations
        val testPoints = listOf(
            Pair(0.1f, 0.1f),
            Pair(0.5f, 0.5f),
            Pair(0.9f, 0.9f)
        )

        // ACT: Transform points and get metrics
        testPoints.forEach { (x, y) ->
            portraitMapper.normalizedToPixel(x, y)
        }

        val metrics = portraitMapper.getPerformanceMetrics()

        // ASSERT: Metrics should be tracked
        assertTrue(
            "Transformation count should be > 0",
            metrics.transformationCount > 0
        )
        assertTrue(
            "Average error should be >= 0",
            metrics.averageError >= 0.0
        )
        assertTrue(
            "Max error should be >= 0",
            metrics.maxError >= 0.0
        )
        assertNotNull("View size should not be null", metrics.viewSize)
        assertNotNull("Image size should not be null", metrics.imageSize)
    }

    @Test
    fun `test metrics reset functionality`() {
        // ARRANGE: Perform transformations to accumulate metrics
        repeat(10) {
            portraitMapper.normalizedToPixel(0.5f, 0.5f)
        }

        val metricsBeforeReset = portraitMapper.getPerformanceMetrics()
        assertTrue("Should have transformation count > 0", metricsBeforeReset.transformationCount > 0)

        // ACT: Reset metrics
        portraitMapper.resetMetrics()
        val metricsAfterReset = portraitMapper.getPerformanceMetrics()

        // ASSERT: Metrics should be reset
        assertEquals(
            "Transformation count should be reset to 0",
            0L,
            metricsAfterReset.transformationCount
        )
        assertEquals(
            "Average error should be reset to 0",
            0.0,
            metricsAfterReset.averageError,
            0.001
        )
        assertEquals(
            "Max error should be reset to 0",
            0.0,
            metricsAfterReset.maxError,
            0.001
        )
    }
}