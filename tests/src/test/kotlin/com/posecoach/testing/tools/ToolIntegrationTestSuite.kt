package com.posecoach.testing.tools

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive test suite for tool integration compliance with Gemini Live API specifications.
 *
 * Tests cover:
 * - Pose analysis tool function declarations
 * - Real-time tool execution testing
 * - NON_BLOCKING scheduling validation
 * - Tool response handling
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ToolIntegrationTestSuite {

    private lateinit var testScope: TestScope
    private lateinit var toolManager: TestToolManager
    private lateinit var gson: Gson
    private lateinit var toolCallFlow: MutableSharedFlow<ToolCall>
    private lateinit var toolResponseFlow: MutableSharedFlow<ToolResponse>
    private lateinit var toolMetricsFlow: MutableStateFlow<ToolMetrics>

    data class ToolCall(
        val id: String,
        val name: String,
        val args: Map<String, Any>,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ToolResponse(
        val callId: String,
        val name: String,
        val response: Map<String, Any>,
        val executionTimeMs: Long,
        val success: Boolean,
        val error: String? = null
    )

    data class ToolMetrics(
        val totalCalls: Int = 0,
        val successfulCalls: Int = 0,
        val averageExecutionTime: Double = 0.0,
        val errorRate: Double = 0.0,
        val concurrentExecutions: Int = 0
    )

    data class PoseLandmark(
        val x: Float,
        val y: Float,
        val z: Float,
        val visibility: Float
    )

    companion object {
        const val MAX_CONCURRENT_TOOLS = 5
        const val TOOL_TIMEOUT_MS = 5000L
        const val POSE_ANALYSIS_MAX_TIME_MS = 1000L
    }

    @Before
    fun setup() {
        testScope = TestScope()
        gson = Gson()
        toolCallFlow = MutableSharedFlow()
        toolResponseFlow = MutableSharedFlow()
        toolMetricsFlow = MutableStateFlow(ToolMetrics())
        toolManager = TestToolManager(
            toolCallFlow = toolCallFlow,
            toolResponseFlow = toolResponseFlow,
            toolMetricsFlow = toolMetricsFlow,
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test pose analysis tool function declaration compliance`() = testScope.runTest {
        val toolDeclaration = toolManager.getPoseAnalysisToolDeclaration()

        // Validate function declaration structure
        assertThat(toolDeclaration.name).isEqualTo("analyze_pose_form")
        assertThat(toolDeclaration.description).contains("pose")
        assertThat(toolDeclaration.description).contains("analysis")

        // Validate parameters schema
        val parameters = toolDeclaration.parameters
        assertThat(parameters.type).isEqualTo("object")
        assertThat(parameters.properties).containsKey("pose_landmarks")
        assertThat(parameters.properties).containsKey("exercise_type")
        assertThat(parameters.properties).containsKey("user_height")
        assertThat(parameters.required).contains("pose_landmarks")
        assertThat(parameters.required).contains("exercise_type")

        // Validate pose_landmarks parameter
        val poseLandmarksParam = parameters.properties["pose_landmarks"] as Map<*, *>
        assertThat(poseLandmarksParam["type"]).isEqualTo("array")
        assertThat(poseLandmarksParam).containsKey("items")

        val landmarkSchema = poseLandmarksParam["items"] as Map<*, *>
        assertThat(landmarkSchema["type"]).isEqualTo("object")
        assertThat(landmarkSchema).containsKey("properties")

        val landmarkProperties = landmarkSchema["properties"] as Map<*, *>
        assertThat(landmarkProperties).containsKey("x")
        assertThat(landmarkProperties).containsKey("y")
        assertThat(landmarkProperties).containsKey("z")
        assertThat(landmarkProperties).containsKey("visibility")

        // Validate exercise_type parameter
        val exerciseTypeParam = parameters.properties["exercise_type"] as Map<*, *>
        assertThat(exerciseTypeParam["type"]).isEqualTo("string")
        assertThat(exerciseTypeParam["enum"]).isInstanceOf(List::class.java)

        val validExercises = exerciseTypeParam["enum"] as List<*>
        assertThat(validExercises).contains("squat")
        assertThat(validExercises).contains("pushup")
        assertThat(validExercises).contains("deadlift")
        assertThat(validExercises).contains("plank")
    }

    @Test
    fun `test real-time tool execution performance`() = testScope.runTest {
        val executionTimes = mutableListOf<Long>()
        val responses = mutableListOf<ToolResponse>()

        val job = launch {
            toolResponseFlow.collect { response ->
                responses.add(response)
                executionTimes.add(response.executionTimeMs)
            }
        }

        // Execute multiple pose analysis calls rapidly
        repeat(20) { index ->
            val toolCall = createPoseAnalysisCall(
                callId = "call_$index",
                exerciseType = if (index % 2 == 0) "squat" else "pushup",
                poseLandmarks = generateMockPoseLandmarks()
            )

            toolManager.executeTool(toolCall)
            delay(50) // Small delay between calls
        }

        delay(2000) // Allow all executions to complete
        job.cancel()

        // Validate execution performance
        assertThat(responses).hasSize(20)
        assertThat(responses.all { it.success }).isTrue()

        // All executions should complete within acceptable time
        assertThat(executionTimes.all { it <= POSE_ANALYSIS_MAX_TIME_MS }).isTrue()

        // Average execution time should be reasonable
        val averageTime = executionTimes.average()
        assertThat(averageTime).isLessThan(500.0) // < 500ms average

        // Validate tool metrics
        val metrics = toolMetricsFlow.value
        assertThat(metrics.totalCalls).isEqualTo(20)
        assertThat(metrics.successfulCalls).isEqualTo(20)
        assertThat(metrics.errorRate).isEqualTo(0.0)
    }

    @Test
    fun `test NON_BLOCKING scheduling validation`() = testScope.runTest {
        val concurrentCalls = 10
        val callStartTimes = mutableMapOf<String, Long>()
        val callEndTimes = mutableMapOf<String, Long>()
        val maxConcurrentExecutions = AtomicInteger(0)

        val responseJob = launch {
            toolResponseFlow.collect { response ->
                callEndTimes[response.callId] = System.currentTimeMillis()
            }
        }

        val metricsJob = launch {
            toolMetricsFlow.collect { metrics ->
                maxConcurrentExecutions.set(maxOf(maxConcurrentExecutions.get(), metrics.concurrentExecutions))
            }
        }

        // Launch multiple tool calls simultaneously
        val callJobs = (1..concurrentCalls).map { index ->
            launch {
                val callId = "concurrent_call_$index"
                callStartTimes[callId] = System.currentTimeMillis()

                val toolCall = createPoseAnalysisCall(
                    callId = callId,
                    exerciseType = "squat",
                    poseLandmarks = generateMockPoseLandmarks(),
                    simulatedDelayMs = 1000L // Simulate processing time
                )

                toolManager.executeTool(toolCall)
            }
        }

        callJobs.joinAll()
        delay(2000) // Allow all executions to complete

        responseJob.cancel()
        metricsJob.cancel()

        // Validate non-blocking execution
        assertThat(callEndTimes).hasSize(concurrentCalls)

        // Verify calls executed concurrently (not sequentially)
        val startTimes = callStartTimes.values.sorted()
        val endTimes = callEndTimes.values.sorted()

        // First call should end before last call starts (if sequential)
        // But with non-blocking, last start should be before first end
        val lastStartTime = startTimes.last()
        val firstEndTime = endTimes.first()
        assertThat(lastStartTime).isLessThan(firstEndTime + 500) // Allow some tolerance

        // Should have handled multiple concurrent executions
        assertThat(maxConcurrentExecutions.get()).isGreaterThan(1)
        assertThat(maxConcurrentExecutions.get()).isAtMost(MAX_CONCURRENT_TOOLS)
    }

    @Test
    fun `test tool response format validation`() = testScope.runTest {
        val toolCall = createPoseAnalysisCall(
            callId = "format_test",
            exerciseType = "squat",
            poseLandmarks = generateMockPoseLandmarks()
        )

        val responses = mutableListOf<ToolResponse>()
        val job = launch {
            toolResponseFlow.collect { response ->
                responses.add(response)
            }
        }

        toolManager.executeTool(toolCall)
        delay(1000)
        job.cancel()

        assertThat(responses).hasSize(1)
        val response = responses.first()

        // Validate response structure
        assertThat(response.callId).isEqualTo("format_test")
        assertThat(response.name).isEqualTo("analyze_pose_form")
        assertThat(response.success).isTrue()
        assertThat(response.executionTimeMs).isGreaterThan(0L)

        // Validate response content
        val responseData = response.response
        assertThat(responseData).containsKey("form_score")
        assertThat(responseData).containsKey("feedback")
        assertThat(responseData).containsKey("improvements")
        assertThat(responseData).containsKey("joint_angles")

        // Validate data types
        assertThat(responseData["form_score"]).isInstanceOf(Number::class.java)
        assertThat(responseData["feedback"]).isInstanceOf(String::class.java)
        assertThat(responseData["improvements"]).isInstanceOf(List::class.java)
        assertThat(responseData["joint_angles"]).isInstanceOf(Map::class.java)

        // Validate score range
        val formScore = (responseData["form_score"] as Number).toDouble()
        assertThat(formScore).isAtLeast(0.0)
        assertThat(formScore).isAtMost(100.0)

        // Validate feedback content
        val feedback = responseData["feedback"] as String
        assertThat(feedback).isNotEmpty()
        assertThat(feedback.length).isLessThan(500) // Reasonable length

        // Validate improvements list
        val improvements = responseData["improvements"] as List<*>
        assertThat(improvements.all { it is String }).isTrue()
    }

    @Test
    fun `test tool error handling and recovery`() = testScope.runTest {
        val errorScenarios = listOf(
            // (call_id, error_type, expected_error_message)
            Triple("invalid_landmarks", "INVALID_LANDMARKS", "Invalid pose landmarks format"),
            Triple("missing_exercise", "MISSING_EXERCISE_TYPE", "Exercise type is required"),
            Triple("unsupported_exercise", "UNSUPPORTED_EXERCISE", "Exercise type not supported"),
            Triple("timeout_test", "EXECUTION_TIMEOUT", "Tool execution timed out"),
            Triple("processing_error", "PROCESSING_ERROR", "Error during pose analysis")
        )

        val responses = mutableListOf<ToolResponse>()
        val job = launch {
            toolResponseFlow.collect { response ->
                responses.add(response)
            }
        }

        errorScenarios.forEach { (callId, errorType, expectedMessage) ->
            val toolCall = when (errorType) {
                "INVALID_LANDMARKS" -> createInvalidLandmarksCall(callId)
                "MISSING_EXERCISE_TYPE" -> createMissingExerciseCall(callId)
                "UNSUPPORTED_EXERCISE" -> createUnsupportedExerciseCall(callId)
                "EXECUTION_TIMEOUT" -> createTimeoutCall(callId)
                "PROCESSING_ERROR" -> createProcessingErrorCall(callId)
                else -> throw IllegalArgumentException("Unknown error type: $errorType")
            }

            toolManager.executeTool(toolCall)
        }

        delay(TOOL_TIMEOUT_MS + 1000) // Wait for timeouts to occur
        job.cancel()

        assertThat(responses).hasSize(5)

        responses.forEach { response ->
            assertThat(response.success).isFalse()
            assertThat(response.error).isNotNull()
            assertThat(response.error).isNotEmpty()
        }

        // Validate specific error messages
        val errorMap = responses.associateBy { it.callId }
        errorScenarios.forEach { (callId, _, expectedMessage) ->
            val response = errorMap[callId]
            assertThat(response).isNotNull()
            assertThat(response!!.error).contains(expectedMessage.split(" ").first())
        }

        // Validate metrics include errors
        val metrics = toolMetricsFlow.value
        assertThat(metrics.totalCalls).isEqualTo(5)
        assertThat(metrics.successfulCalls).isEqualTo(0)
        assertThat(metrics.errorRate).isEqualTo(1.0)
    }

    @Test
    fun `test tool input validation and sanitization`() = testScope.runTest {
        val validationTests = listOf(
            // Test landmark count validation
            createPoseAnalysisCall(
                callId = "too_few_landmarks",
                exerciseType = "squat",
                poseLandmarks = generateMockPoseLandmarks().take(10) // Too few landmarks
            ),

            // Test landmark coordinate validation
            createPoseAnalysisCall(
                callId = "invalid_coordinates",
                exerciseType = "squat",
                poseLandmarks = listOf(
                    PoseLandmark(Float.NaN, 0.5f, 0.0f, 1.0f), // Invalid x
                    PoseLandmark(0.5f, Float.POSITIVE_INFINITY, 0.0f, 1.0f) // Invalid y
                )
            ),

            // Test visibility validation
            createPoseAnalysisCall(
                callId = "invalid_visibility",
                exerciseType = "squat",
                poseLandmarks = listOf(
                    PoseLandmark(0.5f, 0.5f, 0.0f, -1.0f), // Invalid visibility
                    PoseLandmark(0.5f, 0.5f, 0.0f, 2.0f)   // Invalid visibility
                )
            ),

            // Test exercise type case sensitivity
            createPoseAnalysisCall(
                callId = "case_insensitive",
                exerciseType = "SQUAT", // Uppercase
                poseLandmarks = generateMockPoseLandmarks()
            ),

            // Test SQL injection attempt
            createPoseAnalysisCall(
                callId = "sql_injection",
                exerciseType = "squat'; DROP TABLE users; --",
                poseLandmarks = generateMockPoseLandmarks()
            )
        )

        val responses = mutableListOf<ToolResponse>()
        val job = launch {
            toolResponseFlow.collect { response ->
                responses.add(response)
            }
        }

        validationTests.forEach { toolCall ->
            toolManager.executeTool(toolCall)
        }

        delay(2000)
        job.cancel()

        assertThat(responses).hasSize(5)

        val responseMap = responses.associateBy { it.callId }

        // Too few landmarks should fail
        assertThat(responseMap["too_few_landmarks"]?.success).isFalse()

        // Invalid coordinates should fail
        assertThat(responseMap["invalid_coordinates"]?.success).isFalse()

        // Invalid visibility should fail
        assertThat(responseMap["invalid_visibility"]?.success).isFalse()

        // Case insensitive should succeed (normalized)
        assertThat(responseMap["case_insensitive"]?.success).isTrue()

        // SQL injection should be sanitized and handled safely
        assertThat(responseMap["sql_injection"]?.success).isFalse()
        assertThat(responseMap["sql_injection"]?.error).contains("Invalid")
    }

    @Test
    fun `test tool performance under load`() = testScope.runTest {
        val loadTestCalls = 100
        val concurrentBatches = 10
        val callsPerBatch = loadTestCalls / concurrentBatches

        val allResponses = mutableListOf<ToolResponse>()
        val responseJob = launch {
            toolResponseFlow.collect { response ->
                synchronized(allResponses) {
                    allResponses.add(response)
                }
            }
        }

        val startTime = System.currentTimeMillis()

        // Launch concurrent batches of tool calls
        val batchJobs = (1..concurrentBatches).map { batchIndex ->
            launch {
                repeat(callsPerBatch) { callIndex ->
                    val callId = "load_test_${batchIndex}_$callIndex"
                    val toolCall = createPoseAnalysisCall(
                        callId = callId,
                        exerciseType = if (callIndex % 3 == 0) "squat" else if (callIndex % 3 == 1) "pushup" else "deadlift",
                        poseLandmarks = generateMockPoseLandmarks()
                    )

                    toolManager.executeTool(toolCall)

                    // Small delay to simulate realistic call pattern
                    if (callIndex % 5 == 0) {
                        delay(10)
                    }
                }
            }
        }

        batchJobs.joinAll()

        // Wait for all responses
        while (allResponses.size < loadTestCalls) {
            delay(100)
            if (System.currentTimeMillis() - startTime > 30000) { // 30 second timeout
                break
            }
        }

        responseJob.cancel()

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime

        // Validate load test results
        assertThat(allResponses.size).isEqualTo(loadTestCalls)

        val successfulCalls = allResponses.count { it.success }
        val successRate = successfulCalls.toDouble() / loadTestCalls

        assertThat(successRate).isAtLeast(0.95) // At least 95% success rate

        // Validate performance metrics
        val averageExecutionTime = allResponses.map { it.executionTimeMs }.average()
        assertThat(averageExecutionTime).isLessThan(POSE_ANALYSIS_MAX_TIME_MS.toDouble())

        // Validate throughput (calls per second)
        val throughput = loadTestCalls.toDouble() / (totalTime / 1000.0)
        assertThat(throughput).isAtLeast(10.0) // At least 10 calls per second

        // Validate memory efficiency (metrics should not show excessive growth)
        val finalMetrics = toolMetricsFlow.value
        assertThat(finalMetrics.totalCalls).isEqualTo(loadTestCalls)
    }

    @Test
    fun `test tool integration with WebSocket messaging`() = testScope.runTest {
        val webSocketMessages = mutableListOf<String>()
        val mockWebSocket = mockk<okhttp3.WebSocket>(relaxed = true)

        every { mockWebSocket.send(any<String>()) } answers {
            val message = firstArg<String>()
            webSocketMessages.add(message)
            true
        }

        // Setup tool manager with WebSocket integration
        toolManager.setupWebSocketIntegration(mockWebSocket)

        val toolCall = createPoseAnalysisCall(
            callId = "websocket_test",
            exerciseType = "squat",
            poseLandmarks = generateMockPoseLandmarks()
        )

        // Execute tool and wait for response
        val responses = mutableListOf<ToolResponse>()
        val job = launch {
            toolResponseFlow.collect { response ->
                responses.add(response)
            }
        }

        toolManager.executeTool(toolCall)
        delay(1000)
        job.cancel()

        // Validate WebSocket message was sent
        assertThat(webSocketMessages).hasSize(1)

        val sentMessage = webSocketMessages.first()
        val messageJson = gson.fromJson(sentMessage, JsonObject::class.java)

        // Validate message structure
        assertThat(messageJson.has("toolResponse")).isTrue()

        val toolResponse = messageJson.getAsJsonObject("toolResponse")
        assertThat(toolResponse.has("functionResponses")).isTrue()

        val functionResponses = toolResponse.getAsJsonArray("functionResponses")
        assertThat(functionResponses.size()).isEqualTo(1)

        val functionResponse = functionResponses.get(0).asJsonObject
        assertThat(functionResponse.has("name")).isTrue()
        assertThat(functionResponse.has("response")).isTrue()
        assertThat(functionResponse.get("name").asString).isEqualTo("analyze_pose_form")

        // Validate response content
        val response = functionResponse.getAsJsonObject("response")
        assertThat(response.has("form_score")).isTrue()
        assertThat(response.has("feedback")).isTrue()
    }

    // Helper methods for creating test data
    private fun createPoseAnalysisCall(
        callId: String,
        exerciseType: String,
        poseLandmarks: List<PoseLandmark>,
        simulatedDelayMs: Long = 0L
    ): ToolCall {
        val args = mapOf(
            "pose_landmarks" to poseLandmarks.map { landmark ->
                mapOf(
                    "x" to landmark.x,
                    "y" to landmark.y,
                    "z" to landmark.z,
                    "visibility" to landmark.visibility
                )
            },
            "exercise_type" to exerciseType,
            "user_height" to 175.0, // cm
            "simulated_delay_ms" to simulatedDelayMs
        )

        return ToolCall(
            id = callId,
            name = "analyze_pose_form",
            args = args
        )
    }

    private fun generateMockPoseLandmarks(): List<PoseLandmark> {
        // Generate 33 pose landmarks (MediaPipe standard)
        return (0 until 33).map { index ->
            PoseLandmark(
                x = 0.3f + (index % 5) * 0.1f, // Vary x coordinates
                y = 0.2f + (index / 5) * 0.1f, // Vary y coordinates
                z = -0.1f + (index % 3) * 0.05f, // Vary z coordinates
                visibility = 0.8f + (index % 2) * 0.2f // High visibility
            )
        }
    }

    private fun createInvalidLandmarksCall(callId: String): ToolCall {
        return ToolCall(
            id = callId,
            name = "analyze_pose_form",
            args = mapOf(
                "pose_landmarks" to "invalid_format",
                "exercise_type" to "squat"
            )
        )
    }

    private fun createMissingExerciseCall(callId: String): ToolCall {
        return ToolCall(
            id = callId,
            name = "analyze_pose_form",
            args = mapOf(
                "pose_landmarks" to generateMockPoseLandmarks()
                // Missing exercise_type
            )
        )
    }

    private fun createUnsupportedExerciseCall(callId: String): ToolCall {
        return ToolCall(
            id = callId,
            name = "analyze_pose_form",
            args = mapOf(
                "pose_landmarks" to generateMockPoseLandmarks(),
                "exercise_type" to "unsupported_exercise"
            )
        )
    }

    private fun createTimeoutCall(callId: String): ToolCall {
        return createPoseAnalysisCall(
            callId = callId,
            exerciseType = "squat",
            poseLandmarks = generateMockPoseLandmarks(),
            simulatedDelayMs = TOOL_TIMEOUT_MS + 1000L // Exceed timeout
        )
    }

    private fun createProcessingErrorCall(callId: String): ToolCall {
        return ToolCall(
            id = callId,
            name = "analyze_pose_form",
            args = mapOf(
                "pose_landmarks" to generateMockPoseLandmarks(),
                "exercise_type" to "trigger_processing_error"
            )
        )
    }

    // Test helper classes
    data class ToolDeclaration(
        val name: String,
        val description: String,
        val parameters: ParameterSchema
    )

    data class ParameterSchema(
        val type: String,
        val properties: Map<String, Any>,
        val required: List<String>
    )

    private class TestToolManager(
        private val toolCallFlow: MutableSharedFlow<ToolCall>,
        private val toolResponseFlow: MutableSharedFlow<ToolResponse>,
        private val toolMetricsFlow: MutableStateFlow<ToolMetrics>,
        private val scope: CoroutineScope
    ) {
        private val activeExecutions = AtomicInteger(0)
        private val totalCalls = AtomicLong(0)
        private val successfulCalls = AtomicLong(0)
        private val executionTimes = mutableListOf<Long>()
        private var webSocket: okhttp3.WebSocket? = null

        fun getPoseAnalysisToolDeclaration(): ToolDeclaration {
            return ToolDeclaration(
                name = "analyze_pose_form",
                description = "Analyze pose form and provide feedback for exercise improvement",
                parameters = ParameterSchema(
                    type = "object",
                    properties = mapOf(
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
                    required = listOf("pose_landmarks", "exercise_type")
                )
            )
        }

        fun executeTool(toolCall: ToolCall) {
            scope.launch {
                val startTime = System.currentTimeMillis()
                activeExecutions.incrementAndGet()
                totalCalls.incrementAndGet()

                updateMetrics()

                try {
                    val response = when (toolCall.name) {
                        "analyze_pose_form" -> executePoseAnalysis(toolCall)
                        else -> ToolResponse(
                            callId = toolCall.id,
                            name = toolCall.name,
                            response = emptyMap(),
                            executionTimeMs = 0L,
                            success = false,
                            error = "Unknown tool: ${toolCall.name}"
                        )
                    }

                    if (response.success) {
                        successfulCalls.incrementAndGet()
                    }

                    executionTimes.add(response.executionTimeMs)
                    toolResponseFlow.emit(response)

                    // Send response via WebSocket if configured
                    webSocket?.let { ws ->
                        val webSocketMessage = createWebSocketToolResponse(response)
                        ws.send(webSocketMessage)
                    }

                } catch (e: Exception) {
                    val errorResponse = ToolResponse(
                        callId = toolCall.id,
                        name = toolCall.name,
                        response = emptyMap(),
                        executionTimeMs = System.currentTimeMillis() - startTime,
                        success = false,
                        error = e.message ?: "Unknown error"
                    )
                    toolResponseFlow.emit(errorResponse)
                } finally {
                    activeExecutions.decrementAndGet()
                    updateMetrics()
                }
            }
        }

        fun setupWebSocketIntegration(webSocket: okhttp3.WebSocket) {
            this.webSocket = webSocket
        }

        private suspend fun executePoseAnalysis(toolCall: ToolCall): ToolResponse {
            val startTime = System.currentTimeMillis()

            // Validate arguments
            val validationResult = validatePoseAnalysisArgs(toolCall.args)
            if (validationResult != null) {
                return ToolResponse(
                    callId = toolCall.id,
                    name = toolCall.name,
                    response = emptyMap(),
                    executionTimeMs = System.currentTimeMillis() - startTime,
                    success = false,
                    error = validationResult
                )
            }

            // Extract arguments
            val poseLandmarks = toolCall.args["pose_landmarks"] as List<*>
            val exerciseType = toolCall.args["exercise_type"] as String
            val userHeight = toolCall.args["user_height"] as? Double ?: 170.0
            val simulatedDelayMs = toolCall.args["simulated_delay_ms"] as? Long ?: 0L

            // Simulate processing delay
            if (simulatedDelayMs > 0) {
                delay(simulatedDelayMs)
            }

            // Check for timeout
            val executionTime = System.currentTimeMillis() - startTime
            if (executionTime > TOOL_TIMEOUT_MS) {
                return ToolResponse(
                    callId = toolCall.id,
                    name = toolCall.name,
                    response = emptyMap(),
                    executionTimeMs = executionTime,
                    success = false,
                    error = "Tool execution timed out"
                )
            }

            // Check for processing error trigger
            if (exerciseType == "trigger_processing_error") {
                throw RuntimeException("Simulated processing error")
            }

            // Simulate pose analysis
            val formScore = when (exerciseType.lowercase()) {
                "squat" -> 85.0 + (poseLandmarks.size % 10)
                "pushup" -> 78.0 + (userHeight / 10 % 15)
                "deadlift" -> 92.0 + (toolCall.id.hashCode() % 8)
                else -> 75.0
            }

            val feedback = generateFeedback(exerciseType, formScore)
            val improvements = generateImprovements(exerciseType, formScore)
            val jointAngles = generateJointAngles(exerciseType)

            val response = mapOf(
                "form_score" to formScore,
                "feedback" to feedback,
                "improvements" to improvements,
                "joint_angles" to jointAngles,
                "exercise_type" to exerciseType,
                "analysis_timestamp" to System.currentTimeMillis()
            )

            return ToolResponse(
                callId = toolCall.id,
                name = toolCall.name,
                response = response,
                executionTimeMs = System.currentTimeMillis() - startTime,
                success = true
            )
        }

        private fun validatePoseAnalysisArgs(args: Map<String, Any>): String? {
            // Check required parameters
            if (!args.containsKey("pose_landmarks")) {
                return "Missing required parameter: pose_landmarks"
            }
            if (!args.containsKey("exercise_type")) {
                return "Missing required parameter: exercise_type"
            }

            // Validate pose landmarks
            val poseLandmarks = args["pose_landmarks"]
            if (poseLandmarks !is List<*>) {
                return "Invalid pose landmarks format"
            }

            if (poseLandmarks.size < 20) {
                return "Insufficient pose landmarks (minimum 20 required)"
            }

            // Validate landmark structure
            for (landmark in poseLandmarks) {
                if (landmark !is Map<*, *>) {
                    return "Invalid landmark format"
                }

                val x = landmark["x"] as? Number
                val y = landmark["y"] as? Number
                val z = landmark["z"] as? Number
                val visibility = landmark["visibility"] as? Number

                if (x == null || y == null || z == null || visibility == null) {
                    return "Invalid landmark coordinates"
                }

                if (x.toFloat().isNaN() || y.toFloat().isNaN() || z.toFloat().isNaN()) {
                    return "Invalid coordinate values (NaN detected)"
                }

                if (visibility.toFloat() < 0 || visibility.toFloat() > 1) {
                    return "Invalid visibility value (must be between 0 and 1)"
                }
            }

            // Validate exercise type
            val exerciseType = args["exercise_type"] as? String
            if (exerciseType.isNullOrBlank()) {
                return "Exercise type cannot be empty"
            }

            val validExercises = listOf("squat", "pushup", "deadlift", "plank", "bicep_curl", "overhead_press")
            if (exerciseType.lowercase() !in validExercises && !exerciseType.startsWith("trigger_")) {
                return "Unsupported exercise type: $exerciseType"
            }

            // Check for potential injection attacks
            if (exerciseType.contains("'") || exerciseType.contains(";") || exerciseType.contains("--")) {
                return "Invalid characters in exercise type"
            }

            return null
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
            }

            return improvements.ifEmpty { listOf("Keep up the excellent form!") }
        }

        private fun generateJointAngles(exerciseType: String): Map<String, Double> {
            return when (exerciseType.lowercase()) {
                "squat" -> mapOf(
                    "knee_angle" to 90.0 + (Math.random() * 20 - 10),
                    "hip_angle" to 80.0 + (Math.random() * 15 - 7.5),
                    "ankle_angle" to 70.0 + (Math.random() * 10 - 5)
                )
                "pushup" -> mapOf(
                    "elbow_angle" to 90.0 + (Math.random() * 20 - 10),
                    "shoulder_angle" to 45.0 + (Math.random() * 15 - 7.5),
                    "wrist_angle" to 180.0 + (Math.random() * 10 - 5)
                )
                else -> mapOf(
                    "primary_joint" to 90.0 + (Math.random() * 30 - 15)
                )
            }
        }

        private fun createWebSocketToolResponse(response: ToolResponse): String {
            val toolResponseMessage = mapOf(
                "toolResponse" to mapOf(
                    "functionResponses" to listOf(
                        mapOf(
                            "name" to response.name,
                            "response" to response.response
                        )
                    )
                )
            )
            return Gson().toJson(toolResponseMessage)
        }

        private fun updateMetrics() {
            val currentConcurrent = activeExecutions.get()
            val avgExecutionTime = if (executionTimes.isNotEmpty()) {
                executionTimes.average()
            } else 0.0

            val total = totalCalls.get()
            val successful = successfulCalls.get()
            val errorRate = if (total > 0) (total - successful).toDouble() / total else 0.0

            toolMetricsFlow.value = ToolMetrics(
                totalCalls = total.toInt(),
                successfulCalls = successful.toInt(),
                averageExecutionTime = avgExecutionTime,
                errorRate = errorRate,
                concurrentExecutions = currentConcurrent
            )
        }
    }
}