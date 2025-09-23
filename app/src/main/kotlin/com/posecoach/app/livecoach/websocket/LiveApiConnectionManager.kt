package com.posecoach.app.livecoach.websocket

import com.posecoach.app.livecoach.state.LiveCoachStateManager
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * Manages WebSocket connection lifecycle including:
 * - Connection establishment and teardown
 * - Reconnection logic with exponential backoff
 * - Session management and health monitoring
 * - Connection state transitions
 *
 * This component is responsible for the connection layer only,
 * delegating message handling to LiveApiMessageProcessor.
 */
class LiveApiConnectionManager(
    private val stateManager: LiveCoachStateManager,
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    private var reconnectJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var sessionStartTime: Long = 0

    /**
     * Initialize a new connection attempt
     * Updates state and prepares for connection
     */
    fun initializeConnection() {
        if (stateManager.isConnecting() || stateManager.isConnected()) {
            Timber.w("Already connecting or connected, skipping initialization")
            return
        }

        sessionStartTime = System.currentTimeMillis()
        stateManager.updateConnectionState(ConnectionState.CONNECTING)
        stateManager.setError(null)

        Timber.d("Connection initialization started")
    }

    /**
     * Handle successful connection establishment
     * Sets up session and updates state
     */
    fun onConnectionEstablished() {
        Timber.d("WebSocket connection established successfully")

        // Cancel connection timeout
        connectionTimeoutJob?.cancel()

        stateManager.updateConnectionState(ConnectionState.CONNECTED)
        stateManager.resetRetryCount()
        stateManager.setSessionId(generateSessionId())

        Timber.i("Live API session established: ${stateManager.getCurrentState().sessionId}")
    }

    /**
     * Handle connection failures
     * Updates state and prepares for potential reconnection
     */
    fun onConnectionFailed(errorMessage: String, httpCode: Int? = null) {
        Timber.e("WebSocket connection failed: $errorMessage (HTTP: $httpCode)")

        // Cancel ongoing jobs
        connectionTimeoutJob?.cancel()

        stateManager.updateConnectionState(ConnectionState.ERROR)

        val detailedError = if (httpCode != null) {
            "$errorMessage (HTTP $httpCode)"
        } else {
            errorMessage
        }
        stateManager.setError(detailedError)
    }

    /**
     * Handle connection closure
     * Determines if reconnection should be attempted
     */
    fun onConnectionClosed(code: Int, reason: String) {
        Timber.d("WebSocket closed: $code - $reason")
        stateManager.updateConnectionState(ConnectionState.DISCONNECTED)

        // Check if this was a normal closure or if we should reconnect
        if (code != 1000) { // Not normal closure
            launch {
                scheduleReconnect()
            }
        }
    }

    /**
     * Schedule reconnection with exponential backoff
     * @return true if reconnection was scheduled, false if max attempts reached
     */
    fun scheduleReconnect(): Boolean {
        if (!stateManager.canRetry()) {
            val errorMsg = "Max reconnection attempts (${ConnectionConfig.MAX_RECONNECT_ATTEMPTS}) reached"
            Timber.e(errorMsg)
            stateManager.updateConnectionState(ConnectionState.ERROR)
            return false
        }

        reconnectJob?.cancel()
        reconnectJob = launch {
            if (stateManager.incrementRetryCount()) {
                val retryCount = stateManager.getCurrentState().retryCount
                val delay = calculateExponentialBackoff(retryCount)

                Timber.d("Scheduling reconnect attempt $retryCount/${ConnectionConfig.MAX_RECONNECT_ATTEMPTS} in ${delay}ms")
                stateManager.updateConnectionState(ConnectionState.RECONNECTING)

                delay(delay)

                if (isActive && !stateManager.isConnected()) {
                    Timber.i("Attempting reconnection $retryCount/${ConnectionConfig.MAX_RECONNECT_ATTEMPTS}")
                    initializeConnection()
                }
            }
        }
        return true
    }

    /**
     * Force an immediate reconnection
     * Resets retry count and initiates new connection
     */
    fun forceReconnect() {
        Timber.d("Force reconnecting")
        disconnect()
        stateManager.resetRetryCount()
        initializeConnection()
    }

    /**
     * Cleanly disconnect the WebSocket
     */
    fun disconnect() {
        Timber.d("Disconnecting WebSocket")

        // Cancel all connection-related jobs
        connectionTimeoutJob?.cancel()
        reconnectJob?.cancel()

        stateManager.updateConnectionState(ConnectionState.DISCONNECTED)

        val finalMetrics = getConnectionMetrics()
        Timber.i("WebSocket disconnected. Final metrics: $finalMetrics")
    }

    /**
     * Start connection timeout monitoring
     * Cancels connection attempt if it takes too long
     */
    fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = launch {
            delay(ConnectionConfig.CONNECTION_TIMEOUT_MS)
            if (!stateManager.isConnected()) {
                Timber.w("Connection timeout after ${ConnectionConfig.CONNECTION_TIMEOUT_MS}ms")
                stateManager.updateConnectionState(ConnectionState.ERROR)
                stateManager.setError("Connection timeout")
            }
        }
    }

    /**
     * Generate a unique session ID
     */
    fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Calculate exponential backoff delay with jitter
     */
    fun calculateExponentialBackoff(retryCount: Int): Long {
        val baseDelay = ConnectionConfig.BASE_RETRY_DELAY_MS * (1L shl (retryCount - 1))
        val jitter = (0..1000).random() // Add jitter to avoid thundering herd
        return (baseDelay + jitter).coerceAtMost(ConnectionConfig.MAX_RETRY_DELAY_MS)
    }

    /**
     * Check if the connection is healthy
     */
    fun isHealthy(): Boolean {
        return stateManager.isConnected() &&
               stateManager.getCurrentState().retryCount < ConnectionConfig.MAX_RECONNECT_ATTEMPTS
    }

    /**
     * Get current connection metrics
     */
    fun getConnectionMetrics(): SessionMetrics {
        val now = System.currentTimeMillis()
        val sessionDuration = now - sessionStartTime
        val currentState = stateManager.getCurrentState()

        return SessionMetrics(
            sessionId = currentState.sessionId ?: "none",
            connectionState = currentState.connectionState,
            sessionDurationMs = sessionDuration,
            messagesSent = 0, // Will be provided by client
            messagesReceived = 0, // Will be provided by client
            avgMessagesPerSecond = 0, // Will be calculated by client
            lastMessageAgoMs = 0, // Will be provided by client
            retryCount = currentState.retryCount
        )
    }

    /**
     * Destroy the connection manager and clean up resources
     */
    fun destroy() {
        Timber.d("Destroying connection manager")

        // Cancel all jobs
        connectionTimeoutJob?.cancel()
        reconnectJob?.cancel()

        // Cancel coroutine scope
        cancel()

        Timber.d("Connection manager destroyed")
    }
}