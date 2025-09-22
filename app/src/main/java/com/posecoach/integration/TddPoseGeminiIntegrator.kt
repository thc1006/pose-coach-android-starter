package com.posecoach.integration

import android.content.Context
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.suggestions.GeminiPoseSuggestionClient
import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * TDD Green Phase: Minimal integration between pose detection and Gemini suggestions
 *
 * Following CLAUDE.md project goal:
 * "以 Gemini 2.5 Structured Output 回傳 3 條可執行姿勢建議"
 *
 * CONSTRAINTS:
 * - 呼叫 Gemini 時必須帶 `responseSchema`，返回 JSON
 * - Exactly 3 suggestions required
 * - Privacy-first architecture
 */
class TddPoseGeminiIntegrator(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private val geminiClient = GeminiPoseSuggestionClient(context)
    private var lastStablePoseTime = 0L
    private val stablePoseInterval = 2000L // 2 seconds between suggestions

    interface SuggestionsListener {
        fun onSuggestionsReceived(suggestions: List<String>)
        fun onSuggestionsError(error: String)
    }

    private var suggestionsListener: SuggestionsListener? = null

    /**
     * TDD Green Phase: Set listener for suggestions
     */
    fun setSuggestionsListener(listener: SuggestionsListener) {
        this.suggestionsListener = listener
    }

    /**
     * TDD Green Phase: Process pose landmarks and trigger Gemini if stable
     * Tests expect: Stable pose triggers Gemini call with structured output
     */
    fun processPoseLandmarks(poseLandmarkResult: PoseLandmarkResult) {
        // Check if pose is stable and enough time has passed
        if (isPoseStable(poseLandmarkResult) && shouldTriggerGemini()) {
            triggerGeminiSuggestions(poseLandmarkResult)
        }
    }

    /**
     * TDD Green Phase: Simple stability check
     * Tests expect: Stable pose detection logic
     */
    private fun isPoseStable(poseLandmarkResult: PoseLandmarkResult): Boolean {
        // TDD Green Phase: Minimal stability check
        // Real implementation would use StablePoseGate from core-pose
        return poseLandmarkResult.landmarks.isNotEmpty() &&
                poseLandmarkResult.landmarks.map { it.visibility }.average() > 0.7f
    }

    /**
     * TDD Green Phase: Check if we should trigger Gemini
     * Tests expect: Rate limiting to avoid too frequent calls
     */
    private fun shouldTriggerGemini(): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastStablePoseTime) > stablePoseInterval
    }

    /**
     * TDD Green Phase: Trigger Gemini API call with structured output
     * Tests expect: responseSchema parameter, exactly 3 suggestions
     */
    private fun triggerGeminiSuggestions(poseLandmarkResult: PoseLandmarkResult) {
        lastStablePoseTime = System.currentTimeMillis()

        coroutineScope.launch {
            try {
                Timber.d("TDD Green Phase: Triggering Gemini suggestions for pose")

                // Convert pose landmarks to format expected by Gemini
                val poseLandmarksData = convertToGeminiFormat(poseLandmarkResult)

                // Call Gemini with structured output (responseSchema enforced in client)
                val result = geminiClient.getPoseSuggestions(poseLandmarksData)

                result.onSuccess { response ->
                    // Validate exactly 3 suggestions as per CLAUDE.md requirements
                    if (response.suggestions.size == 3) {
                        val suggestionTexts = response.suggestions.map { it.instruction }
                        suggestionsListener?.onSuggestionsReceived(suggestionTexts)
                        Timber.d("TDD Green Phase: Received exactly 3 suggestions from Gemini")
                    } else {
                        val error = "Invalid suggestion count: ${response.suggestions.size}, expected 3"
                        suggestionsListener?.onSuggestionsError(error)
                        Timber.e("TDD Green Phase: $error")
                    }
                }.onFailure { e ->
                    val error = "Gemini API error: ${e.message}"
                    suggestionsListener?.onSuggestionsError(error)
                    Timber.e(e, "TDD Green Phase: Failed to get Gemini suggestions")
                }

            } catch (e: Exception) {
                val error = "Gemini API error: ${e.message}"
                suggestionsListener?.onSuggestionsError(error)
                Timber.e(e, "TDD Green Phase: Failed to get Gemini suggestions")
            }
        }
    }

    /**
     * TDD Green Phase: Convert pose landmarks to Gemini format
     * Tests expect: Proper data transformation for API
     */
    private fun convertToGeminiFormat(poseLandmarkResult: PoseLandmarkResult): PoseLandmarksData {
        // TDD Green Phase: Minimal conversion
        // Real implementation would include more comprehensive data mapping
        return PoseLandmarksData(
            landmarks = poseLandmarkResult.landmarks.mapIndexed { index, landmark ->
                PoseLandmarksData.LandmarkPoint(
                    index = index,
                    x = landmark.x,
                    y = landmark.y,
                    z = landmark.z,
                    visibility = landmark.visibility,
                    presence = landmark.presence
                )
            },
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * TDD Green Phase: Manual trigger for testing
     * Tests expect: Ability to manually trigger suggestions
     */
    fun triggerSuggestionsManually(mockPoseData: PoseLandmarkResult? = null) {
        val poseData = mockPoseData ?: createMockPoseData()
        triggerGeminiSuggestions(poseData)
    }

    /**
     * TDD Green Phase: Create mock pose data for testing
     */
    private fun createMockPoseData(): PoseLandmarkResult {
        // TDD Green Phase: Create minimal mock data
        // This would be replaced with real pose detection data
        return PoseLandmarkResult(
            landmarks = emptyList(), // Mock landmarks
            worldLandmarks = emptyList(),
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 0L
        )
    }

    /**
     * TDD Green Phase: Reset state for testing
     */
    fun resetForTesting() {
        lastStablePoseTime = 0L
    }
}