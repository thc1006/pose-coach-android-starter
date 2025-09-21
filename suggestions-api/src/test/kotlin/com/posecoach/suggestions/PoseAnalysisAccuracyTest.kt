package com.posecoach.suggestions

import com.google.common.truth.Truth.assertThat
import com.posecoach.suggestions.models.PoseLandmarksData
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests for validating pose analysis accuracy and contextual suggestions
 */
class PoseAnalysisAccuracyTest {

    private lateinit var fakeClient: FakePoseSuggestionClient

    @Before
    fun setup() {
        fakeClient = FakePoseSuggestionClient()
    }

    @Test
    fun `should detect forward head posture`() = runTest {
        val forwardHeadPose = createForwardHeadPostureLandmarks()
        val result = fakeClient.getPoseSuggestions(forwardHeadPose)

        assertThat(result.isSuccess).isTrue()
        val suggestions = result.getOrNull()!!.suggestions

        // Should detect and provide forward head posture correction
        val titles = suggestions.map { it.title }
        assertThat(titles.any { it.contains("Head", ignoreCase = true) ||
                                it.contains("posture", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `should detect uneven shoulders`() = runTest {
        val unevenShouldersPose = createUnevenShouldersLandmarks()
        val result = fakeClient.getPoseSuggestions(unevenShouldersPose)

        assertThat(result.isSuccess).isTrue()
        val suggestions = result.getOrNull()!!.suggestions

        // Should detect shoulder imbalance
        val content = suggestions.map { "${it.title} ${it.instruction}" }.joinToString(" ")
        assertThat(content.contains("shoulder", ignoreCase = true)).isTrue()
    }

    @Test
    fun `should provide context-appropriate suggestions for arms raised`() = runTest {
        val armsRaisedPose = createArmsRaisedLandmarks()
        val result = fakeClient.getPoseSuggestions(armsRaisedPose)

        assertThat(result.isSuccess).isTrue()
        val suggestions = result.getOrNull()!!.suggestions

        // Should provide arm-specific guidance
        val content = suggestions.map { "${it.title} ${it.instruction}" }.joinToString(" ")
        assertThat(content.contains("arm", ignoreCase = true) ||
                  content.contains("wrist", ignoreCase = true) ||
                  content.contains("elbow", ignoreCase = true)).isTrue()
    }

    @Test
    fun `should provide standing posture suggestions`() = runTest {
        val standingPose = createStandingPostureLandmarks()
        val result = fakeClient.getPoseSuggestions(standingPose)

        assertThat(result.isSuccess).isTrue()
        val suggestions = result.getOrNull()!!.suggestions

        // Should provide standing-specific guidance
        val targetLandmarks = suggestions.flatMap { it.targetLandmarks }
        assertThat(targetLandmarks.any { it.contains("HIP") || it.contains("KNEE") || it.contains("ANKLE") }).isTrue()
    }

    @Test
    fun `suggestions should target appropriate landmarks`() = runTest {
        val testPose = createTestLandmarks()
        val result = fakeClient.getPoseSuggestions(testPose)

        val suggestions = result.getOrNull()!!.suggestions

        suggestions.forEach { suggestion ->
            // Each suggestion should target at least 2 landmarks
            assertThat(suggestion.targetLandmarks.size).isAtLeast(2)

            // Landmarks should be valid MediaPipe constants
            suggestion.targetLandmarks.forEach { landmark ->
                assertThat(landmark).matches("^[A-Z]+_?[A-Z]*$")
            }
        }
    }

    @Test
    fun `should provide actionable instructions`() = runTest {
        val testPose = createTestLandmarks()
        val result = fakeClient.getPoseSuggestions(testPose)

        val suggestions = result.getOrNull()!!.suggestions

        suggestions.forEach { suggestion ->
            // Instructions should be substantial
            assertThat(suggestion.instruction.length).isAtLeast(30)

            // Should contain actionable language
            val instruction = suggestion.instruction.lowercase()
            val hasActionVerbs = listOf("keep", "maintain", "engage", "relax", "straighten",
                                      "lower", "lift", "pull", "push", "draw", "imagine").any {
                verb -> instruction.contains(verb)
            }
            assertThat(hasActionVerbs).isTrue()
        }
    }

    @Test
    fun `pose hashing should differentiate postures`() {
        val standing = createStandingPostureLandmarks()
        val armsRaised = createArmsRaisedLandmarks()
        val forwardHead = createForwardHeadPostureLandmarks()

        val hashes = setOf(standing.hash(), armsRaised.hash(), forwardHead.hash())

        // All poses should have different hashes
        assertThat(hashes).hasSize(3)
    }

    private fun createTestLandmarks(): PoseLandmarksData {
        return createLandmarksWithPositions()
    }

    private fun createStandingPostureLandmarks(): PoseLandmarksData {
        return createLandmarksWithPositions(
            noseY = 0.2f,
            shoulderY = 0.4f,
            hipY = 0.6f,
            kneeY = 0.8f,
            ankleY = 0.9f
        )
    }

    private fun createArmsRaisedLandmarks(): PoseLandmarksData {
        return createLandmarksWithPositions(
            noseY = 0.3f,
            shoulderY = 0.5f,
            elbowY = 0.3f, // Elbows above shoulders
            wristY = 0.1f, // Wrists above elbows
            hipY = 0.7f,
            kneeY = 0.8f
        )
    }

    private fun createForwardHeadPostureLandmarks(): PoseLandmarksData {
        return createLandmarksWithPositions(
            noseX = 0.6f, // Head forward of shoulders
            shoulderX = 0.5f,
            noseY = 0.2f,
            shoulderY = 0.4f
        )
    }

    private fun createUnevenShouldersLandmarks(): PoseLandmarksData {
        return createLandmarksWithPositions(
            leftShoulderY = 0.35f, // Left shoulder higher
            rightShoulderY = 0.45f // Right shoulder lower
        )
    }

    private fun createLandmarksWithPositions(
        noseX: Float = 0.5f,
        noseY: Float = 0.3f,
        shoulderX: Float = 0.5f,
        shoulderY: Float = 0.4f,
        leftShoulderY: Float = shoulderY,
        rightShoulderY: Float = shoulderY,
        elbowY: Float = 0.5f,
        wristY: Float = 0.6f,
        hipY: Float = 0.7f,
        kneeY: Float = 0.8f,
        ankleY: Float = 0.9f
    ): PoseLandmarksData {
        val landmarks = mutableListOf<PoseLandmarksData.LandmarkPoint>()

        // Create all 33 MediaPipe landmarks
        for (i in 0..32) {
            val point = when (i) {
                0 -> PoseLandmarksData.LandmarkPoint(i, noseX, noseY, 0f, 0.9f, 0.9f) // NOSE
                11 -> PoseLandmarksData.LandmarkPoint(i, shoulderX - 0.1f, leftShoulderY, 0f, 0.9f, 0.9f) // LEFT_SHOULDER
                12 -> PoseLandmarksData.LandmarkPoint(i, shoulderX + 0.1f, rightShoulderY, 0f, 0.9f, 0.9f) // RIGHT_SHOULDER
                13 -> PoseLandmarksData.LandmarkPoint(i, shoulderX - 0.1f, elbowY, 0f, 0.9f, 0.9f) // LEFT_ELBOW
                14 -> PoseLandmarksData.LandmarkPoint(i, shoulderX + 0.1f, elbowY, 0f, 0.9f, 0.9f) // RIGHT_ELBOW
                15 -> PoseLandmarksData.LandmarkPoint(i, shoulderX - 0.1f, wristY, 0f, 0.9f, 0.9f) // LEFT_WRIST
                16 -> PoseLandmarksData.LandmarkPoint(i, shoulderX + 0.1f, wristY, 0f, 0.9f, 0.9f) // RIGHT_WRIST
                23 -> PoseLandmarksData.LandmarkPoint(i, shoulderX - 0.1f, hipY, 0f, 0.9f, 0.9f) // LEFT_HIP
                24 -> PoseLandmarksData.LandmarkPoint(i, shoulderX + 0.1f, hipY, 0f, 0.9f, 0.9f) // RIGHT_HIP
                25 -> PoseLandmarksData.LandmarkPoint(i, shoulderX - 0.1f, kneeY, 0f, 0.9f, 0.9f) // LEFT_KNEE
                26 -> PoseLandmarksData.LandmarkPoint(i, shoulderX + 0.1f, kneeY, 0f, 0.9f, 0.9f) // RIGHT_KNEE
                27 -> PoseLandmarksData.LandmarkPoint(i, shoulderX - 0.1f, ankleY, 0f, 0.9f, 0.9f) // LEFT_ANKLE
                28 -> PoseLandmarksData.LandmarkPoint(i, shoulderX + 0.1f, ankleY, 0f, 0.9f, 0.9f) // RIGHT_ANKLE
                else -> PoseLandmarksData.LandmarkPoint(
                    i,
                    0.5f + (i * 0.01f),
                    0.5f + (i * 0.01f),
                    0f,
                    0.8f,
                    0.8f
                )
            }
            landmarks.add(point)
        }

        return PoseLandmarksData(landmarks)
    }
}