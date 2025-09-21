package com.posecoach.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.posecoach.R
import kotlin.math.*

/**
 * Session timer with 15-minute limit and resumption indication
 * Features:
 * - Visual countdown timer
 * - Session state indication
 * - Automatic resumption countdown
 * - Warning indicators for session limits
 */
class SessionTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var timerText: TextView
    private lateinit var progressIndicator: CircularProgressView
    private var sessionState = SessionState.INACTIVE
    private var onSessionLimitListener: (() -> Unit)? = null

    // Timer values
    private val maxSessionDuration = 15 * 60 * 1000L // 15 minutes in milliseconds
    private val resumptionInterval = 10 * 60 * 1000L // 10 minutes in milliseconds
    private var sessionStartTime = 0L
    private var elapsedTime = 0L
    private var remainingTime = maxSessionDuration

    // Animation
    private var updateTimer: ValueAnimator? = null

    init {
        setupView()
        startUpdateTimer()
    }

    private fun setupView() {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL

        // Circular progress indicator
        progressIndicator = CircularProgressView(context).apply {
            layoutParams = LayoutParams(40, 40)
        }
        addView(progressIndicator)

        // Timer text
        timerText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = 8
            }
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        addView(timerText)

        updateDisplay()
    }

    private fun startUpdateTimer() {
        updateTimer = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (sessionState == SessionState.ACTIVE) {
                    updateElapsedTime()
                    updateDisplay()
                    checkSessionLimits()
                }
            }
            start()
        }
    }

    fun updateSessionState(state: SessionState) {
        sessionState = state

        when (state) {
            SessionState.INACTIVE -> {
                sessionStartTime = 0L
                elapsedTime = 0L
                remainingTime = maxSessionDuration
            }
            SessionState.ACTIVE -> {
                if (sessionStartTime == 0L) {
                    sessionStartTime = System.currentTimeMillis()
                }
            }
            SessionState.RESUMING -> {
                // Keep current elapsed time
            }
            SessionState.EXPIRED -> {
                onSessionLimitListener?.invoke()
            }
        }

        updateDisplay()
    }

    private fun updateElapsedTime() {
        if (sessionStartTime > 0) {
            elapsedTime = System.currentTimeMillis() - sessionStartTime
            remainingTime = maxSessionDuration - elapsedTime
        }
    }

    private fun updateDisplay() {
        when (sessionState) {
            SessionState.INACTIVE -> {
                timerText.text = "Ready"
                timerText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                progressIndicator.setProgress(0f)
                progressIndicator.setColor(ContextCompat.getColor(context, R.color.timer_inactive))
            }
            SessionState.ACTIVE -> {
                val minutes = (remainingTime / 60000).toInt()
                val seconds = ((remainingTime % 60000) / 1000).toInt()
                timerText.text = String.format("%02d:%02d", minutes, seconds)

                val progress = elapsedTime.toFloat() / maxSessionDuration.toFloat()
                progressIndicator.setProgress(progress)

                // Color based on remaining time
                val color = when {
                    remainingTime < 60000 -> ContextCompat.getColor(context, R.color.timer_critical) // < 1 min
                    remainingTime < 300000 -> ContextCompat.getColor(context, R.color.timer_warning) // < 5 min
                    else -> ContextCompat.getColor(context, R.color.timer_active)
                }
                timerText.setTextColor(color)
                progressIndicator.setColor(color)
            }
            SessionState.RESUMING -> {
                timerText.text = "Resuming..."
                timerText.setTextColor(ContextCompat.getColor(context, R.color.timer_resuming))
                progressIndicator.setProgress(0.5f)
                progressIndicator.setColor(ContextCompat.getColor(context, R.color.timer_resuming))
                progressIndicator.setAnimating(true)
            }
            SessionState.EXPIRED -> {
                timerText.text = "Expired"
                timerText.setTextColor(ContextCompat.getColor(context, R.color.timer_expired))
                progressIndicator.setProgress(1f)
                progressIndicator.setColor(ContextCompat.getColor(context, R.color.timer_expired))
            }
        }
    }

    private fun checkSessionLimits() {
        if (remainingTime <= 0 && sessionState == SessionState.ACTIVE) {
            updateSessionState(SessionState.EXPIRED)
        }
    }

    fun setOnSessionLimitListener(listener: () -> Unit) {
        onSessionLimitListener = listener
    }

    fun getRemainingTime(): Long = remainingTime

    fun getElapsedTime(): Long = elapsedTime

    fun shouldResumeSession(): Boolean {
        return elapsedTime >= resumptionInterval
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        updateTimer?.cancel()
    }

    /**
     * Custom circular progress indicator
     */
    private class CircularProgressView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokeWidth = 4f

        private var progress = 0f
        private var color = Color.BLUE
        private var isAnimating = false
        private var animationProgress = 0f
        private var animator: ValueAnimator? = null

        init {
            setupPaints()
        }

        private fun setupPaints() {
            backgroundPaint.apply {
                style = Paint.Style.STROKE
                strokeWidth = this@CircularProgressView.strokeWidth
                color = Color.LTGRAY
                strokeCap = Paint.Cap.ROUND
            }

            progressPaint.apply {
                style = Paint.Style.STROKE
                strokeWidth = this@CircularProgressView.strokeWidth
                strokeCap = Paint.Cap.ROUND
            }
        }

        fun setProgress(newProgress: Float) {
            progress = newProgress.coerceIn(0f, 1f)
            invalidate()
        }

        fun setColor(newColor: Int) {
            color = newColor
            progressPaint.color = color
            invalidate()
        }

        fun setAnimating(animating: Boolean) {
            if (isAnimating == animating) return

            isAnimating = animating
            animator?.cancel()

            if (animating) {
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1000
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { animation ->
                        animationProgress = animation.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                animationProgress = 0f
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val centerX = width / 2f
            val centerY = height / 2f
            val radius = (minOf(width, height) - strokeWidth) / 2f

            // Draw background circle
            canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

            // Draw progress arc
            val sweepAngle = progress * 360f
            val startAngle = -90f // Start from top

            if (isAnimating) {
                progressPaint.alpha = (128 + 127 * animationProgress).toInt()
            } else {
                progressPaint.alpha = 255
            }

            if (sweepAngle > 0) {
                canvas.drawArc(
                    centerX - radius, centerY - radius,
                    centerX + radius, centerY + radius,
                    startAngle, sweepAngle, false, progressPaint
                )
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator?.cancel()
        }
    }
}

/**
 * Session state enumeration
 */
enum class SessionState {
    INACTIVE,   // No active session
    ACTIVE,     // Session is running
    RESUMING,   // Session is being resumed after interruption
    EXPIRED     // Session has reached 15-minute limit
}