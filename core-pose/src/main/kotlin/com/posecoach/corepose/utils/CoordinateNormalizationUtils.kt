package com.posecoach.corepose.utils

import com.posecoach.corepose.models.PoseLandmarkResult
import kotlin.math.*

/**
 * Utility functions for normalizing and rotating pose landmark coordinates.
 *
 * This utility addresses the root cause of skeleton rotation issues by providing
 * proper coordinate transformation functions that account for device rotation
 * and ensure landmarks are properly aligned with the actual pose orientation.
 */
object CoordinateNormalizationUtils {

    /**
     * Normalize and rotate landmarks from MediaPipe output to display coordinates.
     *
     * This function corrects the coordinate system to ensure the skeleton appears
     * properly oriented regardless of device rotation.
     *
     * @param landmarks Original landmarks from MediaPipe inference
     * @param rotationDegrees Device rotation (0, 90, 180, 270)
     * @param imageWidth Original image width before rotation
     * @param imageHeight Original image height before rotation
     * @return Normalized landmarks with corrected orientation
     */
    fun normalizeAndRotateLandmarks(
        landmarks: List<PoseLandmarkResult.Landmark>,
        rotationDegrees: Int,
        imageWidth: Int,
        imageHeight: Int
    ): List<PoseLandmarkResult.Landmark> {

        if (landmarks.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
            return landmarks
        }

        val normalizedRotation = rotationDegrees % 360

        return when (normalizedRotation) {
            0 -> landmarks // No rotation needed
            90 -> rotateLandmarks90(landmarks)
            180 -> rotateLandmarks180(landmarks)
            270 -> rotateLandmarks270(landmarks)
            else -> {
                // Handle arbitrary rotation angles
                rotateLandmarksArbitrary(landmarks, normalizedRotation.toFloat())
            }
        }
    }

    /**
     * Rotate landmarks 90 degrees clockwise.
     * New coordinates: x' = 1 - y, y' = x
     */
    private fun rotateLandmarks90(landmarks: List<PoseLandmarkResult.Landmark>): List<PoseLandmarkResult.Landmark> {
        return landmarks.map { landmark ->
            landmark.copy(
                x = 1f - landmark.y,
                y = landmark.x
            )
        }
    }

    /**
     * Rotate landmarks 180 degrees.
     * New coordinates: x' = 1 - x, y' = 1 - y
     */
    private fun rotateLandmarks180(landmarks: List<PoseLandmarkResult.Landmark>): List<PoseLandmarkResult.Landmark> {
        return landmarks.map { landmark ->
            landmark.copy(
                x = 1f - landmark.x,
                y = 1f - landmark.y
            )
        }
    }

    /**
     * Rotate landmarks 270 degrees clockwise (90 degrees counter-clockwise).
     * New coordinates: x' = y, y' = 1 - x
     */
    private fun rotateLandmarks270(landmarks: List<PoseLandmarkResult.Landmark>): List<PoseLandmarkResult.Landmark> {
        return landmarks.map { landmark ->
            landmark.copy(
                x = landmark.y,
                y = 1f - landmark.x
            )
        }
    }

    /**
     * Rotate landmarks by arbitrary angle (in degrees).
     * Uses rotation matrix transformation around center point (0.5, 0.5).
     */
    private fun rotateLandmarksArbitrary(
        landmarks: List<PoseLandmarkResult.Landmark>,
        angleDegrees: Float
    ): List<PoseLandmarkResult.Landmark> {
        val angleRadians = Math.toRadians(angleDegrees.toDouble())
        val cosAngle = cos(angleRadians).toFloat()
        val sinAngle = sin(angleRadians).toFloat()
        val centerX = 0.5f
        val centerY = 0.5f

        return landmarks.map { landmark ->
            // Translate to origin
            val translatedX = landmark.x - centerX
            val translatedY = landmark.y - centerY

            // Apply rotation matrix
            val rotatedX = translatedX * cosAngle - translatedY * sinAngle
            val rotatedY = translatedX * sinAngle + translatedY * cosAngle

            // Translate back
            landmark.copy(
                x = (rotatedX + centerX).coerceIn(0f, 1f),
                y = (rotatedY + centerY).coerceIn(0f, 1f)
            )
        }
    }

    /**
     * Calculate the actual image dimensions after rotation.
     * This is important for proper coordinate normalization.
     */
    fun getRotatedImageDimensions(
        originalWidth: Int,
        originalHeight: Int,
        rotationDegrees: Int
    ): Pair<Int, Int> {
        return when (rotationDegrees % 360) {
            90, 270 -> Pair(originalHeight, originalWidth)
            else -> Pair(originalWidth, originalHeight)
        }
    }

    /**
     * Validate that landmarks are within the normalized coordinate space.
     * This function can be used for debugging coordinate transformation issues.
     */
    fun validateLandmarkCoordinates(landmarks: List<PoseLandmarkResult.Landmark>): Boolean {
        return landmarks.all { landmark ->
            landmark.x >= 0f && landmark.x <= 1f &&
            landmark.y >= 0f && landmark.y <= 1f
        }
    }

    /**
     * Convert pixel coordinates to normalized coordinates with rotation awareness.
     * This is useful for reverse transformations.
     */
    fun pixelToNormalized(
        pixelX: Float,
        pixelY: Float,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ): Pair<Float, Float> {
        val (actualWidth, actualHeight) = getRotatedImageDimensions(imageWidth, imageHeight, rotationDegrees)

        var normalizedX = if (actualWidth > 0) pixelX / actualWidth.toFloat() else 0f
        var normalizedY = if (actualHeight > 0) pixelY / actualHeight.toFloat() else 0f

        // Apply inverse rotation to get coordinates in the original frame
        when (rotationDegrees % 360) {
            90 -> {
                val temp = normalizedX
                normalizedX = normalizedY
                normalizedY = 1f - temp
            }
            180 -> {
                normalizedX = 1f - normalizedX
                normalizedY = 1f - normalizedY
            }
            270 -> {
                val temp = normalizedX
                normalizedX = 1f - normalizedY
                normalizedY = temp
            }
        }

        return Pair(
            normalizedX.coerceIn(0f, 1f),
            normalizedY.coerceIn(0f, 1f)
        )
    }

    /**
     * Create a debug summary of coordinate transformation.
     * Useful for troubleshooting rotation issues.
     */
    fun createTransformationSummary(
        originalLandmarks: List<PoseLandmarkResult.Landmark>,
        transformedLandmarks: List<PoseLandmarkResult.Landmark>,
        rotationDegrees: Int,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val (rotatedWidth, rotatedHeight) = getRotatedImageDimensions(imageWidth, imageHeight, rotationDegrees)

        return buildString {
            appendLine("Coordinate Transformation Summary:")
            appendLine("  Rotation: ${rotationDegrees}°")
            appendLine("  Original image: ${imageWidth}x${imageHeight}")
            appendLine("  Rotated image: ${rotatedWidth}x${rotatedHeight}")
            appendLine("  Landmarks: ${originalLandmarks.size} -> ${transformedLandmarks.size}")
            appendLine("  Valid coordinates: ${validateLandmarkCoordinates(transformedLandmarks)}")

            if (originalLandmarks.isNotEmpty() && transformedLandmarks.isNotEmpty()) {
                val firstOriginal = originalLandmarks.first()
                val firstTransformed = transformedLandmarks.first()
                appendLine("  Sample transformation: (${firstOriginal.x}, ${firstOriginal.y}) -> (${firstTransformed.x}, ${firstTransformed.y})")
            }
        }
    }
}