package com.posecoach.app.overlay

import com.posecoach.corepose.models.PoseLandmarkResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for coordinate rotation and transformation
 * Testing 0° and 90° rotation scenarios as per requirements
 */
class CoordinateRotationTest {

    private lateinit var testLandmarks: List<PoseLandmarkResult.Landmark>

    @Before
    fun setup() {
        // Create test landmarks at known positions (corners and center)
        testLandmarks = CoordinateUtils.createTestLandmarks(CoordinateUtils.TestPattern.CORNERS)
    }

    @Test
    fun `test 0 degree rotation - landmarks should remain unchanged`() {
        // Given: Test landmarks at corners
        val originalLandmarks = testLandmarks

        // When: Apply 0° rotation
        val rotatedLandmarks = CoordinateUtils.rotateLandmarks(originalLandmarks, 0)

        // Then: Landmarks should be identical
        assertEquals("Same number of landmarks", originalLandmarks.size, rotatedLandmarks.size)

        originalLandmarks.zip(rotatedLandmarks).forEachIndexed { index, (original, rotated) ->
            assertEquals("Landmark $index X unchanged", original.x, rotated.x, 0.001f)
            assertEquals("Landmark $index Y unchanged", original.y, rotated.y, 0.001f)
            assertEquals("Landmark $index visibility unchanged", original.visibility, rotated.visibility, 0.001f)
        }

        // Verify using the verification utility
        assertTrue(
            "0° rotation verification",
            CoordinateUtils.verifyRotation(originalLandmarks, rotatedLandmarks, 0)
        )
    }

    @Test
    fun `test 90 degree rotation - landmarks should rotate clockwise`() {
        // Given: Test landmarks at corners
        // Top-left (0,0), Top-right (1,0), Bottom-right (1,1), Bottom-left (0,1)
        val originalLandmarks = testLandmarks

        // When: Apply 90° rotation
        val rotatedLandmarks = CoordinateUtils.rotateLandmarks(originalLandmarks, 90)

        // Then: Verify rotation transformation
        // 90° clockwise: (x,y) -> (1-y, x)
        // Top-left (0,0) -> Top-right (1,0)
        // Top-right (1,0) -> Bottom-right (1,1)
        // Bottom-right (1,1) -> Bottom-left (0,1)
        // Bottom-left (0,1) -> Top-left (0,0)

        assertEquals("Same number of landmarks", 4, rotatedLandmarks.size)

        // Check each transformed position
        // Original top-left (0,0) should become (1,0)
        assertEquals("Top-left -> Top-right X", 1f, rotatedLandmarks[0].x, 0.001f)
        assertEquals("Top-left -> Top-right Y", 0f, rotatedLandmarks[0].y, 0.001f)

        // Original top-right (1,0) should become (1,1)
        assertEquals("Top-right -> Bottom-right X", 1f, rotatedLandmarks[1].x, 0.001f)
        assertEquals("Top-right -> Bottom-right Y", 1f, rotatedLandmarks[1].y, 0.001f)

        // Original bottom-right (1,1) should become (0,1)
        assertEquals("Bottom-right -> Bottom-left X", 0f, rotatedLandmarks[2].x, 0.001f)
        assertEquals("Bottom-right -> Bottom-left Y", 1f, rotatedLandmarks[2].y, 0.001f)

        // Original bottom-left (0,1) should become (0,0)
        assertEquals("Bottom-left -> Top-left X", 0f, rotatedLandmarks[3].x, 0.001f)
        assertEquals("Bottom-left -> Top-left Y", 0f, rotatedLandmarks[3].y, 0.001f)

        // Verify using the verification utility
        assertTrue(
            "90° rotation verification",
            CoordinateUtils.verifyRotation(originalLandmarks, rotatedLandmarks, 90)
        )
    }

    @Test
    fun `test 180 degree rotation - landmarks should flip`() {
        // Given: Test landmarks
        val originalLandmarks = testLandmarks

        // When: Apply 180° rotation
        val rotatedLandmarks = CoordinateUtils.rotateLandmarks(originalLandmarks, 180)

        // Then: Verify flip transformation
        // 180°: (x,y) -> (1-x, 1-y)
        assertEquals("Top-left -> Bottom-right X", 1f, rotatedLandmarks[0].x, 0.001f)
        assertEquals("Top-left -> Bottom-right Y", 1f, rotatedLandmarks[0].y, 0.001f)

        assertTrue(
            "180° rotation verification",
            CoordinateUtils.verifyRotation(originalLandmarks, rotatedLandmarks, 180)
        )
    }

    @Test
    fun `test 270 degree rotation - landmarks should rotate counter-clockwise`() {
        // Given: Test landmarks
        val originalLandmarks = testLandmarks

        // When: Apply 270° rotation (90° counter-clockwise)
        val rotatedLandmarks = CoordinateUtils.rotateLandmarks(originalLandmarks, 270)

        // Then: Verify rotation transformation
        // 270° clockwise: (x,y) -> (y, 1-x)
        assertEquals("Top-left -> Bottom-left X", 0f, rotatedLandmarks[0].x, 0.001f)
        assertEquals("Top-left -> Bottom-left Y", 1f, rotatedLandmarks[0].y, 0.001f)

        assertTrue(
            "270° rotation verification",
            CoordinateUtils.verifyRotation(originalLandmarks, rotatedLandmarks, 270)
        )
    }

    @Test
    fun `test normalized to screen coordinate transformation`() {
        // Given: View dimensions
        val viewWidth = 1080
        val viewHeight = 1920

        // When: Transform normalized coordinates
        val topLeft = CoordinateUtils.normalizedToScreen(0f, 0f, viewWidth, viewHeight)
        val center = CoordinateUtils.normalizedToScreen(0.5f, 0.5f, viewWidth, viewHeight)
        val bottomRight = CoordinateUtils.normalizedToScreen(1f, 1f, viewWidth, viewHeight)

        // Then: Verify screen coordinates
        assertEquals("Top-left X", 0f, topLeft.x, 0.001f)
        assertEquals("Top-left Y", 0f, topLeft.y, 0.001f)

        assertEquals("Center X", 540f, center.x, 0.001f)
        assertEquals("Center Y", 960f, center.y, 0.001f)

        assertEquals("Bottom-right X", 1080f, bottomRight.x, 0.001f)
        assertEquals("Bottom-right Y", 1920f, bottomRight.y, 0.001f)
    }

    @Test
    fun `test batch normalized to screen transformation`() {
        // Given: Test landmarks and view dimensions
        val landmarks = testLandmarks
        val viewWidth = 1080
        val viewHeight = 1920

        // When: Batch transform
        val screenPoints = CoordinateUtils.batchNormalizedToScreen(landmarks, viewWidth, viewHeight)

        // Then: Verify transformed points
        assertEquals("Same number of points", landmarks.size, screenPoints.size)

        assertEquals("Top-left screen X", 0f, screenPoints[0].x, 0.001f)
        assertEquals("Top-left screen Y", 0f, screenPoints[0].y, 0.001f)

        assertEquals("Top-right screen X", 1080f, screenPoints[1].x, 0.001f)
        assertEquals("Top-right screen Y", 0f, screenPoints[1].y, 0.001f)
    }

    @Test
    fun `test front camera mirroring`() {
        // Given: Test landmarks
        val originalLandmarks = testLandmarks

        // When: Apply mirroring
        val mirroredLandmarks = CoordinateUtils.mirrorLandmarks(originalLandmarks)

        // Then: X coordinates should be flipped
        originalLandmarks.zip(mirroredLandmarks).forEach { (original, mirrored) ->
            assertEquals("X coordinate mirrored", 1f - original.x, mirrored.x, 0.001f)
            assertEquals("Y coordinate unchanged", original.y, mirrored.y, 0.001f)
        }
    }

    @Test
    fun `test landmark validity check`() {
        // Given: Various landmarks
        val validLandmark = PoseLandmarkResult.Landmark(0.5f, 0.5f, 0f, 0.8f, 0.9f)
        val outOfBoundsLandmark = PoseLandmarkResult.Landmark(1.5f, 0.5f, 0f, 0.8f, 0.9f)
        val lowVisibilityLandmark = PoseLandmarkResult.Landmark(0.5f, 0.5f, 0f, 0.3f, 0.9f)

        // When & Then: Check validity
        assertTrue("Valid landmark", CoordinateUtils.isLandmarkValid(validLandmark))
        assertFalse("Out of bounds landmark", CoordinateUtils.isLandmarkValid(outOfBoundsLandmark))
        assertFalse("Low visibility landmark", CoordinateUtils.isLandmarkValid(lowVisibilityLandmark))
    }

    @Test
    fun `test rotation degree calculation`() {
        // Test various sensor and device orientations
        assertEquals("Back camera portrait", 0,
            CoordinateUtils.calculateRotationDegrees(90, 0, false))

        assertEquals("Back camera landscape", 90,
            CoordinateUtils.calculateRotationDegrees(90, 270, false))

        assertEquals("Front camera portrait", 270,
            CoordinateUtils.calculateRotationDegrees(270, 0, true))
    }

    @Test
    fun `test cross pattern rotation for visual verification`() {
        // Given: Cross pattern landmarks
        val crossLandmarks = CoordinateUtils.createTestLandmarks(CoordinateUtils.TestPattern.CENTER_CROSS)

        // When: Apply 90° rotation
        val rotatedLandmarks = CoordinateUtils.rotateLandmarks(crossLandmarks, 90)

        // Then: Verify cross rotated correctly
        // Center should remain at center
        assertEquals("Center X", 0.5f, rotatedLandmarks[0].x, 0.001f)
        assertEquals("Center Y", 0.5f, rotatedLandmarks[0].y, 0.001f)

        // Top (0.5, 0) should become Right (1, 0.5)
        assertEquals("Top -> Right X", 1f, rotatedLandmarks[1].x, 0.001f)
        assertEquals("Top -> Right Y", 0.5f, rotatedLandmarks[1].y, 0.001f)
    }
}