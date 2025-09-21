package com.posecoach.app.multipose

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.posecoach.app.R
import timber.log.Timber

/**
 * 多人選擇覆蓋層 - 顯示偵測到的人物邊界框並支援點擊切換
 */
class PersonSelectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnPersonSelectionListener {
        fun onPersonSelected(personId: String)
        fun onSelectionMethodChangeRequested()
    }

    private var listener: OnPersonSelectionListener? = null
    private var multiPoseResult: MultiPersonPoseManager.MultiPoseResult? = null
    private var showSelectionUI = false

    // Paint 物件
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#4CAF50") // 綠色
        pathEffect = null
    }

    private val unselectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FF9800") // 橙色
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33000000") // 半透明黑色
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0000000") // 深色背景
    }

    private val confidencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = Color.parseColor("#CCFFFFFF")
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    fun setOnPersonSelectionListener(listener: OnPersonSelectionListener?) {
        this.listener = listener
    }

    fun updateMultiPoseResult(result: MultiPersonPoseManager.MultiPoseResult?) {
        multiPoseResult = result
        showSelectionUI = (result?.totalDetected ?: 0) > 1
        invalidate()
    }

    fun setSelectionUIVisible(visible: Boolean) {
        showSelectionUI = visible
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!showSelectionUI) return

        val result = multiPoseResult ?: return

        if (result.detectedPersons.isEmpty()) {
            drawNoPersonsMessage(canvas)
            return
        }

        // 繪製每個偵測到的人
        result.detectedPersons.forEach { person ->
            drawPersonBoundingBox(canvas, person)
        }

        // 繪製選擇方法指示器
        drawSelectionMethodIndicator(canvas, result.selectionMethod)

        // 繪製人數統計
        drawPersonCountIndicator(canvas, result.totalDetected)
    }

    private fun drawPersonBoundingBox(canvas: Canvas, person: MultiPersonPoseManager.DetectedPerson) {
        val boundingBox = person.boundingBox
        val paint = if (person.isSelected) selectedBorderPaint else unselectedBorderPaint

        // 繪製邊界框
        canvas.drawRect(boundingBox, paint)

        // 繪製人物標籤
        drawPersonLabel(canvas, person)

        // 繪製信心度指示器
        drawConfidenceIndicator(canvas, person)
    }

    private fun drawPersonLabel(canvas: Canvas, person: MultiPersonPoseManager.DetectedPerson) {
        val boundingBox = person.boundingBox
        val labelText = if (person.isSelected) "主體 ${person.id}" else "人物 ${person.id}"

        // 計算標籤位置
        val labelX = boundingBox.centerX()
        val labelY = boundingBox.top - 20f

        // 計算標籤背景尺寸
        val textBounds = Rect()
        textPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
        val backgroundRect = RectF(
            labelX - textBounds.width() / 2f - 16f,
            labelY - textBounds.height() - 8f,
            labelX + textBounds.width() / 2f + 16f,
            labelY + 8f
        )

        // 繪製標籤背景
        canvas.drawRoundRect(backgroundRect, 12f, 12f, labelBackgroundPaint)

        // 繪製標籤文字
        canvas.drawText(labelText, labelX, labelY, textPaint)
    }

    private fun drawConfidenceIndicator(canvas: Canvas, person: MultiPersonPoseManager.DetectedPerson) {
        val boundingBox = person.boundingBox
        val confidenceText = "${"%.0f".format(person.confidence * 100)}%"

        // 位置在邊界框右上角
        val confidenceX = boundingBox.right - 30f
        val confidenceY = boundingBox.top + 40f

        // 信心度顏色 (基於數值)
        val confidenceColor = when {
            person.confidence > 0.8f -> Color.parseColor("#4CAF50") // 綠色
            person.confidence > 0.6f -> Color.parseColor("#FF9800") // 橙色
            else -> Color.parseColor("#F44336") // 紅色
        }

        confidencePaint.color = confidenceColor
        canvas.drawText(confidenceText, confidenceX, confidenceY, confidencePaint)
    }

    private fun drawSelectionMethodIndicator(
        canvas: Canvas,
        method: MultiPersonPoseManager.SelectionMethod
    ) {
        val methodText = when (method) {
            MultiPersonPoseManager.SelectionMethod.CLOSEST_TO_CAMERA -> "最近距離"
            MultiPersonPoseManager.SelectionMethod.LARGEST_BOUNDING_BOX -> "最大範圍"
            MultiPersonPoseManager.SelectionMethod.CENTER_OF_FRAME -> "畫面中心"
            MultiPersonPoseManager.SelectionMethod.MANUAL_SELECTION -> "手動選擇"
            MultiPersonPoseManager.SelectionMethod.HIGHEST_CONFIDENCE -> "最高信心"
        }

        val indicatorX = width - 20f
        val indicatorY = 60f

        // 繪製方法指示器背景
        val textBounds = Rect()
        confidencePaint.getTextBounds(methodText, 0, methodText.length, textBounds)
        val backgroundRect = RectF(
            indicatorX - textBounds.width() - 32f,
            indicatorY - textBounds.height() - 12f,
            indicatorX - 8f,
            indicatorY + 12f
        )

        canvas.drawRoundRect(backgroundRect, 8f, 8f, labelBackgroundPaint)

        // 繪製方法文字
        confidencePaint.color = Color.WHITE
        confidencePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(methodText, indicatorX - 20f, indicatorY, confidencePaint)

        // 重置對齊
        confidencePaint.textAlign = Paint.Align.CENTER
    }

    private fun drawPersonCountIndicator(canvas: Canvas, personCount: Int) {
        val countText = "偵測到 $personCount 人"
        val countX = 20f
        val countY = 60f

        // 繪製人數背景
        val textBounds = Rect()
        confidencePaint.getTextBounds(countText, 0, countText.length, textBounds)
        val backgroundRect = RectF(
            countX,
            countY - textBounds.height() - 12f,
            countX + textBounds.width() + 32f,
            countY + 12f
        )

        canvas.drawRoundRect(backgroundRect, 8f, 8f, labelBackgroundPaint)

        // 繪製人數文字
        confidencePaint.color = Color.parseColor("#FFD700") // 金色
        confidencePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(countText, countX + 16f, countY, confidencePaint)

        // 重置對齊
        confidencePaint.textAlign = Paint.Align.CENTER
    }

    private fun drawNoPersonsMessage(canvas: Canvas) {
        val message = "未偵測到人物"
        val messageX = width / 2f
        val messageY = height / 2f

        canvas.drawText(message, messageX, messageY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showSelectionUI) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y

                // 檢查是否點擊了選擇方法指示器 (右上角)
                if (touchX > width - 200f && touchY < 100f) {
                    listener?.onSelectionMethodChangeRequested()
                    return true
                }

                // 檢查是否點擊了某個人的邊界框
                val result = multiPoseResult ?: return false
                val clickedPerson = result.detectedPersons.find { person ->
                    person.boundingBox.contains(touchX, touchY)
                }

                if (clickedPerson != null) {
                    listener?.onPersonSelected(clickedPerson.id)
                    Timber.d("Person ${clickedPerson.id} selected by touch")
                    return true
                }
            }
        }

        return false
    }

    /**
     * 顯示選擇動畫
     */
    fun animateSelection(personId: String) {
        // 可以添加選擇動畫效果
        invalidate()
    }

    /**
     * 設定邊界框樣式
     */
    fun setBoundingBoxStyle(
        selectedColor: Int = Color.parseColor("#4CAF50"),
        unselectedColor: Int = Color.parseColor("#FF9800"),
        strokeWidth: Float = 6f
    ) {
        selectedBorderPaint.color = selectedColor
        selectedBorderPaint.strokeWidth = strokeWidth

        unselectedBorderPaint.color = unselectedColor
        unselectedBorderPaint.strokeWidth = strokeWidth / 2f

        invalidate()
    }

    /**
     * 獲取觸控區域資訊 (用於測試)
     */
    fun getTouchableAreas(): List<RectF> {
        val result = multiPoseResult ?: return emptyList()
        return result.detectedPersons.map { it.boundingBox }
    }

    /**
     * 檢查點是否在任何人物邊界框內
     */
    fun isPointInPersonBoundingBox(x: Float, y: Float): String? {
        val result = multiPoseResult ?: return null
        return result.detectedPersons.find { person ->
            person.boundingBox.contains(x, y)
        }?.id
    }
}