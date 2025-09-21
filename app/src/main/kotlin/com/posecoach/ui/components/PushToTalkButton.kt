package com.posecoach.ui.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.posecoach.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom push-to-talk button with visual feedback for Gemini Live API
 * Features:
 * - Touch and hold to talk
 * - Visual feedback with ripple effect
 * - Session state indication
 * - Haptic feedback
 * - Accessibility support
 */
class PushToTalkButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint objects
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Colors
    private val defaultColor = ContextCompat.getColor(context, R.color.button_default)
    private val activeColor = ContextCompat.getColor(context, R.color.button_active)
    private val disabledColor = ContextCompat.getColor(context, R.color.button_disabled)
    private val rippleColor = ContextCompat.getColor(context, R.color.ripple_color)
    private val borderColor = ContextCompat.getColor(context, R.color.border_color)

    // State
    private var isPressed = false
    private var isEnabled = true
    private var isSessionActive = false

    // Animation properties
    private var rippleRadius = 0f
    private var pressedScale = 1f
    private var currentColor = defaultColor

    // Ripple animation
    private var rippleAnimator: ValueAnimator? = null
    private var scaleAnimator: AnimatorSet? = null

    // Callbacks
    private var onTalkStartListener: (() -> Unit)? = null
    private var onTalkEndListener: (() -> Unit)? = null
    private var onLongPressListener: (() -> Unit)? = null

    // Long press detection
    private val longPressRunnable = Runnable {
        onLongPressListener?.invoke()
    }

    init {
        setupPaints()
        setupClickListeners()
    }

    private fun setupPaints() {
        backgroundPaint.style = Paint.Style.FILL

        ripplePaint.apply {
            style = Paint.Style.FILL
            color = rippleColor
            alpha = 100
        }

        iconPaint.apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        borderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = borderColor
        }
    }

    private fun setupClickListeners() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isEnabled) {
                        handleTouchDown()
                        // Schedule long press detection
                        postDelayed(longPressRunnable, 500)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isEnabled && isPressed) {
                        handleTouchUp()
                    }
                    // Cancel long press detection
                    removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTouchDown() {
        isPressed = true
        performHapticFeedback(HAPTIC_FEEDBACK_VIRTUAL_KEY)

        // Start talking
        onTalkStartListener?.invoke()

        // Start animations
        startPressAnimation()
        startRippleAnimation()

        invalidate()
    }

    private fun handleTouchUp() {
        isPressed = false

        // Stop talking
        onTalkEndListener?.invoke()

        // Stop animations
        stopRippleAnimation()
        startReleaseAnimation()

        invalidate()
    }

    private fun startPressAnimation() {
        scaleAnimator?.cancel()

        val scaleDown = ObjectAnimator.ofFloat(this, "pressedScale", 1f, 0.95f)
        val colorChange = ObjectAnimator.ofArgb(this, "currentColor", defaultColor, activeColor)

        scaleAnimator = AnimatorSet().apply {
            playTogether(scaleDown, colorChange)
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startReleaseAnimation() {
        scaleAnimator?.cancel()

        val scaleUp = ObjectAnimator.ofFloat(this, "pressedScale", pressedScale, 1f)
        val colorChange = ObjectAnimator.ofArgb(this, "currentColor", currentColor, defaultColor)

        scaleAnimator = AnimatorSet().apply {
            playTogether(scaleUp, colorChange)
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startRippleAnimation() {
        rippleAnimator?.cancel()

        rippleAnimator = ValueAnimator.ofFloat(0f, width / 2f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                rippleRadius = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopRippleAnimation() {
        rippleAnimator?.cancel()
        rippleRadius = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 2f) * pressedScale

        // Draw ripple effect (when pressed)
        if (isPressed && rippleRadius > 0) {
            ripplePaint.alpha = ((1f - rippleRadius / (width / 2f)) * 100).toInt()
            canvas.drawCircle(centerX, centerY, rippleRadius, ripplePaint)
        }

        // Draw main button background
        backgroundPaint.color = when {
            !isEnabled -> disabledColor
            else -> currentColor
        }
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // Draw border
        if (isSessionActive) {
            borderPaint.color = activeColor
            canvas.drawCircle(centerX, centerY, radius, borderPaint)
        }

        // Draw microphone icon
        drawMicrophoneIcon(canvas, centerX, centerY, radius * 0.4f)
    }

    private fun drawMicrophoneIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        val iconSize = size * 0.8f

        // Microphone body (rounded rectangle)
        val micRect = RectF(
            centerX - iconSize * 0.3f,
            centerY - iconSize * 0.6f,
            centerX + iconSize * 0.3f,
            centerY + iconSize * 0.1f
        )
        canvas.drawRoundRect(micRect, iconSize * 0.2f, iconSize * 0.2f, iconPaint)

        // Microphone stand
        val standPath = Path().apply {
            moveTo(centerX, centerY + iconSize * 0.1f)
            lineTo(centerX, centerY + iconSize * 0.4f)

            // Base of stand
            moveTo(centerX - iconSize * 0.3f, centerY + iconSize * 0.4f)
            lineTo(centerX + iconSize * 0.3f, centerY + iconSize * 0.4f)
        }

        iconPaint.strokeWidth = iconSize * 0.1f
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(standPath, iconPaint)

        // Reset paint style
        iconPaint.style = Paint.Style.FILL
    }

    // Property setters for animations
    fun setPressedScale(scale: Float) {
        pressedScale = scale
        invalidate()
    }

    fun setCurrentColor(color: Int) {
        currentColor = color
        invalidate()
    }

    // Public API
    fun setOnTalkStartListener(listener: () -> Unit) {
        onTalkStartListener = listener
    }

    fun setOnTalkEndListener(listener: () -> Unit) {
        onTalkEndListener = listener
    }

    fun setOnLongPressListener(listener: () -> Unit) {
        onLongPressListener = listener
    }

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        invalidate()
    }

    fun setSessionActive(active: Boolean) {
        isSessionActive = active
        invalidate()
    }

    fun showConnecting() {
        // Start a pulsing animation to indicate connecting state
        val pulseAnimator = ObjectAnimator.ofFloat(this, "alpha", 1f, 0.5f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    fun hideConnecting() {
        clearAnimation()
        alpha = 1f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rippleAnimator?.cancel()
        scaleAnimator?.cancel()
        removeCallbacks(longPressRunnable)
    }
}