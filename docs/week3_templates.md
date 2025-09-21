# Week 3 Implementation Templates

## Enhanced CoordinateMapper.kt Template

```kotlin
package com.posecoach.app.overlay

import android.graphics.Matrix
import android.graphics.PointF
import com.posecoach.core.pose.models.PoseLandmark33
import kotlin.math.*

class CoordinateMapper {

    private var transformMatrix: Matrix? = null
    private var lastImageDimensions: Pair<Int, Int>? = null
    private var lastViewDimensions: Pair<Int, Int>? = null

    fun updateDimensions(
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val currentImageDims = Pair(imageWidth, imageHeight)
        val currentViewDims = Pair(viewWidth, viewHeight)

        if (lastImageDimensions != currentImageDims || lastViewDimensions != currentViewDims) {
            transformMatrix = calculateTransformMatrix(imageWidth, imageHeight, viewWidth, viewHeight)
            lastImageDimensions = currentImageDims
            lastViewDimensions = currentViewDims
        }
    }

    fun mapNormalizedToPixel(
        landmark: PoseLandmark33,
        viewWidth: Int,
        viewHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): PixelCoordinate {
        updateDimensions(imageWidth, imageHeight, viewWidth, viewHeight)

        // Convert normalized coordinates (0-1) to image coordinates
        val imageX = landmark.x * imageWidth
        val imageY = landmark.y * imageHeight

        // Apply transformation matrix for accurate mapping
        val transformedPoint = transformMatrix?.let { matrix ->
            val points = floatArrayOf(imageX, imageY)
            matrix.mapPoints(points)
            PointF(points[0], points[1])
        } ?: PointF(imageX, imageY)

        return PixelCoordinate(
            x = transformedPoint.x,
            y = transformedPoint.y,
            z = landmark.z * imageWidth, // Scale Z relative to image width
            confidence = landmark.visibility
        )
    }

    private fun calculateTransformMatrix(
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ): Matrix {
        val matrix = Matrix()

        // Calculate scale factors
        val scaleX = viewWidth.toFloat() / imageWidth.toFloat()
        val scaleY = viewHeight.toFloat() / imageHeight.toFloat()

        // Use uniform scaling to maintain aspect ratio
        val scale = minOf(scaleX, scaleY)

        // Calculate translation to center the image
        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale
        val translateX = (viewWidth - scaledImageWidth) / 2f
        val translateY = (viewHeight - scaledImageHeight) / 2f

        matrix.setScale(scale, scale)
        matrix.postTranslate(translateX, translateY)

        return matrix
    }

    fun calculateMappingAccuracy(
        testLandmarks: List<TestLandmark>
    ): AccuracyMetrics {
        val errors = testLandmarks.map { testLandmark ->
            val mapped = mapNormalizedToPixel(
                testLandmark.landmark,
                testLandmark.viewWidth,
                testLandmark.viewHeight,
                testLandmark.imageWidth,
                testLandmark.imageHeight
            )

            val distance = sqrt(
                (mapped.x - testLandmark.expectedPixel.x).pow(2) +
                (mapped.y - testLandmark.expectedPixel.y).pow(2)
            )

            distance
        }

        return AccuracyMetrics(
            averageError = errors.average(),
            maxError = errors.maxOrNull() ?: 0.0,
            pixelsWithinTolerance = errors.count { it < 2.0 },
            totalPixels = errors.size
        )
    }
}

data class PixelCoordinate(
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: Float
)

data class TestLandmark(
    val landmark: PoseLandmark33,
    val expectedPixel: PixelCoordinate,
    val viewWidth: Int,
    val viewHeight: Int,
    val imageWidth: Int,
    val imageHeight: Int
)

data class AccuracyMetrics(
    val averageError: Double,
    val maxError: Double,
    val pixelsWithinTolerance: Int,
    val totalPixels: Int
) {
    val accuracyPercentage: Double
        get() = (pixelsWithinTolerance.toDouble() / totalPixels) * 100
}
```

## Enhanced PoseOverlayView.kt Template

```kotlin
package com.posecoach.app.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.posecoach.core.pose.models.PoseDetectionResult
import com.posecoach.core.pose.models.PoseLandmark33
import timber.log.Timber

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val coordinateMapper = CoordinateMapper()
    private val skeletonRenderer = SkeletonRenderer()

    private var currentPose: PoseDetectionResult? = null
    private var imageDimensions: Pair<Int, Int>? = null

    // Performance optimization - reuse paint objects
    private val landmarkPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val connectionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    // Frame rate monitoring
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    fun updatePose(
        poseResult: PoseDetectionResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        currentPose = poseResult
        imageDimensions = Pair(imageWidth, imageHeight)

        // Update coordinate mapping
        coordinateMapper.updateDimensions(imageWidth, imageHeight, width, height)

        invalidate()
        monitorFrameRate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pose = currentPose ?: return
        val (imageWidth, imageHeight) = imageDimensions ?: return

        if (width == 0 || height == 0) return

        drawPose(canvas, pose, imageWidth, imageHeight)
    }

    private fun drawPose(
        canvas: Canvas,
        pose: PoseDetectionResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        val landmarks = pose.landmarks
        if (landmarks.isEmpty()) return

        // Convert all landmarks to pixel coordinates
        val pixelLandmarks = landmarks.map { landmark ->
            coordinateMapper.mapNormalizedToPixel(
                landmark,
                width,
                height,
                imageWidth,
                imageHeight
            )
        }

        // Draw skeleton connections first (behind landmarks)
        drawSkeletonConnections(canvas, pixelLandmarks, landmarks)

        // Draw landmarks on top
        drawLandmarks(canvas, pixelLandmarks, landmarks)

        // Draw confidence indicator
        drawConfidenceIndicator(canvas, pose.confidence)
    }

    private fun drawSkeletonConnections(
        canvas: Canvas,
        pixelLandmarks: List<PixelCoordinate>,
        originalLandmarks: List<PoseLandmark33>
    ) {
        val connections = PoseConnections.getConnections()

        connections.forEach { (startIdx, endIdx) ->
            if (startIdx < pixelLandmarks.size && endIdx < pixelLandmarks.size) {
                val startLandmark = originalLandmarks[startIdx]
                val endLandmark = originalLandmarks[endIdx]

                // Only draw if both landmarks are visible
                if (startLandmark.visibility > 0.5f && endLandmark.visibility > 0.5f) {
                    val startPixel = pixelLandmarks[startIdx]
                    val endPixel = pixelLandmarks[endIdx]

                    // Calculate line opacity based on confidence
                    val avgConfidence = (startLandmark.visibility + endLandmark.visibility) / 2f
                    connectionPaint.alpha = (avgConfidence * 255).toInt()
                    connectionPaint.color = getConnectionColor(startIdx, endIdx)

                    canvas.drawLine(
                        startPixel.x,
                        startPixel.y,
                        endPixel.x,
                        endPixel.y,
                        connectionPaint
                    )
                }
            }
        }
    }

    private fun drawLandmarks(
        canvas: Canvas,
        pixelLandmarks: List<PixelCoordinate>,
        originalLandmarks: List<PoseLandmark33>
    ) {
        pixelLandmarks.forEachIndexed { index, pixelLandmark ->
            val originalLandmark = originalLandmarks[index]

            if (originalLandmark.visibility > 0.5f) {
                // Adjust size based on confidence and landmark importance
                val radius = getLandmarkRadius(index, originalLandmark.visibility)

                landmarkPaint.color = getLandmarkColor(index, originalLandmark.visibility)
                landmarkPaint.alpha = (originalLandmark.visibility * 255).toInt()

                canvas.drawCircle(
                    pixelLandmark.x,
                    pixelLandmark.y,
                    radius,
                    landmarkPaint
                )
            }
        }
    }

    private fun drawConfidenceIndicator(canvas: Canvas, confidence: Float) {
        val indicatorWidth = 100f
        val indicatorHeight = 10f
        val margin = 20f

        val rect = RectF(
            margin,
            margin,
            margin + indicatorWidth,
            margin + indicatorHeight
        )

        // Background
        landmarkPaint.color = Color.GRAY
        landmarkPaint.alpha = 128
        canvas.drawRect(rect, landmarkPaint)

        // Confidence fill
        val fillWidth = indicatorWidth * confidence
        val fillRect = RectF(
            margin,
            margin,
            margin + fillWidth,
            margin + indicatorHeight
        )

        landmarkPaint.color = getConfidenceColor(confidence)
        landmarkPaint.alpha = 255
        canvas.drawRect(fillRect, landmarkPaint)
    }

    private fun getLandmarkRadius(index: Int, visibility: Float): Float {
        val baseRadius = when (index) {
            0, 9, 10 -> 8f // Head landmarks - larger
            11, 12, 23, 24 -> 6f // Body core - medium
            else -> 4f // Extremities - smaller
        }
        return baseRadius * visibility
    }

    private fun getLandmarkColor(index: Int, visibility: Float): Int {
        val alpha = (visibility * 255).toInt()
        return when (index) {
            in 0..10 -> Color.argb(alpha, 255, 100, 100) // Head - red
            in 11..16 -> Color.argb(alpha, 100, 255, 100) // Arms - green
            in 17..22 -> Color.argb(alpha, 100, 100, 255) // Hands - blue
            else -> Color.argb(alpha, 255, 255, 100) // Body/legs - yellow
        }
    }

    private fun getConnectionColor(startIdx: Int, endIdx: Int): Int {
        return when {
            startIdx <= 10 || endIdx <= 10 -> Color.RED // Head connections
            startIdx in 11..16 || endIdx in 11..16 -> Color.GREEN // Arm connections
            else -> Color.BLUE // Body/leg connections
        }
    }

    private fun getConfidenceColor(confidence: Float): Int {
        return when {
            confidence > 0.8f -> Color.GREEN
            confidence > 0.6f -> Color.YELLOW
            else -> Color.RED
        }
    }

    private fun monitorFrameRate() {
        frameCount++
        if (frameCount % 30 == 0) {
            val currentTime = System.currentTimeMillis()
            val fps = (frameCount * 1000.0) / (currentTime - lastFpsTime)
            Timber.d("Overlay rendering FPS: %.1f", fps)

            frameCount = 0
            lastFpsTime = currentTime
        }
    }

    fun clearPose() {
        currentPose = null
        invalidate()
    }
}

object PoseConnections {
    fun getConnections(): List<Pair<Int, Int>> {
        return listOf(
            // Face
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7),
            Pair(0, 4), Pair(4, 5), Pair(5, 6), Pair(6, 8),

            // Body
            Pair(9, 10),
            Pair(11, 12), Pair(11, 13), Pair(13, 15),
            Pair(12, 14), Pair(14, 16),

            // Arms
            Pair(15, 17), Pair(15, 19), Pair(15, 21),
            Pair(16, 18), Pair(16, 20), Pair(16, 22),

            // Torso
            Pair(11, 23), Pair(12, 24), Pair(23, 24),

            // Legs
            Pair(23, 25), Pair(24, 26),
            Pair(25, 27), Pair(26, 28),
            Pair(27, 29), Pair(28, 30),
            Pair(29, 31), Pair(30, 32)
        )
    }
}
```

## OverlayRenderer.kt Template

```kotlin
package com.posecoach.app.overlay

import android.graphics.*
import com.posecoach.core.pose.models.PoseDetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OverlayRenderer {

    private val renderCache = mutableMapOf<String, Bitmap>()
    private val maxCacheSize = 10

    suspend fun renderPoseOverlay(
        poseResult: PoseDetectionResult,
        overlayWidth: Int,
        overlayHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        style: RenderStyle = RenderStyle.default()
    ): Bitmap = withContext(Dispatchers.Default) {

        val cacheKey = generateCacheKey(poseResult, overlayWidth, overlayHeight)
        renderCache[cacheKey]?.let { return@withContext it }

        val bitmap = Bitmap.createBitmap(
            overlayWidth,
            overlayHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        val coordinateMapper = CoordinateMapper()
        coordinateMapper.updateDimensions(imageWidth, imageHeight, overlayWidth, overlayHeight)

        renderPoseToCanvas(
            canvas,
            poseResult,
            coordinateMapper,
            overlayWidth,
            overlayHeight,
            imageWidth,
            imageHeight,
            style
        )

        // Cache management
        if (renderCache.size >= maxCacheSize) {
            val oldestKey = renderCache.keys.first()
            renderCache.remove(oldestKey)
        }
        renderCache[cacheKey] = bitmap

        bitmap
    }

    private fun renderPoseToCanvas(
        canvas: Canvas,
        poseResult: PoseDetectionResult,
        coordinateMapper: CoordinateMapper,
        overlayWidth: Int,
        overlayHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        style: RenderStyle
    ) {
        val landmarks = poseResult.landmarks
        if (landmarks.isEmpty()) return

        val pixelLandmarks = landmarks.map { landmark ->
            coordinateMapper.mapNormalizedToPixel(
                landmark,
                overlayWidth,
                overlayHeight,
                imageWidth,
                imageHeight
            )
        }

        // Render in layers for proper z-ordering
        if (style.showConnections) {
            renderConnections(canvas, pixelLandmarks, landmarks, style)
        }

        if (style.showLandmarks) {
            renderLandmarks(canvas, pixelLandmarks, landmarks, style)
        }

        if (style.showLabels) {
            renderLabels(canvas, pixelLandmarks, landmarks, style)
        }
    }

    private fun renderConnections(
        canvas: Canvas,
        pixelLandmarks: List<PixelCoordinate>,
        landmarks: List<com.posecoach.core.pose.models.PoseLandmark33>,
        style: RenderStyle
    ) {
        val connectionPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = style.connectionWidth
            strokeCap = Paint.Cap.ROUND
        }

        PoseConnections.getConnections().forEach { (startIdx, endIdx) ->
            if (startIdx < landmarks.size && endIdx < landmarks.size) {
                val startLandmark = landmarks[startIdx]
                val endLandmark = landmarks[endIdx]

                if (startLandmark.visibility > style.visibilityThreshold &&
                    endLandmark.visibility > style.visibilityThreshold) {

                    val startPixel = pixelLandmarks[startIdx]
                    val endPixel = pixelLandmarks[endIdx]

                    val avgConfidence = (startLandmark.visibility + endLandmark.visibility) / 2f
                    connectionPaint.color = style.connectionColor
                    connectionPaint.alpha = (avgConfidence * style.connectionOpacity * 255).toInt()

                    canvas.drawLine(
                        startPixel.x,
                        startPixel.y,
                        endPixel.x,
                        endPixel.y,
                        connectionPaint
                    )
                }
            }
        }
    }

    private fun renderLandmarks(
        canvas: Canvas,
        pixelLandmarks: List<PixelCoordinate>,
        landmarks: List<com.posecoach.core.pose.models.PoseLandmark33>,
        style: RenderStyle
    ) {
        val landmarkPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        pixelLandmarks.forEachIndexed { index, pixelLandmark ->
            val landmark = landmarks[index]

            if (landmark.visibility > style.visibilityThreshold) {
                val radius = style.landmarkRadius * landmark.visibility

                landmarkPaint.color = style.landmarkColor
                landmarkPaint.alpha = (landmark.visibility * style.landmarkOpacity * 255).toInt()

                canvas.drawCircle(
                    pixelLandmark.x,
                    pixelLandmark.y,
                    radius,
                    landmarkPaint
                )
            }
        }
    }

    private fun renderLabels(
        canvas: Canvas,
        pixelLandmarks: List<PixelCoordinate>,
        landmarks: List<com.posecoach.core.pose.models.PoseLandmark33>,
        style: RenderStyle
    ) {
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = style.labelColor
            textSize = style.labelTextSize
            textAlign = Paint.Align.CENTER
        }

        landmarks.forEachIndexed { index, landmark ->
            if (landmark.visibility > style.visibilityThreshold) {
                val pixelLandmark = pixelLandmarks[index]
                val label = getLandmarkLabel(index)

                canvas.drawText(
                    label,
                    pixelLandmark.x,
                    pixelLandmark.y - style.landmarkRadius - 5f,
                    textPaint
                )
            }
        }
    }

    private fun getLandmarkLabel(index: Int): String {
        return when (index) {
            0 -> "Nose"
            11 -> "L Shoulder"
            12 -> "R Shoulder"
            13 -> "L Elbow"
            14 -> "R Elbow"
            15 -> "L Wrist"
            16 -> "R Wrist"
            23 -> "L Hip"
            24 -> "R Hip"
            25 -> "L Knee"
            26 -> "R Knee"
            27 -> "L Ankle"
            28 -> "R Ankle"
            else -> "$index"
        }
    }

    private fun generateCacheKey(
        poseResult: PoseDetectionResult,
        width: Int,
        height: Int
    ): String {
        return "${poseResult.timestampMs}_${width}x${height}_${poseResult.landmarks.hashCode()}"
    }

    fun clearCache() {
        renderCache.clear()
    }
}

data class RenderStyle(
    val showLandmarks: Boolean = true,
    val showConnections: Boolean = true,
    val showLabels: Boolean = false,
    val landmarkRadius: Float = 6f,
    val connectionWidth: Float = 4f,
    val landmarkColor: Int = Color.RED,
    val connectionColor: Int = Color.BLUE,
    val labelColor: Int = Color.WHITE,
    val landmarkOpacity: Float = 0.8f,
    val connectionOpacity: Float = 0.6f,
    val labelTextSize: Float = 12f,
    val visibilityThreshold: Float = 0.5f
) {
    companion object {
        fun default() = RenderStyle()

        fun minimal() = RenderStyle(
            showLabels = false,
            landmarkOpacity = 0.6f,
            connectionOpacity = 0.4f
        )
    }
}
```

## Test Templates

### CoordinateMapperTest.kt
```kotlin
package com.posecoach.app.overlay

import com.google.common.truth.Truth.assertThat
import com.posecoach.core.pose.models.PoseLandmark33
import org.junit.Before
import org.junit.Test

class CoordinateMapperTest {

    private lateinit var coordinateMapper: CoordinateMapper

    @Before
    fun setup() {
        coordinateMapper = CoordinateMapper()
    }

    @Test
    fun `coordinate mapping accuracy should be within 2 pixels`() {
        val testCases = listOf(
            createTestCase(0.5f, 0.5f, 800, 600, 400, 300), // Center
            createTestCase(0.0f, 0.0f, 800, 600, 400, 300), // Top-left
            createTestCase(1.0f, 1.0f, 800, 600, 400, 300), // Bottom-right
            createTestCase(0.25f, 0.75f, 1080, 1920, 540, 960) // Different aspect ratio
        )

        testCases.forEach { testCase ->
            val result = coordinateMapper.mapNormalizedToPixel(
                testCase.landmark,
                testCase.viewWidth,
                testCase.viewHeight,
                testCase.imageWidth,
                testCase.imageHeight
            )

            val distance = calculateDistance(result, testCase.expectedPixel)
            assertThat(distance).isLessThan(2.0)
        }
    }

    private fun createTestCase(
        normalizedX: Float,
        normalizedY: Float,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ): TestLandmark {
        val landmark = PoseLandmark33(
            id = 0,
            x = normalizedX,
            y = normalizedY,
            z = 0f,
            visibility = 1f,
            presence = 1f
        )

        // Calculate expected pixel coordinate manually
        val expectedX = normalizedX * viewWidth
        val expectedY = normalizedY * viewHeight

        return TestLandmark(
            landmark = landmark,
            expectedPixel = PixelCoordinate(expectedX, expectedY, 0f, 1f),
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    private fun calculateDistance(actual: PixelCoordinate, expected: PixelCoordinate): Double {
        val dx = actual.x - expected.x
        val dy = actual.y - expected.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
```