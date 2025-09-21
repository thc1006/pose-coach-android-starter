package com.posecoach.app.privacy

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import timber.log.Timber
import kotlin.math.sin

/**
 * Privacy Indicator View
 * Displays real-time privacy status and data processing activity
 *
 * Features:
 * - Visual privacy status indicators
 * - Pulsing animation during data processing
 * - Color-coded privacy levels
 * - Accessible design with text labels
 */
class PrivacyIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class PrivacyStatus {
        LOCAL_ONLY,          // Green - All local processing
        LANDMARKS_ONLY,      // Yellow - Only landmarks uploaded
        CLOUD_ENABLED,       // Orange - Cloud features active
        OFFLINE_MODE,        // Blue - Forced offline mode
        PRIVACY_VIOLATION    // Red - Potential privacy issue
    }

    data class PrivacyState(
        val status: PrivacyStatus,
        val isDataBeingProcessed: Boolean = false,
        val dataType: String? = null,
        val processingLocation: String = "local",
        val lastUpdate: Long = System.currentTimeMillis()
    )

    private var currentState = PrivacyState(PrivacyStatus.LOCAL_ONLY)
    private var pulseAnimator: ValueAnimator? = null
    private var pulsePhase = 0f

    // Paint objects for drawing
    private val indicatorPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val smallTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 16f
        textAlign = Paint.Align.CENTER
    }

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(180, 0, 0, 0)
    }

    init {
        // Set up accessibility
        contentDescription = "Privacy status indicator"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /**
     * Update privacy status with audit logging
     * 更新隱私狀態並記錄審計日誌
     */
    fun updatePrivacyStatus(
        status: PrivacyStatus,
        dataType: String? = null,
        processingLocation: String = "local"
    ) {
        val newState = currentState.copy(
            status = status,
            dataType = dataType,
            processingLocation = processingLocation,
            lastUpdate = System.currentTimeMillis()
        )

        if (newState.status != currentState.status) {
            Timber.d("Privacy status changed: ${currentState.status} -> ${newState.status}")
        }

        currentState = newState
        updateAccessibilityDescription()
        invalidate()
    }

    /**
     * Set data processing activity state
     * 設定資料處理活動狀態
     */
    fun setDataProcessingActive(
        active: Boolean,
        dataType: String? = null,
        processingLocation: String = "local"
    ) {
        currentState = currentState.copy(
            isDataBeingProcessed = active,
            dataType = dataType,
            processingLocation = processingLocation
        )

        if (active) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }

        updateAccessibilityDescription()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val centerX = width - 80f
        val centerY = 80f
        val radius = 30f

        // Draw background circle
        drawBackgroundCircle(canvas, centerX, centerY, radius)

        // Draw main indicator
        drawMainIndicator(canvas, centerX, centerY, radius)

        // Draw pulse effect if processing
        if (currentState.isDataBeingProcessed) {
            drawPulseEffect(canvas, centerX, centerY, radius)
        }

        // Draw status text
        drawStatusText(canvas, centerX, centerY)

        // Draw processing info if active
        if (currentState.isDataBeingProcessed) {
            drawProcessingInfo(canvas, centerX, centerY + radius + 20f)
        }
    }

    private fun drawBackgroundCircle(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        backgroundPaint.color = Color.argb(200, 0, 0, 0)
        canvas.drawCircle(centerX, centerY, radius + 8f, backgroundPaint)
    }

    private fun drawMainIndicator(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val indicatorColor = getStatusColor(currentState.status)
        indicatorPaint.color = indicatorColor

        // Draw main circle
        canvas.drawCircle(centerX, centerY, radius, indicatorPaint)

        // Draw border
        strokePaint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
    }

    private fun drawPulseEffect(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val pulseRadius = radius + (15f * sin(pulsePhase))
        val pulseAlpha = (128 * (1f - sin(pulsePhase) / 2f)).toInt()

        val pulsePaint = Paint().apply {
            color = Color.argb(pulseAlpha, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        canvas.drawCircle(centerX, centerY, pulseRadius, pulsePaint)

        // Draw inner pulse for processing indication
        val innerPulseRadius = radius + (8f * sin(pulsePhase + 1f))
        val innerPulsePaint = Paint().apply {
            color = Color.argb(pulseAlpha / 2, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        canvas.drawCircle(centerX, centerY, innerPulseRadius, innerPulsePaint)
    }

    private fun drawStatusText(canvas: Canvas, centerX: Float, centerY: Float) {
        val statusText = getStatusText(currentState.status)
        textPaint.color = Color.WHITE

        // Add text shadow for better readability
        val shadowPaint = Paint(textPaint).apply {
            color = Color.BLACK
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }

        canvas.drawText(statusText, centerX, centerY + 5f, shadowPaint)
        canvas.drawText(statusText, centerX, centerY + 5f, textPaint)
    }

    private fun drawProcessingInfo(canvas: Canvas, centerX: Float, centerY: Float) {
        val processingText = when (currentState.processingLocation) {
            "local" -> "本地處理"
            "cloud_landmarks" -> "地標分析"
            "cloud_enhanced" -> "雲端增強"
            else -> "處理中"
        }

        smallTextPaint.color = getStatusColor(currentState.status)

        // Background for processing text
        val textBounds = Rect()
        smallTextPaint.getTextBounds(processingText, 0, processingText.length, textBounds)

        val bgLeft = centerX - textBounds.width() / 2f - 8f
        val bgRight = centerX + textBounds.width() / 2f + 8f
        val bgTop = centerY - textBounds.height() / 2f - 4f
        val bgBottom = centerY + textBounds.height() / 2f + 4f

        backgroundPaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 8f, 8f, backgroundPaint)

        canvas.drawText(processingText, centerX, centerY, smallTextPaint)
    }

    private fun getStatusColor(status: PrivacyStatus): Int {
        return when (status) {
            PrivacyStatus.LOCAL_ONLY -> Color.parseColor("#4CAF50")      // Green
            PrivacyStatus.LANDMARKS_ONLY -> Color.parseColor("#FF9800")  // Orange
            PrivacyStatus.CLOUD_ENABLED -> Color.parseColor("#2196F3")   // Blue
            PrivacyStatus.OFFLINE_MODE -> Color.parseColor("#9C27B0")    // Purple
            PrivacyStatus.PRIVACY_VIOLATION -> Color.parseColor("#F44336") // Red
        }
    }

    private fun getStatusText(status: PrivacyStatus): String {
        return when (status) {
            PrivacyStatus.LOCAL_ONLY -> "本地"
            PrivacyStatus.LANDMARKS_ONLY -> "地標"
            PrivacyStatus.CLOUD_ENABLED -> "雲端"
            PrivacyStatus.OFFLINE_MODE -> "離線"
            PrivacyStatus.PRIVACY_VIOLATION -> "警告"
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()

        pulseAnimator = ValueAnimator.ofFloat(0f, 2f * Math.PI.toFloat()).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animation ->
                pulsePhase = animation.animatedValue as Float
                invalidate()
            }
        }

        pulseAnimator?.start()
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        invalidate()
    }

    private fun updateAccessibilityDescription() {
        val statusDescription = when (currentState.status) {
            PrivacyStatus.LOCAL_ONLY -> "隱私狀態：本地處理，資料不離開裝置"
            PrivacyStatus.LANDMARKS_ONLY -> "隱私狀態：僅上傳匿名地標資料"
            PrivacyStatus.CLOUD_ENABLED -> "隱私狀態：雲端功能已啟用"
            PrivacyStatus.OFFLINE_MODE -> "隱私狀態：離線模式"
            PrivacyStatus.PRIVACY_VIOLATION -> "隱私警告：偵測到潛在隱私問題"
        }

        val processingDescription = if (currentState.isDataBeingProcessed) {
            "，正在${currentState.processingLocation}處理${currentState.dataType ?: "資料"}"
        } else {
            ""
        }

        contentDescription = "$statusDescription$processingDescription"
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulseAnimation()
    }

    /**
     * Get current privacy state for external monitoring
     * 獲取當前隱私狀態供外部監控
     */
    fun getCurrentPrivacyState(): PrivacyState {
        return currentState
    }

    /**
     * Show privacy violation alert
     * 顯示隱私違規警報
     */
    fun showPrivacyViolationAlert(message: String) {
        updatePrivacyStatus(PrivacyStatus.PRIVACY_VIOLATION)
        setDataProcessingActive(true, "VIOLATION", "BLOCKED")

        // Flash the indicator red
        val originalPaint = Paint(indicatorPaint)
        indicatorPaint.color = Color.RED

        postDelayed({
            indicatorPaint.color = originalPaint.color
            invalidate()
        }, 3000)

        Timber.e("Privacy violation alert displayed: $message")
    }
}