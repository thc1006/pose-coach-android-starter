package com.posecoach.testing.websocket

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import okhttp3.*
import okio.ByteString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Comprehensive test suite for WebSocket protocol compliance with Gemini Live API specifications.
 *
 * Tests cover:
 * - BidiGenerateContentSetup message validation
 * - BidiGenerateContentClientContent testing
 * - BidiGenerateContentRealtimeInput audio streaming
 * - BidiGenerateContentToolResponse handling
 * - Server message handling (serverContent, toolCall, setupComplete, goAway)
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class WebSocketProtocolTestSuite {

    private lateinit var testScope: TestScope
    private lateinit var mockWebSocket: WebSocket
    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var webSocketListener: WebSocketListener
    private lateinit var gson: Gson

    // Test data flows
    private val messageFlow = MutableSharedFlow<String>()
    private val binaryFlow = MutableSharedFlow<ByteString>()
    private val connectionStateFlow = MutableSharedFlow<WebSocketState>()

    enum class WebSocketState {
        CONNECTING, CONNECTED, CLOSING, CLOSED, FAILED
    }

    @Before
    fun setup() {
        testScope = TestScope()
        mockWebSocket = mockk(relaxed = true)
        mockOkHttpClient = mockk(relaxed = true)
        gson = Gson()

        webSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                testScope.launch { connectionStateFlow.emit(WebSocketState.CONNECTED) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                testScope.launch { messageFlow.emit(text) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                testScope.launch { binaryFlow.emit(bytes) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                testScope.launch { connectionStateFlow.emit(WebSocketState.CLOSING) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                testScope.launch { connectionStateFlow.emit(WebSocketState.CLOSED) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                testScope.launch { connectionStateFlow.emit(WebSocketState.FAILED) }
            }
        }
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test BidiGenerateContentSetup message validation`() = testScope.runTest {
        // Test setup message structure compliance
        val setupMessage = createSetupMessage()
        val jsonMessage = gson.toJson(setupMessage)

        // Validate required fields
        val parsed = gson.fromJson(jsonMessage, JsonObject::class.java)
        assertThat(parsed.has("setup")).isTrue()
        assertThat(parsed.getAsJsonObject("setup").has("model")).isTrue()
        assertThat(parsed.getAsJsonObject("setup").has("generationConfig")).isTrue()

        // Validate generation config
        val generationConfig = parsed.getAsJsonObject("setup").getAsJsonObject("generationConfig")
        assertThat(generationConfig.has("responseModalities")).isTrue()
        assertThat(generationConfig.getAsJsonArray("responseModalities").size()).isGreaterThan(0)

        // Validate tools configuration
        if (parsed.getAsJsonObject("setup").has("tools")) {
            val tools = parsed.getAsJsonObject("setup").getAsJsonArray("tools")
            tools.forEach { tool ->
                val toolObj = tool.asJsonObject
                assertThat(toolObj.has("functionDeclarations")).isTrue()
                assertThat(toolObj.getAsJsonArray("functionDeclarations").size()).isGreaterThan(0)
            }
        }

        // Send setup message and verify WebSocket interaction
        every { mockWebSocket.send(any<String>()) } returns true
        val result = mockWebSocket.send(jsonMessage)
        assertThat(result).isTrue()
        verify { mockWebSocket.send(jsonMessage) }
    }

    @Test
    fun `test BidiGenerateContentClientContent message structure`() = testScope.runTest {
        val clientContent = createClientContentMessage(
            text = "Analyze this pose for form improvements",
            mediaData = createMockImageData()
        )

        val jsonMessage = gson.toJson(clientContent)
        val parsed = gson.fromJson(jsonMessage, JsonObject::class.java)

        // Validate message structure
        assertThat(parsed.has("clientContent")).isTrue()

        val content = parsed.getAsJsonObject("clientContent")
        assertThat(content.has("turns")).isTrue()

        val turn = content.getAsJsonArray("turns").get(0).asJsonObject
        assertThat(turn.has("parts")).isTrue()

        val parts = turn.getAsJsonArray("parts")
        assertThat(parts.size()).isEqualTo(2) // Text + Image

        // Validate text part
        val textPart = parts.get(0).asJsonObject
        assertThat(textPart.has("text")).isTrue()
        assertThat(textPart.get("text").asString).isEqualTo("Analyze this pose for form improvements")

        // Validate inline data part
        val dataPart = parts.get(1).asJsonObject
        assertThat(dataPart.has("inlineData")).isTrue()

        val inlineData = dataPart.getAsJsonObject("inlineData")
        assertThat(inlineData.has("mimeType")).isTrue()
        assertThat(inlineData.has("data")).isTrue()
        assertThat(inlineData.get("mimeType").asString).startsWith("image/")
    }

    @Test
    fun `test BidiGenerateContentRealtimeInput audio streaming`() = testScope.runTest {
        val audioData = createMockAudioData() // 16-bit PCM 16kHz
        val realtimeInput = createRealtimeInputMessage(audioData)

        val jsonMessage = gson.toJson(realtimeInput)
        val parsed = gson.fromJson(jsonMessage, JsonObject::class.java)

        // Validate realtime input structure
        assertThat(parsed.has("realtimeInput")).isTrue()

        val realtimeInputObj = parsed.getAsJsonObject("realtimeInput")
        assertThat(realtimeInputObj.has("mediaChunks")).isTrue()

        val mediaChunks = realtimeInputObj.getAsJsonArray("mediaChunks")
        assertThat(mediaChunks.size()).isGreaterThan(0)

        val chunk = mediaChunks.get(0).asJsonObject
        assertThat(chunk.has("mimeType")).isTrue()
        assertThat(chunk.has("data")).isTrue()
        assertThat(chunk.get("mimeType").asString).isEqualTo("audio/pcm")

        // Validate audio data format (Base64 encoded 16-bit PCM)
        val audioBase64 = chunk.get("data").asString
        assertThat(audioBase64).isNotEmpty()

        // Test binary audio streaming
        val binaryAudioData = ByteString.of(*audioData)
        every { mockWebSocket.send(any<ByteString>()) } returns true

        val binaryResult = mockWebSocket.send(binaryAudioData)
        assertThat(binaryResult).isTrue()
        verify { mockWebSocket.send(binaryAudioData) }
    }

    @Test
    fun `test BidiGenerateContentToolResponse handling`() = testScope.runTest {
        val toolResponse = createToolResponseMessage(
            functionName = "analyze_pose_form",
            functionResponse = mapOf(
                "feedback" to "Great squat form! Keep your back straight.",
                "score" to 85,
                "improvements" to listOf("Slightly deeper squat", "Engage core more")
            )
        )

        val jsonMessage = gson.toJson(toolResponse)
        val parsed = gson.fromJson(jsonMessage, JsonObject::class.java)

        // Validate tool response structure
        assertThat(parsed.has("toolResponse")).isTrue()

        val toolResponseObj = parsed.getAsJsonObject("toolResponse")
        assertThat(toolResponseObj.has("functionResponses")).isTrue()

        val functionResponses = toolResponseObj.getAsJsonArray("functionResponses")
        assertThat(functionResponses.size()).isEqualTo(1)

        val functionResponse = functionResponses.get(0).asJsonObject
        assertThat(functionResponse.has("name")).isTrue()
        assertThat(functionResponse.has("response")).isTrue()
        assertThat(functionResponse.get("name").asString).isEqualTo("analyze_pose_form")

        val response = functionResponse.getAsJsonObject("response")
        assertThat(response.has("feedback")).isTrue()
        assertThat(response.has("score")).isTrue()
        assertThat(response.has("improvements")).isTrue()
    }

    @Test
    fun `test server message handling - serverContent`() = testScope.runTest {
        val serverContentMessage = """
            {
                "serverContent": {
                    "modelTurn": {
                        "parts": [
                            {
                                "text": "I can see you're performing a squat. Let me analyze your form..."
                            }
                        ]
                    },
                    "turnComplete": false
                }
            }
        """.trimIndent()

        // Simulate receiving server content
        webSocketListener.onMessage(mockWebSocket, serverContentMessage)

        // Collect emitted messages
        val messages = mutableListOf<String>()
        val job = launch {
            messageFlow.toList(messages)
        }

        advanceUntilIdle()

        assertThat(messages).hasSize(1)
        assertThat(messages[0]).isEqualTo(serverContentMessage)

        // Validate message parsing
        val parsed = gson.fromJson(serverContentMessage, JsonObject::class.java)
        assertThat(parsed.has("serverContent")).isTrue()

        val serverContent = parsed.getAsJsonObject("serverContent")
        assertThat(serverContent.has("modelTurn")).isTrue()
        assertThat(serverContent.has("turnComplete")).isTrue()

        job.cancel()
    }

    @Test
    fun `test server message handling - toolCall`() = testScope.runTest {
        val toolCallMessage = """
            {
                "toolCall": {
                    "functionCalls": [
                        {
                            "name": "analyze_pose_form",
                            "id": "call_123",
                            "args": {
                                "pose_data": "base64_encoded_pose_landmarks",
                                "exercise_type": "squat"
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        webSocketListener.onMessage(mockWebSocket, toolCallMessage)

        val messages = mutableListOf<String>()
        val job = launch {
            messageFlow.toList(messages)
        }

        advanceUntilIdle()

        assertThat(messages).hasSize(1)

        // Validate tool call structure
        val parsed = gson.fromJson(toolCallMessage, JsonObject::class.java)
        assertThat(parsed.has("toolCall")).isTrue()

        val toolCall = parsed.getAsJsonObject("toolCall")
        assertThat(toolCall.has("functionCalls")).isTrue()

        val functionCalls = toolCall.getAsJsonArray("functionCalls")
        assertThat(functionCalls.size()).isEqualTo(1)

        val functionCall = functionCalls.get(0).asJsonObject
        assertThat(functionCall.has("name")).isTrue()
        assertThat(functionCall.has("id")).isTrue()
        assertThat(functionCall.has("args")).isTrue()

        job.cancel()
    }

    @Test
    fun `test server message handling - setupComplete`() = testScope.runTest {
        val setupCompleteMessage = """
            {
                "setupComplete": {}
            }
        """.trimIndent()

        webSocketListener.onMessage(mockWebSocket, setupCompleteMessage)

        val messages = mutableListOf<String>()
        val job = launch {
            messageFlow.toList(messages)
        }

        advanceUntilIdle()

        assertThat(messages).hasSize(1)

        val parsed = gson.fromJson(setupCompleteMessage, JsonObject::class.java)
        assertThat(parsed.has("setupComplete")).isTrue()

        job.cancel()
    }

    @Test
    fun `test server message handling - goAway`() = testScope.runTest {
        val goAwayMessage = """
            {
                "goAway": {
                    "reason": "SESSION_TIMEOUT"
                }
            }
        """.trimIndent()

        webSocketListener.onMessage(mockWebSocket, goAwayMessage)

        val messages = mutableListOf<String>()
        val job = launch {
            messageFlow.toList(messages)
        }

        advanceUntilIdle()

        assertThat(messages).hasSize(1)

        val parsed = gson.fromJson(goAwayMessage, JsonObject::class.java)
        assertThat(parsed.has("goAway")).isTrue()

        val goAway = parsed.getAsJsonObject("goAway")
        assertThat(goAway.has("reason")).isTrue()
        assertThat(goAway.get("reason").asString).isEqualTo("SESSION_TIMEOUT")

        job.cancel()
    }

    @Test
    fun `test message serialization performance`() = testScope.runTest {
        val largeMessage = createLargeClientContentMessage(1000) // 1000 parts

        val startTime = System.nanoTime()
        val jsonMessage = gson.toJson(largeMessage)
        val serializationTime = (System.nanoTime() - startTime) / 1_000_000 // ms

        // Serialization should be under 100ms for large messages
        assertThat(serializationTime).isLessThan(100)
        assertThat(jsonMessage).isNotEmpty()

        // Test deserialization performance
        val deserializeStart = System.nanoTime()
        val parsed = gson.fromJson(jsonMessage, JsonObject::class.java)
        val deserializationTime = (System.nanoTime() - deserializeStart) / 1_000_000

        assertThat(deserializationTime).isLessThan(50)
        assertThat(parsed.has("clientContent")).isTrue()
    }

    @Test
    fun `test concurrent message handling`() = testScope.runTest {
        val messageCount = 100
        val latch = CountDownLatch(messageCount)

        val messages = mutableListOf<String>()
        val job = launch {
            messageFlow.collect { message ->
                synchronized(messages) {
                    messages.add(message)
                }
                latch.countDown()
            }
        }

        // Send multiple messages concurrently
        repeat(messageCount) { index ->
            launch {
                val message = """{"clientContent": {"text": "Message $index"}}"""
                webSocketListener.onMessage(mockWebSocket, message)
            }
        }

        // Wait for all messages to be processed
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(messages).hasSize(messageCount)

        job.cancel()
    }

    // Helper methods for creating test data
    private fun createSetupMessage(): Map<String, Any> {
        return mapOf(
            "setup" to mapOf(
                "model" to "models/gemini-2.0-flash-exp",
                "generationConfig" to mapOf(
                    "responseModalities" to listOf("AUDIO", "TEXT"),
                    "speechConfig" to mapOf(
                        "voiceConfig" to mapOf(
                            "prebuiltVoiceConfig" to mapOf(
                                "voiceName" to "Aoede"
                            )
                        )
                    )
                ),
                "systemInstruction" to mapOf(
                    "parts" to listOf(
                        mapOf("text" to "You are a helpful pose analysis assistant.")
                    )
                ),
                "tools" to listOf(
                    mapOf(
                        "functionDeclarations" to listOf(
                            mapOf(
                                "name" to "analyze_pose_form",
                                "description" to "Analyze pose form and provide feedback",
                                "parameters" to mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "pose_data" to mapOf("type" to "string"),
                                        "exercise_type" to mapOf("type" to "string")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    private fun createClientContentMessage(text: String, mediaData: ByteArray?): Map<String, Any> {
        val parts = mutableListOf<Map<String, Any>>()
        parts.add(mapOf("text" to text))

        mediaData?.let { data ->
            parts.add(
                mapOf(
                    "inlineData" to mapOf(
                        "mimeType" to "image/jpeg",
                        "data" to android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
                    )
                )
            )
        }

        return mapOf(
            "clientContent" to mapOf(
                "turns" to listOf(
                    mapOf("parts" to parts)
                )
            )
        )
    }

    private fun createRealtimeInputMessage(audioData: ByteArray): Map<String, Any> {
        return mapOf(
            "realtimeInput" to mapOf(
                "mediaChunks" to listOf(
                    mapOf(
                        "mimeType" to "audio/pcm",
                        "data" to android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
                    )
                )
            )
        )
    }

    private fun createToolResponseMessage(functionName: String, functionResponse: Map<String, Any>): Map<String, Any> {
        return mapOf(
            "toolResponse" to mapOf(
                "functionResponses" to listOf(
                    mapOf(
                        "name" to functionName,
                        "response" to functionResponse
                    )
                )
            )
        )
    }

    private fun createMockImageData(): ByteArray {
        // Create a simple mock JPEG header for testing
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00,
            // ... (truncated for brevity)
            0xFF.toByte(), 0xD9.toByte() // JPEG end marker
        )
    }

    private fun createMockAudioData(): ByteArray {
        // Create 16-bit PCM audio data (16kHz, mono, 1 second)
        val sampleRate = 16000
        val duration = 1.0 // seconds
        val samples = (sampleRate * duration).toInt()
        val audioData = ByteArray(samples * 2) // 16-bit = 2 bytes per sample

        // Generate a simple sine wave for testing
        for (i in 0 until samples) {
            val sample = (32767 * kotlin.math.sin(2 * kotlin.math.PI * 440 * i / sampleRate)).toInt().toShort()
            val buffer = ByteBuffer.allocate(2).putShort(sample)
            buffer.array().copyInto(audioData, i * 2)
        }

        return audioData
    }

    private fun createLargeClientContentMessage(partCount: Int): Map<String, Any> {
        val parts = (0 until partCount).map { index ->
            mapOf("text" to "Part $index of large message")
        }

        return mapOf(
            "clientContent" to mapOf(
                "turns" to listOf(
                    mapOf("parts" to parts)
                )
            )
        )
    }
}