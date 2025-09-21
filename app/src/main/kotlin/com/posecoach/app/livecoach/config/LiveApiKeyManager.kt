package com.posecoach.app.livecoach.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

class LiveApiKeyManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "live_api_secure_prefs"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val DEFAULT_API_KEY = "AIzaSyDAckkkZGtSOjAnyUJsWvG3hZGFM39TLXI"
    }

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create encrypted preferences, falling back to regular prefs")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getApiKey(): String {
        return try {
            val storedKey = encryptedPrefs.getString(KEY_API_KEY, null)
            when {
                !storedKey.isNullOrEmpty() -> {
                    Timber.d("Using stored API key")
                    storedKey
                }
                else -> {
                    Timber.d("Using default API key")
                    setApiKey(DEFAULT_API_KEY)
                    DEFAULT_API_KEY
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading API key, using default")
            DEFAULT_API_KEY
        }
    }

    fun setApiKey(apiKey: String) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_API_KEY, apiKey)
                .apply()
            Timber.d("API key stored securely")
        } catch (e: Exception) {
            Timber.e(e, "Failed to store API key")
        }
    }

    fun hasValidApiKey(): Boolean {
        val apiKey = getApiKey()
        return apiKey.isNotEmpty() &&
               apiKey.startsWith("AIza") &&
               apiKey.length >= 35
    }

    fun validateApiKey(apiKey: String): Boolean {
        return apiKey.startsWith("AIza") &&
               apiKey.length >= 35 &&
               apiKey.matches(Regex("^[A-Za-z0-9_-]+$"))
    }

    fun clearApiKey() {
        try {
            encryptedPrefs.edit()
                .remove(KEY_API_KEY)
                .apply()
            Timber.d("API key cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear API key")
        }
    }

    fun getObfuscatedApiKey(): String {
        val apiKey = getApiKey()
        return if (apiKey.length > 10) {
            "${apiKey.take(6)}...${apiKey.takeLast(4)}"
        } else {
            "****"
        }
    }
}