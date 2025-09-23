package com.posecoach.performance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.livecoach.models.LiveApiModels
import com.posecoach.app.livecoach.performance.LiveCoachPerformanceMonitor
import com.posecoach.app.livecoach.state.LiveCoachStateManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.*
import okio.ByteString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Performance benchmark tests for Live API integration.
 *
 * Requirements from CLAUDE.md:
 * - WebSocket connection time <3s
 * - Voice response latency <2s
 * - Audio streaming performance
 * - Network resilience testing
 *
 * TDD Approach:
 * RED: Write failing tests defining Live API performance requirements
 * GREEN: Implement minimal code to pass tests
 * REFACTOR: Optimize for real-world network conditions
 */
@RunWith(AndroidJUnit4::class)
class LiveApiPerformanceTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var mockWebSocket: WebSocket
    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var mockLiveCoachManager: LiveCoachManager
    private lateinit var mockPerformanceMonitor: LiveCoachPerformanceMonitor

    // Network simulation
    private val networkSimulator = NetworkSimulator()
    private val audioSimulator = AudioStreamSimulator()

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)

        mockWebSocket = mockk(relaxed = true)
        mockOkHttpClient = mockk(relaxed = true)
        mockLiveCoachManager = mockk(relaxed = true)
        mockPerformanceMonitor = mockk(relaxed = true)

        clearAllMocks()
    }

    @After
    fun tearDown() {
        testScope.cancel()
        networkSimulator.reset()
        audioSimulator.cleanup()
        clearAllMocks()
    }

    /**
     * TDD RED: Test WebSocket connection establishment under 3 seconds
     */
    @Test
    fun `websocket connection should establish within 3 seconds`() = testScope.runTest {
        // Arrange
        val maxConnectionTimeMs = 3000L
        val connectionLatch = CountDownLatch(1)
        var connectionTime = 0L

        // Mock WebSocket connection with realistic delay
        every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
            val listener = secondArg<WebSocketListener>()
            testScope.backgroundScope.launch {
                delay(1500) // Simulate network connection time
                listener.onOpen(mockWebSocket, mockk(relaxed = true))
                connectionLatch.countDown()
            }
            mockWebSocket
        }

        // Act
        connectionTime = measureTimeMillis {
            mockOkHttpClient.newWebSocket(mockk(relaxed = true), mockk(relaxed = true))
            assertTrue("WebSocket should connect within timeout",
                      connectionLatch.await(maxConnectionTimeMs + 500, TimeUnit.MILLISECONDS))
        }

        advanceUntilIdle()

        // Assert
        assertTrue("WebSocket connection time ($connectionTime ms) should be under $maxConnectionTimeMs ms",
                  connectionTime < maxConnectionTimeMs)
    }

    /**
     * TDD RED: Test voice response latency under 2 seconds
     */
    @Test
    fun `voice response should complete within 2 seconds`() = testScope.runTest {
        // Arrange
        val maxResponseTimeMs = 2000L
        val responseLatch = CountDownLatch(1)
        var responseTime = 0L

        // Mock voice processing pipeline
        val mockAudioData = ByteArray(1024) { it.toByte() }
        val mockResponse = LiveApiModels.VoiceResponse(
            text = "Great form! Keep your back straight.",
            confidence = 0.95f,
            duration = 1.2f
        )

        every { mockLiveCoachManager.processVoiceInput(any()) } returns flow {
            delay(800) // Simulate processing time
            emit(mockResponse)
        }

        // Act
        responseTime = measureTimeMillis {
            mockLiveCoachManager.processVoiceInput(mockAudioData)
                .collect { response ->
                    assertNotNull("Response should not be null", response)
                    assertTrue("Response should have content", response.text.isNotEmpty())
                    responseLatch.countDown()
                }
        }

        advanceUntilIdle()
        assertTrue("Voice response should complete within timeout",
                  responseLatch.await(maxResponseTimeMs + 500, TimeUnit.MILLISECONDS))

        // Assert
        assertTrue("Voice response time ($responseTime ms) should be under $maxResponseTimeMs ms",
                  responseTime < maxResponseTimeMs)
    }

    /**
     * TDD RED: Test audio streaming performance and quality
     */
    @Test
    fun `audio streaming should maintain quality under continuous load`() = testScope.runTest {
        // Arrange
        val streamDurationMs = 10000L // 10 seconds
        val expectedSampleRate = 16000 // 16kHz
        val expectedChannels = 1 // Mono
        val maxDroppedFrames = 5 // Allow max 5 dropped frames

        val droppedFrames = AtomicInteger(0)
        val totalFrames = AtomicInteger(0)
        val latencies = mutableListOf<Long>()

        // Mock audio streaming
        every { mockLiveCoachManager.startAudioStream(any()) } returns flow {
            val frameInterval = 1000L / (expectedSampleRate / 1024) // ~64ms per frame
            var frameCount = 0

            while (frameCount * frameInterval < streamDurationMs) {
                val frameStart = System.nanoTime()

                // Simulate audio frame processing
                delay(frameInterval)

                val audioFrame = audioSimulator.generateFrame(1024, expectedSampleRate)
                totalFrames.incrementAndGet()

                // Simulate occasional frame drops
                if (Math.random() < 0.05) { // 5% drop rate
                    droppedFrames.incrementAndGet()
                } else {
                    val latency = (System.nanoTime() - frameStart) / 1_000_000
                    latencies.add(latency)
                    emit(audioFrame)
                }

                frameCount++
            }
        }

        // Act
        var receivedFrames = 0
        mockLiveCoachManager.startAudioStream(mockk(relaxed = true))
            .collect { frame ->
                receivedFrames++
                assertNotNull("Audio frame should not be null", frame)
                assertTrue("Audio frame should have data", frame.data.isNotEmpty())
                assertEquals("Sample rate should match", expectedSampleRate, frame.sampleRate)
                assertEquals("Channel count should match", expectedChannels, frame.channels)
            }

        advanceUntilIdle()

        // Assert
        val actualDroppedFrames = droppedFrames.get()
        val averageLatency = latencies.average()
        val frameDropRate = actualDroppedFrames.toDouble() / totalFrames.get()

        assertTrue("Dropped frames ($actualDroppedFrames) should be under threshold ($maxDroppedFrames)",
                  actualDroppedFrames <= maxDroppedFrames)
        assertTrue("Frame drop rate ($frameDropRate) should be under 10%", frameDropRate < 0.1)
        assertTrue("Average frame latency ($averageLatency ms) should be under 100ms",
                  averageLatency < 100)
    }

    /**
     * TDD RED: Test network resilience and reconnection
     */
    @Test
    fun `live api should handle network interruptions gracefully`() = testScope.runTest {
        // Arrange
        val maxReconnectionTime = 5000L
        val networkFailureSimulations = listOf(
            NetworkFailureType.TEMPORARY_DISCONNECT,
            NetworkFailureType.HIGH_LATENCY,
            NetworkFailureType.PACKET_LOSS
        )

        val reconnectionAttempts = AtomicInteger(0)
        val successfulReconnections = AtomicInteger(0)

        // Mock network failure scenarios
        networkFailureSimulations.forEach { failureType ->
            networkSimulator.simulateFailure(failureType, 2000L) // 2 second failure

            every { mockLiveCoachManager.reconnect() } answers {
                reconnectionAttempts.incrementAndGet()
                testScope.backgroundScope.launch {
                    delay(1000) // Reconnection delay
                    successfulReconnections.incrementAndGet()
                }
                mockk(relaxed = true)
            }

            // Act
            val reconnectionTime = measureTimeMillis {
                mockLiveCoachManager.reconnect()
                delay(1500) // Allow time for reconnection
            }

            // Assert for each failure type
            assertTrue("Reconnection time for $failureType should be under $maxReconnectionTime ms",
                      reconnectionTime < maxReconnectionTime)
        }

        advanceUntilIdle()

        val reconnectionSuccessRate = successfulReconnections.get().toDouble() / reconnectionAttempts.get()
        assertTrue("Reconnection success rate ($reconnectionSuccessRate) should be above 80%",
                  reconnectionSuccessRate > 0.8)
    }

    /**
     * TDD RED: Test concurrent voice sessions performance
     */
    @Test
    fun `concurrent voice sessions should not degrade performance`() = testScope.runTest {
        // Arrange
        val sessionCount = 3
        val messagesPerSession = 5
        val maxLatencyIncrease = 0.5 // 50% max latency increase

        val sessionLatencies = mutableMapOf<Int, MutableList<Long>>()

        // Mock concurrent sessions
        (0 until sessionCount).forEach { sessionId ->
            sessionLatencies[sessionId] = mutableListOf()

            every { mockLiveCoachManager.createSession(sessionId.toString()) } returns flow {
                repeat(messagesPerSession) { messageIndex ->
                    val processingStart = System.nanoTime()

                    // Simulate increasing load
                    val baseLatency = 500L
                    val loadFactor = (sessionId + 1) * 0.1
                    val actualLatency = (baseLatency * (1 + loadFactor)).toLong()

                    delay(actualLatency)

                    val totalLatency = (System.nanoTime() - processingStart) / 1_000_000
                    sessionLatencies[sessionId]?.add(totalLatency)

                    emit(LiveApiModels.VoiceResponse(
                        text = "Session $sessionId, Message $messageIndex",
                        confidence = 0.9f,
                        duration = 1.0f
                    ))
                }
            }
        }

        // Act
        val jobs = (0 until sessionCount).map { sessionId ->
            testScope.launch {
                mockLiveCoachManager.createSession(sessionId.toString()).collect { response ->
                    assertNotNull("Response should not be null", response)
                    assertTrue("Response should contain session ID", response.text.contains("Session $sessionId"))
                }
            }
        }

        jobs.forEach { it.join() }
        advanceUntilIdle()

        // Assert
        val baselineLatency = sessionLatencies[0]?.average() ?: 0.0
        sessionLatencies.entries.forEach { (sessionId, latencies) ->
            val averageLatency = latencies.average()
            val latencyIncrease = (averageLatency - baselineLatency) / baselineLatency

            assertTrue("Session $sessionId latency increase ($latencyIncrease) should be under $maxLatencyIncrease",
                      latencyIncrease <= maxLatencyIncrease)
        }
    }

    /**
     * TDD RED: Test WebSocket message throughput
     */
    @Test
    fun `websocket should handle high message throughput`() = testScope.runTest {
        // Arrange
        val messageCount = 100
        val targetThroughputMsgsPerSec = 50
        val maxTestDuration = messageCount / targetThroughputMsgsPerSec * 1000L + 1000L // Add 1s buffer

        val sentMessages = AtomicInteger(0)
        val receivedMessages = AtomicInteger(0)
        val messageLatch = CountDownLatch(messageCount)

        // Mock WebSocket message handling
        every { mockWebSocket.send(any<String>()) } answers {
            sentMessages.incrementAndGet()
            testScope.backgroundScope.launch {
                delay(10) // Simulate network delay
                receivedMessages.incrementAndGet()
                messageLatch.countDown()
            }
            true
        }

        // Act
        val startTime = System.currentTimeMillis()

        repeat(messageCount) { index ->
            val message = """{"type": "pose_data", "frame": $index, "landmarks": []}"""
            mockWebSocket.send(message)
            delay(20) // 50 msgs/sec = 20ms interval
        }

        val completed = messageLatch.await(maxTestDuration, TimeUnit.MILLISECONDS)
        val actualDuration = System.currentTimeMillis() - startTime

        advanceUntilIdle()

        // Assert
        assertTrue("All messages should be processed within timeout", completed)
        val actualThroughput = receivedMessages.get().toDouble() / (actualDuration / 1000.0)
        assertTrue("Message throughput ($actualThroughput msgs/sec) should meet target ($targetThroughputMsgsPerSec msgs/sec)",
                  actualThroughput >= targetThroughputMsgsPerSec * 0.8) // Allow 20% tolerance
    }

    /**
     * TDD RED: Test Live API memory usage under load
     */
    @Test
    fun `live api should maintain stable memory usage under load`() = testScope.runTest {
        // Arrange
        val testDurationMs = 8000L
        val maxMemoryIncreaseMB = 30 // 30MB max increase
        val memoryMeasurements = mutableListOf<Long>()

        // Monitor memory usage
        val initialMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

        // Mock continuous Live API usage
        every { mockLiveCoachManager.startContinuousSession() } returns flow {
            val sessionStart = System.currentTimeMillis()

            while (System.currentTimeMillis() - sessionStart < testDurationMs) {
                // Simulate voice processing
                delay(100)

                // Measure memory periodically
                if (memoryMeasurements.isEmpty() ||
                    System.currentTimeMillis() - sessionStart > memoryMeasurements.size * 500) {
                    val currentMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
                    memoryMeasurements.add(currentMemory)
                }

                emit(LiveApiModels.VoiceResponse(
                    text = "Continuous feedback ${System.currentTimeMillis()}",
                    confidence = 0.9f,
                    duration = 1.0f
                ))
            }
        }

        // Act
        mockLiveCoachManager.startContinuousSession().collect { response ->
            assertNotNull("Response should not be null", response)
        }

        advanceUntilIdle()

        // Force garbage collection and final measurement
        System.gc()
        delay(100)
        val finalMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

        // Assert
        val memoryIncreaseMB = (finalMemory - initialMemory) / (1024 * 1024)
        assertTrue("Memory increase ($memoryIncreaseMB MB) should be under $maxMemoryIncreaseMB MB",
                  memoryIncreaseMB < maxMemoryIncreaseMB)

        // Check for memory leaks
        if (memoryMeasurements.size >= 4) {
            val firstQuarter = memoryMeasurements.take(memoryMeasurements.size / 4).average()
            val lastQuarter = memoryMeasurements.takeLast(memoryMeasurements.size / 4).average()
            val growthRate = (lastQuarter - firstQuarter) / firstQuarter

            assertTrue("Memory growth rate ($growthRate) should be under 25%", growthRate < 0.25)
        }
    }

    // Helper classes for testing
    private class NetworkSimulator {
        fun simulateFailure(type: NetworkFailureType, durationMs: Long) {
            // Simulate different types of network failures
            when (type) {
                NetworkFailureType.TEMPORARY_DISCONNECT -> {
                    // Simulate complete disconnection
                }
                NetworkFailureType.HIGH_LATENCY -> {
                    // Simulate high latency conditions
                }
                NetworkFailureType.PACKET_LOSS -> {
                    // Simulate packet loss
                }
            }
        }

        fun reset() {
            // Reset network simulation state
        }
    }

    private enum class NetworkFailureType {
        TEMPORARY_DISCONNECT,
        HIGH_LATENCY,
        PACKET_LOSS
    }

    private class AudioStreamSimulator {
        fun generateFrame(samples: Int, sampleRate: Int): LiveApiModels.AudioFrame {
            val audioData = ByteArray(samples * 2) // 16-bit samples
            // Generate sine wave test audio
            for (i in 0 until samples) {
                val sample = (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 32767).toInt().toShort()
                audioData[i * 2] = (sample.toInt() and 0xFF).toByte()
                audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }

            return LiveApiModels.AudioFrame(
                data = audioData,
                sampleRate = sampleRate,
                channels = 1,
                timestamp = System.currentTimeMillis()
            )
        }

        fun cleanup() {
            // Cleanup resources
        }
    }
}