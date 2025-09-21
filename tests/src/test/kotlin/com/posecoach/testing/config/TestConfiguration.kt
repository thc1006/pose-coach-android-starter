package com.posecoach.testing.config

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import java.util.concurrent.TimeUnit

/**
 * Centralized test configuration and mock services for Gemini Live API testing.
 *
 * Provides:
 * - Mock WebSocket server setup
 * - Test data generators
 * - Configuration constants
 * - Mock service implementations
 */
object TestConfiguration {

    // Gemini Live API Configuration
    object GeminiApi {
        const val MODEL_NAME = "models/gemini-2.0-flash-exp"
        const val BASE_URL = "https://generativelanguage.googleapis.com"
        const val WEBSOCKET_ENDPOINT = "/v1beta/models/$MODEL_NAME:streamGenerateContent"

        // Session limits
        const val SESSION_DURATION_MINUTES = 15L
        const val CONNECTION_LIFECYCLE_MINUTES = 10L
        const val NEW_SESSION_WINDOW_MINUTES = 1L

        // Token configuration
        const val TOKEN_VALIDITY_MINUTES = 30L
        const val TOKEN_REFRESH_THRESHOLD_MINUTES = 5L

        // Audio configuration
        const val AUDIO_SAMPLE_RATE = 16000
        const val AUDIO_CHANNELS = 1
        const val AUDIO_BIT_DEPTH = 16

        // Performance targets
        const val TARGET_LATENCY_MS = 500L
        const val MAX_ACCEPTABLE_LATENCY_MS = 1000L
        const val AUDIO_PROCESSING_TARGET_MS = 100L
        const val TOOL_EXECUTION_TARGET_MS = 1000L
    }

    // Test Environment Configuration
    object TestEnv {
        const val MOCK_SERVER_PORT = 8080
        const val TEST_TIMEOUT_SECONDS = 30L
        const val CONCURRENT_TEST_THREADS = 10
        const val LOAD_TEST_USERS = 50
        const val STRESS_TEST_DURATION_MINUTES = 60L
    }

    // Mock WebSocket Server
    class MockGeminiWebSocketServer {
        private val mockServer = MockWebServer()

        fun start(): String {
            // Setup default responses
            setupMockResponses()
            mockServer.start(TestEnv.MOCK_SERVER_PORT)
            return mockServer.url("/").toString()
        }

        fun stop() {
            mockServer.shutdown()
        }

        private fun setupMockResponses() {
            // Setup complete response
            mockServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody("""{"setupComplete": {}}""")
                .setHeader("Content-Type", "application/json"))

            // Server content response
            mockServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody("""{"serverContent": {"modelTurn": {"parts": [{"text": "I can see you're performing a squat. Let me analyze your form..."}]}, "turnComplete": false}}""")
                .setHeader("Content-Type", "application/json"))

            // Tool call response
            mockServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody("""{"toolCall": {"functionCalls": [{"name": "analyze_pose_form", "id": "call_123", "args": {"pose_data": "base64_encoded_pose_landmarks", "exercise_type": "squat"}}]}}""")
                .setHeader("Content-Type", "application/json"))

            // Token refresh response
            mockServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody("""{"ephemeral_token": "new_token_${System.currentTimeMillis()}", "expires_in": 1800}""")
                .setHeader("Content-Type", "application/json"))
        }

        fun addCustomResponse(response: MockResponse) {
            mockServer.enqueue(response)
        }
    }

    // Test Data Generators
    object TestDataGenerators {

        fun generateEphemeralToken(): String {
            return "test_token_${System.currentTimeMillis()}_${(1000..9999).random()}"
        }

        fun generateSessionId(): String {
            return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
        }

        fun generatePoseLandmarks(count: Int = 33): List<Map<String, Any>> {
            return (0 until count).map { index ->
                mapOf(
                    "x" to (0.2 + (index % 5) * 0.15),
                    "y" to (0.1 + (index / 5) * 0.12),
                    "z" to (-0.1 + (index % 3) * 0.05),
                    "visibility" to (0.7 + (index % 2) * 0.3)
                )
            }
        }

        fun generateAudioChunk(sizeBytes: Int = 2048, frequency: Float = 440.0f): ByteArray {
            val samples = sizeBytes / 2 // 16-bit samples
            val audioData = ByteArray(sizeBytes)

            for (i in 0 until samples) {
                val time = i.toFloat() / GeminiApi.AUDIO_SAMPLE_RATE
                val amplitude = kotlin.math.sin(2 * kotlin.math.PI * frequency * time)
                val sample = (amplitude * Short.MAX_VALUE).toInt().toShort()

                // Little-endian encoding
                audioData[i * 2] = (sample.toInt() and 0xFF).toByte()
                audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }

            return audioData
        }

        fun generateToolDeclaration(toolName: String = "analyze_pose_form"): Map<String, Any> {
            return mapOf(
                "name" to toolName,
                "description" to "Analyze pose form and provide feedback for exercise improvement",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "pose_landmarks" to mapOf(
                            "type" to "array",
                            "description" to "Array of pose landmarks with x, y, z coordinates and visibility",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "x" to mapOf("type" to "number", "minimum" to 0.0, "maximum" to 1.0),
                                    "y" to mapOf("type" to "number", "minimum" to 0.0, "maximum" to 1.0),
                                    "z" to mapOf("type" to "number"),
                                    "visibility" to mapOf("type" to "number", "minimum" to 0.0, "maximum" to 1.0)
                                ),
                                "required" to listOf("x", "y", "z", "visibility")
                            )
                        ),
                        "exercise_type" to mapOf(
                            "type" to "string",
                            "description" to "Type of exercise being performed",
                            "enum" to listOf("squat", "pushup", "deadlift", "plank", "bicep_curl", "overhead_press")
                        ),
                        "user_height" to mapOf(
                            "type" to "number",
                            "description" to "User height in centimeters",
                            "minimum" to 100.0,
                            "maximum" to 250.0
                        )
                    ),
                    "required" to listOf("pose_landmarks", "exercise_type")
                )
            )
        }

        fun generateSetupMessage(): Map<String, Any> {
            return mapOf(
                "setup" to mapOf(
                    "model" to GeminiApi.MODEL_NAME,
                    "generationConfig" to mapOf(
                        "responseModalities" to listOf("AUDIO", "TEXT"),
                        "speechConfig" to mapOf(
                            "voiceConfig" to mapOf(
                                "prebuiltVoiceConfig" to mapOf(
                                    "voiceName" to "Aoede"
                                )
                            )
                        )
                    ),
                    "systemInstruction" to mapOf(
                        "parts" to listOf(
                            mapOf("text" to "You are a helpful pose analysis assistant. Analyze poses and provide constructive feedback for form improvement.")
                        )
                    ),
                    "tools" to listOf(
                        mapOf(
                            "functionDeclarations" to listOf(generateToolDeclaration())
                        )
                    )
                )
            )
        }

        fun generateClientContentMessage(text: String, includeImage: Boolean = false): Map<String, Any> {
            val parts = mutableListOf<Map<String, Any>>()
            parts.add(mapOf("text" to text))

            if (includeImage) {
                parts.add(mapOf(
                    "inlineData" to mapOf(
                        "mimeType" to "image/jpeg",
                        "data" to generateMockImageData()
                    )
                ))
            }

            return mapOf(
                "clientContent" to mapOf(
                    "turns" to listOf(
                        mapOf("parts" to parts)
                    )
                )
            )
        }

        fun generateRealtimeInputMessage(audioData: ByteArray): Map<String, Any> {
            return mapOf(
                "realtimeInput" to mapOf(
                    "mediaChunks" to listOf(
                        mapOf(
                            "mimeType" to "audio/pcm",
                            "data" to android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
                        )
                    )
                )
            )
        }

        fun generateToolResponseMessage(functionName: String, response: Map<String, Any>): Map<String, Any> {
            return mapOf(
                "toolResponse" to mapOf(
                    "functionResponses" to listOf(
                        mapOf(
                            "name" to functionName,
                            "response" to response
                        )
                    )
                )
            )
        }

        private fun generateMockImageData(): String {
            // Simple JPEG header for testing
            val jpegHeader = byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00,
                0xFF.toByte(), 0xDB.toByte(), 0x00, 0x43, 0x00,
                // Simplified quantization table and data
                0xFF.toByte(), 0xD9.toByte() // JPEG end marker
            )
            return android.util.Base64.encodeToString(jpegHeader, android.util.Base64.NO_WRAP)
        }
    }

    // Mock Service Implementations
    class MockAuthService {
        private val validTokens = mutableSetOf<String>()
        private val tokenExpiryTimes = mutableMapOf<String, Long>()

        fun generateToken(): String {
            val token = TestDataGenerators.generateEphemeralToken()
            validTokens.add(token)
            tokenExpiryTimes[token] = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(GeminiApi.TOKEN_VALIDITY_MINUTES)
            return token
        }

        fun validateToken(token: String): Boolean {
            val expiryTime = tokenExpiryTimes[token] ?: return false
            return validTokens.contains(token) && System.currentTimeMillis() < expiryTime
        }

        fun refreshToken(oldToken: String): String? {
            return if (validateToken(oldToken)) {
                val newToken = generateToken()
                invalidateToken(oldToken)
                newToken
            } else null
        }

        fun invalidateToken(token: String) {
            validTokens.remove(token)
            tokenExpiryTimes.remove(token)
        }
    }

    class MockSessionService {
        private val activeSessions = mutableMapOf<String, SessionInfo>()

        data class SessionInfo(
            val sessionId: String,
            val startTime: Long,
            val token: String,
            val context: MutableMap<String, Any> = mutableMapOf()
        )

        fun createSession(token: String): String {
            val sessionId = TestDataGenerators.generateSessionId()
            activeSessions[sessionId] = SessionInfo(
                sessionId = sessionId,
                startTime = System.currentTimeMillis(),
                token = token
            )
            return sessionId
        }

        fun getSession(sessionId: String): SessionInfo? {
            return activeSessions[sessionId]
        }

        fun isSessionValid(sessionId: String): Boolean {
            val session = activeSessions[sessionId] ?: return false
            val sessionAge = System.currentTimeMillis() - session.startTime
            return sessionAge < TimeUnit.MINUTES.toMillis(GeminiApi.SESSION_DURATION_MINUTES)
        }

        fun updateSessionContext(sessionId: String, key: String, value: Any) {
            activeSessions[sessionId]?.context?.put(key, value)
        }

        fun endSession(sessionId: String) {
            activeSessions.remove(sessionId)
        }

        fun cleanupExpiredSessions() {
            val currentTime = System.currentTimeMillis()
            val expiredSessions = activeSessions.filter { (_, session) ->
                currentTime - session.startTime > TimeUnit.MINUTES.toMillis(GeminiApi.SESSION_DURATION_MINUTES)
            }
            expiredSessions.keys.forEach { activeSessions.remove(it) }
        }
    }

    class MockPoseAnalysisService {
        fun analyzePose(landmarks: List<Map<String, Any>>, exerciseType: String): Map<String, Any> {
            // Simulate pose analysis
            val formScore = when (exerciseType.lowercase()) {
                "squat" -> 85.0 + (landmarks.size % 10)
                "pushup" -> 78.0 + (landmarks.hashCode() % 15)
                "deadlift" -> 92.0 + (exerciseType.hashCode() % 8)
                else -> 75.0
            }

            val feedback = generateFeedback(exerciseType, formScore)
            val improvements = generateImprovements(exerciseType, formScore)
            val jointAngles = generateJointAngles(exerciseType)

            return mapOf(
                "form_score" to formScore,
                "feedback" to feedback,
                "improvements" to improvements,
                "joint_angles" to jointAngles,
                "exercise_type" to exerciseType,
                "analysis_timestamp" to System.currentTimeMillis(),
                "landmark_count" to landmarks.size
            )
        }

        private fun generateFeedback(exerciseType: String, formScore: Double): String {
            return when (exerciseType.lowercase()) {
                "squat" -> when {
                    formScore >= 90 -> "Excellent squat form! Keep up the great work."
                    formScore >= 80 -> "Good squat form. Focus on keeping your chest up and knees tracking over toes."
                    formScore >= 70 -> "Decent squat form. Work on going deeper and maintaining a neutral spine."
                    else -> "Squat form needs improvement. Focus on proper hip hinge and knee alignment."
                }
                "pushup" -> when {
                    formScore >= 90 -> "Perfect pushup form! Your body position is excellent."
                    formScore >= 80 -> "Good pushup form. Maintain a straight line from head to heels."
                    formScore >= 70 -> "Decent pushup form. Focus on controlling the descent."
                    else -> "Pushup form needs work. Keep your core engaged and avoid sagging hips."
                }
                "deadlift" -> when {
                    formScore >= 90 -> "Outstanding deadlift technique! Perfect hip hinge and bar path."
                    formScore >= 80 -> "Good deadlift form. Keep the bar close to your body throughout the lift."
                    formScore >= 70 -> "Decent deadlift form. Focus on maintaining a neutral spine."
                    else -> "Deadlift form needs attention. Work on proper hip hinge and knee drive."
                }
                else -> "Good effort! Keep focusing on proper form and controlled movements."
            }
        }

        private fun generateImprovements(exerciseType: String, formScore: Double): List<String> {
            val improvements = mutableListOf<String>()

            if (formScore < 85) {
                improvements.add("Focus on controlled movement tempo")
            }
            if (formScore < 80) {
                improvements.add("Improve range of motion")
            }
            if (formScore < 75) {
                improvements.add("Work on body alignment")
            }

            when (exerciseType.lowercase()) {
                "squat" -> {
                    if (formScore < 90) improvements.add("Keep knees tracking over toes")
                    if (formScore < 80) improvements.add("Go deeper into the squat")
                    if (formScore < 70) improvements.add("Maintain chest up position")
                }
                "pushup" -> {
                    if (formScore < 90) improvements.add("Maintain straight body line")
                    if (formScore < 80) improvements.add("Control the descent phase")
                    if (formScore < 70) improvements.add("Engage core muscles")
                }
                "deadlift" -> {
                    if (formScore < 90) improvements.add("Keep bar path vertical")
                    if (formScore < 80) improvements.add("Drive through your heels")
                    if (formScore < 70) improvements.add("Maintain shoulder blade position")
                }
            }

            return improvements.ifEmpty { listOf("Keep up the excellent form!") }
        }

        private fun generateJointAngles(exerciseType: String): Map<String, Double> {
            return when (exerciseType.lowercase()) {
                "squat" -> mapOf(
                    "knee_angle" to (90.0 + kotlin.random.Random.nextDouble(-10.0, 10.0)),
                    "hip_angle" to (80.0 + kotlin.random.Random.nextDouble(-7.5, 7.5)),
                    "ankle_angle" to (70.0 + kotlin.random.Random.nextDouble(-5.0, 5.0))
                )
                "pushup" -> mapOf(
                    "elbow_angle" to (90.0 + kotlin.random.Random.nextDouble(-10.0, 10.0)),
                    "shoulder_angle" to (45.0 + kotlin.random.Random.nextDouble(-7.5, 7.5)),
                    "wrist_angle" to (180.0 + kotlin.random.Random.nextDouble(-5.0, 5.0))
                )
                "deadlift" -> mapOf(
                    "hip_angle" to (85.0 + kotlin.random.Random.nextDouble(-10.0, 10.0)),
                    "knee_angle" to (120.0 + kotlin.random.Random.nextDouble(-15.0, 15.0)),
                    "spine_angle" to (15.0 + kotlin.random.Random.nextDouble(-5.0, 5.0))
                )
                else -> mapOf(
                    "primary_joint" to (90.0 + kotlin.random.Random.nextDouble(-15.0, 15.0))
                )
            }
        }
    }

    // Test Utilities
    object TestUtils {
        fun waitForCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
            val endTime = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < endTime) {
                if (condition()) {
                    return true
                }
                Thread.sleep(100)
            }
            return false
        }

        fun measureExecutionTime(operation: () -> Unit): Long {
            val startTime = System.currentTimeMillis()
            operation()
            return System.currentTimeMillis() - startTime
        }

        fun createMockWebSocketResponse(type: String, content: Map<String, Any>): String {
            return com.google.gson.Gson().toJson(mapOf(type to content))
        }

        fun validateJsonStructure(json: String, requiredFields: List<String>): Boolean {
            return try {
                val jsonObject = com.google.gson.JsonParser.parseString(json).asJsonObject
                requiredFields.all { jsonObject.has(it) }
            } catch (e: Exception) {
                false
            }
        }

        fun calculateLatencyPercentile(latencies: List<Long>, percentile: Double): Long {
            val sorted = latencies.sorted()
            val index = ((sorted.size - 1) * percentile).toInt()
            return sorted[index]
        }

        fun generateRandomString(length: Int): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..length).map { chars.random() }.joinToString("")
        }

        fun simulateNetworkLatency(minMs: Long, maxMs: Long) {
            val latency = kotlin.random.Random.nextLong(minMs, maxMs)
            Thread.sleep(latency)
        }

        fun createTemporaryAudioFile(durationSeconds: Int): ByteArray {
            val sampleRate = GeminiApi.AUDIO_SAMPLE_RATE
            val samples = sampleRate * durationSeconds
            return TestDataGenerators.generateAudioChunk(samples * 2, 440.0f)
        }
    }
}