package com.posecoach.app.performance

import android.util.Size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * 效能降級策略：根據即時效能調整輸入尺寸和幀率
 */
class PerformanceDegradationStrategy(
    private val performanceMetrics: PerformanceMetrics
) {

    data class PerformanceLevel(
        val level: Level,
        val targetResolution: Size,
        val frameSkipRatio: Int, // 1 = 處理每幀, 2 = 跳過每兩幀處理一幀
        val maxDetectedPoses: Int,
        val description: String
    )

    enum class Level {
        HIGH_QUALITY,    // 高品質模式
        BALANCED,        // 平衡模式
        PERFORMANCE,     // 效能優先
        LOW_POWER        // 省電模式
    }

    companion object {
        // 預設效能等級設定
        val PERFORMANCE_LEVELS = mapOf(
            Level.HIGH_QUALITY to PerformanceLevel(
                level = Level.HIGH_QUALITY,
                targetResolution = Size(640, 480),
                frameSkipRatio = 1,
                maxDetectedPoses = 5,
                description = "高品質：最佳準確度和多人偵測"
            ),
            Level.BALANCED to PerformanceLevel(
                level = Level.BALANCED,
                targetResolution = Size(480, 360),
                frameSkipRatio = 1,
                maxDetectedPoses = 3,
                description = "平衡：準確度與效能兼顧"
            ),
            Level.PERFORMANCE to PerformanceLevel(
                level = Level.PERFORMANCE,
                targetResolution = Size(320, 240),
                frameSkipRatio = 2,
                maxDetectedPoses = 2,
                description = "效能：優先流暢度"
            ),
            Level.LOW_POWER to PerformanceLevel(
                level = Level.LOW_POWER,
                targetResolution = Size(240, 180),
                frameSkipRatio = 3,
                maxDetectedPoses = 1,
                description = "省電：最低資源消耗"
            )
        )

        // 效能閾值
        private const val PERFORMANCE_DEGRADATION_THRESHOLD_MS = 50.0
        private const val PERFORMANCE_RECOVERY_THRESHOLD_MS = 30.0
        private const val CONSECUTIVE_CHECKS_BEFORE_CHANGE = 3
    }

    private val _currentLevel = MutableStateFlow(Level.BALANCED)
    val currentLevel: StateFlow<Level> = _currentLevel.asStateFlow()

    private val _isAutoOptimizationEnabled = MutableStateFlow(true)
    val isAutoOptimizationEnabled: StateFlow<Boolean> = _isAutoOptimizationEnabled.asStateFlow()

    private var frameCounter = 0
    private var recentPerformanceChecks = mutableListOf<Double>()
    private var lastLevelChangeTime = 0L

    fun getCurrentPerformanceLevel(): PerformanceLevel {
        return PERFORMANCE_LEVELS[_currentLevel.value]
            ?: PERFORMANCE_LEVELS[Level.BALANCED]!!
    }

    /**
     * 根據效能資料自動調整等級
     */
    fun analyzeAndAdjustPerformance(
        currentInferenceTimeMs: Double,
        currentEndToEndTimeMs: Double
    ) {
        if (!_isAutoOptimizationEnabled.value) return

        val currentTime = System.currentTimeMillis()
        val timeSinceLastChange = currentTime - lastLevelChangeTime

        // 避免過於頻繁的等級切換 (至少間隔 2 秒)
        if (timeSinceLastChange < 2000) return

        // 記錄最近的效能資料
        recentPerformanceChecks.add(currentEndToEndTimeMs)
        if (recentPerformanceChecks.size > CONSECUTIVE_CHECKS_BEFORE_CHANGE) {
            recentPerformanceChecks.removeAt(0)
        }

        // 需要連續幾次檢查才改變等級
        if (recentPerformanceChecks.size < CONSECUTIVE_CHECKS_BEFORE_CHANGE) return

        val averagePerformance = recentPerformanceChecks.average()
        val currentLevel = _currentLevel.value

        val newLevel = when {
            // 效能不佳，需要降級
            averagePerformance > PERFORMANCE_DEGRADATION_THRESHOLD_MS && canDegrade(currentLevel) -> {
                degradePerformanceLevel(currentLevel)
            }
            // 效能良好，可以升級
            averagePerformance < PERFORMANCE_RECOVERY_THRESHOLD_MS && canUpgrade(currentLevel) -> {
                upgradePerformanceLevel(currentLevel)
            }
            else -> currentLevel
        }

        if (newLevel != currentLevel) {
            setPerformanceLevel(newLevel)
            lastLevelChangeTime = currentTime
            recentPerformanceChecks.clear()

            Timber.i("效能等級自動調整: $currentLevel -> $newLevel (平均延遲: ${"%.1f".format(averagePerformance)}ms)")
        }
    }

    private fun canDegrade(currentLevel: Level): Boolean {
        return when (currentLevel) {
            Level.HIGH_QUALITY -> true
            Level.BALANCED -> true
            Level.PERFORMANCE -> true
            Level.LOW_POWER -> false
        }
    }

    private fun canUpgrade(currentLevel: Level): Boolean {
        return when (currentLevel) {
            Level.HIGH_QUALITY -> false
            Level.BALANCED -> true
            Level.PERFORMANCE -> true
            Level.LOW_POWER -> true
        }
    }

    private fun degradePerformanceLevel(currentLevel: Level): Level {
        return when (currentLevel) {
            Level.HIGH_QUALITY -> Level.BALANCED
            Level.BALANCED -> Level.PERFORMANCE
            Level.PERFORMANCE -> Level.LOW_POWER
            Level.LOW_POWER -> Level.LOW_POWER
        }
    }

    private fun upgradePerformanceLevel(currentLevel: Level): Level {
        return when (currentLevel) {
            Level.HIGH_QUALITY -> Level.HIGH_QUALITY
            Level.BALANCED -> Level.HIGH_QUALITY
            Level.PERFORMANCE -> Level.BALANCED
            Level.LOW_POWER -> Level.PERFORMANCE
        }
    }

    /**
     * 手動設定效能等級
     */
    fun setPerformanceLevel(level: Level) {
        val oldLevel = _currentLevel.value
        _currentLevel.value = level

        val performanceLevel = getCurrentPerformanceLevel()
        Timber.i("效能等級設定: $oldLevel -> $level")
        Timber.i("  解析度: ${performanceLevel.targetResolution}")
        Timber.i("  幀跳躍: ${performanceLevel.frameSkipRatio}")
        Timber.i("  最大偵測人數: ${performanceLevel.maxDetectedPoses}")
    }

    /**
     * 啟用/停用自動最佳化
     */
    fun setAutoOptimizationEnabled(enabled: Boolean) {
        _isAutoOptimizationEnabled.value = enabled
        if (enabled) {
            recentPerformanceChecks.clear()
            lastLevelChangeTime = 0L
        }
        Timber.i("自動效能最佳化: ${if (enabled) "啟用" else "停用"}")
    }

    /**
     * 檢查是否應該處理此幀 (基於幀跳躍策略)
     */
    fun shouldProcessFrame(): Boolean {
        frameCounter++
        val skipRatio = getCurrentPerformanceLevel().frameSkipRatio
        return (frameCounter % skipRatio) == 0
    }

    /**
     * 根據當前效能等級調整輸入解析度
     */
    fun adjustInputResolution(originalSize: Size): Size {
        val targetResolution = getCurrentPerformanceLevel().targetResolution

        // 保持原始長寬比
        val originalAspectRatio = originalSize.width.toFloat() / originalSize.height.toFloat()
        val targetAspectRatio = targetResolution.width.toFloat() / targetResolution.height.toFloat()

        return if (originalAspectRatio > targetAspectRatio) {
            // 原始圖像較寬，以寬度為基準
            val adjustedHeight = (targetResolution.width / originalAspectRatio).toInt()
            Size(targetResolution.width, adjustedHeight)
        } else {
            // 原始圖像較高，以高度為基準
            val adjustedWidth = (targetResolution.height * originalAspectRatio).toInt()
            Size(adjustedWidth, targetResolution.height)
        }
    }

    /**
     * 限制最大偵測人數
     */
    fun limitMaxDetectedPoses(detectedPoses: Int): Int {
        val maxPoses = getCurrentPerformanceLevel().maxDetectedPoses
        return min(detectedPoses, maxPoses)
    }

    /**
     * 獲取效能建議
     */
    fun getPerformanceRecommendations(): List<String> {
        val currentLevel = getCurrentPerformanceLevel()
        val stats = performanceMetrics.getAllPerformanceStats()
        val recommendations = mutableListOf<String>()

        // 基於當前等級的建議
        when (currentLevel.level) {
            Level.HIGH_QUALITY -> {
                recommendations.add("目前使用高品質模式，如遇到延遲可考慮降級")
            }
            Level.BALANCED -> {
                recommendations.add("目前使用平衡模式，提供準確度與效能的良好平衡")
            }
            Level.PERFORMANCE -> {
                recommendations.add("目前使用效能模式，優先保證流暢度")
                recommendations.add("若效能仍不足，可考慮啟用省電模式")
            }
            Level.LOW_POWER -> {
                recommendations.add("目前使用省電模式，已是最低資源消耗設定")
                recommendations.add("若需要更好的準確度，請升級硬體或關閉其他應用")
            }
        }

        // 基於效能統計的建議
        stats["pose_inference"]?.let { inferenceStats ->
            if (inferenceStats.averageMs > PerformanceMetrics.WARNING_INFERENCE_TIME_MS) {
                recommendations.add("推論時間偏高 (${"%.1f".format(inferenceStats.averageMs)}ms)，建議:")
                recommendations.add("  • 降低輸入解析度")
                recommendations.add("  • 啟用幀跳躍策略")
                recommendations.add("  • 限制最大偵測人數")
            }
        }

        return recommendations
    }

    /**
     * 重置效能策略
     */
    fun reset() {
        frameCounter = 0
        recentPerformanceChecks.clear()
        lastLevelChangeTime = 0L
        _currentLevel.value = Level.BALANCED
        Timber.d("效能降級策略已重置")
    }

    /**
     * 獲取當前狀態報告
     */
    fun getStatusReport(): String {
        val currentLevel = getCurrentPerformanceLevel()
        val stats = performanceMetrics.getAllPerformanceStats()

        return buildString {
            appendLine("=== 效能降級策略狀態 ===")
            appendLine("當前等級: ${currentLevel.level.name}")
            appendLine("描述: ${currentLevel.description}")
            appendLine("目標解析度: ${currentLevel.targetResolution}")
            appendLine("幀跳躍比例: ${currentLevel.frameSkipRatio}")
            appendLine("最大偵測人數: ${currentLevel.maxDetectedPoses}")
            appendLine("自動最佳化: ${if (_isAutoOptimizationEnabled.value) "啟用" else "停用"}")
            appendLine()

            stats["pose_inference"]?.let { inferenceStats ->
                appendLine("推論效能:")
                appendLine("  平均: ${"%.1f".format(inferenceStats.averageMs)}ms")
                appendLine("  P95: ${"%.1f".format(inferenceStats.p95Ms)}ms")
            }

            stats["end_to_end"]?.let { e2eStats ->
                appendLine("端到端效能:")
                appendLine("  平均: ${"%.1f".format(e2eStats.averageMs)}ms")
                appendLine("  P95: ${"%.1f".format(e2eStats.p95Ms)}ms")
            }
        }
    }
}