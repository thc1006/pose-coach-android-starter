package com.posecoach.testing.utils

import android.content.Context
import android.graphics.Bitmap
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.testing.framework.coverage.CoverageTracker
import com.posecoach.testing.framework.performance.PerformanceTestOrchestrator
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.random.Random

/**
 * Utility functions for testing infrastructure
 * Provides common test data generation, validation helpers, and test utilities
 */
object TestUtils {

    /**
     * Generate realistic pose landmarks for testing
     */
    fun generateRealisticPoseLandmarks(
        poseType: PoseType = PoseType.STANDING_NEUTRAL,
        confidence: Float = 0.9f,
        addNoise: Boolean = false
    ): List<PoseLandmarkResult.Landmark> {
        val baseLandmarks = when (poseType) {
            PoseType.STANDING_NEUTRAL -> generateStandingNeutralPose()
            PoseType.SITTING -> generateSittingPose()
            PoseType.YOGA_TREE -> generateYogaTreePose()
            PoseType.ARMS_RAISED -> generateArmsRaisedPose()
            PoseType.SIDE_PROFILE -> generateSideProfilePose()
            PoseType.POOR_POSTURE -> generatePoorPosturePose()
        }

        return if (addNoise) {
            addNoiseToLandmarks(baseLandmarks, noiseLevel = 0.02f)
        } else {
            baseLandmarks
        }.map { landmark ->
            landmark.copy(visibility = confidence)
        }
    }

    /**
     * Generate complete pose landmark result
     */
    fun generatePoseLandmarkResult(
        poseType: PoseType = PoseType.STANDING_NEUTRAL,
        confidence: Float = 0.9f,
        imageWidth: Int = 1920,
        imageHeight: Int = 1080,
        addNoise: Boolean = false
    ): PoseLandmarkResult {
        return PoseLandmarkResult(
            landmarks = generateRealisticPoseLandmarks(poseType, confidence, addNoise),
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    /**
     * Generate test pose suggestions
     */
    fun generateTestPoseSuggestions(
        suggestionType: SuggestionType = SuggestionType.FORM_CORRECTION,
        count: Int = 3
    ): List<PoseSuggestion> {
        val suggestions = mutableListOf<PoseSuggestion>()

        repeat(count) { index ->
            suggestions.add(
                when (suggestionType) {
                    SuggestionType.FORM_CORRECTION -> generateFormCorrectionSuggestion(index)
                    SuggestionType.ENCOURAGEMENT -> generateEncouragementSuggestion(index)
                    SuggestionType.SAFETY -> generateSafetySuggestion(index)
                    SuggestionType.PROGRESSION -> generateProgressionSuggestion(index)
                }
            )
        }

        return suggestions
    }

    /**
     * Validate pose landmarks for correctness
     */
    fun validatePoseLandmarks(landmarks: List<PoseLandmarkResult.Landmark>): ValidationResult {
        val errors = mutableListOf<String>()

        // Check landmark count
        if (landmarks.size != 33) {
            errors.add("Expected 33 landmarks, got ${landmarks.size}")
        }

        // Check coordinate ranges
        landmarks.forEachIndexed { index, landmark ->
            if (landmark.x < 0f || landmark.x > 1f) {
                errors.add("Landmark $index x-coordinate out of range: ${landmark.x}")
            }
            if (landmark.y < 0f || landmark.y > 1f) {
                errors.add("Landmark $index y-coordinate out of range: ${landmark.y}")
            }
            if (landmark.visibility < 0f || landmark.visibility > 1f) {
                errors.add("Landmark $index visibility out of range: ${landmark.visibility}")
            }
        }

        // Check anatomical consistency
        validateAnatomicalConsistency(landmarks)?.let { error ->
            errors.add(error)
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Create test bitmap for image processing tests
     */
    fun createTestBitmap(
        width: Int = 1920,
        height: Int = 1080,
        content: BitmapContent = BitmapContent.SOLID_COLOR
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        when (content) {
            BitmapContent.SOLID_COLOR -> {
                canvas.drawColor(android.graphics.Color.BLUE)
            }
            BitmapContent.GRADIENT -> {
                val paint = android.graphics.Paint()
                val gradient = android.graphics.LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    android.graphics.Color.RED, android.graphics.Color.BLUE,
                    android.graphics.Shader.TileMode.CLAMP
                )
                paint.shader = gradient
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            BitmapContent.NOISE -> {
                val paint = android.graphics.Paint()
                for (x in 0 until width step 4) {
                    for (y in 0 until height step 4) {
                        paint.color = android.graphics.Color.rgb(
                            Random.nextInt(256),
                            Random.nextInt(256),
                            Random.nextInt(256)
                        )
                        canvas.drawRect(x.toFloat(), y.toFloat(), (x + 4).toFloat(), (y + 4).toFloat(), paint)
                    }
                }
            }
        }

        return bitmap
    }

    /**
     * Wait for condition with timeout
     */
    suspend fun waitForCondition(
        timeoutMs: Long = 5000L,
        intervalMs: Long = 100L,
        condition: suspend () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) {
                return true
            }
            delay(intervalMs)
        }

        return false
    }

    /**
     * Record test execution with coverage and performance tracking
     */
    inline fun <T> recordTestExecution(
        testName: String,
        className: String = "TestUtils",
        block: () -> T
    ): T {
        CoverageTracker.recordMethodExecution(className, testName)
        return PerformanceTestOrchestrator.measureMethod(testName, block)
    }

    /**
     * Record test execution for suspend functions
     */
    suspend inline fun <T> recordSuspendTestExecution(
        testName: String,
        className: String = "TestUtils",
        crossinline block: suspend () -> T
    ): T {
        CoverageTracker.recordMethodExecution(className, testName)
        return PerformanceTestOrchestrator.measureSuspendMethod(testName, block)
    }

    /**
     * Assert with detailed error message
     */
    fun assertWithDetails(
        condition: Boolean,
        message: String,
        details: Map<String, Any> = emptyMap()
    ) {
        if (!condition) {
            val detailsString = details.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            val fullMessage = if (details.isNotEmpty()) {
                "$message. Details: $detailsString"
            } else {
                message
            }
            throw AssertionError(fullMessage)
        }
    }

    /**
     * Log test results in standardized format
     */
    fun logTestResult(
        testName: String,
        success: Boolean,
        executionTimeMs: Long,
        details: Map<String, Any> = emptyMap()
    ) {
        val status = if (success) "PASS" else "FAIL"
        val detailsString = details.entries.joinToString(", ") { "${it.key}: ${it.value}" }

        Timber.i("TEST_RESULT: [$status] $testName (${executionTimeMs}ms)")
        if (details.isNotEmpty()) {
            Timber.i("TEST_DETAILS: $detailsString")
        }
    }

    // Private helper methods

    private fun generateStandingNeutralPose(): List<PoseLandmarkResult.Landmark> {
        return listOf(
            // Head landmarks (0-10)
            PoseLandmarkResult.Landmark(0.5f, 0.15f, 0.0f, 0.95f), // nose
            PoseLandmarkResult.Landmark(0.48f, 0.12f, 0.01f, 0.90f), // left eye inner
            PoseLandmarkResult.Landmark(0.46f, 0.12f, 0.01f, 0.90f), // left eye
            PoseLandmarkResult.Landmark(0.44f, 0.12f, 0.01f, 0.85f), // left eye outer
            PoseLandmarkResult.Landmark(0.52f, 0.12f, 0.01f, 0.90f), // right eye inner
            PoseLandmarkResult.Landmark(0.54f, 0.12f, 0.01f, 0.90f), // right eye
            PoseLandmarkResult.Landmark(0.56f, 0.12f, 0.01f, 0.85f), // right eye outer
            PoseLandmarkResult.Landmark(0.42f, 0.14f, 0.02f, 0.80f), // left ear
            PoseLandmarkResult.Landmark(0.58f, 0.14f, 0.02f, 0.80f), // right ear
            PoseLandmarkResult.Landmark(0.47f, 0.18f, 0.01f, 0.85f), // mouth left
            PoseLandmarkResult.Landmark(0.53f, 0.18f, 0.01f, 0.85f), // mouth right

            // Shoulder landmarks (11-12)
            PoseLandmarkResult.Landmark(0.38f, 0.32f, 0.0f, 0.95f), // left shoulder
            PoseLandmarkResult.Landmark(0.62f, 0.32f, 0.0f, 0.95f), // right shoulder

            // Arm landmarks (13-22)
            PoseLandmarkResult.Landmark(0.32f, 0.45f, -0.05f, 0.90f), // left elbow
            PoseLandmarkResult.Landmark(0.68f, 0.45f, -0.05f, 0.90f), // right elbow
            PoseLandmarkResult.Landmark(0.30f, 0.58f, -0.08f, 0.85f), // left wrist
            PoseLandmarkResult.Landmark(0.70f, 0.58f, -0.08f, 0.85f), // right wrist
            PoseLandmarkResult.Landmark(0.29f, 0.60f, -0.10f, 0.80f), // left pinky
            PoseLandmarkResult.Landmark(0.71f, 0.60f, -0.10f, 0.80f), // right pinky
            PoseLandmarkResult.Landmark(0.28f, 0.59f, -0.09f, 0.80f), // left index
            PoseLandmarkResult.Landmark(0.72f, 0.59f, -0.09f, 0.80f), // right index
            PoseLandmarkResult.Landmark(0.29f, 0.61f, -0.10f, 0.75f), // left thumb
            PoseLandmarkResult.Landmark(0.71f, 0.61f, -0.10f, 0.75f), // right thumb

            // Hip landmarks (23-24)
            PoseLandmarkResult.Landmark(0.42f, 0.65f, 0.0f, 0.95f), // left hip
            PoseLandmarkResult.Landmark(0.58f, 0.65f, 0.0f, 0.95f), // right hip

            // Leg landmarks (25-32)
            PoseLandmarkResult.Landmark(0.41f, 0.78f, 0.02f, 0.90f), // left knee
            PoseLandmarkResult.Landmark(0.59f, 0.78f, 0.02f, 0.90f), // right knee
            PoseLandmarkResult.Landmark(0.40f, 0.92f, 0.05f, 0.85f), // left ankle
            PoseLandmarkResult.Landmark(0.60f, 0.92f, 0.05f, 0.85f), // right ankle
            PoseLandmarkResult.Landmark(0.38f, 0.95f, 0.08f, 0.80f), // left heel
            PoseLandmarkResult.Landmark(0.62f, 0.95f, 0.08f, 0.80f), // right heel
            PoseLandmarkResult.Landmark(0.37f, 0.93f, 0.10f, 0.75f), // left foot index
            PoseLandmarkResult.Landmark(0.63f, 0.93f, 0.10f, 0.75f)  // right foot index
        )
    }

    private fun generateSittingPose(): List<PoseLandmarkResult.Landmark> {
        // Similar to standing but with different hip/leg positioning
        val standing = generateStandingNeutralPose()
        return standing.mapIndexed { index, landmark ->
            when (index) {
                in 23..32 -> { // Adjust leg positions for sitting
                    when (index) {
                        25, 26 -> landmark.copy(y = 0.70f) // knees higher
                        27, 28 -> landmark.copy(y = 0.75f) // ankles closer
                        else -> landmark
                    }
                }
                else -> landmark
            }
        }
    }

    private fun generateYogaTreePose(): List<PoseLandmarkResult.Landmark> {
        val standing = generateStandingNeutralPose()
        return standing.mapIndexed { index, landmark ->
            when (index) {
                25 -> landmark.copy(x = 0.55f, y = 0.72f) // left knee to side
                27 -> landmark.copy(x = 0.58f, y = 0.75f) // left ankle on right leg
                13, 15 -> landmark.copy(y = landmark.y - 0.15f) // arms raised
                else -> landmark
            }
        }
    }

    private fun generateArmsRaisedPose(): List<PoseLandmarkResult.Landmark> {
        val standing = generateStandingNeutralPose()
        return standing.mapIndexed { index, landmark ->
            when (index) {
                in 13..22 -> { // Adjust arm positions
                    when (index) {
                        13 -> landmark.copy(y = 0.25f) // left elbow up
                        14 -> landmark.copy(y = 0.25f) // right elbow up
                        15 -> landmark.copy(y = 0.20f) // left wrist up
                        16 -> landmark.copy(y = 0.20f) // right wrist up
                        else -> landmark.copy(y = landmark.y - 0.15f)
                    }
                }
                else -> landmark
            }
        }
    }

    private fun generateSideProfilePose(): List<PoseLandmarkResult.Landmark> {
        val standing = generateStandingNeutralPose()
        return standing.map { landmark ->
            landmark.copy(z = landmark.z + 0.3f) // Shift to side view
        }
    }

    private fun generatePoorPosturePose(): List<PoseLandmarkResult.Landmark> {
        val standing = generateStandingNeutralPose()
        return standing.mapIndexed { index, landmark ->
            when (index) {
                0 -> landmark.copy(y = landmark.y + 0.05f) // head forward
                11, 12 -> landmark.copy(y = landmark.y + 0.03f) // shoulders hunched
                else -> landmark
            }
        }
    }

    private fun addNoiseToLandmarks(
        landmarks: List<PoseLandmarkResult.Landmark>,
        noiseLevel: Float
    ): List<PoseLandmarkResult.Landmark> {
        return landmarks.map { landmark ->
            landmark.copy(
                x = landmark.x + (Random.nextFloat() - 0.5f) * noiseLevel,
                y = landmark.y + (Random.nextFloat() - 0.5f) * noiseLevel,
                z = landmark.z + (Random.nextFloat() - 0.5f) * noiseLevel
            )
        }
    }

    private fun validateAnatomicalConsistency(landmarks: List<PoseLandmarkResult.Landmark>): String? {
        if (landmarks.size < 33) return "Insufficient landmarks for validation"

        // Check shoulder alignment
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val shoulderDiff = kotlin.math.abs(leftShoulder.y - rightShoulder.y)
        if (shoulderDiff > 0.3f) {
            return "Anatomically inconsistent shoulder alignment"
        }

        // Check hip alignment
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val hipDiff = kotlin.math.abs(leftHip.y - rightHip.y)
        if (hipDiff > 0.3f) {
            return "Anatomically inconsistent hip alignment"
        }

        return null
    }

    private fun generateFormCorrectionSuggestion(index: Int): PoseSuggestion {
        val suggestions = listOf(
            "Straighten your back and lift your chest",
            "Keep your shoulders level and relaxed",
            "Align your head over your shoulders",
            "Engage your core muscles",
            "Distribute weight evenly on both feet"
        )

        return PoseSuggestion(
            title = "Form Correction",
            description = suggestions[index % suggestions.size],
            priority = PoseSuggestion.Priority.HIGH,
            category = PoseSuggestion.Category.FORM_CORRECTION,
            confidence = 0.85f + (index * 0.03f)
        )
    }

    private fun generateEncouragementSuggestion(index: Int): PoseSuggestion {
        val suggestions = listOf(
            "Great job! Keep maintaining this position",
            "Excellent form - you're doing well",
            "Nice work on your posture improvement",
            "Keep it up! Your alignment looks good",
            "Perfect! Hold this position a bit longer"
        )

        return PoseSuggestion(
            title = "Encouragement",
            description = suggestions[index % suggestions.size],
            priority = PoseSuggestion.Priority.LOW,
            category = PoseSuggestion.Category.ENCOURAGEMENT,
            confidence = 0.90f + (index * 0.02f)
        )
    }

    private fun generateSafetySuggestion(index: Int): PoseSuggestion {
        val suggestions = listOf(
            "If you feel dizzy, please stop and rest",
            "Make sure you have enough space around you",
            "Keep a chair nearby for support if needed",
            "Don't hold your breath - breathe naturally",
            "Stop if you experience any pain"
        )

        return PoseSuggestion(
            title = "Safety Reminder",
            description = suggestions[index % suggestions.size],
            priority = PoseSuggestion.Priority.HIGH,
            category = PoseSuggestion.Category.SAFETY,
            confidence = 0.95f
        )
    }

    private fun generateProgressionSuggestion(index: Int): PoseSuggestion {
        val suggestions = listOf(
            "Try holding this position for 30 seconds longer",
            "Consider adding a slight variation to challenge yourself",
            "Once comfortable, try closing your eyes briefly",
            "Add gentle arm movements while maintaining posture",
            "Progress to the next difficulty level when ready"
        )

        return PoseSuggestion(
            title = "Progression",
            description = suggestions[index % suggestions.size],
            priority = PoseSuggestion.Priority.MEDIUM,
            category = PoseSuggestion.Category.PROGRESSION,
            confidence = 0.80f + (index * 0.04f)
        )
    }

    // Enums and data classes

    enum class PoseType {
        STANDING_NEUTRAL,
        SITTING,
        YOGA_TREE,
        ARMS_RAISED,
        SIDE_PROFILE,
        POOR_POSTURE
    }

    enum class SuggestionType {
        FORM_CORRECTION,
        ENCOURAGEMENT,
        SAFETY,
        PROGRESSION
    }

    enum class BitmapContent {
        SOLID_COLOR,
        GRADIENT,
        NOISE
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
}