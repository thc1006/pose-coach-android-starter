package com.posecoach.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Manages ephemeral token authentication for Gemini Live API
 * Features:
 * - Secure token storage
 * - Automatic token refresh
 * - Ephemeral token generation
 * - Token expiration handling
 */
class TokenManager(private val context: Context) {

    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_NAME = "gemini_auth_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_TOKEN = "ephemeral_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val TOKEN_REFRESH_THRESHOLD = 5 * 60 * 1000L // 5 minutes before expiry
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Initialize with API key
     */
    fun setApiKey(apiKey: String) {
        encryptedPrefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    /**
     * Get current ephemeral token, refreshing if necessary
     */
    suspend fun getEphemeralToken(): String? = withContext(Dispatchers.IO) {
        val currentToken = encryptedPrefs.getString(KEY_TOKEN, null)
        val expiryTime = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)

        // Check if token needs refresh
        val now = System.currentTimeMillis()
        if (currentToken != null && expiryTime > now + TOKEN_REFRESH_THRESHOLD) {
            return@withContext currentToken
        }

        // Generate new ephemeral token
        return@withContext generateEphemeralToken()
    }

    /**
     * Generate a new ephemeral token
     */
    private suspend fun generateEphemeralToken(): String? = suspendCoroutine { continuation ->
        val apiKey = encryptedPrefs.getString(KEY_API_KEY, null)
        if (apiKey == null) {
            Log.e(TAG, "API key not found")
            continuation.resume(null)
            return@suspendCoroutine
        }

        val requestBody = JSONObject().apply {
            put("ttlSeconds", 900) // 15 minutes to match session duration
            put("scopes", arrayOf("https://www.googleapis.com/auth/generative-language"))
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1/tokens:generateAccessToken")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to generate ephemeral token", e)
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        val token = json.getString("accessToken")
                        val expiresIn = json.optLong("expiresIn", 900) * 1000 // Convert to milliseconds
                        val expiryTime = System.currentTimeMillis() + expiresIn

                        // Store token securely
                        encryptedPrefs.edit()
                            .putString(KEY_TOKEN, token)
                            .putLong(KEY_TOKEN_EXPIRY, expiryTime)
                            .apply()

                        Log.d(TAG, "Successfully generated ephemeral token")
                        continuation.resume(token)
                    } else {
                        val error = "Token generation failed: ${response.code} - $responseBody"
                        Log.e(TAG, error)
                        continuation.resumeWithException(Exception(error))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing token response", e)
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    /**
     * Check if current token is valid
     */
    fun isTokenValid(): Boolean {
        val token = encryptedPrefs.getString(KEY_TOKEN, null)
        val expiryTime = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        val now = System.currentTimeMillis()

        return token != null && expiryTime > now + TOKEN_REFRESH_THRESHOLD
    }

    /**
     * Get token expiry time
     */
    fun getTokenExpiryTime(): Long {
        return encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
    }

    /**
     * Clear all stored tokens
     */
    fun clearTokens() {
        encryptedPrefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    /**
     * Generate session ID for tracking
     */
    fun generateSessionId(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Validate API key format
     */
    fun validateApiKey(apiKey: String): Boolean {
        return apiKey.isNotBlank() &&
               apiKey.length >= 32 &&
               apiKey.matches(Regex("^[A-Za-z0-9_-]+$"))
    }

    /**
     * Get remaining token time in minutes
     */
    fun getRemainingTokenTime(): Long {
        val expiryTime = getTokenExpiryTime()
        val now = System.currentTimeMillis()
        return if (expiryTime > now) {
            (expiryTime - now) / 60000 // Convert to minutes
        } else {
            0
        }
    }

    /**
     * Schedule automatic token refresh
     */
    suspend fun scheduleTokenRefresh(): Boolean = withContext(Dispatchers.IO) {
        try {
            val newToken = generateEphemeralToken()
            return@withContext newToken != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh token", e)
            return@withContext false
        }
    }

    /**
     * Get token status information
     */
    fun getTokenStatus(): TokenStatus {
        val token = encryptedPrefs.getString(KEY_TOKEN, null)
        val expiryTime = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        val now = System.currentTimeMillis()

        return when {
            token == null -> TokenStatus.NOT_AVAILABLE
            expiryTime <= now -> TokenStatus.EXPIRED
            expiryTime <= now + TOKEN_REFRESH_THRESHOLD -> TokenStatus.EXPIRING_SOON
            else -> TokenStatus.VALID
        }
    }

    /**
     * Create authorization header
     */
    fun createAuthHeader(): String? {
        val token = encryptedPrefs.getString(KEY_TOKEN, null)
        return if (token != null) "Bearer $token" else null
    }
}

/**
 * Token status enumeration
 */
enum class TokenStatus {
    NOT_AVAILABLE,
    VALID,
    EXPIRING_SOON,
    EXPIRED
}