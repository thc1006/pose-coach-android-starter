package com.posecoach.suggestions

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

class ApiKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "api_keys_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_API_ENABLED = "api_enabled"
    }

    fun getGeminiApiKey(): String? {
        return try {
            val storedKey = encryptedPrefs.getString(KEY_GEMINI_API, null)
            if (storedKey.isNullOrBlank()) {
                val envKey = System.getenv("GEMINI_API_KEY")
                if (!envKey.isNullOrBlank()) {
                    Timber.d("Using Gemini API key from environment")
                    return envKey
                }

                val buildConfigKey = BuildConfig.GEMINI_API_KEY
                if (buildConfigKey.isNotBlank()) {
                    Timber.d("Using Gemini API key from build config")
                    return buildConfigKey
                }
            }
            storedKey
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving API key")
            null
        }
    }

    fun setGeminiApiKey(apiKey: String) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_GEMINI_API, apiKey)
                .apply()
            Timber.d("Gemini API key stored securely")
        } catch (e: Exception) {
            Timber.e(e, "Error storing API key")
        }
    }

    fun clearApiKey() {
        try {
            encryptedPrefs.edit()
                .remove(KEY_GEMINI_API)
                .apply()
            Timber.d("API key cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing API key")
        }
    }

    fun isApiEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_API_ENABLED, false)
    }

    fun setApiEnabled(enabled: Boolean) {
        encryptedPrefs.edit()
            .putBoolean(KEY_API_ENABLED, enabled)
            .apply()
    }

    fun hasValidApiKey(): Boolean {
        val key = getGeminiApiKey()
        return !key.isNullOrBlank() && key.length > 10
    }
}