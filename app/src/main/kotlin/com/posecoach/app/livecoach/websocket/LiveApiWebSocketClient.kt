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
        // Delegate connection initialization to connection manager
        connectionManager.initializeConnection()

        sessionStartTime = System.currentTimeMillis()
        messagesSent.set(0)
        messagesReceived.set(0)
        lastMessageTimestamp.set(sessionStartTime)

        val url = "${ConnectionConfig.WEBSOCKET_URL}?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "PoseCoach-Android/1.0")
            .addHeader("Connection", "Upgrade")
            .addHeader("Upgrade", "websocket")
            .build()

        // Start connection timeout through connection manager
        connectionManager.startConnectionTimeout()

        webSocket = client.newWebSocket(request, createWebSocketListener(config))

        Timber.d("WebSocket connection initiated to: $url")
    }

    private fun createWebSocketListener(config: LiveApiConfig) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
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

            Timber.v("Received message (#${messagesReceived.get()}): ${text.take(200)}...")

            // Delegate message processing to message processor
            messageProcessor.processIncomingMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // Handle binary messages if needed
            Timber.d("Received binary message: ${bytes.size} bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("WebSocket closing: $code - $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            cancelPingJob()
            connectionManager.onConnectionClosed(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val errorMessage = "Connection failed: ${t.message}"
            val httpCode = response?.code

            Timber.e(t, "WebSocket connection failed (HTTP: $httpCode)")

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