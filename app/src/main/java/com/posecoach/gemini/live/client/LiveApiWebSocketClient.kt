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

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.posecoach.gemini.live.models.*
import com.posecoach.gemini.live.security.EphemeralTokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * Production-ready WebSocket client for Gemini Live API
 * Implements complete protocol specification with comprehensive error handling
 */
class LiveApiWebSocketClient(
    private val tokenManager: EphemeralTokenManager,
    private val config: LiveApiConfig = LiveApiConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : CoroutineScope {

    companion object {
        private const val WEBSOCKET_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val PING_INTERVAL_MS = 30_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val SESSION_TIMEOUT_MS = 600_000L // 10 minutes
        private const val GOAWAY_GRACE_PERIOD_MS = 5_000L
    }

    override val coroutineContext: CoroutineContext = scope.coroutineContext

    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for streaming
        .writeTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false) // We handle retries manually
        .build()

    private var webSocket: WebSocket? = null
    private val connectionState = MutableStateFlow(SessionState.DISCONNECTED)
    private val isConnected = AtomicBoolean(false)
    private val requestIdCounter = AtomicLong(0)
    private val currentSession = AtomicReference<String?>(null)

    // Message channels
    private val incomingMessages = Channel<String>(Channel.UNLIMITED)
    private val outgoingMessages = Channel<LiveApiMessage>(Channel.UNLIMITED)

    // Response flows
    private val _serverContent = MutableSharedFlow<BidiGenerateContentServerContent>()
    val serverContent: SharedFlow<BidiGenerateContentServerContent> = _serverContent.asSharedFlow()

    private val _setupComplete = MutableSharedFlow<BidiGenerateContentSetupComplete>()
    val setupComplete: SharedFlow<BidiGenerateContentSetupComplete> = _setupComplete.asSharedFlow()

    private val _goAway = MutableSharedFlow<BidiGenerateContentGoAway>()
    val goAway: SharedFlow<BidiGenerateContentGoAway> = _goAway.asSharedFlow()

    private val _connectionErrors = MutableSharedFlow<LiveApiError>()
    val connectionErrors: SharedFlow<LiveApiError> = _connectionErrors.asSharedFlow()

    // Session management
    private var sessionStartTime = 0L
    private var reconnectAttempts = 0
    private var sessionTimeoutJob: Job? = null
    private var messageProcessingJob: Job? = null
    private var outgoingMessageJob: Job? = null

    val state: StateFlow<SessionState> = connectionState.asStateFlow()

    init {
        startMessageProcessing()
        startOutgoingMessageProcessing()
        setupSessionTimeout()
    }

    /**
     * Connect to the Gemini Live API WebSocket
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get()) {
                return@withContext Result.success(Unit)
            }

            connectionState.value = SessionState.CONNECTING

            val token = tokenManager.getValidToken()
                ?: return@withContext Result.failure(
                    LiveApiError.AuthenticationError("No valid token available")
                )

            val request = Request.Builder()
                .url("$WEBSOCKET_URL?key=$token")
                .addHeader("Sec-WebSocket-Protocol", "generative-ai")
                .build()

            val listener = createWebSocketListener()
            webSocket = okHttpClient.newWebSocket(request, listener)

            // Wait for connection to be established
            connectionState.first { it == SessionState.CONNECTED || it == SessionState.ERROR }

            return@withContext if (connectionState.value == SessionState.CONNECTED) {
                Result.success(Unit)
            } else {
                Result.failure(LiveApiError.ConnectionError("Failed to establish connection"))
            }

        } catch (e: Exception) {
            connectionState.value = SessionState.ERROR
            Timber.e(e, "Failed to connect to Live API")
            Result.failure(LiveApiError.ConnectionError("Connection failed", e))
        }
    }

    /**
     * Setup the session with model configuration
     */
    suspend fun setupSession(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (connectionState.value != SessionState.CONNECTED) {
                return@withContext Result.failure(
                    LiveApiError.SessionError("Not connected")
                )
            }

            connectionState.value = SessionState.SETUP_PENDING

            val setupMessage = BidiGenerateContentSetup(
                requestId = generateRequestId(),
                setup = SetupConfig(
                    model = config.model,
                    generationConfig = GenerationConfig(
                        responseModalities = config.responseModalities,
                        speechConfig = SpeechConfig(
                            voiceConfig = VoiceConfig(
                                prebuiltVoiceConfig = PrebuiltVoiceConfig(config.voiceName)
                            )
                        ),
                        temperature = config.temperature,
                        maxOutputTokens = config.maxOutputTokens
                    ),
                    systemInstruction = config.systemInstruction?.let { instruction ->
                        Content(
                            role = "system",
                            parts = listOf(TextPart(instruction))
                        )
                    },
                    tools = config.tools
                )
            )

            sendMessage(setupMessage)

            // Wait for setup completion
            setupComplete.first()
            connectionState.value = SessionState.SETUP_COMPLETE

            return@withContext Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Session setup failed")
            Result.failure(LiveApiError.SessionError("Setup failed: ${e.message}"))
        }
    }

    /**
     * Send text content to the model
     */
    suspend fun sendText(text: String, turnComplete: Boolean = true): Result<Unit> {
        return try {
            val message = BidiGenerateContentClientContent(
                requestId = generateRequestId(),
                clientContent = ClientContent(
                    turns = listOf(
                        Turn(
                            role = "user",
                            parts = listOf(TextPart(text))
                        )
                    ),
                    turnComplete = turnComplete
                )
            )

            sendMessage(message)
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to send text")
            Result.failure(LiveApiError.ProtocolError("Failed to send text: ${e.message}"))
        }
    }

    /**
     * Send audio data in real-time
     */
    suspend fun sendAudioChunk(audioData: ByteArray): Result<Unit> {
        return try {
            val mediaChunk = createPCMMediaChunk(audioData)
            val message = BidiGenerateContentRealtimeInput(
                requestId = generateRequestId(),
                realtimeInput = RealtimeInput(
                    mediaChunks = listOf(mediaChunk)
                )
            )

            sendMessage(message)
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to send audio chunk")
            Result.failure(LiveApiError.AudioError("Failed to send audio", e))
        }
    }

    /**
     * Send tool response
     */
    suspend fun sendToolResponse(functionResponses: List<FunctionResponse>): Result<Unit> {
        return try {
            val message = BidiGenerateContentToolResponse(
                requestId = generateRequestId(),
                toolResponse = ToolResponse(functionResponses)
            )

            sendMessage(message)
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to send tool response")
            Result.failure(LiveApiError.ProtocolError("Failed to send tool response: ${e.message}"))
        }
    }

    /**
     * Gracefully disconnect from the Live API
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            connectionState.value = SessionState.DISCONNECTING

            // Cancel ongoing operations
            sessionTimeoutJob?.cancel()
            messageProcessingJob?.cancel()
            outgoingMessageJob?.cancel()

            // Close WebSocket
            webSocket?.close(1000, "Normal closure")
            webSocket = null

            isConnected.set(false)
            connectionState.value = SessionState.DISCONNECTED
            currentSession.set(null)

            Timber.d("Disconnected from Live API")

        } catch (e: Exception) {
            Timber.e(e, "Error during disconnect")
        }
    }

    /**
     * Attempt to reconnect after connection loss
     */
    private suspend fun attemptReconnect() = withContext(Dispatchers.IO) {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Timber.e("Max reconnection attempts reached")
            _connectionErrors.emit(
                LiveApiError.ConnectionError("Max reconnection attempts exceeded")
            )
            return@withContext
        }

        val delay = RECONNECT_BASE_DELAY_MS * (1 shl reconnectAttempts) // Exponential backoff
        Timber.d("Attempting reconnection in ${delay}ms (attempt ${reconnectAttempts + 1})")

        delay(delay)
        reconnectAttempts++

        connect().onSuccess {
            reconnectAttempts = 0
            setupSession()
        }.onFailure { error ->
            Timber.e("Reconnection attempt $reconnectAttempts failed: ${error.message}")
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                attemptReconnect()
            }
        }
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("WebSocket connection opened")
                isConnected.set(true)
                connectionState.value = SessionState.CONNECTED
                sessionStartTime = System.currentTimeMillis()
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.v("Received message: $text")
                launch {
                    incomingMessages.send(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("WebSocket closing: $code - $reason")
                connectionState.value = SessionState.DISCONNECTING
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("WebSocket closed: $code - $reason")
                isConnected.set(false)
                connectionState.value = SessionState.DISCONNECTED

                // Attempt reconnection for unexpected closures
                if (code != 1000 && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    launch { attemptReconnect() }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WebSocket failure")
                isConnected.set(false)
                connectionState.value = SessionState.ERROR

                launch {
                    _connectionErrors.emit(
                        LiveApiError.ConnectionError("WebSocket failure", t)
                    )
                }

                // Attempt reconnection
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    launch { attemptReconnect() }
                }
            }
        }
    }

    private fun startMessageProcessing() {
        messageProcessingJob = launch {
            for (message in incomingMessages) {
                try {
                    processIncomingMessage(message)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing incoming message")
                    _connectionErrors.emit(
                        LiveApiError.ProtocolError("Message processing error: ${e.message}")
                    )
                }
            }
        }
    }

    private fun startOutgoingMessageProcessing() {
        outgoingMessageJob = launch {
            for (message in outgoingMessages) {
                try {
                    val json = gson.toJson(message)
                    webSocket?.send(json)
                    Timber.v("Sent message: $json")
                } catch (e: Exception) {
                    Timber.e(e, "Error sending message")
                    _connectionErrors.emit(
                        LiveApiError.ProtocolError("Failed to send message: ${e.message}")
                    )
                }
            }
        }
    }

    private suspend fun processIncomingMessage(json: String) {
        val jsonElement = JsonParser.parseString(json).asJsonObject

        when {
            jsonElement.has("setupComplete") -> {
                val message = gson.fromJson(json, BidiGenerateContentSetupComplete::class.java)
                _setupComplete.emit(message)
            }

            jsonElement.has("serverContent") -> {
                val message = gson.fromJson(json, BidiGenerateContentServerContent::class.java)
                _serverContent.emit(message)
            }

            jsonElement.has("goAway") -> {
                val message = gson.fromJson(json, BidiGenerateContentGoAway::class.java)
                _goAway.emit(message)
                handleGoAway(message)
            }

            else -> {
                Timber.w("Unknown message type: $json")
            }
        }
    }

    private suspend fun handleGoAway(message: BidiGenerateContentGoAway) {
        Timber.w("Received GoAway: ${message.reason} - ${message.details}")

        // Give some time for graceful shutdown
        delay(GOAWAY_GRACE_PERIOD_MS)

        // Force disconnect if still connected
        if (isConnected.get()) {
            disconnect()
        }

        // Attempt reconnection for certain reasons
        if (message.reason.contains("timeout", ignoreCase = true) ||
            message.reason.contains("session_limit", ignoreCase = true)) {
            attemptReconnect()
        }
    }

    private fun setupSessionTimeout() {
        sessionTimeoutJob = launch {
            delay(SESSION_TIMEOUT_MS)

            if (isConnected.get()) {
                Timber.w("Session timeout reached, disconnecting")
                disconnect()
            }
        }
    }

    private suspend fun sendMessage(message: LiveApiMessage) {
        if (!isConnected.get()) {
            throw IllegalStateException("Not connected")
        }

        outgoingMessages.send(message)
    }

    private fun generateRequestId(): String {
        return "req_${requestIdCounter.incrementAndGet()}"
    }

    /**
     * Check if session is approaching timeout and needs renewal
     */
    fun isSessionNearTimeout(): Boolean {
        val elapsed = System.currentTimeMillis() - sessionStartTime
        return elapsed > SESSION_TIMEOUT_MS * 0.9 // 90% of timeout
    }

    /**
     * Get current session statistics
     */
    fun getSessionStats(): SessionStats {
        return SessionStats(
            sessionId = currentSession.get(),
            uptime = if (sessionStartTime > 0) System.currentTimeMillis() - sessionStartTime else 0,
            reconnectAttempts = reconnectAttempts,
            state = connectionState.value
        )
    }

    fun cleanup() {
        runBlocking {
            disconnect()
        }
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
    }
}

data class SessionStats(
    val sessionId: String?,
    val uptime: Long,
    val reconnectAttempts: Int,
    val state: SessionState
)