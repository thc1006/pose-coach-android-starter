package com.posecoach.corepose.stability

import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.PoseLandmarks
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.math.sin
import kotlin.math.cos

/**
 * Comprehensive test suite for EnhancedStablePoseGate.
 * Tests stability detection algorithms, performance, and edge cases.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnhancedStablePoseGateTest {

    private lateinit var stablePoseGate: EnhancedStablePoseGate
    private var testTimestamp = System.currentTimeMillis()

    // Test configurations
    private val strictConfig = EnhancedStablePoseGate.StabilityConfig(
        windowSec = 1.0,
        positionThresholdNormalized = 0.005,
        velocityThresholdPerSec = 0.01,
        accelerationThresholdPerSec2 = 0.02,
        angleThresholdDeg = 3.0,
        stabilityScoreThreshold = 0.9
    )

    private val relaxedConfig = EnhancedStablePoseGate.StabilityConfig(
        windowSec = 0.5,
        positionThresholdNormalized = 0.02,
        velocityThresholdPerSec = 0.05,
        accelerationThresholdPerSec2 = 0.1,
        angleThresholdDeg = 10.0,
        stabilityScoreThreshold = 0.7
    )

    @BeforeEach
    fun setup() {
        stablePoseGate = EnhancedStablePoseGate()
        testTimestamp = System.currentTimeMillis()
    }

    @AfterEach
    fun tearDown() {
        stablePoseGate.reset()
    }

    @Nested
    @DisplayName("Basic Stability Detection Tests")
    inner class BasicStabilityTests {

        @Test
        fun `should not trigger on first frame`() {
            // Given
            val poseResult = createStablePoseResult(testTimestamp)

            // When
            val result = stablePoseGate.update(poseResult)

            // Then
            assertFalse(result.isStable, "Should not be stable on first frame")
            assertFalse(result.justTriggered, "Should not trigger on first frame")
            assertEquals(0.0, result.stabilityScore, 0.001, "Initial stability score should be 0")
        }

        @Test
        fun `should detect stability with consistent poses`() {
            // Given
            val basePose = createStablePoseResult(testTimestamp)

            // When - feed consistent poses for stability window
            var lastResult: EnhancedStablePoseGate.StabilityResult? = null
            repeat(50) { frame ->
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L) // 30 FPS
                lastResult = stablePoseGate.update(poseResult)
            }

            // Then
            assertNotNull(lastResult, "Should have result")
            assertTrue(lastResult!!.isStable, "Should detect stability with consistent poses")
            assertTrue(lastResult!!.stabilityScore > 0.8, "Stability score should be high")
        }

        @Test
        fun `should trigger exactly once when stability window is reached`() {
            // Given
            var triggerCount = 0
            val windowFrames = (1.5 * 30).toInt() // 1.5 seconds at 30 FPS

            // When
            repeat(windowFrames + 10) { frame ->
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L)
                val result = stablePoseGate.update(poseResult)

                if (result.justTriggered) {
                    triggerCount++
                }
            }

            // Then
            assertEquals(1, triggerCount, "Should trigger exactly once")
        }

        @Test
        fun `should reset stability on movement`() {
            // Given - establish stability
            repeat(50) { frame ->
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L)
                stablePoseGate.update(poseResult)
            }

            // When - introduce movement
            val movingPose = createStablePoseResult(testTimestamp + 51 * 33L).copy(
                landmarks = createMovedLandmarks(0.05f) // Significant movement
            )
            val result = stablePoseGate.update(movingPose)

            // Then
            assertFalse(result.isStable, "Should lose stability with movement")
            assertTrue(result.stabilityScore < 0.5, "Stability score should drop")
        }
    }

    @Nested
    @DisplayName("Advanced Stability Metrics Tests")
    inner class AdvancedMetricsTests {

        @Test
        fun `should calculate position stability correctly`() {
            // Given
            stablePoseGate = EnhancedStablePoseGate(strictConfig)

            // When - feed slightly varying poses
            var lastResult: EnhancedStablePoseGate.StabilityResult? = null
            repeat(20) { frame ->
                val noise = 0.001f * sin(frame * 0.1) // Small sinusoidal movement
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L).copy(
                    landmarks = createNoisyLandmarks(noise)
                )
                lastResult = stablePoseGate.update(poseResult)
            }

            // Then
            assertNotNull(lastResult, "Should have result")
            assertTrue(lastResult!!.metrics.positionStability > 0.7,
                "Position stability should be high with minimal noise")
        }

        @Test
        fun `should calculate velocity stability correctly`() {
            // Given
            val results = mutableListOf<EnhancedStablePoseGate.StabilityResult>()

            // When - constant velocity movement
            repeat(30) { frame ->
                val offset = frame * 0.002f // Constant slow movement
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L).copy(
                    landmarks = createMovedLandmarks(offset)
                )
                results.add(stablePoseGate.update(poseResult))
            }

            // Then
            val lastResult = results.last()
            assertTrue(lastResult.metrics.velocityStability > 0.0,
                "Should calculate velocity stability")
            assertTrue(lastResult.metrics.velocityStability < 1.0,
                "Velocity stability should be reduced with movement")
        }

        @Test
        fun `should calculate acceleration stability correctly`() {
            // Given
            val results = mutableListOf<EnhancedStablePoseGate.StabilityResult>()

            // When - accelerating movement
            repeat(30) { frame ->
                val offset = frame * frame * 0.0001f // Quadratic movement (acceleration)
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L).copy(
                    landmarks = createMovedLandmarks(offset)
                )
                results.add(stablePoseGate.update(poseResult))
            }

            // Then
            val lastResult = results.last()
            assertTrue(lastResult.metrics.accelerationStability >= 0.0,
                "Should calculate acceleration stability")
            assertTrue(lastResult.metrics.accelerationStability < 0.8,
                "Acceleration stability should be low with acceleration")
        }

        @Test
        fun `should calculate angular stability correctly`() {
            // Given
            val results = mutableListOf<EnhancedStablePoseGate.StabilityResult>()

            // When - rotating poses
            repeat(30) { frame ->
                val angle = frame * 0.1 // Gradual rotation
                val poseResult = createRotatedPoseResult(testTimestamp + frame * 33L, angle)
                results.add(stablePoseGate.update(poseResult))
            }

            // Then
            val lastResult = results.last()
            assertTrue(lastResult.metrics.angularStability >= 0.0,
                "Should calculate angular stability")
            assertTrue(lastResult.metrics.angularStability < 0.8,
                "Angular stability should be reduced with rotation")
        }

        @Test
        fun `should calculate keypoint-specific stability`() {
            // Given
            repeat(20) { frame ->
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L)
                stablePoseGate.update(poseResult)
            }

            // When
            val result = stablePoseGate.getCurrentStability()

            // Then
            assertNotNull(result, "Should have current stability")
            assertTrue(result!!.metrics.keyPointStabilities.isNotEmpty(),
                "Should calculate keypoint stabilities")

            // Check specific keypoints
            assertTrue(result.metrics.keyPointStabilities.containsKey(PoseLandmarks.LEFT_SHOULDER))
            assertTrue(result.metrics.keyPointStabilities.containsKey(PoseLandmarks.RIGHT_SHOULDER))
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    inner class ConfigurationTests {

        @Test
        fun `should respect strict configuration`() {
            // Given
            stablePoseGate = EnhancedStablePoseGate(strictConfig)

            // When - small movements that would pass relaxed config
            var stable = false
            repeat(60) { frame ->
                val noise = 0.01f * sin(frame * 0.2) // Movement that should fail strict config
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L).copy(
                    landmarks = createNoisyLandmarks(noise)
                )
                val result = stablePoseGate.update(poseResult)
                if (result.isStable) stable = true
            }

            // Then
            assertFalse(stable, "Strict config should not detect stability with noise")
        }

        @Test
        fun `should respect relaxed configuration`() {
            // Given
            stablePoseGate = EnhancedStablePoseGate(relaxedConfig)

            // When - small movements that should pass relaxed config
            var triggered = false
            repeat(40) { frame ->
                val noise = 0.01f * sin(frame * 0.2) // Movement that should pass relaxed config
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L).copy(
                    landmarks = createNoisyLandmarks(noise)
                )
                val result = stablePoseGate.update(poseResult)
                if (result.justTriggered) triggered = true
            }

            // Then
            assertTrue(triggered, "Relaxed config should detect stability with small noise")
        }

        @Test
        fun `should respect custom keypoint weights`() {
            // Given
            val customWeights = mapOf(
                PoseLandmarks.NOSE to 0.1, // Very low weight for nose
                PoseLandmarks.LEFT_HIP to 3.0 // Very high weight for hip
            )
            val customConfig = relaxedConfig.copy(keyPointWeights = customWeights)
            stablePoseGate = EnhancedStablePoseGate(customConfig)

            // When - move only the nose significantly
            var lastResult: EnhancedStablePoseGate.StabilityResult? = null
            repeat(30) { frame ->
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L)
                // Move only the nose
                val modifiedLandmarks = poseResult.landmarks.toMutableList()
                modifiedLandmarks[PoseLandmarks.NOSE] = modifiedLandmarks[PoseLandmarks.NOSE].copy(
                    x = 0.5f + frame * 0.01f // Significant nose movement
                )

                lastResult = stablePoseGate.update(poseResult.copy(landmarks = modifiedLandmarks))
            }

            // Then
            assertNotNull(lastResult, "Should have result")
            assertTrue(lastResult!!.stabilityScore > 0.7,
                "Should maintain high stability despite nose movement due to low weight")
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    inner class EdgeCasesTests {

        @Test
        fun `should handle missing landmarks gracefully`() {
            // Given
            val incompleteLandmarks = (0..20).map { index -> // Only 21 landmarks instead of 33
                PoseLandmarkResult.Landmark(
                    x = 0.5f, y = 0.5f, z = 0.1f,
                    visibility = 0.9f, presence = 0.9f
                )
            }
            val poseResult = PoseLandmarkResult(
                landmarks = incompleteLandmarks,
                worldLandmarks = incompleteLandmarks,
                timestampMs = testTimestamp,
                inferenceTimeMs = 25L
            )

            // When/Then - should not throw exception
            assertDoesNotThrow {
                stablePoseGate.update(poseResult)
            }
        }

        @Test
        fun `should handle extreme timestamp values`() {
            // Given
            val extremeTimestamps = listOf(0L, Long.MAX_VALUE, -1L)

            // When/Then - should handle extreme timestamps gracefully
            assertDoesNotThrow {
                extremeTimestamps.forEach { timestamp ->
                    val poseResult = createStablePoseResult(timestamp)
                    stablePoseGate.update(poseResult)
                }
            }
        }

        @Test
        fun `should handle invalid landmark coordinates`() {
            // Given
            val invalidLandmarks = (0..32).map { index ->
                PoseLandmarkResult.Landmark(
                    x = if (index % 3 == 0) Float.NaN else 0.5f,
                    y = if (index % 3 == 1) Float.POSITIVE_INFINITY else 0.5f,
                    z = if (index % 3 == 2) Float.NEGATIVE_INFINITY else 0.1f,
                    visibility = 0.9f,
                    presence = 0.9f
                )
            }
            val poseResult = PoseLandmarkResult(
                landmarks = invalidLandmarks,
                worldLandmarks = invalidLandmarks,
                timestampMs = testTimestamp,
                inferenceTimeMs = 25L
            )

            // When/Then - should handle invalid coordinates gracefully
            assertDoesNotThrow {
                stablePoseGate.update(poseResult)
            }
        }

        @Test
        fun `should handle rapid frame rate changes`() {
            // Given
            val intervals = listOf(16L, 33L, 100L, 5L, 50L) // Varying frame intervals

            // When/Then - should handle varying frame rates
            assertDoesNotThrow {
                intervals.forEachIndexed { index, interval ->
                    val timestamp = testTimestamp + intervals.take(index + 1).sum()
                    val poseResult = createStablePoseResult(timestamp)
                    stablePoseGate.update(poseResult)
                }
            }
        }

        @Test
        fun `should handle very long time gaps`() {
            // Given
            val poseResult1 = createStablePoseResult(testTimestamp)
            val poseResult2 = createStablePoseResult(testTimestamp + 10000L) // 10 second gap

            // When
            stablePoseGate.update(poseResult1)
            val result = stablePoseGate.update(poseResult2)

            // Then - should reset stability tracking after long gap
            assertFalse(result.isStable, "Should reset stability after long time gap")
            assertEquals(0.0, result.metrics.timeStable, 0.001, "Stable time should reset")
        }
    }

    @Nested
    @DisplayName("State Management Tests")
    inner class StateManagementTests {

        @Test
        fun `should reset state correctly`() {
            // Given - establish some history
            repeat(20) { frame ->
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L)
                stablePoseGate.update(poseResult)
            }

            // When
            stablePoseGate.reset()
            val poseResult = createStablePoseResult(testTimestamp + 1000L)
            val result = stablePoseGate.update(poseResult)

            // Then
            assertFalse(result.isStable, "Should not be stable after reset")
            assertEquals(0.0, result.stabilityScore, 0.001, "Stability score should be 0 after reset")
            assertEquals(0.0, result.metrics.timeStable, 0.001, "Stable time should be 0 after reset")
        }

        @Test
        fun `should maintain state across getCurrentStability calls`() {
            // Given
            repeat(15) { frame ->
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L)
                stablePoseGate.update(poseResult)
            }

            // When
            val currentStability1 = stablePoseGate.getCurrentStability()
            val currentStability2 = stablePoseGate.getCurrentStability()

            // Then
            assertNotNull(currentStability1, "Should have current stability")
            assertNotNull(currentStability2, "Should have current stability")
            assertEquals(currentStability1!!.stabilityScore, currentStability2!!.stabilityScore, 0.001,
                "getCurrentStability should not change state")
        }

        @Test
        fun `should return null for getCurrentStability without history`() {
            // When
            val currentStability = stablePoseGate.getCurrentStability()

            // Then
            assertNull(currentStability, "Should return null without history")
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    inner class PerformanceTests {

        @Test
        fun `should process frames efficiently`() {
            // Given
            val frameCount = 1000
            val frames = (0 until frameCount).map { frame ->
                createStablePoseResult(testTimestamp + frame * 33L)
            }

            // When
            val startTime = System.currentTimeMillis()
            frames.forEach { poseResult ->
                stablePoseGate.update(poseResult)
            }
            val endTime = System.currentTimeMillis()

            // Then
            val processingTime = endTime - startTime
            val avgTimePerFrame = processingTime.toDouble() / frameCount

            assertTrue(avgTimePerFrame < 1.0, // Less than 1ms per frame
                "Should process frames efficiently: ${avgTimePerFrame}ms per frame")
        }

        @Test
        fun `should maintain consistent performance with history`() {
            // Given
            val results = mutableListOf<Long>()

            // When - measure processing time as history builds up
            repeat(100) { frame ->
                val poseResult = createStablePoseResult(testTimestamp + frame * 33L)

                val startTime = System.nanoTime()
                stablePoseGate.update(poseResult)
                val endTime = System.nanoTime()

                results.add(endTime - startTime)
            }

            // Then - processing time should remain relatively stable
            val firstTenAvg = results.take(10).average()
            val lastTenAvg = results.takeLast(10).average()

            val performanceDegradation = (lastTenAvg - firstTenAvg) / firstTenAvg

            assertTrue(performanceDegradation < 2.0, // Less than 200% degradation
                "Performance should not degrade significantly with history")
        }
    }

    // Helper functions for creating test data

    private fun createStablePoseResult(timestampMs: Long): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f,
                y = 0.2f + (index / 5) * 0.1f,
                z = 0.1f,
                visibility = 0.9f,
                presence = 0.95f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = 25L
        )
    }

    private fun createNoisyLandmarks(noise: Float): List<PoseLandmarkResult.Landmark> {
        return (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f + noise,
                y = 0.2f + (index / 5) * 0.1f + noise,
                z = 0.1f + noise * 0.1f,
                visibility = 0.9f,
                presence = 0.95f
            )
        }
    }

    private fun createMovedLandmarks(offset: Float): List<PoseLandmarkResult.Landmark> {
        return (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f + offset,
                y = 0.2f + (index / 5) * 0.1f + offset,
                z = 0.1f,
                visibility = 0.9f,
                presence = 0.95f
            )
        }
    }

    private fun createRotatedPoseResult(timestampMs: Long, angle: Double): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            val baseX = 0.3f + (index % 5) * 0.1f
            val baseY = 0.2f + (index / 5) * 0.1f

            // Rotate around center point (0.5, 0.5)
            val centerX = 0.5f
            val centerY = 0.5f
            val relX = baseX - centerX
            val relY = baseY - centerY

            val rotatedX = (relX * cos(angle) - relY * sin(angle)).toFloat() + centerX
            val rotatedY = (relX * sin(angle) + relY * cos(angle)).toFloat() + centerY

            PoseLandmarkResult.Landmark(
                x = rotatedX,
                y = rotatedY,
                z = 0.1f,
                visibility = 0.9f,
                presence = 0.95f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = 25L
        )
    }
}