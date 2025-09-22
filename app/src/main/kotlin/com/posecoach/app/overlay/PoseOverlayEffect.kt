package com.posecoach.app.overlay

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.DynamicRange
// import androidx.camera.core.SurfaceProcessor.Companion.CORRECTION_NOT_SUPPORTED // TODO: Fix when available
import com.posecoach.corepose.SkeletonEdges
import com.posecoach.corepose.models.PoseLandmarkResult
import timber.log.Timber
import java.util.concurrent.Executor
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.Q)
class PoseOverlayEffect(
    private val executor: Executor
) : SurfaceProcessor {

    private var currentLandmarks: PoseLandmarkResult? = null
    private var outputSurface: android.view.Surface? = null
    private var isActive = false

    private val landmarkPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val skeletonPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private var minVisibilityThreshold = 0.5f
    private var minPresenceThreshold = 0.5f

    fun updatePose(landmarks: PoseLandmarkResult) {
        currentLandmarks = landmarks
        renderOverlay()
    }

    fun setVisibilityThreshold(threshold: Float) {
        minVisibilityThreshold = threshold.coerceIn(0f, 1f)
    }

    fun setPresenceThreshold(threshold: Float) {
        minPresenceThreshold = threshold.coerceIn(0f, 1f)
    }

    override fun onInputSurface(request: SurfaceRequest) {
        Timber.d("onInputSurface: ${request.resolution}")

        request.provideSurface(
            outputSurface!!,
            executor
        ) { result ->
            Timber.d("Surface provided with result: $result")
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        Timber.d("onOutputSurface: ${surfaceOutput.size}")

        outputSurface = surfaceOutput.getSurface(executor) { event ->
            Timber.d("Surface event: $event")
            when (event) {
                // TODO: Fix when SurfaceOutput.Event is available
                // SurfaceOutput.Event.EVENT_REQUEST_CLOSE -> {
                else -> {
                    isActive = false
                    surfaceOutput.close()
                }
            }
        }

        isActive = true
        // TODO: Fix when updateTransformMatrix signature is clarified
        // surfaceOutput.updateTransformMatrix(Matrix().apply { setScale(1f, 1f) }, 0f)
    }

    private fun renderOverlay() {
        val surface = outputSurface ?: return
        val landmarks = currentLandmarks ?: return

        if (!isActive) return

        try {
            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // TODO: Fix when lockHardwareCanvas is available
                surface.lockCanvas(null)
            } else {
                surface.lockCanvas(null)
            }

            canvas?.let { canvasInstance ->
                drawOverlay(canvasInstance, landmarks)
                surface.unlockCanvasAndPost(canvasInstance)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error rendering overlay")
        }
    }

    private fun drawOverlay(canvas: Canvas, landmarks: PoseLandmarkResult) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()

        drawSkeleton(canvas, landmarks, width, height)
        drawLandmarks(canvas, landmarks, width, height)
        drawPerformanceInfo(canvas, landmarks)
    }

    private fun drawSkeleton(
        canvas: Canvas,
        landmarks: PoseLandmarkResult,
        width: Float,
        height: Float
    ) {
        val landmarkList = landmarks.landmarks

        SkeletonEdges.DEFAULT.forEach { (startIdx, endIdx) ->
            if (startIdx < landmarkList.size && endIdx < landmarkList.size) {
                val start = landmarkList[startIdx]
                val end = landmarkList[endIdx]

                if (isLandmarkVisible(start) && isLandmarkVisible(end)) {
                    val startX = start.x * width
                    val startY = start.y * height
                    val endX = end.x * width
                    val endY = end.y * height

                    canvas.drawLine(startX, startY, endX, endY, skeletonPaint)
                }
            }
        }
    }

    private fun drawLandmarks(
        canvas: Canvas,
        landmarks: PoseLandmarkResult,
        width: Float,
        height: Float
    ) {
        landmarks.landmarks.forEach { landmark ->
            if (isLandmarkVisible(landmark)) {
                val x = landmark.x * width
                val y = landmark.y * height

                val radius = when {
                    landmark.visibility > 0.8f -> 12f
                    landmark.visibility > 0.6f -> 10f
                    else -> 8f
                }

                landmarkPaint.alpha = (landmark.visibility * 255).toInt()
                canvas.drawCircle(x, y, radius, landmarkPaint)
            }
        }
    }

    private fun drawPerformanceInfo(canvas: Canvas, landmarks: PoseLandmarkResult) {
        val paint = Paint().apply {
            color = when {
                landmarks.inferenceTimeMs < 20 -> Color.GREEN
                landmarks.inferenceTimeMs < 33 -> Color.YELLOW
                else -> Color.RED
            }
            textSize = 36f
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val text = "Inference: ${landmarks.inferenceTimeMs}ms"
        canvas.drawText(text, 20f, 50f, paint)
    }

    private fun isLandmarkVisible(landmark: PoseLandmarkResult.Landmark): Boolean {
        return landmark.visibility >= minVisibilityThreshold &&
                landmark.presence >= minPresenceThreshold
    }

    fun clear() {
        currentLandmarks = null
    }
}