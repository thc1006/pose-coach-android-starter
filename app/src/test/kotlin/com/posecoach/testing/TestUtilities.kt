package com.posecoach.testing

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.pose.PoseLandmark
import com.posecoach.app.livecoach.models.*
import com.posecoach.corepose.models.PoseLandmarkResult
import io.mockk.*
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Comprehensive test utilities for Voice Coach integration testing
 * Following the specification in .claude/specs/voice-coach-integration.md
 *
 * Provides:
 * 1. Mock factories for all core components
 * 2. Test data builders with realistic data
 * 3. Performance testing helpers
 * 4. Validation utilities
 * 5. Common test scenarios
 */

object TestUtilities {

    // MOCK FACTORIES

    object MockFactories {

        /**
         * Creates a mock ImageProxy with configurable parameters
         */
        fun createMockImageProxy(
            width: Int = 720,
            height: Int = 1280,
            format: Int = ImageFormat.YUV_420_888,
            rotationDegrees: Int = 0
        ): ImageProxy = mockk {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
            every { this@mockk.format } returns format
            every { close() } just Runs

            every { imageInfo } returns mockk {
                every { this@mockk.rotationDegrees } returns rotationDegrees
            }

            // Mock planes for different formats
            when (format) {
                ImageFormat.YUV_420_888 -> {
                    val yPlane = createMockPlane(width * height)
                    val uPlane = createMockPlane(width * height / 4, pixelStride = 2)
                    val vPlane = createMockPlane(width * height / 4, pixelStride = 2)
                    every { planes } returns arrayOf(yPlane, uPlane, vPlane)
                }
                ImageFormat.JPEG -> {
                    val jpegPlane = createMockPlane(width * height / 2) // Approximate JPEG size
                    every { planes } returns arrayOf(jpegPlane)
                }
                else -> {
                    every { planes } returns arrayOf(createMockPlane(width * height))
                }
            }

            // Mock the image property
            every { image } returns mockk(relaxed = true) {
                every { width } returns width
                every { height } returns height
                every { format } returns format
            }
        }

        private fun createMockPlane(
            bufferSize: Int,
            pixelStride: Int = 1,
            rowStride: Int = 0
        ): ImageProxy.PlaneProxy = mockk {
            val buffer = ByteBuffer.allocate(bufferSize)
            // Fill with realistic test data
            repeat(bufferSize) { buffer.put((Random.nextInt(256) - 128).toByte()) }
            buffer.rewind()

            every { this@mockk.buffer } returns buffer
            every { this@mockk.pixelStride } returns pixelStride
            every { this@mockk.rowStride } returns if (rowStride == 0) bufferSize / 10 else rowStride
        }

        /**
         * Creates mock pose landmarks with realistic positions
         */
        fun createMockPoseLandmarks(
            landmarkCount: Int = 33,
            confidence: Float = 0.8f
        ): List<PoseLandmark> {
            return (0 until landmarkCount).map { index ->
                mockk<PoseLandmark> {
                    every { landmarkType } returns PoseLandmark.values()[index % PoseLandmark.values().size]
                    every { position } returns PointF(
                        0.1f + (index % 8) * 0.1f, // X varies from 0.1 to 0.8
                        0.1f + (index / 8) * 0.1f  // Y varies based on row
                    )
                    every { position3D } returns mockk {
                        every { x } returns 0.1f + (index % 8) * 0.1f
                        every { y } returns 0.1f + (index / 8) * 0.1f
                        every { z } returns -0.5f + Random.nextFloat() // Depth variation
                    }
                    every { inFrameLikelihood } returns confidence + Random.nextFloat() * 0.2f - 0.1f
                }
            }
        }

        /**
         * Creates mock audio data with realistic properties
         */
        fun createMockAudioData(
            sampleRate: Int = 16000,
            durationMs: Long = 1000L,
            amplitude: Float = 0.5f
        ): ByteArray {
            val sampleCount = (sampleRate * durationMs / 1000).toInt()
            val audioData = ByteArray(sampleCount * 2) // 16-bit samples

            // Generate simple sine wave for testing
            for (i in 0 until sampleCount) {
                val sample = (amplitude * Short.MAX_VALUE *
                    kotlin.math.sin(2 * kotlin.math.PI * 440.0 * i / sampleRate)).toInt()
                audioData[i * 2] = (sample and 0xFF).toByte()
                audioData[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }

            return audioData
        }

        /**
         * Creates mock Live API responses
         */
        fun createMockLiveApiResponse(
            type: LiveApiResponseType = LiveApiResponseType.SERVER_CONTENT,
            content: String = "Great form! Keep it up!"
        ): LiveApiResponse = when (type) {
            LiveApiResponseType.SETUP_COMPLETE ->
                LiveApiResponse.SetupComplete(true)

            LiveApiResponseType.SERVER_CONTENT ->
                LiveApiResponse.ServerContent(
                    modelTurn = Content(listOf(Part.TextPart(content))),
                    turnComplete = true,
                    interrupted = false
                )

            LiveApiResponseType.TOOL_CALL ->
                LiveApiResponse.ToolCall(
                    functionCalls = listOf(
                        FunctionCall(
                            name = "analyzePose",
                            args = mapOf("exercise" to "squat", "feedback" to content),
                            id = "call-${Random.nextInt(1000)}"
                        )
                    )
                )
        }

        enum class LiveApiResponseType {
            SETUP_COMPLETE, SERVER_CONTENT, TOOL_CALL
        }
    }

    // TEST DATA BUILDERS

    object TestDataBuilders {

        class PoseLandmarkResultBuilder {
            private var landmarks = mutableListOf<PoseLandmarkResult.Landmark>()
            private var worldLandmarks = mutableListOf<PoseLandmarkResult.Landmark>()
            private var timestampMs = System.currentTimeMillis()
            private var inferenceTimeMs = 50L

            fun withRandomLandmarks(count: Int = 33): PoseLandmarkResultBuilder {
                landmarks.clear()
                worldLandmarks.clear()

                repeat(count) { index ->
                    val landmark = PoseLandmarkResult.Landmark(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        z = Random.nextFloat() - 0.5f,
                        visibility = 0.5f + Random.nextFloat() * 0.5f,
                        presence = 0.5f + Random.nextFloat() * 0.5f
                    )
                    landmarks.add(landmark)
                    worldLandmarks.add(landmark.copy(
                        x = landmark.x * 2 - 1, // Convert to world coordinates
                        y = landmark.y * 2 - 1,
                        z = landmark.z * 2
                    ))
                }
                return this
            }

            fun withSpecificLandmark(
                index: Int,
                x: Float,
                y: Float,
                z: Float = 0f,
                visibility: Float = 1f,
                presence: Float = 1f
            ): PoseLandmarkResultBuilder {
                while (landmarks.size <= index) {
                    landmarks.add(PoseLandmarkResult.Landmark(0f, 0f, 0f, 0f, 0f))
                    worldLandmarks.add(PoseLandmarkResult.Landmark(0f, 0f, 0f, 0f, 0f))
                }

                landmarks[index] = PoseLandmarkResult.Landmark(x, y, z, visibility, presence)
                worldLandmarks[index] = PoseLandmarkResult.Landmark(x * 2 - 1, y * 2 - 1, z * 2, visibility, presence)
                return this
            }

            fun withTimestamp(timestamp: Long): PoseLandmarkResultBuilder {
                this.timestampMs = timestamp
                return this
            }

            fun withInferenceTime(inferenceTime: Long): PoseLandmarkResultBuilder {
                this.inferenceTimeMs = inferenceTime
                return this
            }

            fun build(): PoseLandmarkResult {
                return PoseLandmarkResult(
                    landmarks = landmarks.toList(),
                    worldLandmarks = worldLandmarks.toList(),
                    timestampMs = timestampMs,
                    inferenceTimeMs = inferenceTimeMs
                )
            }
        }

        class AudioChunkBuilder {
            private var data = ByteArray(0)
            private var timestamp = System.currentTimeMillis()
            private var sampleRate = 16000

            fun withDuration(durationMs: Long): AudioChunkBuilder {
                this.data = MockFactories.createMockAudioData(sampleRate, durationMs)
                return this
            }

            fun withSampleRate(rate: Int): AudioChunkBuilder {
                this.sampleRate = rate
                return this
            }

            fun withTimestamp(timestamp: Long): AudioChunkBuilder {
                this.timestamp = timestamp
                return this
            }

            fun build(): AudioChunk {
                return AudioChunk(
                    data = data,
                    timestamp = timestamp,
                    sampleRate = sampleRate
                )
            }
        }

        class LiveApiConfigBuilder {
            private var model = "gemini-2.0-flash-exp"
            private var generationConfig = mapOf<String, Any>(
                "temperature" to 0.7,
                "maxOutputTokens" to 1000
            )
            private var systemInstruction = mapOf<String, Any>(
                "parts" to listOf(
                    mapOf("text" to "You are a helpful fitness coach providing real-time pose feedback.")
                )
            )

            fun withModel(model: String): LiveApiConfigBuilder {
                this.model = model
                return this
            }

            fun withTemperature(temperature: Double): LiveApiConfigBuilder {
                this.generationConfig = generationConfig.toMutableMap().apply {
                    put("temperature", temperature)
                }
                return this
            }

            fun withSystemInstruction(instruction: String): LiveApiConfigBuilder {
                this.systemInstruction = mapOf(
                    "parts" to listOf(mapOf("text" to instruction))
                )
                return this
            }

            fun build(): LiveApiConfig {
                return LiveApiConfig(
                    model = model,
                    generationConfig = generationConfig,
                    systemInstruction = systemInstruction
                )
            }
        }
    }

    // PERFORMANCE TESTING HELPERS

    object PerformanceHelpers {

        data class PerformanceResult(
            val operationCount: Int,
            val totalDurationMs: Long,
            val averageLatencyMs: Double,
            val minLatencyMs: Long,
            val maxLatencyMs: Long,
            val p95LatencyMs: Long,
            val throughputOpsPerSec: Double
        )

        /**
         * Measures the performance of an operation over multiple iterations
         */
        suspend fun measurePerformance(
            operationName: String,
            iterations: Int,
            warmupIterations: Int = 10,
            operation: suspend () -> Unit
        ): PerformanceResult {
            // Warmup
            repeat(warmupIterations) {
                operation()
            }

            val latencies = mutableListOf<Long>()
            val startTime = System.currentTimeMillis()

            repeat(iterations) {
                val operationStart = System.nanoTime()
                operation()
                val operationEnd = System.nanoTime()
                latencies.add((operationEnd - operationStart) / 1_000_000) // Convert to ms
            }

            val endTime = System.currentTimeMillis()
            val totalDuration = endTime - startTime

            latencies.sort()
            val p95Index = (latencies.size * 0.95).toInt()

            return PerformanceResult(
                operationCount = iterations,
                totalDurationMs = totalDuration,
                averageLatencyMs = latencies.average(),
                minLatencyMs = latencies.minOrNull() ?: 0L,
                maxLatencyMs = latencies.maxOrNull() ?: 0L,
                p95LatencyMs = latencies.getOrElse(p95Index) { latencies.last() },
                throughputOpsPerSec = iterations * 1000.0 / totalDuration
            )
        }

        /**
         * Runs a stress test with increasing load
         */
        suspend fun runStressTest(
            operationName: String,
            maxConcurrency: Int,
            durationPerLevel: Long = 5000L,
            operation: suspend () -> Unit
        ): Map<Int, PerformanceResult> {
            val results = mutableMapOf<Int, PerformanceResult>()

            for (concurrency in 1..maxConcurrency) {
                val iterations = (durationPerLevel / 100).toInt() // Estimate iterations
                val result = measureConcurrentPerformance(concurrency, iterations, operation)
                results[concurrency] = result

                // Break if performance degrades significantly
                if (concurrency > 1) {
                    val previousResult = results[concurrency - 1]!!
                    val performanceDegradation =
                        result.averageLatencyMs / previousResult.averageLatencyMs

                    if (performanceDegradation > 3.0) { // 3x degradation
                        break
                    }
                }
            }

            return results
        }

        private suspend fun measureConcurrentPerformance(
            concurrency: Int,
            iterationsPerWorker: Int,
            operation: suspend () -> Unit
        ): PerformanceResult {
            val allLatencies = mutableListOf<Long>()
            val startTime = System.currentTimeMillis()

            kotlinx.coroutines.coroutineScope {
                val jobs = (1..concurrency).map {
                    kotlinx.coroutines.launch {
                        repeat(iterationsPerWorker) {
                            val operationStart = System.nanoTime()
                            operation()
                            val operationEnd = System.nanoTime()
                            synchronized(allLatencies) {
                                allLatencies.add((operationEnd - operationStart) / 1_000_000)
                            }
                        }
                    }
                }
                jobs.forEach { it.join() }
            }

            val endTime = System.currentTimeMillis()
            val totalDuration = endTime - startTime

            allLatencies.sort()
            val p95Index = (allLatencies.size * 0.95).toInt()

            return PerformanceResult(
                operationCount = allLatencies.size,
                totalDurationMs = totalDuration,
                averageLatencyMs = allLatencies.average(),
                minLatencyMs = allLatencies.minOrNull() ?: 0L,
                maxLatencyMs = allLatencies.maxOrNull() ?: 0L,
                p95LatencyMs = allLatencies.getOrElse(p95Index) { allLatencies.last() },
                throughputOpsPerSec = allLatencies.size * 1000.0 / totalDuration
            )
        }

        /**
         * Memory usage monitoring helper
         */
        fun getMemoryUsage(): MemoryUsage {
            val runtime = Runtime.getRuntime()
            return MemoryUsage(
                totalMemory = runtime.totalMemory(),
                freeMemory = runtime.freeMemory(),
                usedMemory = runtime.totalMemory() - runtime.freeMemory(),
                maxMemory = runtime.maxMemory()
            )
        }

        data class MemoryUsage(
            val totalMemory: Long,
            val freeMemory: Long,
            val usedMemory: Long,
            val maxMemory: Long
        ) {
            val usedPercentage: Double get() = usedMemory.toDouble() / totalMemory * 100
        }
    }

    // VALIDATION UTILITIES

    object ValidationHelpers {

        /**
         * Validates pose landmark results
         */
        fun validatePoseLandmarks(result: PoseLandmarkResult): ValidationResult {
            val issues = mutableListOf<String>()

            if (result.landmarks.size != 33) {
                issues.add("Expected 33 landmarks, got ${result.landmarks.size}")
            }

            if (result.worldLandmarks.size != 33) {
                issues.add("Expected 33 world landmarks, got ${result.worldLandmarks.size}")
            }

            result.landmarks.forEachIndexed { index, landmark ->
                if (landmark.x < 0f || landmark.x > 1f) {
                    issues.add("Landmark $index X coordinate out of range: ${landmark.x}")
                }
                if (landmark.y < 0f || landmark.y > 1f) {
                    issues.add("Landmark $index Y coordinate out of range: ${landmark.y}")
                }
                if (landmark.visibility < 0f || landmark.visibility > 1f) {
                    issues.add("Landmark $index visibility out of range: ${landmark.visibility}")
                }
                if (landmark.presence < 0f || landmark.presence > 1f) {
                    issues.add("Landmark $index presence out of range: ${landmark.presence}")
                }
            }

            if (result.timestampMs <= 0) {
                issues.add("Invalid timestamp: ${result.timestampMs}")
            }

            if (result.inferenceTimeMs < 0) {
                issues.add("Invalid inference time: ${result.inferenceTimeMs}")
            }

            return ValidationResult(issues.isEmpty(), issues)
        }

        /**
         * Validates audio chunk data
         */
        fun validateAudioChunk(chunk: AudioChunk): ValidationResult {
            val issues = mutableListOf<String>()

            if (chunk.data.isEmpty()) {
                issues.add("Audio data is empty")
            }

            if (chunk.data.size % 2 != 0) {
                issues.add("Audio data size should be even for 16-bit samples")
            }

            if (chunk.sampleRate !in 8000..48000) {
                issues.add("Sample rate out of typical range: ${chunk.sampleRate}")
            }

            if (chunk.timestamp <= 0) {
                issues.add("Invalid timestamp: ${chunk.timestamp}")
            }

            return ValidationResult(issues.isEmpty(), issues)
        }

        /**
         * Validates coordinate transformation accuracy
         */
        fun validateCoordinateTransformation(
            original: Pair<Float, Float>,
            transformed: Pair<Float, Float>,
            backTransformed: Pair<Float, Float>,
            tolerance: Float = 0.01f
        ): ValidationResult {
            val issues = mutableListOf<String>()

            val errorX = kotlin.math.abs(original.first - backTransformed.first)
            val errorY = kotlin.math.abs(original.second - backTransformed.second)

            if (errorX > tolerance) {
                issues.add("X coordinate round-trip error exceeds tolerance: $errorX > $tolerance")
            }

            if (errorY > tolerance) {
                issues.add("Y coordinate round-trip error exceeds tolerance: $errorY > $tolerance")
            }

            return ValidationResult(issues.isEmpty(), issues)
        }

        data class ValidationResult(
            val isValid: Boolean,
            val issues: List<String>
        )
    }

    // COMMON TEST SCENARIOS

    object TestScenarios {

        /**
         * Simulates a typical workout session with pose detection
         */
        suspend fun simulateWorkoutSession(
            durationMs: Long,
            frameRate: Int = 30,
            onFrame: suspend (frame: Int, timestamp: Long) -> Unit
        ) {
            val frameInterval = 1000L / frameRate
            val endTime = System.currentTimeMillis() + durationMs
            var frameCount = 0

            while (System.currentTimeMillis() < endTime) {
                val frameStart = System.currentTimeMillis()
                onFrame(frameCount++, frameStart)

                val processingTime = System.currentTimeMillis() - frameStart
                val sleepTime = frameInterval - processingTime

                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
        }

        /**
         * Simulates network connectivity issues
         */
        suspend fun simulateNetworkIssues(
            scenario: NetworkScenario,
            duration: Long,
            onStateChange: (connected: Boolean) -> Unit
        ) {
            when (scenario) {
                NetworkScenario.INTERMITTENT -> {
                    val intervals = listOf(2000L, 1000L, 3000L, 500L, 4000L)
                    var connected = true

                    for (interval in intervals) {
                        if (System.currentTimeMillis() > duration) break

                        onStateChange(connected)
                        delay(interval)
                        connected = !connected
                    }
                }

                NetworkScenario.SLOW_CONNECTION -> {
                    onStateChange(true)
                    // Simulate slow responses by adding delays
                    delay(duration)
                }

                NetworkScenario.COMPLETE_LOSS -> {
                    onStateChange(false)
                    delay(duration / 2)
                    onStateChange(true) // Recovery
                    delay(duration / 2)
                }
            }
        }

        enum class NetworkScenario {
            INTERMITTENT, SLOW_CONNECTION, COMPLETE_LOSS
        }

        /**
         * Simulates memory pressure conditions
         */
        suspend fun simulateMemoryPressure(
            pressureLevel: MemoryPressureLevel,
            duration: Long,
            onMemoryState: (usage: PerformanceHelpers.MemoryUsage) -> Unit
        ) {
            val allocations = mutableListOf<ByteArray>()
            val endTime = System.currentTimeMillis() + duration

            try {
                while (System.currentTimeMillis() < endTime) {
                    val currentMemory = PerformanceHelpers.getMemoryUsage()
                    onMemoryState(currentMemory)

                    when (pressureLevel) {
                        MemoryPressureLevel.LOW -> {
                            allocations.add(ByteArray(1024 * 1024)) // 1MB
                            delay(1000)
                        }
                        MemoryPressureLevel.MEDIUM -> {
                            allocations.add(ByteArray(5 * 1024 * 1024)) // 5MB
                            delay(500)
                        }
                        MemoryPressureLevel.HIGH -> {
                            allocations.add(ByteArray(10 * 1024 * 1024)) // 10MB
                            delay(200)
                        }
                    }

                    // Occasionally free some memory
                    if (allocations.size > 10 && Random.nextFloat() < 0.3f) {
                        allocations.removeAt(Random.nextInt(allocations.size))
                    }
                }
            } finally {
                allocations.clear()
                System.gc()
            }
        }

        enum class MemoryPressureLevel {
            LOW, MEDIUM, HIGH
        }
    }

    // UTILITY EXTENSIONS

    /**
     * Extension function to measure execution time
     */
    suspend inline fun <T> measureTime(block: () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        return Pair(result, endTime - startTime)
    }

    /**
     * Extension function to retry operations with exponential backoff
     */
    suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10000L,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var delayMs = initialDelayMs

        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
                }
            }
        }

        throw lastException ?: RuntimeException("All retry attempts failed")
    }
}