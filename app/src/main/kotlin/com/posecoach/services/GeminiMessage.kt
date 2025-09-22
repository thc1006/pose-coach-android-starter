package com.posecoach.services

/**
 * Message types for Gemini Live API communication
 */
enum class GeminiMessageType {
    TRANSCRIPTION,
    ASSISTANT_RESPONSE,
    TOOL_CALL,
    AUDIO_DATA,
    ERROR
}

/**
 * Data class representing a message from/to Gemini Live API
 */
data class GeminiMessage(
    val type: GeminiMessageType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)