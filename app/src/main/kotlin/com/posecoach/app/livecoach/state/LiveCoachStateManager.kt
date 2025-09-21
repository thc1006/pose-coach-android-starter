package com.posecoach.app.livecoach.state

import com.posecoach.app.livecoach.models.ConnectionState
import com.posecoach.app.livecoach.models.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class LiveCoachStateManager {
    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val maxRetryAttempts = 5
    private val baseRetryDelayMs = 1000L
    private val maxRetryDelayMs = 30000L

    // Session timing and health tracking
    private var sessionStartTime: Long = 0
    private var lastSuccessfulConnection: Long = 0
    private var connectionAttempts: Int = 0
    private var totalDisconnections: Int = 0

    // Quality metrics
    private val stateTransitionHistory = mutableListOf<StateTransition>()
    private val maxHistorySize = 50

    data class StateTransition(
        val fromState: ConnectionState,
        val toState: ConnectionState,
        val timestamp: Long,
        val reason: String? = null
    )

    fun updateConnectionState(newState: ConnectionState, reason: String? = null) {
        val currentState = _sessionState.value
        val oldState = currentState.connectionState

        // Record state transition
        recordStateTransition(oldState, newState, reason)

        // Update session timing
        updateSessionTiming(newState)

        _sessionState.value = currentState.copy(
            connectionState = newState,
            lastStateChange = System.currentTimeMillis()
        )

        Timber.d("Connection state: $oldState -> $newState${reason?.let { " ($it)" } ?: ""}")

        // Log significant state changes
        when (newState) {
            ConnectionState.CONNECTED -> {
                lastSuccessfulConnection = System.currentTimeMillis()
                Timber.i("Connected successfully after $connectionAttempts attempts")
            }
            ConnectionState.ERROR -> {
                totalDisconnections++
                Timber.w("Connection error (total disconnections: $totalDisconnections)")
            }
            ConnectionState.DISCONNECTED -> {
                if (oldState == ConnectionState.CONNECTED) {
                    totalDisconnections++
                }
            }
            else -> { /* Other states logged in debug */ }
        }
    }

    fun setRecording(isRecording: Boolean) {
        val currentState = _sessionState.value
        _sessionState.value = currentState.copy(isRecording = isRecording)
        Timber.d("Recording state: $isRecording")
    }

    fun setSpeaking(isSpeaking: Boolean) {
        val currentState = _sessionState.value
        _sessionState.value = currentState.copy(isSpeaking = isSpeaking)
        Timber.d("Speaking state: $isSpeaking")
    }

    fun setError(error: String?) {
        val currentState = _sessionState.value
        _sessionState.value = currentState.copy(lastError = error)
        if (error != null) {
            Timber.e("Session error: $error")
        }
    }

    fun setSessionId(sessionId: String?) {
        val currentState = _sessionState.value
        _sessionState.value = currentState.copy(sessionId = sessionId)
        Timber.d("Session ID: $sessionId")
    }

    fun incrementRetryCount(): Boolean {
        val currentState = _sessionState.value
        val newRetryCount = currentState.retryCount + 1
        connectionAttempts++

        return if (newRetryCount <= maxRetryAttempts) {
            _sessionState.value = currentState.copy(
                retryCount = newRetryCount,
                lastRetryAttempt = System.currentTimeMillis()
            )
            Timber.d("Retry attempt: $newRetryCount/$maxRetryAttempts (total attempts: $connectionAttempts)")
            true
        } else {
            Timber.w("Max retry attempts reached: $maxRetryAttempts (total attempts: $connectionAttempts)")
            false
        }
    }

    fun resetRetryCount() {
        val currentState = _sessionState.value
        _sessionState.value = currentState.copy(
            retryCount = 0,
            lastRetryAttempt = null
        )
        Timber.d("Retry count reset (total attempts preserved: $connectionAttempts)")
    }

    fun reset() {
        // Preserve some metrics for analysis
        val oldState = _sessionState.value
        val sessionDuration = if (sessionStartTime > 0) {
            System.currentTimeMillis() - sessionStartTime
        } else 0

        Timber.i("Resetting state manager. Session stats: duration=${sessionDuration}ms, attempts=$connectionAttempts, disconnections=$totalDisconnections")

        _sessionState.value = SessionState()
        sessionStartTime = 0
        lastSuccessfulConnection = 0
        connectionAttempts = 0
        totalDisconnections = 0
        stateTransitionHistory.clear()

        Timber.d("State manager reset completed")
    }

    fun getRetryDelay(): Long {
        val retryCount = _sessionState.value.retryCount
        val exponentialDelay = baseRetryDelayMs * (1L shl (retryCount - 1))
        val jitter = (0..1000).random() // Add jitter to prevent thundering herd
        return (exponentialDelay + jitter).coerceAtMost(maxRetryDelayMs)
    }

    fun canRetry(): Boolean {
        return _sessionState.value.retryCount < maxRetryAttempts
    }

    fun isConnected(): Boolean {
        return _sessionState.value.connectionState == ConnectionState.CONNECTED
    }

    fun isConnecting(): Boolean {
        return _sessionState.value.connectionState in listOf(
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING
        )
    }

    fun getCurrentState(): SessionState = _sessionState.value

    private fun recordStateTransition(from: ConnectionState, to: ConnectionState, reason: String?) {
        val transition = StateTransition(
            fromState = from,
            toState = to,
            timestamp = System.currentTimeMillis(),
            reason = reason
        )

        stateTransitionHistory.add(transition)
        if (stateTransitionHistory.size > maxHistorySize) {
            stateTransitionHistory.removeAt(0)
        }
    }

    private fun updateSessionTiming(newState: ConnectionState) {
        when (newState) {
            ConnectionState.CONNECTING -> {
                if (sessionStartTime == 0L) {
                    sessionStartTime = System.currentTimeMillis()
                }
            }
            else -> { /* No special timing updates needed */ }
        }
    }

    // Enhanced state checking methods
    fun getSessionDuration(): Long {
        return if (sessionStartTime > 0) {
            System.currentTimeMillis() - sessionStartTime
        } else 0
    }

    fun getTimeSinceLastConnection(): Long {
        return if (lastSuccessfulConnection > 0) {
            System.currentTimeMillis() - lastSuccessfulConnection
        } else -1
    }

    fun getConnectionStability(): Double {
        if (connectionAttempts == 0) return 1.0
        val successRate = if (lastSuccessfulConnection > 0) 1.0 else 0.0
        val disconnectionPenalty = totalDisconnections * 0.1
        return (successRate - disconnectionPenalty).coerceIn(0.0, 1.0)
    }

    fun getStateTransitionHistory(): List<StateTransition> {
        return stateTransitionHistory.toList()
    }

    fun isStable(): Boolean {
        val stability = getConnectionStability()
        val recentTransitions = stateTransitionHistory.takeLast(5)
        val hasRecentErrors = recentTransitions.any {
            it.toState == ConnectionState.ERROR &&
            (System.currentTimeMillis() - it.timestamp) < 30000 // Last 30 seconds
        }
        return stability > 0.7 && !hasRecentErrors
    }

    fun shouldTriggerHealthCheck(): Boolean {
        val currentState = _sessionState.value
        val timeSinceLastChange = System.currentTimeMillis() - (currentState.lastStateChange ?: 0)

        return isConnected() &&
               timeSinceLastChange > 60000 && // 1 minute since last state change
               getConnectionStability() < 0.8
    }

    fun getAdvancedSessionInfo(): Map<String, Any> {
        val state = getCurrentState()

        return mapOf(
            "sessionDurationMs" to getSessionDuration(),
            "timeSinceLastConnectionMs" to getTimeSinceLastConnection(),
            "connectionAttempts" to connectionAttempts,
            "totalDisconnections" to totalDisconnections,
            "connectionStability" to getConnectionStability(),
            "isStable" to isStable(),
            "shouldHealthCheck" to shouldTriggerHealthCheck(),
            "retryCount" to state.retryCount,
            "maxRetryAttempts" to maxRetryAttempts,
            "currentRetryDelay" to getRetryDelay(),
            "recentTransitions" to stateTransitionHistory.takeLast(10).map {
                mapOf(
                    "from" to it.fromState.name,
                    "to" to it.toState.name,
                    "timestamp" to it.timestamp,
                    "reason" to it.reason
                )
            }
        )
    }

    // Battery optimization methods
    fun optimizeForBattery(enable: Boolean) {
        // This could adjust retry frequencies and health check intervals
        // For now, we'll just log the request
        Timber.d("Battery optimization ${if (enable) "enabled" else "disabled"}")
    }
}