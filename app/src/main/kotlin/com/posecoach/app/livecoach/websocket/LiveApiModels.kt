package com.posecoach.app.livecoach.websocket

import com.posecoach.app.livecoach.models.*

/**
 * Shared data models and enums for Live API WebSocket communication
 *
 * Contains all the data structures needed across the WebSocket components
 * to maintain type safety and consistent data flow.
 */

/**
 * Connection state enumeration for WebSocket lifecycle tracking
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * Configuration for Live API connection parameters
 */
data class LiveApiConfig(
    val model: String = "models/gemini-2.0-flash-exp",
    val generationConfig: Map<String, Any> = mapOf(
        "temperature" to 0.7,
        "maxOutputTokens" to 1000
    ),
    val systemInstruction: Map<String, Any> = mapOf(
        "parts" to listOf(
            mapOf("text" to "You are a helpful pose coaching assistant.")
        )
    ),
    val realtimeInputConfig: Map<String, Any> = mapOf(
        "enableAudio" to true,
        "enableVideo" to true
    )
)

/**
 * WebSocket connection state and session information
 */
data class WebSocketState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val sessionId: String? = null,
    val retryCount: Int = 0,
    val error: String? = null,
    val isRetrying: Boolean = false
)

/**
 * Session metrics for monitoring and debugging
 */
data class SessionMetrics(
    val sessionId: String,
    val connectionState: ConnectionState,
    val sessionDurationMs: Long,
    val messagesSent: Int,
    val messagesReceived: Int,
    val avgMessagesPerSecond: Long,
    val lastMessageAgoMs: Long,
    val retryCount: Int
)

/**
 * Connection configuration constants
 */
object ConnectionConfig {
    const val WEBSOCKET_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val PING_INTERVAL_MS = 20000L
    const val CONNECTION_TIMEOUT_MS = 30000L
    const val MAX_RECONNECT_ATTEMPTS = 5
    const val BASE_RETRY_DELAY_MS = 1000L
    const val MAX_RETRY_DELAY_MS = 30000L
    const val HEALTH_CHECK_INTERVAL_MS = 60000L
    const val MAX_IDLE_TIME_MS = 300000L // 5 minutes
    const val MESSAGE_RATE_LIMIT = 50 // messages per second
}

/**
 * Health check result for connection monitoring
 */
data class HealthCheckResult(
    val isHealthy: Boolean,
    val timeSinceLastMessage: Long,
    val retryCount: Int,
    val connectionState: ConnectionState,
    val reason: String? = null
)

/**
 * Rate limiting state for message throttling
 */
data class RateLimitState(
    val sessionStartTime: Long,
    val messagesSent: Int,
    val canSend: Boolean,
    val messagesPerSecond: Long
)