package com.posecoach.suggestions

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min

/**
 * Advanced rate limiting and request throttling for Gemini API
 * Implements token bucket algorithm with burst capacity and adaptive backoff
 */
class RateLimitManager(
    private val maxRequestsPerMinute: Int = 15, // Gemini free tier limit
    private val maxRequestsPerDay: Int = 1500,   // Daily quota
    private val burstCapacity: Int = 5,          // Allow short bursts
    private val baseDelayMs: Long = 4000L        // Base delay between requests (4s)
) {

    private val mutex = Mutex()
    private val requestTimes = ConcurrentLinkedQueue<Long>()
    private val dailyRequestTimes = ConcurrentLinkedQueue<Long>()

    // Adaptive backoff state
    private var consecutiveFailures = 0
    private var lastFailureTime = 0L

    companion object {
        private const val MINUTE_MS = 60 * 1000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_BACKOFF_MS = 60 * 1000L // Maximum 1 minute backoff
        private const val FAILURE_BACKOFF_MULTIPLIER = 2.0
    }

    /**
     * Check if a request can be made and potentially wait for rate limit
     * @param waitIfLimited If true, will wait until request can be made
     * @return true if request can proceed, false if rate limited and not waiting
     */
    suspend fun canMakeRequest(waitIfLimited: Boolean = true): Boolean {
        return mutex.withLock {
            val currentTime = System.currentTimeMillis()

            // Clean old request timestamps
            cleanOldRequests(currentTime)

            // Check daily quota first
            if (dailyRequestTimes.size >= maxRequestsPerDay) {
                Timber.w("Daily API quota exceeded: ${dailyRequestTimes.size}/$maxRequestsPerDay")
                return@withLock false
            }

            // Check minute-based rate limit
            val recentRequests = requestTimes.size
            val canProceed = recentRequests < maxRequestsPerMinute

            if (!canProceed && !waitIfLimited) {
                Timber.d("Rate limited: $recentRequests/$maxRequestsPerMinute requests in last minute")
                return@withLock false
            }

            // Calculate delay needed
            val delayNeeded = calculateDelay(currentTime, recentRequests)

            if (delayNeeded > 0) {
                Timber.d("Rate limiting: waiting ${delayNeeded}ms before request")
                delay(delayNeeded)
            }

            // Record the request
            recordRequest(currentTime + delayNeeded)

            true
        }
    }

    /**
     * Record a successful request
     */
    suspend fun recordSuccess() {
        mutex.withLock {
            consecutiveFailures = 0
            Timber.d("Request successful, reset failure counter")
        }
    }

    /**
     * Record a failed request and adjust backoff
     */
    suspend fun recordFailure(exception: Throwable) {
        mutex.withLock {
            consecutiveFailures++
            lastFailureTime = System.currentTimeMillis()

            val isRateLimitError = exception.message?.contains("rate limit", ignoreCase = true) == true ||
                                  exception.message?.contains("quota", ignoreCase = true) == true ||
                                  exception.message?.contains("429", ignoreCase = true) == true

            if (isRateLimitError) {
                consecutiveFailures += 2 // Penalize rate limit errors more
                Timber.w("Rate limit error detected, increasing backoff")
            }

            Timber.d("Request failed (attempt $consecutiveFailures): ${exception.message}")
        }
    }

    /**
     * Get current rate limit status
     */
    fun getRateLimitStatus(): RateLimitStatus {
        val currentTime = System.currentTimeMillis()
        cleanOldRequests(currentTime)

        val minuteRequests = requestTimes.size
        val dailyRequests = dailyRequestTimes.size

        return RateLimitStatus(
            requestsInLastMinute = minuteRequests,
            maxRequestsPerMinute = maxRequestsPerMinute,
            requestsToday = dailyRequests,
            maxRequestsPerDay = maxRequestsPerDay,
            consecutiveFailures = consecutiveFailures,
            isLimited = minuteRequests >= maxRequestsPerMinute || dailyRequests >= maxRequestsPerDay
        )
    }

    private fun calculateDelay(currentTime: Long, recentRequests: Int): Long {
        var delay = 0L

        // Base delay between requests
        if (recentRequests > 0) {
            delay = max(delay, baseDelayMs)
        }

        // Burst capacity management
        if (recentRequests >= burstCapacity) {
            val burstPenalty = (recentRequests - burstCapacity + 1) * 1000L
            delay = max(delay, burstPenalty)
        }

        // Adaptive backoff for failures
        if (consecutiveFailures > 0) {
            val timeSinceFailure = currentTime - lastFailureTime
            val backoffDelay = min(
                (baseDelayMs * Math.pow(FAILURE_BACKOFF_MULTIPLIER, consecutiveFailures.toDouble())).toLong(),
                MAX_BACKOFF_MS
            )

            // Only apply backoff if recent failure
            if (timeSinceFailure < backoffDelay) {
                delay = max(delay, backoffDelay - timeSinceFailure)
            }
        }

        // Smooth out requests to avoid bunching
        if (recentRequests > maxRequestsPerMinute / 2) {
            val smoothingDelay = MINUTE_MS / maxRequestsPerMinute
            delay = max(delay, smoothingDelay)
        }

        return delay
    }

    private fun recordRequest(timestamp: Long) {
        requestTimes.offer(timestamp)
        dailyRequestTimes.offer(timestamp)

        Timber.d("Request recorded at $timestamp")
    }

    private fun cleanOldRequests(currentTime: Long) {
        // Remove requests older than 1 minute
        while (requestTimes.peek()?.let { currentTime - it > MINUTE_MS } == true) {
            requestTimes.poll()
        }

        // Remove requests older than 1 day
        while (dailyRequestTimes.peek()?.let { currentTime - it > DAY_MS } == true) {
            dailyRequestTimes.poll()
        }
    }

    /**
     * Reset all rate limiting state (for testing or configuration changes)
     */
    suspend fun reset() {
        mutex.withLock {
            requestTimes.clear()
            dailyRequestTimes.clear()
            consecutiveFailures = 0
            lastFailureTime = 0L
            Timber.d("Rate limiter reset")
        }
    }
}

data class RateLimitStatus(
    val requestsInLastMinute: Int,
    val maxRequestsPerMinute: Int,
    val requestsToday: Int,
    val maxRequestsPerDay: Int,
    val consecutiveFailures: Int,
    val isLimited: Boolean
) {
    val minuteUtilization: Float = requestsInLastMinute.toFloat() / maxRequestsPerMinute
    val dailyUtilization: Float = requestsToday.toFloat() / maxRequestsPerDay

    fun canMakeRequest(): Boolean = !isLimited

    fun getNextAvailableSlot(): String {
        return when {
            requestsToday >= maxRequestsPerDay -> "Tomorrow"
            requestsInLastMinute >= maxRequestsPerMinute -> "Next minute"
            else -> "Now"
        }
    }
}