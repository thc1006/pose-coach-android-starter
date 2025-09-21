package com.posecoach.app.privacy

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import timber.log.Timber

class PrivacyManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "privacy_settings",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_API_CONSENT = "api_consent_given"
        private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
        private const val KEY_API_ENABLED = "api_enabled"
        private const val KEY_UPLOAD_LANDMARKS = "upload_landmarks_enabled"
        private const val KEY_LOCAL_ONLY = "local_only_mode"
    }

    fun hasApiConsent(): Boolean {
        return prefs.getBoolean(KEY_API_CONSENT, false)
    }

    fun isApiEnabled(): Boolean {
        return hasApiConsent() && prefs.getBoolean(KEY_API_ENABLED, false)
    }

    fun isUploadLandmarksEnabled(): Boolean {
        return hasApiConsent() && prefs.getBoolean(KEY_UPLOAD_LANDMARKS, false)
    }

    fun isLocalOnlyMode(): Boolean {
        return prefs.getBoolean(KEY_LOCAL_ONLY, true)
    }

    fun setApiConsent(granted: Boolean) {
        prefs.edit()
            .putBoolean(KEY_API_CONSENT, granted)
            .putLong(KEY_CONSENT_TIMESTAMP, if (granted) System.currentTimeMillis() else 0)
            .apply()

        if (!granted) {
            setApiEnabled(false)
            setUploadLandmarksEnabled(false)
        }

        Timber.d("API consent ${if (granted) "granted" else "revoked"}")
    }

    fun setApiEnabled(enabled: Boolean) {
        if (enabled && !hasApiConsent()) {
            Timber.w("Cannot enable API without consent")
            return
        }

        prefs.edit()
            .putBoolean(KEY_API_ENABLED, enabled)
            .apply()

        Timber.d("API ${if (enabled) "enabled" else "disabled"}")
    }

    fun setUploadLandmarksEnabled(enabled: Boolean) {
        if (enabled && !hasApiConsent()) {
            Timber.w("Cannot enable landmark upload without consent")
            return
        }

        prefs.edit()
            .putBoolean(KEY_UPLOAD_LANDMARKS, enabled)
            .apply()

        Timber.d("Landmark upload ${if (enabled) "enabled" else "disabled"}")
    }

    fun setLocalOnlyMode(localOnly: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LOCAL_ONLY, localOnly)
            .apply()

        if (localOnly) {
            setApiEnabled(false)
            setUploadLandmarksEnabled(false)
        }

        Timber.d("Local-only mode ${if (localOnly) "enabled" else "disabled"}")
    }

    fun showConsentDialog(
        onAccept: () -> Unit,
        onDecline: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Privacy Notice")
            .setMessage("""
                This app can provide AI-powered pose suggestions by analyzing your body landmarks.

                What we collect:
                • Only numerical landmark coordinates (33 points)
                • Never actual images or videos
                • Data is processed and immediately discarded

                How it works:
                • Landmark data is sent to Gemini AI for analysis
                • You receive personalized posture suggestions
                • All processing is anonymous

                You can:
                • Use the app without AI features (local-only mode)
                • Enable/disable AI suggestions anytime
                • Revoke consent in settings

                Do you consent to use AI-powered suggestions?
            """.trimIndent())
            .setPositiveButton("Accept & Enable AI") { _, _ ->
                setApiConsent(true)
                setApiEnabled(true)
                setLocalOnlyMode(false)
                onAccept()
            }
            .setNegativeButton("Decline (Local Only)") { _, _ ->
                setApiConsent(false)
                setLocalOnlyMode(true)
                onDecline()
            }
            .setCancelable(false)
            .show()
    }

    fun getConsentTimestamp(): Long {
        return prefs.getLong(KEY_CONSENT_TIMESTAMP, 0)
    }

    fun clearAllSettings() {
        prefs.edit().clear().apply()
        Timber.d("All privacy settings cleared")
    }
}