package com.posecoach.app.livecoach.websocket

import com.google.gson.GsonBuilder
import com.posecoach.app.livecoach.models.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.test.AfterTest

/**
 * Unit tests for LiveApiMessageProcessor
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveApiMessageProcessorTest {

    private lateinit var messageProcessor: LiveApiMessageProcessor
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        messageProcessor = LiveApiMessageProcessor(testScope)
    }

    @AfterTest
    fun tearDown() {
        messageProcessor.destroy()
    }

    @Test
    fun processIncomingMessage_setupComplete_emitsCorrectResponse() = testScope.runTest {
        val setupCompleteJson = """{"setupComplete": true}"""
        val responses = mutableListOf<LiveApiResponse>()

        val job = launch {
            messageProcessor.responses.collect { responses.add(it) }
        }

        messageProcessor.processIncomingMessage(setupCompleteJson)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, responses.size)
        assertTrue(responses[0] is LiveApiResponse.SetupComplete)
        assertTrue((responses[0] as LiveApiResponse.SetupComplete).setupComplete)

        job.cancel()
    }

    @Test
    fun processIncomingMessage_serverContent_emitsCorrectResponse() = testScope.runTest {
        val serverContentJson = """
            {
                "serverContent": {
                    "modelTurn": {
                        "parts": [{"text": "Hello"}]
                    },
                    "turnComplete": true,
                    "interrupted": false
                }
            }
        """.trimIndent()

        val responses = mutableListOf<LiveApiResponse>()
        val job = launch {
            messageProcessor.responses.collect { responses.add(it) }
        }

        messageProcessor.processIncomingMessage(serverContentJson)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, responses.size)
        assertTrue(responses[0] is LiveApiResponse.ServerContent)
        val serverContent = responses[0] as LiveApiResponse.ServerContent
        assertTrue(serverContent.turnComplete)
        assertFalse(serverContent.interrupted)
        assertNotNull(serverContent.modelTurn)

        job.cancel()
    }

    @Test
    fun processIncomingMessage_toolCall_emitsCorrectResponse() = testScope.runTest {
        val toolCallJson = """
            {
                "toolCall": {
                    "functionCalls": [
                        {
                            "name": "test_function",
                            "args": {"param": "value"},
                            "id": "call_123"
                        }
                    ]
                }
            }
        """.trimIndent()

        val responses = mutableListOf<LiveApiResponse>()
        val job = launch {
            messageProcessor.responses.collect { responses.add(it) }
        }

        messageProcessor.processIncomingMessage(toolCallJson)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, responses.size)
        assertTrue(responses[0] is LiveApiResponse.ToolCall)
        val toolCall = responses[0] as LiveApiResponse.ToolCall
        assertEquals(1, toolCall.functionCalls.size)
        assertEquals("test_function", toolCall.functionCalls[0].name)
        assertEquals("call_123", toolCall.functionCalls[0].id)

        job.cancel()
    }

    @Test
    fun processIncomingMessage_toolCallCancellation_emitsCorrectResponse() = testScope.runTest {
        val cancellationJson = """
            {
                "toolCallCancellation": {
                    "ids": ["call_123", "call_456"]
                }
            }
        """.trimIndent()

        val responses = mutableListOf<LiveApiResponse>()
        val job = launch {
            messageProcessor.responses.collect { responses.add(it) }
        }

        messageProcessor.processIncomingMessage(cancellationJson)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, responses.size)
        assertTrue(responses[0] is LiveApiResponse.ToolCallCancellation)
        val cancellation = responses[0] as LiveApiResponse.ToolCallCancellation
        assertEquals(2, cancellation.ids.size)
        assertTrue(cancellation.ids.contains("call_123"))
        assertTrue(cancellation.ids.contains("call_456"))

        job.cancel()
    }

    @Test
    fun processIncomingMessage_unknownMessageType_emitsError() = testScope.runTest {
        val unknownJson = """{"unknownField": "value"}"""
        val errors = mutableListOf<String>()

        val job = launch {
            messageProcessor.errors.collect { errors.add(it) }
        }

        messageProcessor.processIncomingMessage(unknownJson)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Unknown message type"))

        job.cancel()
    }

    @Test
    fun processIncomingMessage_invalidJson_emitsError() = testScope.runTest {
        val invalidJson = """{"invalid": json}"""
        val errors = mutableListOf<String>()

        val job = launch {
            messageProcessor.errors.collect { errors.add(it) }
        }

        messageProcessor.processIncomingMessage(invalidJson)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Failed to parse message"))

        job.cancel()
    }

    @Test
    fun createSetupMessage_generatesCorrectJson() {
        val config = LiveApiConfig(
            model = "test-model",
            generationConfig = mapOf("temperature" to 0.5),
            systemInstruction = mapOf("text" to "Test instruction")
        )

        val setupJson = messageProcessor.createSetupMessage(config)

        assertTrue(setupJson.contains("test-model"))
        assertTrue(setupJson.contains("temperature"))
        assertTrue(setupJson.contains("0.5"))
        assertTrue(setupJson.contains("Test instruction"))
        assertTrue(setupJson.contains("setup"))
    }

    @Test
    fun createRealtimeInputMessage_generatesCorrectJson() {
        val mediaChunk = MediaChunk("image/jpeg", "base64data")
        val input = LiveApiMessage.RealtimeInput(
            mediaChunks = listOf(mediaChunk),
            text = "Test input"
        )

        val inputJson = messageProcessor.createRealtimeInputMessage(input)

        assertTrue(inputJson.contains("realtimeInput"))
        assertTrue(inputJson.contains("image/jpeg"))
        assertTrue(inputJson.contains("base64data"))
        assertTrue(inputJson.contains("Test input"))
    }

    @Test
    fun createToolResponseMessage_generatesCorrectJson() {
        val response = LiveApiMessage.ToolResponse(
            functionResponses = listOf(
                FunctionResponse(
                    name = "test_function",
                    id = "call_123",
                    response = mapOf("result" to "success")
                )
            )
        )

        val responseJson = messageProcessor.createToolResponseMessage(response)

        assertTrue(responseJson.contains("toolResponse"))
        assertTrue(responseJson.contains("test_function"))
        assertTrue(responseJson.contains("call_123"))
        assertTrue(responseJson.contains("success"))
    }

    @Test
    fun parseContent_withTextPart_parsesCorrectly() {
        val contentData = mapOf(
            "parts" to listOf(
                mapOf("text" to "Hello world")
            )
        )

        val content = messageProcessor.parseContent(contentData)

        assertEquals(1, content.parts.size)
        assertTrue(content.parts[0] is Part.TextPart)
        assertEquals("Hello world", (content.parts[0] as Part.TextPart).text)
    }

    @Test
    fun parseContent_withInlineDataPart_parsesCorrectly() {
        val contentData = mapOf(
            "parts" to listOf(
                mapOf(
                    "inlineData" to mapOf(
                        "mimeType" to "image/jpeg",
                        "data" to "base64data"
                    )
                )
            )
        )

        val content = messageProcessor.parseContent(contentData)

        assertEquals(1, content.parts.size)
        assertTrue(content.parts[0] is Part.InlineDataPart)
        val inlineDataPart = content.parts[0] as Part.InlineDataPart
        assertEquals("image/jpeg", inlineDataPart.mimeType)
        assertEquals("base64data", inlineDataPart.data)
    }

    @Test
    fun parseContent_withMixedParts_parsesCorrectly() {
        val contentData = mapOf(
            "parts" to listOf(
                mapOf("text" to "Description:"),
                mapOf(
                    "inlineData" to mapOf(
                        "mimeType" to "image/jpeg",
                        "data" to "base64data"
                    )
                )
            )
        )

        val content = messageProcessor.parseContent(contentData)

        assertEquals(2, content.parts.size)
        assertTrue(content.parts[0] is Part.TextPart)
        assertTrue(content.parts[1] is Part.InlineDataPart)
    }

    @Test
    fun getMessageMetrics_returnsCorrectCounts() {
        val initialMetrics = messageProcessor.getMessageMetrics()
        assertEquals(0, initialMetrics.messagesProcessed)
        assertEquals(0, initialMetrics.errorsEncountered)

        // Process a valid message
        runTest {
            messageProcessor.processIncomingMessage("""{"setupComplete": true}""")
            testDispatcher.scheduler.advanceUntilIdle()
        }

        val afterValidMessage = messageProcessor.getMessageMetrics()
        assertEquals(1, afterValidMessage.messagesProcessed)
        assertEquals(0, afterValidMessage.errorsEncountered)

        // Process an invalid message
        runTest {
            messageProcessor.processIncomingMessage("""invalid json""")
            testDispatcher.scheduler.advanceUntilIdle()
        }

        val afterInvalidMessage = messageProcessor.getMessageMetrics()
        assertEquals(2, afterInvalidMessage.messagesProcessed)
        assertEquals(1, afterInvalidMessage.errorsEncountered)
    }

    @Test
    fun destroy_cancelsCoroutineScope() {
        assertFalse(messageProcessor.isDestroyed())

        messageProcessor.destroy()

        assertTrue(messageProcessor.isDestroyed())
    }
}