package com.posecoach.app.camera

import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.view.Surface
import com.posecoach.app.overlay.FitMode
import timber.log.Timber
import kotlin.math.*

/**
 * Comprehensive rotation and transformation manager for CameraX pipeline.
 * Handles device rotations, camera sensor orientations, and coordinate system transformations.
 */
class RotationTransformManager {

    data class TransformationConfig(
        val sourceSize: Size,
        val targetSize: Size,
        val sensorOrientation: Int,
        val displayRotation: Int,
        val isFrontFacing: Boolean,
        val fitMode: FitMode,
        val mirrorMode: MirrorMode = MirrorMode.AUTO
    )

    data class TransformationResult(
        val matrix: Matrix,
        val rotationDegrees: Int,
        val effectiveRotation: Int,
        val scaleX: Float,
        val scaleY: Float,
        val translateX: Float,
        val translateY: Float,
        val cropRect: RectF?,
        val isValid: Boolean
    )

    enum class MirrorMode {
        NONE,       // No mirroring
        HORIZONTAL, // Mirror horizontally
        VERTICAL,   // Mirror vertically
        AUTO        // Auto-detect based on camera facing
    }

    companion object {
        private const val TAG = "RotationTransformManager"

        // Standard rotation angles in degrees
        private const val ROTATION_0 = 0
        private const val ROTATION_90 = 90
        private const val ROTATION_180 = 180
        private const val ROTATION_270 = 270

        // Common camera sensor orientations
        private const val SENSOR_ORIENTATION_0 = 0
        private const val SENSOR_ORIENTATION_90 = 90
        private const val SENSOR_ORIENTATION_180 = 180
        private const val SENSOR_ORIENTATION_270 = 270

        // Error tolerance for floating point comparisons
        private const val FLOAT_TOLERANCE = 0.001f
    }

    /**
     * Calculate comprehensive transformation matrix for given configuration
     */
    fun calculateTransformation(config: TransformationConfig): TransformationResult {
        try {
            Timber.d("Calculating transformation: ${config}")

            val matrix = Matrix()
            val effectiveRotation = calculateEffectiveRotation(
                config.sensorOrientation,
                config.displayRotation,
                config.isFrontFacing
            )

            // Calculate scale factors based on fit mode
            val (scaleX, scaleY, translateX, translateY) = calculateScaleAndTranslation(
                config.sourceSize,
                config.targetSize,
                config.fitMode
            )

            // Build transformation matrix step by step
            buildTransformationMatrix(
                matrix,
                config.sourceSize,
                config.targetSize,
                effectiveRotation,
                scaleX,
                scaleY,
                translateX,
                translateY,
                config.mirrorMode,
                config.isFrontFacing
            )

            // Calculate crop rectangle if needed
            val cropRect = calculateCropRect(
                config.sourceSize,
                config.targetSize,
                config.fitMode
            )

            return TransformationResult(
                matrix = matrix,
                rotationDegrees = config.displayRotation,
                effectiveRotation = effectiveRotation,
                scaleX = scaleX,
                scaleY = scaleY,
                translateX = translateX,
                translateY = translateY,
                cropRect = cropRect,
                isValid = true
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate transformation")
            return TransformationResult(
                matrix = Matrix(),
                rotationDegrees = 0,
                effectiveRotation = 0,
                scaleX = 1f,
                scaleY = 1f,
                translateX = 0f,
                translateY = 0f,
                cropRect = null,
                isValid = false
            )
        }
    }

    /**
     * Calculate effective rotation considering sensor orientation and display rotation
     */
    private fun calculateEffectiveRotation(
        sensorOrientation: Int,
        displayRotation: Int,
        isFrontFacing: Boolean
    ): Int {
        val displayRotationDegrees = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        val rotation = if (isFrontFacing) {
            (sensorOrientation + displayRotationDegrees) % 360
        } else {
            (sensorOrientation - displayRotationDegrees + 360) % 360
        }

        Timber.d("Effective rotation: sensor=$sensorOrientation, display=$displayRotationDegrees, front=$isFrontFacing -> $rotation")
        return rotation
    }

    /**
     * Calculate scale factors and translation for different fit modes
     */
    private fun calculateScaleAndTranslation(
        sourceSize: Size,
        targetSize: Size,
        fitMode: FitMode
    ): Quadruple<Float, Float, Float, Float> {
        val sourceAspect = sourceSize.width.toFloat() / sourceSize.height.toFloat()
        val targetAspect = targetSize.width.toFloat() / targetSize.height.toFloat()

        val (scaleX, scaleY) = when (fitMode) {
            FitMode.FILL -> {
                // Stretch to fill, may distort aspect ratio
                Pair(
                    targetSize.width.toFloat() / sourceSize.width.toFloat(),
                    targetSize.height.toFloat() / sourceSize.height.toFloat()
                )
            }
            FitMode.CENTER_CROP -> {
                // Maintain aspect ratio, crop if necessary
                val scale = max(
                    targetSize.width.toFloat() / sourceSize.width.toFloat(),
                    targetSize.height.toFloat() / sourceSize.height.toFloat()
                )
                Pair(scale, scale)
            }
            FitMode.CENTER_INSIDE -> {
                // Maintain aspect ratio, fit inside target
                val scale = min(
                    targetSize.width.toFloat() / sourceSize.width.toFloat(),
                    targetSize.height.toFloat() / sourceSize.height.toFloat()
                )
                Pair(scale, scale)
            }
        }

        // Calculate translation to center the image
        val scaledWidth = sourceSize.width * scaleX
        val scaledHeight = sourceSize.height * scaleY
        val translateX = (targetSize.width - scaledWidth) / 2f
        val translateY = (targetSize.height - scaledHeight) / 2f

        Timber.d("Scale and translation: scale=($scaleX, $scaleY), translate=($translateX, $translateY)")
        return Quadruple(scaleX, scaleY, translateX, translateY)
    }

    /**
     * Build comprehensive transformation matrix
     */
    private fun buildTransformationMatrix(
        matrix: Matrix,
        sourceSize: Size,
        targetSize: Size,
        rotation: Int,
        scaleX: Float,
        scaleY: Float,
        translateX: Float,
        translateY: Float,
        mirrorMode: MirrorMode,
        isFrontFacing: Boolean
    ) {
        matrix.reset()

        // Step 1: Move to center of source
        matrix.postTranslate(-sourceSize.width / 2f, -sourceSize.height / 2f)

        // Step 2: Apply rotation around origin
        if (rotation != 0) {
            matrix.postRotate(rotation.toFloat())
        }

        // Step 3: Apply mirroring
        val (mirrorX, mirrorY) = when (mirrorMode) {
            MirrorMode.NONE -> Pair(1f, 1f)
            MirrorMode.HORIZONTAL -> Pair(-1f, 1f)
            MirrorMode.VERTICAL -> Pair(1f, -1f)
            MirrorMode.AUTO -> {
                if (isFrontFacing) Pair(-1f, 1f) else Pair(1f, 1f)
            }
        }
        if (mirrorX != 1f || mirrorY != 1f) {
            matrix.postScale(mirrorX, mirrorY)
        }

        // Step 4: Apply scaling
        matrix.postScale(scaleX, scaleY)

        // Step 5: Move to target position
        matrix.postTranslate(
            targetSize.width / 2f + translateX,
            targetSize.height / 2f + translateY
        )

        Timber.d("Transformation matrix built: rotation=$rotation, scale=($scaleX, $scaleY), mirror=($mirrorX, $mirrorY)")
    }

    /**
     * Calculate crop rectangle for different fit modes
     */
    private fun calculateCropRect(
        sourceSize: Size,
        targetSize: Size,
        fitMode: FitMode
    ): RectF? {
        if (fitMode != FitMode.CENTER_CROP) {
            return null // No cropping needed
        }

        val sourceAspect = sourceSize.width.toFloat() / sourceSize.height.toFloat()
        val targetAspect = targetSize.width.toFloat() / targetSize.height.toFloat()

        return if (sourceAspect > targetAspect) {
            // Source is wider, crop horizontally
            val cropWidth = sourceSize.height * targetAspect
            val offsetX = (sourceSize.width - cropWidth) / 2f
            RectF(offsetX, 0f, offsetX + cropWidth, sourceSize.height.toFloat())
        } else {
            // Source is taller, crop vertically
            val cropHeight = sourceSize.width / targetAspect
            val offsetY = (sourceSize.height - cropHeight) / 2f
            RectF(0f, offsetY, sourceSize.width.toFloat(), offsetY + cropHeight)
        }
    }

    /**
     * Transform a point using the given transformation matrix
     */
    fun transformPoint(matrix: Matrix, point: PointF): PointF {
        val points = floatArrayOf(point.x, point.y)
        matrix.mapPoints(points)
        return PointF(points[0], points[1])
    }

    /**
     * Transform multiple points efficiently using batch processing
     */
    fun transformPoints(matrix: Matrix, points: List<PointF>): List<PointF> {
        if (points.isEmpty()) return emptyList()

        val flatPoints = FloatArray(points.size * 2)
        points.forEachIndexed { index, point ->
            flatPoints[index * 2] = point.x
            flatPoints[index * 2 + 1] = point.y
        }

        matrix.mapPoints(flatPoints)

        return (0 until points.size).map { index ->
            PointF(flatPoints[index * 2], flatPoints[index * 2 + 1])
        }
    }

    /**
     * Create inverse transformation matrix
     */
    fun createInverseMatrix(matrix: Matrix): Matrix {
        val inverseMatrix = Matrix()
        if (!matrix.invert(inverseMatrix)) {
            Timber.e("Failed to create inverse matrix")
            inverseMatrix.reset()
        }
        return inverseMatrix
    }

    /**
     * Validate transformation accuracy by round-trip testing
     */
    fun validateTransformation(
        forwardMatrix: Matrix,
        inverseMatrix: Matrix,
        testPoints: List<PointF>,
        tolerance: Float = 2.0f
    ): Boolean {
        var maxError = 0f
        var errorCount = 0

        testPoints.forEach { originalPoint ->
            // Forward transform
            val transformedPoint = transformPoint(forwardMatrix, originalPoint)

            // Inverse transform
            val roundTripPoint = transformPoint(inverseMatrix, transformedPoint)

            // Calculate error
            val error = sqrt(
                (originalPoint.x - roundTripPoint.x).pow(2) +
                (originalPoint.y - roundTripPoint.y).pow(2)
            )

            if (error > tolerance) {
                errorCount++
            }
            maxError = max(maxError, error)
        }

        val accuracy = 1f - (errorCount.toFloat() / testPoints.size)
        Timber.d("Transformation validation: maxError=$maxError, accuracy=${accuracy * 100}%")

        return maxError <= tolerance && accuracy >= 0.95f
    }

    /**
     * Get rotation degrees from display rotation constant
     */
    fun getRotationDegrees(displayRotation: Int): Int {
        return when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    /**
     * Normalize rotation angle to 0-359 range
     */
    fun normalizeRotation(rotation: Int): Int {
        return ((rotation % 360) + 360) % 360
    }

    /**
     * Check if two rotation angles are equivalent
     */
    fun areRotationsEquivalent(rotation1: Int, rotation2: Int): Boolean {
        return normalizeRotation(rotation1) == normalizeRotation(rotation2)
    }

    /**
     * Calculate the shortest rotation path between two angles
     */
    fun calculateRotationDelta(fromRotation: Int, toRotation: Int): Int {
        val from = normalizeRotation(fromRotation)
        val to = normalizeRotation(toRotation)

        val delta = to - from

        return when {
            delta > 180 -> delta - 360
            delta < -180 -> delta + 360
            else -> delta
        }
    }

    /**
     * Generate test points for transformation validation
     */
    fun generateTestPoints(sourceSize: Size, density: Int = 10): List<PointF> {
        val points = mutableListOf<PointF>()

        // Corner points
        points.addAll(listOf(
            PointF(0f, 0f),
            PointF(sourceSize.width.toFloat(), 0f),
            PointF(sourceSize.width.toFloat(), sourceSize.height.toFloat()),
            PointF(0f, sourceSize.height.toFloat())
        ))

        // Center point
        points.add(PointF(sourceSize.width / 2f, sourceSize.height / 2f))

        // Grid points
        for (x in 0..density) {
            for (y in 0..density) {
                val pointX = (x.toFloat() / density) * sourceSize.width
                val pointY = (y.toFloat() / density) * sourceSize.height
                points.add(PointF(pointX, pointY))
            }
        }

        return points
    }

    // Helper data class for returning four values
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}