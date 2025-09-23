package com.posecoach.app.livecoach.models

import com.google.gson.annotations.SerializedName
import com.posecoach.corepose.models.PoseLandmarkResult
// Compatibility re-exports for moved models
import com.posecoach.app.livecoach.websocket.ConnectionState
import com.posecoach.app.livecoach.websocket.LiveApiConfig as WebSocketLiveApiConfig

data class LiveApiConfig(
    val model: String = "models/gemini-2.0-flash-exp",
    val generationConfig: GenerationConfig = GenerationConfig(),
    val systemInstruction: String = "You are a fitness coach providing real-time pose feedback.",
    val realtimeInputConfig: RealtimeInputConfig = RealtimeInputConfig()
)

data class GenerationConfig(
    val maxOutputTokens: Int = 8192,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val responseModalities: List<String> = listOf("TEXT", "AUDIO"),
    val speechConfig: SpeechConfig = SpeechConfig()
)

data class SpeechConfig(
    val voiceConfig: VoiceConfig = VoiceConfig()
)

data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig = PrebuiltVoiceConfig()
)

data class PrebuiltVoiceConfig(
    val voiceName: String = "Charon"
)

data class RealtimeInputConfig(
    val mediaResolution: String = "LOW"
)

// WebSocket Message Types
sealed class LiveApiMessage {
    data class Setup(
        val model: String,
        val generationConfig: GenerationConfig,
        val systemInstruction: String,
        val realtimeInputConfig: RealtimeInputConfig
    ) : LiveApiMessage()

    data class RealtimeInput(
        val mediaChunks: List<MediaChunk>? = null,
        val audioStreamEnd: Boolean? = null,
        val text: String? = null
    ) : LiveApiMessage()

    data class ToolResponse(
        val functionResponses: List<FunctionResponse>
    ) : LiveApiMessage()
}

data class MediaChunk(
    val mimeType: String,
    val data: String // Base64 encoded
)

data class FunctionResponse(
    val name: String,
    val response: Map<String, Any>
)

// Server Response Types
sealed class LiveApiResponse {
    data class SetupComplete(val setupComplete: Boolean) : LiveApiResponse()

    data class ServerContent(
        val modelTurn: Content? = null,
        val turnComplete: Boolean = false,
        val interrupted: Boolean = false,
        val inputTranscription: Transcription? = null,
        val outputTranscription: Transcription? = null
    ) : LiveApiResponse()

    data class ToolCall(
        val functionCalls: List<FunctionCall>
    ) : LiveApiResponse()

    data class ToolCallCancellation(
        val ids: List<String>
    ) : LiveApiResponse()
}

data class Content(
    val parts: List<Part>
)

sealed class Part {
    data class TextPart(val text: String) : Part()
    data class InlineDataPart(
        val mimeType: String,
        val data: String
    ) : Part()
}

data class Transcription(
    val transcribedText: String
)

data class FunctionCall(
    val name: String,
    val args: Map<String, Any>,
    val id: String
)

// Live Coach specific models
data class PoseSnapshot(
    val imageData: String, // Base64 encoded low-res image
    val landmarks: PoseLandmarkResult,
    val timestamp: Long
)

data class AudioChunk(
    val data: ByteArray,
    val timestamp: Long,
    val sampleRate: Int = 16000
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioChunk
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false
        if (sampleRate != other.sampleRate) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sampleRate
        return result
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class SessionState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isRecording: Boolean = false,
    val isSpeaking: Boolean = false,
    val lastError: String? = null,
    val sessionId: String? = null,
    val retryCount: Int = 0,
    val lastStateChange: Long? = null,
    val lastRetryAttempt: Long? = null,
    val batteryOptimized: Boolean = false
)
// Additional models for performance testing
data class VoiceResponse(
    val text: String,
    val confidence: Float,
    val duration: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class AudioFrame(
    val data: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioFrame

        if (!data.contentEquals(other.data)) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
