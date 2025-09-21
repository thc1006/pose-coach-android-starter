package com.posecoach.suggestions

import com.google.common.truth.Truth.assertThat
import com.posecoach.suggestions.models.PoseLandmarksData
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PoseSuggestionClientTest {

    private lateinit var fakeClient: PoseSuggestionClient
    private lateinit var testLandmarks: PoseLandmarksData

    @Before
    fun setup() {
        fakeClient = FakePoseSuggestionClient()
        testLandmarks = createTestLandmarks()
    }

    @Test
    fun `getPoseSuggestions should return exactly 3 suggestions`() = runTest {
        val result = fakeClient.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()!!
        assertThat(response.suggestions).hasSize(3)
    }

    @Test
    fun `each suggestion should have title and instruction`() = runTest {
        val result = fakeClient.getPoseSuggestions(testLandmarks)

        val suggestions = result.getOrNull()!!.suggestions
        suggestions.forEach { suggestion ->
            assertThat(suggestion.title).isNotEmpty()
            assertThat(suggestion.instruction).isNotEmpty()
            assertThat(suggestion.targetLandmarks).isNotEmpty()
        }
    }

    @Test
    fun `fake client should not require API key`() {
        assertThat(fakeClient.requiresApiKey()).isFalse()
    }

    @Test
    fun `fake client should always be available`() = runTest {
        assertThat(fakeClient.isAvailable()).isTrue()
    }

    @Test
    fun `landmarks hash should be consistent`() {
        val landmarks1 = createTestLandmarks()
        val landmarks2 = createTestLandmarks()

        assertThat(landmarks1.hash()).isEqualTo(landmarks2.hash())
    }

    @Test
    fun `different poses should have different hashes`() {
        val standing = createTestLandmarks(baseX = 0.5f)
        val sitting = createTestLandmarks(baseX = 0.3f)

        assertThat(standing.hash()).isNotEqualTo(sitting.hash())
    }

    @Test
    fun `toJsonString should format landmarks correctly`() {
        val landmarks = PoseLandmarksData(
            landmarks = listOf(
                PoseLandmarksData.LandmarkPoint(0, 0.5f, 0.5f, 0f, 0.9f, 0.9f)
            )
        )

        val json = landmarks.toJsonString()
        assertThat(json).contains("\"i\":0")
        assertThat(json).contains("\"x\":0.5")
        assertThat(json).contains("\"y\":0.5")
    }

    private fun createTestLandmarks(baseX: Float = 0.5f): PoseLandmarksData {
        val landmarks = List(33) { index ->
            PoseLandmarksData.LandmarkPoint(
                index = index,
                x = baseX + (index * 0.01f),
                y = 0.5f + (index * 0.01f),
                z = 0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }
        return PoseLandmarksData(landmarks)
    }
}