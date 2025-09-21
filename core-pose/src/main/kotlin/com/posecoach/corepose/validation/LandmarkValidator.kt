package com.posecoach.corepose.validation

import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.PoseLandmarks
import timber.log.Timber
import kotlin.math.*

/**
 * Comprehensive landmark validation and filtering system.
 * Ensures pose data quality through multiple validation layers.
 */
class LandmarkValidator(
    private val config: ValidationConfig = ValidationConfig()
) {

    data class ValidationConfig(
        val minVisibilityThreshold: Float = 0.5f,
        val minPresenceThreshold: Float = 0.7f,
        val maxOutlierDistance: Float = 0.5f, // Normalized screen coordinates
        val enableAnatomicalValidation: Boolean = true,
        val enableTemporalValidation: Boolean = true,
        val maxTemporalJump: Float = 0.1f, // Max movement between frames
        val requiredCoreKeypoints: Set<Int> = setOf(
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER,
            PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP
        ),
        val smoothingStrength: Float = 0.3f,
        val enableKalmanFiltering: Boolean = true
    )

    data class ValidationResult(
        val isValid: Boolean,
        val filteredLandmarks: List<PoseLandmarkResult.Landmark>,
        val qualityScore: Double,
        val errors: List<ValidationError>,
        val warnings: List<ValidationWarning>,
        val appliedFilters: List<String>
    )

    data class ValidationError(val type: ErrorType, val message: String, val landmarkIndex: Int? = null)
    data class ValidationWarning(val type: WarningType, val message: String, val landmarkIndex: Int? = null)

    enum class ErrorType {
        INSUFFICIENT_VISIBILITY,
        MISSING_CORE_KEYPOINTS,
        ANATOMICAL_IMPOSSIBILITY,
        EXTREME_OUTLIER,
        TEMPORAL_DISCONTINUITY
    }

    enum class WarningType {
        LOW_CONFIDENCE,
        POTENTIAL_OCCLUSION,
        UNUSUAL_POSE,
        SMOOTHING_APPLIED,
        PREDICTION_USED
    }

    // Temporal tracking for smoothing
    private val landmarkHistory = mutableMapOf<Int, MutableList<PoseLandmarkResult.Landmark>>()
    private val kalmanFilters = mutableMapOf<Int, KalmanFilter>()
    private var lastValidPose: PoseLandmarkResult? = null
    private val maxHistorySize = 10

    /**
     * Validate and filter pose landmarks with comprehensive quality checks.
     */
    fun validateAndFilter(poseResult: PoseLandmarkResult): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        val appliedFilters = mutableListOf<String>()

        try {
            // 1. Basic visibility and presence validation
            val visibilityResult = validateVisibilityAndPresence(poseResult.landmarks, errors, warnings)
            if (!visibilityResult) {
                return ValidationResult(false, poseResult.landmarks, 0.0, errors, warnings, appliedFilters)
            }

            // 2. Core keypoints validation
            val coreKeypointsResult = validateCoreKeypoints(poseResult.landmarks, errors)
            if (!coreKeypointsResult) {
                return ValidationResult(false, poseResult.landmarks, 0.0, errors, warnings, appliedFilters)
            }

            // 3. Anatomical validation
            if (config.enableAnatomicalValidation) {
                validateAnatomicalConstraints(poseResult.landmarks, errors, warnings)
            }

            // 4. Temporal validation and smoothing
            var filteredLandmarks = poseResult.landmarks
            if (config.enableTemporalValidation && lastValidPose != null) {
                val temporalResult = validateTemporalConsistency(poseResult, lastValidPose!!, errors, warnings)
                if (temporalResult.hasDiscontinuity) {
                    filteredLandmarks = applySmoothingFilters(poseResult.landmarks, appliedFilters, warnings)
                }
            }

            // 5. Outlier detection and correction
            filteredLandmarks = detectAndCorrectOutliers(filteredLandmarks, appliedFilters, warnings)

            // 6. Kalman filtering for enhanced smoothness
            if (config.enableKalmanFiltering) {
                filteredLandmarks = applyKalmanFiltering(filteredLandmarks, poseResult.timestampMs, appliedFilters)
            }

            // 7. Calculate overall quality score
            val qualityScore = calculateQualityScore(filteredLandmarks, errors, warnings)

            // 8. Update history for temporal validation
            updateLandmarkHistory(filteredLandmarks)
            if (errors.isEmpty() || errors.all { it.type != ErrorType.MISSING_CORE_KEYPOINTS }) {
                lastValidPose = poseResult.copy(landmarks = filteredLandmarks)
            }

            val isValid = errors.isEmpty() && qualityScore >= 0.6

            return ValidationResult(
                isValid = isValid,
                filteredLandmarks = filteredLandmarks,
                qualityScore = qualityScore,
                errors = errors,
                warnings = warnings,
                appliedFilters = appliedFilters
            )

        } catch (e: Exception) {
            Timber.e(e, "Error during landmark validation")
            errors.add(ValidationError(ErrorType.EXTREME_OUTLIER, "Validation failed: ${e.message}"))
            return ValidationResult(false, poseResult.landmarks, 0.0, errors, warnings, appliedFilters)
        }
    }

    private fun validateVisibilityAndPresence(
        landmarks: List<PoseLandmarkResult.Landmark>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ): Boolean {
        var validCount = 0
        val totalCount = landmarks.size

        for ((index, landmark) in landmarks.withIndex()) {
            if (landmark.visibility < config.minVisibilityThreshold) {
                if (index in config.requiredCoreKeypoints) {
                    errors.add(ValidationError(
                        ErrorType.INSUFFICIENT_VISIBILITY,
                        "Core keypoint $index has insufficient visibility: ${landmark.visibility}",
                        index
                    ))
                } else {
                    warnings.add(ValidationWarning(
                        WarningType.LOW_CONFIDENCE,
                        "Low visibility for landmark $index: ${landmark.visibility}",
                        index
                    ))
                }
            }

            if (landmark.presence < config.minPresenceThreshold) {
                if (index in config.requiredCoreKeypoints) {
                    errors.add(ValidationError(
                        ErrorType.MISSING_CORE_KEYPOINTS,
                        "Core keypoint $index has low presence: ${landmark.presence}",
                        index
                    ))
                } else {
                    warnings.add(ValidationWarning(
                        WarningType.POTENTIAL_OCCLUSION,
                        "Low presence for landmark $index: ${landmark.presence}",
                        index
                    ))
                }
            }

            if (landmark.visibility >= config.minVisibilityThreshold &&
                landmark.presence >= config.minPresenceThreshold) {
                validCount++
            }
        }

        // At least 70% of landmarks should be valid
        val validityRatio = validCount.toDouble() / totalCount
        return validityRatio >= 0.7
    }

    private fun validateCoreKeypoints(
        landmarks: List<PoseLandmarkResult.Landmark>,
        errors: MutableList<ValidationError>
    ): Boolean {
        var missingCoreKeypoints = 0

        for (requiredIndex in config.requiredCoreKeypoints) {
            if (requiredIndex >= landmarks.size) {
                errors.add(ValidationError(
                    ErrorType.MISSING_CORE_KEYPOINTS,
                    "Required keypoint $requiredIndex is missing from landmarks",
                    requiredIndex
                ))
                missingCoreKeypoints++
                continue
            }

            val landmark = landmarks[requiredIndex]
            if (landmark.visibility < config.minVisibilityThreshold ||
                landmark.presence < config.minPresenceThreshold) {
                missingCoreKeypoints++
            }
        }

        // Allow missing at most 1 core keypoint
        return missingCoreKeypoints <= 1
    }

    private fun validateAnatomicalConstraints(
        landmarks: List<PoseLandmarkResult.Landmark>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        try {
            // Check shoulder-hip alignment
            validateBodySymmetry(landmarks, errors, warnings)

            // Check limb proportions
            validateLimbProportions(landmarks, warnings)

            // Check joint angle ranges
            validateJointAngles(landmarks, warnings)

        } catch (e: Exception) {
            warnings.add(ValidationWarning(
                WarningType.UNUSUAL_POSE,
                "Could not complete anatomical validation: ${e.message}"
            ))
        }
    }

    private fun validateBodySymmetry(
        landmarks: List<PoseLandmarkResult.Landmark>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        if (landmarks.size <= maxOf(PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP)) return

        val leftShoulder = landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks[PoseLandmarks.RIGHT_HIP]

        // Check if body is extremely asymmetric
        val shoulderDistance = distance(leftShoulder, rightShoulder)
        val hipDistance = distance(leftHip, rightHip)

        if (shoulderDistance < 0.05 || hipDistance < 0.05) {
            errors.add(ValidationError(
                ErrorType.ANATOMICAL_IMPOSSIBILITY,
                "Body dimensions too small: shoulder=${shoulderDistance}, hip=${hipDistance}"
            ))
        }

        // Check torso alignment
        val shoulderMidpoint = midpoint(leftShoulder, rightShoulder)
        val hipMidpoint = midpoint(leftHip, rightHip)
        val torsoLength = distance(shoulderMidpoint, hipMidpoint)

        if (torsoLength < 0.1) {
            warnings.add(ValidationWarning(
                WarningType.UNUSUAL_POSE,
                "Unusually short torso detected: $torsoLength"
            ))
        }
    }

    private fun validateLimbProportions(
        landmarks: List<PoseLandmarkResult.Landmark>,
        warnings: MutableList<ValidationWarning>
    ) {
        if (landmarks.size <= maxOf(PoseLandmarks.LEFT_ANKLE, PoseLandmarks.RIGHT_ANKLE)) return

        try {
            // Check arm proportions
            val leftArmLength = distance(landmarks[PoseLandmarks.LEFT_SHOULDER], landmarks[PoseLandmarks.LEFT_ELBOW]) +
                              distance(landmarks[PoseLandmarks.LEFT_ELBOW], landmarks[PoseLandmarks.LEFT_WRIST])

            val rightArmLength = distance(landmarks[PoseLandmarks.RIGHT_SHOULDER], landmarks[PoseLandmarks.RIGHT_ELBOW]) +
                               distance(landmarks[PoseLandmarks.RIGHT_ELBOW], landmarks[PoseLandmarks.RIGHT_WRIST])

            if (abs(leftArmLength - rightArmLength) > 0.1) {
                warnings.add(ValidationWarning(
                    WarningType.UNUSUAL_POSE,
                    "Significant arm length difference: L=${leftArmLength}, R=${rightArmLength}"
                ))
            }

            // Check leg proportions
            val leftLegLength = distance(landmarks[PoseLandmarks.LEFT_HIP], landmarks[PoseLandmarks.LEFT_KNEE]) +
                              distance(landmarks[PoseLandmarks.LEFT_KNEE], landmarks[PoseLandmarks.LEFT_ANKLE])

            val rightLegLength = distance(landmarks[PoseLandmarks.RIGHT_HIP], landmarks[PoseLandmarks.RIGHT_KNEE]) +
                               distance(landmarks[PoseLandmarks.RIGHT_KNEE], landmarks[PoseLandmarks.RIGHT_ANKLE])

            if (abs(leftLegLength - rightLegLength) > 0.1) {
                warnings.add(ValidationWarning(
                    WarningType.UNUSUAL_POSE,
                    "Significant leg length difference: L=${leftLegLength}, R=${rightLegLength}"
                ))
            }
        } catch (e: Exception) {
            // Silently continue if some landmarks are missing
        }
    }

    private fun validateJointAngles(
        landmarks: List<PoseLandmarkResult.Landmark>,
        warnings: MutableList<ValidationWarning>
    ) {
        try {
            // Check elbow angles (should be between 0-180 degrees)
            val leftElbowAngle = calculateAngle(
                landmarks[PoseLandmarks.LEFT_SHOULDER],
                landmarks[PoseLandmarks.LEFT_ELBOW],
                landmarks[PoseLandmarks.LEFT_WRIST]
            )

            if (leftElbowAngle < 10 || leftElbowAngle > 170) {
                warnings.add(ValidationWarning(
                    WarningType.UNUSUAL_POSE,
                    "Unusual left elbow angle: ${leftElbowAngle}°"
                ))
            }

            // Check knee angles
            val leftKneeAngle = calculateAngle(
                landmarks[PoseLandmarks.LEFT_HIP],
                landmarks[PoseLandmarks.LEFT_KNEE],
                landmarks[PoseLandmarks.LEFT_ANKLE]
            )

            if (leftKneeAngle < 30 || leftKneeAngle > 170) {
                warnings.add(ValidationWarning(
                    WarningType.UNUSUAL_POSE,
                    "Unusual left knee angle: ${leftKneeAngle}°"
                ))
            }
        } catch (e: Exception) {
            // Silently continue if angle calculation fails
        }
    }

    private fun validateTemporalConsistency(
        current: PoseLandmarkResult,
        previous: PoseLandmarkResult,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ): TemporalValidationResult {
        val timeDelta = (current.timestampMs - previous.timestampMs) / 1000.0 // seconds
        var hasDiscontinuity = false

        if (timeDelta > 0.5) {
            warnings.add(ValidationWarning(
                WarningType.UNUSUAL_POSE,
                "Large time gap between frames: ${timeDelta}s"
            ))
            return TemporalValidationResult(false)
        }

        for (i in current.landmarks.indices) {
            if (i >= previous.landmarks.size) continue

            val currentLandmark = current.landmarks[i]
            val previousLandmark = previous.landmarks[i]

            val distance = distance(currentLandmark, previousLandmark)
            val velocity = if (timeDelta > 0) distance / timeDelta else 0.0

            // Check for impossible movements
            if (distance > config.maxTemporalJump) {
                hasDiscontinuity = true

                if (i in config.requiredCoreKeypoints) {
                    errors.add(ValidationError(
                        ErrorType.TEMPORAL_DISCONTINUITY,
                        "Core keypoint $i moved too far: ${distance} (max: ${config.maxTemporalJump})",
                        i
                    ))
                } else {
                    warnings.add(ValidationWarning(
                        WarningType.UNUSUAL_POSE,
                        "Landmark $i jumped: distance=${distance}, velocity=${velocity}",
                        i
                    ))
                }
            }
        }

        return TemporalValidationResult(hasDiscontinuity)
    }

    private fun applySmoothingFilters(
        landmarks: List<PoseLandmarkResult.Landmark>,
        appliedFilters: MutableList<String>,
        warnings: MutableList<ValidationWarning>
    ): List<PoseLandmarkResult.Landmark> {
        appliedFilters.add("Exponential Smoothing")
        warnings.add(ValidationWarning(WarningType.SMOOTHING_APPLIED, "Applied temporal smoothing"))

        return landmarks.mapIndexed { index, landmark ->
            val history = landmarkHistory[index]
            if (history != null && history.isNotEmpty()) {
                val previousLandmark = history.last()
                val alpha = config.smoothingStrength

                PoseLandmarkResult.Landmark(
                    x = (alpha * landmark.x + (1 - alpha) * previousLandmark.x),
                    y = (alpha * landmark.y + (1 - alpha) * previousLandmark.y),
                    z = (alpha * landmark.z + (1 - alpha) * previousLandmark.z),
                    visibility = landmark.visibility,
                    presence = landmark.presence
                )
            } else {
                landmark
            }
        }
    }

    private fun detectAndCorrectOutliers(
        landmarks: List<PoseLandmarkResult.Landmark>,
        appliedFilters: MutableList<String>,
        warnings: MutableList<ValidationWarning>
    ): List<PoseLandmarkResult.Landmark> {
        val correctedLandmarks = landmarks.toMutableList()

        for (i in landmarks.indices) {
            val landmark = landmarks[i]
            val history = landmarkHistory[i]

            if (history != null && history.size >= 3) {
                val recentPositions = history.takeLast(3)
                val avgX = recentPositions.map { it.x }.average().toFloat()
                val avgY = recentPositions.map { it.y }.average().toFloat()
                val avgZ = recentPositions.map { it.z }.average().toFloat()

                val distance = sqrt(
                    (landmark.x - avgX).pow(2) +
                    (landmark.y - avgY).pow(2) +
                    (landmark.z - avgZ).pow(2)
                )

                if (distance > config.maxOutlierDistance) {
                    // Replace with smoothed position
                    correctedLandmarks[i] = PoseLandmarkResult.Landmark(
                        x = avgX,
                        y = avgY,
                        z = avgZ,
                        visibility = landmark.visibility,
                        presence = landmark.presence
                    )

                    appliedFilters.add("Outlier Correction")
                    warnings.add(ValidationWarning(
                        WarningType.PREDICTION_USED,
                        "Corrected outlier for landmark $i: distance=$distance",
                        i
                    ))
                }
            }
        }

        return correctedLandmarks
    }

    private fun applyKalmanFiltering(
        landmarks: List<PoseLandmarkResult.Landmark>,
        timestampMs: Long,
        appliedFilters: MutableList<String>
    ): List<PoseLandmarkResult.Landmark> {
        appliedFilters.add("Kalman Filtering")

        return landmarks.mapIndexed { index, landmark ->
            val filter = kalmanFilters.getOrPut(index) { KalmanFilter() }
            val filtered = filter.update(landmark, timestampMs)
            filtered
        }
    }

    private fun calculateQualityScore(
        landmarks: List<PoseLandmarkResult.Landmark>,
        errors: List<ValidationError>,
        warnings: List<ValidationWarning>
    ): Double {
        val visibilityScore = landmarks.map { it.visibility }.average().toDouble()
        val presenceScore = landmarks.map { it.presence }.average().toDouble()

        val errorPenalty = errors.size * 0.2
        val warningPenalty = warnings.size * 0.05

        val baseScore = (visibilityScore + presenceScore) / 2.0
        val finalScore = (baseScore - errorPenalty - warningPenalty).coerceIn(0.0, 1.0)

        return finalScore
    }

    private fun updateLandmarkHistory(landmarks: List<PoseLandmarkResult.Landmark>) {
        for ((index, landmark) in landmarks.withIndex()) {
            val history = landmarkHistory.getOrPut(index) { mutableListOf() }
            history.add(landmark)

            if (history.size > maxHistorySize) {
                history.removeAt(0)
            }
        }
    }

    private fun distance(p1: PoseLandmarkResult.Landmark, p2: PoseLandmarkResult.Landmark): Float {
        return sqrt(
            (p1.x - p2.x).pow(2) +
            (p1.y - p2.y).pow(2) +
            (p1.z - p2.z).pow(2)
        )
    }

    private fun midpoint(p1: PoseLandmarkResult.Landmark, p2: PoseLandmarkResult.Landmark): PoseLandmarkResult.Landmark {
        return PoseLandmarkResult.Landmark(
            x = (p1.x + p2.x) / 2f,
            y = (p1.y + p2.y) / 2f,
            z = (p1.z + p2.z) / 2f,
            visibility = minOf(p1.visibility, p2.visibility),
            presence = minOf(p1.presence, p2.presence)
        )
    }

    private fun calculateAngle(p1: PoseLandmarkResult.Landmark, p2: PoseLandmarkResult.Landmark, p3: PoseLandmarkResult.Landmark): Double {
        val v1 = Triple(p1.x - p2.x, p1.y - p2.y, p1.z - p2.z)
        val v2 = Triple(p3.x - p2.x, p3.y - p2.y, p3.z - p2.z)

        val dotProduct = v1.first * v2.first + v1.second * v2.second + v1.third * v2.third
        val magnitude1 = sqrt(v1.first.pow(2) + v1.second.pow(2) + v1.third.pow(2))
        val magnitude2 = sqrt(v2.first.pow(2) + v2.second.pow(2) + v2.third.pow(2))

        val cosAngle = (dotProduct / (magnitude1 * magnitude2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosAngle.toDouble()))
    }

    /**
     * Reset all validation state.
     */
    fun reset() {
        landmarkHistory.clear()
        kalmanFilters.clear()
        lastValidPose = null
        Timber.d("Landmark validator reset")
    }

    private data class TemporalValidationResult(val hasDiscontinuity: Boolean)

    /**
     * Simple Kalman filter for landmark smoothing.
     */
    private class KalmanFilter {
        private var lastTimestamp = 0L
        private var x = 0f
        private var y = 0f
        private var z = 0f
        private var vx = 0f
        private var vy = 0f
        private var vz = 0f
        private var initialized = false

        fun update(landmark: PoseLandmarkResult.Landmark, timestampMs: Long): PoseLandmarkResult.Landmark {
            if (!initialized) {
                x = landmark.x
                y = landmark.y
                z = landmark.z
                lastTimestamp = timestampMs
                initialized = true
                return landmark
            }

            val dt = (timestampMs - lastTimestamp) / 1000f
            if (dt <= 0) return landmark

            // Predict
            x += vx * dt
            y += vy * dt
            z += vz * dt

            // Update with measurement
            val alpha = 0.3f // Smoothing factor
            x = alpha * landmark.x + (1 - alpha) * x
            y = alpha * landmark.y + (1 - alpha) * y
            z = alpha * landmark.z + (1 - alpha) * z

            // Update velocity
            vx = (landmark.x - x) / dt
            vy = (landmark.y - y) / dt
            vz = (landmark.z - z) / dt

            lastTimestamp = timestampMs

            return PoseLandmarkResult.Landmark(
                x = x,
                y = y,
                z = z,
                visibility = landmark.visibility,
                presence = landmark.presence
            )
        }
    }
}