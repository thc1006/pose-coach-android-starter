package com.posecoach.app.overlay

import android.graphics.PointF
import com.posecoach.corepose.models.PoseLandmarkResult
import timber.log.Timber
import kotlin.math.abs

/**
 * Utility functions for coordinate transformation and rotation handling
 * Following CLAUDE.md requirements for coordinate system management
 */
object CoordinateUtils {

    /**
     * Rotate landmarks based on device orientation
     * @param landmarks List of normalized landmarks (0-1 range)
     * @param rotationDegrees Rotation in degrees (must be multiple of 90)
     * @param imageWidth Original image width before rotation
     * @param imageHeight Original image height before rotation
     * @return List of rotated landmarks in normalized coordinates
     */
    fun rotateLandmarks(
        landmarks: List<PoseLandmarkResult.Landmark>,
        rotationDegrees: Int,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ): List<PoseLandmarkResult.Landmark> {
        // Normalize rotation to 0-360 range
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360

        // Rotation must be a multiple of 90 degrees for MediaPipe/ML Kit
        if (normalizedRotation % 90 != 0) {
            Timber.w("Rotation $rotationDegrees is not a multiple of 90, using $normalizedRotation")
        }

        return when (normalizedRotation) {
            0 -> landmarks // No rotation needed
            90 -> landmarks.map { landmark ->
                // 90° clockwise: (x,y) -> (1-y, x)
                landmark.copy(
                    x = 1f - landmark.y,
                    y = landmark.x
                )
            }
            180 -> landmarks.map { landmark ->
                // 180°: (x,y) -> (1-x, 1-y)
                landmark.copy(
                    x = 1f - landmark.x,
                    y = 1f - landmark.y
                )
            }
            270 -> landmarks.map { landmark ->
                // 270° clockwise (90° counter-clockwise): (x,y) -> (y, 1-x)
                landmark.copy(
                    x = landmark.y,
                    y = 1f - landmark.x
                )
            }
            else -> {
                Timber.w("Unsupported rotation angle: $normalizedRotation, returning original landmarks")
                landmarks
            }
        }
    }

    /**
     * Apply horizontal mirroring for front-facing camera
     * @param landmarks List of normalized landmarks
     * @return List of mirrored landmarks
     */
    fun mirrorLandmarks(
        landmarks: List<PoseLandmarkResult.Landmark>
    ): List<PoseLandmarkResult.Landmark> {
        return landmarks.map { landmark ->
            landmark.copy(x = 1f - landmark.x)
        }
    }

    /**
     * Transform normalized coordinates to screen pixels
     * Simple direct projection: px = x * viewWidth, py = y * viewHeight
     * @param normalizedX X coordinate in 0-1 range
     * @param normalizedY Y coordinate in 0-1 range
     * @param viewWidth View width in pixels
     * @param viewHeight View height in pixels
     * @return Screen coordinates in pixels
     */
    fun normalizedToScreen(
        normalizedX: Float,
        normalizedY: Float,
        viewWidth: Int,
        viewHeight: Int
    ): PointF {
        if (viewWidth <= 0 || viewHeight <= 0) {
            Timber.w("Invalid view dimensions: ${viewWidth}x${viewHeight}")
            return PointF(0f, 0f)
        }

        val screenX = (normalizedX * viewWidth).coerceIn(0f, viewWidth.toFloat())
        val screenY = (normalizedY * viewHeight).coerceIn(0f, viewHeight.toFloat())

        return PointF(screenX, screenY)
    }

    /**
     * Batch transform normalized coordinates to screen pixels
     * @param landmarks List of normalized landmarks
     * @param viewWidth View width in pixels
     * @param viewHeight View height in pixels
     * @return List of screen coordinates in pixels
     */
    fun batchNormalizedToScreen(
        landmarks: List<PoseLandmarkResult.Landmark>,
        viewWidth: Int,
        viewHeight: Int
    ): List<PointF> {
        if (viewWidth <= 0 || viewHeight <= 0) {
            Timber.w("Invalid view dimensions for batch transform: ${viewWidth}x${viewHeight}")
            return landmarks.map { PointF(0f, 0f) }
        }

        return landmarks.map { landmark ->
            normalizedToScreen(landmark.x, landmark.y, viewWidth, viewHeight)
        }
    }

    /**
     * Validate landmark coordinates
     * @param landmark Landmark to validate
     * @return True if landmark has valid normalized coordinates
     */
    fun isLandmarkValid(landmark: PoseLandmarkResult.Landmark): Boolean {
        return landmark.x in 0f..1f &&
               landmark.y in 0f..1f &&
               landmark.visibility > 0.5f
    }

    /**
     * Calculate the appropriate rotation for landmarks based on device orientation
     * @param sensorOrientation Camera sensor orientation
     * @param deviceRotation Current device rotation
     * @param isFrontFacing Whether the camera is front-facing
     * @return Rotation degrees to apply (0, 90, 180, or 270)
     */
    fun calculateRotationDegrees(
        sensorOrientation: Int,
        deviceRotation: Int,
        isFrontFacing: Boolean
    ): Int {
        val rotation = if (isFrontFacing) {
            (sensorOrientation + deviceRotation) % 360
        } else {
            (sensorOrientation - deviceRotation + 360) % 360
        }

        // Round to nearest 90 degrees
        return when (rotation) {
            in 0..44, in 315..360 -> 0
            in 45..134 -> 90
            in 135..224 -> 180
            in 225..314 -> 270
            else -> 0
        }
    }

    /**
     * Test utility: Create artificial landmarks for rotation testing
     * @param pattern Test pattern type
     * @return List of test landmarks
     */
    fun createTestLandmarks(pattern: TestPattern): List<PoseLandmarkResult.Landmark> {
        return when (pattern) {
            TestPattern.CORNERS -> listOf(
                // Four corners
                PoseLandmarkResult.Landmark(0f, 0f, 0f, 1f, 1f),      // Top-left
                PoseLandmarkResult.Landmark(1f, 0f, 0f, 1f, 1f),      // Top-right
                PoseLandmarkResult.Landmark(1f, 1f, 0f, 1f, 1f),      // Bottom-right
                PoseLandmarkResult.Landmark(0f, 1f, 0f, 1f, 1f)       // Bottom-left
            )
            TestPattern.CENTER_CROSS -> listOf(
                // Center point and cross
                PoseLandmarkResult.Landmark(0.5f, 0.5f, 0f, 1f, 1f),  // Center
                PoseLandmarkResult.Landmark(0.5f, 0f, 0f, 1f, 1f),    // Top
                PoseLandmarkResult.Landmark(1f, 0.5f, 0f, 1f, 1f),    // Right
                PoseLandmarkResult.Landmark(0.5f, 1f, 0f, 1f, 1f),    // Bottom
                PoseLandmarkResult.Landmark(0f, 0.5f, 0f, 1f, 1f)     // Left
            )
            TestPattern.DIAGONAL -> listOf(
                // Diagonal line from top-left to bottom-right
                PoseLandmarkResult.Landmark(0.25f, 0.25f, 0f, 1f, 1f),
                PoseLandmarkResult.Landmark(0.5f, 0.5f, 0f, 1f, 1f),
                PoseLandmarkResult.Landmark(0.75f, 0.75f, 0f, 1f, 1f)
            )
        }
    }

    /**
     * Verify rotation correctness by checking landmark positions
     * @param original Original landmarks
     * @param rotated Rotated landmarks
     * @param rotationDegrees Applied rotation
     * @param tolerance Position tolerance for comparison
     * @return True if rotation is correct within tolerance
     */
    fun verifyRotation(
        original: List<PoseLandmarkResult.Landmark>,
        rotated: List<PoseLandmarkResult.Landmark>,
        rotationDegrees: Int,
        tolerance: Float = 0.01f
    ): Boolean {
        if (original.size != rotated.size) return false

        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360

        return original.zip(rotated).all { (orig, rot) ->
            val (expectedX, expectedY) = when (normalizedRotation) {
                0 -> Pair(orig.x, orig.y)
                90 -> Pair(1f - orig.y, orig.x)
                180 -> Pair(1f - orig.x, 1f - orig.y)
                270 -> Pair(orig.y, 1f - orig.x)
                else -> return false
            }

            abs(rot.x - expectedX) < tolerance && abs(rot.y - expectedY) < tolerance
        }
    }

    enum class TestPattern {
        CORNERS,        // Four corner points
        CENTER_CROSS,   // Center with cross pattern
        DIAGONAL        // Diagonal line pattern
    }
}