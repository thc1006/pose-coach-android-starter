package com.posecoach.app.privacy

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.*

/**
 * ConsentManager - Simplified consent management interface
 * Provides compatibility layer for existing privacy components
 * Following CLAUDE.md requirements:
 * - Privacy-first architecture
 * - On-device processing as default
 * - User consent for data upload
 */
class ConsentManager(private val context: Context) {

    /**
     * Types of consent that can be requested
     */
    enum class ConsentType {
        CAMERA_ACCESS,          // Local camera access for pose detection
        LANDMARK_DATA,          // Upload pose landmark coordinates
        AUDIO_CAPTURE,          // Audio recording and processing
        PERFORMANCE_METRICS,    // Anonymous performance data
        ANALYTICS,              // Usage analytics
        IMAGE_UPLOAD            // Image upload (permanently blocked)
    }

    /**
     * Levels of consent for simplified consent management
     */
    enum class ConsentLevel {
        MINIMAL,    // Local processing only
        STANDARD,   // Local + landmark data
        FULL        // All features except image upload
    }

    /**
     * Data types that can be processed
     */
    enum class DataType {
        POSE_LANDMARKS,         // Body pose coordinates
        AUDIO_RECORDINGS,       // Voice commands and feedback
        CAMERA_IMAGES,          // Camera feed for pose detection
        BIOMETRIC_DATA,         // Heart rate, movement patterns
        USAGE_METRICS,          // App usage and interaction data
        DEVICE_INFORMATION,     // Device specs and capabilities
        LOCATION_DATA,          // Geographic location
        PERSONAL_INFORMATION,   // Name, age, fitness goals
        HEALTH_DATA,           // Medical conditions, limitations
        WORKOUT_HISTORY        // Historical workout data
    }

    /**
     * Processing locations for data handling
     */
    enum class ProcessingLocation {
        LOCAL_DEVICE,          // On-device processing only
        EDGE_COMPUTING,        // Local edge servers
        REGIONAL_CLOUD,        // Regional cloud infrastructure
        GLOBAL_CLOUD,          // Global cloud services
        THIRD_PARTY_SERVICES,  // External service providers
        FEDERATED_NETWORK      // Federated learning network
    }

    @Serializable
    data class ConsentState(
        val consents: Map<ConsentType, Boolean> = emptyMap(),
        val level: ConsentLevel = ConsentLevel.MINIMAL,
        val timestamp: Long = System.currentTimeMillis(),
        val version: Int = CONSENT_VERSION
    )

    companion object {
        private const val PREFS_NAME = "consent_manager_simple"
        private const val KEY_CONSENT_STATE = "consent_state"
        private const val CONSENT_VERSION = 1

        // Legacy keys for backward compatibility
        private const val KEY_USER_CONSENT = "user_consent_given"
        private const val KEY_DATA_UPLOAD_CONSENT = "data_upload_consent"
        private const val KEY_OFFLINE_MODE = "offline_mode_enabled"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val CURRENT_CONSENT_VERSION = 1

        // Define what each consent level includes
        private val CONSENT_LEVEL_MAPPINGS = mapOf(
            ConsentLevel.MINIMAL to setOf(ConsentType.CAMERA_ACCESS),
            ConsentLevel.STANDARD to setOf(
                ConsentType.CAMERA_ACCESS,
                ConsentType.LANDMARK_DATA,
                ConsentType.PERFORMANCE_METRICS
            ),
            ConsentLevel.FULL to setOf(
                ConsentType.CAMERA_ACCESS,
                ConsentType.LANDMARK_DATA,
                ConsentType.AUDIO_CAPTURE,
                ConsentType.PERFORMANCE_METRICS,
                ConsentType.ANALYTICS
            )
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to create encrypted preferences, using regular prefs")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Legacy prefs for backward compatibility
    private val legacyPrefs: SharedPreferences = context.getSharedPreferences(
        "pose_coach_privacy",
        Context.MODE_PRIVATE
    )

    private val _consentState = MutableStateFlow(loadConsentState())
    val consentState: StateFlow<ConsentState> = _consentState.asStateFlow()

    init {
        Timber.d("ConsentManager initialized")
        migrateLegacyPrefs()
        validateConsentState()
    }

    /**
     * Check if consent is granted for a specific type
     */
    fun hasConsent(type: ConsentType): Boolean {
        val state = _consentState.value

        // Image upload is permanently blocked
        if (type == ConsentType.IMAGE_UPLOAD) {
            return false
        }

        return state.consents[type] ?: false
    }

    /**
     * Check if consent is granted for a specific data type by string name (for compatibility)
     */
    fun hasConsentForDataType(dataTypeName: String): Boolean {
        return when (dataTypeName.lowercase()) {
            "pose_analysis", "pose_landmarks" -> hasConsent(ConsentType.LANDMARK_DATA)
            "performance_metrics" -> hasConsent(ConsentType.PERFORMANCE_METRICS)
            "device_telemetry", "analytics" -> hasConsent(ConsentType.ANALYTICS)
            "audio_capture", "audio" -> hasConsent(ConsentType.AUDIO_CAPTURE)
            "camera_access", "camera" -> hasConsent(ConsentType.CAMERA_ACCESS)
            "image_upload", "images" -> false // Always false
            else -> false
        }
    }

    /**
     * Grant or revoke consent for a specific type
     */
    fun grantConsent(type: ConsentType, granted: Boolean) {
        val currentState = _consentState.value

        // Image upload consent cannot be granted
        if (type == ConsentType.IMAGE_UPLOAD && granted) {
            Timber.w("Attempted to grant image upload consent - blocked by policy")
            return
        }

        val updatedConsents = currentState.consents.toMutableMap()
        updatedConsents[type] = granted

        val updatedState = currentState.copy(
            consents = updatedConsents,
            timestamp = System.currentTimeMillis()
        )

        _consentState.value = updatedState
        saveConsentState(updatedState)

        Timber.i("Consent ${if (granted) "granted" else "revoked"} for $type")
    }

    /**
     * Request consent for a specific type (returns current state)
     */
    fun requestConsent(type: ConsentType): Boolean {
        // For image upload, always return false
        if (type == ConsentType.IMAGE_UPLOAD) {
            Timber.w("Image upload consent requested - permanently blocked")
            return false
        }

        return hasConsent(type)
    }

    /**
     * Set consent level (applies predefined set of consents)
     */
    fun setConsentLevel(level: ConsentLevel) {
        val allowedTypes = CONSENT_LEVEL_MAPPINGS[level] ?: emptySet()
        val currentState = _consentState.value

        val updatedConsents = mutableMapOf<ConsentType, Boolean>()

        // Set all consent types based on the level
        ConsentType.values().forEach { type ->
            when (type) {
                ConsentType.IMAGE_UPLOAD -> updatedConsents[type] = false // Always false
                else -> updatedConsents[type] = allowedTypes.contains(type)
            }
        }

        val updatedState = currentState.copy(
            consents = updatedConsents,
            level = level,
            timestamp = System.currentTimeMillis()
        )

        _consentState.value = updatedState
        saveConsentState(updatedState)

        Timber.i("Consent level set to $level")
    }

    /**
     * Withdraw all consents (reset to minimal)
     */
    fun withdrawAllConsent() {
        val updatedConsents = mutableMapOf<ConsentType, Boolean>()

        // Only keep camera access (required for basic functionality)
        ConsentType.values().forEach { type ->
            updatedConsents[type] = when (type) {
                ConsentType.CAMERA_ACCESS -> true
                else -> false
            }
        }

        val updatedState = _consentState.value.copy(
            consents = updatedConsents,
            level = ConsentLevel.MINIMAL,
            timestamp = System.currentTimeMillis()
        )

        _consentState.value = updatedState
        saveConsentState(updatedState)

        Timber.i("All consents withdrawn - reset to minimal level")
    }

    /**
     * Get current consent level
     */
    fun getCurrentLevel(): ConsentLevel {
        return _consentState.value.level
    }

    /**
     * Check if any uploads are allowed (for privacy mode determination)
     */
    fun hasAnyUploadConsent(): Boolean {
        val state = _consentState.value
        val uploadTypes = setOf(
            ConsentType.LANDMARK_DATA,
            ConsentType.AUDIO_CAPTURE,
            ConsentType.PERFORMANCE_METRICS,
            ConsentType.ANALYTICS
        )

        return uploadTypes.any { state.consents[it] == true }
    }

    // Legacy compatibility methods

    /**
     * Check if user has given basic consent to use the app (legacy)
     */
    fun hasUserConsent(): Boolean {
        return hasConsent(ConsentType.CAMERA_ACCESS)
    }

    /**
     * Check if user has consented to data upload (legacy)
     */
    fun hasDataUploadConsent(): Boolean {
        return hasConsent(ConsentType.LANDMARK_DATA)
    }

    /**
     * Check if offline mode is enabled (legacy)
     */
    fun isOfflineModeEnabled(): Boolean {
        return !hasAnyUploadConsent()
    }

    /**
     * Show consent dialog to user (legacy - simplified version)
     */
    fun showConsentDialog(callback: (Boolean) -> Unit) {
        if (context !is AppCompatActivity) {
            // If not in activity context, just return current state
            callback(hasUserConsent())
            return
        }

        val activity = context as AppCompatActivity
        AlertDialog.Builder(activity)
            .setTitle("Privacy & Consent")
            .setMessage("""
                This app uses pose detection to provide coaching suggestions.

                By default, all processing happens on your device for privacy.

                You can optionally enable cloud features for enhanced suggestions.

                Do you consent to use this app?
            """.trimIndent())
            .setPositiveButton("Accept") { _, _ ->
                grantConsent(ConsentType.CAMERA_ACCESS, true)
                showDataUploadOptions(callback)
            }
            .setNegativeButton("Decline") { _, _ ->
                callback(false)
            }
            .setCancelable(false)
            .show()
    }

    private fun showDataUploadOptions(callback: (Boolean) -> Unit) {
        if (context !is AppCompatActivity) {
            callback(true)
            return
        }

        val activity = context as AppCompatActivity
        AlertDialog.Builder(activity)
            .setTitle("Data Upload Options")
            .setMessage("""
                Choose your privacy preference:

                • On-Device Only: All processing stays on your device (recommended)
                • Cloud Enhanced: Send pose data to improve suggestions

                You can change this anytime in settings.
            """.trimIndent())
            .setPositiveButton("Cloud Enhanced") { _, _ ->
                setConsentLevel(ConsentLevel.STANDARD)
                callback(true)
            }
            .setNegativeButton("On-Device Only") { _, _ ->
                setConsentLevel(ConsentLevel.MINIMAL)
                callback(true)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Update privacy preferences (for settings screen)
     */
    fun updateDataUploadConsent(granted: Boolean) {
        grantConsent(ConsentType.LANDMARK_DATA, granted)
    }

    fun updateOfflineMode(enabled: Boolean) {
        if (enabled) {
            setConsentLevel(ConsentLevel.MINIMAL)
        } else {
            setConsentLevel(ConsentLevel.STANDARD)
        }
    }

    /**
     * Reset all consent (for testing or privacy reset)
     */
    fun resetAllConsent() {
        withdrawAllConsent()
        encryptedPrefs.edit().clear().apply()
        legacyPrefs.edit().clear().apply()
    }

    /**
     * Get privacy status summary
     */
    fun getPrivacyStatus(): Map<String, Boolean> {
        return mapOf(
            "user_consent" to hasUserConsent(),
            "data_upload_consent" to hasDataUploadConsent(),
            "offline_mode" to isOfflineModeEnabled()
        )
    }

    /**
     * Get consent summary for display
     */
    fun getConsentSummary(): Map<ConsentType, Boolean> {
        return _consentState.value.consents.toMap()
    }

    /**
     * Export consent data for audit/compliance
     */
    fun exportConsentData(): ConsentState {
        return _consentState.value.copy()
    }

    /**
     * Validate current consent state and fix any inconsistencies
     */
    private fun validateConsentState() {
        val state = _consentState.value
        var needsUpdate = false
        val updatedConsents = state.consents.toMutableMap()

        // Ensure image upload is always false
        if (updatedConsents[ConsentType.IMAGE_UPLOAD] == true) {
            updatedConsents[ConsentType.IMAGE_UPLOAD] = false
            needsUpdate = true
            Timber.w("Fixed image upload consent - enforced policy block")
        }

        // Ensure camera access is always granted (required for basic functionality)
        if (updatedConsents[ConsentType.CAMERA_ACCESS] != true) {
            updatedConsents[ConsentType.CAMERA_ACCESS] = true
            needsUpdate = true
            Timber.i("Granted camera access - required for basic functionality")
        }

        if (needsUpdate) {
            val updatedState = state.copy(
                consents = updatedConsents,
                timestamp = System.currentTimeMillis()
            )
            _consentState.value = updatedState
            saveConsentState(updatedState)
        }
    }

    private fun migrateLegacyPrefs() {
        // Check if we have legacy preferences to migrate
        if (legacyPrefs.contains(KEY_USER_CONSENT) || legacyPrefs.contains(KEY_DATA_UPLOAD_CONSENT)) {
            Timber.i("Migrating legacy consent preferences")

            val userConsent = legacyPrefs.getBoolean(KEY_USER_CONSENT, false)
            val dataUploadConsent = legacyPrefs.getBoolean(KEY_DATA_UPLOAD_CONSENT, false)
            val offlineMode = legacyPrefs.getBoolean(KEY_OFFLINE_MODE, true)

            // Determine the appropriate consent level
            val level = when {
                !userConsent -> ConsentLevel.MINIMAL
                dataUploadConsent && !offlineMode -> ConsentLevel.STANDARD
                else -> ConsentLevel.MINIMAL
            }

            setConsentLevel(level)

            // Clear legacy preferences after migration
            legacyPrefs.edit().clear().apply()
            Timber.i("Legacy preferences migrated to new system")
        }
    }

    private fun loadConsentState(): ConsentState {
        return try {
            val jsonString = encryptedPrefs.getString(KEY_CONSENT_STATE, null)
            if (jsonString != null) {
                json.decodeFromString<ConsentState>(jsonString)
            } else {
                // Default to minimal consent level
                ConsentState(
                    consents = mapOf(ConsentType.CAMERA_ACCESS to true),
                    level = ConsentLevel.MINIMAL
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load consent state, using defaults")
            ConsentState(
                consents = mapOf(ConsentType.CAMERA_ACCESS to true),
                level = ConsentLevel.MINIMAL
            )
        }
    }

    private fun saveConsentState(state: ConsentState) {
        try {
            val jsonString = json.encodeToString(state)
            encryptedPrefs.edit()
                .putString(KEY_CONSENT_STATE, jsonString)
                .apply()
            Timber.d("Consent state saved successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save consent state")
        }
    }
}
