package com.posecoach.corepose.validation

import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.PoseLandmarks
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for LandmarkValidator.
 * Tests validation logic, filtering algorithms, and error handling.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LandmarkValidatorTest {

    private lateinit var validator: LandmarkValidator
    private var testTimestamp = System.currentTimeMillis()

    private val strictConfig = LandmarkValidator.ValidationConfig(
        minVisibilityThreshold = 0.8f,
        minPresenceThreshold = 0.9f,
        maxOutlierDistance = 0.1f,
        maxTemporalJump = 0.05f,
        smoothingStrength = 0.2f
    )

    private val relaxedConfig = LandmarkValidator.ValidationConfig(
        minVisibilityThreshold = 0.3f,
        minPresenceThreshold = 0.5f,
        maxOutlierDistance = 0.3f,
        maxTemporalJump = 0.2f,
        smoothingStrength = 0.5f
    )

    @BeforeEach
    fun setup() {
        validator = LandmarkValidator()
        testTimestamp = System.currentTimeMillis()
    }

    @AfterEach
    fun tearDown() {
        validator.reset()
    }

    @Nested
    @DisplayName("Basic Validation Tests")
    inner class BasicValidationTests {

        @Test
        fun `should validate high-quality pose successfully`() {
            // Given
            val highQualityPose = createHighQualityPoseResult()

            // When
            val result = validator.validateAndFilter(highQualityPose)

            // Then
            assertTrue(result.isValid, "High-quality pose should be valid")
            assertTrue(result.qualityScore > 0.8, "Quality score should be high")
            assertTrue(result.errors.isEmpty(), "Should have no errors")
            assertEquals(33, result.filteredLandmarks.size, "Should maintain all landmarks")
        }

        @Test
        fun `should reject pose with insufficient visibility`() {
            // Given
            val lowVisibilityPose = createLowVisibilityPoseResult()
            validator = LandmarkValidator(strictConfig)

            // When
            val result = validator.validateAndFilter(lowVisibilityPose)

            // Then
            assertFalse(result.isValid, "Low visibility pose should be invalid")
            assertTrue(result.errors.any { it.type == LandmarkValidator.ErrorType.INSUFFICIENT_VISIBILITY },
                "Should have visibility errors")
            assertTrue(result.qualityScore < 0.6, "Quality score should be low")
        }

        @Test
        fun `should reject pose with missing core keypoints`() {
            // Given
            val missingCoreKeypoints = createPoseWithMissingCoreKeypoints()

            // When
            val result = validator.validateAndFilter(missingCoreKeypoints)

            // Then
            assertFalse(result.isValid, "Pose with missing core keypoints should be invalid")
            assertTrue(result.errors.any { it.type == LandmarkValidator.ErrorType.MISSING_CORE_KEYPOINTS },
                "Should have missing keypoint errors")
        }

        @Test
        fun `should validate pose with warnings but no errors`() {
            // Given
            val marginalPose = createMarginalQualityPoseResult()

            // When
            val result = validator.validateAndFilter(marginalPose)

            // Then
            assertTrue(result.isValid, "Marginal pose should be valid")
            assertTrue(result.warnings.isNotEmpty(), "Should have warnings")
            assertTrue(result.errors.isEmpty(), "Should have no errors")
        }
    }

    @Nested
    @DisplayName("Anatomical Validation Tests")
    inner class AnatomicalValidationTests {

        @Test
        fun `should detect anatomically impossible pose`() {
            // Given
            val impossiblePose = createAnatomicallyImpossiblePose()
            val config = LandmarkValidator.ValidationConfig(enableAnatomicalValidation = true)
            validator = LandmarkValidator(config)

            // When
            val result = validator.validateAndFilter(impossiblePose)

            // Then
            assertTrue(result.errors.any { it.type == LandmarkValidator.ErrorType.ANATOMICAL_IMPOSSIBILITY } ||
                      result.warnings.any { it.type == LandmarkValidator.WarningType.UNUSUAL_POSE },
                "Should detect anatomical problems")
        }

        @Test
        fun `should validate normal human proportions`() {
            // Given
            val normalPose = createRealisticPoseResult()
            val config = LandmarkValidator.ValidationConfig(enableAnatomicalValidation = true)
            validator = LandmarkValidator(config)

            // When
            val result = validator.validateAndFilter(normalPose)

            // Then
            assertTrue(result.isValid, "Normal pose should be valid")
            assertFalse(result.errors.any { it.type == LandmarkValidator.ErrorType.ANATOMICAL_IMPOSSIBILITY },
                "Should not have anatomical errors for normal pose")
        }

        @Test
        fun `should detect asymmetric pose`() {
            // Given
            val asymmetricPose = createAsymmetricPoseResult()
            val config = LandmarkValidator.ValidationConfig(enableAnatomicalValidation = true)
            validator = LandmarkValidator(config)

            // When
            val result = validator.validateAndFilter(asymmetricPose)

            // Then
            assertTrue(result.warnings.any { it.type == LandmarkValidator.WarningType.UNUSUAL_POSE },
                "Should warn about asymmetric pose")
        }

        @Test
        fun `should validate joint angle ranges`() {
            // Given
            val extremeJointPose = createExtremeJointAnglePose()
            val config = LandmarkValidator.ValidationConfig(enableAnatomicalValidation = true)
            validator = LandmarkValidator(config)

            // When
            val result = validator.validateAndFilter(extremeJointPose)

            // Then
            assertTrue(result.warnings.any { it.type == LandmarkValidator.WarningType.UNUSUAL_POSE },
                "Should warn about extreme joint angles")
        }
    }

    @Nested
    @DisplayName("Temporal Validation Tests")
    inner class TemporalValidationTests {

        @Test
        fun `should detect temporal discontinuity`() {
            // Given
            val config = LandmarkValidator.ValidationConfig(enableTemporalValidation = true)
            validator = LandmarkValidator(config)

            val pose1 = createHighQualityPoseResult()
            val pose2 = createJumpedPoseResult() // Significant movement

            // When
            validator.validateAndFilter(pose1) // Establish baseline
            val result = validator.validateAndFilter(pose2)

            // Then
            assertTrue(result.errors.any { it.type == LandmarkValidator.ErrorType.TEMPORAL_DISCONTINUITY } ||
                      result.warnings.any { it.type == LandmarkValidator.WarningType.UNUSUAL_POSE },
                "Should detect temporal discontinuity")
        }

        @Test
        fun `should apply temporal smoothing`() {
            // Given
            val config = LandmarkValidator.ValidationConfig(
                enableTemporalValidation = true,
                smoothingStrength = 0.5f
            )
            validator = LandmarkValidator(config)

            val stablePose = createHighQualityPoseResult()
            val jumpedPose = createJumpedPoseResult()

            // When
            validator.validateAndFilter(stablePose) // Establish baseline
            val result = validator.validateAndFilter(jumpedPose)

            // Then
            assertTrue(result.appliedFilters.contains("Exponential Smoothing"),
                "Should apply temporal smoothing")
            assertTrue(result.warnings.any { it.type == LandmarkValidator.WarningType.SMOOTHING_APPLIED },
                "Should warn about smoothing")

            // Filtered landmarks should be between original and previous
            val originalX = jumpedPose.landmarks[0].x
            val stableX = stablePose.landmarks[0].x
            val filteredX = result.filteredLandmarks[0].x

            assertTrue(filteredX != originalX, "Should modify landmark position")
            assertTrue((filteredX - stableX) * (originalX - stableX) >= 0,
                "Filtered position should be between stable and original")
        }

        @Test
        fun `should handle large time gaps`() {
            // Given
            val config = LandmarkValidator.ValidationConfig(enableTemporalValidation = true)
            validator = LandmarkValidator(config)

            val pose1 = createHighQualityPoseResult()
            val pose2 = pose1.copy(timestampMs = pose1.timestampMs + 10000L) // 10 second gap

            // When
            validator.validateAndFilter(pose1)
            val result = validator.validateAndFilter(pose2)

            // Then
            assertTrue(result.warnings.any { it.type == LandmarkValidator.WarningType.UNUSUAL_POSE },
                "Should warn about large time gap")
        }
    }

    @Nested
    @DisplayName("Filtering and Enhancement Tests")
    inner class FilteringTests {

        @Test
        fun `should detect and correct outliers`() {
            // Given
            repeat(5) { // Build history
                val pose = createHighQualityPoseResult()
                validator.validateAndFilter(pose)
            }

            val outlierPose = createOutlierPoseResult()

            // When
            val result = validator.validateAndFilter(outlierPose)

            // Then
            assertTrue(result.appliedFilters.contains("Outlier Correction"),
                "Should apply outlier correction")
            assertTrue(result.warnings.any { it.type == LandmarkValidator.WarningType.PREDICTION_USED },
                "Should warn about prediction usage")

            // Corrected landmarks should be different from original outliers
            assertNotEquals(outlierPose.landmarks[0].x, result.filteredLandmarks[0].x,
                "Should correct outlier landmarks")
        }

        @Test
        fun `should apply Kalman filtering when enabled`() {
            // Given
            val config = LandmarkValidator.ValidationConfig(enableKalmanFiltering = true)
            validator = LandmarkValidator(config)

            // When
            repeat(3) {
                val pose = createHighQualityPoseResult()
                val result = validator.validateAndFilter(pose)

                if (it > 0) { // Kalman should be applied after first frame
                    assertTrue(result.appliedFilters.contains("Kalman Filtering"),
                        "Should apply Kalman filtering")
                }
            }
        }

        @Test
        fun `should maintain landmark count after filtering`() {
            // Given
            val noisyPose = createNoisyPoseResult()

            // When
            val result = validator.validateAndFilter(noisyPose)

            // Then
            assertEquals(noisyPose.landmarks.size, result.filteredLandmarks.size,
                "Should maintain landmark count after filtering")
        }

        @Test
        fun `should preserve landmark visibility and presence`() {
            // Given
            val pose = createHighQualityPoseResult()

            // When
            val result = validator.validateAndFilter(pose)

            // Then
            for (i in pose.landmarks.indices) {
                assertEquals(pose.landmarks[i].visibility, result.filteredLandmarks[i].visibility,
                    "Should preserve visibility")
                assertEquals(pose.landmarks[i].presence, result.filteredLandmarks[i].presence,
                    "Should preserve presence")
            }
        }
    }

    @Nested
    @DisplayName("Quality Score Tests")
    inner class QualityScoreTests {

        @Test
        fun `should calculate quality score based on visibility and presence`() {
            // Given
            val highQualityPose = createHighQualityPoseResult()
            val lowQualityPose = createLowVisibilityPoseResult()

            // When
            val highResult = validator.validateAndFilter(highQualityPose)
            val lowResult = validator.validateAndFilter(lowQualityPose)

            // Then
            assertTrue(highResult.qualityScore > lowResult.qualityScore,
                "High quality pose should have higher score")
            assertTrue(highResult.qualityScore > 0.8, "High quality score should be > 0.8")
            assertTrue(lowResult.qualityScore < 0.5, "Low quality score should be < 0.5")
        }

        @Test
        fun `should penalize quality score for errors and warnings`() {
            // Given
            val perfectPose = createHighQualityPoseResult()
            val problematicPose = createPoseWithMissingCoreKeypoints()

            // When
            val perfectResult = validator.validateAndFilter(perfectPose)
            val problematicResult = validator.validateAndFilter(problematicPose)

            // Then
            assertTrue(perfectResult.qualityScore > problematicResult.qualityScore,
                "Perfect pose should have higher quality score")

            assertTrue(problematicResult.qualityScore < 0.6,
                "Problematic pose should have reduced quality score due to errors")
        }

        @Test
        fun `should bound quality score between 0 and 1`() {
            // Given
            val extremelyBadPose = createExtremeleBadPoseResult()

            // When
            val result = validator.validateAndFilter(extremelyBadPose)

            // Then
            assertTrue(result.qualityScore >= 0.0, "Quality score should be >= 0")
            assertTrue(result.qualityScore <= 1.0, "Quality score should be <= 1")
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    inner class ConfigurationTests {

        @Test
        fun `should respect strict configuration`() {
            // Given
            validator = LandmarkValidator(strictConfig)
            val marginalPose = createMarginalQualityPoseResult()

            // When
            val result = validator.validateAndFilter(marginalPose)

            // Then
            assertTrue(result.errors.isNotEmpty() || result.qualityScore < 0.6,
                "Strict config should reject marginal pose")
        }

        @Test
        fun `should respect relaxed configuration`() {
            // Given
            validator = LandmarkValidator(relaxedConfig)
            val marginalPose = createMarginalQualityPoseResult()

            // When
            val result = validator.validateAndFilter(marginalPose)

            // Then
            assertTrue(result.isValid, "Relaxed config should accept marginal pose")
            assertTrue(result.errors.isEmpty(), "Should have no errors with relaxed config")
        }

        @Test
        fun `should disable features when configured`() {
            // Given
            val config = LandmarkValidator.ValidationConfig(
                enableAnatomicalValidation = false,
                enableTemporalValidation = false,
                enableKalmanFiltering = false
            )
            validator = LandmarkValidator(config)

            val badPose = createAnatomicallyImpossiblePose()

            // When
            val result = validator.validateAndFilter(badPose)

            // Then
            assertFalse(result.errors.any { it.type == LandmarkValidator.ErrorType.ANATOMICAL_IMPOSSIBILITY },
                "Should not validate anatomy when disabled")
            assertFalse(result.appliedFilters.contains("Kalman Filtering"),
                "Should not apply Kalman when disabled")
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    inner class EdgeCasesTests {

        @Test
        fun `should handle empty landmarks list`() {
            // Given
            val emptyPose = PoseLandmarkResult(
                landmarks = emptyList(),
                worldLandmarks = emptyList(),
                timestampMs = testTimestamp,
                inferenceTimeMs = 25L
            )

            // When/Then - should not throw exception
            assertDoesNotThrow {
                validator.validateAndFilter(emptyPose)
            }
        }

        @Test
        fun `should handle invalid landmark coordinates`() {
            // Given
            val invalidPose = createInvalidCoordinatePoseResult()

            // When/Then - should handle gracefully
            assertDoesNotThrow {
                val result = validator.validateAndFilter(invalidPose)
                assertNotNull(result)
            }
        }

        @Test
        fun `should handle extreme timestamp values`() {
            // Given
            val extremeTimestamps = listOf(0L, Long.MAX_VALUE, -1L)

            // When/Then
            assertDoesNotThrow {
                extremeTimestamps.forEach { timestamp ->
                    val pose = createHighQualityPoseResult().copy(timestampMs = timestamp)
                    validator.validateAndFilter(pose)
                }
            }
        }

        @Test
        fun `should handle insufficient landmarks for anatomy validation`() {
            // Given
            val incompletePose = PoseLandmarkResult(
                landmarks = listOf(
                    PoseLandmarkResult.Landmark(0.5f, 0.5f, 0.1f, 0.9f, 0.9f)
                ), // Only one landmark
                worldLandmarks = listOf(
                    PoseLandmarkResult.Landmark(0.5f, 0.5f, 0.1f, 0.9f, 0.9f)
                ),
                timestampMs = testTimestamp,
                inferenceTimeMs = 25L
            )

            val config = LandmarkValidator.ValidationConfig(enableAnatomicalValidation = true)
            validator = LandmarkValidator(config)

            // When/Then - should not crash
            assertDoesNotThrow {
                validator.validateAndFilter(incompletePose)
            }
        }
    }

    @Nested
    @DisplayName("State Management Tests")
    inner class StateManagementTests {

        @Test
        fun `should reset state correctly`() {
            // Given - build some history
            repeat(5) {
                val pose = createHighQualityPoseResult()
                validator.validateAndFilter(pose)
            }

            // When
            validator.reset()
            val pose = createHighQualityPoseResult()
            val result = validator.validateAndFilter(pose)

            // Then
            assertFalse(result.appliedFilters.contains("Temporal Smoothing"),
                "Should not apply temporal filtering after reset")
            assertFalse(result.warnings.any { it.type == LandmarkValidator.WarningType.SMOOTHING_APPLIED },
                "Should not have smoothing warnings after reset")
        }

        @Test
        fun `should maintain history for consistent processing`() {
            // Given
            repeat(3) {
                val pose = createHighQualityPoseResult()
                validator.validateAndFilter(pose)
            }

            // When - introduce outlier
            val outlierPose = createOutlierPoseResult()
            val result = validator.validateAndFilter(outlierPose)

            // Then - should use history for outlier detection
            assertTrue(result.appliedFilters.isNotEmpty() ||
                      result.warnings.isNotEmpty(),
                "Should use history for validation")
        }
    }

    // Helper functions for creating test data

    private fun createHighQualityPoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f,
                y = 0.2f + (index / 5) * 0.1f,
                z = 0.1f,
                visibility = 0.95f,
                presence = 0.98f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createLowVisibilityPoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f,
                y = 0.2f + (index / 5) * 0.1f,
                z = 0.1f,
                visibility = 0.2f, // Very low visibility
                presence = 0.3f    // Very low presence
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createPoseWithMissingCoreKeypoints(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            val visibility = if (index in setOf(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_HIP)) {
                0.1f // Very low for core keypoints
            } else {
                0.9f
            }

            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f,
                y = 0.2f + (index / 5) * 0.1f,
                z = 0.1f,
                visibility = visibility,
                presence = visibility
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createMarginalQualityPoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f,
                y = 0.2f + (index / 5) * 0.1f,
                z = 0.1f,
                visibility = 0.6f, // Marginal visibility
                presence = 0.7f    // Marginal presence
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createAnatomicallyImpossiblePose(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = if (index < 16) 0.1f else 0.9f, // Extremely separated body parts
                y = 0.5f,
                z = if (index % 2 == 0) -1.0f else 2.0f, // Impossible Z coordinates
                visibility = 0.9f,
                presence = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createRealisticPoseResult(): PoseLandmarkResult {
        // Create a realistic human pose with proper proportions
        val landmarks = mutableListOf<PoseLandmarkResult.Landmark>()

        // Head region
        landmarks.addAll((0..10).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index - 5) * 0.01f,
                y = 0.1f + index * 0.01f,
                z = 0.0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        })

        // Body region
        landmarks.addAll((11..24).map { index ->
            val bodyY = 0.2f + (index - 11) * 0.05f
            PoseLandmarkResult.Landmark(
                x = 0.5f + if (index % 2 == 0) -0.1f else 0.1f, // Left/right alternating
                y = bodyY,
                z = 0.0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        })

        // Legs region
        landmarks.addAll((25..32).map { index ->
            val legY = 0.6f + (index - 25) * 0.05f
            PoseLandmarkResult.Landmark(
                x = 0.5f + if (index % 2 == 0) -0.05f else 0.05f,
                y = legY,
                z = 0.0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        })

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createAsymmetricPoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            val asymmetryFactor = if (index % 2 == 0) 1.0f else 0.5f // Left side different from right
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f * asymmetryFactor,
                y = 0.2f + (index / 5) * 0.1f * asymmetryFactor,
                z = 0.1f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createExtremeJointAnglePose(): PoseLandmarkResult {
        val landmarks = mutableListOf<PoseLandmarkResult.Landmark>()

        // Create pose with extreme elbow angle (nearly 0 degrees)
        landmarks.add(PoseLandmarkResult.Landmark(0.4f, 0.3f, 0.0f, 0.9f, 0.9f)) // Left shoulder
        landmarks.add(PoseLandmarkResult.Landmark(0.4f, 0.4f, 0.0f, 0.9f, 0.9f)) // Left elbow
        landmarks.add(PoseLandmarkResult.Landmark(0.4f, 0.45f, 0.0f, 0.9f, 0.9f)) // Left wrist (very close)

        // Fill rest with normal landmarks
        repeat(30) { index ->
            landmarks.add(PoseLandmarkResult.Landmark(
                x = 0.5f + (index % 5) * 0.1f,
                y = 0.5f + (index / 5) * 0.1f,
                z = 0.1f,
                visibility = 0.9f,
                presence = 0.9f
            ))
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createJumpedPoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.8f + (index % 5) * 0.1f, // Significant jump from normal position
                y = 0.8f + (index / 5) * 0.1f,
                z = 0.5f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp + 33L,
            inferenceTimeMs = 25L
        )
    }

    private fun createOutlierPoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = if (index == 0) 2.0f else 0.3f + (index % 5) * 0.1f, // First landmark is outlier
                y = if (index == 0) 2.0f else 0.2f + (index / 5) * 0.1f,
                z = 0.1f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createNoisyPoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            val noise = (Math.random() - 0.5).toFloat() * 0.02f // Small random noise
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f + noise,
                y = 0.2f + (index / 5) * 0.1f + noise,
                z = 0.1f + noise,
                visibility = 0.9f,
                presence = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createExtremeleBadPoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = Float.NaN,
                y = Float.POSITIVE_INFINITY,
                z = Float.NEGATIVE_INFINITY,
                visibility = 0.0f,
                presence = 0.0f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }

    private fun createInvalidCoordinatePoseResult(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = when (index % 4) {
                    0 -> Float.NaN
                    1 -> Float.POSITIVE_INFINITY
                    2 -> Float.NEGATIVE_INFINITY
                    else -> 0.5f
                },
                y = 0.5f,
                z = 0.1f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = testTimestamp,
            inferenceTimeMs = 25L
        )
    }
}