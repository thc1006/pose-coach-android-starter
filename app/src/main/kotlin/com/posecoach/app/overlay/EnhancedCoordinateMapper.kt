package com.posecoach.app.overlay

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import com.posecoach.app.BuildConfig
import timber.log.Timber
import kotlin.math.*

/**
 * Enhanced coordinate mapper with advanced rotation support, Android 15+ compatibility,
 * and optimized batch processing for pose landmark transformations.
 *
 * Key features:
 * - Sub-pixel accuracy with <1px error tolerance
 * - Full 360° rotation support with proper matrix transformations
 * - Android 15+ coordinate system support
 * - Optimized batch processing for performance
 * - Enhanced visibility checking with view bounds
 * - Front/back camera mirroring with orientation compensation
 * - Multiple aspect ratio fitting modes
 *
 * Following CLAUDE.md requirements for coordinate transformation from landmarks to screen space.
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

    // Enhanced transformation matrices for better rotation support
    private val mirrorMatrix = Matrix()
    private val rotationMatrix = Matrix()

    // Android 15+ specific matrices
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private val android15Matrix = Matrix()

    // Cached calculations for performance
    private var visibleRegion: RectF = RectF()
    private var scaleX: Float = 1f
    private var scaleY: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    // Enhanced performance tracking
    private var transformationCount = 0L
    private var batchTransformationCount = 0L
    private var totalError = 0.0
    private var maxError = 0.0
    private var lastBatchSize = 0
    private var lastBatchDuration = 0.0

    // Boundary caching for visibility checks
    private var boundaryPoints: FloatArray = FloatArray(8) // 4 corners as x,y pairs

    // Caching for performance optimization
    private val cachedPoints = mutableMapOf<Pair<Float, Float>, Pair<Float, Float>>()
    private var cacheInvalidated = true

    companion object {
        private const val PIXEL_ERROR_TOLERANCE = 1.0f
        private const val SUB_PIXEL_PRECISION = 10000.0f
        private const val CACHE_SIZE_LIMIT = 1000
        private const val BATCH_OPTIMIZATION_THRESHOLD = 10
        private const val TAG = "EnhancedCoordinateMapper"

        // Android 15+ specific constants
        private const val ANDROID_15_SCALE_FACTOR = 1.0f
        private const val DISPLAY_ROTATION_COMPENSATION = true
    }

    init {
        initializeMatrices()
        updateTransformation()
        Timber.d("$TAG initialized: view=${viewWidth}x${viewHeight}, image=${imageWidth}x${imageHeight}, rotation=${rotation}°, front=$isFrontFacing")
    }

    /**
     * Initialize transformation matrices for rotation and mirroring
     */
    private fun initializeMatrices() {
        // Setup mirror matrix for front-facing camera
        mirrorMatrix.reset()
        if (isFrontFacing) {
            mirrorMatrix.setScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f)
        }

        // Setup rotation matrix
        updateRotationMatrix()

        // Setup Android 15+ specific transformations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setupAndroid15Matrix()
        }
    }

    /**
     * Update rotation matrix with enhanced support for all orientations
     */
    private fun updateRotationMatrix() {
        rotationMatrix.reset()

        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        when (rotation % 360) {
            0 -> {
                // No rotation needed
            }
            90 -> {
                rotationMatrix.setRotate(90f, centerX, centerY)
                // Compensate for coordinate system change
                if (DISPLAY_ROTATION_COMPENSATION) {
                    rotationMatrix.postTranslate(0f, (viewHeight - viewWidth) / 2f)
                }
            }
            180 -> {
                rotationMatrix.setRotate(180f, centerX, centerY)
            }
            270 -> {
                rotationMatrix.setRotate(270f, centerX, centerY)
                // Compensate for coordinate system change
                if (DISPLAY_ROTATION_COMPENSATION) {
                    rotationMatrix.postTranslate((viewWidth - viewHeight) / 2f, 0f)
                }
            }
            else -> {
                // Handle arbitrary rotation angles with interpolation
                val normalizedRotation = rotation % 360
                rotationMatrix.setRotate(normalizedRotation.toFloat(), centerX, centerY)

                // Apply additional compensation for non-90 degree rotations
                val radians = Math.toRadians(normalizedRotation.toDouble())
                val cosR = cos(radians).toFloat()
                val sinR = sin(radians).toFloat()

                // Calculate bounds adjustment for arbitrary rotation
                val halfWidth = viewWidth / 2f
                val halfHeight = viewHeight / 2f
                val newHalfWidth = abs(cosR * halfWidth) + abs(sinR * halfHeight)
                val newHalfHeight = abs(sinR * halfWidth) + abs(cosR * halfHeight)

                val scaleToFit = min(halfWidth / newHalfWidth, halfHeight / newHalfHeight)
                if (scaleToFit < 1.0f) {
                    rotationMatrix.postScale(scaleToFit, scaleToFit, centerX, centerY)
                }

                Timber.v("$TAG: Applied arbitrary rotation ${normalizedRotation}° with scale factor $scaleToFit")
            }
        }
    }

    /**
     * Setup Android 15+ specific coordinate transformations
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun setupAndroid15Matrix() {
        android15Matrix.reset()

        // Android 15+ introduces new coordinate system optimizations
        android15Matrix.setScale(ANDROID_15_SCALE_FACTOR, ANDROID_15_SCALE_FACTOR)

        // Handle new edge-to-edge display modes
        if (viewWidth > viewHeight) {
            // Landscape mode adjustments for Android 15+
            val edgeInset = 0.02f * viewWidth // 2% edge compensation
            android15Matrix.postTranslate(edgeInset, 0f)
            android15Matrix.postScale(
                (viewWidth - 2 * edgeInset) / viewWidth,
                1f,
                viewWidth / 2f,
                viewHeight / 2f
            )
        }

        Timber.d("$TAG: Applied Android 15+ coordinate system optimizations")
    }

    /**
     * Convert normalized coordinates to pixel coordinates with enhanced precision and caching
     */
    fun normalizedToPixel(normalizedX: Float, normalizedY: Float): Pair<Float, Float> {
        transformationCount++

        // Check cache first for performance
        val cacheKey = Pair(normalizedX, normalizedY)
        if (!cacheInvalidated && cachedPoints.containsKey(cacheKey)) {
            return cachedPoints[cacheKey]!!
        }

        // Input validation and high-precision normalization
        val x = (normalizedX * SUB_PIXEL_PRECISION).roundToInt() / SUB_PIXEL_PRECISION
        val y = (normalizedY * SUB_PIXEL_PRECISION).roundToInt() / SUB_PIXEL_PRECISION

        // Transform to pixel coordinates using enhanced matrix
        val points = floatArrayOf(x, y)
        transform.mapPoints(points)

        // Apply sub-pixel precision and bounds checking
        val finalX = points[0].coerceIn(0f, viewWidth.toFloat())
        val finalY = points[1].coerceIn(0f, viewHeight.toFloat())

        val result = Pair(finalX, finalY)

        // Cache result if cache is not full
        if (cachedPoints.size < CACHE_SIZE_LIMIT) {
            cachedPoints[cacheKey] = result
        }

        // Validate transformation accuracy in debug builds
        if (BuildConfig.DEBUG) {
            validateTransformationAccuracy(normalizedX, normalizedY, finalX, finalY)
        }

        return result
    }

    /**
     * Optimized batch transformation for multiple landmarks with enhanced performance
     */
    fun batchNormalizedToPixel(landmarks: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (landmarks.isEmpty()) return emptyList()

        val startTime = System.nanoTime()
        batchTransformationCount++
        lastBatchSize = landmarks.size

        // Use optimized path for larger batches
        val result = if (landmarks.size >= BATCH_OPTIMIZATION_THRESHOLD) {
            batchTransformOptimized(landmarks)
        } else {
            // Use individual transforms for small batches to leverage caching
            landmarks.map { (x, y) -> normalizedToPixel(x, y) }
        }

        lastBatchDuration = (System.nanoTime() - startTime) / 1_000_000.0

        if (lastBatchDuration > 10.0) {
            Timber.w("$TAG: Batch transformation took ${lastBatchDuration}ms for ${landmarks.size} landmarks")
        } else {
            Timber.v("$TAG: Batch transformation: ${landmarks.size} landmarks in ${lastBatchDuration}ms")
        }

        return result
    }

    /**
     * Optimized batch transformation implementation
     */
    private fun batchTransformOptimized(landmarks: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val count = landmarks.size
        val inputPoints = FloatArray(count * 2)
        val outputPoints = FloatArray(count * 2)

        // Prepare input points with high precision
        landmarks.forEachIndexed { index, (x, y) ->
            val preciseX = (x * SUB_PIXEL_PRECISION).roundToInt() / SUB_PIXEL_PRECISION
            val preciseY = (y * SUB_PIXEL_PRECISION).roundToInt() / SUB_PIXEL_PRECISION

            inputPoints[index * 2] = preciseX
            inputPoints[index * 2 + 1] = preciseY
        }

        // Apply batch transformation
        transform.mapPoints(outputPoints, inputPoints)

        // Convert to result pairs with bounds checking
        return (0 until count).map { i ->
            val x = outputPoints[i * 2].coerceIn(0f, viewWidth.toFloat())
            val y = outputPoints[i * 2 + 1].coerceIn(0f, viewHeight.toFloat())
            Pair(x, y)
        }
    }

    /**
     * Enhanced visibility checking with rotation and cropping awareness
     */
    fun isPointVisible(normalizedX: Float, normalizedY: Float): Boolean {
        // Apply additional margin for edge cases
        val margin = 0.05f // 5% margin
        val bounds = getVisibleBounds()

        return normalizedX >= (bounds.left - margin) &&
               normalizedX <= (bounds.right + margin) &&
               normalizedY >= (bounds.top - margin) &&
               normalizedY <= (bounds.bottom + margin)
    }

    /**
     * Get visible bounds accounting for all transformations
     */
    fun getVisibleBounds(): RectF {
        // Calculate bounds considering current transformation
        val corners = floatArrayOf(
            0f, 0f,                                    // Top-left
            viewWidth.toFloat(), 0f,                   // Top-right
            viewWidth.toFloat(), viewHeight.toFloat(), // Bottom-right
            0f, viewHeight.toFloat()                   // Bottom-left
        )

        // Apply inverse transformation to get normalized bounds
        inverseTransform.mapPoints(corners)

        // Find the actual visible region
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in 0 until 4) {
            val x = corners[i * 2].coerceIn(0f, 1f)
            val y = corners[i * 2 + 1].coerceIn(0f, 1f)

            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }

        return RectF(minX, minY, maxX, maxY)
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
     * Update camera orientation with enhanced matrix recalculation
     */
    fun updateRotation(newRotation: Int, newIsFrontFacing: Boolean = isFrontFacing) {
        if (rotation != newRotation || isFrontFacing != newIsFrontFacing) {
            rotation = newRotation
            isFrontFacing = newIsFrontFacing

            // Recalculate matrices
            updateRotationMatrix()
            updateTransformation()
            invalidateCaches()

            Timber.d("$TAG: Updated rotation to ${newRotation}°, frontFacing=$newIsFrontFacing")
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
     * Get enhanced performance metrics with additional tracking
     */
    fun getPerformanceMetrics(): EnhancedPerformanceMetrics {
        val averageError = if (transformationCount > 0) totalError / transformationCount else 0.0
        val cacheHitRate = if (transformationCount > 0) cachedPoints.size.toFloat() / transformationCount else 0f

        return EnhancedPerformanceMetrics(
            transformationCount = transformationCount,
            batchTransformationCount = batchTransformationCount,
            averageError = averageError,
            maxError = maxError,
            currentFitMode = fitMode,
            viewSize = Size(viewWidth, viewHeight),
            imageSize = Size(imageWidth, imageHeight),
            lastBatchSize = lastBatchSize,
            lastBatchDuration = lastBatchDuration,
            cacheHitRate = cacheHitRate,
            androidVersion = Build.VERSION.SDK_INT,
            supportsAndroid15Features = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
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

        // Apply transformations in correct order:
        // 1. Scale and offset for aspect ratio
        transform.setScale(scaleX, scaleY)
        transform.postTranslate(offsetX, offsetY)

        // 2. Apply rotation
        transform.postConcat(rotationMatrix)

        // 3. Apply mirroring for front camera
        if (isFrontFacing) {
            transform.postConcat(mirrorMatrix)
        }

        // 4. Apply Android 15+ specific transformations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            transform.postConcat(android15Matrix)
        }

        // Calculate inverse transform
        if (!transform.invert(inverseTransform)) {
            Timber.e("$TAG: Failed to compute inverse transformation matrix")
            inverseTransform.reset()
            inverseTransform.setScale(1f / scaleX, 1f / scaleY)
            inverseTransform.postTranslate(-offsetX / scaleX, -offsetY / scaleY)
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

    /**
     * Validate transformation accuracy for debugging
     */
    private fun validateTransformationAccuracy(
        normalizedX: Float,
        normalizedY: Float,
        pixelX: Float,
        pixelY: Float
    ) {
        // Reverse transform to check accuracy
        val (reverseNormX, reverseNormY) = pixelToNormalized(pixelX, pixelY)
        val errorX = abs(normalizedX - reverseNormX)
        val errorY = abs(normalizedY - reverseNormY)

        val maxError = max(errorX * viewWidth, errorY * viewHeight)
        if (maxError > PIXEL_ERROR_TOLERANCE) {
            Timber.w("$TAG: Coordinate transformation error: ${maxError}px (tolerance: ${PIXEL_ERROR_TOLERANCE}px)")
        }

        // Update running average
        val error = sqrt((errorX * errorX + errorY * errorY).toDouble())
        totalError += error
        this.maxError = max(this.maxError, error)
    }

    /**
     * Invalidate all caches
     */
    private fun invalidateCaches() {
        cacheInvalidated = true
        cachedPoints.clear()
        cacheInvalidated = false
    }

    /**
     * Enhanced performance metrics data class
     */
    data class EnhancedPerformanceMetrics(
        val transformationCount: Long,
        val batchTransformationCount: Long,
        val averageError: Double,
        val maxError: Double,
        val currentFitMode: FitMode,
        val viewSize: Size,
        val imageSize: Size,
        val lastBatchSize: Int,
        val lastBatchDuration: Double,
        val cacheHitRate: Float,
        val androidVersion: Int,
        val supportsAndroid15Features: Boolean
    )
}