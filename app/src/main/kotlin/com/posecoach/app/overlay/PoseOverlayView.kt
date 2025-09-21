package com.posecoach.app.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.posecoach.corepose.models.PoseLandmarkResult

/**
 * Custom overlay view for rendering pose skeleton
 * Following CLAUDE.md requirement: Use OverlayView (not camera surface drawing)
 * Implements coordinate transformation from landmarks to screen space
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentPose: PoseLandmarkResult? = null

    // Paint objects for drawing
    private val landmarkPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val connectionPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    /**
     * Update pose landmarks and trigger redraw
     */
    fun updatePose(pose: PoseLandmarkResult?) {
        currentPose = pose
        invalidate() // Trigger onDraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        currentPose?.let { pose ->
            drawPoseSkeleton(canvas, pose)
        }
    }

    private fun drawPoseSkeleton(canvas: Canvas, pose: PoseLandmarkResult) {
        val landmarks = pose.landmarks
        if (landmarks.isEmpty()) return

        // Draw connections between landmarks first (skeleton structure)
        drawPoseConnections(canvas, landmarks)

        // Draw landmarks on top
        landmarks.forEachIndexed { index, landmark ->
            if (landmark.visibility > 0.5f) {
                val screenPoint = transformLandmarkToScreen(landmark)
                val radius = 6f * landmark.visibility
                landmarkPaint.alpha = (landmark.visibility * 255).toInt()

                canvas.drawCircle(screenPoint.x, screenPoint.y, radius, landmarkPaint)
            }
        }
    }

    private fun drawPoseConnections(canvas: Canvas, landmarks: List<PoseLandmarkResult.Landmark>) {
        // MediaPipe pose connections following the 33-point model
        val connections = listOf(
            // Face
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7),
            Pair(0, 4), Pair(4, 5), Pair(5, 6), Pair(6, 8),
            // Torso
            Pair(9, 10),
            // Arms
            Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(15, 17), Pair(15, 19), Pair(15, 21),
            Pair(12, 14), Pair(14, 16), Pair(16, 18), Pair(16, 20), Pair(16, 22),
            // Body
            Pair(11, 23), Pair(12, 24), Pair(23, 24),
            // Legs
            Pair(23, 25), Pair(25, 27), Pair(27, 29), Pair(27, 31),
            Pair(24, 26), Pair(26, 28), Pair(28, 30), Pair(28, 32)
        )

        connections.forEach { (startIdx, endIdx) ->
            if (startIdx < landmarks.size && endIdx < landmarks.size) {
                val startLandmark = landmarks[startIdx]
                val endLandmark = landmarks[endIdx]

                if (startLandmark.visibility > 0.5f && endLandmark.visibility > 0.5f) {
                    val startPoint = transformLandmarkToScreen(startLandmark)
                    val endPoint = transformLandmarkToScreen(endLandmark)

                    val alpha = ((startLandmark.visibility + endLandmark.visibility) / 2 * 255).toInt()
                    connectionPaint.alpha = alpha

                    canvas.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, connectionPaint)
                }
            }
        }
    }

    /**
     * Transform normalized landmark coordinates to screen coordinates
     * Following CLAUDE.md requirement: coordinate transformation from landmarks to screen
     */
    private fun transformLandmarkToScreen(landmark: PoseLandmarkResult.Landmark): PointF {
        val screenX = landmark.x * width
        val screenY = landmark.y * height
        return PointF(screenX, screenY)
    }
}
