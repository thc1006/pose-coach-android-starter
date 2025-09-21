package com.posecoach.suggestions

import com.posecoach.suggestions.models.PoseLandmarksData
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakePoseSuggestionClientTest {

    private lateinit var fakeClient: FakePoseSuggestionClient

    private val normalPoseLandmarks = PoseLandmarksData(
        landmarks = listOf(
            PoseLandmarksData.LandmarkPoint(0, 0.5f, 0.3f, 0.0f, 0.9f, 0.9f), // nose
            PoseLandmarksData.LandmarkPoint(11, 0.4f, 0.5f, 0.0f, 0.9f, 0.9f), // left shoulder
            PoseLandmarksData.LandmarkPoint(12, 0.6f, 0.5f, 0.0f, 0.9f, 0.9f), // right shoulder
            PoseLandmarksData.LandmarkPoint(15, 0.35f, 0.7f, 0.0f, 0.9f, 0.9f), // left wrist
            PoseLandmarksData.LandmarkPoint(16, 0.65f, 0.7f, 0.0f, 0.9f, 0.9f), // right wrist
            PoseLandmarksData.LandmarkPoint(23, 0.45f, 0.8f, 0.0f, 0.9f, 0.9f), // left hip
            PoseLandmarksData.LandmarkPoint(24, 0.55f, 0.8f, 0.0f, 0.9f, 0.9f)  // right hip
        )
    )

    private val forwardHeadLandmarks = PoseLandmarksData(
        landmarks = listOf(
            PoseLandmarksData.LandmarkPoint(0, 0.7f, 0.3f, 0.0f, 0.9f, 0.9f), // nose forward
            PoseLandmarksData.LandmarkPoint(11, 0.4f, 0.5f, 0.0f, 0.9f, 0.9f), // left shoulder
            PoseLandmarksData.LandmarkPoint(12, 0.6f, 0.5f, 0.0f, 0.9f, 0.9f)  // right shoulder
        )
    )

    private val unevenShouldersLandmarks = PoseLandmarksData(
        landmarks = listOf(
            PoseLandmarksData.LandmarkPoint(0, 0.5f, 0.3f, 0.0f, 0.9f, 0.9f), // nose
            PoseLandmarksData.LandmarkPoint(11, 0.4f, 0.4f, 0.0f, 0.9f, 0.9f), // left shoulder higher
            PoseLandmarksData.LandmarkPoint(12, 0.6f, 0.6f, 0.0f, 0.9f, 0.9f)  // right shoulder lower
        )
    )

    @Before
    fun setup() {
        fakeClient = FakePoseSuggestionClient()
    }

    @Test
    fun `requiresApiKey returns false`() {
        assertFalse(fakeClient.requiresApiKey())
    }

    @Test
    fun `isAvailable always returns true`() = runTest {
        assertTrue(fakeClient.isAvailable())
    }

    @Test
    fun `getPoseSuggestions returns exactly 3 suggestions`() = runTest {
        val result = fakeClient.getPoseSuggestions(normalPoseLandmarks)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertEquals(3, response?.suggestions?.size)
    }

    @Test
    fun `getPoseSuggestions with normal pose returns general suggestions`() = runTest {
        val result = fakeClient.getPoseSuggestions(normalPoseLandmarks)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertEquals(3, response?.suggestions?.size)

        // Verify all suggestions have required fields
        response?.suggestions?.forEach { suggestion ->
            assertTrue(suggestion.title.isNotBlank())
            assertTrue(suggestion.instruction.isNotBlank())
            assertTrue(suggestion.targetLandmarks.isNotEmpty())
        }
    }

    @Test
    fun `getPoseSuggestions with forward head detects issue`() = runTest {
        val result = fakeClient.getPoseSuggestions(forwardHeadLandmarks)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertEquals(3, response?.suggestions?.size)

        // Should contain head-related suggestions
        val hasHeadSuggestion = response?.suggestions?.any { suggestion ->
            suggestion.title.contains("Head", ignoreCase = true) ||
            suggestion.instruction.contains("head", ignoreCase = true) ||
            suggestion.targetLandmarks.contains("NOSE")
        } ?: false

        assertTrue(hasHeadSuggestion, "Should contain head-related suggestion for forward head posture")
    }

    @Test
    fun `getPoseSuggestions with uneven shoulders detects issue`() = runTest {
        val result = fakeClient.getPoseSuggestions(unevenShouldersLandmarks)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertEquals(3, response?.suggestions?.size)

        // Should contain shoulder-related suggestions
        val hasShoulderSuggestion = response?.suggestions?.any { suggestion ->
            suggestion.title.contains("Shoulder", ignoreCase = true) ||
            suggestion.instruction.contains("shoulder", ignoreCase = true) ||
            suggestion.targetLandmarks.any { it.contains("SHOULDER") }
        } ?: false

        assertTrue(hasShoulderSuggestion, "Should contain shoulder-related suggestion for uneven shoulders")
    }

    @Test
    fun `getPoseSuggestions with empty landmarks returns default suggestions`() = runTest {
        val emptyLandmarks = PoseLandmarksData(landmarks = emptyList())

        val result = fakeClient.getPoseSuggestions(emptyLandmarks)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertEquals(3, response?.suggestions?.size)
    }

    @Test
    fun `getPoseSuggestions suggestions have valid content`() = runTest {
        val result = fakeClient.getPoseSuggestions(normalPoseLandmarks)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()

        response?.suggestions?.forEach { suggestion ->
            // Title should be reasonable length
            assertTrue(suggestion.title.length in 5..50, "Title length should be 5-50 characters")

            // Instruction should be reasonable length
            assertTrue(suggestion.instruction.length in 30..200, "Instruction length should be 30-200 characters")

            // Should have 2-6 target landmarks
            assertTrue(suggestion.targetLandmarks.size in 2..6, "Should have 2-6 target landmarks")

            // All target landmarks should be valid MediaPipe landmarks
            suggestion.targetLandmarks.forEach { landmark ->
                assertTrue(
                    landmark in setOf(
                        "NOSE", "LEFT_EYE", "RIGHT_EYE", "LEFT_EAR", "RIGHT_EAR",
                        "LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW",
                        "LEFT_WRIST", "RIGHT_WRIST", "LEFT_HIP", "RIGHT_HIP",
                        "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE", "RIGHT_ANKLE",
                        "LEFT_INDEX", "RIGHT_INDEX", "LEFT_THUMB", "RIGHT_THUMB"
                    ),
                    "Invalid landmark: $landmark"
                )
            }
        }
    }

    @Test
    fun `getPoseSuggestions has processing delay`() = runTest {
        val startTime = System.currentTimeMillis()

        val result = fakeClient.getPoseSuggestions(normalPoseLandmarks)

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        assertTrue(result.isSuccess)
        assertTrue(duration >= 500, "Should have at least 500ms processing delay")
        assertTrue(duration <= 2000, "Should not exceed 2000ms processing delay")
    }
}