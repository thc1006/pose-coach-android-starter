package com.posecoach.app.livecoach.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.posecoach.app.R
import com.posecoach.app.livecoach.models.ConnectionState
import kotlin.math.min

class PushToTalkButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnPushToTalkListener {
        fun onStartTalking()
        fun onStopTalking()
        fun onConnectionStateChanged(isConnected: Boolean)
        fun onRetryConnection()
        fun onShowConnectionInfo()
    }

    private var listener: OnPushToTalkListener? = null
    private var connectionState = ConnectionState.DISCONNECTED
    private var isPressed = false
    private var isRecording = false
    private var isSpeaking = false
    private var audioQuality = 1.0
    private var batteryOptimized = false
    private var showConnectionHealth = false
    private var lastTalkStartTime = 0L
    private var minTalkDuration = 500L // Minimum talk duration to prevent accidental taps

    // Paint objects
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
    }

    // Animation
    private var pulseAnimator: ValueAnimator? = null
    private var pulseScale = 1f

    // Touch handling
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    // Colors
    private val colorDisconnected = Color.parseColor("#757575") // Gray
    private val colorConnecting = Color.parseColor("#FF9800") // Orange
    private val colorConnected = Color.parseColor("#4CAF50") // Green
    private val colorRecording = Color.parseColor("#F44336") // Red
    private val colorError = Color.parseColor("#D32F2F") // Dark Red
    private val colorPressed = Color.parseColor("#1976D2") // Blue

    init {
        // Set minimum size
        minimumWidth = 200
        minimumHeight = 200

        // Enable click handling
        isClickable = true
        isFocusable = true

        updateAppearance()
    }

    fun setOnPushToTalkListener(listener: OnPushToTalkListener?) {
        this.listener = listener
    }

    fun updateConnectionState(state: ConnectionState) {
        if (connectionState != state) {
            connectionState = state
            updateAppearance()

            listener?.onConnectionStateChanged(state == ConnectionState.CONNECTED)
        }
    }

    fun setRecording(recording: Boolean) {
        if (isRecording != recording) {
            isRecording = recording
            updateAppearance()

            if (recording) {
                lastTalkStartTime = System.currentTimeMillis()
                startPulseAnimation()
            } else {
                stopPulseAnimation()
            }
        }
    }

    fun setSpeaking(speaking: Boolean) {
        if (isSpeaking != speaking) {
            isSpeaking = speaking
            updateAppearance()

            if (speaking) {
                startSpeakingAnimation()
            } else {
                stopSpeakingAnimation()
            }
        }
    }

    fun setAudioQuality(quality: Double) {
        audioQuality = quality.coerceIn(0.0, 1.0)
        updateAppearance()
    }

    fun setBatteryOptimized(optimized: Boolean) {
        if (batteryOptimized != optimized) {
            batteryOptimized = optimized
            updateAppearance()
        }
    }

    fun setShowConnectionHealth(show: Boolean) {
        showConnectionHealth = show
        updateAppearance()
    }

    private fun updateAppearance() {
        val backgroundColor = when {
            isPressed -> colorPressed
            isRecording -> colorRecording
            isSpeaking -> Color.parseColor("#9C27B0") // Purple for AI speaking
            connectionState == ConnectionState.CONNECTED -> {
                if (audioQuality < 0.3) Color.parseColor("#FF7043") // Orange for poor quality
                else colorConnected
            }
            connectionState == ConnectionState.CONNECTING ||
            connectionState == ConnectionState.RECONNECTING -> colorConnecting
            connectionState == ConnectionState.ERROR -> colorError
            else -> colorDisconnected
        }

        backgroundPaint.color = backgroundColor

        // Dynamic border color based on state
        borderPaint.color = when {
            batteryOptimized -> Color.parseColor("#FFC107") // Amber for battery mode
            audioQuality < 0.5 -> Color.parseColor("#FF5722") // Red-orange for poor quality
            else -> Color.WHITE
        }

        textPaint.color = Color.WHITE

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - borderPaint.strokeWidth

        // Apply pulse scale if recording
        val scale = if (isRecording) pulseScale else 1f
        val scaledRadius = radius * scale

        // Draw background circle
        canvas.drawCircle(centerX, centerY, scaledRadius, backgroundPaint)

        // Draw border
        canvas.drawCircle(centerX, centerY, scaledRadius, borderPaint)

        // Draw text based on state
        val text = when {
            isRecording -> "LISTENING"
            isSpeaking -> "AI SPEAKING"
            connectionState == ConnectionState.CONNECTED -> {
                if (batteryOptimized) "BATTERY MODE" else "PUSH TO TALK"
            }
            connectionState == ConnectionState.CONNECTING ||
            connectionState == ConnectionState.RECONNECTING -> "CONNECTING..."
            connectionState == ConnectionState.ERROR -> "TAP TO RETRY"
            else -> "DISCONNECTED"
        }

        // Calculate text size to fit
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val maxTextWidth = scaledRadius * 1.6f
        if (textBounds.width() > maxTextWidth) {
            textPaint.textSize = textPaint.textSize * maxTextWidth / textBounds.width()
        }

        canvas.drawText(text, centerX, centerY + textBounds.height() / 2f, textPaint)

        // Draw appropriate icon based on state
        when {
            isRecording -> drawMicrophoneIcon(canvas, centerX, centerY - 40f)
            isSpeaking -> drawSpeakerIcon(canvas, centerX, centerY - 40f)
            batteryOptimized -> drawBatteryIcon(canvas, centerX, centerY - 40f)
        }

        // Draw quality indicator if connection is active
        if (connectionState == ConnectionState.CONNECTED && showConnectionHealth) {
            drawQualityIndicator(canvas, centerX + scaledRadius - 30f, centerY - scaledRadius + 30f)
        }
    }

    private fun drawMicrophoneIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val micWidth = 20f
        val micHeight = 30f

        // Draw microphone body
        val micRect = RectF(
            centerX - micWidth / 2,
            centerY - micHeight / 2,
            centerX + micWidth / 2,
            centerY + micHeight / 2
        )
        canvas.drawRoundRect(micRect, 8f, 8f, iconPaint)

        // Draw microphone stand
        canvas.drawRect(
            centerX - 2f,
            centerY + micHeight / 2,
            centerX + 2f,
            centerY + micHeight / 2 + 15f,
            iconPaint
        )

        // Draw microphone base
        canvas.drawRect(
            centerX - 15f,
            centerY + micHeight / 2 + 15f,
            centerX + 15f,
            centerY + micHeight / 2 + 20f,
            iconPaint
        )
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()

        val duration = if (batteryOptimized) 1500L else 1000L
        val maxScale = if (batteryOptimized) 1.1f else 1.2f

        pulseAnimator = ValueAnimator.ofFloat(1f, maxScale, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                pulseScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private var speakingAnimator: ValueAnimator? = null
    private var speakingScale = 1f

    private fun startSpeakingAnimation() {
        stopSpeakingAnimation()

        speakingAnimator = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = 800L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                speakingScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopSpeakingAnimation() {
        speakingAnimator?.cancel()
        speakingAnimator = null
        speakingScale = 1f
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseScale = 1f
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (connectionState != ConnectionState.CONNECTED) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Handle different states
                when (connectionState) {
                    ConnectionState.CONNECTED -> {
                        downX = event.x
                        downY = event.y
                        isPressed = true
                        updateAppearance()

                        // Start talking immediately on press
                        listener?.onStartTalking()
                    }
                    ConnectionState.ERROR -> {
                        // Allow retry on error state
                        listener?.onRetryConnection()
                    }
                    else -> {
                        // Show connection info for other states
                        listener?.onShowConnectionInfo()
                    }
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - downX
                val deltaY = event.y - downY
                val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)

                // If moved too far, cancel the press
                if (distance > touchSlop && isPressed) {
                    isPressed = false
                    updateAppearance()
                    listener?.onStopTalking()
                }

                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPressed) {
                    isPressed = false
                    updateAppearance()

                    // Check minimum talk duration to prevent accidental taps
                    val talkDuration = System.currentTimeMillis() - lastTalkStartTime
                    if (talkDuration >= minTalkDuration) {
                        listener?.onStopTalking()
                    } else {
                        // Too short, might be accidental
                        postDelayed({
                            listener?.onStopTalking()
                        }, minTalkDuration - talkDuration)
                    }
                }

                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(widthSize, 300)
            else -> 200
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(heightSize, 300)
            else -> 200
        }

        // Keep it circular
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    private fun drawSpeakerIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        // Draw speaker cone
        val speakerRect = RectF(
            centerX - 15f,
            centerY - 10f,
            centerX - 5f,
            centerY + 10f
        )
        canvas.drawRect(speakerRect, iconPaint)

        // Draw sound waves
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 3f
        for (i in 1..3) {
            val radius = 8f + (i * 6f)
            canvas.drawArc(
                centerX - 5f - radius,
                centerY - radius,
                centerX - 5f + radius,
                centerY + radius,
                -30f, 60f, false, iconPaint
            )
        }
    }

    private fun drawBatteryIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Draw battery outline
        val batteryRect = RectF(
            centerX - 15f,
            centerY - 8f,
            centerX + 10f,
            centerY + 8f
        )
        canvas.drawRoundRect(batteryRect, 2f, 2f, iconPaint)

        // Draw battery tip
        canvas.drawRect(
            centerX + 10f,
            centerY - 4f,
            centerX + 13f,
            centerY + 4f,
            iconPaint
        )

        // Draw battery level (reduced for optimization)
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = Color.parseColor("#FFC107") // Amber
        val levelWidth = 15f // Reduced from full
        canvas.drawRect(
            centerX - 13f,
            centerY - 6f,
            centerX - 13f + levelWidth,
            centerY + 6f,
            iconPaint
        )
    }

    private fun drawQualityIndicator(canvas: Canvas, centerX: Float, centerY: Float) {
        val qualityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val radius = 8f
        qualityPaint.color = when {
            audioQuality > 0.7 -> Color.parseColor("#4CAF50") // Green
            audioQuality > 0.4 -> Color.parseColor("#FF9800") // Orange
            else -> Color.parseColor("#F44336") // Red
        }

        canvas.drawCircle(centerX, centerY, radius, qualityPaint)

        // Draw signal bars
        qualityPaint.color = Color.WHITE
        val barCount = (audioQuality * 3).toInt() + 1
        for (i in 0 until barCount) {
            val barHeight = 3f + (i * 2f)
            canvas.drawRect(
                centerX - 6f + (i * 4f),
                centerY + 2f - barHeight,
                centerX - 4f + (i * 4f),
                centerY + 2f,
                qualityPaint
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulseAnimation()
        stopSpeakingAnimation()
    }

    // Enhanced functionality
    fun getSessionInfo(): Map<String, Any> {
        return mapOf(
            "connectionState" to connectionState.name,
            "isRecording" to isRecording,
            "isSpeaking" to isSpeaking,
            "audioQuality" to audioQuality,
            "batteryOptimized" to batteryOptimized,
            "showConnectionHealth" to showConnectionHealth,
            "lastTalkDuration" to if (lastTalkStartTime > 0) System.currentTimeMillis() - lastTalkStartTime else 0
        )
    }

    fun setMinTalkDuration(durationMs: Long) {
        minTalkDuration = durationMs.coerceAtLeast(100L) // Minimum 100ms
    }

    // Accessibility support
    override fun getContentDescription(): CharSequence {
        return when {
            isRecording -> "Recording voice input, release to stop"
            isSpeaking -> "AI coach is speaking"
            connectionState == ConnectionState.CONNECTED -> {
                val quality = when {
                    audioQuality > 0.7 -> "excellent"
                    audioQuality > 0.4 -> "good"
                    else -> "poor"
                }
                val mode = if (batteryOptimized) " in battery saving mode" else ""
                "Push and hold to talk to your AI coach. Audio quality: $quality$mode"
            }
            connectionState == ConnectionState.CONNECTING -> "Connecting to AI coach"
            connectionState == ConnectionState.ERROR -> "Connection error, tap to retry"
            else -> "Disconnected from AI coach"
        }
    }
}