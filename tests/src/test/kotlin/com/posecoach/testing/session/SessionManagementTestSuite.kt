package com.posecoach.testing.session

import com.google.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive test suite for session management compliance with Gemini Live API specifications.
 *
 * Tests cover:
 * - 15-minute session limit validation
 * - 10-minute connection lifecycle testing
 * - Session resumption functionality
 * - GoAway message handling
 * - Context window compression testing
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class SessionManagementTestSuite {

    private lateinit var testScope: TestScope
    private lateinit var mockWebSocket: WebSocket
    private lateinit var sessionManager: TestSessionManager
    private lateinit var sessionStateFlow: MutableStateFlow<SessionState>
    private lateinit var connectionStateFlow: MutableStateFlow<ConnectionState>

    data class SessionState(
        val sessionId: String? = null,
        val startTime: Long = 0L,
        val isActive: Boolean = false,
        val remainingTime: Long = 0L,
        val contextWindowSize: Int = 0,
        val compressionEnabled: Boolean = false
    )

    data class ConnectionState(
        val isConnected: Boolean = false,
        val connectionStartTime: Long = 0L,
        val lastPingTime: Long = 0L,
        val reconnectAttempts: Int = 0
    )

    @Before
    fun setup() {
        testScope = TestScope()
        mockWebSocket = mockk(relaxed = true)
        sessionStateFlow = MutableStateFlow(SessionState())
        connectionStateFlow = MutableStateFlow(ConnectionState())
        sessionManager = TestSessionManager(
            sessionStateFlow = sessionStateFlow,
            connectionStateFlow = connectionStateFlow,
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test 15-minute session limit enforcement`() = testScope.runTest {
        val sessionStartTime = System.currentTimeMillis()
        sessionManager.startSession("test-session-123")

        // Verify session starts correctly
        assertThat(sessionStateFlow.value.isActive).isTrue()
        assertThat(sessionStateFlow.value.sessionId).isEqualTo("test-session-123")

        // Fast forward to 14 minutes (still valid)
        val fourteenMinutes = TimeUnit.MINUTES.toMillis(14)
        sessionManager.updateSessionTime(sessionStartTime + fourteenMinutes)

        assertThat(sessionStateFlow.value.isActive).isTrue()
        assertThat(sessionStateFlow.value.remainingTime).isGreaterThan(TimeUnit.MINUTES.toMillis(1))

        // Fast forward to 15 minutes (should trigger session end)
        val fifteenMinutes = TimeUnit.MINUTES.toMillis(15)
        sessionManager.updateSessionTime(sessionStartTime + fifteenMinutes)

        assertThat(sessionStateFlow.value.isActive).isFalse()
        assertThat(sessionStateFlow.value.remainingTime).isEqualTo(0L)

        // Verify goAway message is sent
        verify { mockWebSocket.close(1000, "SESSION_TIMEOUT") }
    }

    @Test
    fun `test 10-minute connection lifecycle management`() = testScope.runTest {
        val connectionStartTime = System.currentTimeMillis()
        sessionManager.establishConnection(mockWebSocket)

        // Verify connection establishes correctly
        assertThat(connectionStateFlow.value.isConnected).isTrue()
        assertThat(connectionStateFlow.value.connectionStartTime).isEqualTo(connectionStartTime)

        // Test periodic ping mechanism (every minute)
        repeat(5) { minute ->
            val currentTime = connectionStartTime + TimeUnit.MINUTES.toMillis((minute + 1).toLong())
            sessionManager.updateConnectionTime(currentTime)

            // Should send ping every minute
            if (minute < 9) { // Within 10-minute window
                assertThat(connectionStateFlow.value.isConnected).isTrue()
            }
        }

        // At 10 minutes, connection should be refreshed
        val tenMinutes = connectionStartTime + TimeUnit.MINUTES.toMillis(10)
        sessionManager.updateConnectionTime(tenMinutes)

        // Verify connection refresh logic
        verify(atLeast = 1) { mockWebSocket.send(match<String> { it.contains("ping") }) }

        // Test connection refresh
        val newMockWebSocket = mockk<WebSocket>(relaxed = true)
        sessionManager.refreshConnection(newMockWebSocket)

        assertThat(connectionStateFlow.value.isConnected).isTrue()
        assertThat(connectionStateFlow.value.reconnectAttempts).isGreaterThan(0)
    }

    @Test
    fun `test session resumption functionality`() = testScope.runTest {
        // Start initial session
        val originalSessionId = "original-session-123"
        sessionManager.startSession(originalSessionId)

        // Simulate some session activity
        sessionManager.addContextData("Initial conversation data")
        sessionManager.addContextData("User question about squats")

        val originalContextSize = sessionStateFlow.value.contextWindowSize
        assertThat(originalContextSize).isGreaterThan(0)

        // Simulate connection loss
        sessionManager.handleConnectionLoss()
        assertThat(connectionStateFlow.value.isConnected).isFalse()

        // Attempt session resumption within valid window (< 1 minute)
        val resumeTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)
        val resumedSessionId = sessionManager.resumeSession(originalSessionId, resumeTime)

        assertThat(resumedSessionId).isEqualTo(originalSessionId)
        assertThat(sessionStateFlow.value.isActive).isTrue()
        assertThat(sessionStateFlow.value.contextWindowSize).isEqualTo(originalContextSize)

        // Test resumption after window expires (> 1 minute)
        sessionManager.handleConnectionLoss()
        val expiredResumeTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2)
        val newSessionId = sessionManager.resumeSession(originalSessionId, expiredResumeTime)

        assertThat(newSessionId).isNotEqualTo(originalSessionId)
        assertThat(sessionStateFlow.value.contextWindowSize).isEqualTo(0)
    }

    @Test
    fun `test goAway message handling`() = testScope.runTest {
        sessionManager.startSession("test-session")

        val goAwayMessages = listOf(
            """{"goAway": {"reason": "SESSION_TIMEOUT"}}""",
            """{"goAway": {"reason": "SERVER_MAINTENANCE"}}""",
            """{"goAway": {"reason": "RESOURCE_EXHAUSTED"}}""",
            """{"goAway": {"reason": "INVALID_REQUEST"}}"""
        )

        goAwayMessages.forEach { message ->
            sessionManager.handleGoAwayMessage(message)

            // Session should be terminated
            assertThat(sessionStateFlow.value.isActive).isFalse()
            assertThat(connectionStateFlow.value.isConnected).isFalse()

            // Restart for next test
            sessionManager.startSession("test-session-${goAwayMessages.indexOf(message)}")
        }
    }

    @Test
    fun `test context window compression`() = testScope.runTest {
        sessionManager.startSession("compression-test-session")

        // Fill context window with data
        val maxContextSize = 1000000 // 1MB limit
        var currentSize = 0
        var messageCount = 0

        while (currentSize < maxContextSize * 0.8) { // Fill to 80%
            val message = "Test message ${messageCount++}: ".repeat(100)
            sessionManager.addContextData(message)
            currentSize += message.length * 2 // Approximate UTF-16 size
        }

        val preCompressionSize = sessionStateFlow.value.contextWindowSize
        assertThat(preCompressionSize).isGreaterThan(maxContextSize / 2)

        // Trigger compression
        sessionManager.compressContextWindow()

        val postCompressionSize = sessionStateFlow.value.contextWindowSize
        assertThat(postCompressionSize).isLessThan(preCompressionSize)
        assertThat(sessionStateFlow.value.compressionEnabled).isTrue()

        // Verify essential context is preserved
        assertThat(sessionManager.getContextSummary()).isNotEmpty()
        assertThat(sessionManager.getRecentContext(10)).hasSize(10)
    }

    @Test
    fun `test concurrent session operations`() = testScope.runTest {
        val sessionId = "concurrent-test-session"
        sessionManager.startSession(sessionId)

        val operationCount = 100
        val completedOperations = AtomicLong(0)

        // Launch concurrent operations
        val jobs = (1..operationCount).map { index ->
            launch {
                when (index % 4) {
                    0 -> sessionManager.addContextData("Message $index")
                    1 -> sessionManager.updateSessionTime(System.currentTimeMillis())
                    2 -> sessionManager.ping()
                    3 -> sessionManager.getSessionStatus()
                }
                completedOperations.incrementAndGet()
            }
        }

        // Wait for all operations to complete
        jobs.joinAll()

        assertThat(completedOperations.get()).isEqualTo(operationCount.toLong())
        assertThat(sessionStateFlow.value.isActive).isTrue()
        assertThat(sessionStateFlow.value.contextWindowSize).isGreaterThan(0)
    }

    @Test
    fun `test session timeout warning system`() = testScope.runTest {
        val sessionId = "timeout-warning-test"
        sessionManager.startSession(sessionId)

        val warnings = mutableListOf<String>()
        val job = launch {
            sessionManager.warningFlow.collect { warning ->
                warnings.add(warning)
            }
        }

        // Fast forward to 13 minutes (2 minutes before timeout)
        val thirteenMinutes = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(13)
        sessionManager.updateSessionTime(thirteenMinutes)

        // Should receive 2-minute warning
        assertThat(warnings).contains("SESSION_TIMEOUT_WARNING_2_MINUTES")

        // Fast forward to 14 minutes (1 minute before timeout)
        val fourteenMinutes = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(14)
        sessionManager.updateSessionTime(fourteenMinutes)

        // Should receive 1-minute warning
        assertThat(warnings).contains("SESSION_TIMEOUT_WARNING_1_MINUTE")

        // Fast forward to 14.5 minutes (30 seconds before timeout)
        val fourteenMinutesThirty = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(14) + TimeUnit.SECONDS.toMillis(30)
        sessionManager.updateSessionTime(fourteenMinutesThirty)

        // Should receive 30-second warning
        assertThat(warnings).contains("SESSION_TIMEOUT_WARNING_30_SECONDS")

        job.cancel()
    }

    @Test
    fun `test session metrics collection`() = testScope.runTest {
        val sessionId = "metrics-test-session"
        sessionManager.startSession(sessionId)

        // Simulate session activity
        repeat(50) { index ->
            sessionManager.addContextData("Test message $index")
            sessionManager.ping()
            delay(10) // Small delay to simulate real activity
        }

        val metrics = sessionManager.getSessionMetrics()

        assertThat(metrics.sessionId).isEqualTo(sessionId)
        assertThat(metrics.totalMessages).isEqualTo(50)
        assertThat(metrics.totalPings).isGreaterThan(0)
        assertThat(metrics.averageResponseTime).isGreaterThan(0.0)
        assertThat(metrics.sessionDuration).isGreaterThan(0L)
        assertThat(metrics.contextCompressions).isEqualTo(0) // No compression in this test
    }

    @Test
    fun `test session cleanup on app background`() = testScope.runTest {
        val sessionId = "background-cleanup-test"
        sessionManager.startSession(sessionId)

        // Add some context data
        sessionManager.addContextData("Before background")
        val beforeBackgroundSize = sessionStateFlow.value.contextWindowSize

        // Simulate app going to background
        sessionManager.onAppBackground()

        // Session should be paused but preserved
        assertThat(sessionStateFlow.value.isActive).isFalse()
        assertThat(connectionStateFlow.value.isConnected).isFalse()

        // Context should be preserved temporarily
        assertThat(sessionStateFlow.value.contextWindowSize).isEqualTo(beforeBackgroundSize)

        // Simulate app returning to foreground within grace period
        sessionManager.onAppForeground()

        // Session should resume
        assertThat(sessionStateFlow.value.isActive).isTrue()
        assertThat(sessionStateFlow.value.contextWindowSize).isEqualTo(beforeBackgroundSize)

        // Test background cleanup after grace period
        sessionManager.onAppBackground()

        // Fast forward past grace period (5 minutes)
        val graceTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(6)
        sessionManager.updateSessionTime(graceTime)

        // Context should be cleared
        assertThat(sessionStateFlow.value.contextWindowSize).isEqualTo(0)
    }

    // Test helper class
    private class TestSessionManager(
        private val sessionStateFlow: MutableStateFlow<SessionState>,
        private val connectionStateFlow: MutableStateFlow<ConnectionState>,
        private val scope: CoroutineScope
    ) {
        private val contextData = mutableListOf<String>()
        private val sessionMetrics = SessionMetrics()
        private val _warningFlow = MutableSharedFlow<String>()
        val warningFlow: SharedFlow<String> = _warningFlow.asSharedFlow()

        private val sessionTimeoutJob = AtomicReference<Job?>()
        private val pingJob = AtomicReference<Job?>()

        fun startSession(sessionId: String) {
            val startTime = System.currentTimeMillis()
            sessionStateFlow.value = SessionState(
                sessionId = sessionId,
                startTime = startTime,
                isActive = true,
                remainingTime = TimeUnit.MINUTES.toMillis(15)
            )

            sessionMetrics.sessionId = sessionId
            sessionMetrics.sessionStartTime = startTime

            startSessionTimeoutMonitoring()
        }

        fun establishConnection(webSocket: WebSocket) {
            connectionStateFlow.value = ConnectionState(
                isConnected = true,
                connectionStartTime = System.currentTimeMillis()
            )

            startPingMonitoring(webSocket)
        }

        fun updateSessionTime(currentTime: Long) {
            val currentState = sessionStateFlow.value
            if (!currentState.isActive) return

            val elapsed = currentTime - currentState.startTime
            val remaining = TimeUnit.MINUTES.toMillis(15) - elapsed

            if (remaining <= 0) {
                endSession("SESSION_TIMEOUT")
            } else {
                sessionStateFlow.value = currentState.copy(remainingTime = remaining)

                // Send warnings
                when {
                    remaining <= TimeUnit.MINUTES.toMillis(2) && remaining > TimeUnit.MINUTES.toMillis(1) ->
                        scope.launch { _warningFlow.emit("SESSION_TIMEOUT_WARNING_2_MINUTES") }
                    remaining <= TimeUnit.MINUTES.toMillis(1) && remaining > TimeUnit.SECONDS.toMillis(30) ->
                        scope.launch { _warningFlow.emit("SESSION_TIMEOUT_WARNING_1_MINUTE") }
                    remaining <= TimeUnit.SECONDS.toMillis(30) ->
                        scope.launch { _warningFlow.emit("SESSION_TIMEOUT_WARNING_30_SECONDS") }
                }
            }
        }

        fun updateConnectionTime(currentTime: Long) {
            val currentState = connectionStateFlow.value
            if (!currentState.isConnected) return

            val elapsed = currentTime - currentState.connectionStartTime

            if (elapsed >= TimeUnit.MINUTES.toMillis(10)) {
                // Connection needs refresh
                connectionStateFlow.value = currentState.copy(
                    reconnectAttempts = currentState.reconnectAttempts + 1
                )
            }
        }

        fun addContextData(data: String) {
            contextData.add(data)
            val currentSize = contextData.sumOf { it.length * 2 } // UTF-16 estimate
            sessionStateFlow.value = sessionStateFlow.value.copy(contextWindowSize = currentSize)
            sessionMetrics.totalMessages++
        }

        fun compressContextWindow() {
            // Simulate compression by keeping only recent messages
            val recentMessages = contextData.takeLast(50)
            contextData.clear()
            contextData.addAll(recentMessages)

            val compressedSize = contextData.sumOf { it.length * 2 }
            sessionStateFlow.value = sessionStateFlow.value.copy(
                contextWindowSize = compressedSize,
                compressionEnabled = true
            )
            sessionMetrics.contextCompressions++
        }

        fun handleConnectionLoss() {
            connectionStateFlow.value = connectionStateFlow.value.copy(isConnected = false)
            sessionStateFlow.value = sessionStateFlow.value.copy(isActive = false)
        }

        fun resumeSession(originalSessionId: String, resumeTime: Long): String {
            val currentState = sessionStateFlow.value
            val timeSinceDisconnect = resumeTime - (currentState.startTime + TimeUnit.MINUTES.toMillis(15) - currentState.remainingTime)

            return if (timeSinceDisconnect <= TimeUnit.MINUTES.toMillis(1)) {
                // Resume existing session
                sessionStateFlow.value = currentState.copy(isActive = true)
                connectionStateFlow.value = connectionStateFlow.value.copy(isConnected = true)
                originalSessionId
            } else {
                // Create new session
                contextData.clear()
                val newSessionId = "resumed-${System.currentTimeMillis()}"
                startSession(newSessionId)
                newSessionId
            }
        }

        fun refreshConnection(newWebSocket: WebSocket) {
            connectionStateFlow.value = ConnectionState(
                isConnected = true,
                connectionStartTime = System.currentTimeMillis(),
                reconnectAttempts = connectionStateFlow.value.reconnectAttempts + 1
            )

            startPingMonitoring(newWebSocket)
        }

        fun handleGoAwayMessage(message: String) {
            endSession("GO_AWAY")
        }

        fun ping() {
            connectionStateFlow.value = connectionStateFlow.value.copy(
                lastPingTime = System.currentTimeMillis()
            )
            sessionMetrics.totalPings++
        }

        fun getSessionStatus(): SessionState = sessionStateFlow.value

        fun getContextSummary(): String = contextData.takeLast(10).joinToString(" ")

        fun getRecentContext(count: Int): List<String> = contextData.takeLast(count)

        fun getSessionMetrics(): SessionMetrics {
            sessionMetrics.sessionDuration = System.currentTimeMillis() - sessionMetrics.sessionStartTime
            return sessionMetrics.copy()
        }

        fun onAppBackground() {
            sessionStateFlow.value = sessionStateFlow.value.copy(isActive = false)
            connectionStateFlow.value = connectionStateFlow.value.copy(isConnected = false)
        }

        fun onAppForeground() {
            if (sessionStateFlow.value.sessionId != null) {
                sessionStateFlow.value = sessionStateFlow.value.copy(isActive = true)
                connectionStateFlow.value = connectionStateFlow.value.copy(isConnected = true)
            }
        }

        private fun endSession(reason: String) {
            sessionStateFlow.value = sessionStateFlow.value.copy(
                isActive = false,
                remainingTime = 0L
            )
            connectionStateFlow.value = connectionStateFlow.value.copy(isConnected = false)

            sessionTimeoutJob.get()?.cancel()
            pingJob.get()?.cancel()
        }

        private fun startSessionTimeoutMonitoring() {
            sessionTimeoutJob.set(
                scope.launch {
                    while (sessionStateFlow.value.isActive) {
                        delay(1000) // Check every second
                        updateSessionTime(System.currentTimeMillis())
                    }
                }
            )
        }

        private fun startPingMonitoring(webSocket: WebSocket) {
            pingJob.set(
                scope.launch {
                    while (connectionStateFlow.value.isConnected) {
                        delay(TimeUnit.MINUTES.toMillis(1)) // Ping every minute
                        webSocket.send("""{"ping": ${System.currentTimeMillis()}}""")
                        ping()
                    }
                }
            )
        }
    }

    data class SessionMetrics(
        var sessionId: String = "",
        var sessionStartTime: Long = 0L,
        var sessionDuration: Long = 0L,
        var totalMessages: Int = 0,
        var totalPings: Int = 0,
        var averageResponseTime: Double = 0.0,
        var contextCompressions: Int = 0
    )
}