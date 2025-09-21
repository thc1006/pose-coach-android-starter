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

package com.posecoach.gemini.live.models

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * Data models for Gemini Live API following official specifications
 * Reference: https://ai.google.dev/api/live
 */

// Base message types
@Serializable
sealed class LiveApiMessage {
    abstract val requestId: String?
}

// Client setup message
@Serializable
data class BidiGenerateContentSetup(
    @SerializedName("request_id") override val requestId: String?,
    @SerializedName("setup") val setup: SetupConfig
) : LiveApiMessage()

@Serializable
data class SetupConfig(
    @SerializedName("model") val model: String = "models/gemini-2.0-flash-exp",
    @SerializedName("generation_config") val generationConfig: GenerationConfig,
    @SerializedName("system_instruction") val systemInstruction: Content? = null,
    @SerializedName("tools") val tools: List<Tool>? = null
)

@Serializable
data class GenerationConfig(
    @SerializedName("response_modalities") val responseModalities: List<String> = listOf("AUDIO"),
    @SerializedName("speech_config") val speechConfig: SpeechConfig? = null,
    @SerializedName("candidate_count") val candidateCount: Int = 1,
    @SerializedName("max_output_tokens") val maxOutputTokens: Int? = null,
    @SerializedName("temperature") val temperature: Double? = null,
    @SerializedName("top_p") val topP: Double? = null,
    @SerializedName("top_k") val topK: Int? = null,
    @SerializedName("presence_penalty") val presencePenalty: Double? = null,
    @SerializedName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerializedName("response_schema") val responseSchema: Schema? = null
)

@Serializable
data class SpeechConfig(
    @SerializedName("voice_config") val voiceConfig: VoiceConfig
)

@Serializable
data class VoiceConfig(
    @SerializedName("prebuilt_voice_config") val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

@Serializable
data class PrebuiltVoiceConfig(
    @SerializedName("voice_name") val voiceName: String = "Aoede"
)

// Client content message
@Serializable
data class BidiGenerateContentClientContent(
    @SerializedName("request_id") override val requestId: String?,
    @SerializedName("client_content") val clientContent: ClientContent
) : LiveApiMessage()

@Serializable
data class ClientContent(
    @SerializedName("turns") val turns: List<Turn>,
    @SerializedName("turn_complete") val turnComplete: Boolean
)

@Serializable
data class Turn(
    @SerializedName("role") val role: String, // "user" or "model"
    @SerializedName("parts") val parts: List<Part>
)

@Serializable
sealed class Part

@Serializable
data class TextPart(
    @SerializedName("text") val text: String
) : Part()

@Serializable
data class InlineDataPart(
    @SerializedName("inline_data") val inlineData: InlineData
) : Part()

@Serializable
data class InlineData(
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("data") val data: String // Base64 encoded
)

@Serializable
data class FunctionCallPart(
    @SerializedName("function_call") val functionCall: FunctionCall
) : Part()

@Serializable
data class FunctionResponsePart(
    @SerializedName("function_response") val functionResponse: FunctionResponse
) : Part()

// Real-time input message
@Serializable
data class BidiGenerateContentRealtimeInput(
    @SerializedName("request_id") override val requestId: String?,
    @SerializedName("realtime_input") val realtimeInput: RealtimeInput
) : LiveApiMessage()

@Serializable
data class RealtimeInput(
    @SerializedName("media_chunks") val mediaChunks: List<MediaChunk>
)

@Serializable
data class MediaChunk(
    @SerializedName("mime_type") val mimeType: String, // "audio/pcm"
    @SerializedName("data") val data: String // Base64 encoded PCM data
)

// Tool response message
@Serializable
data class BidiGenerateContentToolResponse(
    @SerializedName("request_id") override val requestId: String?,
    @SerializedName("tool_response") val toolResponse: ToolResponse
) : LiveApiMessage()

@Serializable
data class ToolResponse(
    @SerializedName("function_responses") val functionResponses: List<FunctionResponse>
)

@Serializable
data class FunctionResponse(
    @SerializedName("name") val name: String,
    @SerializedName("response") val response: Map<String, Any>
)

@Serializable
data class FunctionCall(
    @SerializedName("name") val name: String,
    @SerializedName("args") val args: Map<String, Any>
)

// Server response messages
@Serializable
data class BidiGenerateContentServerContent(
    @SerializedName("server_content") val serverContent: ServerContent,
    @SerializedName("request_id") val requestId: String?
)

@Serializable
data class ServerContent(
    @SerializedName("model_turn") val modelTurn: ModelTurn?,
    @SerializedName("turn_complete") val turnComplete: Boolean,
    @SerializedName("grounding_metadata") val groundingMetadata: GroundingMetadata? = null
)

@Serializable
data class ModelTurn(
    @SerializedName("parts") val parts: List<Part>,
    @SerializedName("role") val role: String = "model"
)

@Serializable
data class GroundingMetadata(
    @SerializedName("grounding_chunks") val groundingChunks: List<GroundingChunk>
)

@Serializable
data class GroundingChunk(
    @SerializedName("web") val web: WebGroundingChunk? = null
)

@Serializable
data class WebGroundingChunk(
    @SerializedName("uri") val uri: String,
    @SerializedName("title") val title: String
)

// Setup complete message
@Serializable
data class BidiGenerateContentSetupComplete(
    @SerializedName("request_id") val requestId: String?
)

// GoAway message for session termination
@Serializable
data class BidiGenerateContentGoAway(
    @SerializedName("request_id") val requestId: String?,
    @SerializedName("reason") val reason: String,
    @SerializedName("details") val details: String? = null
)

// Tool definitions
@Serializable
data class Tool(
    @SerializedName("function_declarations") val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class FunctionDeclaration(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("parameters") val parameters: Schema
)

@Serializable
data class Schema(
    @SerializedName("type") val type: String,
    @SerializedName("properties") val properties: Map<String, Property>? = null,
    @SerializedName("required") val required: List<String>? = null,
    @SerializedName("items") val items: Property? = null
)

@Serializable
data class Property(
    @SerializedName("type") val type: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enum") val enum: List<String>? = null,
    @SerializedName("format") val format: String? = null,
    @SerializedName("properties") val properties: Map<String, Property>? = null,
    @SerializedName("items") val items: Property? = null
)

@Serializable
data class Content(
    @SerializedName("role") val role: String,
    @SerializedName("parts") val parts: List<Part>
)

// Audio configuration constants
object AudioConfig {
    const val SAMPLE_RATE_INPUT = 16000 // 16kHz for input
    const val SAMPLE_RATE_OUTPUT = 24000 // 24kHz for output
    const val CHANNELS = 1 // Mono
    const val BITS_PER_SAMPLE = 16 // 16-bit PCM
    const val FRAME_SIZE_MS = 20 // 20ms frames
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    const val SAMPLES_PER_FRAME = SAMPLE_RATE_INPUT * FRAME_SIZE_MS / 1000
    const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE * CHANNELS

    const val MIME_TYPE_AUDIO_PCM = "audio/pcm"
    const val ENCODING_BASE64 = "base64"
}

// Session configuration
data class LiveApiConfig(
    val model: String = "models/gemini-2.0-flash-exp",
    val voiceName: String = "Aoede",
    val responseModalities: List<String> = listOf("AUDIO"),
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val systemInstruction: String? = null,
    val tools: List<Tool>? = null,
    val enableVoiceActivityDetection: Boolean = true,
    val audioBufferSizeMs: Int = 100,
    val reconnectMaxAttempts: Int = 3,
    val reconnectDelayMs: Long = 1000
)

// Session state
enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SETUP_PENDING,
    SETUP_COMPLETE,
    ACTIVE,
    DISCONNECTING,
    ERROR
}

// Error types
sealed class LiveApiError : Exception() {
    data class ConnectionError(override val message: String, override val cause: Throwable? = null) : LiveApiError()
    data class AuthenticationError(override val message: String) : LiveApiError()
    data class SessionError(override val message: String) : LiveApiError()
    data class AudioError(override val message: String, override val cause: Throwable? = null) : LiveApiError()
    data class ProtocolError(override val message: String) : LiveApiError()
    data class RateLimitError(override val message: String, val retryAfterMs: Long? = null) : LiveApiError()
}

// Utility functions for data conversion
fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)

fun createPCMMediaChunk(audioData: ByteArray): MediaChunk {
    return MediaChunk(
        mimeType = AudioConfig.MIME_TYPE_AUDIO_PCM,
        data = audioData.toBase64()
    )
}

// Response parsing utilities
object LiveApiMessageParser {
    fun parseServerMessage(json: String): Any? {
        // Implementation would use gson to parse different message types
        // Based on the presence of specific fields
        return null // Placeholder
    }
}