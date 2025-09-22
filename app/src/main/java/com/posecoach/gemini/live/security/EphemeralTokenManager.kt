/*
 * Copyright 2024 Pose Coach Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.posecoach.gemini.live.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.posecoach.gemini.live.models.LiveApiError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * Secure ephemeral token management for Gemini Live API
 * Handles token acquisition, refresh, and secure storage
 * Never exposes API keys in client code
 */
class EphemeralTokenManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : CoroutineScope {

    companion object {
        private const val PREFS_NAME = "gemini_live_tokens"
        private const val TOKEN_KEY = "ephemeral_token"
        private const val TOKEN_EXPIRY_KEY = "token_expiry"
        private const val TOKEN_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/cachedContents"
        private const val TOKEN_VALIDITY_MS = 30 * 60 * 1000L // 30 minutes
        const val REFRESH_THRESHOLD_MS = 5 * 60 * 1000L // Refresh 5 mins before expiry
        const val NEW_SESSION_WINDOW_MS = 60 * 1000L // 1 minute grace period
        private const val MAX_REFRESH_ATTEMPTS = 3
        private const val REFRESH_RETRY_DELAY_MS = 2000L
    }

    override val coroutineContext: CoroutineContext = scope.coroutineContext

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val currentToken = AtomicReference<EphemeralToken?>(null)
    private val refreshJob = AtomicReference<Job?>(null)

    // Encrypted shared preferences for secure token storage
    private val encryptedPrefs by lazy {
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

    // Token state flow
    private val _tokenState = MutableStateFlow(TokenState.NONE)
    val tokenState: StateFlow<TokenState> = _tokenState.asStateFlow()

    // Error flow
    private val _tokenErrors = MutableSharedFlow<LiveApiError.AuthenticationError>()
    val tokenErrors: SharedFlow<LiveApiError.AuthenticationError> = _tokenErrors.asSharedFlow()

    init {
        loadStoredToken()
        startTokenRefreshMonitoring()
    }

    /**
     * Get a valid ephemeral token, refreshing if necessary
     */
    suspend fun getValidToken(): String? = withContext(Dispatchers.IO) {
        val token = currentToken.get()

        when {
            token == null -> {
                Timber.d("No token available, acquiring new one")
                acquireNewToken()?.accessToken
            }

            token.isExpired() -> {
                Timber.d("Token expired, acquiring new one")
                acquireNewToken()?.accessToken
            }

            token.shouldRefresh() -> {
                Timber.d("Token needs refresh")
                refreshToken()?.accessToken ?: token.accessToken
            }

            else -> {
                Timber.v("Using existing valid token")
                token.accessToken
            }
        }
    }

    /**
     * Force refresh the current token
     */
    suspend fun refreshToken(): EphemeralToken? = withContext(Dispatchers.IO) {
        val existingToken = currentToken.get()

        if (existingToken?.canRefresh() == true) {
            acquireNewToken()
        } else {
            Timber.w("Cannot refresh token - acquiring new one")
            acquireNewToken()
        }
    }

    /**
     * Acquire a new ephemeral token from the backend
     */
    private suspend fun acquireNewToken(): EphemeralToken? = withContext(Dispatchers.IO) {
        repeat(MAX_REFRESH_ATTEMPTS) { attempt ->
            try {
                _tokenState.value = TokenState.ACQUIRING

                val response = requestTokenFromBackend()

                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        val tokenResponse = gson.fromJson(responseBody, TokenResponse::class.java)

                        val token = EphemeralToken(
                            accessToken = tokenResponse.accessToken,
                            expiresAt = System.currentTimeMillis() + TOKEN_VALIDITY_MS,
                            acquiredAt = System.currentTimeMillis()
                        )

                        currentToken.set(token)
                        storeToken(token)
                        _tokenState.value = TokenState.VALID

                        Timber.d("Successfully acquired new ephemeral token")
                        return@withContext token
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Timber.e("Token acquisition failed: ${response.code} - $errorBody")

                    _tokenErrors.emit(
                        LiveApiError.AuthenticationError(
                            "Token acquisition failed: ${response.code}"
                        )
                    )
                }

            } catch (e: IOException) {
                Timber.e(e, "Network error acquiring token (attempt ${attempt + 1})")

                if (attempt == MAX_REFRESH_ATTEMPTS - 1) {
                    _tokenErrors.emit(
                        LiveApiError.AuthenticationError("Token acquisition failed after $MAX_REFRESH_ATTEMPTS attempts")
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error acquiring token")
                _tokenErrors.emit(
                    LiveApiError.AuthenticationError("Unexpected error: ${e.message}")
                )
                return@withContext null
            }

            if (attempt < MAX_REFRESH_ATTEMPTS - 1) {
                delay(REFRESH_RETRY_DELAY_MS * (attempt + 1)) // Exponential backoff
            }
        }

        _tokenState.value = TokenState.ERROR
        null
    }

    /**
     * Make HTTP request to backend for ephemeral token
     * This should call your secure backend endpoint that has the API key
     */
    private suspend fun requestTokenFromBackend(): Response = withContext(Dispatchers.IO) {
        // In production, replace this with your actual backend endpoint
        // Your backend should:
        // 1. Validate the request (auth, rate limiting, etc.)
        // 2. Use the Gemini API key securely server-side
        // 3. Request ephemeral token from Google
        // 4. Return only the ephemeral token to client

        val requestBody = TokenRequest(
            model = "models/gemini-2.0-flash-exp",
            displayName = "Pose Coach Session",
            ttl = "${TOKEN_VALIDITY_MS / 1000}s"
        )

        val request = Request.Builder()
            .url("https://your-backend.com/api/gemini/ephemeral-token") // Replace with your backend
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", "PoseCoach/1.0")
            .addHeader("X-Client-Platform", "Android")
            .build()

        httpClient.newCall(request).execute()
    }

    private fun loadStoredToken() {
        try {
            val tokenJson = encryptedPrefs.getString(TOKEN_KEY, null)
            val expiryTime = encryptedPrefs.getLong(TOKEN_EXPIRY_KEY, 0L)

            if (tokenJson != null && expiryTime > System.currentTimeMillis()) {
                val token = gson.fromJson(tokenJson, EphemeralToken::class.java)
                currentToken.set(token)
                _tokenState.value = if (token.isExpired()) TokenState.EXPIRED else TokenState.VALID
                Timber.d("Loaded valid token from storage")
            } else {
                clearStoredToken()
                _tokenState.value = TokenState.NONE
                Timber.d("No valid stored token found")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading stored token")
            clearStoredToken()
            _tokenState.value = TokenState.ERROR
        }
    }

    private fun storeToken(token: EphemeralToken) {
        try {
            encryptedPrefs.edit()
                .putString(TOKEN_KEY, gson.toJson(token))
                .putLong(TOKEN_EXPIRY_KEY, token.expiresAt)
                .apply()
            Timber.v("Token stored securely")
        } catch (e: Exception) {
            Timber.e(e, "Failed to store token")
        }
    }

    private fun clearStoredToken() {
        try {
            encryptedPrefs.edit()
                .remove(TOKEN_KEY)
                .remove(TOKEN_EXPIRY_KEY)
                .apply()
            Timber.v("Stored token cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear stored token")
        }
    }

    private fun startTokenRefreshMonitoring() {
        scope.launch {
            while (true) {
                delay(60_000L) // Check every minute

                val token = currentToken.get()
                if (token?.shouldRefresh() == true && refreshJob.get() == null) {
                    val job = launch {
                        try {
                            refreshToken()
                        } finally {
                            refreshJob.set(null)
                        }
                    }
                    refreshJob.set(job)
                }
            }
        }
    }

    /**
     * Check if client can start a new session
     */
    fun canStartNewSession(): Boolean {
        val token = currentToken.get() ?: return false
        val timeSinceAcquired = System.currentTimeMillis() - token.acquiredAt
        return timeSinceAcquired <= NEW_SESSION_WINDOW_MS
    }

    /**
     * Get current token information
     */
    fun getTokenInfo(): TokenInfo? {
        val token = currentToken.get() ?: return null
        return TokenInfo(
            isValid = !token.isExpired(),
            expiresAt = token.expiresAt,
            timeToExpiry = token.expiresAt - System.currentTimeMillis(),
            canRefresh = token.canRefresh(),
            state = _tokenState.value
        )
    }

    /**
     * Clear current token and reset state
     */
    suspend fun clearToken() = withContext(Dispatchers.IO) {
        currentToken.set(null)
        clearStoredToken()
        _tokenState.value = TokenState.NONE
        refreshJob.get()?.cancel()
        refreshJob.set(null)
    }

    fun cleanup() {
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
    }
}

/**
 * Ephemeral token data class
 */
data class EphemeralToken(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_at") val expiresAt: Long,
    @SerializedName("acquired_at") val acquiredAt: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    fun shouldRefresh(): Boolean =
        System.currentTimeMillis() >= (expiresAt - EphemeralTokenManager.REFRESH_THRESHOLD_MS)

    fun canRefresh(): Boolean =
        System.currentTimeMillis() < (acquiredAt + EphemeralTokenManager.NEW_SESSION_WINDOW_MS)
}

/**
 * Token request for backend
 */
private data class TokenRequest(
    val model: String,
    val displayName: String,
    val ttl: String
)

/**
 * Token response from backend
 */
private data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: Long? = null
)

/**
 * Token state enum
 */
enum class TokenState {
    NONE,
    ACQUIRING,
    VALID,
    EXPIRED,
    ERROR
}

/**
 * Token information for monitoring
 */
data class TokenInfo(
    val isValid: Boolean,
    val expiresAt: Long,
    val timeToExpiry: Long,
    val canRefresh: Boolean,
    val state: TokenState
)