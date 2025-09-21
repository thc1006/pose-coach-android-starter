package com.posecoach.app.examples

import android.content.Context
import com.posecoach.suggestions.*
import com.posecoach.suggestions.models.PoseLandmarksData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Example integration showing how to use the Gemini pose suggestion client
 * in an Android application with proper error handling and privacy compliance
 */
class GeminiIntegrationExample(private val context: Context) {

    private val suggestionModule = SuggestionModule(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Initialize the suggestion system
     * Call this in Application.onCreate() or similar
     */
    suspend fun initialize() {
        try {
            suggestionModule.initialize()
            Timber.i("Pose suggestion system initialized")

            // Check status
            val status = suggestionModule.getModuleStatus()
            Timber.d("Module status: ${status.isFullyOperational}")

            if (!status.apiKeyConfigured) {
                Timber.w("API key not configured - will use offline fallback")
            }

            if (!status.privacyConsentGiven) {
                Timber.w("Privacy consent not given - requesting user consent")
                requestPrivacyConsent()
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize suggestion system")
        }
    }

    /**
     * Example of processing pose landmarks and getting suggestions
     */
    fun processPoseLandmarks(landmarks: List<Any>) {
        scope.launch {
            try {
                // Convert your pose landmarks to our format
                val landmarksData = convertToLandmarksData(landmarks)

                // Get the best available client (Gemini or offline fallback)
                val client = suggestionModule.provideBestClient()

                Timber.d("Using client: ${client::class.simpleName}")

                // Request suggestions
                val result = client.getPoseSuggestions(landmarksData)

                if (result.isSuccess) {
                    val suggestions = result.getOrNull()
                    suggestions?.let { response ->
                        displaySuggestions(response.suggestions)
                        Timber.i("Generated ${response.suggestions.size} pose suggestions")
                    }
                } else {
                    handleSuggestionError(result.exceptionOrNull())
                }

            } catch (e: Exception) {
                Timber.e(e, "Error processing pose landmarks")
                showFallbackMessage()
            }
        }
    }

    /**
     * Example of setting up privacy consent
     */
    private suspend fun requestPrivacyConsent() {
        val privacyManager = suggestionModule.providePrivacyManager()

        // In a real app, you'd show a consent dialog here
        // For this example, we'll grant consent programmatically
        privacyManager.grantConsent(ConsentPurpose.POSE_ANALYSIS)

        Timber.d("Privacy consent granted for pose analysis")
    }

    /**
     * Example of configuring API key
     */
    fun configureApiKey(apiKey: String) {
        scope.launch {
            try {
                val apiKeyManager = suggestionModule.provideApiKeyManager()
                apiKeyManager.setUserApiKey(apiKey)

                // Test the key
                val geminiClient = suggestionModule.provideGeminiClient()
                val isAvailable = geminiClient.isAvailable()

                if (isAvailable) {
                    Timber.i("Gemini API key configured successfully")
                } else {
                    Timber.w("Gemini API key configured but not available")
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to configure API key")
            }
        }
    }

    /**
     * Example of getting system status
     */
    suspend fun getSystemStatus(): String {
        val status = suggestionModule.getModuleStatus()
        val clientStatus = suggestionModule.provideClientFactory().getClientStatus()

        return buildString {
            appendLine("=== Pose Suggestion System Status ===")
            appendLine("Initialized: ${status.isInitialized}")
            appendLine("API Key: ${if (status.apiKeyConfigured) "✓" else "✗"}")
            appendLine("Privacy Consent: ${if (status.privacyConsentGiven) "✓" else "✗"}")
            appendLine("Rate Limited: ${if (status.rateLimitStatus.isLimited) "✗" else "✓"}")
            appendLine("Cache Entries: ${status.cacheStats.memoryEntries}")
            appendLine("Recommended Client: ${clientStatus.recommendedClient}")
            appendLine("Fully Operational: ${if (status.isFullyOperational) "✓" else "✗"}")
        }
    }

    /**
     * Convert MediaPipe landmarks to our internal format
     */
    private fun convertToLandmarksData(landmarks: List<Any>): PoseLandmarksData {
        // This is a placeholder - you'd convert from your actual MediaPipe format
        val convertedLandmarks = landmarks.mapIndexed { index, _ ->
            PoseLandmarksData.LandmarkPoint(
                index = index,
                x = 0.5f, // Replace with actual landmark.x
                y = 0.5f, // Replace with actual landmark.y
                z = 0.0f, // Replace with actual landmark.z
                visibility = 0.9f, // Replace with actual landmark.visibility
                presence = 0.9f   // Replace with actual landmark.presence
            )
        }

        return PoseLandmarksData(convertedLandmarks)
    }

    /**
     * Display suggestions to the user
     */
    private fun displaySuggestions(suggestions: List<com.posecoach.suggestions.models.PoseSuggestion>) {
        suggestions.forEachIndexed { index, suggestion ->
            Timber.i("Suggestion ${index + 1}:")
            Timber.i("  Title: ${suggestion.title}")
            Timber.i("  Instruction: ${suggestion.instruction}")
            Timber.i("  Focus on: ${suggestion.targetLandmarks.joinToString(", ")}")
        }

        // In a real app, you'd update your UI here
        // For example: updateSuggestionsUI(suggestions)
    }

    /**
     * Handle suggestion generation errors
     */
    private fun handleSuggestionError(error: Throwable?) {
        when (error) {
            is SecurityException -> {
                Timber.w("Privacy consent required for pose analysis")
                // Show consent dialog
            }
            is IllegalStateException -> {
                if (error.message?.contains("API key") == true) {
                    Timber.w("API key not configured, using offline fallback")
                    // Show API key configuration option
                } else {
                    Timber.e(error, "Configuration error")
                }
            }
            else -> {
                Timber.e(error, "Failed to get pose suggestions")
                showFallbackMessage()
            }
        }
    }

    /**
     * Show fallback message when suggestions are unavailable
     */
    private fun showFallbackMessage() {
        val fallbackSuggestions = listOf(
            "Keep your shoulders level and relaxed",
            "Maintain a neutral spine alignment",
            "Distribute weight evenly on both feet"
        )

        Timber.i("Showing fallback suggestions")
        fallbackSuggestions.forEachIndexed { index, suggestion ->
            Timber.i("Fallback ${index + 1}: $suggestion")
        }
    }

    /**
     * Cleanup when activity/fragment is destroyed
     */
    suspend fun cleanup() {
        suggestionModule.shutdown()
        Timber.d("Suggestion system cleaned up")
    }
}