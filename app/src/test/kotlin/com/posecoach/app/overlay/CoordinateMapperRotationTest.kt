package com.posecoach.app.overlay

import android.graphics.Matrix
import android.graphics.RectF
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
 * TDD test suite for coordinate transformation rotation issues.
 * These tests are designed to FAIL initially to expose the rotation bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoordinateMapperRotationTest {

    // Test view dimensions (simulating phone screen)
    private val viewWidth = 1080
    private val viewHeight = 2340

    // Test image dimensions (simulating camera preview)
    private val imageWidth = 720
    private val imageHeight = 1280

    // Error tolerance for coordinate precision (in pixels)
    private val errorTolerance = 2.0f

    private lateinit var portraitMapper: CoordinateMapper
    private lateinit var landscapeMapper: CoordinateMapper
    private lateinit var frontCameraMapper: CoordinateMapper

    @Before
    fun setup() {
        // Mock Timber to avoid log errors
        mockkStatic(Timber::class)
        every { Timber.d(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.e(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs
        // Portrait mode (0° rotation)
        portraitMapper = CoordinateMapper(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isFrontFacing = false,
            rotation = 0
        )

        // Landscape mode (90° rotation)
        landscapeMapper = CoordinateMapper(
            viewWidth = viewHeight, // Swapped for landscape
            viewHeight = viewWidth,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isFrontFacing = false,
            rotation = 90
        )

        // Front camera (with mirroring)
        frontCameraMapper = CoordinateMapper(
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
    fun `test portrait mode - center point maps to screen center`() {
        // ARRANGE: Center point in normalized coordinates
        val normalizedX = 0.5f
        val normalizedY = 0.5f
        val expectedX = viewWidth / 2f
        val expectedY = viewHeight / 2f

        // ACT: Transform to pixel coordinates
        val (actualX, actualY) = portraitMapper.normalizedToPixel(normalizedX, normalizedY)

        // ASSERT: Should map to center of screen
        val errorX = abs(actualX - expectedX)
        val errorY = abs(actualY - expectedY)

        assertTrue(
            "Center point X mapping error too large: expected $expectedX, got $actualX, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "Center point Y mapping error too large: expected $expectedY, got $actualY, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test portrait mode - top center landmark maps correctly`() {
        // ARRANGE: Top center point (like nose landmark)
        val normalizedX = 0.5f
        val normalizedY = 0.2f
        val expectedX = viewWidth / 2f
        val expectedY = viewHeight * 0.2f

        // ACT: Transform to pixel coordinates
        val (actualX, actualY) = portraitMapper.normalizedToPixel(normalizedX, normalizedY)

        // ASSERT: Should map to top-center area of screen
        val errorX = abs(actualX - expectedX)
        val errorY = abs(actualY - expectedY)

        assertTrue(
            "Top center X mapping error: expected $expectedX, got $actualX, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "Top center Y mapping error: expected $expectedY, got $actualY, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test landscape mode - center point maps correctly after 90 degree rotation`() {
        // ARRANGE: Center point should remain at center even after rotation
        val normalizedX = 0.5f
        val normalizedY = 0.5f
        // In landscape, width/height are swapped
        val expectedX = viewHeight / 2f // Originally width becomes height
        val expectedY = viewWidth / 2f  // Originally height becomes width

        // ACT: Transform to pixel coordinates
        val (actualX, actualY) = landscapeMapper.normalizedToPixel(normalizedX, normalizedY)

        // ASSERT: Center should map to center regardless of rotation
        val errorX = abs(actualX - expectedX)
        val errorY = abs(actualY - expectedY)

        assertTrue(
            "Landscape center X mapping error: expected $expectedX, got $actualX, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "Landscape center Y mapping error: expected $expectedY, got $actualY, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test front camera mirroring - left point becomes right point`() {
        // ARRANGE: Point on left side of image (user's right shoulder)
        val normalizedX = 0.2f // Left side in normalized coords
        val normalizedY = 0.4f
        // With front camera, left becomes right due to mirroring
        val expectedX = viewWidth * (1.0f - 0.2f) // Should be mirrored to right side
        val expectedY = viewHeight * 0.4f

        // ACT: Transform with front camera
        val (actualX, actualY) = frontCameraMapper.normalizedToPixel(normalizedX, normalizedY)

        // ASSERT: X coordinate should be mirrored
        val errorX = abs(actualX - expectedX)
        val errorY = abs(actualY - expectedY)

        assertTrue(
            "Front camera mirroring X error: expected $expectedX, got $actualX, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "Front camera Y error: expected $expectedY, got $actualY, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test skeleton orientation - vertical person stays vertical in portrait`() {
        // ARRANGE: Vertical line representing a standing person
        val headX = 0.5f
        val headY = 0.2f
        val feetX = 0.5f
        val feetY = 0.8f

        // ACT: Transform head and feet landmarks
        val (headPixelX, headPixelY) = portraitMapper.normalizedToPixel(headX, headY)
        val (feetPixelX, feetPixelY) = portraitMapper.normalizedToPixel(feetX, feetY)

        // ASSERT: Person should remain vertical (same X, different Y)
        val xDifference = abs(headPixelX - feetPixelX)
        assertTrue(
            "Person should be vertical in portrait mode: head X=$headPixelX, feet X=$feetPixelX, diff=$xDifference",
            xDifference <= errorTolerance
        )

        // Head should be above feet
        assertTrue(
            "Head should be above feet in portrait: head Y=$headPixelY, feet Y=$feetPixelY",
            headPixelY < feetPixelY
        )
    }

    @Test
    fun `test coordinate round-trip accuracy`() {
        // ARRANGE: Test various normalized coordinates
        val testPoints = listOf(
            Pair(0.0f, 0.0f),   // Top-left corner
            Pair(1.0f, 0.0f),   // Top-right corner
            Pair(0.5f, 0.5f),   // Center
            Pair(0.0f, 1.0f),   // Bottom-left corner
            Pair(1.0f, 1.0f),   // Bottom-right corner
            Pair(0.3f, 0.7f),   // Arbitrary point
        )

        for ((originalX, originalY) in testPoints) {
            // ACT: Transform to pixel and back to normalized
            val (pixelX, pixelY) = portraitMapper.normalizedToPixel(originalX, originalY)
            val (roundTripX, roundTripY) = portraitMapper.pixelToNormalized(pixelX, pixelY)

            // ASSERT: Round-trip should return close to original
            val errorX = abs(originalX - roundTripX)
            val errorY = abs(originalY - roundTripY)

            assertTrue(
                "Round-trip X error for ($originalX, $originalY): error $errorX",
                errorX <= 0.02f // Allow 2% error in normalized coordinates
            )
            assertTrue(
                "Round-trip Y error for ($originalX, $originalY): error $errorY",
                errorY <= 0.02f
            )
        }
    }

    @Test
    fun `test batch transformation consistency`() {
        // ARRANGE: List of landmarks representing a skeleton
        val landmarks = listOf(
            Pair(0.5f, 0.1f),  // Head
            Pair(0.5f, 0.3f),  // Neck
            Pair(0.3f, 0.4f),  // Left shoulder
            Pair(0.7f, 0.4f),  // Right shoulder
            Pair(0.5f, 0.6f),  // Torso center
            Pair(0.4f, 0.8f),  // Left hip
            Pair(0.6f, 0.8f),  // Right hip
        )

        // ACT: Transform individually and in batch
        val individualResults = landmarks.map { (x, y) ->
            portraitMapper.normalizedToPixel(x, y)
        }
        val batchResults = portraitMapper.batchNormalizedToPixel(landmarks)

        // ASSERT: Batch and individual results should match
        assertEquals("Batch and individual results count mismatch", individualResults.size, batchResults.size)

        for (i in individualResults.indices) {
            val (indX, indY) = individualResults[i]
            val (batchX, batchY) = batchResults[i]

            val errorX = abs(indX - batchX)
            val errorY = abs(indY - batchY)

            assertTrue(
                "Batch vs individual X error at index $i: error $errorX",
                errorX <= errorTolerance
            )
            assertTrue(
                "Batch vs individual Y error at index $i: error $errorY",
                errorY <= errorTolerance
            )
        }
    }

    @Test
    fun `test 180 degree rotation transformation`() {
        // ARRANGE: Mapper with 180° rotation
        val rotated180Mapper = CoordinateMapper(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isFrontFacing = false,
            rotation = 180
        )

        // Top-left point in normalized coordinates
        val normalizedX = 0.0f
        val normalizedY = 0.0f
        // After 180° rotation, should map to bottom-right
        val expectedX = viewWidth.toFloat()
        val expectedY = viewHeight.toFloat()

        // ACT: Transform coordinates
        val (actualX, actualY) = rotated180Mapper.normalizedToPixel(normalizedX, normalizedY)

        // ASSERT: Point should be rotated 180 degrees
        val errorX = abs(actualX - expectedX)
        val errorY = abs(actualY - expectedY)

        assertTrue(
            "180° rotation X error: expected $expectedX, got $actualX, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "180° rotation Y error: expected $expectedY, got $actualY, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test 270 degree rotation transformation`() {
        // ARRANGE: Mapper with 270° rotation
        val rotated270Mapper = CoordinateMapper(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isFrontFacing = false,
            rotation = 270
        )

        // Center point should remain at center
        val normalizedX = 0.5f
        val normalizedY = 0.5f
        val expectedX = viewWidth / 2f
        val expectedY = viewHeight / 2f

        // ACT: Transform coordinates
        val (actualX, actualY) = rotated270Mapper.normalizedToPixel(normalizedX, normalizedY)

        // ASSERT: Center should remain at center
        val errorX = abs(actualX - expectedX)
        val errorY = abs(actualY - expectedY)

        assertTrue(
            "270° rotation center X error: expected $expectedX, got $actualX, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "270° rotation center Y error: expected $expectedY, got $actualY, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test visible bounds calculation accuracy`() {
        // ARRANGE: Get visible bounds from mapper
        val bounds = portraitMapper.getVisibleBounds()

        // ASSERT: Bounds should be valid and within expected range
        assertTrue("Left bound should be >= 0", bounds.left >= 0f)
        assertTrue("Top bound should be >= 0", bounds.top >= 0f)
        assertTrue("Right bound should be <= 1", bounds.right <= 1f)
        assertTrue("Bottom bound should be <= 1", bounds.bottom <= 1f)
        assertTrue("Left should be < right", bounds.left < bounds.right)
        assertTrue("Top should be < bottom", bounds.top < bounds.bottom)
    }

    @Test
    fun `test point visibility accuracy`() {
        // ARRANGE: Test various points for visibility
        val testCases = listOf(
            Triple(0.5f, 0.5f, true),   // Center - should be visible
            Triple(0.0f, 0.0f, true),   // Top-left corner - should be visible
            Triple(1.0f, 1.0f, true),   // Bottom-right corner - should be visible
            Triple(-0.1f, 0.5f, false), // Outside left - should not be visible
            Triple(1.1f, 0.5f, false),  // Outside right - should not be visible
            Triple(0.5f, -0.1f, false), // Outside top - should not be visible
            Triple(0.5f, 1.1f, false),  // Outside bottom - should not be visible
        )

        for ((x, y, expectedVisible) in testCases) {
            // ACT: Check if point is visible
            val isVisible = portraitMapper.isPointVisible(x, y)

            // ASSERT: Visibility should match expectation
            assertEquals(
                "Point ($x, $y) visibility should be $expectedVisible but was $isVisible",
                expectedVisible,
                isVisible
            )
        }
    }
}