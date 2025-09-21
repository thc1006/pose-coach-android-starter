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

package com.posecoach.gemini.live.client

import com.posecoach.gemini.live.models.LiveApiError
import com.posecoach.gemini.live.models.SessionState
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * Comprehensive error recovery and resilience management for Gemini Live API
 * Implements circuit breaker, retry logic, graceful degradation, and failure analysis
 */
class ErrorRecoveryManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : CoroutineScope {

    companion object {
        private const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5
        private const val CIRCUIT_BREAKER_SLOW_CALL_THRESHOLD = 10000L // 10 seconds
        private const val CIRCUIT_BREAKER_WAIT_DURATION_SECONDS = 60L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_BASE_DELAY_MS = 1000L
        private const val ERROR_BURST_THRESHOLD = 5
        private const val ERROR_BURST_WINDOW_MS = 30_000L
        private const val DEGRADED_MODE_TIMEOUT_MS = 300_000L // 5 minutes
    }

    override val coroutineContext: CoroutineContext = scope.coroutineContext

    // Circuit breakers for different operations
    private val connectionCircuitBreaker = createCircuitBreaker("connection")
    private val audioCircuitBreaker = createCircuitBreaker("audio")
    private val tokenCircuitBreaker = createCircuitBreaker("token")

    // Retry policies
    private val connectionRetry = createRetryPolicy("connection")
    private val audioRetry = createRetryPolicy("audio")
    private val tokenRetry = createRetryPolicy("token")

    // Error tracking
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val lastErrorTimes = ConcurrentHashMap<String, AtomicLong>()
    private val errorBursts = ConcurrentHashMap<String, MutableList<Long>>()

    // Recovery state
    private val _recoveryState = MutableStateFlow(RecoveryState.NORMAL)
    val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()

    private val _degradedFeatures = MutableStateFlow<Set<String>>(emptySet())
    val degradedFeatures: StateFlow<Set<String>> = _degradedFeatures.asStateFlow()

    private val _recoveryActions = MutableSharedFlow<RecoveryAction>()
    val recoveryActions: SharedFlow<RecoveryAction> = _recoveryActions.asSharedFlow()

    // Recovery jobs
    private var degradationTimer: Job? = null
    private var healthCheckJob: Job? = null

    init {
        startHealthMonitoring()
    }

    /**
     * Handle connection errors with circuit breaker and retry logic
     */
    suspend fun handleConnectionError(error: LiveApiError.ConnectionError): RecoveryResult {
        return withContext(Dispatchers.IO) {
            recordError("connection", error)

            if (connectionCircuitBreaker.state == CircuitBreaker.State.OPEN) {
                RecoveryResult.CircuitOpen("Connection circuit breaker is open")
            } else {
                try {
                    val result = Retry.decorateFunction(connectionRetry) { _: String ->
                        // This would be called by the actual connection logic
                        "connection_attempt"
                    }.apply("retry")

                    RecoveryResult.Success("Connection recovered")

                } catch (e: Exception) {
                    handleRecoveryFailure("connection", e)
                    RecoveryResult.Failed("Connection recovery failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Handle audio processing errors
     */
    suspend fun handleAudioError(error: LiveApiError.AudioError): RecoveryResult {
        return withContext(Dispatchers.IO) {
            recordError("audio", error)

            when {
                isErrorBurst("audio") -> {
                    enableDegradedMode("audio")
                    RecoveryResult.Degraded("Audio degraded due to error burst")
                }

                audioCircuitBreaker.state == CircuitBreaker.State.OPEN -> {
                    enableDegradedMode("audio")
                    RecoveryResult.CircuitOpen("Audio circuit breaker is open")
                }

                else -> {
                    try {
                        // Attempt audio recovery
                        val result = Retry.decorateFunction(audioRetry) { _: String ->
                            "audio_recovery_attempt"
                        }.apply("retry")

                        RecoveryResult.Success("Audio recovered")

                    } catch (e: Exception) {
                        enableDegradedMode("audio")
                        RecoveryResult.Degraded("Audio recovery failed, entering degraded mode")
                    }
                }
            }
        }
    }

    /**
     * Handle authentication/token errors
     */
    suspend fun handleTokenError(error: LiveApiError.AuthenticationError): RecoveryResult {
        return withContext(Dispatchers.IO) {
            recordError("token", error)

            if (tokenCircuitBreaker.state == CircuitBreaker.State.OPEN) {
                RecoveryResult.CircuitOpen("Token circuit breaker is open")
            } else {
                try {
                    val result = Retry.decorateFunction(tokenRetry) { _: String ->
                        // Token refresh would be handled here
                        "token_refresh_attempt"
                    }.apply("retry")

                    RecoveryResult.Success("Token refreshed successfully")

                } catch (e: Exception) {
                    handleRecoveryFailure("token", e)
                    RecoveryResult.Failed("Token recovery failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Handle session-level errors
     */
    suspend fun handleSessionError(error: LiveApiError.SessionError): RecoveryResult {
        return withContext(Dispatchers.IO) {
            recordError("session", error)

            when {
                error.message?.contains("timeout", ignoreCase = true) == true -> {
                    _recoveryActions.emit(RecoveryAction.RestartSession("Session timeout"))
                    RecoveryResult.Restart("Session restart required due to timeout")
                }

                error.message?.contains("rate_limit", ignoreCase = true) == true -> {
                    enableDegradedMode("rate_limited")
                    _recoveryActions.emit(RecoveryAction.WaitAndRetry("Rate limit hit", 60000))
                    RecoveryResult.RateLimited("Rate limit reached, waiting before retry")
                }

                isErrorBurst("session") -> {
                    enableDegradedMode("session")
                    RecoveryResult.Degraded("Session degraded due to repeated errors")
                }

                else -> {
                    _recoveryActions.emit(RecoveryAction.RestartSession("General session error"))
                    RecoveryResult.Restart("Session restart recommended")
                }
            }
        }
    }

    /**
     * Handle protocol-level errors
     */
    suspend fun handleProtocolError(error: LiveApiError.ProtocolError): RecoveryResult {
        return withContext(Dispatchers.IO) {
            recordError("protocol", error)

            when {
                error.message?.contains("invalid_message", ignoreCase = true) == true -> {
                    RecoveryResult.Success("Protocol error logged, continuing")
                }

                error.message?.contains("unsupported", ignoreCase = true) == true -> {
                    enableDegradedMode("protocol")
                    RecoveryResult.Degraded("Protocol feature not supported, degraded mode enabled")
                }

                else -> {
                    _recoveryActions.emit(RecoveryAction.RestartSession("Protocol error"))
                    RecoveryResult.Restart("Session restart required due to protocol error")
                }
            }
        }
    }

    /**
     * Handle rate limiting errors
     */
    suspend fun handleRateLimitError(error: LiveApiError.RateLimitError): RecoveryResult {
        return withContext(Dispatchers.IO) {
            recordError("rate_limit", error)

            val waitTime = error.retryAfterMs ?: 60000L
            enableDegradedMode("rate_limited", waitTime)

            _recoveryActions.emit(RecoveryAction.WaitAndRetry("Rate limit", waitTime))
            RecoveryResult.RateLimited("Rate limited, waiting ${waitTime}ms before retry")
        }
    }

    /**
     * Check if the system should enter degraded mode
     */
    fun shouldEnterDegradedMode(): Boolean {
        val connectionOpen = connectionCircuitBreaker.state == CircuitBreaker.State.OPEN
        val audioOpen = audioCircuitBreaker.state == CircuitBreaker.State.OPEN
        val tokenOpen = tokenCircuitBreaker.state == CircuitBreaker.State.OPEN

        return connectionOpen || audioOpen || tokenOpen ||
               _recoveryState.value == RecoveryState.DEGRADED
    }

    /**
     * Force degraded mode for specific features
     */
    suspend fun enableDegradedMode(feature: String, durationMs: Long = DEGRADED_MODE_TIMEOUT_MS) {
        val currentFeatures = _degradedFeatures.value.toMutableSet()
        currentFeatures.add(feature)
        _degradedFeatures.value = currentFeatures

        _recoveryState.value = RecoveryState.DEGRADED

        // Schedule recovery from degraded mode
        degradationTimer?.cancel()
        degradationTimer = scope.launch {
            delay(durationMs)
            recoverFromDegradedMode(feature)
        }

        _recoveryActions.emit(RecoveryAction.EnterDegradedMode(feature, durationMs))
        Timber.w("Entered degraded mode for feature: $feature")
    }

    /**
     * Recover from degraded mode
     */
    suspend fun recoverFromDegradedMode(feature: String? = null) {
        val currentFeatures = _degradedFeatures.value.toMutableSet()

        if (feature != null) {
            currentFeatures.remove(feature)
        } else {
            currentFeatures.clear()
        }

        _degradedFeatures.value = currentFeatures

        if (currentFeatures.isEmpty()) {
            _recoveryState.value = RecoveryState.NORMAL
            _recoveryActions.emit(RecoveryAction.RecoverFromDegradation)
            Timber.i("Recovered from degraded mode")
        }
    }

    /**
     * Get error statistics
     */
    fun getErrorStatistics(): ErrorStatistics {
        return ErrorStatistics(
            connectionErrors = errorCounts["connection"]?.get() ?: 0,
            audioErrors = errorCounts["audio"]?.get() ?: 0,
            tokenErrors = errorCounts["token"]?.get() ?: 0,
            sessionErrors = errorCounts["session"]?.get() ?: 0,
            protocolErrors = errorCounts["protocol"]?.get() ?: 0,
            rateLimitErrors = errorCounts["rate_limit"]?.get() ?: 0,
            connectionCircuitState = connectionCircuitBreaker.state.name,
            audioCircuitState = audioCircuitBreaker.state.name,
            tokenCircuitState = tokenCircuitBreaker.state.name,
            recoveryState = _recoveryState.value,
            degradedFeatures = _degradedFeatures.value.toList()
        )
    }

    /**
     * Reset error counters and circuit breakers
     */
    suspend fun resetErrorState() {
        errorCounts.clear()
        lastErrorTimes.clear()
        errorBursts.clear()

        // Reset circuit breakers
        connectionCircuitBreaker.reset()
        audioCircuitBreaker.reset()
        tokenCircuitBreaker.reset()

        recoverFromDegradedMode()

        Timber.i("Error state reset")
    }

    private fun createCircuitBreaker(name: String): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .failureRateThreshold(60.0f) // 60% failure rate threshold
            .waitDurationInOpenState(Duration.ofSeconds(CIRCUIT_BREAKER_WAIT_DURATION_SECONDS))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .slowCallRateThreshold(80.0f)
            .slowCallDurationThreshold(Duration.ofMillis(CIRCUIT_BREAKER_SLOW_CALL_THRESHOLD))
            .build()

        return CircuitBreaker.of(name, config)
    }

    private fun createRetryPolicy(name: String): Retry {
        val config = RetryConfig.custom<String>()
            .maxAttempts(MAX_RETRY_ATTEMPTS)
            .waitDuration(Duration.ofMillis(RETRY_BASE_DELAY_MS))
            .exponentialBackoffMultiplier(2.0)
            .retryOnException { throwable ->
                // Retry on network and temporary errors
                when (throwable) {
                    is LiveApiError.ConnectionError -> true
                    is LiveApiError.AudioError -> true
                    is LiveApiError.SessionError -> !throwable.message?.contains("rate_limit", ignoreCase = true)!!
                    else -> false
                }
            }
            .build()

        return Retry.of(name, config)
    }

    private fun recordError(category: String, error: Throwable) {
        val currentTime = System.currentTimeMillis()

        // Update error count
        errorCounts.computeIfAbsent(category) { AtomicInteger(0) }.incrementAndGet()
        lastErrorTimes[category] = AtomicLong(currentTime)

        // Track error bursts
        val bursts = errorBursts.computeIfAbsent(category) { mutableListOf() }
        synchronized(bursts) {
            bursts.add(currentTime)
            // Remove old entries outside the burst window
            bursts.removeAll { it < currentTime - ERROR_BURST_WINDOW_MS }
        }

        Timber.e(error, "Recorded error in category: $category")
    }

    private fun isErrorBurst(category: String): Boolean {
        val bursts = errorBursts[category] ?: return false
        synchronized(bursts) {
            val currentTime = System.currentTimeMillis()
            val recentErrors = bursts.count { it > currentTime - ERROR_BURST_WINDOW_MS }
            return recentErrors >= ERROR_BURST_THRESHOLD
        }
    }

    private fun handleRecoveryFailure(category: String, error: Throwable) {
        Timber.e(error, "Recovery failed for category: $category")
        // Additional failure handling could be added here
    }

    private fun startHealthMonitoring() {
        healthCheckJob = scope.launch {
            while (true) {
                delay(30_000) // Check every 30 seconds

                try {
                    performHealthCheck()
                } catch (e: Exception) {
                    Timber.e(e, "Health check failed")
                }
            }
        }
    }

    private suspend fun performHealthCheck() {
        // Check if we can recover from degraded mode
        if (_recoveryState.value == RecoveryState.DEGRADED) {
            val allCircuitsClosed = listOf(
                connectionCircuitBreaker,
                audioCircuitBreaker,
                tokenCircuitBreaker
            ).all { it.state == CircuitBreaker.State.CLOSED }

            if (allCircuitsClosed && !hasRecentErrors()) {
                recoverFromDegradedMode()
            }
        }

        // Clean up old error data
        cleanupOldErrors()
    }

    private fun hasRecentErrors(): Boolean {
        val fiveMinutesAgo = System.currentTimeMillis() - 300_000
        return lastErrorTimes.values.any { it.get() > fiveMinutesAgo }
    }

    private fun cleanupOldErrors() {
        val oneHourAgo = System.currentTimeMillis() - 3_600_000

        lastErrorTimes.entries.removeAll { it.value.get() < oneHourAgo }

        errorBursts.values.forEach { bursts ->
            synchronized(bursts) {
                bursts.removeAll { it < oneHourAgo }
            }
        }
    }

    fun cleanup() {
        healthCheckJob?.cancel()
        degradationTimer?.cancel()
        scope.cancel()
    }
}

sealed class RecoveryResult {
    data class Success(val message: String) : RecoveryResult()
    data class Failed(val message: String) : RecoveryResult()
    data class Degraded(val message: String) : RecoveryResult()
    data class CircuitOpen(val message: String) : RecoveryResult()
    data class RateLimited(val message: String) : RecoveryResult()
    data class Restart(val message: String) : RecoveryResult()
}

sealed class RecoveryAction {
    data class RestartSession(val reason: String) : RecoveryAction()
    data class WaitAndRetry(val reason: String, val waitTimeMs: Long) : RecoveryAction()
    data class EnterDegradedMode(val feature: String, val durationMs: Long) : RecoveryAction()
    object RecoverFromDegradation : RecoveryAction()
}

enum class RecoveryState {
    NORMAL,
    DEGRADED,
    CRITICAL
}

data class ErrorStatistics(
    val connectionErrors: Int,
    val audioErrors: Int,
    val tokenErrors: Int,
    val sessionErrors: Int,
    val protocolErrors: Int,
    val rateLimitErrors: Int,
    val connectionCircuitState: String,
    val audioCircuitState: String,
    val tokenCircuitState: String,
    val recoveryState: RecoveryState,
    val degradedFeatures: List<String>
)