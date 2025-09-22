package com.posecoach.app.overlay

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.util.Size
import kotlin.math.*

/**
 * Enhanced coordinate mapper optimized for 60fps performance with sub-pixel accuracy.
 * Handles all coordinate transformations between normalized pose coordinates and screen pixels.
 */
class EnhancedCoordinateMapper(
    private var viewWidth: Int,
    private var viewHeight: Int,
    private var imageWidth: Int,
    private var imageHeight: Int,
    private var isFrontFacing: Boolean = true,
    private var rotation: Int = 0
) {

    private var fitMode: FitMode = FitMode.CENTER_CROP
    private var transform: Matrix = Matrix()
    private var inverseTransform: Matrix = Matrix()

    // Cached calculations for performance
    private var visibleRegion: RectF = RectF()
    private var scaleX: Float = 1f
    private var scaleY: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    // Performance tracking
    private var transformationCount = 0L
    private var totalError = 0.0
    private var maxError = 0.0

    // Boundary caching for visibility checks
    private var boundaryPoints: FloatArray = FloatArray(8) // 4 corners as x,y pairs

    init {
        updateTransformation()
    }

    /**
     * Convert normalized coordinates to pixel coordinates with sub-pixel accuracy including rotation
     */
    fun normalizedToPixel(normalizedX: Float, normalizedY: Float): Pair<Float, Float> {
        transformationCount++

        // Apply mirroring for front camera
        val x = if (isFrontFacing) 1f - normalizedX else normalizedX
        val y = normalizedY

        // Apply scaling and offset first
        val scaledX = x * scaleX + offsetX
        val scaledY = y * scaleY + offsetY

        // Apply rotation transformation if needed
        return if (rotation != 0) {
            val point = floatArrayOf(scaledX, scaledY)
            transform.mapPoints(point)
            Pair(point[0], point[1])
        } else {
            Pair(scaledX, scaledY)
        }
    }

    /**
     * Batch convert multiple normalized coordinates for optimal performance
     */
    fun batchNormalizedToPixel(landmarks: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (landmarks.isEmpty()) return emptyList()

        val count = landmarks.size
        val points = FloatArray(count * 2)

        // Apply mirroring and scaling in batch
        landmarks.forEachIndexed { index, (landmarkX, landmarkY) ->
            val x = if (isFrontFacing) 1f - landmarkX else landmarkX
            val y = landmarkY

            points[index * 2] = x * scaleX + offsetX
            points[index * 2 + 1] = y * scaleY + offsetY
        }

        // Apply rotation transformation if needed
        if (rotation != 0) {
            transform.mapPoints(points)
        }

        // Convert back to pairs
        val result = ArrayList<Pair<Float, Float>>(count)
        for (i in 0 until count) {
            result.add(Pair(points[i * 2], points[i * 2 + 1]))
        }

        transformationCount += landmarks.size
        return result
    }

    /**
     * Check if a normalized point is visible in current view
     */
    fun isPointVisible(normalizedX: Float, normalizedY: Float): Boolean {
        val x = if (isFrontFacing) 1f - normalizedX else normalizedX

        return when (fitMode) {
            FitMode.FILL -> {
                normalizedX in 0f..1f && normalizedY in 0f..1f
            }
            FitMode.CENTER_CROP -> {
                // Use precomputed visible region for performance
                x >= visibleRegion.left && x <= visibleRegion.right &&
                normalizedY >= visibleRegion.top && normalizedY <= visibleRegion.bottom
            }
            FitMode.CENTER_INSIDE -> {
                // All points are visible in center inside mode
                true
            }
        }
    }

    /**
     * Update aspect ratio handling mode
     */
    fun updateAspectRatio(newFitMode: FitMode) {
        if (fitMode != newFitMode) {
            fitMode = newFitMode
            updateTransformation()
        }
    }

    /**
     * Update view dimensions
     */
    fun updateViewDimensions(width: Int, height: Int) {
        if (viewWidth != width || viewHeight != height) {
            viewWidth = width
            viewHeight = height
            updateTransformation()
        }
    }

    /**
     * Update image dimensions
     */
    fun updateImageDimensions(width: Int, height: Int) {
        if (imageWidth != width || imageHeight != height) {
            imageWidth = width
            imageHeight = height
            updateTransformation()
        }
    }

    /**
     * Update camera orientation
     */
    fun updateRotation(newRotation: Int, newIsFrontFacing: Boolean) {
        if (rotation != newRotation || isFrontFacing != newIsFrontFacing) {
            rotation = newRotation
            isFrontFacing = newIsFrontFacing
            updateTransformation()
        }
    }

    /**
     * Get current visible region in normalized coordinates
     */
    fun getVisibleRegion(): RectF {
        return RectF(visibleRegion)
    }

    /**
     * Get transformation matrix for advanced usage
     */
    fun getTransformMatrix(): Matrix {
        return Matrix(transform)
    }

    /**
     * Get inverse transformation matrix
     */
    fun getInverseTransformMatrix(): Matrix {
        return Matrix(inverseTransform)
    }

    /**
     * Convert pixel coordinates back to normalized coordinates
     */
    fun pixelToNormalized(pixelX: Float, pixelY: Float): Pair<Float, Float> {
        val point = floatArrayOf(pixelX, pixelY)
        inverseTransform.mapPoints(point)

        var x = point[0] / imageWidth
        var y = point[1] / imageHeight

        // Apply mirroring for front camera
        if (isFrontFacing) {
            x = 1f - x
        }

        return Pair(x, y)
    }

    /**
     * Get performance metrics for monitoring
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        val averageError = if (transformationCount > 0) totalError / transformationCount else 0.0

        return PerformanceMetrics(
            transformationCount = transformationCount,
            averageError = averageError,
            maxError = maxError,
            currentFitMode = fitMode,
            viewSize = Size(viewWidth, viewHeight),
            imageSize = Size(imageWidth, imageHeight)
        )
    }

    /**
     * Reset performance metrics
     */
    fun resetMetrics() {
        transformationCount = 0
        totalError = 0.0
        maxError = 0.0
    }

    private fun updateTransformation() {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return
        }

        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()

        when (fitMode) {
            FitMode.FILL -> {
                // Simple scaling to fill entire view
                scaleX = viewWidth.toFloat()
                scaleY = viewHeight.toFloat()
                offsetX = 0f
                offsetY = 0f

                visibleRegion.set(0f, 0f, 1f, 1f)
            }

            FitMode.CENTER_CROP -> {
                if (imageAspect > viewAspect) {
                    // Image is wider than view - crop sides
                    scaleY = viewHeight.toFloat()
                    scaleX = scaleY * imageAspect
                    offsetX = (viewWidth - scaleX) / 2f
                    offsetY = 0f

                    val visibleWidth = viewWidth / scaleX
                    val cropMargin = (1f - visibleWidth) / 2f
                    visibleRegion.set(cropMargin, 0f, 1f - cropMargin, 1f)
                } else {
                    // Image is taller than view - crop top/bottom
                    scaleX = viewWidth.toFloat()
                    scaleY = scaleX / imageAspect
                    offsetX = 0f
                    offsetY = (viewHeight - scaleY) / 2f

                    val visibleHeight = viewHeight / scaleY
                    val cropMargin = (1f - visibleHeight) / 2f
                    visibleRegion.set(0f, cropMargin, 1f, 1f - cropMargin)
                }
            }

            FitMode.CENTER_INSIDE -> {
                if (imageAspect > viewAspect) {
                    // Image is wider - fit to width
                    scaleX = viewWidth.toFloat()
                    scaleY = scaleX / imageAspect
                    offsetX = 0f
                    offsetY = (viewHeight - scaleY) / 2f
                } else {
                    // Image is taller - fit to height
                    scaleY = viewHeight.toFloat()
                    scaleX = scaleY * imageAspect
                    offsetX = (viewWidth - scaleX) / 2f
                    offsetY = 0f
                }

                visibleRegion.set(0f, 0f, 1f, 1f)
            }
        }

        // Update transformation matrices
        updateMatrices()

        // Update boundary points for fast visibility checks
        updateBoundaryPoints()
    }

    private fun updateMatrices() {
        transform.reset()

        // Apply scaling
        transform.postScale(scaleX, scaleY)

        // Apply offset
        transform.postTranslate(offsetX, offsetY)

        // Apply rotation if needed
        if (rotation != 0) {
            transform.postRotate(
                rotation.toFloat(),
                viewWidth / 2f,
                viewHeight / 2f
            )
        }

        // Calculate inverse transform
        if (!transform.invert(inverseTransform)) {
            inverseTransform.reset() // Fallback to identity
        }
    }

    private fun updateBoundaryPoints() {
        // Calculate the four corner points of the visible region
        val corners = floatArrayOf(
            visibleRegion.left, visibleRegion.top,      // Top-left
            visibleRegion.right, visibleRegion.top,     // Top-right
            visibleRegion.right, visibleRegion.bottom,  // Bottom-right
            visibleRegion.left, visibleRegion.bottom    // Bottom-left
        )

        // Transform to pixel coordinates for boundary checking
        for (i in corners.indices step 2) {
            val pixel = normalizedToPixel(corners[i], corners[i + 1])
            boundaryPoints[i] = pixel.first
            boundaryPoints[i + 1] = pixel.second
        }
    }

    /**
     * Fast visibility check using precomputed boundary points
     */
    fun isPointInBounds(pixelX: Float, pixelY: Float): Boolean {
        // Simple rectangular bounds check for now
        // Can be extended to handle rotation with polygon containment
        val minX = boundaryPoints.filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: 0f
        val maxX = boundaryPoints.filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull() ?: viewWidth.toFloat()
        val minY = boundaryPoints.filterIndexed { index, _ -> index % 2 == 1 }.minOrNull() ?: 0f
        val maxY = boundaryPoints.filterIndexed { index, _ -> index % 2 == 1 }.maxOrNull() ?: viewHeight.toFloat()

        return pixelX >= minX && pixelX <= maxX && pixelY >= minY && pixelY <= maxY
    }

    /**
     * Calculate coordinate mapping error for quality assessment
     */
    fun calculateMappingError(
        originalNormalized: Pair<Float, Float>,
        mappedPixel: Pair<Float, Float>
    ): Double {
        val backMapped = pixelToNormalized(mappedPixel.first, mappedPixel.second)

        val errorX = abs(originalNormalized.first - backMapped.first)
        val errorY = abs(originalNormalized.second - backMapped.second)
        val error = sqrt((errorX * errorX + errorY * errorY).toDouble())

        totalError += error
        maxError = max(maxError, error)

        return error
    }

    data class PerformanceMetrics(
        val transformationCount: Long,
        val averageError: Double,
        val maxError: Double,
        val currentFitMode: FitMode,
        val viewSize: Size,
        val imageSize: Size
    )
}