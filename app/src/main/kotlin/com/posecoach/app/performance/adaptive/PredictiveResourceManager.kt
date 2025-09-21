package com.posecoach.app.performance.adaptive

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

/**
 * Predictive Resource Manager - Uses machine learning to predict resource usage and optimize allocation
 */
class PredictiveResourceManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    data class ResourcePrediction(
        val timestamp: Long,
        val predictedCpuUsage: Float, // 0.0 to 1.0
        val predictedMemoryUsage: Float, // 0.0 to 1.0
        val predictedInferenceTimeMs: Float,
        val predictedBatteryDrainRate: Float, // per hour
        val confidence: Float, // 0.0 to 1.0
        val recommendedQualityLevel: QualityLevel
    )

    data class ResourceSample(
        val timestamp: Long,
        val cpuUsage: Float,
        val memoryUsage: Float,
        val batteryLevel: Float,
        val thermalState: Int,
        val inferenceTimeMs: Float,
        val frameDrops: Int,
        val networkLatency: Float = 0f,
        val userInteractionCount: Int = 0
    )

    enum class QualityLevel {
        ULTRA_HIGH,
        HIGH,
        MEDIUM,
        LOW,
        ULTRA_LOW
    }

    data class PredictionModel(
        val weights: FloatArray,
        val bias: Float,
        val normalizers: FloatArray = floatArrayOf(1f, 1f, 1f, 1f), // For feature normalization
        val accuracy: Float = 0.0f,
        val lastTrainingTime: Long = 0L
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PredictionModel
            return weights.contentEquals(other.weights) && bias == other.bias
        }

        override fun hashCode(): Int {
            var result = weights.contentHashCode()
            result = 31 * result + bias.hashCode()
            return result
        }
    }

    companion object {
        private const val MAX_SAMPLES = 1000
        private const val PREDICTION_WINDOW_MS = 30000L // 30 seconds
        private const val MODEL_UPDATE_INTERVAL_MS = 60000L // 1 minute
        private const val MIN_SAMPLES_FOR_PREDICTION = 10
        private const val FEATURE_COUNT = 8
    }

    private val resourceSamples = ConcurrentLinkedQueue<ResourceSample>()
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private var cpuPredictionModel = PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)
    private var memoryPredictionModel = PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)
    private var inferencePredictionModel = PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)
    private var batteryPredictionModel = PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)

    private val _resourcePredictions = MutableSharedFlow<ResourcePrediction>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val resourcePredictions: SharedFlow<ResourcePrediction> = _resourcePredictions.asSharedFlow()

    private val _optimizationRecommendations = MutableSharedFlow<OptimizationRecommendation>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val optimizationRecommendations: SharedFlow<OptimizationRecommendation> = _optimizationRecommendations.asSharedFlow()

    data class OptimizationRecommendation(
        val type: RecommendationType,
        val priority: Priority,
        val description: String,
        val expectedImprovement: Float, // 0.0 to 1.0
        val confidence: Float,
        val actionRequired: String
    )

    enum class RecommendationType {
        REDUCE_QUALITY,
        INCREASE_QUALITY,
        ENABLE_FRAME_SKIP,
        DISABLE_FRAME_SKIP,
        SWITCH_TO_CPU,
        SWITCH_TO_GPU,
        CLEAR_CACHE,
        PRELOAD_RESOURCES,
        THERMAL_THROTTLE,
        BATTERY_OPTIMIZATION
    }

    enum class Priority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    init {
        startResourceMonitoring()
        startModelTraining()
    }

    private fun startResourceMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    collectResourceSample()
                    generatePredictions()
                    delay(1000) // Collect sample every second
                } catch (e: Exception) {
                    Timber.e(e, "Error in resource monitoring")
                    delay(5000) // Wait longer on error
                }
            }
        }
    }

    private fun startModelTraining() {
        scope.launch {
            while (isActive) {
                try {
                    if (resourceSamples.size >= MIN_SAMPLES_FOR_PREDICTION) {
                        trainPredictionModels()
                    }
                    delay(MODEL_UPDATE_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in model training")
                    delay(MODEL_UPDATE_INTERVAL_MS * 2)
                }
            }
        }
    }

    private suspend fun collectResourceSample() = withContext(Dispatchers.IO) {
        try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val cpuUsage = getCurrentCpuUsage()
            val memoryUsage = 1f - (memInfo.availMem.toFloat() / memInfo.totalMem)
            val batteryLevel = getBatteryLevel()
            val thermalState = getThermalState()

            val sample = ResourceSample(
                timestamp = SystemClock.elapsedRealtime(),
                cpuUsage = cpuUsage,
                memoryUsage = memoryUsage,
                batteryLevel = batteryLevel,
                thermalState = thermalState,
                inferenceTimeMs = getAverageInferenceTime(),
                frameDrops = getRecentFrameDrops()
            )

            resourceSamples.add(sample)

            // Keep only recent samples
            while (resourceSamples.size > MAX_SAMPLES) {
                resourceSamples.poll()
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to collect resource sample")
        }
    }

    private fun generatePredictions() {
        if (resourceSamples.size < MIN_SAMPLES_FOR_PREDICTION) return

        try {
            val currentSample = resourceSamples.lastOrNull() ?: return
            val recentSamples = resourceSamples.takeLast(10)

            val features = extractFeatures(recentSamples)

            val predictedCpu = predictWithModel(features, cpuPredictionModel).coerceIn(0f, 1f)
            val predictedMemory = predictWithModel(features, memoryPredictionModel).coerceIn(0f, 1f)
            val predictedInference = predictWithModel(features, inferencePredictionModel).coerceAtLeast(0f)
            val predictedBattery = predictWithModel(features, batteryPredictionModel).coerceAtLeast(0f)

            val confidence = calculatePredictionConfidence(recentSamples)
            val recommendedQuality = determineOptimalQualityLevel(
                predictedCpu, predictedMemory, predictedInference, predictedBattery
            )

            val prediction = ResourcePrediction(
                timestamp = SystemClock.elapsedRealtime(),
                predictedCpuUsage = predictedCpu,
                predictedMemoryUsage = predictedMemory,
                predictedInferenceTimeMs = predictedInference,
                predictedBatteryDrainRate = predictedBattery,
                confidence = confidence,
                recommendedQualityLevel = recommendedQuality
            )

            _resourcePredictions.tryEmit(prediction)
            generateOptimizationRecommendations(prediction, currentSample)

        } catch (e: Exception) {
            Timber.e(e, "Failed to generate predictions")
        }
    }

    private fun extractFeatures(samples: List<ResourceSample>): FloatArray {
        if (samples.isEmpty()) return FloatArray(FEATURE_COUNT) { 0f }

        val latest = samples.last()
        val avgCpu = samples.map { it.cpuUsage }.average().toFloat()
        val avgMemory = samples.map { it.memoryUsage }.average().toFloat()
        val avgInference = samples.map { it.inferenceTimeMs }.average().toFloat()
        val cpuTrend = if (samples.size > 1) {
            samples.takeLast(3).map { it.cpuUsage }.let { trend ->
                if (trend.size >= 2) trend.last() - trend.first() else 0f
            }
        } else 0f
        val memoryTrend = if (samples.size > 1) {
            samples.takeLast(3).map { it.memoryUsage }.let { trend ->
                if (trend.size >= 2) trend.last() - trend.first() else 0f
            }
        } else 0f

        return floatArrayOf(
            latest.cpuUsage,
            latest.memoryUsage,
            latest.batteryLevel / 100f, // Normalize to 0-1
            latest.thermalState / 4f, // Assuming thermal state 0-4
            avgCpu,
            avgMemory,
            cpuTrend,
            memoryTrend
        )
    }

    private fun predictWithModel(features: FloatArray, model: PredictionModel): Float {
        if (features.size != model.weights.size) {
            Timber.w("Feature size mismatch: ${features.size} vs ${model.weights.size}")
            return 0f
        }

        // Simple linear model with normalization
        var prediction = model.bias
        for (i in features.indices) {
            val normalizedFeature = features[i] / model.normalizers.getOrElse(i) { 1f }
            prediction += normalizedFeature * model.weights[i]
        }

        // Apply sigmoid activation for bounded outputs (CPU, memory usage)
        return 1f / (1f + exp(-prediction))
    }

    private fun trainPredictionModels() {
        val samples = resourceSamples.toList()
        if (samples.size < MIN_SAMPLES_FOR_PREDICTION) return

        scope.launch(Dispatchers.Default) {
            try {
                // Train CPU usage prediction model
                cpuPredictionModel = trainModel(samples) { it.cpuUsage }

                // Train memory usage prediction model
                memoryPredictionModel = trainModel(samples) { it.memoryUsage }

                // Train inference time prediction model
                inferencePredictionModel = trainModel(samples) { it.inferenceTimeMs }

                // Train battery drain prediction model
                batteryPredictionModel = trainModel(samples) { it.batteryLevel }

                Timber.d("Prediction models updated with ${samples.size} samples")

            } catch (e: Exception) {
                Timber.e(e, "Failed to train prediction models")
            }
        }
    }

    private fun trainModel(samples: List<ResourceSample>, targetExtractor: (ResourceSample) -> Float): PredictionModel {
        if (samples.size < 2) return PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)

        // Simple gradient descent training
        val learningRate = 0.01f
        val epochs = 50

        val weights = FloatArray(FEATURE_COUNT) { (Math.random() - 0.5).toFloat() * 0.1f }
        var bias = 0f

        val trainingData = samples.dropLast(1).mapIndexed { index, sample ->
            val nextSample = samples[index + 1]
            val features = extractFeatures(samples.drop(index).take(min(10, samples.size - index)))
            val target = targetExtractor(nextSample)
            features to target
        }

        // Calculate normalizers
        val normalizers = FloatArray(FEATURE_COUNT) { i ->
            val values = trainingData.map { it.first[i] }
            val max = values.maxOrNull() ?: 1f
            val min = values.minOrNull() ?: 0f
            max(max - min, 0.1f) // Avoid division by zero
        }

        repeat(epochs) {
            var totalLoss = 0f

            trainingData.forEach { (features, target) ->
                // Forward pass
                var prediction = bias
                for (i in features.indices) {
                    val normalizedFeature = features[i] / normalizers[i]
                    prediction += normalizedFeature * weights[i]
                }
                prediction = 1f / (1f + exp(-prediction)) // Sigmoid activation

                // Calculate loss and gradients
                val error = prediction - target
                totalLoss += error * error

                // Backward pass
                val sigmoidDerivative = prediction * (1f - prediction)
                val deltaOutput = error * sigmoidDerivative

                // Update weights and bias
                bias -= learningRate * deltaOutput
                for (i in features.indices) {
                    val normalizedFeature = features[i] / normalizers[i]
                    weights[i] -= learningRate * deltaOutput * normalizedFeature
                }
            }

            if (it % 10 == 0) {
                val avgLoss = totalLoss / trainingData.size
                Timber.v("Training epoch $it, average loss: $avgLoss")
            }
        }

        val accuracy = calculateModelAccuracy(trainingData, weights, bias, normalizers)

        return PredictionModel(
            weights = weights,
            bias = bias,
            normalizers = normalizers,
            accuracy = accuracy,
            lastTrainingTime = SystemClock.elapsedRealtime()
        )
    }

    private fun calculateModelAccuracy(
        trainingData: List<Pair<FloatArray, Float>>,
        weights: FloatArray,
        bias: Float,
        normalizers: FloatArray
    ): Float {
        if (trainingData.isEmpty()) return 0f

        var totalError = 0f
        trainingData.forEach { (features, target) ->
            var prediction = bias
            for (i in features.indices) {
                val normalizedFeature = features[i] / normalizers[i]
                prediction += normalizedFeature * weights[i]
            }
            prediction = 1f / (1f + exp(-prediction))

            val error = abs(prediction - target)
            totalError += error
        }

        val avgError = totalError / trainingData.size
        return max(0f, 1f - avgError) // Convert error to accuracy
    }

    private fun calculatePredictionConfidence(samples: List<ResourceSample>): Float {
        if (samples.size < 3) return 0.3f

        // Calculate confidence based on data consistency and model accuracy
        val cpuVariance = samples.map { it.cpuUsage }.let { values ->
            val mean = values.average()
            values.map { (it - mean).pow(2) }.average()
        }

        val memoryVariance = samples.map { it.memoryUsage }.let { values ->
            val mean = values.average()
            values.map { (it - mean).pow(2) }.average()
        }

        // Lower variance = higher confidence
        val varianceConfidence = 1f - min(1f, (cpuVariance + memoryVariance).toFloat() * 2f)

        // Model accuracy contribution
        val modelAccuracy = (cpuPredictionModel.accuracy + memoryPredictionModel.accuracy) / 2f

        // Sample size contribution
        val sampleConfidence = min(1f, samples.size / 20f)

        return (varianceConfidence * 0.4f + modelAccuracy * 0.4f + sampleConfidence * 0.2f).coerceIn(0f, 1f)
    }

    private fun determineOptimalQualityLevel(
        predictedCpu: Float,
        predictedMemory: Float,
        predictedInference: Float,
        predictedBattery: Float
    ): QualityLevel {
        val resourceScore = 1f - (predictedCpu * 0.3f + predictedMemory * 0.3f + min(predictedInference / 100f, 1f) * 0.4f)
        val batteryScore = min(predictedBattery / 100f, 1f)

        val overallScore = resourceScore * 0.7f + batteryScore * 0.3f

        return when {
            overallScore >= 0.9f -> QualityLevel.ULTRA_HIGH
            overallScore >= 0.7f -> QualityLevel.HIGH
            overallScore >= 0.5f -> QualityLevel.MEDIUM
            overallScore >= 0.3f -> QualityLevel.LOW
            else -> QualityLevel.ULTRA_LOW
        }
    }

    private fun generateOptimizationRecommendations(
        prediction: ResourcePrediction,
        currentSample: ResourceSample
    ) {
        val recommendations = mutableListOf<OptimizationRecommendation>()

        // CPU-based recommendations
        if (prediction.predictedCpuUsage > 0.8f) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.REDUCE_QUALITY,
                    priority = Priority.HIGH,
                    description = "High CPU usage predicted (${(prediction.predictedCpuUsage * 100).toInt()}%)",
                    expectedImprovement = 0.3f,
                    confidence = prediction.confidence,
                    actionRequired = "Reduce input resolution or enable frame skipping"
                )
            )
        }

        // Memory-based recommendations
        if (prediction.predictedMemoryUsage > 0.85f) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.CLEAR_CACHE,
                    priority = Priority.MEDIUM,
                    description = "High memory usage predicted (${(prediction.predictedMemoryUsage * 100).toInt()}%)",
                    expectedImprovement = 0.2f,
                    confidence = prediction.confidence,
                    actionRequired = "Clear model cache and reduce memory allocations"
                )
            )
        }

        // Inference time recommendations
        if (prediction.predictedInferenceTimeMs > 50f) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.SWITCH_TO_CPU,
                    priority = Priority.MEDIUM,
                    description = "Slow inference predicted (${prediction.predictedInferenceTimeMs.toInt()}ms)",
                    expectedImprovement = 0.4f,
                    confidence = prediction.confidence,
                    actionRequired = "Consider switching to CPU delegate or reducing model complexity"
                )
            )
        }

        // Battery optimization
        if (currentSample.batteryLevel < 20f && prediction.predictedBatteryDrainRate > 15f) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.BATTERY_OPTIMIZATION,
                    priority = Priority.CRITICAL,
                    description = "Low battery with high drain rate predicted",
                    expectedImprovement = 0.5f,
                    confidence = prediction.confidence,
                    actionRequired = "Enable battery saver mode and reduce processing frequency"
                )
            )
        }

        // Thermal throttling
        if (currentSample.thermalState >= 3) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.THERMAL_THROTTLE,
                    priority = Priority.HIGH,
                    description = "Thermal throttling detected",
                    expectedImprovement = 0.3f,
                    confidence = 0.9f,
                    actionRequired = "Reduce processing load to prevent overheating"
                )
            )
        }

        // Quality improvement recommendations
        if (prediction.predictedCpuUsage < 0.3f && prediction.predictedMemoryUsage < 0.5f &&
            prediction.predictedInferenceTimeMs < 25f) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.INCREASE_QUALITY,
                    priority = Priority.LOW,
                    description = "Resources available for quality improvement",
                    expectedImprovement = 0.2f,
                    confidence = prediction.confidence,
                    actionRequired = "Consider increasing resolution or enabling additional features"
                )
            )
        }

        // Emit recommendations
        recommendations.forEach { recommendation ->
            _optimizationRecommendations.tryEmit(recommendation)
        }
    }

    // Helper methods for resource monitoring
    private fun getCurrentCpuUsage(): Float {
        // Simplified CPU usage estimation
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            (usedMemory.toFloat() / maxMemory).coerceIn(0f, 1f)
        } catch (e: Exception) {
            0.5f // Default moderate usage
        }
    }

    private fun getBatteryLevel(): Float {
        // This would integrate with BatteryManager in a real implementation
        return 75f // Placeholder
    }

    private fun getThermalState(): Int {
        // This would integrate with PowerManager.getThermalState() in API 29+
        return 1 // Placeholder: normal state
    }

    private fun getAverageInferenceTime(): Float {
        // This would integrate with PerformanceMetrics
        return resourceSamples.takeLast(10).map { it.inferenceTimeMs }.average().toFloat()
    }

    private fun getRecentFrameDrops(): Int {
        // This would integrate with frame drop detection
        return 0 // Placeholder
    }

    fun getLatestPrediction(): ResourcePrediction? {
        return _resourcePredictions.replayCache.lastOrNull()
    }

    fun getModelAccuracy(): Map<String, Float> {
        return mapOf(
            "cpu" to cpuPredictionModel.accuracy,
            "memory" to memoryPredictionModel.accuracy,
            "inference" to inferencePredictionModel.accuracy,
            "battery" to batteryPredictionModel.accuracy
        )
    }

    fun resetModels() {
        cpuPredictionModel = PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)
        memoryPredictionModel = PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)
        inferencePredictionModel = PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)
        batteryPredictionModel = PredictionModel(FloatArray(FEATURE_COUNT) { 0.1f }, 0f)
        resourceSamples.clear()
        Timber.i("Prediction models reset")
    }

    fun shutdown() {
        scope.cancel()
    }
}