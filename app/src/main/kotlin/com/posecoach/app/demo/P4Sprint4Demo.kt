package com.posecoach.app.demo

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.multipose.MultiPersonPoseManager
import com.posecoach.app.performance.PerformanceDegradationStrategy
import com.posecoach.app.performance.PerformanceMetrics
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * P4 Sprint 4 示例：效能、多人、隱私功能整合
 */
class P4Sprint4Demo(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    // 核心組件
    private val performanceMetrics = PerformanceMetrics()
    private val degradationStrategy = PerformanceDegradationStrategy(performanceMetrics)
    private val multiPersonManager = MultiPersonPoseManager()
    private val privacyManager = EnhancedPrivacyManager(context)
    private val liveCoachManager = LiveCoachManager(context, lifecycleScope)

    private var frameCounter = 0L

    init {
        setupPerformanceMonitoring()
        setupPrivacyDefaults()
        Timber.i("P4 Sprint 4 Demo initialized")
    }

    private fun setupPerformanceMonitoring() {
        // 監聽效能警告
        lifecycleScope.launch {
            performanceMetrics.performanceAlerts.collect { alert ->
                when (alert.type) {
                    PerformanceMetrics.AlertType.CRITICAL -> {
                        Timber.w("CRITICAL Performance Alert: ${alert.operationName} = ${alert.actualValue}ms")
                        // 自動啟用降級策略
                        degradationStrategy.setAutoOptimizationEnabled(true)
                    }
                    PerformanceMetrics.AlertType.WARNING -> {
                        Timber.w("WARNING Performance Alert: ${alert.operationName} = ${alert.actualValue}ms")
                    }
                    PerformanceMetrics.AlertType.IMPROVEMENT_SUGGESTION -> {
                        Timber.i("Performance Suggestion: ${alert.suggestion}")
                    }
                }
            }
        }

        // 監聽幀計量
        lifecycleScope.launch {
            performanceMetrics.frameMetrics.collect { frameMetrics ->
                Timber.v("Frame ${frameMetrics.frameIndex}: inference=${frameMetrics.inferenceTimeMs}ms, poses=${frameMetrics.numDetectedPoses}")

                // 自動調整效能策略
                degradationStrategy.analyzeAndAdjustPerformance(
                    frameMetrics.inferenceTimeMs,
                    frameMetrics.endToEndTimeMs
                )
            }
        }
    }

    private fun setupPrivacyDefaults() {
        // 設定預設為高隱私等級
        privacyManager.setPrivacyLevel(EnhancedPrivacyManager.PrivacyLevel.HIGH_PRIVACY)

        // 監聽隱私設定變化
        lifecycleScope.launch {
            privacyManager.privacySettings.collect { settings ->
                Timber.i("Privacy settings updated: offline=${settings.offlineModeEnabled}, landmarks=${settings.allowLandmarkUpload}")
            }
        }
    }

    /**
     * 示範單幀處理流程：效能測量 + 多人偵測 + 隱私控制
     */
    fun processSingleFrame(
        poseResults: List<PoseLandmarkResult>,
        originalWidth: Int,
        originalHeight: Int
    ): ProcessingResult {
        frameCounter++

        // 1. 效能測量開始
        val frameTraceId = performanceMetrics.startTrace("frame_processing")
        val inferenceTraceId = performanceMetrics.startTrace("pose_inference")

        try {
            // 2. 檢查是否應該處理此幀 (基於降級策略)
            if (!degradationStrategy.shouldProcessFrame()) {
                performanceMetrics.endTrace(frameTraceId, "frame_processing")
                return ProcessingResult.Skipped("Frame skipped due to performance strategy")
            }

            // 3. 應用解析度降級
            val adjustedSize = degradationStrategy.adjustInputResolution(
                android.util.Size(originalWidth, originalHeight)
            )

            // 4. 限制最大偵測人數
            val maxPoses = degradationStrategy.limitMaxDetectedPoses(poseResults.size)
            val limitedPoseResults = poseResults.take(maxPoses)

            // 模擬推論時間
            val inferenceStartTime = System.currentTimeMillis()
            Thread.sleep((20..80).random().toLong()) // 模擬推論延遲
            val inferenceTime = System.currentTimeMillis() - inferenceStartTime

            performanceMetrics.endTrace(inferenceTraceId, "pose_inference")

            // 5. 多人姿勢處理
            val multiPoseResult = multiPersonManager.processMultiPersonPoses(
                limitedPoseResults,
                maxPoses
            )

            // 6. 隱私檢查與資料過濾
            val filteredResult = applyPrivacyFiltering(multiPoseResult)

            // 7. 記錄效能指標
            if (privacyManager.isDataUploadAllowed(EnhancedPrivacyManager.DataType.PERFORMANCE_METRICS)) {
                performanceMetrics.recordFrameMetrics(
                    frameIndex = frameCounter,
                    inferenceTimeMs = inferenceTime.toDouble(),
                    inputWidth = adjustedSize.width,
                    inputHeight = adjustedSize.height,
                    numDetectedPoses = filteredResult.detectedPersons.size
                )
            }

            performanceMetrics.endTrace(frameTraceId, "frame_processing")

            return ProcessingResult.Success(
                multiPoseResult = filteredResult,
                performanceInfo = ProcessingPerformanceInfo(
                    frameIndex = frameCounter,
                    inferenceTimeMs = inferenceTime.toDouble(),
                    adjustedResolution = adjustedSize,
                    performanceLevel = degradationStrategy.getCurrentPerformanceLevel().level
                )
            )

        } catch (e: Exception) {
            performanceMetrics.endTrace(frameTraceId, "frame_processing")
            Timber.e(e, "Error processing frame")
            return ProcessingResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun applyPrivacyFiltering(
        result: MultiPersonPoseManager.MultiPoseResult
    ): MultiPersonPoseManager.MultiPoseResult {
        // 根據隱私設定過濾資料
        return when {
            privacyManager.isOfflineModeEnabled() -> {
                // 離線模式：保留所有本地資料，但標記為不上傳
                result.copy(
                    // 保持本地處理結果完整
                )
            }
            !privacyManager.isLandmarkUploadAllowed() -> {
                // 如果不允許地標上傳，清空敏感資料
                result.copy(
                    detectedPersons = emptyList(),
                    primaryPerson = null
                )
            }
            else -> result
        }
    }

    /**
     * 示範多人切換功能
     */
    fun demonstratePersonSwitching() {
        val currentResult = multiPersonManager.lastMultiPoseResult.value

        if (currentResult != null && currentResult.totalDetected > 1) {
            Timber.i("Multiple persons detected (${currentResult.totalDetected})")
            Timber.i("Current primary: ${currentResult.primaryPerson?.id}")

            // 示範不同選擇方法
            val methods = MultiPersonPoseManager.SelectionMethod.values()
            methods.forEach { method ->
                multiPersonManager.setSelectionMethod(method)
                val newPrimary = multiPersonManager.lastMultiPoseResult.value?.primaryPerson
                Timber.i("Method $method -> Primary: ${newPrimary?.id}")
            }

            // 示範手動選擇
            currentResult.detectedPersons.firstOrNull()?.let { person ->
                multiPersonManager.selectPersonById(person.id)
                Timber.i("Manual selection: ${person.id}")
            }
        }
    }

    /**
     * 示範隱私控制功能
     */
    fun demonstratePrivacyControls() {
        Timber.i("=== Privacy Demo ===")

        // 示範不同隱私等級
        EnhancedPrivacyManager.PrivacyLevel.values().forEach { level ->
            privacyManager.setPrivacyLevel(level)
            val status = privacyManager.getPrivacyStatusSummary()
            Timber.i("Privacy Level $level:\n$status")
        }

        // 示範離線模式
        privacyManager.setOfflineModeEnabled(true)
        Timber.i("Offline mode enabled")

        // 示範地標專用模式
        privacyManager.setImageUploadAllowed(false)
        privacyManager.setAudioUploadAllowed(false)
        privacyManager.setOfflineModeEnabled(false)
        Timber.i("Landmark-only mode: ${liveCoachManager.isLandmarkOnlyMode()}")
    }

    /**
     * 示範效能降級功能
     */
    fun demonstratePerformanceDegradation() {
        Timber.i("=== Performance Degradation Demo ===")

        // 示範不同效能等級
        PerformanceDegradationStrategy.Level.values().forEach { level ->
            degradationStrategy.setPerformanceLevel(level)
            val perfLevel = degradationStrategy.getCurrentPerformanceLevel()
            Timber.i("Performance Level $level:")
            Timber.i("  Resolution: ${perfLevel.targetResolution}")
            Timber.i("  Frame Skip: ${perfLevel.frameSkipRatio}")
            Timber.i("  Max Poses: ${perfLevel.maxDetectedPoses}")
        }

        // 示範自動降級
        degradationStrategy.setAutoOptimizationEnabled(true)

        // 模擬效能問題
        repeat(5) {
            degradationStrategy.analyzeAndAdjustPerformance(
                currentInferenceTimeMs = 80.0, // 超過閾值
                currentEndToEndTimeMs = 120.0
            )
        }

        Timber.i("Final performance level: ${degradationStrategy.getCurrentPerformanceLevel().level}")
    }

    /**
     * 獲取完整狀態報告
     */
    fun generateStatusReport(): String {
        return buildString {
            appendLine("=== P4 Sprint 4 狀態報告 ===")
            appendLine()

            // 效能狀態
            appendLine(performanceMetrics.generatePerformanceReport())
            appendLine()

            // 降級策略狀態
            appendLine(degradationStrategy.getStatusReport())
            appendLine()

            // 多人偵測狀態
            appendLine(multiPersonManager.getStatusReport())
            appendLine()

            // 隱私狀態
            appendLine(privacyManager.getPrivacyStatusSummary())
            appendLine()

            // Live Coach 狀態
            val sessionInfo = liveCoachManager.getSessionInfo()
            appendLine("Live Coach 狀態:")
            sessionInfo.forEach { (key, value) ->
                appendLine("  $key: $value")
            }
        }
    }

    fun destroy() {
        performanceMetrics.clearAllMeasurements()
        degradationStrategy.reset()
        multiPersonManager.resetSelection()
        liveCoachManager.destroy()
        Timber.d("P4 Sprint 4 Demo destroyed")
    }

    // 結果類別定義
    sealed class ProcessingResult {
        data class Success(
            val multiPoseResult: MultiPersonPoseManager.MultiPoseResult,
            val performanceInfo: ProcessingPerformanceInfo
        ) : ProcessingResult()

        data class Skipped(val reason: String) : ProcessingResult()
        data class Error(val message: String) : ProcessingResult()
    }

    data class ProcessingPerformanceInfo(
        val frameIndex: Long,
        val inferenceTimeMs: Double,
        val adjustedResolution: android.util.Size,
        val performanceLevel: PerformanceDegradationStrategy.Level
    )
}