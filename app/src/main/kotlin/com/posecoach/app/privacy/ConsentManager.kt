package com.posecoach.app.privacy

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Manages user consent and privacy controls
 * Following CLAUDE.md requirements:
 * - Privacy-first architecture
 * - On-device processing as default
 * - User consent for data upload
 */
class ConsentManager(private val activity: AppCompatActivity) {

    private val prefs: SharedPreferences = activity.getSharedPreferences(
        "pose_coach_privacy", 
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_USER_CONSENT = "user_consent_given"
        private const val KEY_DATA_UPLOAD_CONSENT = "data_upload_consent"
        private const val KEY_OFFLINE_MODE = "offline_mode_enabled"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val CURRENT_CONSENT_VERSION = 1
    }

    /**
     * Check if user has given basic consent to use the app
     */
    fun hasUserConsent(): Boolean {
        val hasConsent = prefs.getBoolean(KEY_USER_CONSENT, false)
        val consentVersion = prefs.getInt(KEY_CONSENT_VERSION, 0)
        
        // Check if consent is still valid for current version
        return hasConsent && consentVersion >= CURRENT_CONSENT_VERSION
    }

    /**
     * Check if user has consented to data upload
     */
    fun hasDataUploadConsent(): Boolean {
        return prefs.getBoolean(KEY_DATA_UPLOAD_CONSENT, false)
    }

    /**
     * Check if offline mode is enabled (default per CLAUDE.md)
     */
    fun isOfflineModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_OFFLINE_MODE, true) // Default to offline
    }

    /**
     * Show consent dialog to user
     */
    fun showConsentDialog(callback: (Boolean) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Privacy & Consent")
            .setMessage("""
                This app uses pose detection to provide coaching suggestions.
                
                By default, all processing happens on your device for privacy.
                
                You can optionally enable cloud features for enhanced suggestions.
                
                Do you consent to use this app?
            """.trimIndent())
            .setPositiveButton("Accept") { _, _ ->
                saveUserConsent(true)
                showDataUploadOptions(callback)
            }
            .setNegativeButton("Decline") { _, _ ->
                callback(false)
            }
            .setCancelable(false)
            .show()
    }

    private fun showDataUploadOptions(callback: (Boolean) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Data Upload Options")
            .setMessage("""
                Choose your privacy preference:
                
                • On-Device Only: All processing stays on your device (recommended)
                • Cloud Enhanced: Send pose data to improve suggestions
                
                You can change this anytime in settings.
            """.trimIndent())
            .setPositiveButton("Cloud Enhanced") { _, _ ->
                saveDataUploadConsent(true)
                setOfflineMode(false)
                callback(true)
            }
            .setNegativeButton("On-Device Only") { _, _ ->
                saveDataUploadConsent(false)
                setOfflineMode(true)
                callback(true)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Save user consent preferences
     */
    private fun saveUserConsent(granted: Boolean) {
        prefs.edit()
            .putBoolean(KEY_USER_CONSENT, granted)
            .putInt(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION)
            .apply()
    }

    private fun saveDataUploadConsent(granted: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DATA_UPLOAD_CONSENT, granted)
            .apply()
    }

    private fun setOfflineMode(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_OFFLINE_MODE, enabled)
            .apply()
    }

    /**
     * Update privacy preferences (for settings screen)
     */
    fun updateDataUploadConsent(granted: Boolean) {
        saveDataUploadConsent(granted)
        setOfflineMode(!granted)
    }

    fun updateOfflineMode(enabled: Boolean) {
        setOfflineMode(enabled)
        if (enabled) {
            // If enabling offline mode, disable data upload
            saveDataUploadConsent(false)
        }
    }

    /**
     * Reset all consent (for testing or privacy reset)
     */
    fun resetAllConsent() {
        prefs.edit().clear().apply()
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
}
