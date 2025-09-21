package com.posecoach.testing.mocks.network

import com.posecoach.testing.mocks.MockService
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mock network manager for testing network operations
 * Simulates various network conditions including:
 * - Connection failures
 * - Timeouts
 * - Bandwidth limitations
 * - Intermittent connectivity
 */
class MockNetworkManager : MockService {

    private val interactionCount = AtomicInteger(0)
    private val _networkState = MutableStateFlow(NetworkState.CONNECTED)

    // Test configuration
    var shouldSimulateConnectionFailure = false
    var shouldSimulateTimeout = false
    var shouldSimulateSlowConnection = false
    var shouldSimulateIntermittentConnectivity = false
    var networkLatencyMs = 0L
    var bandwidthLimitKbps = 0 // 0 means no limit

    val networkState: StateFlow<NetworkState> = _networkState

    suspend fun makeRequest(url: String, payload: ByteArray? = null): Result<String> {
        interactionCount.incrementAndGet()

        // Simulate network latency
        if (networkLatencyMs > 0) {
            kotlinx.coroutines.delay(networkLatencyMs)
        }

        // Simulate bandwidth limitations
        if (bandwidthLimitKbps > 0 && payload != null) {
            val transferTimeMs = (payload.size * 8) / bandwidthLimitKbps
            kotlinx.coroutines.delay(transferTimeMs)
        }

        // Simulate various network conditions
        when {
            shouldSimulateConnectionFailure -> {
                _networkState.value = NetworkState.DISCONNECTED
                return Result.failure(ConnectException("Mock connection failure"))
            }
            shouldSimulateTimeout -> {
                kotlinx.coroutines.delay(30000) // Simulate timeout
                return Result.failure(SocketTimeoutException("Mock timeout"))
            }
            shouldSimulateIntermittentConnectivity -> {
                if (Math.random() < 0.3) { // 30% chance of failure
                    _networkState.value = NetworkState.UNSTABLE
                    return Result.failure(ConnectException("Intermittent connectivity"))
                }
            }
            shouldSimulateSlowConnection -> {
                kotlinx.coroutines.delay(5000) // Very slow response
            }
        }

        _networkState.value = NetworkState.CONNECTED
        return Result.success(generateMockResponse(url))
    }

    suspend fun checkConnectivity(): NetworkState {
        interactionCount.incrementAndGet()

        if (networkLatencyMs > 0) {
            kotlinx.coroutines.delay(networkLatencyMs)
        }

        return when {
            shouldSimulateConnectionFailure -> {
                _networkState.value = NetworkState.DISCONNECTED
                NetworkState.DISCONNECTED
            }
            shouldSimulateIntermittentConnectivity -> {
                val state = if (Math.random() < 0.3) NetworkState.UNSTABLE else NetworkState.CONNECTED
                _networkState.value = state
                state
            }
            else -> {
                _networkState.value = NetworkState.CONNECTED
                NetworkState.CONNECTED
            }
        }
    }

    private fun generateMockResponse(url: String): String {
        return when {
            url.contains("gemini") || url.contains("ai") -> {
                """
                {
                    "candidates": [
                        {
                            "content": {
                                "parts": [
                                    {
                                        "text": "Mock AI response for testing"
                                    }
                                ]
                            }
                        }
                    ]
                }
                """.trimIndent()
            }
            url.contains("api") -> {
                """
                {
                    "status": "success",
                    "data": {
                        "message": "Mock API response",
                        "timestamp": ${System.currentTimeMillis()}
                    }
                }
                """.trimIndent()
            }
            else -> {
                """
                {
                    "status": "ok",
                    "mock": true
                }
                """.trimIndent()
            }
        }
    }

    // Test utility methods

    /**
     * Configure the mock to simulate specific test scenarios
     */
    fun configureTestScenario(scenario: TestScenario) {
        when (scenario) {
            TestScenario.CONNECTION_FAILURE -> {
                shouldSimulateConnectionFailure = true
            }
            TestScenario.TIMEOUT -> {
                shouldSimulateTimeout = true
            }
            TestScenario.SLOW_CONNECTION -> {
                shouldSimulateSlowConnection = true
                networkLatencyMs = 2000L
            }
            TestScenario.INTERMITTENT_CONNECTIVITY -> {
                shouldSimulateIntermittentConnectivity = true
            }
            TestScenario.LIMITED_BANDWIDTH -> {
                bandwidthLimitKbps = 56 // Dial-up speed for testing
            }
            TestScenario.HIGH_LATENCY -> {
                networkLatencyMs = 3000L
            }
            TestScenario.NORMAL -> {
                reset()
            }
        }
    }

    /**
     * Set network latency for performance testing
     */
    fun setNetworkLatency(latencyMs: Long) {
        networkLatencyMs = latencyMs
    }

    /**
     * Set bandwidth limit for testing under constrained conditions
     */
    fun setBandwidthLimit(kbps: Int) {
        bandwidthLimitKbps = kbps
    }

    // MockService implementation

    override fun reset() {
        shouldSimulateConnectionFailure = false
        shouldSimulateTimeout = false
        shouldSimulateSlowConnection = false
        shouldSimulateIntermittentConnectivity = false
        networkLatencyMs = 0L
        bandwidthLimitKbps = 0
        interactionCount.set(0)
        _networkState.value = NetworkState.CONNECTED
    }

    override fun getInteractionCount(): Int = interactionCount.get()

    enum class NetworkState {
        CONNECTED,
        DISCONNECTED,
        UNSTABLE
    }

    enum class TestScenario {
        NORMAL,
        CONNECTION_FAILURE,
        TIMEOUT,
        SLOW_CONNECTION,
        INTERMITTENT_CONNECTIVITY,
        LIMITED_BANDWIDTH,
        HIGH_LATENCY
    }
}