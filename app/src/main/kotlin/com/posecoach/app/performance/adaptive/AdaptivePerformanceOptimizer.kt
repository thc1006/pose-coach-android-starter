package com.posecoach.app.performance.adaptive

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import com.posecoach.app.performance.PerformanceMetrics
import com.posecoach.app.performance.PerformanceDegradationStrategy
import kotlin.math.*

/**
 * Advanced Adaptive Performance Optimizer - Main engine that coordinates all optimization systems
 */
class AdaptivePerformanceOptimizer(
    private val context: Context,
    private val performanceMetrics: PerformanceMetrics,
    private val degradationStrategy: PerformanceDegradationStrategy,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    data class OptimizationState(
        val timestamp: Long,
        val currentMode: OptimizationMode,
        val activeStrategies: Set<String>,
        val performanceScore: Float, // 0.0 to 1.0
        val efficiency: Float, // Performance per resource unit
        val batteryImpact: Float, // Estimated battery drain rate
        val userSatisfaction: Float, // Predicted user satisfaction
        val systemStability: Float, // System stability score
        val adaptationReason: String
    )

    enum class OptimizationMode {
        PERFORMANCE_FIRST,    // Maximum performance regardless of resources
        BALANCED,            // Balance between performance and efficiency
        EFFICIENCY_FIRST,    // Maximum efficiency with acceptable performance
        BATTERY_SAVER,       // Maximum battery life
        ADAPTIVE,            // AI-driven dynamic adaptation
        USER_DEFINED         // Custom user preferences
    }

    data class OptimizationRule(
        val id: String,
        val condition: (OptimizationContext) -> Boolean,
        val action: suspend (OptimizationContext) -> OptimizationResult,
        val priority: Int,
        val cooldownMs: Long = 5000L,
        val description: String
    )

    data class OptimizationContext(
        val currentMetrics: PerformanceMetrics.FrameMetrics,
        val historicalMetrics: List<PerformanceMetrics.FrameMetrics>,
        val systemResources: SystemResources,
        val userPreferences: UserPreferences,
        val environmentalFactors: EnvironmentalFactors,
        val predictionData: PredictiveResourceManager.ResourcePrediction?
    )

    data class SystemResources(
        val cpuUsage: Float,
        val memoryUsage: Float,
        val batteryLevel: Float,
        val thermalState: Int,
        val networkBandwidth: Float,
        val availableStorage: Long
    )

    data class UserPreferences(
        val preferredQuality: Int, // 0-100
        val batteryPriority: Int,  // 0-100
        val performancePriority: Int, // 0-100
        val adaptationSensitivity: Float, // 0.0-1.0
        val allowBackgroundOptimization: Boolean
    )

    data class EnvironmentalFactors(
        val timeOfDay: Int,
        val isCharging: Boolean,
        val isInBackground: Boolean,
        val screenBrightness: Float,
        val ambientLight: Float,
        val deviceOrientation: String
    )

    data class OptimizationResult(
        val success: Boolean,
        val changes: Map<String, Any>,
        val estimatedImprovement: Float,
        val resourceCost: Float,
        val description: String,
        val revertible: Boolean = true
    )

    // Core components
    private val predictiveResourceManager = PredictiveResourceManager(context, scope)
    private val performancePredictionModels = PerformancePredictionModels()
    private val dynamicQualityManager = DynamicQualityManager()
    private val intelligentCacheManager = IntelligentCacheManager(context)

    // State management
    private val _optimizationState = MutableStateFlow(
        OptimizationState(
            timestamp = SystemClock.elapsedRealtime(),
            currentMode = OptimizationMode.BALANCED,
            activeStrategies = emptySet(),
            performanceScore = 0.5f,
            efficiency = 0.5f,
            batteryImpact = 0.3f,
            userSatisfaction = 0.7f,
            systemStability = 0.8f,
            adaptationReason = "Initial state"
        )
    )
    val optimizationState: StateFlow<OptimizationState> = _optimizationState.asStateFlow()

    private val _performanceImprovements = MutableSharedFlow<PerformanceImprovement>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val performanceImprovements: SharedFlow<PerformanceImprovement> = _performanceImprovements.asSharedFlow()

    data class PerformanceImprovement(
        val timestamp: Long,
        val improvement: Float, // 0.0 to 1.0
        val metric: String,
        val strategy: String,
        val beforeValue: Float,
        val afterValue: Float,
        val confidence: Float
    )

    // Optimization rules
    private val optimizationRules = mutableListOf<OptimizationRule>()
    private val lastRuleExecution = mutableMapOf<String, Long>()

    // User preferences
    private var userPreferences = UserPreferences(
        preferredQuality = 70,
        batteryPriority = 30,
        performancePriority = 70,
        adaptationSensitivity = 0.5f,
        allowBackgroundOptimization = true
    )

    companion object {
        private const val OPTIMIZATION_INTERVAL_MS = 2000L
        private const val METRICS_WINDOW_SIZE = 30
        private const val PERFORMANCE_THRESHOLD = 0.6f
        private const val EFFICIENCY_THRESHOLD = 0.5f
        private const val STABILITY_THRESHOLD = 0.7f
    }

    init {
        setupOptimizationRules()
        startOptimizationEngine()
        observePerformanceMetrics()
        observePredictions()
    }

    private fun setupOptimizationRules() {
        // Rule 1: High CPU usage optimization
        optimizationRules.add(OptimizationRule(
            id = "high_cpu_optimization",
            condition = { context ->
                context.systemResources.cpuUsage > 0.8f &&
                context.currentMetrics.inferenceTimeMs > PerformanceMetrics.WARNING_INFERENCE_TIME_MS
            },
            action = { context ->
                optimizeCpuUsage(context)
            },
            priority = 10,
            description = "Optimize when CPU usage is high"
        ))

        // Rule 2: Memory pressure relief
        optimizationRules.add(OptimizationRule(
            id = "memory_pressure_relief",
            condition = { context ->
                context.systemResources.memoryUsage > 0.85f
            },
            action = { context ->
                relieveMemoryPressure(context)
            },
            priority = 9,
            description = "Relief memory pressure"
        ))

        // Rule 3: Battery optimization
        optimizationRules.add(OptimizationRule(
            id = "battery_optimization",
            condition = { context ->
                context.systemResources.batteryLevel < 20f &&
                !context.environmentalFactors.isCharging &&
                context.userPreferences.batteryPriority > 60
            },
            action = { context ->
                optimizeBatteryUsage(context)
            },
            priority = 8,
            description = "Optimize for battery life"
        ))

        // Rule 4: Thermal throttling prevention
        optimizationRules.add(OptimizationRule(
            id = "thermal_management",
            condition = { context ->
                context.systemResources.thermalState >= 3
            },
            action = { context ->
                manageThermalLoad(context)
            },
            priority = 11,
            description = "Prevent thermal throttling"
        ))

        // Rule 5: Quality enhancement opportunity
        optimizationRules.add(OptimizationRule(
            id = "quality_enhancement",
            condition = { context ->
                context.systemResources.cpuUsage < 0.4f &&
                context.systemResources.memoryUsage < 0.6f &&
                context.currentMetrics.inferenceTimeMs < PerformanceMetrics.TARGET_INFERENCE_TIME_MS &&
                context.userPreferences.preferredQuality > 80
            },
            action = { context ->
                enhanceQuality(context)
            },
            priority = 3,
            description = "Enhance quality when resources allow"
        ))

        // Rule 6: Predictive optimization
        optimizationRules.add(OptimizationRule(
            id = "predictive_optimization",
            condition = { context ->
                context.predictionData?.confidence ?: 0f > 0.7f &&
                context.predictionData?.recommendedQualityLevel != null
            },
            action = { context ->
                applyPredictiveOptimization(context)
            },
            priority = 6,
            description = "Apply ML-based predictive optimizations"
        ))

        // Rule 7: Network-aware optimization
        optimizationRules.add(OptimizationRule(
            id = "network_optimization",
            condition = { context ->
                context.systemResources.networkBandwidth < 1.0f // Low bandwidth
            },
            action = { context ->
                optimizeForLowBandwidth(context)
            },
            priority = 5,
            description = "Optimize for low network bandwidth"
        ))

        // Rule 8: Background optimization
        optimizationRules.add(OptimizationRule(
            id = "background_optimization",
            condition = { context ->
                context.environmentalFactors.isInBackground &&
                context.userPreferences.allowBackgroundOptimization
            },
            action = { context ->
                optimizeForBackground(context)
            },
            priority = 4,
            description = "Optimize when app is in background"
        ))
    }

    private fun startOptimizationEngine() {
        scope.launch {
            while (isActive) {
                try {
                    runOptimizationCycle()
                    delay(OPTIMIZATION_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in optimization engine")
                    delay(OPTIMIZATION_INTERVAL_MS * 2)
                }
            }
        }
    }

    private suspend fun runOptimizationCycle() {
        val context = buildOptimizationContext()
        val applicableRules = findApplicableRules(context)

        if (applicableRules.isNotEmpty()) {
            // Sort by priority and execute
            val sortedRules = applicableRules.sortedByDescending { it.priority }

            for (rule in sortedRules) {
                if (canExecuteRule(rule)) {
                    try {
                        val result = rule.action(context)
                        processOptimizationResult(rule, result)
                        lastRuleExecution[rule.id] = SystemClock.elapsedRealtime()

                        if (result.success) {
                            Timber.d("Applied optimization rule: ${rule.description}")
                            // Add some delay between rule executions
                            delay(500)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error executing optimization rule: ${rule.id}")
                    }
                }
            }
        }

        updateOptimizationState(context)
    }

    private suspend fun buildOptimizationContext(): OptimizationContext {
        // Get current metrics
        val currentFrameMetrics = getCurrentFrameMetrics()
        val historicalMetrics = getHistoricalFrameMetrics()

        // Get system resources
        val systemResources = getCurrentSystemResources()

        // Get environmental factors
        val environmentalFactors = getCurrentEnvironmentalFactors()

        // Get prediction data
        val predictionData = predictiveResourceManager.getLatestPrediction()

        return OptimizationContext(
            currentMetrics = currentFrameMetrics,
            historicalMetrics = historicalMetrics,
            systemResources = systemResources,
            userPreferences = userPreferences,
            environmentalFactors = environmentalFactors,
            predictionData = predictionData
        )
    }

    private fun findApplicableRules(context: OptimizationContext): List<OptimizationRule> {
        return optimizationRules.filter { rule ->
            try {
                rule.condition(context)
            } catch (e: Exception) {
                Timber.e(e, "Error evaluating rule condition: ${rule.id}")
                false
            }
        }
    }

    private fun canExecuteRule(rule: OptimizationRule): Boolean {
        val lastExecution = lastRuleExecution[rule.id] ?: 0L
        val currentTime = SystemClock.elapsedRealtime()
        return (currentTime - lastExecution) >= rule.cooldownMs
    }

    private suspend fun optimizeCpuUsage(context: OptimizationContext): OptimizationResult {
        val changes = mutableMapOf<String, Any>()
        var estimatedImprovement = 0f

        // Reduce input resolution
        val currentLevel = degradationStrategy.getCurrentPerformanceLevel()
        if (currentLevel.level != PerformanceDegradationStrategy.Level.PERFORMANCE) {
            degradationStrategy.setPerformanceLevel(PerformanceDegradationStrategy.Level.PERFORMANCE)
            changes["performance_level"] = "PERFORMANCE"
            estimatedImprovement += 0.3f
        }

        // Enable frame skipping
        dynamicQualityManager.enableAdaptiveFrameSkipping(true)
        changes["frame_skipping"] = true
        estimatedImprovement += 0.2f

        // Switch to CPU delegate if using GPU
        if (shouldSwitchToCpu(context)) {
            changes["delegate"] = "CPU"
            estimatedImprovement += 0.15f
        }

        return OptimizationResult(
            success = changes.isNotEmpty(),
            changes = changes,
            estimatedImprovement = estimatedImprovement,
            resourceCost = 0.1f,
            description = "Optimized for high CPU usage",
            revertible = true
        )
    }

    private suspend fun relieveMemoryPressure(context: OptimizationContext): OptimizationResult {
        val changes = mutableMapOf<String, Any>()
        var estimatedImprovement = 0f

        // Clear caches
        intelligentCacheManager.clearLowPriorityCache()
        changes["cache_cleared"] = true
        estimatedImprovement += 0.25f

        // Reduce maximum detected poses
        val currentLevel = degradationStrategy.getCurrentPerformanceLevel()
        if (currentLevel.maxDetectedPoses > 1) {
            degradationStrategy.setPerformanceLevel(PerformanceDegradationStrategy.Level.LOW_POWER)
            changes["max_poses"] = 1
            estimatedImprovement += 0.2f
        }

        // Reduce image quality
        dynamicQualityManager.setImageQuality(0.7f)
        changes["image_quality"] = 0.7f
        estimatedImprovement += 0.15f

        return OptimizationResult(
            success = changes.isNotEmpty(),
            changes = changes,
            estimatedImprovement = estimatedImprovement,
            resourceCost = 0.05f,
            description = "Relieved memory pressure",
            revertible = true
        )
    }

    private suspend fun optimizeBatteryUsage(context: OptimizationContext): OptimizationResult {
        val changes = mutableMapOf<String, Any>()
        var estimatedImprovement = 0f

        // Switch to most efficient performance level
        degradationStrategy.setPerformanceLevel(PerformanceDegradationStrategy.Level.LOW_POWER)
        changes["performance_level"] = "LOW_POWER"
        estimatedImprovement += 0.4f

        // Reduce processing frequency
        dynamicQualityManager.setProcessingFrequency(0.5f)
        changes["processing_frequency"] = 0.5f
        estimatedImprovement += 0.3f

        // Disable GPU acceleration
        changes["gpu_delegate"] = false
        estimatedImprovement += 0.2f

        // Reduce overlay complexity
        changes["overlay_complexity"] = "minimal"
        estimatedImprovement += 0.1f

        return OptimizationResult(
            success = true,
            changes = changes,
            estimatedImprovement = estimatedImprovement,
            resourceCost = 0.6f, // High resource cost due to quality reduction
            description = "Optimized for battery life",
            revertible = true
        )
    }

    private suspend fun manageThermalLoad(context: OptimizationContext): OptimizationResult {
        val changes = mutableMapOf<String, Any>()
        var estimatedImprovement = 0f

        // Immediate thermal relief
        degradationStrategy.setPerformanceLevel(PerformanceDegradationStrategy.Level.LOW_POWER)
        changes["thermal_mode"] = "enabled"
        estimatedImprovement += 0.5f

        // Reduce processing intensity
        dynamicQualityManager.enableThermalThrottling(true)
        changes["thermal_throttling"] = true
        estimatedImprovement += 0.3f

        // Add processing delays
        changes["processing_delay"] = 100L // ms
        estimatedImprovement += 0.2f

        return OptimizationResult(
            success = true,
            changes = changes,
            estimatedImprovement = estimatedImprovement,
            resourceCost = 0.4f,
            description = "Applied thermal management",
            revertible = true
        )
    }

    private suspend fun enhanceQuality(context: OptimizationContext): OptimizationResult {
        val changes = mutableMapOf<String, Any>()
        var estimatedImprovement = 0f

        // Increase performance level if resources allow
        val currentLevel = degradationStrategy.getCurrentPerformanceLevel()
        if (currentLevel.level != PerformanceDegradationStrategy.Level.HIGH_QUALITY) {
            degradationStrategy.setPerformanceLevel(PerformanceDegradationStrategy.Level.HIGH_QUALITY)
            changes["performance_level"] = "HIGH_QUALITY"
            estimatedImprovement += 0.3f
        }

        // Increase image quality
        dynamicQualityManager.setImageQuality(1.0f)
        changes["image_quality"] = 1.0f
        estimatedImprovement += 0.2f

        // Enable additional features
        changes["enhanced_features"] = true
        estimatedImprovement += 0.15f

        return OptimizationResult(
            success = changes.isNotEmpty(),
            changes = changes,
            estimatedImprovement = estimatedImprovement,
            resourceCost = 0.3f,
            description = "Enhanced quality settings",
            revertible = true
        )
    }

    private suspend fun applyPredictiveOptimization(context: OptimizationContext): OptimizationResult {
        val prediction = context.predictionData ?: return OptimizationResult(
            false, emptyMap(), 0f, 0f, "No prediction data available"
        )

        val changes = mutableMapOf<String, Any>()
        var estimatedImprovement = 0f

        // Apply ML-recommended quality level
        val recommendedLevel = when (prediction.recommendedQualityLevel) {
            PredictiveResourceManager.QualityLevel.ULTRA_HIGH -> PerformanceDegradationStrategy.Level.HIGH_QUALITY
            PredictiveResourceManager.QualityLevel.HIGH -> PerformanceDegradationStrategy.Level.HIGH_QUALITY
            PredictiveResourceManager.QualityLevel.MEDIUM -> PerformanceDegradationStrategy.Level.BALANCED
            PredictiveResourceManager.QualityLevel.LOW -> PerformanceDegradationStrategy.Level.PERFORMANCE
            PredictiveResourceManager.QualityLevel.ULTRA_LOW -> PerformanceDegradationStrategy.Level.LOW_POWER
        }

        degradationStrategy.setPerformanceLevel(recommendedLevel)
        changes["ml_recommended_level"] = recommendedLevel.name
        estimatedImprovement += prediction.confidence * 0.4f

        // Preload resources based on predictions
        if (prediction.confidence > 0.8f) {
            intelligentCacheManager.preloadPredictedResources(prediction)
            changes["predictive_preload"] = true
            estimatedImprovement += 0.2f
        }

        return OptimizationResult(
            success = changes.isNotEmpty(),
            changes = changes,
            estimatedImprovement = estimatedImprovement,
            resourceCost = 0.1f,
            description = "Applied ML-based predictive optimization (confidence: ${prediction.confidence})",
            revertible = true
        )
    }

    private suspend fun optimizeForLowBandwidth(context: OptimizationContext): OptimizationResult {
        val changes = mutableMapOf<String, Any>()
        var estimatedImprovement = 0f

        // Reduce network-dependent features
        changes["cloud_processing"] = false
        estimatedImprovement += 0.3f

        // Enable local processing optimizations
        changes["local_processing"] = true
        estimatedImprovement += 0.2f

        return OptimizationResult(
            success = true,
            changes = changes,
            estimatedImprovement = estimatedImprovement,
            resourceCost = 0.1f,
            description = "Optimized for low bandwidth",
            revertible = true
        )
    }

    private suspend fun optimizeForBackground(context: OptimizationContext): OptimizationResult {
        val changes = mutableMapOf<String, Any>()
        var estimatedImprovement = 0f

        // Reduce processing when in background
        dynamicQualityManager.setProcessingFrequency(0.2f)
        changes["background_processing"] = 0.2f
        estimatedImprovement += 0.5f

        // Disable non-essential features
        changes["essential_only"] = true
        estimatedImprovement += 0.3f

        return OptimizationResult(
            success = true,
            changes = changes,
            estimatedImprovement = estimatedImprovement,
            resourceCost = 0.1f,
            description = "Optimized for background operation",
            revertible = true
        )
    }

    private fun processOptimizationResult(rule: OptimizationRule, result: OptimizationResult) {
        if (result.success) {
            val improvement = PerformanceImprovement(
                timestamp = SystemClock.elapsedRealtime(),
                improvement = result.estimatedImprovement,
                metric = "overall_performance",
                strategy = rule.id,
                beforeValue = _optimizationState.value.performanceScore,
                afterValue = _optimizationState.value.performanceScore + result.estimatedImprovement,
                confidence = 0.8f // Rule-based confidence
            )

            _performanceImprovements.tryEmit(improvement)
        }
    }

    private fun updateOptimizationState(context: OptimizationContext) {
        val currentState = _optimizationState.value

        // Calculate performance score based on current metrics
        val performanceScore = calculatePerformanceScore(context)
        val efficiency = calculateEfficiency(context)
        val batteryImpact = calculateBatteryImpact(context)
        val userSatisfaction = calculateUserSatisfaction(context, performanceScore)
        val systemStability = calculateSystemStability(context)

        val newState = currentState.copy(
            timestamp = SystemClock.elapsedRealtime(),
            performanceScore = performanceScore,
            efficiency = efficiency,
            batteryImpact = batteryImpact,
            userSatisfaction = userSatisfaction,
            systemStability = systemStability,
            adaptationReason = "Continuous optimization"
        )

        _optimizationState.value = newState
    }

    private fun calculatePerformanceScore(context: OptimizationContext): Float {
        val inferenceScore = if (context.currentMetrics.inferenceTimeMs <= PerformanceMetrics.TARGET_INFERENCE_TIME_MS) {
            1.0f
        } else {
            (PerformanceMetrics.CRITICAL_INFERENCE_TIME_MS - context.currentMetrics.inferenceTimeMs) /
            (PerformanceMetrics.CRITICAL_INFERENCE_TIME_MS - PerformanceMetrics.TARGET_INFERENCE_TIME_MS)
        }.coerceIn(0f, 1f)

        val resourceScore = 1f - ((context.systemResources.cpuUsage + context.systemResources.memoryUsage) / 2f)

        return (inferenceScore * 0.6f + resourceScore * 0.4f).coerceIn(0f, 1f)
    }

    private fun calculateEfficiency(context: OptimizationContext): Float {
        val performanceScore = calculatePerformanceScore(context)
        val resourceUsage = (context.systemResources.cpuUsage + context.systemResources.memoryUsage) / 2f

        return if (resourceUsage > 0f) {
            (performanceScore / resourceUsage).coerceIn(0f, 2f) / 2f
        } else {
            1f
        }
    }

    private fun calculateBatteryImpact(context: OptimizationContext): Float {
        val baseImpact = (context.systemResources.cpuUsage * 0.4f +
                         context.systemResources.thermalState / 4f * 0.3f +
                         if (context.environmentalFactors.isCharging) 0f else 0.3f)

        return baseImpact.coerceIn(0f, 1f)
    }

    private fun calculateUserSatisfaction(context: OptimizationContext, performanceScore: Float): Float {
        val qualityScore = performanceScore
        val responsiveness = if (context.currentMetrics.inferenceTimeMs <= 50f) 1f else 0.5f
        val stabilityScore = if (context.historicalMetrics.size >= 10) {
            val variance = context.historicalMetrics.map { it.inferenceTimeMs }.let { times ->
                val mean = times.average()
                times.map { (it - mean).pow(2) }.average()
            }
            max(0f, 1f - (variance / 1000f).toFloat())
        } else 0.7f

        return (qualityScore * 0.4f + responsiveness * 0.3f + stabilityScore * 0.3f).coerceIn(0f, 1f)
    }

    private fun calculateSystemStability(context: OptimizationContext): Float {
        val thermalStability = 1f - (context.systemResources.thermalState / 4f)
        val memoryStability = 1f - max(0f, context.systemResources.memoryUsage - 0.8f) * 5f
        val performanceStability = if (context.historicalMetrics.size >= 5) {
            val recentTimes = context.historicalMetrics.takeLast(5).map { it.inferenceTimeMs }
            val variance = recentTimes.let { times ->
                val mean = times.average()
                times.map { (it - mean).pow(2) }.average()
            }
            max(0f, 1f - (variance / 500f).toFloat())
        } else 0.8f

        return (thermalStability * 0.3f + memoryStability * 0.3f + performanceStability * 0.4f).coerceIn(0f, 1f)
    }

    // Helper methods for gathering context data
    private fun getCurrentFrameMetrics(): PerformanceMetrics.FrameMetrics {
        // This would integrate with actual performance metrics
        return PerformanceMetrics.FrameMetrics(
            frameIndex = 0L,
            inferenceTimeMs = 30.0,
            preprocessTimeMs = 5.0,
            postprocessTimeMs = 5.0,
            endToEndTimeMs = 40.0,
            inputWidth = 640,
            inputHeight = 480,
            numDetectedPoses = 1
        )
    }

    private fun getHistoricalFrameMetrics(): List<PerformanceMetrics.FrameMetrics> {
        // This would integrate with metrics history
        return emptyList()
    }

    private fun getCurrentSystemResources(): SystemResources {
        // This would integrate with actual system monitoring
        return SystemResources(
            cpuUsage = 0.5f,
            memoryUsage = 0.6f,
            batteryLevel = 75f,
            thermalState = 1,
            networkBandwidth = 10f,
            availableStorage = 1000000L
        )
    }

    private fun getCurrentEnvironmentalFactors(): EnvironmentalFactors {
        // This would integrate with actual environment monitoring
        return EnvironmentalFactors(
            timeOfDay = 14,
            isCharging = false,
            isInBackground = false,
            screenBrightness = 0.7f,
            ambientLight = 0.5f,
            deviceOrientation = "portrait"
        )
    }

    private fun shouldSwitchToCpu(context: OptimizationContext): Boolean {
        return context.systemResources.thermalState >= 2 ||
               context.systemResources.cpuUsage > 0.9f
    }

    private fun observePerformanceMetrics() {
        scope.launch {
            performanceMetrics.frameMetrics.collect { frameMetrics ->
                // Process frame metrics for optimization decisions
                processFrameMetrics(frameMetrics)
            }
        }
    }

    private fun observePredictions() {
        scope.launch {
            predictiveResourceManager.resourcePredictions.collect { prediction ->
                // Use predictions for proactive optimization
                processPrediction(prediction)
            }
        }
    }

    private fun processFrameMetrics(frameMetrics: PerformanceMetrics.FrameMetrics) {
        // This would trigger optimization rules based on frame metrics
        Timber.d("Processing frame metrics: inference=${frameMetrics.inferenceTimeMs}ms")
    }

    private fun processPrediction(prediction: PredictiveResourceManager.ResourcePrediction) {
        // This would trigger predictive optimizations
        Timber.d("Processing prediction: confidence=${prediction.confidence}, quality=${prediction.recommendedQualityLevel}")
    }

    // Public API methods
    fun setOptimizationMode(mode: OptimizationMode) {
        val currentState = _optimizationState.value
        _optimizationState.value = currentState.copy(
            currentMode = mode,
            adaptationReason = "User mode change to $mode"
        )
        Timber.i("Optimization mode changed to: $mode")
    }

    fun setUserPreferences(preferences: UserPreferences) {
        userPreferences = preferences
        Timber.i("User preferences updated")
    }

    fun addCustomRule(rule: OptimizationRule) {
        optimizationRules.add(rule)
        Timber.i("Custom optimization rule added: ${rule.id}")
    }

    fun removeRule(ruleId: String) {
        optimizationRules.removeAll { it.id == ruleId }
        lastRuleExecution.remove(ruleId)
        Timber.i("Optimization rule removed: $ruleId")
    }

    fun getOptimizationSummary(): Map<String, Any> {
        val state = _optimizationState.value
        return mapOf(
            "mode" to state.currentMode.name,
            "performance_score" to state.performanceScore,
            "efficiency" to state.efficiency,
            "battery_impact" to state.batteryImpact,
            "user_satisfaction" to state.userSatisfaction,
            "system_stability" to state.systemStability,
            "active_strategies" to state.activeStrategies.toList(),
            "rules_count" to optimizationRules.size,
            "last_optimization" to state.timestamp
        )
    }

    fun shutdown() {
        scope.cancel()
        predictiveResourceManager.shutdown()
        intelligentCacheManager.shutdown()
    }
}