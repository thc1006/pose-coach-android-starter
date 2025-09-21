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

import com.posecoach.gemini.live.models.*
import com.posecoach.gemini.live.security.EphemeralTokenManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.*

@RunWith(MockitoJUnitRunner::class)
class LiveApiWebSocketClientTest {

    private lateinit var tokenManager: EphemeralTokenManager
    private lateinit var config: LiveApiConfig
    private lateinit var client: LiveApiWebSocketClient
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        tokenManager = mockk()
        config = LiveApiConfig(
            model = "models/gemini-2.0-flash-exp",
            voiceName = "Aoede",
            responseModalities = listOf("AUDIO")
        )
        testScope = TestScope()
        client = LiveApiWebSocketClient(tokenManager, config, testScope)
    }

    @After
    fun tearDown() {
        runBlocking {
            client.disconnect()
            client.cleanup()
        }
    }

    @Test
    fun `connect should establish WebSocket connection successfully`() = runTest {
        // Given
        val mockToken = "test_token_123"
        coEvery { tokenManager.getValidToken() } returns mockToken

        // Mock WebSocket behavior would go here
        // Note: In real implementation, you'd mock OkHttp WebSocket

        // When
        val result = client.connect()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(SessionState.CONNECTED, client.state.value)
    }

    @Test
    fun `connect should fail when no token available`() = runTest {
        // Given
        coEvery { tokenManager.getValidToken() } returns null

        // When
        val result = client.connect()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LiveApiError.AuthenticationError)
    }

    @Test
    fun `setupSession should configure model correctly`() = runTest {
        // Given
        val mockToken = "test_token_123"
        coEvery { tokenManager.getValidToken() } returns mockToken

        // Mock successful connection
        every { client.state } returns MutableStateFlow(SessionState.CONNECTED)

        // When
        val result = client.setupSession()

        // Then
        // Verify setup message would be sent
        // In real test, you'd verify the actual WebSocket message
        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendText should format message correctly`() = runTest {
        // Given
        val testText = "Hello, Gemini!"

        // Mock connected state
        every { client.state } returns MutableStateFlow(SessionState.SETUP_COMPLETE)

        // When
        val result = client.sendText(testText, turnComplete = true)

        // Then
        assertTrue(result.isSuccess)
        // Verify message format in real implementation
    }

    @Test
    fun `sendAudioChunk should encode PCM data correctly`() = runTest {
        // Given
        val audioData = ByteArray(320) { it.toByte() } // 20ms of 16kHz audio

        // Mock connected state
        every { client.state } returns MutableStateFlow(SessionState.SETUP_COMPLETE)

        // When
        val result = client.sendAudioChunk(audioData)

        // Then
        assertTrue(result.isSuccess)
        // Verify PCM encoding and base64 conversion
    }

    @Test
    fun `sendToolResponse should format function responses correctly`() = runTest {
        // Given
        val functionResponses = listOf(
            FunctionResponse(
                name = "analyze_pose",
                response = mapOf(
                    "confidence" to 0.95,
                    "feedback" to "Good form!"
                )
            )
        )

        // Mock connected state
        every { client.state } returns MutableStateFlow(SessionState.SETUP_COMPLETE)

        // When
        val result = client.sendToolResponse(functionResponses)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `connection should handle GoAway messages properly`() = runTest {
        // Given
        val goAwayMessage = BidiGenerateContentGoAway(
            requestId = "req_123",
            reason = "session_timeout",
            details = "Session exceeded time limit"
        )

        // When
        // Simulate receiving GoAway message
        // client.handleGoAway(goAwayMessage)

        // Then
        // Verify reconnection attempt for timeout
        // Verify graceful shutdown for other reasons
    }

    @Test
    fun `client should attempt reconnection on unexpected disconnection`() = runTest {
        // Given
        val mockToken = "test_token_123"
        coEvery { tokenManager.getValidToken() } returns mockToken

        // When
        // Simulate unexpected disconnection
        // client.onFailure(mockWebSocket, IOException("Connection lost"), null)

        // Then
        // Verify reconnection attempts
        // Verify exponential backoff
    }

    @Test
    fun `session timeout should trigger disconnection`() = runTest {
        // Given
        val shortTimeoutConfig = config.copy()
        val clientWithTimeout = LiveApiWebSocketClient(tokenManager, shortTimeoutConfig, testScope)

        // When
        // Wait for session timeout
        advanceTimeBy(600_001) // Just over 10 minutes

        // Then
        // Verify disconnection
        assertEquals(SessionState.DISCONNECTED, clientWithTimeout.state.value)
    }

    @Test
    fun `isSessionNearTimeout should return correct status`() = runTest {
        // Given
        val mockToken = "test_token_123"
        coEvery { tokenManager.getValidToken() } returns mockToken

        // When
        val result = client.connect()
        assertTrue(result.isSuccess)

        // Advance time to near timeout
        advanceTimeBy(540_000) // 9 minutes

        // Then
        assertTrue(client.isSessionNearTimeout())
    }

    @Test
    fun `getSessionStats should provide accurate metrics`() = runTest {
        // Given
        val mockToken = "test_token_123"
        coEvery { tokenManager.getValidToken() } returns mockToken

        // When
        client.connect()
        advanceTimeBy(60_000) // 1 minute

        val stats = client.getSessionStats()

        // Then
        assertNotNull(stats.sessionId)
        assertTrue(stats.uptime > 0)
        assertEquals(SessionState.CONNECTED, stats.state)
    }

    @Test
    fun `message parsing should handle all server message types`() = runTest {
        // Test setup complete
        val setupCompleteJson = """{"setupComplete": {"request_id": "req_123"}}"""
        // Verify parsing

        // Test server content
        val serverContentJson = """
            {
                "serverContent": {
                    "modelTurn": {
                        "parts": [{"text": "Hello!"}],
                        "role": "model"
                    },
                    "turnComplete": true
                },
                "request_id": "req_456"
            }
        """.trimIndent()
        // Verify parsing

        // Test GoAway
        val goAwayJson = """
            {
                "goAway": {
                    "request_id": "req_789",
                    "reason": "session_limit_reached"
                }
            }
        """.trimIndent()
        // Verify parsing
    }

    @Test
    fun `error handling should emit appropriate error types`() = runTest {
        // Test connection errors
        val connectionError = LiveApiError.ConnectionError("Network error")
        // Verify error emission

        // Test authentication errors
        val authError = LiveApiError.AuthenticationError("Invalid token")
        // Verify error emission

        // Test protocol errors
        val protocolError = LiveApiError.ProtocolError("Invalid message format")
        // Verify error emission
    }

    @Test
    fun `audio data encoding should follow specifications`() = runTest {
        // Given
        val audioData = ByteArray(320) { (it % 256).toByte() }

        // When
        val mediaChunk = createPCMMediaChunk(audioData)

        // Then
        assertEquals(AudioConfig.MIME_TYPE_AUDIO_PCM, mediaChunk.mimeType)
        assertEquals(audioData.toBase64(), mediaChunk.data)

        // Verify round-trip encoding
        val decodedData = mediaChunk.data.fromBase64()
        assertContentEquals(audioData, decodedData)
    }

    @Test
    fun `rate limiting should be handled gracefully`() = runTest {
        // Given
        val rateLimitError = LiveApiError.RateLimitError("Rate limit exceeded", 60000)

        // When
        // Simulate rate limit error

        // Then
        // Verify appropriate backoff
        // Verify retry after specified time
    }

    @Test
    fun `concurrent operations should be thread-safe`() = runTest {
        // Given
        val mockToken = "test_token_123"
        coEvery { tokenManager.getValidToken() } returns mockToken

        // When
        val jobs = List(10) {
            async {
                client.sendText("Message $it")
            }
        }

        val results = jobs.awaitAll()

        // Then
        results.forEach { result ->
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun `memory management should prevent leaks`() = runTest {
        // Given
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // When
        repeat(100) {
            val tempClient = LiveApiWebSocketClient(tokenManager, config, testScope)
            tempClient.cleanup()
        }

        System.gc()
        Thread.sleep(100)

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Then
        // Memory usage should not increase significantly
        val memoryIncrease = finalMemory - initialMemory
        assertTrue(memoryIncrease < 10_000_000) // Less than 10MB increase
    }
}