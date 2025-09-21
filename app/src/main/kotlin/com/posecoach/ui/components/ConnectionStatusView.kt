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
import com.posecoach.ui.activities.ConnectionStatus

/**
 * Connection status indicator for Gemini Live API
 * Shows connection state, signal strength, and retry options
 */
class ConnectionStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var statusText: TextView
    private lateinit var signalIndicator: SignalIndicatorView
    private var currentStatus = ConnectionStatus.DISCONNECTED
    private var onConnectionRetryListener: (() -> Unit)? = null

    init {
        setupView()
    }

    private fun setupView() {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL

        // Signal indicator
        signalIndicator = SignalIndicatorView(context).apply {
            layoutParams = LayoutParams(48, 24)
        }
        addView(signalIndicator)

        // Status text
        statusText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = 12
            }
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        addView(statusText)

        // Click listener for retry
        setOnClickListener {
            if (currentStatus == ConnectionStatus.ERROR) {
                onConnectionRetryListener?.invoke()
            }
        }

        updateStatus(ConnectionStatus.DISCONNECTED)
    }

    fun updateStatus(status: ConnectionStatus) {
        currentStatus = status

        when (status) {
            ConnectionStatus.DISCONNECTED -> {
                statusText.text = "Disconnected"
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_error))
                signalIndicator.setSignalStrength(0)
                signalIndicator.setAnimating(false)
                isClickable = false
            }
            ConnectionStatus.CONNECTING -> {
                statusText.text = "Connecting..."
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_warning))
                signalIndicator.setSignalStrength(1)
                signalIndicator.setAnimating(true)
                isClickable = false
            }
            ConnectionStatus.CONNECTED -> {
                statusText.text = "Connected"
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_success))
                signalIndicator.setSignalStrength(3)
                signalIndicator.setAnimating(false)
                isClickable = false
            }
            ConnectionStatus.RESUMING -> {
                statusText.text = "Resuming session..."
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_warning))
                signalIndicator.setSignalStrength(2)
                signalIndicator.setAnimating(true)
                isClickable = false
            }
            ConnectionStatus.ERROR -> {
                statusText.text = "Connection error (tap to retry)"
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_error))
                signalIndicator.setSignalStrength(0)
                signalIndicator.setAnimating(false)
                isClickable = true
            }
        }
    }

    fun setConnectionQuality(quality: Float) {
        // Update signal strength based on connection quality (0.0 to 1.0)
        val signalLevel = when {
            quality >= 0.8f -> 3
            quality >= 0.5f -> 2
            quality >= 0.2f -> 1
            else -> 0
        }
        signalIndicator.setSignalStrength(signalLevel)
    }

    fun setOnConnectionRetryListener(listener: () -> Unit) {
        onConnectionRetryListener = listener
    }

    /**
     * Custom view for signal strength indicator
     */
    private class SignalIndicatorView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var signalStrength = 0 // 0-3
        private var isAnimating = false
        private var animationProgress = 0f
        private var animator: ValueAnimator? = null

        private val activeColor = ContextCompat.getColor(context, R.color.signal_active)
        private val inactiveColor = ContextCompat.getColor(context, R.color.signal_inactive)
        private val animatingColor = ContextCompat.getColor(context, R.color.signal_animating)

        init {
            paint.style = Paint.Style.FILL
        }

        fun setSignalStrength(strength: Int) {
            signalStrength = strength.coerceIn(0, 3)
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

            val barWidth = width / 4f * 0.6f
            val barSpacing = width / 4f * 0.4f

            for (i in 0 until 4) {
                val x = i * (barWidth + barSpacing)
                val barHeight = height * (0.3f + i * 0.2f)
                val y = height - barHeight

                // Determine bar color
                paint.color = when {
                    isAnimating && i <= signalStrength -> {
                        val alpha = (128 + 127 * animationProgress).toInt()
                        Color.argb(alpha, Color.red(animatingColor), Color.green(animatingColor), Color.blue(animatingColor))
                    }
                    i < signalStrength -> activeColor
                    else -> inactiveColor
                }

                canvas.drawRoundRect(
                    x, y, x + barWidth, height.toFloat(),
                    barWidth / 4f, barWidth / 4f,
                    paint
                )
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator?.cancel()
        }
    }
}