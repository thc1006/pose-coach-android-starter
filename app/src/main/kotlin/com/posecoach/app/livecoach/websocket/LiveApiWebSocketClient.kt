package com.posecoach.app.livecoach.websocket

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.posecoach.app.livecoach.models.*
import com.posecoach.app.livecoach.state.LiveCoachStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class LiveApiWebSocketClient(
    private val apiKey: String,
    private val stateManager: LiveCoachStateManager,
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var sessionStartTime: Long = 0
    private var messagesSent: Int = 0
    private var messagesReceived: Int = 0
    private var lastMessageTimestamp: Long = 0

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

    companion object {
        private const val WEBSOCKET_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val PING_INTERVAL_MS = 20000L
        private const val CONNECTION_TIMEOUT_MS = 30000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BASE_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val HEALTH_CHECK_INTERVAL_MS = 60000L
        private const val MAX_IDLE_TIME_MS = 300000L // 5 minutes
        private const val MESSAGE_RATE_LIMIT = 50 // messages per second
    }

    fun connect(config: LiveApiConfig = LiveApiConfig()) {
        if (stateManager.isConnecting() || stateManager.isConnected()) {
            Timber.w("Already connecting or connected")
            return
        }

        sessionStartTime = System.currentTimeMillis()
        messagesSent = 0
        messagesReceived = 0
        lastMessageTimestamp = sessionStartTime

        stateManager.updateConnectionState(ConnectionState.CONNECTING)
        stateManager.setError(null)

        val url = "$WEBSOCKET_URL?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "PoseCoach-Android/1.0")
            .addHeader("Connection", "Upgrade")
            .addHeader("Upgrade", "websocket")
            .build()

        // Start connection timeout
        startConnectionTimeout()

        webSocket = client.newWebSocket(request, createWebSocketListener(config))

        Timber.d("WebSocket connection initiated to: $url")
    }

    private fun createWebSocketListener(config: LiveApiConfig) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.d("WebSocket connected successfully")

            // Cancel connection timeout
            connectionTimeoutJob?.cancel()

            stateManager.updateConnectionState(ConnectionState.CONNECTED)
            stateManager.resetRetryCount()
            stateManager.setSessionId(generateSessionId())

            // Send initial setup
            sendSetupMessage(config)

            // Start monitoring jobs
            startPingJob()
            startHealthCheck()

            Timber.i("Live API session established: ${stateManager.getCurrentState().sessionId}")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            messagesReceived++
            lastMessageTimestamp = System.currentTimeMillis()

            try {
                Timber.v("Received message (#$messagesReceived): ${text.take(200)}...")
                handleIncomingMessage(text)
            } catch (e: Exception) {
                Timber.e(e, "Error handling message: $text")
                stateManager.setError("Message parsing error: ${e.message}")
                launch { _errors.emit("Failed to process server message: ${e.message}") }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // Handle binary messages if needed
            Timber.d("Received binary message: ${bytes.size} bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("WebSocket closing: $code - $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("WebSocket closed: $code - $reason")
            stateManager.updateConnectionState(ConnectionState.DISCONNECTED)
            cancelPingJob()

            // Attempt reconnection if not manually closed
            if (code != 1000) { // Not normal closure
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val errorMessage = "Connection failed: ${t.message}"
            val httpCode = response?.code

            Timber.e(t, "WebSocket connection failed (HTTP: $httpCode)")

            // Cancel ongoing jobs
            connectionTimeoutJob?.cancel()
            cancelPingJob()
            cancelHealthCheck()

            stateManager.updateConnectionState(ConnectionState.ERROR)
            stateManager.setError(errorMessage)

            launch {
                val detailedError = if (httpCode != null) {
                    "$errorMessage (HTTP $httpCode)"
                } else {
                    errorMessage
                }
                _errors.emit(detailedError)
            }

            scheduleReconnect()
        }
    }

    private fun sendSetupMessage(config: LiveApiConfig) {
        val setupMessage = mapOf(
            "setup" to mapOf(
                "model" to config.model,
                "generationConfig" to config.generationConfig,
                "systemInstruction" to config.systemInstruction,
                "realtimeInputConfig" to config.realtimeInputConfig
            )
        )

        val json = gson.toJson(setupMessage)
        Timber.d("Sending setup: $json")
        webSocket?.send(json)
    }

    fun sendRealtimeInput(input: LiveApiMessage.RealtimeInput) {
        if (!stateManager.isConnected()) {
            Timber.w("Not connected, cannot send realtime input")
            return
        }

        // Rate limiting check
        if (!checkRateLimit()) {
            Timber.w("Rate limit exceeded, dropping message")
            return
        }

        val message = mapOf("realtimeInput" to input)
        val json = gson.toJson(message)

        launch {
            try {
                val success = webSocket?.send(json) ?: false
                if (success) {
                    messagesSent++
                    lastMessageTimestamp = System.currentTimeMillis()
                    Timber.v("Sent realtime input (#$messagesSent)")
                } else {
                    throw IllegalStateException("WebSocket send returned false")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to send realtime input")
                stateManager.setError("Send failed: ${e.message}")
                launch { _errors.emit("Failed to send data: ${e.message}") }
            }
        }
    }

    fun sendToolResponse(response: LiveApiMessage.ToolResponse) {
        if (!stateManager.isConnected()) {
            Timber.w("Not connected, cannot send tool response")
            return
        }

        val message = mapOf("toolResponse" to response)
        val json = gson.toJson(message)

        launch {
            try {
                webSocket?.send(json)
                Timber.d("Sent tool response")
            } catch (e: Exception) {
                Timber.e(e, "Failed to send tool response")
                stateManager.setError("Tool response failed: ${e.message}")
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        Timber.v("Received: $text")

        try {
            val jsonObject = gson.fromJson(text, Map::class.java) as Map<String, Any>

            when {
                jsonObject.containsKey("setupComplete") -> {
                    val setupComplete = jsonObject["setupComplete"] as Boolean
                    launch { _responses.emit(LiveApiResponse.SetupComplete(setupComplete)) }
                    Timber.d("Setup complete: $setupComplete")
                }

                jsonObject.containsKey("serverContent") -> {
                    val serverContent = parseServerContent(jsonObject["serverContent"] as Map<String, Any>)
                    launch { _responses.emit(serverContent) }
                }

                jsonObject.containsKey("toolCall") -> {
                    val toolCall = parseToolCall(jsonObject["toolCall"] as Map<String, Any>)
                    launch { _responses.emit(toolCall) }
                }

                jsonObject.containsKey("toolCallCancellation") -> {
                    val cancellation = parseToolCallCancellation(jsonObject["toolCallCancellation"] as Map<String, Any>)
                    launch { _responses.emit(cancellation) }
                }

                else -> {
                    Timber.w("Unknown message type: ${jsonObject.keys}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse message: $text")
            stateManager.setError("Message parsing failed: ${e.message}")
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

    private fun parseContent(data: Map<String, Any>): Content {
        val parts = (data["parts"] as? List<Map<String, Any>>)?.map { part ->
            when {
                part.containsKey("text") -> Part.TextPart(part["text"] as String)
                part.containsKey("inlineData") -> {
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

    private fun parseToolCall(data: Map<String, Any>): LiveApiResponse.ToolCall {
        val functionCalls = (data["functionCalls"] as? List<Map<String, Any>>)?.map { call ->
            FunctionCall(
                name = call["name"] as String,
                args = call["args"] as Map<String, Any>,
                id = call["id"] as String
            )
        } ?: emptyList()

        return LiveApiResponse.ToolCall(functionCalls)
    }

    private fun parseToolCallCancellation(data: Map<String, Any>): LiveApiResponse.ToolCallCancellation {
        val ids = (data["ids"] as? List<String>) ?: emptyList()
        return LiveApiResponse.ToolCallCancellation(ids)
    }

    private var pingJob: Job? = null

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = launch {
            while (isActive && stateManager.isConnected()) {
                delay(PING_INTERVAL_MS)
                try {
                    webSocket?.send("ping")
                } catch (e: Exception) {
                    Timber.w(e, "Ping failed")
                    break
                }
            }
        }
    }

    private fun cancelPingJob() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun scheduleReconnect() {
        if (!stateManager.canRetry()) {
            val errorMsg = "Max reconnection attempts (${MAX_RECONNECT_ATTEMPTS}) reached"
            Timber.e(errorMsg)
            stateManager.updateConnectionState(ConnectionState.ERROR)
            launch { _errors.emit(errorMsg) }
            return
        }

        reconnectJob?.cancel()
        reconnectJob = launch {
            if (stateManager.incrementRetryCount()) {
                val retryCount = stateManager.getCurrentState().retryCount
                val delay = calculateExponentialBackoff(retryCount)

                Timber.d("Scheduling reconnect attempt $retryCount/$MAX_RECONNECT_ATTEMPTS in ${delay}ms")
                stateManager.updateConnectionState(ConnectionState.RECONNECTING)

                delay(delay)

                if (isActive && !stateManager.isConnected()) {
                    Timber.i("Attempting reconnection $retryCount/$MAX_RECONNECT_ATTEMPTS")
                    connect()
                }
            }
        }
    }

    fun disconnect() {
        Timber.d("Disconnecting WebSocket")

        // Cancel all monitoring jobs
        connectionTimeoutJob?.cancel()
        reconnectJob?.cancel()
        cancelPingJob()
        cancelHealthCheck()

        // Close WebSocket with normal closure code
        webSocket?.close(1000, "Manual disconnect")
        webSocket = null

        stateManager.updateConnectionState(ConnectionState.DISCONNECTED)

        Timber.i("WebSocket disconnected. Final metrics: ${getSessionMetrics()}")
    }

    fun forceReconnect() {
        Timber.d("Force reconnecting")
        disconnect()
        stateManager.resetRetryCount()
        connect()
    }

    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (!stateManager.isConnected()) {
                Timber.w("Connection timeout after ${CONNECTION_TIMEOUT_MS}ms")
                webSocket?.close(1001, "Connection timeout")
                stateManager.updateConnectionState(ConnectionState.ERROR)
                stateManager.setError("Connection timeout")
                launch { _errors.emit("Connection timeout - please check your internet connection") }
            }
        }
    }

    private fun calculateExponentialBackoff(retryCount: Int): Long {
        val baseDelay = BASE_RETRY_DELAY_MS * (1L shl (retryCount - 1))
        val jitter = (0..1000).random() // Add jitter to avoid thundering herd
        return (baseDelay + jitter).coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceStart = now - sessionStartTime
        if (timeSinceStart == 0L) return true

        val messagesPerSecond = (messagesSent * 1000L) / timeSinceStart
        return messagesPerSecond < MESSAGE_RATE_LIMIT
    }

    private var healthCheckJob: Job? = null

    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = launch {
            while (isActive && stateManager.isConnected()) {
                delay(HEALTH_CHECK_INTERVAL_MS)

                val now = System.currentTimeMillis()
                val timeSinceLastMessage = now - lastMessageTimestamp

                if (timeSinceLastMessage > MAX_IDLE_TIME_MS) {
                    Timber.w("Connection idle for ${timeSinceLastMessage}ms, checking health")
                    if (!checkConnectionHealth()) {
                        Timber.e("Health check failed, reconnecting")
                        forceReconnect()
                        break
                    }
                }
            }
        }
    }

    private fun cancelHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    private suspend fun checkConnectionHealth(): Boolean {
        return try {
            val healthMessage = mapOf(
                "ping" to mapOf(
                    "timestamp" to System.currentTimeMillis()
                )
            )
            val success = webSocket?.send(gson.toJson(healthMessage)) ?: false
            if (success) {
                Timber.d("Health check ping sent")
            }
            success
        } catch (e: Exception) {
            Timber.e(e, "Health check failed")
            false
        }
    }

    fun getSessionMetrics(): Map<String, Any> {
        val now = System.currentTimeMillis()
        val sessionDuration = now - sessionStartTime

        return mapOf(
            "sessionId" to (stateManager.getCurrentState().sessionId ?: "none"),
            "connectionState" to stateManager.getCurrentState().connectionState.name,
            "sessionDurationMs" to sessionDuration,
            "messagesSent" to messagesSent,
            "messagesReceived" to messagesReceived,
            "avgMessagesPerSecond" to if (sessionDuration > 0) (messagesSent * 1000L / sessionDuration) else 0,
            "lastMessageAgoMs" to (now - lastMessageTimestamp),
            "retryCount" to stateManager.getCurrentState().retryCount
        )
    }

    fun isHealthy(): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastMessage = now - lastMessageTimestamp
        return stateManager.isConnected() &&
               timeSinceLastMessage < MAX_IDLE_TIME_MS &&
               stateManager.getCurrentState().retryCount < MAX_RECONNECT_ATTEMPTS
    }

    fun destroy() {
        Timber.d("Destroying WebSocket client")

        // Cancel all jobs
        connectionTimeoutJob?.cancel()
        cancelHealthCheck()
        reconnectJob?.cancel()

        disconnect()
        cancel() // Cancel coroutine scope

        Timber.i("WebSocket client destroyed. Session metrics: ${getSessionMetrics()}")
    }
}