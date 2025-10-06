package com.posecoach.app.livecoach.websocket

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.posecoach.app.livecoach.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * Handles parsing and processing of Live API WebSocket messages
 *
 * Responsibilities:
 * - Parsing incoming JSON messages from WebSocket
 * - Creating outgoing message JSON
 * - Converting between JSON and Live API model objects
 * - Emitting parsed responses to subscribers
 * - Handling message parsing errors
 *
 * This component focuses purely on message processing and does not
 * handle connection management or WebSocket lifecycle.
 */
class LiveApiMessageProcessor(
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    // Message flows
    private val _responses = MutableSharedFlow<LiveApiResponse>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val responses: SharedFlow<LiveApiResponse> = _responses.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // Message processing metrics
    private val messagesProcessed = AtomicLong(0)
    private val errorsEncountered = AtomicLong(0)

    /**
     * Emit an error message to subscribers
     * Used by WebSocket client for connection errors
     */
    suspend fun emitError(errorMessage: String) {
        _errors.emit(errorMessage)
    }

    /**
     * Process an incoming WebSocket message
     * Parses JSON and emits appropriate LiveApiResponse
     */
    fun processIncomingMessage(text: String) {
        messagesProcessed.incrementAndGet()

        try {
            Timber.v("Processing message: ${text.take(200)}...")
            handleJsonMessage(text)
        } catch (e: Exception) {
            errorsEncountered.incrementAndGet()
            Timber.e(e, "Error processing message: $text")
            launch { _errors.emit("Failed to process server message: ${e.message}") }
        }
    }

    /**
     * Create a setup message JSON for WebSocket connection initialization
     */
    fun createSetupMessage(config: LiveApiConfig): String {
        val setupMessage = mapOf(
            "setup" to mapOf(
                "model" to config.model,
                "generationConfig" to config.generationConfig,
                "systemInstruction" to config.systemInstruction,
                "realtimeInputConfig" to config.realtimeInputConfig
            )
        )

        return gson.toJson(setupMessage)
    }

    /**
     * Create a realtime input message JSON
     */
    fun createRealtimeInputMessage(input: LiveApiMessage.RealtimeInput): String {
        val message = mapOf("realtimeInput" to input)
        return gson.toJson(message)
    }

    /**
     * Create a tool response message JSON
     */
    fun createToolResponseMessage(response: LiveApiMessage.ToolResponse): String {
        val message = mapOf("toolResponse" to response)
        return gson.toJson(message)
    }

    /**
     * Parse content structure from JSON
     */
    fun parseContent(data: Map<String, Any>): Content {
        val parts = (data["parts"] as? List<Map<String, Any>>)?.map { part ->
            when {
                part.containsKey("text") -> Part.TextPart(part["text"] as String)
                part.containsKey("inlineData") -> {
                    @Suppress("UNCHECKED_CAST")
                    val inlineData = part["inlineData"] as Map<String, Any>
                    Part.InlineDataPart(
                        mimeType = inlineData["mimeType"] as String,
                        data = inlineData["data"] as String
                    )
                }
                else -> throw IllegalArgumentException("Unknown part type: $part")
            }
        } ?: emptyList()

        return Content(parts)
    }

    /**
     * Get message processing metrics
     */
    fun getMessageMetrics(): MessageMetrics {
        return MessageMetrics(
            messagesProcessed = messagesProcessed.get(),
            errorsEncountered = errorsEncountered.get(),
            successRate = if (messagesProcessed.get() > 0) {
                ((messagesProcessed.get() - errorsEncountered.get()) * 100.0 / messagesProcessed.get())
            } else {
                100.0
            }
        )
    }

    /**
     * Check if the processor has been destroyed
     */
    fun isDestroyed(): Boolean {
        return !coroutineContext.isActive
    }

    /**
     * Handle parsed JSON message and route to appropriate response type
     */
    private fun handleJsonMessage(text: String) {
        @Suppress("UNCHECKED_CAST")
        val jsonObject = gson.fromJson(text, Map::class.java) as Map<String, Any>

        when {
            jsonObject.containsKey("setupComplete") -> {
                val setupComplete = jsonObject["setupComplete"] as Boolean
                launch { _responses.emit(LiveApiResponse.SetupComplete(setupComplete)) }
                Timber.d("Setup complete: $setupComplete")
            }

            jsonObject.containsKey("serverContent") -> {
                @Suppress("UNCHECKED_CAST")
                val serverContent = parseServerContent(jsonObject["serverContent"] as Map<String, Any>)
                launch { _responses.emit(serverContent) }
            }

            jsonObject.containsKey("toolCall") -> {
                @Suppress("UNCHECKED_CAST")
                val toolCall = parseToolCall(jsonObject["toolCall"] as Map<String, Any>)
                launch { _responses.emit(toolCall) }
            }

            jsonObject.containsKey("toolCallCancellation") -> {
                @Suppress("UNCHECKED_CAST")
                val cancellation = parseToolCallCancellation(jsonObject["toolCallCancellation"] as Map<String, Any>)
                launch { _responses.emit(cancellation) }
            }

            else -> {
                val errorMsg = "Unknown message type: ${jsonObject.keys}"
                Timber.w(errorMsg)
                launch { _errors.emit(errorMsg) }
            }
        }
    }

    private fun parseServerContent(data: Map<String, Any>): LiveApiResponse.ServerContent {
        val modelTurn = (data["modelTurn"] as? Map<String, Any>)?.let { parseContent(it) }
        val turnComplete = data["turnComplete"] as? Boolean ?: false
        val interrupted = data["interrupted"] as? Boolean ?: false
        val inputTranscription = (data["inputTranscription"] as? Map<String, Any>)?.let {
            Transcription(it["transcribedText"] as String)
        }
        val outputTranscription = (data["outputTranscription"] as? Map<String, Any>)?.let {
            Transcription(it["transcribedText"] as String)
        }

        return LiveApiResponse.ServerContent(
            modelTurn = modelTurn,
            turnComplete = turnComplete,
            interrupted = interrupted,
            inputTranscription = inputTranscription,
            outputTranscription = outputTranscription
        )
    }

    private fun parseToolCall(data: Map<String, Any>): LiveApiResponse.ToolCall {
        val functionCalls = (data["functionCalls"] as? List<Map<String, Any>>)?.map { call ->
            FunctionCall(
                name = call["name"] as String,
                args = call["args"].let {
                    @Suppress("UNCHECKED_CAST")
                    it as Map<String, Any>
                },
                id = call["id"] as String
            )
        } ?: emptyList()

        return LiveApiResponse.ToolCall(functionCalls)
    }

    private fun parseToolCallCancellation(data: Map<String, Any>): LiveApiResponse.ToolCallCancellation {
        val ids = (data["ids"] as? List<String>) ?: emptyList()
        return LiveApiResponse.ToolCallCancellation(ids)
    }

    /**
     * Destroy the message processor and clean up resources
     */
    fun destroy() {
        Timber.d("Destroying message processor")
        cancel()
        Timber.d("Message processor destroyed")
    }

    /**
     * Message processing metrics data class
     */
    data class MessageMetrics(
        val messagesProcessed: Long,
        val errorsEncountered: Long,
        val successRate: Double
    )
}