package com.posecoach.suggestions

import android.content.Context
import com.posecoach.corepose.EnhancedStablePoseGate
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

class SuggestionsOrchestrator(
    private val context: Context,
    private val clientFactory: PoseSuggestionClientFactory,
    private val privacyEnabled: Boolean = true
) {
    private val stablePoseGate = EnhancedStablePoseGate(
        windowSec = 1.5,
        posThreshold = 0.02f,
        angleThresholdDeg = 5.0f
    )

    private val deduplicationManager = PoseDeduplicationManager(
        deduplicationWindowMs = 5000L
    )

    private var currentClient: PoseSuggestionClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val performanceTracker = PerformanceTracker()

    private val _suggestionsFlow = MutableSharedFlow<Result<PoseSuggestionsResponse>>()
    val suggestionsFlow: SharedFlow<Result<PoseSuggestionsResponse>> = _suggestionsFlow

    private val localFallbackSuggestions = PoseSuggestionsResponse(
        suggestions = listOf(
            PoseSuggestion(
                title = "Check Your Posture",
                instruction = "Stand tall with shoulders back and down, keeping your head level",
                targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "NOSE")
            ),
            PoseSuggestion(
                title = "Breathe Deeply",
                instruction = "Take slow, deep breaths to help relax and center yourself",
                targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER")
            ),
            PoseSuggestion(
                title = "Stay Balanced",
                instruction = "Keep your weight evenly distributed on both feet",
                targetLandmarks = listOf("LEFT_ANKLE", "RIGHT_ANKLE", "LEFT_HIP", "RIGHT_HIP")
            )
        )
    )

    init {
        updateClient()
    }

    suspend fun updateClient() {
        currentClient = if (!privacyEnabled) {
            clientFactory.createFakeClient()
        } else {
            clientFactory.createClient(
                preferReal = true,
                respectPrivacySettings = true
            )
        }

        Timber.d("Using client: ${currentClient!!::class.simpleName}")
    }

    private suspend fun getCurrentClient(): PoseSuggestionClient {
        // Get or create client
        if (currentClient == null) {
            updateClient()
        }
        return currentClient!!
    }

    suspend fun processPoseLandmarks(landmarks: PoseLandmarkResult) {
        val stabilityResult = stablePoseGate.update(landmarks)

        if (stabilityResult.justTriggered) {
            Timber.d("Stable pose detected, processing suggestions")

            val poseLandmarksData = PoseLandmarksData(
                landmarks = landmarks.landmarks.mapIndexed { index, landmark ->
                    PoseLandmarksData.LandmarkPoint(
                        index = index,
                        x = landmark.x,
                        y = landmark.y,
                        z = landmark.z,
                        visibility = landmark.visibility,
                        presence = landmark.presence
                    )
                },
                timestamp = landmarks.timestampMs
            )

            if (deduplicationManager.shouldProcessPose(poseLandmarksData)) {
                requestSuggestions(poseLandmarksData)
            }
        }
    }

    private fun requestSuggestions(landmarks: PoseLandmarksData) {
        coroutineScope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // Emit empty result to indicate processing
                _suggestionsFlow.emit(Result.success(PoseSuggestionsResponse(emptyList())))

                // Get client with error handling wrapper
                val client = getCurrentClient()
                val wrappedClient = ErrorHandlingWrapper(
                    primaryClient = client,
                    fallbackClient = clientFactory.createFakeClient(),
                    timeoutMs = 8000L
                )

                val result = wrappedClient.getPoseSuggestions(landmarks)
                val duration = System.currentTimeMillis() - startTime

                performanceTracker.recordApiCall(duration, result.isSuccess)

                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    val validatedResponse = validateResponseQuality(response, landmarks)
                    _suggestionsFlow.emit(Result.success(validatedResponse))
                    Timber.d("Suggestions received: ${validatedResponse.suggestions.size} in ${duration}ms")
                } else {
                    Timber.w("All clients failed, using local fallback")
                    _suggestionsFlow.emit(Result.success(localFallbackSuggestions))
                }

            } catch (e: Exception) {
                Timber.e(e, "Critical error getting suggestions, using fallback")
                _suggestionsFlow.emit(Result.success(localFallbackSuggestions))
            }
        }
    }

    fun retryLastRequest() {
        coroutineScope.launch {
            try {
                val testLandmarks = createTestLandmarks()
                val client = getCurrentClient()
                val result = client.getPoseSuggestions(testLandmarks)
                _suggestionsFlow.emit(result)
            } catch (e: Exception) {
                Timber.e(e, "Retry failed")
                _suggestionsFlow.emit(Result.failure(e))
            }
        }
    }

    fun reset() {
        stablePoseGate.reset()
        deduplicationManager.clear()
        currentClient = null // Force client recreation
    }

    fun onDestroy() {
        coroutineScope.cancel()
    }

    private fun createTestLandmarks(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                PoseLandmarksData.LandmarkPoint(
                    index = index,
                    x = 0.5f,
                    y = 0.5f,
                    z = 0f,
                    visibility = 0.9f,
                    presence = 0.9f
                )
            }
        )
    }

    private fun validateResponseQuality(
        response: PoseSuggestionsResponse,
        landmarks: PoseLandmarksData
    ): PoseSuggestionsResponse {
        // Check if suggestions are contextually appropriate
        val validatedSuggestions = response.suggestions.filter { suggestion ->
            // Basic quality checks
            suggestion.title.isNotBlank() &&
            suggestion.instruction.length >= 20 &&
            suggestion.targetLandmarks.isNotEmpty() &&
            suggestion.targetLandmarks.size <= 6
        }

        // If we don't have enough quality suggestions, supplement with defaults
        val finalSuggestions = if (validatedSuggestions.size < 3) {
            Timber.w("Only ${validatedSuggestions.size} quality suggestions, adding defaults")
            validatedSuggestions + generateContextualDefaults(landmarks, 3 - validatedSuggestions.size)
        } else {
            validatedSuggestions.take(3)
        }

        return PoseSuggestionsResponse(finalSuggestions)
    }

    private fun generateContextualDefaults(
        landmarks: PoseLandmarksData,
        count: Int
    ): List<PoseSuggestion> {
        val defaults = mutableListOf<PoseSuggestion>()

        if (defaults.size < count) {
            defaults.add(
                PoseSuggestion(
                    title = "Maintain Natural Alignment",
                    instruction = "Keep your body in natural alignment with relaxed, balanced posture",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP")
                )
            )
        }

        if (defaults.size < count) {
            defaults.add(
                PoseSuggestion(
                    title = "Breathe Mindfully",
                    instruction = "Focus on slow, deep breathing to enhance your pose stability",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER")
                )
            )
        }

        if (defaults.size < count) {
            defaults.add(
                PoseSuggestion(
                    title = "Ground Your Foundation",
                    instruction = "Feel stability through your base, whether standing or sitting",
                    targetLandmarks = listOf("LEFT_ANKLE", "RIGHT_ANKLE", "LEFT_KNEE", "RIGHT_KNEE")
                )
            )
        }

        return defaults.take(count)
    }

    /**
     * Get performance metrics for monitoring
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        return performanceTracker.getMetrics()
    }

    /**
     * Check if real API client is available and configured
     */
    suspend fun isRealClientAvailable(): Boolean {
        return try {
            val client = clientFactory.createClient(preferReal = true, respectPrivacySettings = false)
            client.requiresApiKey() && client.isAvailable()
        } catch (e: Exception) {
            false
        }
    }

    data class PerformanceMetrics(
        val totalCalls: Int,
        val successfulCalls: Int,
        val averageResponseTime: Long,
        val failureRate: Double
    )

    private class PerformanceTracker {
        private val calls = mutableListOf<ApiCall>()
        private val maxCalls = 100 // Keep last 100 calls

        fun recordApiCall(durationMs: Long, success: Boolean) {
            calls.add(ApiCall(System.currentTimeMillis(), durationMs, success))
            if (calls.size > maxCalls) {
                calls.removeAt(0)
            }
        }

        fun getMetrics(): PerformanceMetrics {
            if (calls.isEmpty()) {
                return PerformanceMetrics(0, 0, 0, 0.0)
            }

            val successful = calls.count { it.success }
            val avgTime = calls.map { it.durationMs }.average().toLong()
            val failureRate = (calls.size - successful).toDouble() / calls.size

            return PerformanceMetrics(
                totalCalls = calls.size,
                successfulCalls = successful,
                averageResponseTime = avgTime,
                failureRate = failureRate
            )
        }

        private data class ApiCall(
            val timestamp: Long,
            val durationMs: Long,
            val success: Boolean
        )
    }
}