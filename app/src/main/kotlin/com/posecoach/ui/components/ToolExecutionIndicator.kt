package com.posecoach.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.posecoach.R
import kotlin.math.*

/**
 * Indicator for active tool execution by Gemini Live API
 * Features:
 * - Shows when pose analysis tools are running
 * - Progress indication for long-running tools
 * - Tool type identification
 * - Execution status and results
 */
class ToolExecutionIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var statusText: TextView
    private lateinit var progressView: ToolProgressView
    private var currentExecution: ToolExecution? = null

    // Animation
    private var pulseAnimator: ValueAnimator? = null

    init {
        setupView()
    }

    private fun setupView() {
        // Configure container
        background = ContextCompat.getDrawable(context, R.drawable.tool_indicator_background)
        elevation = 4f
        setPadding(12, 8, 12, 8)

        // Progress view
        progressView = ToolProgressView(context).apply {
            layoutParams = LayoutParams(24, 24).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            }
        }
        addView(progressView)

        // Status text
        statusText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
                marginStart = 32
            }
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        addView(statusText)

        // Initially hidden
        visibility = View.GONE
        alpha = 0f
    }

    fun show(execution: ToolExecution) {
        currentExecution = execution
        updateDisplay()

        if (visibility == View.GONE) {
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }

        startPulseAnimation()
    }

    fun hide() {
        stopPulseAnimation()

        animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                visibility = View.GONE
                currentExecution = null
            }
            .start()
    }

    fun updateProgress(progress: Float) {
        currentExecution?.let {
            currentExecution = it.copy(progress = progress)
            updateDisplay()
        }
    }

    fun updateStatus(status: ToolExecutionStatus) {
        currentExecution?.let {
            currentExecution = it.copy(status = status)
            updateDisplay()

            if (status == ToolExecutionStatus.COMPLETED || status == ToolExecutionStatus.FAILED) {
                // Auto-hide after completion
                postDelayed({ hide() }, 2000)
            }
        }
    }

    private fun updateDisplay() {
        val execution = currentExecution ?: return

        // Update status text
        statusText.text = when (execution.status) {
            ToolExecutionStatus.STARTING -> "Starting ${execution.toolName}..."
            ToolExecutionStatus.RUNNING -> "Analyzing pose..."
            ToolExecutionStatus.COMPLETED -> "Analysis complete"
            ToolExecutionStatus.FAILED -> "Analysis failed"
        }

        // Update text color based on status
        val textColor = when (execution.status) {
            ToolExecutionStatus.STARTING -> ContextCompat.getColor(context, R.color.tool_starting)
            ToolExecutionStatus.RUNNING -> ContextCompat.getColor(context, R.color.tool_running)
            ToolExecutionStatus.COMPLETED -> ContextCompat.getColor(context, R.color.tool_completed)
            ToolExecutionStatus.FAILED -> ContextCompat.getColor(context, R.color.tool_failed)
        }
        statusText.setTextColor(textColor)

        // Update progress view
        progressView.updateExecution(execution)
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()

        pulseAnimator = ValueAnimator.ofFloat(0.8f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        scaleX = 1f
        scaleY = 1f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulseAnimation()
    }

    /**
     * Custom view for tool execution progress
     */
    private class ToolProgressView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var execution: ToolExecution? = null
        private var rotationAngle = 0f
        private var rotationAnimator: ValueAnimator? = null

        init {
            setupPaints()
        }

        private fun setupPaints() {
            backgroundPaint.apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.LTGRAY
            }

            progressPaint.apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
            }

            iconPaint.apply {
                style = Paint.Style.FILL
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }
        }

        fun updateExecution(newExecution: ToolExecution) {
            execution = newExecution

            // Update progress paint color
            progressPaint.color = when (newExecution.status) {
                ToolExecutionStatus.STARTING -> ContextCompat.getColor(context, R.color.tool_starting)
                ToolExecutionStatus.RUNNING -> ContextCompat.getColor(context, R.color.tool_running)
                ToolExecutionStatus.COMPLETED -> ContextCompat.getColor(context, R.color.tool_completed)
                ToolExecutionStatus.FAILED -> ContextCompat.getColor(context, R.color.tool_failed)
            }

            // Start/stop rotation animation
            if (newExecution.status == ToolExecutionStatus.RUNNING) {
                startRotationAnimation()
            } else {
                stopRotationAnimation()
            }

            invalidate()
        }

        private fun startRotationAnimation() {
            rotationAnimator?.cancel()

            rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    rotationAngle = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun stopRotationAnimation() {
            rotationAnimator?.cancel()
            rotationAngle = 0f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val execution = this.execution ?: return

            val centerX = width / 2f
            val centerY = height / 2f
            val radius = (minOf(width, height) - 6f) / 2f

            // Draw background circle
            canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

            // Draw progress
            when (execution.status) {
                ToolExecutionStatus.STARTING, ToolExecutionStatus.RUNNING -> {
                    // Spinning progress for indeterminate state
                    canvas.save()
                    canvas.rotate(rotationAngle, centerX, centerY)
                    canvas.drawArc(
                        centerX - radius, centerY - radius,
                        centerX + radius, centerY + radius,
                        0f, 120f, false, progressPaint
                    )
                    canvas.restore()
                }
                ToolExecutionStatus.COMPLETED -> {
                    // Complete circle
                    canvas.drawCircle(centerX, centerY, radius, progressPaint)
                    // Checkmark
                    drawCheckmark(canvas, centerX, centerY, radius * 0.6f)
                }
                ToolExecutionStatus.FAILED -> {
                    // X mark
                    drawXMark(canvas, centerX, centerY, radius * 0.6f)
                }
            }

            // Draw tool icon
            if (execution.status != ToolExecutionStatus.COMPLETED && execution.status != ToolExecutionStatus.FAILED) {
                drawToolIcon(canvas, centerX, centerY, radius * 0.5f)
            }
        }

        private fun drawCheckmark(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
            iconPaint.strokeWidth = 3f
            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeCap = Paint.Cap.ROUND

            val path = Path().apply {
                moveTo(centerX - size * 0.3f, centerY)
                lineTo(centerX - size * 0.1f, centerY + size * 0.2f)
                lineTo(centerX + size * 0.3f, centerY - size * 0.2f)
            }

            canvas.drawPath(path, iconPaint)
            iconPaint.style = Paint.Style.FILL
        }

        private fun drawXMark(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
            iconPaint.strokeWidth = 3f
            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeCap = Paint.Cap.ROUND

            canvas.drawLine(
                centerX - size * 0.3f, centerY - size * 0.3f,
                centerX + size * 0.3f, centerY + size * 0.3f,
                iconPaint
            )
            canvas.drawLine(
                centerX + size * 0.3f, centerY - size * 0.3f,
                centerX - size * 0.3f, centerY + size * 0.3f,
                iconPaint
            )

            iconPaint.style = Paint.Style.FILL
        }

        private fun drawToolIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
            // Simple gear icon representing tool execution
            val path = Path()
            val numTeeth = 8
            val innerRadius = size * 0.6f
            val outerRadius = size

            for (i in 0 until numTeeth * 2) {
                val angle = i * PI / numTeeth
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                val x = centerX + radius * cos(angle).toFloat()
                val y = centerY + radius * sin(angle).toFloat()

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            canvas.drawPath(path, iconPaint)

            // Center hole
            canvas.drawCircle(centerX, centerY, size * 0.3f, backgroundPaint)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopRotationAnimation()
        }
    }
}

/**
 * Data class representing tool execution state
 */
data class ToolExecution(
    val toolName: String,
    val status: ToolExecutionStatus,
    val progress: Float = 0f, // 0.0 to 1.0
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val result: Any? = null,
    val error: String? = null
) {
    val isActive: Boolean
        get() = status == ToolExecutionStatus.STARTING || status == ToolExecutionStatus.RUNNING

    val duration: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime
}

/**
 * Tool execution status enumeration
 */
enum class ToolExecutionStatus {
    STARTING,   // Tool is being initialized
    RUNNING,    // Tool is actively processing
    COMPLETED,  // Tool finished successfully
    FAILED      // Tool execution failed
}