package com.posecoach.app.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.posecoach.app.privacy.PrivacyManager
import com.posecoach.corepose.SkeletonEdges
import com.posecoach.corepose.models.PoseLandmarkResult
import timber.log.Timber
import kotlin.math.min
import kotlin.math.max

/**
 * Enhanced pose overlay view with sub-pixel accuracy, privacy controls, and performance optimization.
 * Provides real-time pose visualization with <2px alignment accuracy.
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentLandmarks: PoseLandmarkResult? = null
    private var coordinateMapper: CoordinateMapper? = null
    private var privacyManager: PrivacyManager? = null

    // Multi-person pose support
    private var multiPersonLandmarks: List<PoseLandmarkResult> = emptyList()
    private var selectedPersonIndex = 0

    // Performance tracking
    private var lastRenderTime = 0L
    private var frameCount = 0L
    private var averageRenderTime = 0.0

    // Enhanced paint objects with better visual quality
    private val landmarkPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
        isDither = true
        isFilterBitmap = true
    }

    private val skeletonPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        pathEffect = CornerPathEffect(10f)
    }

    private val confidenceThresholdPaint = Paint().apply {
        color = Color.parseColor("#FFFF00")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        alpha = 180
    }

    private val lowConfidencePaint = Paint().apply {
        color = Color.parseColor("#FF6666")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        alpha = 120
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    private val performancePaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
        typeface = Typeface.DEFAULT_BOLD
    }

    private val debugPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        alpha = 150
    }

    // Configuration options
    private var showPerformanceOverlay = true
    private var showDebugInfo = false
    private var enablePrivacyMode = false
    private var minVisibilityThreshold = 0.5f
    private var minPresenceThreshold = 0.5f
    private var maxRenderFps = 30
    private var enableMultiPersonMode = false

    // Visual quality settings
    private var landmarkScale = 1.0f
    private var skeletonThickness = 1.0f
    private var animateConfidence = true

    fun updatePose(landmarks: PoseLandmarkResult) {
        if (enablePrivacyMode && privacyManager?.isLocalOnlyMode() == true) {
            // In privacy mode, only show basic skeleton without detailed landmarks
            currentLandmarks = landmarks.copy(landmarks = landmarks.landmarks.map {
                it.copy(visibility = if (it.visibility > minVisibilityThreshold) 0.8f else 0.0f)
            })
        } else {
            currentLandmarks = landmarks
        }

        // Rate limit rendering for performance
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRender = currentTime - lastRenderTime
        val minInterval = 1000L / maxRenderFps

        if (timeSinceLastRender >= minInterval) {
            postInvalidate()
            lastRenderTime = currentTime
        }
    }

    fun updateMultiPersonPoses(posesList: List<PoseLandmarkResult>) {
        if (enableMultiPersonMode) {
            multiPersonLandmarks = posesList
            if (posesList.isNotEmpty() && selectedPersonIndex < posesList.size) {
                updatePose(posesList[selectedPersonIndex])
            }
        }
    }

    fun selectPerson(index: Int) {
        if (index >= 0 && index < multiPersonLandmarks.size) {
            selectedPersonIndex = index
            updatePose(multiPersonLandmarks[index])
        }
    }

    fun setCoordinateMapper(mapper: CoordinateMapper) {
        coordinateMapper = mapper
        Timber.d("CoordinateMapper updated: ${mapper.getPerformanceMetrics()}")
        postInvalidate()
    }

    fun setPrivacyManager(privacyManager: PrivacyManager) {
        this.privacyManager = privacyManager
        enablePrivacyMode = true
    }

    fun setVisibilityThreshold(threshold: Float) {
        minVisibilityThreshold = threshold.coerceIn(0f, 1f)
        postInvalidate()
    }

    fun setPresenceThreshold(threshold: Float) {
        minPresenceThreshold = threshold.coerceIn(0f, 1f)
        postInvalidate()
    }

    fun setShowPerformance(show: Boolean) {
        showPerformanceOverlay = show
        postInvalidate()
    }

    fun setShowDebugInfo(show: Boolean) {
        showDebugInfo = show
        postInvalidate()
    }

    fun setMaxRenderFps(fps: Int) {
        maxRenderFps = fps.coerceIn(1, 60)
    }

    fun setVisualQuality(landmarkScale: Float, skeletonThickness: Float, animateConfidence: Boolean) {
        this.landmarkScale = landmarkScale.coerceIn(0.5f, 3.0f)
        this.skeletonThickness = skeletonThickness.coerceIn(0.5f, 3.0f)
        this.animateConfidence = animateConfidence
        postInvalidate()
    }

    fun enableMultiPersonMode(enable: Boolean) {
        enableMultiPersonMode = enable
        if (!enable) {
            multiPersonLandmarks = emptyList()
            selectedPersonIndex = 0
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val drawStartTime = System.nanoTime()
        frameCount++

        val landmarks = currentLandmarks ?: return
        val mapper = coordinateMapper ?: return

        // Apply privacy filtering if enabled
        if (enablePrivacyMode && privacyManager?.isLocalOnlyMode() == true) {
            drawPrivacyAwarePose(canvas, landmarks, mapper)
        } else {
            drawFullPose(canvas, landmarks, mapper)
        }

        // Draw multi-person indicators
        if (enableMultiPersonMode && multiPersonLandmarks.size > 1) {
            drawMultiPersonIndicators(canvas)
        }

        // Draw overlays
        if (showPerformanceOverlay) {
            drawPerformanceInfo(canvas, landmarks)
        }

        if (showDebugInfo) {
            drawDebugInfo(canvas, landmarks, mapper)
        }

        // Update performance metrics
        val drawDuration = (System.nanoTime() - drawStartTime) / 1_000_000.0
        averageRenderTime = (averageRenderTime * (frameCount - 1) + drawDuration) / frameCount

        if (drawDuration > 16.67) { // More than 60fps threshold
            Timber.w("Slow rendering: ${drawDuration}ms (frame $frameCount)")
        }
    }

    private fun drawFullPose(canvas: Canvas, landmarks: PoseLandmarkResult, mapper: CoordinateMapper) {
        drawSkeleton(canvas, landmarks, mapper)
        drawLandmarks(canvas, landmarks, mapper)
    }

    private fun drawPrivacyAwarePose(canvas: Canvas, landmarks: PoseLandmarkResult, mapper: CoordinateMapper) {
        // In privacy mode, show simplified skeleton without detailed landmarks
        drawSimplifiedSkeleton(canvas, landmarks, mapper)
        drawPrivacyNotice(canvas)
    }

    private fun drawSkeleton(
        canvas: Canvas,
        landmarks: PoseLandmarkResult,
        mapper: CoordinateMapper
    ) {
        val landmarkList = landmarks.landmarks
        val path = Path()

        SkeletonEdges.DEFAULT.forEach { (startIdx, endIdx) ->
            if (startIdx < landmarkList.size && endIdx < landmarkList.size) {
                val start = landmarkList[startIdx]
                val end = landmarkList[endIdx]

                if (isLandmarkVisible(start) && isLandmarkVisible(end)) {
                    val (startX, startY) = mapper.normalizedToPixel(start.x, start.y)
                    val (endX, endY) = mapper.normalizedToPixel(end.x, end.y)

                    val confidence = min(start.visibility, end.visibility)
                    val paint = selectSkeletonPaint(confidence)

                    // Apply thickness scaling
                    paint.strokeWidth = paint.strokeWidth * skeletonThickness

                    // Animate confidence if enabled
                    if (animateConfidence) {
                        paint.alpha = (confidence * 255).toInt()
                    }

                    canvas.drawLine(startX, startY, endX, endY, paint)

                    // Reset paint properties
                    paint.strokeWidth = paint.strokeWidth / skeletonThickness
                    if (animateConfidence) {
                        paint.alpha = 255
                    }
                }
            }
        }
    }

    private fun drawSimplifiedSkeleton(
        canvas: Canvas,
        landmarks: PoseLandmarkResult,
        mapper: CoordinateMapper
    ) {
        // Draw only major body structure in privacy mode
        val majorConnections = listOf(
            11 to 12, // Shoulders
            11 to 23, // Left shoulder to hip
            12 to 24, // Right shoulder to hip
            23 to 24, // Hips
            23 to 25, // Left hip to knee
            24 to 26, // Right hip to knee
            25 to 27, // Left knee to ankle
            26 to 28  // Right knee to ankle
        )

        val landmarkList = landmarks.landmarks
        val paint = confidenceThresholdPaint.apply {
            alpha = 120
            strokeWidth = 4f
        }

        majorConnections.forEach { (startIdx, endIdx) ->
            if (startIdx < landmarkList.size && endIdx < landmarkList.size) {
                val start = landmarkList[startIdx]
                val end = landmarkList[endIdx]

                if (isLandmarkVisible(start) && isLandmarkVisible(end)) {
                    val (startX, startY) = mapper.normalizedToPixel(start.x, start.y)
                    val (endX, endY) = mapper.normalizedToPixel(end.x, end.y)
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }
        }
    }

    private fun selectSkeletonPaint(confidence: Float): Paint {
        return when {
            confidence > 0.8f -> skeletonPaint
            confidence > 0.6f -> confidenceThresholdPaint
            else -> lowConfidencePaint
        }
    }

    private fun isLandmarkVisible(landmark: PoseLandmarkResult.Landmark): Boolean {
        return landmark.visibility >= minVisibilityThreshold &&
                landmark.presence >= minPresenceThreshold
    }

    private fun drawLandmarks(
        canvas: Canvas,
        landmarks: PoseLandmarkResult,
        mapper: CoordinateMapper
    ) {
        landmarks.landmarks.forEachIndexed { index, landmark ->
            if (isLandmarkVisible(landmark)) {
                val (x, y) = mapper.normalizedToPixel(landmark.x, landmark.y)

                val baseRadius = when {
                    landmark.visibility > 0.8f -> 12f
                    landmark.visibility > 0.6f -> 10f
                    else -> 8f
                }

                val radius = baseRadius * landmarkScale

                // Draw outer circle with confidence-based alpha
                landmarkPaint.alpha = (landmark.visibility * 255).toInt()
                canvas.drawCircle(x, y, radius, landmarkPaint)

                // Draw inner circle for better visibility
                val innerPaint = landmarkPaint.apply {
                    alpha = 255
                    color = Color.BLACK
                }
                canvas.drawCircle(x, y, radius * 0.3f, innerPaint)

                // Draw landmark index in debug mode
                if (showDebugInfo) {
                    drawLandmarkIndex(canvas, x, y, index, radius)
                }

                // Reset paint
                landmarkPaint.color = Color.WHITE
            }
        }
    }

    private fun drawLandmarkIndex(canvas: Canvas, x: Float, y: Float, index: Int, radius: Float) {
        val textPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 20f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(index.toString(), x, y - radius - 10f, textPaint)
    }

    private fun drawPerformanceInfo(canvas: Canvas, landmarks: PoseLandmarkResult) {
        val inferenceMs = landmarks.inferenceTimeMs
        val detectionFps = if (inferenceMs > 0) (1000 / inferenceMs).toInt() else 0
        val renderFps = if (averageRenderTime > 0) (1000 / averageRenderTime).toInt() else 0

        val color = when {
            inferenceMs < 20 -> Color.GREEN
            inferenceMs < 33 -> Color.YELLOW
            else -> Color.RED
        }

        performancePaint.color = color

        // Performance metrics
        val performanceText = "Detection: ${inferenceMs}ms (${detectionFps}fps) | Render: ${averageRenderTime.toInt()}ms (${renderFps}fps)"
        canvas.drawText(performanceText, 20f, 50f, performancePaint)

        // Quality metrics
        val visibleCount = landmarks.landmarks.count { isLandmarkVisible(it) }
        val avgConfidence = landmarks.landmarks.filter { isLandmarkVisible(it) }
            .map { it.visibility }.average().takeIf { !it.isNaN() } ?: 0.0

        val qualityText = "Visible: $visibleCount/33 | Avg Confidence: ${(avgConfidence * 100).toInt()}%"
        canvas.drawText(qualityText, 20f, 100f, performancePaint)

        // Coordinate mapper metrics
        val mapperMetrics = coordinateMapper?.getPerformanceMetrics()
        if (mapperMetrics != null) {
            val mapperText = "Transforms: ${mapperMetrics.transformationCount} | Avg Error: ${mapperMetrics.averageError.toInt()}px"
            canvas.drawText(mapperText, 20f, 150f, performancePaint)
        }

        // Multi-person info
        if (enableMultiPersonMode && multiPersonLandmarks.size > 1) {
            val multiPersonText = "Person ${selectedPersonIndex + 1}/${multiPersonLandmarks.size}"
            canvas.drawText(multiPersonText, 20f, 200f, performancePaint)
        }
    }

    private fun drawDebugInfo(canvas: Canvas, landmarks: PoseLandmarkResult, mapper: CoordinateMapper) {
        // Draw visible bounds
        val bounds = mapper.getVisibleRegion()
        val (topLeft) = mapper.normalizedToPixel(bounds.left, bounds.top)
        val (bottomRight) = mapper.normalizedToPixel(bounds.right, bounds.bottom)

        val debugRect = RectF(topLeft.first, topLeft.second, bottomRight.first, bottomRight.second)
        canvas.drawRect(debugRect, debugPaint)

        // Draw coordinate system indicators
        drawCoordinateGrid(canvas, mapper)
    }

    private fun drawCoordinateGrid(canvas: Canvas, mapper: CoordinateMapper) {
        val gridPaint = Paint().apply {
            color = Color.CYAN
            strokeWidth = 1f
            alpha = 100
        }

        // Draw grid lines every 0.1 normalized units
        for (i in 0..10) {
            val normalizedPos = i / 10f

            // Vertical lines
            val (topX, topY) = mapper.normalizedToPixel(normalizedPos, 0f)
            val (bottomX, bottomY) = mapper.normalizedToPixel(normalizedPos, 1f)
            canvas.drawLine(topX, topY, bottomX, bottomY, gridPaint)

            // Horizontal lines
            val (leftX, leftY) = mapper.normalizedToPixel(0f, normalizedPos)
            val (rightX, rightY) = mapper.normalizedToPixel(1f, normalizedPos)
            canvas.drawLine(leftX, leftY, rightX, rightY, gridPaint)
        }
    }

    private fun drawMultiPersonIndicators(canvas: Canvas) {
        val indicatorPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            isAntiAlias = true
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }

        multiPersonLandmarks.forEachIndexed { index, _ ->
            val isSelected = index == selectedPersonIndex
            indicatorPaint.color = if (isSelected) Color.YELLOW else Color.WHITE

            val x = width - 50f - (index * 30f)
            val y = 50f

            canvas.drawCircle(x, y, if (isSelected) 12f else 8f, indicatorPaint)

            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText((index + 1).toString(), x, y + 5f, textPaint)
        }
    }

    private fun drawPrivacyNotice(canvas: Canvas) {
        val noticePaint = Paint().apply {
            color = Color.YELLOW
            textSize = 24f
            isAntiAlias = true
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }

        val notice = "Privacy Mode: Limited pose data"
        canvas.drawText(notice, 20f, height - 50f, noticePaint)
    }

    fun clear() {
        currentLandmarks = null
        multiPersonLandmarks = emptyList()
        selectedPersonIndex = 0
        frameCount = 0
        averageRenderTime = 0.0
        invalidate()
    }

    /**
     * Get current performance statistics for monitoring and optimization.
     */
    fun getPerformanceStats(): PerformanceStats {
        return PerformanceStats(
            frameCount = frameCount,
            averageRenderTime = averageRenderTime,
            currentFps = if (averageRenderTime > 0) (1000 / averageRenderTime).toInt() else 0,
            visibleLandmarks = currentLandmarks?.landmarks?.count { isLandmarkVisible(it) } ?: 0,
            totalLandmarks = currentLandmarks?.landmarks?.size ?: 0,
            multiPersonCount = multiPersonLandmarks.size
        )
    }

    data class PerformanceStats(
        val frameCount: Long,
        val averageRenderTime: Double,
        val currentFps: Int,
        val visibleLandmarks: Int,
        val totalLandmarks: Int,
        val multiPersonCount: Int
    )
}