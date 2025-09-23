package com.posecoach.app.overlay

import android.graphics.RectF
import android.util.Size
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * AspectRatioManager handles aspect ratio calculations and transformations.
 * Separated from EnhancedCoordinateMapper for better modularity (<150 lines).
 *
 * Features:
 * - Multiple fit modes (CENTER_CROP, CENTER_INSIDE, FIT_XY)
 * - Aspect ratio preservation
 * - Visible region calculation
 * - Transform metrics
 */
class AspectRatioManager {

    companion object {
        private const val TAG = "AspectRatioManager"
        private const val EPSILON = 0.001f
    }

    private var currentFitMode = FitMode.CENTER_CROP
    private var lastViewSize: Size? = null
    private var lastImageSize: Size? = null

    /**
     * Set the fit mode for aspect ratio handling
     */
    fun setFitMode(fitMode: FitMode) {
        currentFitMode = fitMode
        Timber.d("$TAG: Fit mode set to $fitMode")
    }

    /**
     * Calculate transformation for given view and image sizes
     */
    fun calculateTransform(viewSize: Size, imageSize: Size): CoordinateTransform {
        require(viewSize.width > 0 && viewSize.height > 0) { "View dimensions must be positive" }
        require(imageSize.width > 0 && imageSize.height > 0) { "Image dimensions must be positive" }

        lastViewSize = viewSize
        lastImageSize = imageSize

        val viewAspectRatio = viewSize.width.toFloat() / viewSize.height.toFloat()
        val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()

        return when (currentFitMode) {
            FitMode.CENTER_CROP -> calculateCenterCrop(viewSize, imageSize, viewAspectRatio, imageAspectRatio)
            FitMode.CENTER_INSIDE -> calculateCenterInside(viewSize, imageSize, viewAspectRatio, imageAspectRatio)
            FitMode.FIT_XY -> calculateFitXY(viewSize, imageSize)
        }
    }

    /**
     * Calculate visible region in normalized coordinates
     */
    fun calculateVisibleRegion(viewSize: Size, imageSize: Size): RectF {
        val transform = calculateTransform(viewSize, imageSize)

        // Calculate which part of the image is visible
        val visibleLeft = max(0f, -transform.offsetX / transform.scaleX) / imageSize.width
        val visibleTop = max(0f, -transform.offsetY / transform.scaleY) / imageSize.height
        val visibleRight = min(
            imageSize.width.toFloat(),
            (viewSize.width - transform.offsetX) / transform.scaleX
        ) / imageSize.width
        val visibleBottom = min(
            imageSize.height.toFloat(),
            (viewSize.height - transform.offsetY) / transform.scaleY
        ) / imageSize.height

        return RectF(visibleLeft, visibleTop, visibleRight, visibleBottom)
    }

    /**
     * Get current view aspect ratio
     */
    fun getViewAspectRatio(): Float {
        return lastViewSize?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f
    }

    /**
     * Get current image aspect ratio
     */
    fun getImageAspectRatio(): Float {
        return lastImageSize?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f
    }

    /**
     * Update dimensions for recalculation
     */
    fun updateDimensions(viewSize: Size, imageSize: Size) {
        lastViewSize = viewSize
        lastImageSize = imageSize
    }

    /**
     * Get transform metrics for analysis
     */
    fun getTransformMetrics(viewSize: Size, imageSize: Size): AspectRatioMetrics {
        val transform = calculateTransform(viewSize, imageSize)
        val visibleRegion = calculateVisibleRegion(viewSize, imageSize)

        val cropPercentageX = (1f - visibleRegion.width()) * 100f
        val cropPercentageY = (1f - visibleRegion.height()) * 100f

        val viewAspectRatio = viewSize.width.toFloat() / viewSize.height.toFloat()
        val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
        val aspectRatioError = kotlin.math.abs(viewAspectRatio - imageAspectRatio)

        return AspectRatioMetrics(
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            cropPercentageX = cropPercentageX,
            cropPercentageY = cropPercentageY,
            aspectRatioError = aspectRatioError
        )
    }

    private fun calculateCenterCrop(
        viewSize: Size,
        imageSize: Size,
        viewAspectRatio: Float,
        imageAspectRatio: Float
    ): CoordinateTransform {
        val scale = if (imageAspectRatio > viewAspectRatio) {
            // Image is wider, scale to fit height
            viewSize.height.toFloat() / imageSize.height.toFloat()
        } else {
            // Image is taller, scale to fit width
            viewSize.width.toFloat() / imageSize.width.toFloat()
        }

        val scaledImageWidth = imageSize.width * scale
        val scaledImageHeight = imageSize.height * scale

        val offsetX = (viewSize.width - scaledImageWidth) / 2f
        val offsetY = (viewSize.height - scaledImageHeight) / 2f

        return CoordinateTransform(scale, scale, offsetX, offsetY)
    }

    private fun calculateCenterInside(
        viewSize: Size,
        imageSize: Size,
        viewAspectRatio: Float,
        imageAspectRatio: Float
    ): CoordinateTransform {
        val scale = if (imageAspectRatio > viewAspectRatio) {
            // Image is wider, scale to fit width
            viewSize.width.toFloat() / imageSize.width.toFloat()
        } else {
            // Image is taller, scale to fit height
            viewSize.height.toFloat() / imageSize.height.toFloat()
        }

        val scaledImageWidth = imageSize.width * scale
        val scaledImageHeight = imageSize.height * scale

        val offsetX = (viewSize.width - scaledImageWidth) / 2f
        val offsetY = (viewSize.height - scaledImageHeight) / 2f

        return CoordinateTransform(scale, scale, offsetX, offsetY)
    }

    private fun calculateFitXY(viewSize: Size, imageSize: Size): CoordinateTransform {
        val scaleX = viewSize.width.toFloat() / imageSize.width.toFloat()
        val scaleY = viewSize.height.toFloat() / imageSize.height.toFloat()

        return CoordinateTransform(scaleX, scaleY, 0f, 0f)
    }
}