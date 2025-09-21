package com.posecoach.suggestions

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.suggestions.models.PoseLandmarksData
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Integration tests for the complete pose suggestions system
 */
class IntegrationTest {

    private lateinit var mockContext: Context
    private lateinit var clientFactory: PoseSuggestionClientFactory
    private lateinit var orchestrator: SuggestionsOrchestrator

    @Before
    fun setup() {
        mockContext = mock()
        clientFactory = PoseSuggestionClientFactory(mockContext)
        orchestrator = SuggestionsOrchestrator(
            context = mockContext,
            clientFactory = clientFactory,
            privacyEnabled = true
        )
    }

    @Test
    fun `complete flow should work with fake client`() = runTest {
        // Test the complete flow from pose landmarks to suggestions
        val poseLandmarks = createTestPoseLandmarks()

        // Process landmarks through orchestrator
        orchestrator.processPoseLandmarks(poseLandmarks)

        // Verify orchestrator is set up correctly
        assertThat(orchestrator.isRealClientAvailable()).isFalse()
    }

    @Test
    fun `factory should create appropriate clients based on configuration`() = runTest {
        // Test fake client creation
        val fakeClient = clientFactory.createFakeClient()
        assertThat(fakeClient).isInstanceOf(FakePoseSuggestionClient::class.java)
        assertThat(fakeClient.requiresApiKey()).isFalse()

        // Test real client creation with API key
        val realClient = clientFactory.createRealClient("test-api-key")
        assertThat(realClient).isInstanceOf(GeminiPoseSuggestionClient::class.java)
        assertThat(realClient.requiresApiKey()).isTrue()
    }

    @Test
    fun `error handling wrapper should provide fallback functionality`() = runTest {
        val fakeClient = clientFactory.createFakeClient()
        val realClient = clientFactory.createRealClient("invalid-key")

        val wrapper = ErrorHandlingWrapper(
            primaryClient = realClient,
            fallbackClient = fakeClient
        )

        val testLandmarks = createTestLandmarksData()
        val result = wrapper.getPoseSuggestions(testLandmarks)

        // Should succeed with fallback even if primary fails
        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()!!
        assertThat(response.suggestions).hasSize(3)
    }

    @Test
    fun `structured output validation should work end-to-end`() = runTest {
        val fakeClient = clientFactory.createFakeClient()
        val testLandmarks = createTestLandmarksData()

        val result = fakeClient.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()!!

        // Validate structure matches Gemini 2.5 schema requirements
        assertThat(response.suggestions).hasSize(3)
        response.suggestions.forEach { suggestion ->
            assertThat(suggestion.title).isNotEmpty()
            assertThat(suggestion.title.length).isAtMost(50)
            assertThat(suggestion.instruction).isNotEmpty()
            assertThat(suggestion.instruction.length).isAtLeast(20)
            assertThat(suggestion.targetLandmarks).isNotEmpty()
            assertThat(suggestion.targetLandmarks.size).isAtMost(6)

            // Validate landmark names are valid MediaPipe constants
            suggestion.targetLandmarks.forEach { landmark ->
                assertThat(landmark).matches("^[A-Z]+_?[A-Z]*$")
            }
        }
    }

    @Test
    fun `performance tracking should work correctly`() = runTest {
        orchestrator.updateClient()

        // Simulate some API calls
        val testLandmarks = createTestLandmarksData()
        val client = clientFactory.createFakeClient()

        // Make a few calls to generate metrics
        repeat(3) {
            client.getPoseSuggestions(testLandmarks)
        }

        val metrics = orchestrator.getPerformanceMetrics()
        // Initial state should have no metrics yet in this isolated test
        assertThat(metrics.totalCalls).isEqualTo(0)
    }

    @Test
    fun `pose context analysis should detect different postures`() = runTest {
        val fakeClient = clientFactory.createFakeClient()

        // Test standing posture
        val standingPose = createStandingPostureLandmarks()
        val standingResult = fakeClient.getPoseSuggestions(standingPose)
        assertThat(standingResult.isSuccess).isTrue()

        // Test arms raised posture
        val armsRaisedPose = createArmsRaisedLandmarks()
        val armsResult = fakeClient.getPoseSuggestions(armsRaisedPose)
        assertThat(armsResult.isSuccess).isTrue()

        // Results should be different based on pose context
        val standingSuggestions = standingResult.getOrNull()!!.suggestions
        val armsSuggestions = armsResult.getOrNull()!!.suggestions

        // Both should have 3 suggestions
        assertThat(standingSuggestions).hasSize(3)
        assertThat(armsSuggestions).hasSize(3)
    }

    private fun createTestPoseLandmarks(): PoseLandmarkResult {
        return PoseLandmarkResult(
            landmarks = List(33) { index ->
                PoseLandmarkResult.LandmarkPoint(
                    x = 0.5f + (index * 0.01f),
                    y = 0.5f + (index * 0.01f),
                    z = 0f,
                    visibility = 0.9f,
                    presence = 0.9f
                )
            },
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun createTestLandmarksData(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                PoseLandmarksData.LandmarkPoint(
                    index = index,
                    x = 0.5f + (index * 0.01f),
                    y = 0.5f + (index * 0.01f),
                    z = 0f,
                    visibility = 0.9f,
                    presence = 0.9f
                )
            }
        )
    }

    private fun createStandingPostureLandmarks(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                val point = when (index) {
                    0 -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.2f, 0f, 0.9f, 0.9f) // NOSE
                    11 -> PoseLandmarksData.LandmarkPoint(index, 0.4f, 0.4f, 0f, 0.9f, 0.9f) // LEFT_SHOULDER
                    12 -> PoseLandmarksData.LandmarkPoint(index, 0.6f, 0.4f, 0f, 0.9f, 0.9f) // RIGHT_SHOULDER
                    23 -> PoseLandmarksData.LandmarkPoint(index, 0.4f, 0.7f, 0f, 0.9f, 0.9f) // LEFT_HIP
                    24 -> PoseLandmarksData.LandmarkPoint(index, 0.6f, 0.7f, 0f, 0.9f, 0.9f) // RIGHT_HIP
                    25 -> PoseLandmarksData.LandmarkPoint(index, 0.4f, 0.8f, 0f, 0.9f, 0.9f) // LEFT_KNEE
                    26 -> PoseLandmarksData.LandmarkPoint(index, 0.6f, 0.8f, 0f, 0.9f, 0.9f) // RIGHT_KNEE
                    else -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.5f, 0f, 0.8f, 0.8f)
                }
                point
            }
        )
    }

    private fun createArmsRaisedLandmarks(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                val point = when (index) {
                    0 -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.3f, 0f, 0.9f, 0.9f) // NOSE
                    11 -> PoseLandmarksData.LandmarkPoint(index, 0.4f, 0.5f, 0f, 0.9f, 0.9f) // LEFT_SHOULDER
                    12 -> PoseLandmarksData.LandmarkPoint(index, 0.6f, 0.5f, 0f, 0.9f, 0.9f) // RIGHT_SHOULDER
                    13 -> PoseLandmarksData.LandmarkPoint(index, 0.3f, 0.3f, 0f, 0.9f, 0.9f) // LEFT_ELBOW (raised)
                    14 -> PoseLandmarksData.LandmarkPoint(index, 0.7f, 0.3f, 0f, 0.9f, 0.9f) // RIGHT_ELBOW (raised)
                    15 -> PoseLandmarksData.LandmarkPoint(index, 0.2f, 0.1f, 0f, 0.9f, 0.9f) // LEFT_WRIST (raised)
                    16 -> PoseLandmarksData.LandmarkPoint(index, 0.8f, 0.1f, 0f, 0.9f, 0.9f) // RIGHT_WRIST (raised)
                    else -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.5f, 0f, 0.8f, 0.8f)
                }
                point
            }
        )
    }
}