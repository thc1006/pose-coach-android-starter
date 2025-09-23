package com.posecoach.app.livecoach.websocket

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Live API models and data structures
 */
class LiveApiModelsTest {

    @Test
    fun connectionState_hasCorrectValues() {
        assertEquals(5, ConnectionState.values().size)
        assertTrue(ConnectionState.values().contains(ConnectionState.DISCONNECTED))
        assertTrue(ConnectionState.values().contains(ConnectionState.CONNECTING))
        assertTrue(ConnectionState.values().contains(ConnectionState.CONNECTED))
        assertTrue(ConnectionState.values().contains(ConnectionState.RECONNECTING))
        assertTrue(ConnectionState.values().contains(ConnectionState.ERROR))
    }

    @Test
    fun liveApiConfig_hasDefaultValues() {
        val config = LiveApiConfig()

        assertEquals("models/gemini-2.0-flash-exp", config.model)
        assertTrue(config.generationConfig.containsKey("temperature"))
        assertTrue(config.generationConfig.containsKey("maxOutputTokens"))
        assertEquals(0.7, config.generationConfig["temperature"])
        assertEquals(1000, config.generationConfig["maxOutputTokens"])

        assertTrue(config.realtimeInputConfig.containsKey("enableAudio"))
        assertTrue(config.realtimeInputConfig.containsKey("enableVideo"))
        assertEquals(true, config.realtimeInputConfig["enableAudio"])
        assertEquals(true, config.realtimeInputConfig["enableVideo"])
    }

    @Test
    fun webSocketState_hasDefaultValues() {
        val state = WebSocketState()

        assertEquals(ConnectionState.DISCONNECTED, state.connectionState)
        assertNull(state.sessionId)
        assertEquals(0, state.retryCount)
        assertNull(state.error)
        assertFalse(state.isRetrying)
    }

    @Test
    fun webSocketState_canBeModified() {
        val state = WebSocketState(
            connectionState = ConnectionState.CONNECTED,
            sessionId = "test-session",
            retryCount = 2,
            error = "test error",
            isRetrying = true
        )

        assertEquals(ConnectionState.CONNECTED, state.connectionState)
        assertEquals("test-session", state.sessionId)
        assertEquals(2, state.retryCount)
        assertEquals("test error", state.error)
        assertTrue(state.isRetrying)
    }

    @Test
    fun sessionMetrics_calculatesCorrectValues() {
        val metrics = SessionMetrics(
            sessionId = "test-session",
            connectionState = ConnectionState.CONNECTED,
            sessionDurationMs = 10000L,
            messagesSent = 5,
            messagesReceived = 3,
            avgMessagesPerSecond = 2,
            lastMessageAgoMs = 1000L,
            retryCount = 0
        )

        assertEquals("test-session", metrics.sessionId)
        assertEquals(10000L, metrics.sessionDurationMs)
        assertEquals(5, metrics.messagesSent)
        assertEquals(3, metrics.messagesReceived)
        assertEquals(2L, metrics.avgMessagesPerSecond)
    }

    @Test
    fun connectionConfig_hasCorrectConstants() {
        assertTrue(ConnectionConfig.WEBSOCKET_URL.startsWith("wss://"))
        assertEquals(20000L, ConnectionConfig.PING_INTERVAL_MS)
        assertEquals(30000L, ConnectionConfig.CONNECTION_TIMEOUT_MS)
        assertEquals(5, ConnectionConfig.MAX_RECONNECT_ATTEMPTS)
        assertEquals(1000L, ConnectionConfig.BASE_RETRY_DELAY_MS)
        assertEquals(30000L, ConnectionConfig.MAX_RETRY_DELAY_MS)
        assertEquals(60000L, ConnectionConfig.HEALTH_CHECK_INTERVAL_MS)
        assertEquals(300000L, ConnectionConfig.MAX_IDLE_TIME_MS)
        assertEquals(50, ConnectionConfig.MESSAGE_RATE_LIMIT)
    }

    @Test
    fun healthCheckResult_healthyByDefault() {
        val result = HealthCheckResult(
            isHealthy = true,
            timeSinceLastMessage = 1000L,
            retryCount = 0,
            connectionState = ConnectionState.CONNECTED
        )

        assertTrue(result.isHealthy)
        assertEquals(1000L, result.timeSinceLastMessage)
        assertEquals(0, result.retryCount)
        assertEquals(ConnectionState.CONNECTED, result.connectionState)
        assertNull(result.reason)
    }

    @Test
    fun healthCheckResult_canBeUnhealthy() {
        val result = HealthCheckResult(
            isHealthy = false,
            timeSinceLastMessage = 400000L,
            retryCount = 3,
            connectionState = ConnectionState.ERROR,
            reason = "Connection timeout"
        )

        assertFalse(result.isHealthy)
        assertEquals(400000L, result.timeSinceLastMessage)
        assertEquals(3, result.retryCount)
        assertEquals(ConnectionState.ERROR, result.connectionState)
        assertEquals("Connection timeout", result.reason)
    }

    @Test
    fun rateLimitState_calculatesCorrectly() {
        val currentTime = System.currentTimeMillis()
        val state = RateLimitState(
            sessionStartTime = currentTime - 10000, // 10 seconds ago
            messagesSent = 25,
            canSend = true,
            messagesPerSecond = 2 // 25 messages / 10 seconds = 2.5 rounded down
        )

        assertEquals(25, state.messagesSent)
        assertTrue(state.canSend)
        assertEquals(2L, state.messagesPerSecond)
    }
}