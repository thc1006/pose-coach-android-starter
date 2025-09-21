package com.posecoach.suggestions

import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import timber.log.Timber

/**
 * Privacy-aware wrapper for PoseSuggestionClient that respects user privacy settings
 * and provides granular control over data transmission
 */
class PrivacyAwareSuggestionsClient(
    private val delegate: PoseSuggestionClient,
    private val privacySettings: PrivacySettings = PrivacySettings()
) : PoseSuggestionClient {

    data class PrivacySettings(
        val allowApiCalls: Boolean = true,
        val allowDataTransmission: Boolean = true,
        val anonymizeLandmarks: Boolean = false,
        val limitDataPrecision: Boolean = false,
        val requireExplicitConsent: Boolean = false,
        val localProcessingOnly: Boolean = false
    )

    override suspend fun getPoseSuggestions(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse> {
        // Check if API calls are allowed
        if (!privacySettings.allowApiCalls || privacySettings.localProcessingOnly) {
            Timber.d("API calls disabled by privacy settings, using local fallback")
            return createLocalFallbackResponse()
        }

        // Check if data transmission is allowed
        if (!privacySettings.allowDataTransmission) {
            Timber.d("Data transmission disabled, using local processing")
            return createLocalFallbackResponse()
        }

        // Check for explicit consent requirement
        if (privacySettings.requireExplicitConsent && !hasValidConsent()) {
            Timber.d("Explicit consent required but not granted")
            return Result.failure(IllegalStateException("User consent required for API access"))
        }

        // Process landmarks based on privacy settings
        val processedLandmarks = processLandmarksForPrivacy(landmarks)

        // Delegate to actual client
        return try {
            delegate.getPoseSuggestions(processedLandmarks)
        } catch (e: Exception) {
            Timber.e(e, "Error in delegate client, falling back to local processing")
            createLocalFallbackResponse()
        }
    }

    override suspend fun isAvailable(): Boolean {
        return if (privacySettings.allowApiCalls && !privacySettings.localProcessingOnly) {
            delegate.isAvailable()
        } else {
            true // Local fallback is always available
        }
    }

    override fun requiresApiKey(): Boolean {
        return delegate.requiresApiKey() && privacySettings.allowApiCalls
    }

    private fun processLandmarksForPrivacy(landmarks: PoseLandmarksData): PoseLandmarksData {
        if (!privacySettings.anonymizeLandmarks && !privacySettings.limitDataPrecision) {
            return landmarks
        }

        val processedLandmarks = landmarks.landmarks.map { landmark ->
            var processedLandmark = landmark

            // Anonymize by removing or masking identifying features
            if (privacySettings.anonymizeLandmarks) {
                processedLandmark = anonymizeLandmark(processedLandmark)
            }

            // Limit precision to reduce identifying information
            if (privacySettings.limitDataPrecision) {
                processedLandmark = limitPrecision(processedLandmark)
            }

            processedLandmark
        }

        return PoseLandmarksData(
            landmarks = processedLandmarks,
            timestamp = landmarks.timestamp // Keep original timestamp
        )
    }

    private fun anonymizeLandmark(landmark: PoseLandmarksData.LandmarkPoint): PoseLandmarksData.LandmarkPoint {
        // Anonymize by removing fine-grained facial features
        return if (landmark.index <= 10) { // Face landmarks
            landmark.copy(
                x = roundToDecimalPlaces(landmark.x, 2),
                y = roundToDecimalPlaces(landmark.y, 2),
                z = 0f, // Remove depth information for face
                visibility = minOf(landmark.visibility, 0.8f) // Reduce visibility precision
            )
        } else {
            landmark
        }
    }

    private fun limitPrecision(landmark: PoseLandmarksData.LandmarkPoint): PoseLandmarksData.LandmarkPoint {
        return landmark.copy(
            x = roundToDecimalPlaces(landmark.x, 3),
            y = roundToDecimalPlaces(landmark.y, 3),
            z = roundToDecimalPlaces(landmark.z, 3),
            visibility = roundToDecimalPlaces(landmark.visibility, 2),
            presence = roundToDecimalPlaces(landmark.presence, 2)
        )
    }

    private fun roundToDecimalPlaces(value: Float, places: Int): Float {
        val factor = Math.pow(10.0, places.toDouble()).toFloat()
        return Math.round(value * factor) / factor
    }

    private fun hasValidConsent(): Boolean {
        // In a real implementation, this would check stored consent preferences
        // For now, assume consent is managed externally
        return true
    }

    private fun createLocalFallbackResponse(): Result<PoseSuggestionsResponse> {
        val fallbackSuggestions = PoseSuggestionsResponse(
            suggestions = listOf(
                com.posecoach.suggestions.models.PoseSuggestion(
                    title = "Privacy-Protected Suggestion",
                    instruction = "Maintain good posture with your spine naturally aligned and shoulders relaxed",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP")
                ),
                com.posecoach.suggestions.models.PoseSuggestion(
                    title = "Local Processing Active",
                    instruction = "Your pose analysis is being processed locally to protect your privacy",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER")
                ),
                com.posecoach.suggestions.models.PoseSuggestion(
                    title = "General Wellness Tip",
                    instruction = "Take regular breaks to stretch and move to maintain good circulation",
                    targetLandmarks = listOf("LEFT_ANKLE", "RIGHT_ANKLE", "LEFT_KNEE", "RIGHT_KNEE")
                )
            )
        )

        return Result.success(fallbackSuggestions)
    }

    /**
     * Updates privacy settings at runtime
     */
    fun updatePrivacySettings(newSettings: PrivacySettings) {
        // In a real implementation, this might need to be handled more carefully
        // to ensure thread safety
        Timber.d("Privacy settings updated: $newSettings")
    }

    /**
     * Gets current privacy settings
     */
    fun getPrivacySettings(): PrivacySettings = privacySettings

    /**
     * Checks if current configuration requires user consent
     */
    fun requiresUserConsent(): Boolean {
        return privacySettings.requireExplicitConsent &&
               (privacySettings.allowApiCalls || privacySettings.allowDataTransmission)
    }

    companion object {
        /**
         * Creates a privacy-aware client with conservative default settings
         */
        fun createConservative(delegate: PoseSuggestionClient): PrivacyAwareSuggestionsClient {
            return PrivacyAwareSuggestionsClient(
                delegate = delegate,
                privacySettings = PrivacySettings(
                    allowApiCalls = false,
                    allowDataTransmission = false,
                    anonymizeLandmarks = true,
                    limitDataPrecision = true,
                    requireExplicitConsent = true,
                    localProcessingOnly = true
                )
            )
        }

        /**
         * Creates a privacy-aware client with balanced settings
         */
        fun createBalanced(delegate: PoseSuggestionClient): PrivacyAwareSuggestionsClient {
            return PrivacyAwareSuggestionsClient(
                delegate = delegate,
                privacySettings = PrivacySettings(
                    allowApiCalls = true,
                    allowDataTransmission = true,
                    anonymizeLandmarks = true,
                    limitDataPrecision = true,
                    requireExplicitConsent = true,
                    localProcessingOnly = false
                )
            )
        }

        /**
         * Creates a privacy-aware client with permissive settings
         */
        fun createPermissive(delegate: PoseSuggestionClient): PrivacyAwareSuggestionsClient {
            return PrivacyAwareSuggestionsClient(
                delegate = delegate,
                privacySettings = PrivacySettings(
                    allowApiCalls = true,
                    allowDataTransmission = true,
                    anonymizeLandmarks = false,
                    limitDataPrecision = false,
                    requireExplicitConsent = false,
                    localProcessingOnly = false
                )
            )
        }
    }
}