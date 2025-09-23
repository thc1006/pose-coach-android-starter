package com.posecoach.app.privacy.minimization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.*

/**
 * Data Minimization Processor
 * Implements intelligent data reduction, real-time anonymization,
 * differential privacy, and PII detection with automatic redaction.
 */
class DataMinimizationProcessor {

    @Serializable
    data class MinimizationPolicy(
        val reductionLevel: ReductionLevel = ReductionLevel.MODERATE,
        val anonymizationEnabled: Boolean = true,
        val differentialPrivacyEpsilon: Double = 1.0,
        val piiDetectionEnabled: Boolean = true,
        val automaticRedaction: Boolean = true,
        val dataRetentionHours: Long = 24L,
        val minimumDataQuality: Float = 0.8f,
        val preserveEssentialFeatures: Boolean = true
    )

    @Serializable
    data class ProcessingResult(
        val originalSize: Int,
        val minimizedSize: Int,
        val reductionPercentage: Float,
        val qualityScore: Float,
        val piiDetected: List<PIIDetection>,
        val anonymizationApplied: Boolean,
        val differentialPrivacyApplied: Boolean,
        val processingTimeMs: Long
    )

    @Serializable
    data class PIIDetection(
        val type: PIIType,
        val location: DataLocation,
        val confidence: Float,
        val redactionApplied: Boolean,
        val replacementValue: String?
    )

    enum class ReductionLevel {
        MINIMAL,     // 10-20% reduction
        MODERATE,    // 30-50% reduction
        AGGRESSIVE,  // 60-80% reduction
        MAXIMUM      // 80-95% reduction
    }

    enum class PIIType {
        NAME,
        EMAIL,
        PHONE_NUMBER,
        ADDRESS,
        BIOMETRIC_IDENTIFIER,
        DEVICE_ID,
        IP_ADDRESS,
        GEOLOCATION,
        HEALTH_DATA,
        FACIAL_FEATURES
    }

    @Serializable
    data class DataLocation(
        val fieldName: String,
        val startIndex: Int,
        val endIndex: Int
    )

    private val secureRandom = SecureRandom()
    private val _currentPolicy = MutableStateFlow(MinimizationPolicy())
    val currentPolicy: StateFlow<MinimizationPolicy> = _currentPolicy.asStateFlow()

    private val _processingStats = MutableStateFlow(ProcessingStatistics())
    val processingStats: StateFlow<ProcessingStatistics> = _processingStats.asStateFlow()

    data class ProcessingStatistics(
        val totalProcessed: Long = 0L,
        val totalReductionBytes: Long = 0L,
        val averageReductionPercentage: Float = 0f,
        val piiDetections: Long = 0L,
        val qualityMaintained: Float = 0f
    )

    /**
     * Process pose landmark data with intelligent minimization
     */
    fun minimizePoseLandmarks(
        landmarks: FloatArray,
        metadata: PoseMetadata = PoseMetadata()
    ): MinimizedPoseData {
        val startTime = System.currentTimeMillis()
        val policy = _currentPolicy.value

        // Apply data reduction based on coaching effectiveness
        val reducedLandmarks = when (policy.reductionLevel) {
            ReductionLevel.MINIMAL -> reduceMinimal(landmarks, metadata)
            ReductionLevel.MODERATE -> reduceModerate(landmarks, metadata)
            ReductionLevel.AGGRESSIVE -> reduceAggressive(landmarks, metadata)
            ReductionLevel.MAXIMUM -> reduceMaximum(landmarks, metadata)
        }

        // Apply differential privacy if enabled
        val privateLandmarks = if (policy.anonymizationEnabled) {
            applyDifferentialPrivacy(reducedLandmarks, policy.differentialPrivacyEpsilon)
        } else {
            reducedLandmarks
        }

        // Calculate quality score
        val qualityScore = calculateQualityScore(landmarks, privateLandmarks)

        // Ensure minimum quality requirements
        val finalLandmarks = if (qualityScore < policy.minimumDataQuality && policy.preserveEssentialFeatures) {
            preserveEssentialFeatures(landmarks, privateLandmarks, metadata)
        } else {
            privateLandmarks
        }

        val processingTime = System.currentTimeMillis() - startTime

        return MinimizedPoseData(
            landmarks = finalLandmarks,
            originalSize = landmarks.size,
            reducedSize = finalLandmarks.size,
            qualityScore = qualityScore,
            reductionPercentage = ((landmarks.size - finalLandmarks.size).toFloat() / landmarks.size * 100),
            processingTimeMs = processingTime,
            metadata = metadata.copy(minimized = true)
        )
    }

    /**
     * Anonymize biometric data in real-time
     */
    fun anonymizeBiometricData(
        biometricData: BiometricData,
        anonymizationLevel: AnonymizationLevel = AnonymizationLevel.HIGH
    ): AnonymizedBiometricData {
        val policy = _currentPolicy.value

        // Remove direct identifiers
        val anonymizedData = biometricData.copy(
            userId = null,
            deviceId = if (anonymizationLevel >= AnonymizationLevel.HIGH) null else biometricData.deviceId,
            timestamp = if (anonymizationLevel >= AnonymizationLevel.MAXIMUM)
                roundTimestamp(biometricData.timestamp) else biometricData.timestamp
        )

        // Apply noise to measurements
        val noisyMeasurements = anonymizedData.measurements.map { measurement ->
            when (anonymizationLevel) {
                AnonymizationLevel.LOW -> measurement + generateGaussianNoise(0.01)
                AnonymizationLevel.MEDIUM -> measurement + generateGaussianNoise(0.05)
                AnonymizationLevel.HIGH -> measurement + generateGaussianNoise(0.1)
                AnonymizationLevel.MAXIMUM -> measurement + generateGaussianNoise(0.2)
            }
        }.toFloatArray()

        // Apply k-anonymity for group-based anonymization
        val kAnonymizedData = applyKAnonymity(anonymizedData.copy(measurements = noisyMeasurements), k = 5)

        return AnonymizedBiometricData(
            data = kAnonymizedData,
            anonymizationLevel = anonymizationLevel,
            noiseVariance = when (anonymizationLevel) {
                AnonymizationLevel.LOW -> 0.01
                AnonymizationLevel.MEDIUM -> 0.05
                AnonymizationLevel.HIGH -> 0.1
                AnonymizationLevel.MAXIMUM -> 0.2
            },
            kValue = 5
        )
    }

    /**
     * Detect and redact PII from text data
     */
    fun detectAndRedactPII(
        text: String,
        redactionMode: RedactionMode = RedactionMode.REPLACEMENT
    ): PIIProcessingResult {
        val detections = mutableListOf<PIIDetection>()
        var processedText = text

        // Detect various PII types
        detections.addAll(detectEmails(text))
        detections.addAll(detectPhoneNumbers(text))
        detections.addAll(detectNames(text))
        detections.addAll(detectAddresses(text))
        detections.addAll(detectHealthData(text))

        // Apply redaction based on mode
        when (redactionMode) {
            RedactionMode.REMOVAL -> {
                detections.sortedByDescending { it.location.startIndex }.forEach { detection ->
                    processedText = processedText.removeRange(
                        detection.location.startIndex,
                        detection.location.endIndex
                    )
                }
            }
            RedactionMode.REPLACEMENT -> {
                detections.sortedByDescending { it.location.startIndex }.forEach { detection ->
                    val replacement = generateReplacement(detection.type)
                    processedText = processedText.replaceRange(
                        detection.location.startIndex,
                        detection.location.endIndex,
                        replacement
                    )
                }
            }
            RedactionMode.MASKING -> {
                detections.sortedByDescending { it.location.startIndex }.forEach { detection ->
                    val mask = "*".repeat(detection.location.endIndex - detection.location.startIndex)
                    processedText = processedText.replaceRange(
                        detection.location.startIndex,
                        detection.location.endIndex,
                        mask
                    )
                }
            }
        }

        return PIIProcessingResult(
            originalText = text,
            processedText = processedText,
            detections = detections,
            redactionMode = redactionMode,
            piiRemoved = detections.isNotEmpty()
        )
    }

    /**
     * Apply differential privacy to numerical data
     */
    fun applyDifferentialPrivacy(
        data: FloatArray,
        epsilon: Double = 1.0,
        sensitivity: Double = 1.0
    ): FloatArray {
        val scale = sensitivity / epsilon
        return data.map { value ->
            value + generateLaplaceNoise(scale).toFloat()
        }.toFloatArray()
    }

    /**
     * Intelligently reduce data based on coaching effectiveness
     */
    fun intelligentDataReduction(
        poseData: PoseSequence,
        coachingContext: CoachingContext
    ): ReducedPoseSequence {
        val importance = calculateFeatureImportance(poseData, coachingContext)
        val policy = _currentPolicy.value

        val retentionRatio = when (policy.reductionLevel) {
            ReductionLevel.MINIMAL -> 0.9f
            ReductionLevel.MODERATE -> 0.7f
            ReductionLevel.AGGRESSIVE -> 0.4f
            ReductionLevel.MAXIMUM -> 0.2f
        }

        // Select most important features
        val importantIndices = importance
            .mapIndexed { index, score -> index to score }
            .sortedByDescending { it.second }
            .take((poseData.frames.size * retentionRatio).toInt())
            .map { it.first }
            .sorted()

        val reducedFrames = importantIndices.map { index ->
            poseData.frames[index]
        }

        return ReducedPoseSequence(
            frames = reducedFrames,
            originalFrameCount = poseData.frames.size,
            retainedFeatureIndices = importantIndices,
            reductionRatio = retentionRatio,
            qualityScore = calculateSequenceQuality(poseData, reducedFrames)
        )
    }

    /**
     * Update minimization policy
     */
    fun updatePolicy(newPolicy: MinimizationPolicy) {
        _currentPolicy.value = newPolicy
        Timber.i("Data minimization policy updated: $newPolicy")
    }

    // Private implementation methods

    private fun reduceMinimal(landmarks: FloatArray, metadata: PoseMetadata): FloatArray {
        // Remove redundant landmarks while preserving core pose structure
        val essentialIndices = getEssentialLandmarkIndices(metadata.workoutType)
        return landmarks.filterIndexed { index, _ ->
            index in essentialIndices || index % 4 != 3 // Remove z-coordinates for some points
        }.toFloatArray()
    }

    private fun reduceModerate(landmarks: FloatArray, metadata: PoseMetadata): FloatArray {
        // More aggressive reduction, keeping only key joints
        val keyJointIndices = getKeyJointIndices(metadata.workoutType)
        return landmarks.filterIndexed { index, _ -> index in keyJointIndices }.toFloatArray()
    }

    private fun reduceAggressive(landmarks: FloatArray, metadata: PoseMetadata): FloatArray {
        // Reduce to essential pose landmarks only
        val coreIndices = getCoreJointIndices()
        return landmarks.filterIndexed { index, _ -> index in coreIndices }.toFloatArray()
    }

    private fun reduceMaximum(landmarks: FloatArray, metadata: PoseMetadata): FloatArray {
        // Extreme reduction - only most critical points
        val criticalIndices = getCriticalJointIndices(metadata.workoutType)
        return landmarks.filterIndexed { index, _ -> index in criticalIndices }.toFloatArray()
    }

    private fun calculateQualityScore(original: FloatArray, processed: FloatArray): Float {
        if (processed.isEmpty()) return 0f

        // Calculate normalized RMSE
        val minSize = min(original.size, processed.size)
        var sumSquaredError = 0.0

        for (i in 0 until minSize) {
            val error = original[i] - processed[i]
            sumSquaredError += error * error
        }

        val rmse = sqrt(sumSquaredError / minSize)
        return max(0f, 1f - rmse.toFloat())
    }

    private fun preserveEssentialFeatures(
        original: FloatArray,
        processed: FloatArray,
        metadata: PoseMetadata
    ): FloatArray {
        val essential = getEssentialFeatureIndices(metadata)
        val result = processed.copyOf()

        // Restore essential features from original data
        essential.forEach { index ->
            if (index < original.size && index < result.size) {
                result[index] = original[index]
            }
        }

        return result
    }

    private fun detectEmails(text: String): List<PIIDetection> {
        val emailRegex = Regex("""[\w\.-]+@[\w\.-]+\.\w+""")
        return emailRegex.findAll(text).map { match ->
            PIIDetection(
                type = PIIType.EMAIL,
                location = DataLocation("text", match.range.first, match.range.last + 1),
                confidence = 0.95f,
                redactionApplied = false,
                replacementValue = null
            )
        }.toList()
    }

    private fun detectPhoneNumbers(text: String): List<PIIDetection> {
        val phoneRegex = Regex("""(\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}""")
        return phoneRegex.findAll(text).map { match ->
            PIIDetection(
                type = PIIType.PHONE_NUMBER,
                location = DataLocation("text", match.range.first, match.range.last + 1),
                confidence = 0.9f,
                redactionApplied = false,
                replacementValue = null
            )
        }.toList()
    }

    private fun detectNames(text: String): List<PIIDetection> {
        // Simplified name detection - would use NLP in production
        val namePatterns = listOf(
            Regex("""Mr\.|Mrs\.|Ms\.|Dr\.\s+[A-Z][a-z]+\s+[A-Z][a-z]+"""),
            Regex("""[A-Z][a-z]+\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)?""")
        )

        return namePatterns.flatMap { regex ->
            regex.findAll(text).map { match ->
                PIIDetection(
                    type = PIIType.NAME,
                    location = DataLocation("text", match.range.first, match.range.last + 1),
                    confidence = 0.7f,
                    redactionApplied = false,
                    replacementValue = null
                )
            }
        }
    }

    private fun detectAddresses(text: String): List<PIIDetection> {
        val addressRegex = Regex("""\d+\s+[A-Za-z\s]+(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd)""", RegexOption.IGNORE_CASE)
        return addressRegex.findAll(text).map { match ->
            PIIDetection(
                type = PIIType.ADDRESS,
                location = DataLocation("text", match.range.first, match.range.last + 1),
                confidence = 0.8f,
                redactionApplied = false,
                replacementValue = null
            )
        }.toList()
    }

    private fun detectHealthData(text: String): List<PIIDetection> {
        val healthTerms = listOf("diagnosis", "medication", "treatment", "condition", "symptoms", "medical history")
        val detections = mutableListOf<PIIDetection>()

        healthTerms.forEach { term ->
            val regex = Regex("""$term[\w\s,.-]{0,50}""", RegexOption.IGNORE_CASE)
            regex.findAll(text).forEach { match ->
                detections.add(
                    PIIDetection(
                        type = PIIType.HEALTH_DATA,
                        location = DataLocation("text", match.range.first, match.range.last + 1),
                        confidence = 0.6f,
                        redactionApplied = false,
                        replacementValue = null
                    )
                )
            }
        }

        return detections
    }

    private fun generateReplacement(type: PIIType): String {
        return when (type) {
            PIIType.NAME -> "[NAME]"
            PIIType.EMAIL -> "[EMAIL]"
            PIIType.PHONE_NUMBER -> "[PHONE]"
            PIIType.ADDRESS -> "[ADDRESS]"
            PIIType.HEALTH_DATA -> "[HEALTH_INFO]"
            PIIType.BIOMETRIC_IDENTIFIER -> "[BIOMETRIC]"
            PIIType.DEVICE_ID -> "[DEVICE_ID]"
            PIIType.IP_ADDRESS -> "[IP_ADDRESS]"
            PIIType.GEOLOCATION -> "[LOCATION]"
            PIIType.FACIAL_FEATURES -> "[FACIAL_DATA]"
        }
    }

    private fun generateLaplaceNoise(scale: Double): Double {
        val u = secureRandom.nextDouble() - 0.5
        return -scale * Math.signum(u) * ln(1 - 2 * abs(u))
    }

    private fun generateGaussianNoise(variance: Double): Float {
        return (secureRandom.nextGaussian() * sqrt(variance)).toFloat()
    }

    private fun roundTimestamp(timestamp: Long, granularityMs: Long = 3600000L): Long {
        // Round to nearest hour for privacy
        return (timestamp / granularityMs) * granularityMs
    }

    private fun applyKAnonymity(data: BiometricData, k: Int): BiometricData {
        // Simplified k-anonymity implementation
        // In production, this would involve more sophisticated grouping
        return data
    }

    private fun calculateFeatureImportance(
        poseData: PoseSequence,
        context: CoachingContext
    ): FloatArray {
        // Calculate importance scores based on coaching context
        val scores = FloatArray(poseData.frames.size)

        poseData.frames.forEachIndexed { index, frame ->
            scores[index] = when (context.focusArea) {
                FocusArea.POSTURE -> calculatePostureImportance(frame)
                FocusArea.BALANCE -> calculateBalanceImportance(frame)
                FocusArea.MOVEMENT -> calculateMovementImportance(frame, index, poseData.frames)
                FocusArea.FLEXIBILITY -> calculateFlexibilityImportance(frame)
            }
        }

        return scores
    }

    private fun calculateSequenceQuality(original: PoseSequence, reduced: List<PoseFrame>): Float {
        // Calculate quality based on preserved motion patterns
        if (reduced.isEmpty()) return 0f

        val originalMotion = calculateMotionComplexity(original.frames)
        val reducedMotion = calculateMotionComplexity(reduced)

        return (reducedMotion / originalMotion).coerceIn(0f, 1f)
    }

    private fun calculateMotionComplexity(frames: List<PoseFrame>): Float {
        if (frames.size < 2) return 0f

        var totalVariation = 0f
        for (i in 1 until frames.size) {
            totalVariation += calculateFrameVariation(frames[i-1], frames[i])
        }

        return totalVariation / (frames.size - 1)
    }

    private fun calculateFrameVariation(frame1: PoseFrame, frame2: PoseFrame): Float {
        val minSize = min(frame1.landmarks.size, frame2.landmarks.size)
        var variation = 0f

        for (i in 0 until minSize) {
            variation += abs(frame1.landmarks[i] - frame2.landmarks[i])
        }

        return variation / minSize
    }

    // Helper methods for landmark filtering
    private fun getEssentialLandmarkIndices(workoutType: WorkoutType): Set<Int> = when (workoutType) {
        WorkoutType.YOGA -> setOf(0, 1, 2, 5, 6, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26)
        WorkoutType.STRENGTH -> setOf(5, 6, 7, 8, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
        WorkoutType.CARDIO -> setOf(0, 11, 12, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)
        WorkoutType.REHABILITATION -> setOf(0, 1, 2, 5, 6, 11, 12, 13, 14, 15, 16)
    }

    private fun getKeyJointIndices(workoutType: WorkoutType): Set<Int> = when (workoutType) {
        WorkoutType.YOGA -> setOf(0, 5, 6, 11, 12, 15, 16, 23, 24)
        WorkoutType.STRENGTH -> setOf(5, 6, 11, 12, 15, 16, 23, 24, 25, 26)
        WorkoutType.CARDIO -> setOf(11, 12, 23, 24, 25, 26, 27, 28)
        WorkoutType.REHABILITATION -> setOf(0, 11, 12, 15, 16, 23, 24)
    }

    private fun getCoreJointIndices(): Set<Int> = setOf(0, 11, 12, 23, 24) // Head, shoulders, hips

    private fun getCriticalJointIndices(workoutType: WorkoutType): Set<Int> = when (workoutType) {
        WorkoutType.YOGA -> setOf(0, 11, 12) // Head and shoulders
        WorkoutType.STRENGTH -> setOf(11, 12, 23, 24) // Shoulders and hips
        WorkoutType.CARDIO -> setOf(23, 24) // Hips only
        WorkoutType.REHABILITATION -> setOf(0, 11, 12) // Head and shoulders
    }

    private fun getEssentialFeatureIndices(metadata: PoseMetadata): Set<Int> {
        return when (metadata.workoutType) {
            WorkoutType.REHABILITATION -> setOf(0, 1, 2, 11, 12) // Always preserve head and shoulders
            else -> setOf(11, 12, 23, 24) // Preserve torso
        }
    }

    private fun calculatePostureImportance(frame: PoseFrame): Float = 0.8f
    private fun calculateBalanceImportance(frame: PoseFrame): Float = 0.7f
    private fun calculateMovementImportance(frame: PoseFrame, index: Int, frames: List<PoseFrame>): Float = 0.6f
    private fun calculateFlexibilityImportance(frame: PoseFrame): Float = 0.5f

    // Data classes
    enum class AnonymizationLevel { LOW, MEDIUM, HIGH, MAXIMUM }
    enum class RedactionMode { REMOVAL, REPLACEMENT, MASKING }
    enum class WorkoutType { YOGA, STRENGTH, CARDIO, REHABILITATION }
    enum class FocusArea { POSTURE, BALANCE, MOVEMENT, FLEXIBILITY }

    data class PoseMetadata(
        val workoutType: WorkoutType = WorkoutType.STRENGTH,
        val timestamp: Long = System.currentTimeMillis(),
        val minimized: Boolean = false
    )

    data class MinimizedPoseData(
        val landmarks: FloatArray,
        val originalSize: Int,
        val reducedSize: Int,
        val qualityScore: Float,
        val reductionPercentage: Float,
        val processingTimeMs: Long,
        val metadata: PoseMetadata
    )

    data class BiometricData(
        val userId: String?,
        val deviceId: String?,
        val timestamp: Long,
        val measurements: FloatArray
    )

    data class AnonymizedBiometricData(
        val data: BiometricData,
        val anonymizationLevel: AnonymizationLevel,
        val noiseVariance: Double,
        val kValue: Int
    )

    data class PIIProcessingResult(
        val originalText: String,
        val processedText: String,
        val detections: List<PIIDetection>,
        val redactionMode: RedactionMode,
        val piiRemoved: Boolean
    )

    data class PoseSequence(val frames: List<PoseFrame>)
    data class PoseFrame(val landmarks: FloatArray)
    data class CoachingContext(val focusArea: FocusArea)

    data class ReducedPoseSequence(
        val frames: List<PoseFrame>,
        val originalFrameCount: Int,
        val retainedFeatureIndices: List<Int>,
        val reductionRatio: Float,
        val qualityScore: Float
    )

    fun minimizeData(data: FloatArray, dataType: String, requirement: String): FloatArray {
        return when (dataType) {
            "pose_landmarks" -> {
                val minimized = minimizePoseLandmarks(data, PoseMetadata())
                minimized.landmarks
            }
            else -> data
        }
    }
}