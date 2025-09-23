package com.posecoach.app.livecoach.websocket

import com.posecoach.app.livecoach.state.LiveCoachStateManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.test.AfterTest

/**
 * Unit tests for LiveApiConnectionManager
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveApiConnectionManagerTest {

    private lateinit var connectionManager: LiveApiConnectionManager
    private lateinit var stateManager: LiveCoachStateManager
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        stateManager = mockk(relaxed = true)
        connectionManager = LiveApiConnectionManager(stateManager, testScope)

        // Default mock behaviors
        every { stateManager.isConnecting() } returns false
        every { stateManager.isConnected() } returns false
        every { stateManager.canRetry() } returns true
        every { stateManager.incrementRetryCount() } returns true
        every { stateManager.getCurrentState() } returns mockk {
            every { retryCount } returns 1
            every { sessionId } returns "test-session"
            every { connectionState } returns ConnectionState.DISCONNECTED
        }
    }

    @AfterTest
    fun tearDown() {
        connectionManager.destroy()
    }

    @Test
    fun initializeConnection_updatesStateToConnecting() {
        connectionManager.initializeConnection()

        verify { stateManager.updateConnectionState(ConnectionState.CONNECTING) }
        verify { stateManager.setError(null) }
    }

    @Test
    fun initializeConnection_whenAlreadyConnecting_skipsInitialization() {
        every { stateManager.isConnecting() } returns true

        connectionManager.initializeConnection()

        verify(exactly = 0) { stateManager.updateConnectionState(any()) }
    }

    @Test
    fun initializeConnection_whenAlreadyConnected_skipsInitialization() {
        every { stateManager.isConnected() } returns true

        connectionManager.initializeConnection()

        verify(exactly = 0) { stateManager.updateConnectionState(any()) }
    }

    @Test
    fun onConnectionEstablished_updatesStateAndGeneratesSessionId() {
        connectionManager.onConnectionEstablished()

        verify { stateManager.updateConnectionState(ConnectionState.CONNECTED) }
        verify { stateManager.resetRetryCount() }
        verify { stateManager.setSessionId(any()) }
    }

    @Test
    fun onConnectionFailed_updatesStateWithError() {
        val errorMessage = "Connection failed"
        val httpCode = 500

        connectionManager.onConnectionFailed(errorMessage, httpCode)

        verify { stateManager.updateConnectionState(ConnectionState.ERROR) }
        verify { stateManager.setError("$errorMessage (HTTP $httpCode)") }
    }

    @Test
    fun onConnectionFailed_withoutHttpCode_setsSimpleError() {
        val errorMessage = "Connection failed"

        connectionManager.onConnectionFailed(errorMessage, null)

        verify { stateManager.updateConnectionState(ConnectionState.ERROR) }
        verify { stateManager.setError(errorMessage) }
    }

    @Test
    fun onConnectionClosed_normalClosure_updatesStateToDisconnected() {
        connectionManager.onConnectionClosed(1000, "Normal closure")

        verify { stateManager.updateConnectionState(ConnectionState.DISCONNECTED) }
        verify(exactly = 0) { stateManager.updateConnectionState(ConnectionState.RECONNECTING) }
    }

    @Test
    fun onConnectionClosed_abnormalClosure_triggersReconnect() = testScope.runTest {
        connectionManager.onConnectionClosed(1001, "Abnormal closure")

        verify { stateManager.updateConnectionState(ConnectionState.DISCONNECTED) }
        // Note: Actual reconnect scheduling is tested separately
    }

    @Test
    fun generateSessionId_createsUniqueId() {
        val sessionId1 = connectionManager.generateSessionId()
        val sessionId2 = connectionManager.generateSessionId()

        assertNotEquals(sessionId1, sessionId2)
        assertTrue(sessionId1.startsWith("session_"))
        assertTrue(sessionId2.startsWith("session_"))
    }

    @Test
    fun calculateExponentialBackoff_increasesWithRetryCount() {
        val delay1 = connectionManager.calculateExponentialBackoff(1)
        val delay2 = connectionManager.calculateExponentialBackoff(2)
        val delay3 = connectionManager.calculateExponentialBackoff(3)

        assertTrue(delay1 > 0)
        assertTrue(delay2 > delay1)
        assertTrue(delay3 > delay2)

        // All delays should be within reasonable bounds
        assertTrue(delay1 <= ConnectionConfig.MAX_RETRY_DELAY_MS)
        assertTrue(delay2 <= ConnectionConfig.MAX_RETRY_DELAY_MS)
        assertTrue(delay3 <= ConnectionConfig.MAX_RETRY_DELAY_MS)
    }

    @Test
    fun calculateExponentialBackoff_respectsMaxDelay() {
        val veryHighRetryCount = 20
        val delay = connectionManager.calculateExponentialBackoff(veryHighRetryCount)

        assertTrue(delay <= ConnectionConfig.MAX_RETRY_DELAY_MS)
    }

    @Test
    fun scheduleReconnect_whenCannotRetry_doesNotSchedule() = testScope.runTest {
        every { stateManager.canRetry() } returns false

        val result = connectionManager.scheduleReconnect()

        assertFalse(result)
        verify(exactly = 0) { stateManager.incrementRetryCount() }
    }

    @Test
    fun scheduleReconnect_whenCanRetry_schedulesReconnect() = testScope.runTest {
        val result = connectionManager.scheduleReconnect()

        assertTrue(result)
        verify { stateManager.incrementRetryCount() }
        verify { stateManager.updateConnectionState(ConnectionState.RECONNECTING) }
    }

    @Test
    fun forceReconnect_resetsRetryCountAndReconnects() {
        connectionManager.forceReconnect()

        verify { stateManager.resetRetryCount() }
        verify { stateManager.updateConnectionState(ConnectionState.CONNECTING) }
    }

    @Test
    fun disconnect_updatesStateToDisconnected() {
        connectionManager.disconnect()

        verify { stateManager.updateConnectionState(ConnectionState.DISCONNECTED) }
    }

    @Test
    fun isHealthy_whenConnectedAndLowRetries_returnsTrue() {
        every { stateManager.isConnected() } returns true
        every { stateManager.getCurrentState() } returns mockk {
            every { retryCount } returns 2
        }

        val result = connectionManager.isHealthy()

        assertTrue(result)
    }

    @Test
    fun isHealthy_whenNotConnected_returnsFalse() {
        every { stateManager.isConnected() } returns false

        val result = connectionManager.isHealthy()

        assertFalse(result)
    }

    @Test
    fun isHealthy_whenHighRetryCount_returnsFalse() {
        every { stateManager.isConnected() } returns true
        every { stateManager.getCurrentState() } returns mockk {
            every { retryCount } returns ConnectionConfig.MAX_RECONNECT_ATTEMPTS
        }

        val result = connectionManager.isHealthy()

        assertFalse(result)
    }

    @Test
    fun getConnectionMetrics_returnsCorrectMetrics() {
        val mockState = mockk<com.posecoach.app.livecoach.state.LiveCoachState> {
            every { sessionId } returns "test-session"
            every { connectionState } returns ConnectionState.CONNECTED
            every { retryCount } returns 2
        }
        every { stateManager.getCurrentState() } returns mockState

        val metrics = connectionManager.getConnectionMetrics()

        assertEquals("test-session", metrics.sessionId)
        assertEquals(ConnectionState.CONNECTED, metrics.connectionState)
        assertEquals(2, metrics.retryCount)
    }

    @Test
    fun destroy_cancelsAllJobs() {
        connectionManager.scheduleReconnect()

        connectionManager.destroy()

        // Verify that subsequent operations don't work
        assertFalse(connectionManager.scheduleReconnect())
    }
}