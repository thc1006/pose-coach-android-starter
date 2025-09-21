package com.posecoach.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.posecoach.R
import kotlin.math.*
import kotlin.random.Random

/**
 * Real-time voice activity visualization with waveform display
 * Features:
 * - Real-time amplitude visualization
 * - Smooth animations
 * - Voice activity detection indicator
 * - Frequency-based coloring
 */
class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint objects
    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vadIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Colors
    private val primaryColor = ContextCompat.getColor(context, R.color.waveform_primary)
    private val secondaryColor = ContextCompat.getColor(context, R.color.waveform_secondary)
    private val backgroundColorValue = ContextCompat.getColor(context, R.color.waveform_background)
    private val vadActiveColor = ContextCompat.getColor(context, R.color.vad_active)
    private val vadInactiveColor = ContextCompat.getColor(context, R.color.vad_inactive)

    // Waveform data
    private val amplitudes = mutableListOf<Float>()
    private val maxAmplitudes = 50 // Number of amplitude bars
    private var currentAmplitude = 0f
    private var targetAmplitude = 0f

    // Voice Activity Detection
    private var isVoiceActive = false
    private var vadIndicatorAlpha = 0f

    // Animation
    private var amplitudeAnimator: ValueAnimator? = null
    private var vadAnimator: ValueAnimator? = null

    // Configuration
    private val barWidth = 6f
    private val barSpacing = 2f
    private val minBarHeight = 4f
    private val maxBarHeight = 80f

    init {
        setupPaints()
        initializeAmplitudes()
        startIdleAnimation()
    }

    private fun setupPaints() {
        waveformPaint.apply {
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
        }

        backgroundPaint.apply {
            style = Paint.Style.FILL
            color = backgroundColorValue
        }

        vadIndicatorPaint.apply {
            style = Paint.Style.FILL
        }
    }

    private fun initializeAmplitudes() {
        // Initialize with zero amplitudes
        repeat(maxAmplitudes) {
            amplitudes.add(0f)
        }
    }

    private fun startIdleAnimation() {
        // Subtle idle animation when no voice input
        amplitudeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                if (!isVoiceActive) {
                    updateIdleWaveform(animator.animatedValue as Float)
                }
            }
            start()
        }
    }

    private fun updateIdleWaveform(phase: Float) {
        for (i in amplitudes.indices) {
            val frequency = 0.5f + i * 0.1f
            val amplitude = sin(phase * 2 * PI * frequency).toFloat()
            amplitudes[i] = (amplitude * 0.2f + 0.2f) * minBarHeight
        }
        invalidate()
    }

    fun updateActivity(voiceActivity: VoiceActivity) {
        isVoiceActive = voiceActivity.isActive
        targetAmplitude = voiceActivity.amplitude

        // Update amplitude with smooth animation
        animateAmplitude()

        // Update VAD indicator
        animateVADIndicator(voiceActivity.isActive)

        // Add new amplitude to the list
        if (voiceActivity.isActive) {
            addAmplitude(voiceActivity.amplitude)
        }
    }

    private fun animateAmplitude() {
        amplitudeAnimator?.cancel()

        amplitudeAnimator = ValueAnimator.ofFloat(currentAmplitude, targetAmplitude).apply {
            duration = 100
            addUpdateListener { animator ->
                currentAmplitude = animator.animatedValue as Float
                if (isVoiceActive) {
                    updateActiveWaveform()
                }
            }
            start()
        }
    }

    private fun animateVADIndicator(active: Boolean) {
        vadAnimator?.cancel()

        val targetAlpha = if (active) 1f else 0f
        vadAnimator = ValueAnimator.ofFloat(vadIndicatorAlpha, targetAlpha).apply {
            duration = 200
            addUpdateListener { animator ->
                vadIndicatorAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun addAmplitude(amplitude: Float) {
        // Remove oldest amplitude and add new one
        if (amplitudes.size >= maxAmplitudes) {
            amplitudes.removeAt(0)
        }
        amplitudes.add(amplitude)
        invalidate()
    }

    private fun updateActiveWaveform() {
        // Generate realistic waveform based on current amplitude
        for (i in amplitudes.indices) {
            val baseAmplitude = currentAmplitude * maxBarHeight
            val variation = Random.nextFloat() * 0.5f + 0.5f
            val frequency = sin((i * 0.3f) + (System.currentTimeMillis() * 0.01f))

            amplitudes[i] = max(
                minBarHeight,
                baseAmplitude * variation * (0.8f + frequency * 0.2f)
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        val totalWidth = amplitudes.size * (barWidth + barSpacing)
        val startX = (width - totalWidth) / 2f

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Draw waveform bars
        for (i in amplitudes.indices) {
            val x = startX + i * (barWidth + barSpacing)
            val barHeight = amplitudes[i]

            // Calculate color based on amplitude
            val normalizedAmplitude = barHeight / maxBarHeight
            val color = interpolateColor(secondaryColor, primaryColor, normalizedAmplitude)
            waveformPaint.color = color

            // Draw symmetric bars around center
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f

            canvas.drawRoundRect(
                x, top, x + barWidth, bottom,
                barWidth / 2f, barWidth / 2f,
                waveformPaint
            )
        }

        // Draw VAD indicator
        drawVADIndicator(canvas)
    }

    private fun drawVADIndicator(canvas: Canvas) {
        if (vadIndicatorAlpha > 0f) {
            val indicatorSize = 12f
            val margin = 16f

            vadIndicatorPaint.color = if (isVoiceActive) vadActiveColor else vadInactiveColor
            vadIndicatorPaint.alpha = (vadIndicatorAlpha * 255).toInt()

            canvas.drawCircle(
                width - margin - indicatorSize / 2f,
                margin + indicatorSize / 2f,
                indicatorSize / 2f,
                vadIndicatorPaint
            )

            // Draw pulsing ring for active state
            if (isVoiceActive) {
                val pulseRadius = indicatorSize / 2f + sin(System.currentTimeMillis() * 0.01f).toFloat() * 4f
                vadIndicatorPaint.alpha = ((1f - (pulseRadius - indicatorSize / 2f) / 4f) * vadIndicatorAlpha * 100).toInt()
                canvas.drawCircle(
                    width - margin - indicatorSize / 2f,
                    margin + indicatorSize / 2f,
                    pulseRadius,
                    vadIndicatorPaint
                )
            }
        }
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val clampedFraction = fraction.coerceIn(0f, 1f)

        val startA = Color.alpha(startColor)
        val startR = Color.red(startColor)
        val startG = Color.green(startColor)
        val startB = Color.blue(startColor)

        val endA = Color.alpha(endColor)
        val endR = Color.red(endColor)
        val endG = Color.green(endColor)
        val endB = Color.blue(endColor)

        return Color.argb(
            (startA + (endA - startA) * clampedFraction).toInt(),
            (startR + (endR - startR) * clampedFraction).toInt(),
            (startG + (endG - startG) * clampedFraction).toInt(),
            (startB + (endB - startB) * clampedFraction).toInt()
        )
    }

    fun reset() {
        isVoiceActive = false
        currentAmplitude = 0f
        targetAmplitude = 0f
        amplitudes.clear()
        initializeAmplitudes()
        invalidate()
    }

    fun setVADSensitivity(sensitivity: Float) {
        // Adjust VAD sensitivity (0.0 to 1.0)
        // This could be used to adjust the threshold for voice activity detection
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        amplitudeAnimator?.cancel()
        vadAnimator?.cancel()
    }
}

/**
 * Data class representing voice activity information
 */
data class VoiceActivity(
    val isActive: Boolean,
    val amplitude: Float, // 0.0 to 1.0
    val frequency: Float = 0f, // Optional frequency information
    val confidence: Float = 1f // Confidence level of VAD detection
)