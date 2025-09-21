package com.posecoach.app.performance.adaptive

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import kotlin.math.*

/**
 * Dynamic Quality Manager - Intelligently adapts quality settings based on performance and user preferences
 */
class DynamicQualityManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    data class QualitySettings(
        val imageQuality: Float, // 0.0 to 1.0
        val resolutionScale: Float, // 0.1 to 1.0
        val frameSkipRatio: Int, // 1 = no skip, 2 = skip every other frame
        val processingFrequency: Float, // 0.1 to 1.0
        val overlayComplexity: OverlayComplexity,
        val detectionAccuracy: DetectionAccuracy,
        val smoothingFactor: Float, // 0.0 to 1.0
        val enableGpuAcceleration: Boolean,
        val maxDetectedObjects: Int,
        val enableSubPixelAccuracy: Boolean
    )

    enum class OverlayComplexity {
        MINIMAL,     // Basic skeleton only
        SIMPLE,      // Skeleton + basic landmarks
        STANDARD,    // Full skeleton + landmarks + confidence
        DETAILED,    // Everything + additional visualizations
        RESEARCH     // Maximum detail for analysis
    }

    enum class DetectionAccuracy {
        FAST,        // Speed optimized
        BALANCED,    // Balance of speed and accuracy
        ACCURATE,    // Accuracy optimized
        PRECISE      // Maximum precision
    }

    data class QualityProfile(
        val name: String,
        val description: String,
        val settings: QualitySettings,
        val performanceTarget: Float, // Expected performance score
        val batteryImpact: Float,     // Expected battery impact
        val userSatisfaction: Float   // Expected user satisfaction
    )

    data class AdaptationEvent(
        val timestamp: Long,
        val fromSettings: QualitySettings,
        val toSettings: QualitySettings,
        val reason: String,
        val confidence: Float,
        val expectedImprovement: Float
    )

    companion object {
        private const val ADAPTATION_INTERVAL_MS = 1000L
        private const val STABILITY_WINDOW_SIZE = 10
        private const val MIN_ADAPTATION_THRESHOLD = 0.1f

        // Predefined quality profiles
        val QUALITY_PROFILES = mapOf(
            "ultra_low" to QualityProfile(
                name = "Ultra Low",
                description = "Minimum quality for maximum performance",
                settings = QualitySettings(
                    imageQuality = 0.3f,
                    resolutionScale = 0.4f,
                    frameSkipRatio = 4,
                    processingFrequency = 0.5f,
                    overlayComplexity = OverlayComplexity.MINIMAL,
                    detectionAccuracy = DetectionAccuracy.FAST,
                    smoothingFactor = 0.8f,
                    enableGpuAcceleration = false,
                    maxDetectedObjects = 1,
                    enableSubPixelAccuracy = false
                ),
                performanceTarget = 0.9f,
                batteryImpact = 0.1f,
                userSatisfaction = 0.3f
            ),
            "low" to QualityProfile(
                name = "Low",
                description = "Basic quality with good performance",
                settings = QualitySettings(
                    imageQuality = 0.5f,
                    resolutionScale = 0.6f,
                    frameSkipRatio = 2,
                    processingFrequency = 0.7f,
                    overlayComplexity = OverlayComplexity.SIMPLE,
                    detectionAccuracy = DetectionAccuracy.FAST,
                    smoothingFactor = 0.6f,
                    enableGpuAcceleration = true,
                    maxDetectedObjects = 2,
                    enableSubPixelAccuracy = false
                ),
                performanceTarget = 0.8f,
                batteryImpact = 0.2f,
                userSatisfaction = 0.5f
            ),
            "medium" to QualityProfile(
                name = "Medium",
                description = "Balanced quality and performance",
                settings = QualitySettings(
                    imageQuality = 0.7f,
                    resolutionScale = 0.8f,
                    frameSkipRatio = 1,
                    processingFrequency = 0.8f,
                    overlayComplexity = OverlayComplexity.STANDARD,
                    detectionAccuracy = DetectionAccuracy.BALANCED,
                    smoothingFactor = 0.4f,
                    enableGpuAcceleration = true,
                    maxDetectedObjects = 3,
                    enableSubPixelAccuracy = false
                ),
                performanceTarget = 0.7f,
                batteryImpact = 0.4f,
                userSatisfaction = 0.7f
            ),
            "high" to QualityProfile(
                name = "High",
                description = "High quality with moderate performance",
                settings = QualitySettings(
                    imageQuality = 0.9f,
                    resolutionScale = 1.0f,
                    frameSkipRatio = 1,
                    processingFrequency = 1.0f,
                    overlayComplexity = OverlayComplexity.DETAILED,
                    detectionAccuracy = DetectionAccuracy.ACCURATE,
                    smoothingFactor = 0.3f,
                    enableGpuAcceleration = true,
                    maxDetectedObjects = 5,
                    enableSubPixelAccuracy = true
                ),
                performanceTarget = 0.6f,
                batteryImpact = 0.6f,
                userSatisfaction = 0.9f
            ),
            "ultra_high" to QualityProfile(
                name = "Ultra High",
                description = "Maximum quality for research and analysis",
                settings = QualitySettings(
                    imageQuality = 1.0f,
                    resolutionScale = 1.0f,
                    frameSkipRatio = 1,
                    processingFrequency = 1.0f,
                    overlayComplexity = OverlayComplexity.RESEARCH,
                    detectionAccuracy = DetectionAccuracy.PRECISE,
                    smoothingFactor = 0.1f,
                    enableGpuAcceleration = true,
                    maxDetectedObjects = 10,
                    enableSubPixelAccuracy = true
                ),
                performanceTarget = 0.4f,
                batteryImpact = 0.8f,
                userSatisfaction = 1.0f
            )
        )
    }

    // Current state
    private val _currentSettings = MutableStateFlow(QUALITY_PROFILES["medium"]!!.settings)
    val currentSettings: StateFlow<QualitySettings> = _currentSettings.asStateFlow()

    private val _adaptationHistory = MutableSharedFlow<AdaptationEvent>(
        replay = 10,
        extraBufferCapacity = 50
    )
    val adaptationHistory: SharedFlow<AdaptationEvent> = _adaptationHistory.asSharedFlow()

    // Adaptive controls
    private var isAdaptiveFrameSkippingEnabled = false
    private var isThermalThrottlingEnabled = false
    private var adaptationSensitivity = 0.5f
    private var userQualityPreference = 0.7f

    // Performance tracking
    private val performanceHistory = mutableListOf<Float>()
    private val batteryHistory = mutableListOf<Float>()
    private val thermalHistory = mutableListOf<Int>()

    init {
        startAdaptiveQualityEngine()
    }

    private fun startAdaptiveQualityEngine() {
        scope.launch {
            while (isActive) {
                try {
                    evaluateAndAdaptQuality()
                    delay(ADAPTATION_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in adaptive quality engine")
                    delay(ADAPTATION_INTERVAL_MS * 2)
                }
            }
        }
    }

    private suspend fun evaluateAndAdaptQuality() {
        val currentPerformance = getCurrentPerformanceScore()
        val batteryLevel = getCurrentBatteryLevel()
        val thermalState = getCurrentThermalState()

        // Update history
        updateHistory(currentPerformance, batteryLevel, thermalState)

        // Check if adaptation is needed
        val adaptationDecision = shouldAdaptQuality(currentPerformance, batteryLevel, thermalState)

        if (adaptationDecision != null) {
            adaptQualitySettings(adaptationDecision)
        }
    }

    private fun updateHistory(performance: Float, battery: Float, thermal: Int) {
        performanceHistory.add(performance)
        batteryHistory.add(battery)
        thermalHistory.add(thermal)

        // Keep only recent history
        if (performanceHistory.size > STABILITY_WINDOW_SIZE) {
            performanceHistory.removeAt(0)
            batteryHistory.removeAt(0)
            thermalHistory.removeAt(0)
        }
    }

    private fun shouldAdaptQuality(
        currentPerformance: Float,
        batteryLevel: Float,
        thermalState: Int
    ): QualityAdaptationDecision? {

        // Check for immediate adaptation triggers
        if (thermalState >= 3 && isThermalThrottlingEnabled) {
            return QualityAdaptationDecision(
                direction = AdaptationDirection.DECREASE,
                urgency = Urgency.HIGH,
                reason = "Thermal throttling protection",
                targetImprovement = 0.4f
            )
        }

        if (batteryLevel < 15f && !isCharging()) {
            return QualityAdaptationDecision(
                direction = AdaptationDirection.DECREASE,
                urgency = Urgency.HIGH,
                reason = "Critical battery level",
                targetImprovement = 0.5f
            )
        }

        // Check performance stability
        if (performanceHistory.size >= STABILITY_WINDOW_SIZE) {
            val avgPerformance = performanceHistory.average().toFloat()
            val performanceVariance = calculateVariance(performanceHistory)

            // Poor performance with high variance indicates instability
            if (avgPerformance < 0.6f && performanceVariance > 0.1f) {
                return QualityAdaptationDecision(
                    direction = AdaptationDirection.DECREASE,
                    urgency = Urgency.MEDIUM,
                    reason = "Performance instability detected",
                    targetImprovement = 0.3f
                )
            }

            // Good performance with resources available
            if (avgPerformance > 0.8f && performanceVariance < 0.05f &&
                thermalState <= 1 && batteryLevel > 50f) {
                return QualityAdaptationDecision(
                    direction = AdaptationDirection.INCREASE,
                    urgency = Urgency.LOW,
                    reason = "Resources available for quality improvement",
                    targetImprovement = 0.2f
                )
            }
        }

        // Check user preference alignment
        val currentQualityScore = calculateCurrentQualityScore()
        val qualityGap = userQualityPreference - currentQualityScore

        if (abs(qualityGap) > MIN_ADAPTATION_THRESHOLD) {
            return QualityAdaptationDecision(
                direction = if (qualityGap > 0) AdaptationDirection.INCREASE else AdaptationDirection.DECREASE,
                urgency = Urgency.LOW,
                reason = "Aligning with user preference",
                targetImprovement = abs(qualityGap)
            )
        }

        return null
    }

    private data class QualityAdaptationDecision(
        val direction: AdaptationDirection,
        val urgency: Urgency,
        val reason: String,
        val targetImprovement: Float
    )

    private enum class AdaptationDirection {
        INCREASE, DECREASE
    }

    private enum class Urgency {
        LOW, MEDIUM, HIGH
    }

    private suspend fun adaptQualitySettings(decision: QualityAdaptationDecision) {
        val currentSettings = _currentSettings.value
        val newSettings = when (decision.direction) {
            AdaptationDirection.DECREASE -> degradeQualitySettings(currentSettings, decision)
            AdaptationDirection.INCREASE -> enhanceQualitySettings(currentSettings, decision)
        }

        if (newSettings != currentSettings) {
            val adaptationEvent = AdaptationEvent(
                timestamp = SystemClock.elapsedRealtime(),
                fromSettings = currentSettings,
                toSettings = newSettings,
                reason = decision.reason,
                confidence = calculateAdaptationConfidence(decision),
                expectedImprovement = decision.targetImprovement
            )

            _currentSettings.value = newSettings
            _adaptationHistory.tryEmit(adaptationEvent)

            Timber.i("Quality adapted: ${decision.reason} (${decision.direction.name})")
            logQualityChange(currentSettings, newSettings)
        }
    }

    private fun degradeQualitySettings(
        current: QualitySettings,
        decision: QualityAdaptationDecision
    ): QualitySettings {
        val degradationFactor = when (decision.urgency) {
            Urgency.HIGH -> 0.7f
            Urgency.MEDIUM -> 0.85f
            Urgency.LOW -> 0.95f
        }

        return current.copy(
            imageQuality = (current.imageQuality * degradationFactor).coerceAtLeast(0.1f),
            resolutionScale = (current.resolutionScale * degradationFactor).coerceAtLeast(0.3f),
            frameSkipRatio = if (isAdaptiveFrameSkippingEnabled) {
                min(current.frameSkipRatio + 1, 4)
            } else current.frameSkipRatio,
            processingFrequency = (current.processingFrequency * degradationFactor).coerceAtLeast(0.1f),
            overlayComplexity = degradeOverlayComplexity(current.overlayComplexity),
            detectionAccuracy = degradeDetectionAccuracy(current.detectionAccuracy),
            smoothingFactor = min(current.smoothingFactor + 0.1f, 1.0f),
            enableGpuAcceleration = if (decision.urgency == Urgency.HIGH) false else current.enableGpuAcceleration,
            maxDetectedObjects = max(current.maxDetectedObjects - 1, 1),
            enableSubPixelAccuracy = if (decision.urgency >= Urgency.MEDIUM) false else current.enableSubPixelAccuracy
        )
    }

    private fun enhanceQualitySettings(
        current: QualitySettings,
        decision: QualityAdaptationDecision
    ): QualitySettings {
        val enhancementFactor = when (decision.urgency) {
            Urgency.HIGH -> 1.3f
            Urgency.MEDIUM -> 1.15f
            Urgency.LOW -> 1.05f
        }

        return current.copy(
            imageQuality = (current.imageQuality * enhancementFactor).coerceAtMost(1.0f),
            resolutionScale = (current.resolutionScale * enhancementFactor).coerceAtMost(1.0f),
            frameSkipRatio = if (isAdaptiveFrameSkippingEnabled) {
                max(current.frameSkipRatio - 1, 1)
            } else current.frameSkipRatio,
            processingFrequency = (current.processingFrequency * enhancementFactor).coerceAtMost(1.0f),
            overlayComplexity = enhanceOverlayComplexity(current.overlayComplexity),
            detectionAccuracy = enhanceDetectionAccuracy(current.detectionAccuracy),
            smoothingFactor = max(current.smoothingFactor - 0.1f, 0.0f),
            enableGpuAcceleration = true,
            maxDetectedObjects = min(current.maxDetectedObjects + 1, 10),
            enableSubPixelAccuracy = true
        )
    }

    private fun degradeOverlayComplexity(current: OverlayComplexity): OverlayComplexity {
        return when (current) {
            OverlayComplexity.RESEARCH -> OverlayComplexity.DETAILED
            OverlayComplexity.DETAILED -> OverlayComplexity.STANDARD
            OverlayComplexity.STANDARD -> OverlayComplexity.SIMPLE
            OverlayComplexity.SIMPLE -> OverlayComplexity.MINIMAL
            OverlayComplexity.MINIMAL -> OverlayComplexity.MINIMAL
        }
    }

    private fun enhanceOverlayComplexity(current: OverlayComplexity): OverlayComplexity {
        return when (current) {
            OverlayComplexity.MINIMAL -> OverlayComplexity.SIMPLE
            OverlayComplexity.SIMPLE -> OverlayComplexity.STANDARD
            OverlayComplexity.STANDARD -> OverlayComplexity.DETAILED
            OverlayComplexity.DETAILED -> OverlayComplexity.RESEARCH
            OverlayComplexity.RESEARCH -> OverlayComplexity.RESEARCH
        }
    }

    private fun degradeDetectionAccuracy(current: DetectionAccuracy): DetectionAccuracy {
        return when (current) {
            DetectionAccuracy.PRECISE -> DetectionAccuracy.ACCURATE
            DetectionAccuracy.ACCURATE -> DetectionAccuracy.BALANCED
            DetectionAccuracy.BALANCED -> DetectionAccuracy.FAST
            DetectionAccuracy.FAST -> DetectionAccuracy.FAST
        }
    }

    private fun enhanceDetectionAccuracy(current: DetectionAccuracy): DetectionAccuracy {
        return when (current) {
            DetectionAccuracy.FAST -> DetectionAccuracy.BALANCED
            DetectionAccuracy.BALANCED -> DetectionAccuracy.ACCURATE
            DetectionAccuracy.ACCURATE -> DetectionAccuracy.PRECISE
            DetectionAccuracy.PRECISE -> DetectionAccuracy.PRECISE
        }
    }

    private fun calculateAdaptationConfidence(decision: QualityAdaptationDecision): Float {
        val baseConfidence = when (decision.urgency) {
            Urgency.HIGH -> 0.9f
            Urgency.MEDIUM -> 0.7f
            Urgency.LOW -> 0.5f
        }

        val historyConfidence = if (performanceHistory.size >= STABILITY_WINDOW_SIZE) {
            val variance = calculateVariance(performanceHistory)
            1f - min(variance * 2f, 0.5f)
        } else 0.5f

        return (baseConfidence + historyConfidence) / 2f
    }

    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average().toFloat()
    }

    private fun calculateCurrentQualityScore(): Float {
        val settings = _currentSettings.value
        return (settings.imageQuality * 0.25f +
                settings.resolutionScale * 0.25f +
                (1f / settings.frameSkipRatio) * 0.2f +
                settings.processingFrequency * 0.15f +
                settings.overlayComplexity.ordinal / 4f * 0.1f +
                if (settings.enableSubPixelAccuracy) 0.05f else 0f)
    }

    private fun logQualityChange(from: QualitySettings, to: QualitySettings) {
        Timber.d("""
            Quality Settings Changed:
            Image Quality: ${from.imageQuality} -> ${to.imageQuality}
            Resolution Scale: ${from.resolutionScale} -> ${to.resolutionScale}
            Frame Skip: ${from.frameSkipRatio} -> ${to.frameSkipRatio}
            Processing Freq: ${from.processingFrequency} -> ${to.processingFrequency}
            Overlay: ${from.overlayComplexity} -> ${to.overlayComplexity}
            Detection: ${from.detectionAccuracy} -> ${to.detectionAccuracy}
            GPU: ${from.enableGpuAcceleration} -> ${to.enableGpuAcceleration}
            Max Objects: ${from.maxDetectedObjects} -> ${to.maxDetectedObjects}
        """.trimIndent())
    }

    // Helper methods for system state
    private fun getCurrentPerformanceScore(): Float {
        // This would integrate with actual performance monitoring
        return 0.7f // Placeholder
    }

    private fun getCurrentBatteryLevel(): Float {
        // This would integrate with actual battery monitoring
        return 75f // Placeholder
    }

    private fun getCurrentThermalState(): Int {
        // This would integrate with actual thermal monitoring
        return 1 // Placeholder
    }

    private fun isCharging(): Boolean {
        // This would integrate with actual charging state
        return false // Placeholder
    }

    // Public API methods
    fun setQualityProfile(profileName: String) {
        val profile = QUALITY_PROFILES[profileName]
        if (profile != null) {
            _currentSettings.value = profile.settings
            Timber.i("Quality profile set to: $profileName")
        } else {
            Timber.w("Unknown quality profile: $profileName")
        }
    }

    fun setImageQuality(quality: Float) {
        val current = _currentSettings.value
        _currentSettings.value = current.copy(
            imageQuality = quality.coerceIn(0.1f, 1.0f)
        )
        Timber.d("Image quality set to: $quality")
    }

    fun setResolutionScale(scale: Float) {
        val current = _currentSettings.value
        _currentSettings.value = current.copy(
            resolutionScale = scale.coerceIn(0.1f, 1.0f)
        )
        Timber.d("Resolution scale set to: $scale")
    }

    fun setProcessingFrequency(frequency: Float) {
        val current = _currentSettings.value
        _currentSettings.value = current.copy(
            processingFrequency = frequency.coerceIn(0.1f, 1.0f)
        )
        Timber.d("Processing frequency set to: $frequency")
    }

    fun enableAdaptiveFrameSkipping(enable: Boolean) {
        isAdaptiveFrameSkippingEnabled = enable
        Timber.i("Adaptive frame skipping: ${if (enable) "enabled" else "disabled"}")
    }

    fun enableThermalThrottling(enable: Boolean) {
        isThermalThrottlingEnabled = enable
        Timber.i("Thermal throttling: ${if (enable) "enabled" else "disabled"}")
    }

    fun setAdaptationSensitivity(sensitivity: Float) {
        adaptationSensitivity = sensitivity.coerceIn(0.0f, 1.0f)
        Timber.i("Adaptation sensitivity set to: $sensitivity")
    }

    fun setUserQualityPreference(preference: Float) {
        userQualityPreference = preference.coerceIn(0.0f, 1.0f)
        Timber.i("User quality preference set to: $preference")
    }

    fun forceQualityAdaptation(direction: String, urgency: String = "medium") {
        val adaptationDirection = when (direction.lowercase()) {
            "increase", "up", "enhance" -> AdaptationDirection.INCREASE
            "decrease", "down", "reduce" -> AdaptationDirection.DECREASE
            else -> {
                Timber.w("Invalid adaptation direction: $direction")
                return
            }
        }

        val adaptationUrgency = when (urgency.lowercase()) {
            "low" -> Urgency.LOW
            "medium" -> Urgency.MEDIUM
            "high" -> Urgency.HIGH
            else -> Urgency.MEDIUM
        }

        scope.launch {
            val decision = QualityAdaptationDecision(
                direction = adaptationDirection,
                urgency = adaptationUrgency,
                reason = "Manual override",
                targetImprovement = 0.3f
            )
            adaptQualitySettings(decision)
        }
    }

    fun getQualitySummary(): Map<String, Any> {
        val settings = _currentSettings.value
        return mapOf(
            "current_profile" to detectCurrentProfile(),
            "image_quality" to settings.imageQuality,
            "resolution_scale" to settings.resolutionScale,
            "frame_skip_ratio" to settings.frameSkipRatio,
            "processing_frequency" to settings.processingFrequency,
            "overlay_complexity" to settings.overlayComplexity.name,
            "detection_accuracy" to settings.detectionAccuracy.name,
            "gpu_acceleration" to settings.enableGpuAcceleration,
            "max_objects" to settings.maxDetectedObjects,
            "subpixel_accuracy" to settings.enableSubPixelAccuracy,
            "adaptive_frame_skipping" to isAdaptiveFrameSkippingEnabled,
            "thermal_throttling" to isThermalThrottlingEnabled,
            "adaptation_sensitivity" to adaptationSensitivity,
            "quality_score" to calculateCurrentQualityScore(),
            "adaptations_count" to _adaptationHistory.replayCache.size
        )
    }

    private fun detectCurrentProfile(): String {
        val current = _currentSettings.value
        return QUALITY_PROFILES.entries.minByOrNull { (_, profile) ->
            calculateSettingsDifference(current, profile.settings)
        }?.key ?: "custom"
    }

    private fun calculateSettingsDifference(s1: QualitySettings, s2: QualitySettings): Float {
        return abs(s1.imageQuality - s2.imageQuality) +
               abs(s1.resolutionScale - s2.resolutionScale) +
               abs(s1.frameSkipRatio - s2.frameSkipRatio) / 4f +
               abs(s1.processingFrequency - s2.processingFrequency)
    }

    fun getAdaptationStatistics(): Map<String, Any> {
        val adaptations = _adaptationHistory.replayCache
        if (adaptations.isEmpty()) {
            return mapOf("total_adaptations" to 0)
        }

        val increases = adaptations.count { it.toSettings.imageQuality > it.fromSettings.imageQuality }
        val decreases = adaptations.count { it.toSettings.imageQuality < it.fromSettings.imageQuality }
        val avgImprovement = adaptations.map { it.expectedImprovement }.average()
        val avgConfidence = adaptations.map { it.confidence }.average()

        return mapOf(
            "total_adaptations" to adaptations.size,
            "quality_increases" to increases,
            "quality_decreases" to decreases,
            "average_improvement" to avgImprovement,
            "average_confidence" to avgConfidence,
            "most_common_reason" to adaptations.groupBy { it.reason }
                .maxByOrNull { it.value.size }?.key ?: "none"
        )
    }

    fun resetToDefaults() {
        _currentSettings.value = QUALITY_PROFILES["medium"]!!.settings
        isAdaptiveFrameSkippingEnabled = false
        isThermalThrottlingEnabled = false
        adaptationSensitivity = 0.5f
        userQualityPreference = 0.7f
        performanceHistory.clear()
        batteryHistory.clear()
        thermalHistory.clear()
        Timber.i("Quality manager reset to defaults")
    }

    fun shutdown() {
        scope.cancel()
    }
}