package com.posecoach.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.posecoach.R
import kotlin.math.*

/**
 * Overlay view for real-time pose visualization and corrections
 * Features:
 * - Real-time pose landmark rendering
 * - Pose correction indicators
 * - Confidence visualization
 * - Joint angle measurements
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint objects
    private val posePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val correctionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val confidencePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Colors
    private val goodPoseColor = ContextCompat.getColor(context, R.color.pose_good)
    private val warningPoseColor = ContextCompat.getColor(context, R.color.pose_warning)
    private val errorPoseColor = ContextCompat.getColor(context, R.color.pose_error)
    private val jointColor = ContextCompat.getColor(context, R.color.pose_joint)
    private val correctionColor = ContextCompat.getColor(context, R.color.pose_correction)

    // Pose data
    private var currentPoses: List<PoseData> = emptyList()
    private var corrections: List<PoseCorrection> = emptyList()

    // Configuration
    private val jointRadius = 8f
    private val lineStrokeWidth = 4f
    private val correctionStrokeWidth = 6f
    private val textSize = 24f

    init {
        setupPaints()
    }

    private fun setupPaints() {
        posePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = lineStrokeWidth
            strokeCap = Paint.Cap.ROUND
        }

        jointPaint.apply {
            style = Paint.Style.FILL
            color = jointColor
        }

        correctionPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = correctionStrokeWidth
            color = correctionColor
            pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }

        confidencePaint.apply {
            style = Paint.Style.FILL
        }

        textPaint.apply {
            textSize = this@PoseOverlayView.textSize
            color = Color.WHITE
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
            textAlign = Paint.Align.CENTER
        }
    }

    fun updatePoses(poses: List<PoseData>) {
        currentPoses = poses
        invalidate()
    }

    fun updateCorrections(newCorrections: List<PoseCorrection>) {
        corrections = newCorrections
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw each detected pose
        for (pose in currentPoses) {
            drawPose(canvas, pose)
        }

        // Draw corrections
        for (correction in corrections) {
            drawCorrection(canvas, correction)
        }
    }

    private fun drawPose(canvas: Canvas, pose: PoseData) {
        val landmarks = pose.landmarks

        // Draw pose connections
        drawPoseConnections(canvas, landmarks, pose.overallConfidence)

        // Draw joints
        for (landmark in landmarks) {
            drawJoint(canvas, landmark)
        }

        // Draw pose quality indicator
        drawPoseQuality(canvas, pose)
    }

    private fun drawPoseConnections(canvas: Canvas, landmarks: List<PoseLandmark>, confidence: Float) {
        // Define pose connections (body skeleton)
        val connections = listOf(
            // Head and torso
            Pair(PoseJoint.NOSE, PoseJoint.LEFT_EYE),
            Pair(PoseJoint.NOSE, PoseJoint.RIGHT_EYE),
            Pair(PoseJoint.LEFT_EYE, PoseJoint.LEFT_EAR),
            Pair(PoseJoint.RIGHT_EYE, PoseJoint.RIGHT_EAR),
            Pair(PoseJoint.LEFT_SHOULDER, PoseJoint.RIGHT_SHOULDER),
            Pair(PoseJoint.LEFT_SHOULDER, PoseJoint.LEFT_HIP),
            Pair(PoseJoint.RIGHT_SHOULDER, PoseJoint.RIGHT_HIP),
            Pair(PoseJoint.LEFT_HIP, PoseJoint.RIGHT_HIP),

            // Arms
            Pair(PoseJoint.LEFT_SHOULDER, PoseJoint.LEFT_ELBOW),
            Pair(PoseJoint.LEFT_ELBOW, PoseJoint.LEFT_WRIST),
            Pair(PoseJoint.RIGHT_SHOULDER, PoseJoint.RIGHT_ELBOW),
            Pair(PoseJoint.RIGHT_ELBOW, PoseJoint.RIGHT_WRIST),

            // Legs
            Pair(PoseJoint.LEFT_HIP, PoseJoint.LEFT_KNEE),
            Pair(PoseJoint.LEFT_KNEE, PoseJoint.LEFT_ANKLE),
            Pair(PoseJoint.RIGHT_HIP, PoseJoint.RIGHT_KNEE),
            Pair(PoseJoint.RIGHT_KNEE, PoseJoint.RIGHT_ANKLE)
        )

        // Set line color based on confidence
        posePaint.color = when {
            confidence >= 0.8f -> goodPoseColor
            confidence >= 0.5f -> warningPoseColor
            else -> errorPoseColor
        }

        // Draw connections
        for (connection in connections) {
            val startLandmark = landmarks.find { it.joint == connection.first }
            val endLandmark = landmarks.find { it.joint == connection.second }

            if (startLandmark != null && endLandmark != null &&
                startLandmark.confidence > 0.3f && endLandmark.confidence > 0.3f) {

                canvas.drawLine(
                    startLandmark.x, startLandmark.y,
                    endLandmark.x, endLandmark.y,
                    posePaint
                )
            }
        }
    }

    private fun drawJoint(canvas: Canvas, landmark: PoseLandmark) {
        if (landmark.confidence > 0.3f) {
            // Joint color based on confidence
            val alpha = (landmark.confidence * 255).toInt()
            jointPaint.alpha = alpha

            canvas.drawCircle(landmark.x, landmark.y, jointRadius, jointPaint)

            // Draw confidence ring
            confidencePaint.color = Color.WHITE
            confidencePaint.alpha = alpha / 2
            canvas.drawCircle(
                landmark.x, landmark.y,
                jointRadius + jointRadius * landmark.confidence,
                confidencePaint
            )
        }
    }

    private fun drawPoseQuality(canvas: Canvas, pose: PoseData) {
        val x = width * 0.1f
        val y = height * 0.1f
        val qualityText = "Pose Quality: ${(pose.overallConfidence * 100).toInt()}%"

        textPaint.color = when {
            pose.overallConfidence >= 0.8f -> goodPoseColor
            pose.overallConfidence >= 0.5f -> warningPoseColor
            else -> errorPoseColor
        }

        canvas.drawText(qualityText, x, y, textPaint)
    }

    private fun drawCorrection(canvas: Canvas, correction: PoseCorrection) {
        when (correction.type) {
            CorrectionType.ANGLE_ADJUSTMENT -> drawAngleCorrection(canvas, correction)
            CorrectionType.POSITION_ADJUSTMENT -> drawPositionCorrection(canvas, correction)
            CorrectionType.ALIGNMENT -> drawAlignmentCorrection(canvas, correction)
        }
    }

    private fun drawAngleCorrection(canvas: Canvas, correction: PoseCorrection) {
        val startPoint = correction.startPoint
        val endPoint = correction.endPoint
        val targetPoint = correction.targetPoint

        if (startPoint != null && endPoint != null && targetPoint != null) {
            // Draw current angle
            posePaint.color = errorPoseColor
            canvas.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, posePaint)

            // Draw target angle with dashed line
            canvas.drawLine(startPoint.x, startPoint.y, targetPoint.x, targetPoint.y, correctionPaint)

            // Draw angle arc
            val angle = calculateAngle(startPoint, endPoint, targetPoint)
            drawAngleArc(canvas, startPoint, endPoint, targetPoint, angle)

            // Draw correction text
            val midX = (startPoint.x + targetPoint.x) / 2f
            val midY = (startPoint.y + targetPoint.y) / 2f
            canvas.drawText("${angle.toInt()}°", midX, midY, textPaint)
        }
    }

    private fun drawPositionCorrection(canvas: Canvas, correction: PoseCorrection) {
        val currentPoint = correction.startPoint
        val targetPoint = correction.targetPoint

        if (currentPoint != null && targetPoint != null) {
            // Draw arrow from current to target position
            drawArrow(canvas, currentPoint, targetPoint)

            // Draw correction message
            val midX = (currentPoint.x + targetPoint.x) / 2f
            val midY = (currentPoint.y + targetPoint.y) / 2f - 30f
            canvas.drawText(correction.message, midX, midY, textPaint)
        }
    }

    private fun drawAlignmentCorrection(canvas: Canvas, correction: PoseCorrection) {
        val points = correction.alignmentPoints
        if (points.size >= 2) {
            // Draw alignment line
            correctionPaint.pathEffect = DashPathEffect(floatArrayOf(15f, 5f), 0f)
            for (i in 0 until points.size - 1) {
                canvas.drawLine(
                    points[i].x, points[i].y,
                    points[i + 1].x, points[i + 1].y,
                    correctionPaint
                )
            }

            // Draw correction message
            val centerX = points.map { it.x }.average().toFloat()
            val centerY = points.map { it.y }.average().toFloat() - 40f
            canvas.drawText(correction.message, centerX, centerY, textPaint)
        }
    }

    private fun drawAngleArc(canvas: Canvas, center: PointF, point1: PointF, point2: PointF, angle: Float) {
        val radius = 30f
        val startAngle = atan2(point1.y - center.y, point1.x - center.x) * 180f / PI.toFloat()

        val rectF = RectF(
            center.x - radius, center.y - radius,
            center.x + radius, center.y + radius
        )

        correctionPaint.style = Paint.Style.STROKE
        canvas.drawArc(rectF, startAngle, angle, false, correctionPaint)
        correctionPaint.style = Paint.Style.FILL
    }

    private fun drawArrow(canvas: Canvas, start: PointF, end: PointF) {
        // Arrow shaft
        correctionPaint.style = Paint.Style.STROKE
        canvas.drawLine(start.x, start.y, end.x, end.y, correctionPaint)

        // Arrow head
        val arrowLength = 20f
        val arrowAngle = PI / 6 // 30 degrees

        val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())

        val arrowX1 = end.x - arrowLength * cos(angle - arrowAngle).toFloat()
        val arrowY1 = end.y - arrowLength * sin(angle - arrowAngle).toFloat()
        val arrowX2 = end.x - arrowLength * cos(angle + arrowAngle).toFloat()
        val arrowY2 = end.y - arrowLength * sin(angle + arrowAngle).toFloat()

        canvas.drawLine(end.x, end.y, arrowX1, arrowY1, correctionPaint)
        canvas.drawLine(end.x, end.y, arrowX2, arrowY2, correctionPaint)
    }

    private fun calculateAngle(center: PointF, point1: PointF, point2: PointF): Float {
        val angle1 = atan2(point1.y - center.y, point1.x - center.x)
        val angle2 = atan2(point2.y - center.y, point2.x - center.x)
        var diff = angle2 - angle1

        if (diff > PI) diff -= 2 * PI
        if (diff < -PI) diff += 2 * PI

        return abs(diff * 180f / PI.toFloat())
    }

    fun clearPoses() {
        currentPoses = emptyList()
        corrections = emptyList()
        invalidate()
    }
}

// Data classes for pose information
data class PoseData(
    val landmarks: List<PoseLandmark>,
    val overallConfidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class PoseLandmark(
    val joint: PoseJoint,
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val confidence: Float
)

enum class PoseJoint {
    NOSE, LEFT_EYE, RIGHT_EYE, LEFT_EAR, RIGHT_EAR,
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
}

data class PoseCorrection(
    val type: CorrectionType,
    val message: String,
    val startPoint: PointF? = null,
    val endPoint: PointF? = null,
    val targetPoint: PointF? = null,
    val alignmentPoints: List<PointF> = emptyList(),
    val severity: CorrectionSeverity = CorrectionSeverity.MEDIUM
)

enum class CorrectionType {
    ANGLE_ADJUSTMENT,
    POSITION_ADJUSTMENT,
    ALIGNMENT
}

enum class CorrectionSeverity {
    LOW, MEDIUM, HIGH
}