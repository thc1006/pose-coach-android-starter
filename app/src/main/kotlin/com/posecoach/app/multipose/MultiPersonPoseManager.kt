package com.posecoach.app.multipose

import android.graphics.PointF
import android.graphics.RectF
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 多人姿勢偵測管理器
 * 當偵測到多人時，自動選擇主體或允許手動切換
 */
class MultiPersonPoseManager {

    data class DetectedPerson(
        val id: String,
        val landmarks: PoseLandmarkResult.Landmark,
        val boundingBox: RectF,
        val confidence: Float,
        val noseDepth: Float, // Z 座標，用於距離判斷
        val isSelected: Boolean = false
    ) {
        val boundingBoxArea: Float get() = boundingBox.width() * boundingBox.height()
        val centerPoint: PointF get() = PointF(
            boundingBox.centerX(),
            boundingBox.centerY()
        )
    }

    data class MultiPoseResult(
        val detectedPersons: List<DetectedPerson>,
        val primaryPerson: DetectedPerson?,
        val totalDetected: Int,
        val selectionMethod: SelectionMethod,
        val originalLandmarks: List<PoseLandmarkResult>
    )

    enum class SelectionMethod {
        CLOSEST_TO_CAMERA,    // 最接近相機 (鼻尖深度最小)
        LARGEST_BOUNDING_BOX, // 最大邊界框
        CENTER_OF_FRAME,      // 最接近畫面中心
        MANUAL_SELECTION,     // 手動選擇
        HIGHEST_CONFIDENCE    // 最高信心度
    }

    companion object {
        private const val MIN_POSE_CONFIDENCE = 0.5f
        private const val NOSE_LANDMARK_INDEX = 0  // MediaPipe 鼻子地標索引
        private const val LEFT_EYE_INDEX = 1
        private const val RIGHT_EYE_INDEX = 2
        private const val LEFT_EAR_INDEX = 7
        private const val RIGHT_EAR_INDEX = 8

        // 用於計算邊界框的關鍵點索引
        private val BOUNDING_BOX_LANDMARKS = listOf(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,  // 臉部
            11, 12, 13, 14, 15, 16,             // 上半身
            23, 24, 25, 26, 27, 28              // 下半身
        )
    }

    private val _currentSelection = MutableStateFlow(SelectionMethod.CLOSEST_TO_CAMERA)
    val currentSelectionMethod: StateFlow<SelectionMethod> = _currentSelection.asStateFlow()

    private val _lastMultiPoseResult = MutableStateFlow<MultiPoseResult?>(null)
    val lastMultiPoseResult: StateFlow<MultiPoseResult?> = _lastMultiPoseResult.asStateFlow()

    private var manuallySelectedPersonId: String? = null
    private var frameWidth = 1080f
    private var frameHeight = 1920f

    fun setFrameSize(width: Int, height: Int) {
        frameWidth = width.toFloat()
        frameHeight = height.toFloat()
        Timber.d("Frame size updated: ${width}x${height}")
    }

    /**
     * 處理多人姿勢偵測結果
     */
    fun processMultiPersonPoses(
        poseResults: List<PoseLandmarkResult>,
        maxPersons: Int = 5
    ): MultiPoseResult {
        if (poseResults.isEmpty()) {
            val emptyResult = MultiPoseResult(
                detectedPersons = emptyList(),
                primaryPerson = null,
                totalDetected = 0,
                selectionMethod = _currentSelection.value,
                originalLandmarks = emptyList()
            )
            _lastMultiPoseResult.value = emptyResult
            return emptyResult
        }

        // 轉換為 DetectedPerson 物件
        val detectedPersons = poseResults.take(maxPersons).mapIndexed { index, poseResult ->
            createDetectedPerson(index.toString(), poseResult)
        }.filter { it.confidence >= MIN_POSE_CONFIDENCE }

        if (detectedPersons.isEmpty()) {
            val emptyResult = MultiPoseResult(
                detectedPersons = emptyList(),
                primaryPerson = null,
                totalDetected = 0,
                selectionMethod = _currentSelection.value,
                originalLandmarks = poseResults
            )
            _lastMultiPoseResult.value = emptyResult
            return emptyResult
        }

        // 選擇主體
        val primaryPerson = selectPrimaryPerson(detectedPersons)

        val result = MultiPoseResult(
            detectedPersons = detectedPersons.map { person ->
                person.copy(isSelected = person.id == primaryPerson?.id)
            },
            primaryPerson = primaryPerson,
            totalDetected = detectedPersons.size,
            selectionMethod = _currentSelection.value,
            originalLandmarks = poseResults
        )

        _lastMultiPoseResult.value = result

        Timber.d("Multi-pose processed: ${detectedPersons.size} persons, primary: ${primaryPerson?.id}")
        return result
    }

    private fun createDetectedPerson(id: String, poseResult: PoseLandmarkResult): DetectedPerson {
        val landmarks = poseResult.landmarks
        if (landmarks.isEmpty()) {
            throw IllegalArgumentException("Pose result has no landmarks")
        }

        // 計算邊界框
        val boundingBox = calculateBoundingBox(landmarks)

        // 獲取鼻子深度 (用於距離判斷)
        val noseDepth = if (landmarks.size > NOSE_LANDMARK_INDEX) {
            landmarks[NOSE_LANDMARK_INDEX].z
        } else {
            Float.MAX_VALUE
        }

        // 計算信心度 (基於可見性和存在性)
        val confidence = calculatePoseConfidence(landmarks)

        return DetectedPerson(
            id = id,
            landmarks = landmarks[0], // 使用第一個地標作為代表
            boundingBox = boundingBox,
            confidence = confidence,
            noseDepth = noseDepth
        )
    }

    private fun calculateBoundingBox(landmarks: List<PoseLandmarkResult.Landmark>): RectF {
        if (landmarks.isEmpty()) return RectF()

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        // 只使用關鍵地標來計算邊界框
        BOUNDING_BOX_LANDMARKS.forEach { index ->
            if (index < landmarks.size) {
                val landmark = landmarks[index]
                if (landmark.visibility > 0.5f) {  // 只考慮可見的地標
                    minX = min(minX, landmark.x)
                    maxX = max(maxX, landmark.x)
                    minY = min(minY, landmark.y)
                    maxY = max(maxY, landmark.y)
                }
            }
        }

        // 轉換為像素座標並添加邊距
        val margin = 0.05f // 5% 邊距
        val pixelMinX = (minX * frameWidth) - (frameWidth * margin)
        val pixelMaxX = (maxX * frameWidth) + (frameWidth * margin)
        val pixelMinY = (minY * frameHeight) - (frameHeight * margin)
        val pixelMaxY = (maxY * frameHeight) + (frameHeight * margin)

        return RectF(
            max(0f, pixelMinX),
            max(0f, pixelMinY),
            min(frameWidth, pixelMaxX),
            min(frameHeight, pixelMaxY)
        )
    }

    private fun calculatePoseConfidence(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        if (landmarks.isEmpty()) return 0f

        val keyLandmarks = listOf(
            NOSE_LANDMARK_INDEX,
            LEFT_EYE_INDEX,
            RIGHT_EYE_INDEX,
            LEFT_EAR_INDEX,
            RIGHT_EAR_INDEX
        )

        var totalConfidence = 0f
        var validLandmarks = 0

        keyLandmarks.forEach { index ->
            if (index < landmarks.size) {
                val landmark = landmarks[index]
                totalConfidence += landmark.visibility * landmark.presence
                validLandmarks++
            }
        }

        return if (validLandmarks > 0) totalConfidence / validLandmarks else 0f
    }

    private fun selectPrimaryPerson(detectedPersons: List<DetectedPerson>): DetectedPerson? {
        if (detectedPersons.isEmpty()) return null
        if (detectedPersons.size == 1) return detectedPersons.first()

        return when (_currentSelection.value) {
            SelectionMethod.MANUAL_SELECTION -> {
                selectManuallyChosenPerson(detectedPersons)
            }
            SelectionMethod.CLOSEST_TO_CAMERA -> {
                detectedPersons.minByOrNull { it.noseDepth }
            }
            SelectionMethod.LARGEST_BOUNDING_BOX -> {
                detectedPersons.maxByOrNull { it.boundingBoxArea }
            }
            SelectionMethod.CENTER_OF_FRAME -> {
                selectClosestToCenter(detectedPersons)
            }
            SelectionMethod.HIGHEST_CONFIDENCE -> {
                detectedPersons.maxByOrNull { it.confidence }
            }
        }
    }

    private fun selectManuallyChosenPerson(detectedPersons: List<DetectedPerson>): DetectedPerson? {
        // 首先嘗試找到手動選擇的人
        manuallySelectedPersonId?.let { selectedId ->
            detectedPersons.find { it.id == selectedId }?.let { return it }
        }

        // 如果手動選擇的人不在當前偵測中，回退到最接近相機的人
        return detectedPersons.minByOrNull { it.noseDepth }
    }

    private fun selectClosestToCenter(detectedPersons: List<DetectedPerson>): DetectedPerson? {
        val centerX = frameWidth / 2f
        val centerY = frameHeight / 2f

        return detectedPersons.minByOrNull { person ->
            val personCenter = person.centerPoint
            val distance = sqrt(
                (personCenter.x - centerX) * (personCenter.x - centerX) +
                (personCenter.y - centerY) * (personCenter.y - centerY)
            )
            distance
        }
    }

    /**
     * 手動選擇特定的人
     */
    fun selectPersonById(personId: String) {
        manuallySelectedPersonId = personId
        _currentSelection.value = SelectionMethod.MANUAL_SELECTION

        // 重新處理最後的結果以更新選擇
        _lastMultiPoseResult.value?.let { lastResult ->
            val updatedPersons = lastResult.detectedPersons.map { person ->
                person.copy(isSelected = person.id == personId)
            }
            val updatedPrimary = updatedPersons.find { it.id == personId }

            _lastMultiPoseResult.value = lastResult.copy(
                detectedPersons = updatedPersons,
                primaryPerson = updatedPrimary,
                selectionMethod = SelectionMethod.MANUAL_SELECTION
            )
        }

        Timber.i("Manual selection: person $personId")
    }

    /**
     * 根據點擊座標選擇人
     */
    fun selectPersonByTouch(touchX: Float, touchY: Float): Boolean {
        val currentResult = _lastMultiPoseResult.value ?: return false

        // 找到被點擊的人
        val touchedPerson = currentResult.detectedPersons.find { person ->
            person.boundingBox.contains(touchX, touchY)
        }

        return if (touchedPerson != null && touchedPerson.id != manuallySelectedPersonId) {
            selectPersonById(touchedPerson.id)
            true
        } else {
            false
        }
    }

    /**
     * 設定選擇方法
     */
    fun setSelectionMethod(method: SelectionMethod) {
        if (method != SelectionMethod.MANUAL_SELECTION) {
            manuallySelectedPersonId = null
        }
        _currentSelection.value = method

        // 重新處理最後的結果
        _lastMultiPoseResult.value?.let { lastResult ->
            if (lastResult.originalLandmarks.isNotEmpty()) {
                processMultiPersonPoses(lastResult.originalLandmarks)
            }
        }

        Timber.i("Selection method changed to: $method")
    }

    /**
     * 獲取主體人物的姿勢結果
     */
    fun getPrimaryPersonPose(): PoseLandmarkResult? {
        val result = _lastMultiPoseResult.value
        val primaryPerson = result?.primaryPerson ?: return null

        // 從原始結果中找到對應的姿勢資料
        return result.originalLandmarks.getOrNull(primaryPerson.id.toIntOrNull() ?: 0)
    }

    /**
     * 檢查是否偵測到多人
     */
    fun hasMultiplePerson(): Boolean {
        return (_lastMultiPoseResult.value?.totalDetected ?: 0) > 1
    }

    /**
     * 獲取偵測到的人數
     */
    fun getDetectedPersonCount(): Int {
        return _lastMultiPoseResult.value?.totalDetected ?: 0
    }

    /**
     * 重置選擇
     */
    fun resetSelection() {
        manuallySelectedPersonId = null
        _currentSelection.value = SelectionMethod.CLOSEST_TO_CAMERA
        Timber.d("Selection reset to default")
    }

    /**
     * 獲取狀態報告
     */
    fun getStatusReport(): String {
        val result = _lastMultiPoseResult.value
        return buildString {
            appendLine("=== 多人姿勢偵測狀態 ===")
            appendLine("偵測人數: ${result?.totalDetected ?: 0}")
            appendLine("選擇方法: ${_currentSelection.value}")
            appendLine("手動選擇ID: ${manuallySelectedPersonId ?: "無"}")
            appendLine("主體人物: ${result?.primaryPerson?.id ?: "無"}")

            result?.detectedPersons?.forEach { person ->
                appendLine()
                appendLine("人物 ${person.id}:")
                appendLine("  信心度: ${"%.2f".format(person.confidence)}")
                appendLine("  邊界框面積: ${"%.0f".format(person.boundingBoxArea)}px")
                appendLine("  鼻尖深度: ${"%.3f".format(person.noseDepth)}")
                appendLine("  是否選中: ${person.isSelected}")
            }
        }
    }
}