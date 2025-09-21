package com.posecoach.app.livecoach.config

import android.content.Context
import com.posecoach.app.BuildConfig
import timber.log.Timber
import java.util.Properties

/**
 * Secure API key management that reads from build configuration only.
 * Never stores API keys in source code.
 */
class SecureApiKeyManager(private val context: Context) {

    companion object {
        private const val PROPERTY_GEMINI_API_KEY = "GEMINI_API_KEY"
    }

    /**
     * Get API key from secure sources only:
     * 1. BuildConfig (from local.properties)
     * 2. Environment variables
     * Never from hardcoded strings!
     */
    fun getApiKey(): String? {
        return try {
            // First try BuildConfig (populated from local.properties)
            val buildConfigKey = getBuildConfigApiKey()
            if (!buildConfigKey.isNullOrEmpty()) {
                Timber.d("Using API key from build configuration")
                return buildConfigKey
            }

            // Fallback to environment variable
            val envKey = System.getenv(PROPERTY_GEMINI_API_KEY)
            if (!envKey.isNullOrEmpty()) {
                Timber.d("Using API key from environment variable")
                return envKey
            }

            Timber.w("No API key found in secure sources")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error reading API key from secure sources")
            null
        }
    }

    private fun getBuildConfigApiKey(): String? {
        return try {
            // Use reflection to access BuildConfig fields safely
            val buildConfigClass = Class.forName("${context.packageName}.BuildConfig")
            val field = buildConfigClass.getDeclaredField(PROPERTY_GEMINI_API_KEY)
            field.get(null) as? String
        } catch (e: Exception) {
            Timber.d("API key not found in BuildConfig")
            null
        }
    }

    fun hasValidApiKey(): Boolean {
        val apiKey = getApiKey()
        return !apiKey.isNullOrEmpty() &&
               apiKey.startsWith("AIza") &&
               apiKey.length >= 35
    }

    fun validateApiKey(apiKey: String?): Boolean {
        return !apiKey.isNullOrEmpty() &&
               apiKey.startsWith("AIza") &&
               apiKey.length >= 35 &&
               apiKey.matches(Regex("^[A-Za-z0-9_-]+$"))
    }

    fun getObfuscatedApiKey(): String {
        val apiKey = getApiKey()
        return if (!apiKey.isNullOrEmpty() && apiKey.length > 10) {
            "${apiKey.take(6)}...${apiKey.takeLast(4)}"
        } else {
            "Not configured"
        }
    }
}