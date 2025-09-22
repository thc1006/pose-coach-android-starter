package com.posecoach.app.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.posecoach.corepose.models.PoseLandmarkResult
import timber.log.Timber

/**
 * Custom overlay view for rendering pose skeleton with proper rotation support
 * Following CLAUDE.md requirement: Use OverlayView (not camera surface drawing)
 * Implements coordinate transformation from landmarks to screen space with rotation compensation
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentPose: PoseLandmarkResult? = null

    // Coordinate transformation infrastructure
    private var coordinateMapper: EnhancedCoordinateMapper? = null

    // Camera and display configuration
    private var cameraImageWidth: Int = 0
    private var cameraImageHeight: Int = 0
    private var deviceRotation: Int = 0
    private var isFrontFacing: Boolean = true
    private var fitMode: FitMode = FitMode.CENTER_CROP

    // Paint objects for drawing - make them more visible
    private val landmarkPaint = Paint().apply {
        color = Color.RED  // Changed to red for better visibility
        style = Paint.Style.FILL
        strokeWidth = 12f  // Increased size
        isAntiAlias = true
    }

    private val connectionPaint = Paint().apply {
        color = Color.YELLOW  // Changed to yellow for better visibility
        style = Paint.Style.STROKE
        strokeWidth = 6f  // Increased thickness
        isAntiAlias = true
    }

    // Debug paint for drawing info
    private val debugPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * Configure camera and display parameters for proper coordinate transformation
     */
    fun configureCameraDisplay(
        cameraWidth: Int,
        cameraHeight: Int,
        rotation: Int,
        frontFacing: Boolean,
        aspectFitMode: FitMode = FitMode.CENTER_CROP
    ) {
        cameraImageWidth = cameraWidth
        cameraImageHeight = cameraHeight
        deviceRotation = rotation
        isFrontFacing = frontFacing
        fitMode = aspectFitMode

        Timber.d("Camera display configured: ${cameraWidth}x${cameraHeight}, rotation: $rotation, frontFacing: $frontFacing")
        updateCoordinateMapper()
    }

    /**
     * Update the coordinate mapper when view dimensions or camera parameters change
     */
    private fun updateCoordinateMapper() {
        if (width > 0 && height > 0 && cameraImageWidth > 0 && cameraImageHeight > 0) {
            coordinateMapper = EnhancedCoordinateMapper(
                viewWidth = width,
                viewHeight = height,
                imageWidth = cameraImageWidth,
                imageHeight = cameraImageHeight,
                isFrontFacing = isFrontFacing,
                rotation = deviceRotation
            ).apply {
                updateAspectRatio(fitMode)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCoordinateMapper()
    }

    /**
     * Update pose landmarks and trigger redraw
     */
    fun updatePose(pose: PoseLandmarkResult?) {
        currentPose = pose
        pose?.let {
            Timber.d("PoseOverlayView: Received pose with ${it.landmarks.size} landmarks")
        } ?: Timber.d("PoseOverlayView: Cleared pose")
        invalidate() // Trigger onDraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw debug info
        canvas.drawText("Overlay Active", 50f, 50f, debugPaint)

        currentPose?.let { pose ->
            Timber.i("Drawing pose skeleton with ${pose.landmarks.size} landmarks")
            drawPoseSkeleton(canvas, pose)

            // Draw landmark count
            canvas.drawText("Landmarks: ${pose.landmarks.size}", 50f, 100f, debugPaint)
        } ?: run {
            Timber.v("No pose to draw")
            canvas.drawText("No pose detected", 50f, 100f, debugPaint)
        }
    }

    private fun drawPoseSkeleton(canvas: Canvas, pose: PoseLandmarkResult) {
        val landmarks = pose.landmarks
        if (landmarks.isEmpty()) {
            Timber.w("No landmarks to draw")
            return
        }
        Timber.d("Drawing skeleton with ${landmarks.size} landmarks")

        // Batch transform all landmarks for better performance
        val transformedLandmarks = batchTransformLandmarksToScreen(landmarks)

        // Draw connections between landmarks first (skeleton structure)
        drawPoseConnections(canvas, landmarks, transformedLandmarks)

        // Draw landmarks on top with larger circles
        landmarks.forEachIndexed { index, landmark ->
            if (isLandmarkVisible(landmark) && index < transformedLandmarks.size) {
                val screenPoint = transformedLandmarks[index]
                val radius = 10f * landmark.visibility  // Increased base radius
                landmarkPaint.alpha = Math.max(128, (landmark.visibility * 255).toInt())  // Minimum alpha of 128

                canvas.drawCircle(screenPoint.x, screenPoint.y, radius, landmarkPaint)

                // Draw landmark index for debugging (only for first few)
                if (index < 5) {
                    canvas.drawText(index.toString(), screenPoint.x + 15, screenPoint.y, debugPaint)
                }
            }
        }
    }

    private fun drawPoseConnections(
        canvas: Canvas,
        landmarks: List<PoseLandmarkResult.Landmark>,
        transformedLandmarks: List<PointF>
    ) {
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
            if (startIdx < landmarks.size && endIdx < landmarks.size &&
                startIdx < transformedLandmarks.size && endIdx < transformedLandmarks.size) {

                val startLandmark = landmarks[startIdx]
                val endLandmark = landmarks[endIdx]

                if (isLandmarkVisible(startLandmark) && isLandmarkVisible(endLandmark)) {
                    val startPoint = transformedLandmarks[startIdx]
                    val endPoint = transformedLandmarks[endIdx]

                    val alpha = ((startLandmark.visibility + endLandmark.visibility) / 2 * 255).toInt()
                    connectionPaint.alpha = alpha

                    canvas.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, connectionPaint)
                }
            }
        }
    }

    /**
     * Transform normalized landmark coordinates to screen coordinates with proper rotation support
     * Following CLAUDE.md requirement: coordinate transformation from landmarks to screen
     */
    private fun transformLandmarkToScreen(landmark: PoseLandmarkResult.Landmark): PointF {
        val mapper = coordinateMapper
        return if (mapper != null) {
            // Use the enhanced coordinate mapper for proper transformation including rotation
            val (screenX, screenY) = mapper.normalizedToPixel(landmark.x, landmark.y)
            PointF(screenX, screenY)
        } else {
            // Fallback to simple transformation if mapper not initialized
            val screenX = landmark.x * width
            val screenY = landmark.y * height
            PointF(screenX, screenY)
        }
    }

    /**
     * Batch transform landmarks for better performance
     */
    private fun batchTransformLandmarksToScreen(landmarks: List<PoseLandmarkResult.Landmark>): List<PointF> {
        val mapper = coordinateMapper
        return if (mapper != null) {
            // Use batch transformation for optimal performance
            val normalizedPairs = landmarks.map { Pair(it.x, it.y) }
            val pixelPairs = mapper.batchNormalizedToPixel(normalizedPairs)
            pixelPairs.map { (x, y) -> PointF(x, y) }
        } else {
            // Fallback to individual transformations
            landmarks.map { transformLandmarkToScreen(it) }
        }
    }

    /**
     * Check if a landmark is visible in the current view
     */
    private fun isLandmarkVisible(landmark: PoseLandmarkResult.Landmark): Boolean {
        val mapper = coordinateMapper
        return if (mapper != null) {
            // Use the coordinate mapper's visibility check which accounts for cropping and rotation
            mapper.isPointVisible(landmark.x, landmark.y) && landmark.visibility > 0.5f
        } else {
            // Fallback visibility check
            landmark.visibility > 0.5f &&
                    landmark.x >= 0f && landmark.x <= 1f &&
                    landmark.y >= 0f && landmark.y <= 1f
        }
    }

    /**
     * Get current coordinate mapper for external use
     */
    fun getCoordinateMapper(): EnhancedCoordinateMapper? = coordinateMapper

    /**
     * Update only the rotation without changing other parameters
     */
    fun updateRotation(rotation: Int) {
        if (deviceRotation != rotation) {
            deviceRotation = rotation
            coordinateMapper?.updateRotation(rotation, isFrontFacing)
            invalidate()
        }
    }

    /**
     * Update camera facing direction
     */
    fun updateCameraFacing(frontFacing: Boolean) {
        if (isFrontFacing != frontFacing) {
            isFrontFacing = frontFacing
            coordinateMapper?.updateRotation(deviceRotation, frontFacing)
            invalidate()
        }
    }

    /**
     * Update aspect ratio fit mode
     */
    fun updateFitMode(newFitMode: FitMode) {
        if (fitMode != newFitMode) {
            fitMode = newFitMode
            coordinateMapper?.updateAspectRatio(newFitMode)
            invalidate()
        }
    }
}
