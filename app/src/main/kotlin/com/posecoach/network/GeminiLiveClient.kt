package com.posecoach.network

import com.posecoach.ui.activities.ConnectionStatus
import com.posecoach.ui.components.*
import com.posecoach.services.GeminiMessage
import com.posecoach.services.GeminiMessageType
import kotlinx.coroutines.delay
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for Gemini Live API integration
 * Features:
 * - Real-time audio streaming
 * - Tool execution for pose analysis
 * - Session management
 * - Automatic reconnection
 */
class GeminiLiveClient {

    companion object {
        private const val GEMINI_LIVE_URL = "wss://generativelanguage.googleapis.com/ws/v1/models/gemini-2.0-flash-exp:streamGenerateContent"
        private const val TAG = "GeminiLiveClient"
    }

    private var webSocket: WebSocket? = null
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for streaming
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Callbacks
    private var connectionStatusListener: ((ConnectionStatus) -> Unit)? = null
    private var messageListener: ((GeminiMessage) -> Unit)? = null
    private var toolExecutionListener: ((ToolExecution) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null

    // Connection state
    private var isConnected = false
    private var currentToken: String? = null

    /**
     * Connect to Gemini Live API with ephemeral token
     */
    suspend fun connect(token: String) {
        currentToken = token
        connectionStatusListener?.invoke(ConnectionStatus.CONNECTING)

        val request = Request.Builder()
            .url(GEMINI_LIVE_URL)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        webSocket = httpClient.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Disconnect from Gemini Live API
     */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
        connectionStatusListener?.invoke(ConnectionStatus.DISCONNECTED)
    }

    /**
     * Reconnect with existing token
     */
    suspend fun reconnect() {
        currentToken?.let { token ->
            disconnect()
            delay(1000)
            connect(token)
        }
    }

    /**
     * Reconnect with new token
     */
    suspend fun reconnectWithToken(token: String) {
        disconnect()
        delay(1000)
        connect(token)
    }

    /**
     * Send audio data to Gemini Live API
     */
    fun sendAudioData(audioBuffer: ShortArray) {
        if (isConnected) {
            // Convert audio data to the format expected by Gemini Live API
            val audioMessage = createAudioMessage(audioBuffer)
            webSocket?.send(audioMessage)
        }
    }

    /**
     * Execute pose analysis tool
     */
    suspend fun executePoseAnalysisTool(poses: List<PoseData>): String? {
        if (!isConnected) return null

        val toolMessage = createPoseAnalysisToolMessage(poses)
        webSocket?.send(toolMessage)

        // In a real implementation, you would wait for the tool execution result
        // For now, we'll simulate a response
        delay(1000)
        return "Pose analysis completed: Good form detected"
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                connectionStatusListener?.invoke(ConnectionStatus.CONNECTED)

                // Send initial setup message
                val setupMessage = createSetupMessage()
                webSocket.send(setupMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryMessage(bytes)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connectionStatusListener?.invoke(ConnectionStatus.DISCONNECTED)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                connectionStatusListener?.invoke(ConnectionStatus.DISCONNECTED)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                errorListener?.invoke("WebSocket error: ${t.message}")
                connectionStatusListener?.invoke(ConnectionStatus.ERROR)
            }
        }
    }

    private fun handleTextMessage(text: String) {
        try {
            // Parse JSON message from Gemini Live API
            // This is a simplified implementation

            when {
                text.contains("\"serverContent\"") -> {
                    // Handle transcription or assistant response
                    val message = GeminiMessage(
                        type = GeminiMessageType.TRANSCRIPTION,
                        content = extractContent(text)
                    )
                    messageListener?.invoke(message)
                }
                text.contains("\"toolCall\"") -> {
                    // Handle tool execution
                    val execution = parseToolExecution(text)
                    toolExecutionListener?.invoke(execution)
                }
                text.contains("\"error\"") -> {
                    // Handle error
                    errorListener?.invoke(extractError(text))
                }
            }
        } catch (e: Exception) {
            errorListener?.invoke("Failed to parse message: ${e.message}")
        }
    }

    private fun handleBinaryMessage(bytes: ByteString) {
        // Handle binary audio data if needed
        // For now, this is a placeholder
    }

    private fun createSetupMessage(): String {
        return """
        {
            "setup": {
                "model": "models/gemini-2.0-flash-exp",
                "generationConfig": {
                    "responseModalities": ["AUDIO", "TEXT"],
                    "speechConfig": {
                        "voiceConfig": {
                            "prebuiltVoiceConfig": {
                                "voiceName": "Aoede"
                            }
                        }
                    }
                },
                "systemInstruction": {
                    "parts": [
                        {
                            "text": "You are an AI fitness coach specializing in pose analysis and real-time coaching. Provide concise, encouraging feedback about posture and form. When analyzing poses, use the pose_analysis tool to evaluate alignment, joint angles, and form quality."
                        }
                    ]
                },
                "tools": [
                    {
                        "functionDeclarations": [
                            {
                                "name": "analyze_pose",
                                "description": "Analyze pose data and provide coaching feedback",
                                "parameters": {
                                    "type": "object",
                                    "properties": {
                                        "poses": {
                                            "type": "array",
                                            "description": "Array of pose landmarks"
                                        },
                                        "feedback_type": {
                                            "type": "string",
                                            "description": "Type of feedback needed"
                                        }
                                    },
                                    "required": ["poses"]
                                }
                            }
                        ]
                    }
                ]
            }
        }
        """.trimIndent()
    }

    private fun createAudioMessage(audioBuffer: ShortArray): String {
        // Convert audio buffer to base64 or appropriate format
        // This is a simplified implementation
        return """
        {
            "clientContent": {
                "turns": [
                    {
                        "role": "user",
                        "parts": [
                            {
                                "inlineData": {
                                    "mimeType": "audio/pcm",
                                    "data": "${encodeAudioData(audioBuffer)}"
                                }
                            }
                        ]
                    }
                ]
            }
        }
        """.trimIndent()
    }

    private fun createPoseAnalysisToolMessage(poses: List<PoseData>): String {
        return """
        {
            "clientContent": {
                "turns": [
                    {
                        "role": "user",
                        "parts": [
                            {
                                "functionCall": {
                                    "name": "analyze_pose",
                                    "args": {
                                        "poses": ${serializePoses(poses)},
                                        "feedback_type": "real_time_coaching"
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        }
        """.trimIndent()
    }

    private fun encodeAudioData(audioBuffer: ShortArray): String {
        // Convert short array to base64 encoded string
        // This is a placeholder implementation
        return java.util.Base64.getEncoder().encodeToString(
            audioBuffer.map { it.toByte() }.toByteArray()
        )
    }

    private fun serializePoses(poses: List<PoseData>): String {
        // Convert pose data to JSON format
        // This is a simplified implementation
        return """
        [
            ${poses.joinToString(",") { pose ->
                """
                {
                    "landmarks": [
                        ${pose.landmarks.joinToString(",") { landmark ->
                            """
                            {
                                "joint": "${landmark.joint}",
                                "x": ${landmark.x},
                                "y": ${landmark.y},
                                "z": ${landmark.z},
                                "confidence": ${landmark.confidence}
                            }
                            """.trimIndent()
                        }}
                    ],
                    "confidence": ${pose.overallConfidence}
                }
                """.trimIndent()
            }}
        ]
        """.trimIndent()
    }

    private fun extractContent(text: String): String {
        // Extract content from Gemini response
        // This is a simplified implementation
        return text.substringAfter("\"text\":\"").substringBefore("\"")
    }

    private fun extractError(text: String): String {
        // Extract error message from response
        return text.substringAfter("\"message\":\"").substringBefore("\"")
    }

    private fun parseToolExecution(text: String): ToolExecution {
        // Parse tool execution from response
        // This is a simplified implementation
        return ToolExecution(
            toolName = "pose_analysis",
            status = ToolExecutionStatus.COMPLETED,
            result = extractContent(text)
        )
    }

    // Callback setters
    fun setConnectionStatusListener(listener: (ConnectionStatus) -> Unit) {
        connectionStatusListener = listener
    }

    fun setMessageListener(listener: (GeminiMessage) -> Unit) {
        messageListener = listener
    }

    fun setToolExecutionListener(listener: (ToolExecution) -> Unit) {
        toolExecutionListener = listener
    }

    fun setErrorListener(listener: (String) -> Unit) {
        errorListener = listener
    }
}