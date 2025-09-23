package com.posecoach.app.overlay

import android.graphics.PointF
import android.graphics.RectF

/**
 * Fit mode enumeration for aspect ratio handling
 */
enum class FitMode {
    CENTER_CROP,    // Scale to fill view, crop excess
    CENTER_INSIDE,  // Scale to fit within view, preserve aspect ratio
    FIT_XY         // Scale to fill view exactly, may stretch
}

/**
 * Coordinate transformation result
 */
data class CoordinateTransform(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Transformation metrics for performance monitoring
 */
data class TransformationMetrics(
    val transformationCount: Long = 0,
    val batchTransformationCount: Long = 0,
    val averageError: Double = 0.0,
    val maxError: Double = 0.0,
    val cacheHitRate: Double = 0.0,
    val lastBatchSize: Int = 0,
    val lastBatchDuration: Double = 0.0
)

/**
 * View and image dimensions
 */
data class CoordinateDimensions(
    val viewWidth: Int,
    val viewHeight: Int,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * Transform metrics for aspect ratio analysis
 */
data class AspectRatioMetrics(
    val scaleX: Float,
    val scaleY: Float,
    val cropPercentageX: Float,
    val cropPercentageY: Float,
    val aspectRatioError: Float = 0f
)

/**
 * Rotation state information
 */
data class RotationState(
    val angle: Int,
    val normalizedAngle: Int,
    val radians: Float,
    val isRotated: Boolean = normalizedAngle != 0
)