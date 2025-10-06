package com.posecoach.corepose.utils

import com.posecoach.corepose.models.PoseLandmarkResult
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * Unit tests for CoordinateNormalizationUtils.
 *
 * These tests verify that skeleton orientation is properly corrected
 * for different device rotations, addressing the 90° rotation bug.
 */
class CoordinateNormalizationUtilsTest {

    private val tolerance = 0.001f
    private val imageWidth = 720
    private val imageHeight = 1280

    // Test landmarks representing a person standing upright
    private val standingPersonLandmarks = listOf(
        // Head (top center)
        PoseLandmarkResult.Landmark(0.5f, 0.1f, 0f, 1.0f, 1.0f),
        // Left shoulder
        PoseLandmarkResult.Landmark(0.3f, 0.3f, 0f, 1.0f, 1.0f),
        // Right shoulder
        PoseLandmarkResult.Landmark(0.7f, 0.3f, 0f, 1.0f, 1.0f),
        // Left hip
        PoseLandmarkResult.Landmark(0.4f, 0.7f, 0f, 1.0f, 1.0f),
        // Right hip
        PoseLandmarkResult.Landmark(0.6f, 0.7f, 0f, 1.0f, 1.0f),
        // Left ankle
        PoseLandmarkResult.Landmark(0.4f, 0.9f, 0f, 1.0f, 1.0f),
        // Right ankle
        PoseLandmarkResult.Landmark(0.6f, 0.9f, 0f, 1.0f, 1.0f)
    )

    @Test
    fun `test 0 degree rotation - no transformation needed`() {
        // ARRANGE
        val rotationDegrees = 0

        // ACT
        val transformedLandmarks = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            standingPersonLandmarks,
            rotationDegrees,
            imageWidth,
            imageHeight
        )

        // ASSERT
        assertEquals("Landmark count should remain the same", standingPersonLandmarks.size, transformedLandmarks.size)

        // With 0° rotation, coordinates should remain unchanged
        for (i in standingPersonLandmarks.indices) {
            val original = standingPersonLandmarks[i]
            val transformed = transformedLandmarks[i]

            assertEquals("X coordinate should remain unchanged", original.x, transformed.x, tolerance)
            assertEquals("Y coordinate should remain unchanged", original.y, transformed.y, tolerance)
        }

        // Verify person is still standing upright (head above feet)
        val head = transformedLandmarks[0]
        val leftAnkle = transformedLandmarks[5]
        val rightAnkle = transformedLandmarks[6]

        assertTrue("Head should be above ankles", head.y < leftAnkle.y && head.y < rightAnkle.y)
    }

    @Test
    fun `test 90 degree rotation - person rotated clockwise`() {
        // ARRANGE
        val rotationDegrees = 90

        // ACT
        val transformedLandmarks = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            standingPersonLandmarks,
            rotationDegrees,
            imageWidth,
            imageHeight
        )

        // ASSERT
        assertEquals("Landmark count should remain the same", standingPersonLandmarks.size, transformedLandmarks.size)

        // Verify 90° rotation transformation: x' = 1 - y, y' = x
        val originalHead = standingPersonLandmarks[0] // (0.5, 0.1)
        val transformedHead = transformedLandmarks[0]

        val expectedX = 1f - originalHead.y // 1 - 0.1 = 0.9
        val expectedY = originalHead.x      // 0.5

        assertEquals("Head X coordinate after 90° rotation", expectedX, transformedHead.x, tolerance)
        assertEquals("Head Y coordinate after 90° rotation", expectedY, transformedHead.y, tolerance)

        // Verify all coordinates are within bounds
        assertTrue("All coordinates should be valid",
            CoordinateNormalizationUtils.validateLandmarkCoordinates(transformedLandmarks))

        // After 90° clockwise rotation, the person should appear lying on their side
        // Original vertical arrangement should become horizontal
        val leftShoulder = transformedLandmarks[1]
        val rightShoulder = transformedLandmarks[2]

        // Shoulders should now be vertically aligned (different Y, similar X)
        val shoulderYDiff = abs(leftShoulder.y - rightShoulder.y)
        assertTrue("Shoulders should be vertically separated after 90° rotation", shoulderYDiff > 0.1f)
    }

    @Test
    fun `test 180 degree rotation - person upside down`() {
        // ARRANGE
        val rotationDegrees = 180

        // ACT
        val transformedLandmarks = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            standingPersonLandmarks,
            rotationDegrees,
            imageWidth,
            imageHeight
        )

        // ASSERT
        assertEquals("Landmark count should remain the same", standingPersonLandmarks.size, transformedLandmarks.size)

        // Verify 180° rotation transformation: x' = 1 - x, y' = 1 - y
        val originalHead = standingPersonLandmarks[0] // (0.5, 0.1)
        val transformedHead = transformedLandmarks[0]

        val expectedX = 1f - originalHead.x // 1 - 0.5 = 0.5
        val expectedY = 1f - originalHead.y // 1 - 0.1 = 0.9

        assertEquals("Head X coordinate after 180° rotation", expectedX, transformedHead.x, tolerance)
        assertEquals("Head Y coordinate after 180° rotation", expectedY, transformedHead.y, tolerance)

        // After 180° rotation, head should be at the bottom
        val leftAnkle = transformedLandmarks[5]
        val rightAnkle = transformedLandmarks[6]

        assertTrue("Head should be below ankles after 180° rotation",
            transformedHead.y > leftAnkle.y && transformedHead.y > rightAnkle.y)
    }

    @Test
    fun `test 270 degree rotation - person rotated counter-clockwise`() {
        // ARRANGE
        val rotationDegrees = 270

        // ACT
        val transformedLandmarks = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            standingPersonLandmarks,
            rotationDegrees,
            imageWidth,
            imageHeight
        )

        // ASSERT
        assertEquals("Landmark count should remain the same", standingPersonLandmarks.size, transformedLandmarks.size)

        // Verify 270° rotation transformation: x' = y, y' = 1 - x
        val originalHead = standingPersonLandmarks[0] // (0.5, 0.1)
        val transformedHead = transformedLandmarks[0]

        val expectedX = originalHead.y      // 0.1
        val expectedY = 1f - originalHead.x // 1 - 0.5 = 0.5

        assertEquals("Head X coordinate after 270° rotation", expectedX, transformedHead.x, tolerance)
        assertEquals("Head Y coordinate after 270° rotation", expectedY, transformedHead.y, tolerance)

        // Verify all coordinates are within bounds
        assertTrue("All coordinates should be valid",
            CoordinateNormalizationUtils.validateLandmarkCoordinates(transformedLandmarks))
    }

    @Test
    fun `test arbitrary angle rotation - 45 degrees`() {
        // ARRANGE
        val rotationDegrees = 45

        // ACT
        val transformedLandmarks = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            standingPersonLandmarks,
            rotationDegrees,
            imageWidth,
            imageHeight
        )

        // ASSERT
        assertEquals("Landmark count should remain the same", standingPersonLandmarks.size, transformedLandmarks.size)

        // Verify all coordinates are within bounds
        assertTrue("All coordinates should be valid",
            CoordinateNormalizationUtils.validateLandmarkCoordinates(transformedLandmarks))

        // With 45° rotation, coordinates should be different from original
        val originalHead = standingPersonLandmarks[0]
        val transformedHead = transformedLandmarks[0]

        val coordinatesChanged = abs(originalHead.x - transformedHead.x) > tolerance ||
                               abs(originalHead.y - transformedHead.y) > tolerance

        assertTrue("Coordinates should change with 45° rotation", coordinatesChanged)
    }

    @Test
    fun `test getRotatedImageDimensions - dimensions swap at 90 and 270 degrees`() {
        // ARRANGE
        val originalWidth = 720
        val originalHeight = 1280

        // ACT & ASSERT
        // 0° and 180° should keep original dimensions
        assertEquals(Pair(originalWidth, originalHeight),
            CoordinateNormalizationUtils.getRotatedImageDimensions(originalWidth, originalHeight, 0))
        assertEquals(Pair(originalWidth, originalHeight),
            CoordinateNormalizationUtils.getRotatedImageDimensions(originalWidth, originalHeight, 180))

        // 90° and 270° should swap dimensions
        assertEquals(Pair(originalHeight, originalWidth),
            CoordinateNormalizationUtils.getRotatedImageDimensions(originalWidth, originalHeight, 90))
        assertEquals(Pair(originalHeight, originalWidth),
            CoordinateNormalizationUtils.getRotatedImageDimensions(originalWidth, originalHeight, 270))

        // Test with rotation angles beyond 360°
        assertEquals(Pair(originalHeight, originalWidth),
            CoordinateNormalizationUtils.getRotatedImageDimensions(originalWidth, originalHeight, 450)) // 450° = 90°
    }

    @Test
    fun `test validateLandmarkCoordinates - detects invalid coordinates`() {
        // ARRANGE
        val validLandmarks = listOf(
            PoseLandmarkResult.Landmark(0.0f, 0.0f, 0f, 1.0f, 1.0f),
            PoseLandmarkResult.Landmark(0.5f, 0.5f, 0f, 1.0f, 1.0f),
            PoseLandmarkResult.Landmark(1.0f, 1.0f, 0f, 1.0f, 1.0f)
        )

        val invalidLandmarks = listOf(
            PoseLandmarkResult.Landmark(-0.1f, 0.5f, 0f, 1.0f, 1.0f), // X out of bounds
            PoseLandmarkResult.Landmark(0.5f, 1.1f, 0f, 1.0f, 1.0f)   // Y out of bounds
        )

        // ACT & ASSERT
        assertTrue("Valid landmarks should pass validation",
            CoordinateNormalizationUtils.validateLandmarkCoordinates(validLandmarks))
        assertFalse("Invalid landmarks should fail validation",
            CoordinateNormalizationUtils.validateLandmarkCoordinates(invalidLandmarks))
    }

    @Test
    fun `test pixelToNormalized - converts pixel coordinates correctly`() {
        // ARRANGE
        val pixelX = 360f // Half of 720
        val pixelY = 640f // Half of 1280
        val rotationDegrees = 0

        // ACT
        val (normalizedX, normalizedY) = CoordinateNormalizationUtils.pixelToNormalized(
            pixelX, pixelY, imageWidth, imageHeight, rotationDegrees
        )

        // ASSERT
        assertEquals("Normalized X should be 0.5", 0.5f, normalizedX, tolerance)
        assertEquals("Normalized Y should be 0.5", 0.5f, normalizedY, tolerance)
    }

    @Test
    fun `test pixelToNormalized - handles rotation correctly`() {
        // ARRANGE
        val pixelX = 360f // Half of 720
        val pixelY = 640f // Half of 1280
        val rotationDegrees = 90

        // ACT
        val (normalizedX, normalizedY) = CoordinateNormalizationUtils.pixelToNormalized(
            pixelX, pixelY, imageWidth, imageHeight, rotationDegrees
        )

        // ASSERT
        // With 90° rotation, the inverse transformation should be applied
        assertTrue("Normalized coordinates should be valid",
            normalizedX >= 0f && normalizedX <= 1f && normalizedY >= 0f && normalizedY <= 1f)
    }

    @Test
    fun `test createTransformationSummary - generates informative summary`() {
        // ARRANGE
        val transformedLandmarks = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            standingPersonLandmarks, 90, imageWidth, imageHeight
        )

        // ACT
        val summary = CoordinateNormalizationUtils.createTransformationSummary(
            standingPersonLandmarks, transformedLandmarks, 90, imageWidth, imageHeight
        )

        // ASSERT
        assertTrue("Summary should contain rotation info", summary.contains("Rotation: 90°"))
        assertTrue("Summary should contain image dimensions",
            summary.contains("Original image: ${imageWidth}x${imageHeight}"))
        assertTrue("Summary should contain landmark count",
            summary.contains("Landmarks: ${standingPersonLandmarks.size}"))
        assertTrue("Summary should contain validation result", summary.contains("Valid coordinates: true"))
    }

    @Test
    fun `test empty landmarks list - returns empty list`() {
        // ARRANGE
        val emptyLandmarks = emptyList<PoseLandmarkResult.Landmark>()

        // ACT
        val result = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            emptyLandmarks, 90, imageWidth, imageHeight
        )

        // ASSERT
        assertTrue("Empty list should return empty list", result.isEmpty())
    }

    @Test
    fun `test invalid image dimensions - returns original landmarks`() {
        // ARRANGE
        val invalidWidth = 0
        val invalidHeight = -1

        // ACT
        val result = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            standingPersonLandmarks, 90, invalidWidth, invalidHeight
        )

        // ASSERT
        assertEquals("Should return original landmarks for invalid dimensions",
            standingPersonLandmarks, result)
    }

    @Test
    fun `test rotation normalization - handles angles greater than 360`() {
        // ARRANGE
        val landmarks = listOf(PoseLandmarkResult.Landmark(0.3f, 0.7f, 0f, 1.0f, 1.0f))

        // ACT
        val result450 = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            landmarks, 450, imageWidth, imageHeight // 450° = 90°
        )
        val result90 = CoordinateNormalizationUtils.normalizeAndRotateLandmarks(
            landmarks, 90, imageWidth, imageHeight
        )

        // ASSERT
        assertEquals("450° should produce same result as 90°", result90, result450)
    }
}