package com.posecoach.app.livecoach.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.posecoach.app.BuildConfig
import timber.log.Timber

class LiveApiKeyManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "live_api_secure_prefs"
        private const val KEY_API_KEY = "gemini_api_key"
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
            // Priority 1: Check encrypted shared preferences
            val storedKey = encryptedPrefs.getString(KEY_API_KEY, null)
            if (!storedKey.isNullOrEmpty()) {
                Timber.d("Using stored API key from encrypted preferences")
                return storedKey
            }

            // Priority 2: Check BuildConfig (from local.properties)
            val buildConfigKey = try {
                if (BuildConfig.GEMINI_LIVE_API_KEY.isNotEmpty()) {
                    BuildConfig.GEMINI_LIVE_API_KEY
                } else if (BuildConfig.GEMINI_API_KEY.isNotEmpty()) {
                    BuildConfig.GEMINI_API_KEY
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "BuildConfig API key not available")
                null
            }

            if (!buildConfigKey.isNullOrEmpty()) {
                Timber.d("Using API key from BuildConfig (local.properties)")
                // Store for future use
                setApiKey(buildConfigKey)
                return buildConfigKey
            }

            // Priority 3: Check environment variable
            val envKey = System.getenv("GEMINI_API_KEY")
                ?: System.getenv("GEMINI_LIVE_API_KEY")
            if (!envKey.isNullOrEmpty()) {
                Timber.d("Using API key from environment variable")
                return envKey
            }

            // No API key found
            Timber.e("No API key configured! Please set it in local.properties or via setApiKey()")
            ""
        } catch (e: Exception) {
            Timber.e(e, "Error reading API key")
            ""
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
        val isValid = apiKey.isNotEmpty() &&
               apiKey.startsWith("AIza") &&
               apiKey.length >= 35

        if (!isValid) {
            Timber.e("API Key validation failed: empty=${apiKey.isEmpty()}, format=${apiKey.startsWith("AIza")}, length=${apiKey.length}")
        }

        return isValid
    }

    fun validateApiKey(apiKey: String): Boolean {
        val isValid = apiKey.startsWith("AIza") &&
               apiKey.length >= 35 &&
               apiKey.matches(Regex("^[A-Za-z0-9_-]+$"))

        if (!isValid) {
            Timber.w("API Key validation failed for provided key: length=${apiKey.length}, format=${apiKey.take(4)}")
        }

        return isValid
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