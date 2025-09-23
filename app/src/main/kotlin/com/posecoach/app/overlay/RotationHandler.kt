package com.posecoach.app.overlay

import android.graphics.Matrix
import android.graphics.PointF
import timber.log.Timber
import kotlin.math.*

/**
 * RotationHandler manages coordinate rotation transformations.
 * Separated from EnhancedCoordinateMapper for better modularity (<200 lines).
 *
 * Features:
 * - Full 360° rotation support
 * - Optimized matrix transformations
 * - Batch point rotation
 * - View dimension compensation
 */
class RotationHandler {

    companion object {
        private const val TAG = "RotationHandler"
        private const val DEGREES_TO_RADIANS = PI / 180.0
    }

    private var currentRotation = 0
    private var normalizedRotation = 0
    private var rotationRadians = 0f
    private var viewWidth = 0
    private var viewHeight = 0

    private val rotationMatrix = Matrix()
    private val tempPoints = FloatArray(2)

    /**
     * Set rotation angle and view dimensions
     */
    fun setRotation(angle: Int, width: Int, height: Int) {
        currentRotation = angle
        normalizedRotation = normalizeAngle(angle)
        rotationRadians = (normalizedRotation * DEGREES_TO_RADIANS).toFloat()
        viewWidth = width
        viewHeight = height

        updateRotationMatrix()
        Timber.d("$TAG: Rotation set to $normalizedRotation° for view ${width}x${height}")
    }

    /**
     * Rotate a single point
     */
    fun rotatePoint(point: PointF): PointF {
        if (normalizedRotation == 0) {
            return PointF(point.x, point.y)
        }

        tempPoints[0] = point.x
        tempPoints[1] = point.y
        rotationMatrix.mapPoints(tempPoints)

        return PointF(tempPoints[0], tempPoints[1])
    }

    /**
     * Rotate multiple points efficiently
     */
    fun rotatePoints(points: List<PointF>): List<PointF> {
        if (normalizedRotation == 0) {
            return points.map { PointF(it.x, it.y) }
        }

        val pointArray = FloatArray(points.size * 2)
        points.forEachIndexed { index, point ->
            pointArray[index * 2] = point.x
            pointArray[index * 2 + 1] = point.y
        }

        rotationMatrix.mapPoints(pointArray)

        return (0 until points.size).map { index ->
            PointF(pointArray[index * 2], pointArray[index * 2 + 1])
        }
    }

    /**
     * Get current normalized rotation angle
     */
    fun getNormalizedRotation(): Int = normalizedRotation

    /**
     * Get rotation matrix
     */
    fun getRotationMatrix(): Matrix = Matrix(rotationMatrix)

    /**
     * Reset rotation to 0 degrees
     */
    fun resetRotation() {
        setRotation(0, viewWidth, viewHeight)
    }

    /**
     * Update view dimensions without changing rotation
     */
    fun updateViewDimensions(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        updateRotationMatrix()
        Timber.d("$TAG: View dimensions updated to ${width}x${height}")
    }

    /**
     * Get current rotation state
     */
    fun getRotationState(): RotationState {
        return RotationState(
            angle = currentRotation,
            normalizedAngle = normalizedRotation,
            radians = rotationRadians,
            isRotated = normalizedRotation != 0
        )
    }

    /**
     * Check if rotation is applied
     */
    fun isRotated(): Boolean = normalizedRotation != 0

    private fun updateRotationMatrix() {
        rotationMatrix.reset()

        when (normalizedRotation) {
            0 -> {
                // No rotation needed
            }
            90 -> {
                // 90° clockwise: (x,y) -> (y, width-x)
                rotationMatrix.setRotate(90f, viewWidth / 2f, viewHeight / 2f)
                rotationMatrix.postTranslate((viewHeight - viewWidth) / 2f, (viewWidth - viewHeight) / 2f)
            }
            180 -> {
                // 180°: (x,y) -> (width-x, height-y)
                rotationMatrix.setRotate(180f, viewWidth / 2f, viewHeight / 2f)
            }
            270 -> {
                // 270° clockwise: (x,y) -> (height-y, x)
                rotationMatrix.setRotate(270f, viewWidth / 2f, viewHeight / 2f)
                rotationMatrix.postTranslate((viewHeight - viewWidth) / 2f, (viewWidth - viewHeight) / 2f)
            }
            else -> {
                // Arbitrary angle rotation
                rotationMatrix.setRotate(normalizedRotation.toFloat(), viewWidth / 2f, viewHeight / 2f)
            }
        }
    }

    /**
     * Normalize angle to 0-359 range
     */
    private fun normalizeAngle(angle: Int): Int {
        var normalized = angle % 360
        if (normalized < 0) {
            normalized += 360
        }
        return normalized
    }

    /**
     * Calculate rotation for coordinate compensation
     */
    fun calculateDisplayRotation(sensorRotation: Int, deviceRotation: Int): Int {
        // Calculate display rotation compensation
        val rotation = (sensorRotation - deviceRotation + 360) % 360
        return rotation
    }

    /**
     * Apply rotation compensation for camera orientation
     */
    fun applyCameraOrientation(cameraRotation: Int, displayRotation: Int): Int {
        return (cameraRotation + displayRotation) % 360
    }
}