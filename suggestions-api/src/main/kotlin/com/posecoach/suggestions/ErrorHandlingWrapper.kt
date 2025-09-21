package com.posecoach.suggestions

import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Wrapper that adds comprehensive error handling and fallback strategies
 * to any PoseSuggestionClient implementation
 */
class ErrorHandlingWrapper(
    private val primaryClient: PoseSuggestionClient,
    private val fallbackClient: PoseSuggestionClient = FakePoseSuggestionClient(),
    private val timeoutMs: Long = 10_000L
) : PoseSuggestionClient {

    companion object {
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 1000L
    }

    override suspend fun getPoseSuggestions(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse> {
        return try {
            withTimeout(timeoutMs) {
                executeWithRetry(landmarks)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w("Primary client timeout, falling back to ${fallbackClient::class.simpleName}")
            handleFallback(landmarks, "Timeout after ${timeoutMs}ms")
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error in primary client")
            handleFallback(landmarks, "Unexpected error: ${e.message}")
        }
    }

    private suspend fun executeWithRetry(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse> {
        var lastException: Exception? = null

        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                val result = primaryClient.getPoseSuggestions(landmarks)

                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    val validatedResponse = validateResponse(response)

                    if (validatedResponse != null) {
                        if (attempt > 0) {
                            Timber.d("Primary client succeeded on retry attempt $attempt")
                        }
                        return Result.success(validatedResponse)
                    } else {
                        throw IllegalStateException("Invalid response structure from primary client")
                    }
                } else {
                    lastException = result.exceptionOrNull() as? Exception
                        ?: IllegalStateException("Unknown error from primary client")

                    if (!isRetryableError(lastException)) {
                        return@repeat // Don't retry non-retryable errors
                    }
                }

            } catch (e: Exception) {
                lastException = e
                Timber.w("Primary client attempt ${attempt + 1} failed: ${e.message}")

                if (!isRetryableError(e)) {
                    return@repeat // Don't retry non-retryable errors
                }
            }

            if (attempt < MAX_RETRIES) {
                kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }

        // All retries failed
        throw lastException ?: IllegalStateException("All retry attempts failed")
    }

    private suspend fun handleFallback(
        landmarks: PoseLandmarksData,
        reason: String
    ): Result<PoseSuggestionsResponse> {
        Timber.d("Using fallback client due to: $reason")

        return try {
            val fallbackResult = fallbackClient.getPoseSuggestions(landmarks)

            if (fallbackResult.isSuccess) {
                Timber.d("Fallback client provided suggestions successfully")
                fallbackResult
            } else {
                Timber.e("Fallback client also failed: ${fallbackResult.exceptionOrNull()?.message}")
                Result.failure(
                    IllegalStateException("Both primary and fallback clients failed")
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Fallback client threw exception")
            Result.failure(e)
        }
    }

    private fun isRetryableError(exception: Exception?): Boolean {
        return when (exception) {
            is SocketTimeoutException,
            is UnknownHostException,
            is TimeoutCancellationException -> true

            is IllegalStateException -> {
                val message = exception.message?.lowercase() ?: ""
                // Retry API quota/rate limit errors
                message.contains("quota") ||
                message.contains("rate limit") ||
                message.contains("too many requests") ||
                message.contains("service unavailable")
            }

            else -> false
        }
    }

    private fun validateResponse(response: PoseSuggestionsResponse): PoseSuggestionsResponse? {
        return try {
            // Validate structure and content
            if (response.suggestions.isEmpty()) {
                Timber.w("Response contains no suggestions")
                return null
            }

            if (response.suggestions.size > 5) {
                Timber.w("Response contains too many suggestions (${response.suggestions.size}), trimming to 3")
            }

            val validSuggestions = response.suggestions.take(3).mapNotNull { suggestion ->
                validateSuggestion(suggestion)
            }

            if (validSuggestions.isEmpty()) {
                Timber.w("No valid suggestions found in response")
                return null
            }

            // Ensure we have exactly 3 suggestions
            val finalSuggestions = when {
                validSuggestions.size >= 3 -> validSuggestions.take(3)
                else -> validSuggestions + generateDefaultSuggestions(3 - validSuggestions.size)
            }

            PoseSuggestionsResponse(finalSuggestions)

        } catch (e: Exception) {
            Timber.e(e, "Error validating response")
            null
        }
    }

    private fun validateSuggestion(suggestion: PoseSuggestion): PoseSuggestion? {
        return try {
            // Validate title
            val title = suggestion.title.trim()
            if (title.isEmpty() || title.length > 100) {
                Timber.w("Invalid suggestion title: '$title'")
                return null
            }

            // Validate instruction
            val instruction = suggestion.instruction.trim()
            if (instruction.isEmpty() || instruction.length < 20) {
                Timber.w("Invalid suggestion instruction: '$instruction'")
                return null
            }

            // Validate landmarks
            val landmarks = suggestion.targetLandmarks.filter { it.isNotBlank() }
            if (landmarks.isEmpty() || landmarks.size > 8) {
                Timber.w("Invalid target landmarks: $landmarks")
                return null
            }

            PoseSuggestion(
                title = title,
                instruction = instruction,
                targetLandmarks = landmarks
            )

        } catch (e: Exception) {
            Timber.e(e, "Error validating suggestion")
            null
        }
    }

    private fun generateDefaultSuggestions(count: Int): List<PoseSuggestion> {
        val defaults = listOf(
            PoseSuggestion(
                title = "Maintain Spine Alignment",
                instruction = "Keep your spine naturally curved with head balanced over shoulders",
                targetLandmarks = listOf("NOSE", "LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP")
            ),
            PoseSuggestion(
                title = "Relax Your Shoulders",
                instruction = "Allow your shoulders to drop away from your ears and soften any tension",
                targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW")
            ),
            PoseSuggestion(
                title = "Ground Through Your Feet",
                instruction = "Feel connection with the ground through balanced weight on both feet",
                targetLandmarks = listOf("LEFT_ANKLE", "RIGHT_ANKLE", "LEFT_KNEE", "RIGHT_KNEE")
            )
        )

        return defaults.take(count)
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            primaryClient.isAvailable() || fallbackClient.isAvailable()
        } catch (e: Exception) {
            Timber.e(e, "Error checking availability")
            fallbackClient.isAvailable()
        }
    }

    override fun requiresApiKey(): Boolean {
        return primaryClient.requiresApiKey()
    }
}