package com.posecoach.app.livecoach.websocket

import com.posecoach.app.livecoach.models.*
import com.posecoach.app.livecoach.state.LiveCoachStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import okio.ByteString
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * Core WebSocket client for Live API communication
 *
 * Orchestrates connection management, message processing, and health monitoring
 * by delegating to specialized components. This maintains the public API while
 * improving internal organization and testability.
 */
class LiveApiWebSocketClient(
    private val apiKey: String,
    private val stateManager: LiveCoachStateManager,
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    // Specialized components for modular functionality
    private val connectionManager = LiveApiConnectionManager(stateManager, this)
    private val messageProcessor = LiveApiMessageProcessor(this)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var healthCheckJob: Job? = null
    private var messagesSent = AtomicLong(0)
    private var messagesReceived = AtomicLong(0)
    private var lastMessageTimestamp = AtomicLong(0)
    private var sessionStartTime: Long = 0

    // Expose flows from message processor
    val responses: SharedFlow<LiveApiResponse> = messageProcessor.responses
    val errors: SharedFlow<String> = messageProcessor.errors

    fun connect(config: LiveApiConfig = LiveApiConfig()) {
        // Validate API key before connecting
        if (apiKey.isEmpty()) {
            val error = "Cannot connect: API key is empty. Please configure API key in local.properties"
            Timber.e(error)
            stateManager.setError(error)
            launch {
                messageProcessor.emitError(error)
            }
            return
        }

        if (!apiKey.startsWith("AIza") || apiKey.length < 35) {
            val error = "Cannot connect: API key format is invalid (should start with 'AIza' and be at least 35 chars)"
            Timber.e(error)
            stateManager.setError(error)
            launch {
                messageProcessor.emitError(error)
            }
            return
        }

        // Delegate connection initialization to connection manager
        connectionManager.initializeConnection()

        sessionStartTime = System.currentTimeMillis()
        messagesSent.set(0)
        messagesReceived.set(0)
        lastMessageTimestamp.set(sessionStartTime)

        val url = "${ConnectionConfig.WEBSOCKET_URL}?key=$apiKey"
        val obfuscatedUrl = "${ConnectionConfig.WEBSOCKET_URL}?key=${apiKey.take(6)}...${apiKey.takeLast(4)}"

        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.d("📡 Live API WebSocket Connection")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.d("🔗 URL: $obfuscatedUrl")
        Timber.d("🔑 API Key present: ✓")
        Timber.d("🔑 API Key length: ${apiKey.length}")
        Timber.d("📝 Model: ${config.model}")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "PoseCoach-Android/1.0")
            .addHeader("Connection", "Upgrade")
            .addHeader("Upgrade", "websocket")
            .build()

        // Start connection timeout through connection manager
        connectionManager.startConnectionTimeout()

        webSocket = client.newWebSocket(request, createWebSocketListener(config))

        Timber.d("⏳ WebSocket connection initiated, waiting for response...")
    }

    private fun createWebSocketListener(config: LiveApiConfig) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.d("✅ WebSocket Connected Successfully!")
            Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.d("📊 Response Code: ${response.code}")
            Timber.d("📊 Protocol: ${response.protocol}")
            Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Delegate to connection manager
            connectionManager.onConnectionEstablished()

            // Send initial setup using message processor
            sendSetupMessage(config)

            // Start monitoring jobs
            startPingJob()
            startHealthCheck()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            messagesReceived.incrementAndGet()
            lastMessageTimestamp.set(System.currentTimeMillis())

            Timber.v("📨 Received message (#${messagesReceived.get()}): ${text.take(200)}...")

            // Delegate message processing to message processor
            messageProcessor.processIncomingMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // Handle binary messages if needed
            Timber.d("📨 Received binary message: ${bytes.size} bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.w("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.w("⚠️ WebSocket closing...")
            Timber.w("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.w("📊 Close Code: $code")
            Timber.w("📊 Reason: $reason")
            Timber.w("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("🔌 WebSocket Closed")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("📊 Close Code: $code")
            Timber.i("📊 Reason: $reason")
            Timber.i("📊 Session Duration: ${System.currentTimeMillis() - sessionStartTime}ms")
            Timber.i("📊 Messages Sent: ${messagesSent.get()}")
            Timber.i("📊 Messages Received: ${messagesReceived.get()}")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            cancelPingJob()
            connectionManager.onConnectionClosed(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val errorMessage = "Connection failed: ${t.message}"
            val httpCode = response?.code

            Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.e("❌ WebSocket Connection FAILED")
            Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.e("📊 HTTP Code: $httpCode")
            Timber.e("📊 Error: ${t.message}")
            Timber.e("📊 Error Type: ${t.javaClass.simpleName}")

            // Log response details if available
            response?.let { resp ->
                Timber.e("📊 Response Code: ${resp.code}")
                Timber.e("📊 Response Message: ${resp.message}")
                resp.body?.let { body ->
                    try {
                        val bodyString = body.string()
                        Timber.e("📊 Response Body: $bodyString")
                    } catch (e: Exception) {
                        Timber.e("📊 Could not read response body: ${e.message}")
                    }
                }
            }

            // Common error interpretations
            when {
                httpCode == 401 || httpCode == 403 -> {
                    Timber.e("🔑 Authentication Error: Invalid or expired API key")
                    Timber.e("💡 Solution: Check your API key in local.properties")
                }
                httpCode == 429 -> {
                    Timber.e("⏱️ Rate Limit Error: Too many requests")
                    Timber.e("💡 Solution: Wait before retrying or check quota")
                }
                httpCode == 404 -> {
                    Timber.e("🔍 Not Found Error: Invalid endpoint URL")
                    Timber.e("💡 Solution: Check WebSocket URL configuration")
                }
                t is java.net.UnknownHostException -> {
                    Timber.e("🌐 Network Error: Cannot resolve host")
                    Timber.e("💡 Solution: Check internet connection")
                }
                t is java.net.SocketTimeoutException -> {
                    Timber.e("⏰ Timeout Error: Connection timed out")
                    Timber.e("💡 Solution: Check network stability")
                }
            }

            Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.e(t, "Full stack trace:")

            // Cancel ongoing jobs
            cancelPingJob()
            cancelHealthCheck()

            // Delegate to connection manager
            connectionManager.onConnectionFailed(errorMessage, httpCode)

            // Schedule reconnect
            connectionManager.scheduleReconnect()
        }
    }

    private fun sendSetupMessage(config: LiveApiConfig) {
        val json = messageProcessor.createSetupMessage(config)
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

        val json = messageProcessor.createRealtimeInputMessage(input)

        launch {
            try {
                val success = webSocket?.send(json) ?: false
                if (success) {
                    messagesSent.incrementAndGet()
                    lastMessageTimestamp.set(System.currentTimeMillis())
                    Timber.v("Sent realtime input (#${messagesSent.get()})")
                } else {
                    throw IllegalStateException("WebSocket send returned false")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to send realtime input")
                stateManager.setError("Send failed: ${e.message}")
            }
        }
    }

    fun sendToolResponse(response: LiveApiMessage.ToolResponse) {
        if (!stateManager.isConnected()) {
            Timber.w("Not connected, cannot send tool response")
            return
        }

        val json = messageProcessor.createToolResponseMessage(response)

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

    // Message parsing is now handled by messageProcessor

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = launch {
            while (isActive && stateManager.isConnected()) {
                delay(ConnectionConfig.PING_INTERVAL_MS)
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

    // Reconnection logic is now handled by connectionManager

    fun disconnect() {
        Timber.d("Disconnecting WebSocket")

        // Cancel all monitoring jobs
        cancelPingJob()
        cancelHealthCheck()

        // Close WebSocket with normal closure code
        webSocket?.close(1000, "Manual disconnect")
        webSocket = null

        // Delegate to connection manager
        connectionManager.disconnect()

        Timber.i("WebSocket disconnected. Final metrics: ${getSessionMetrics()}")
    }

    fun forceReconnect() {
        Timber.d("Force reconnecting")
        disconnect()
        connectionManager.forceReconnect()
    }

    // Session ID generation is now handled by connectionManager

    // Connection timeout is now handled by connectionManager

    // Exponential backoff calculation is now handled by connectionManager

    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceStart = now - sessionStartTime
        if (timeSinceStart == 0L) return true

        val messagesPerSecond = (messagesSent.get() * 1000L) / timeSinceStart
        return messagesPerSecond < ConnectionConfig.MESSAGE_RATE_LIMIT
    }

    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = launch {
            while (isActive && stateManager.isConnected()) {
                delay(ConnectionConfig.HEALTH_CHECK_INTERVAL_MS)

                val now = System.currentTimeMillis()
                val timeSinceLastMessage = now - lastMessageTimestamp.get()

                if (timeSinceLastMessage > ConnectionConfig.MAX_IDLE_TIME_MS) {
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
            val json = messageProcessor.createRealtimeInputMessage(
                LiveApiMessage.RealtimeInput(text = "health_check")
            )
            val success = webSocket?.send(json) ?: false
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
        val messagesSentValue = messagesSent.get()
        val messagesReceivedValue = messagesReceived.get()

        return mapOf(
            "sessionId" to (stateManager.getCurrentState().sessionId ?: "none"),
            "connectionState" to stateManager.getCurrentState().connectionState.name,
            "sessionDurationMs" to sessionDuration,
            "messagesSent" to messagesSentValue,
            "messagesReceived" to messagesReceivedValue,
            "avgMessagesPerSecond" to if (sessionDuration > 0) (messagesSentValue * 1000L / sessionDuration) else 0,
            "lastMessageAgoMs" to (now - lastMessageTimestamp.get()),
            "retryCount" to stateManager.getCurrentState().retryCount
        )
    }

    fun isHealthy(): Boolean {
        return connectionManager.isHealthy() &&
               (System.currentTimeMillis() - lastMessageTimestamp.get()) < ConnectionConfig.MAX_IDLE_TIME_MS
    }

    fun destroy() {
        Timber.d("Destroying WebSocket client")

        // Cancel monitoring jobs
        cancelHealthCheck()
        cancelPingJob()

        disconnect()

        // Destroy components
        connectionManager.destroy()
        messageProcessor.destroy()

        cancel() // Cancel coroutine scope

        Timber.i("WebSocket client destroyed. Session metrics: ${getSessionMetrics()}")
    }
}