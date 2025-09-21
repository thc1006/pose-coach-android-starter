package com.posecoach.corepose

import com.posecoach.corepose.models.PoseLandmarkResult
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class EnhancedStablePoseGateTest {

    private lateinit var gate: EnhancedStablePoseGate

    @Before
    fun setup() {
        gate = EnhancedStablePoseGate(
            windowSec = 1.0,
            posThreshold = 0.02f,
            angleThresholdDeg = 5.0f
        )
    }

    @Test
    fun `stable sequence should trigger after window time`() {
        var triggered = false
        var baseTime = 0L

        repeat(12) { i ->
            val result = gate.update(createStableLandmarks(baseTime))
            if (result.justTriggered) {
                triggered = true
                assertEquals(1.0, result.stabilityDuration, 0.1)
            }
            baseTime += 100
        }

        assertTrue("Should have triggered after 1 second", triggered)
    }

    @Test
    fun `unstable sequence should not trigger`() {
        var baseTime = 0L

        repeat(20) { i ->
            val landmarks = if (i % 3 == 0) {
                createUnstableLandmarks(baseTime)
            } else {
                createStableLandmarks(baseTime)
            }

            val result = gate.update(landmarks)
            assertFalse("Should not trigger with unstable poses", result.justTriggered)
            baseTime += 100
        }
    }

    @Test
    fun `trigger should occur exactly once`() {
        var triggerCount = 0
        var baseTime = 0L

        repeat(30) { i ->
            val result = gate.update(createStableLandmarks(baseTime))
            if (result.justTriggered) {
                triggerCount++
            }
            baseTime += 100
        }

        assertEquals("Should trigger exactly once", 1, triggerCount)
    }

    @Test
    fun `reset should clear accumulated time`() {
        var baseTime = 0L

        repeat(5) {
            gate.update(createStableLandmarks(baseTime))
            baseTime += 100
        }

        gate.reset()

        val result = gate.update(createStableLandmarks(baseTime))
        assertEquals(0.0, result.stabilityDuration, 0.01)
        assertFalse(result.isStable)
    }

    @Test
    fun `low confidence should prevent triggering`() {
        val gate = EnhancedStablePoseGate(
            windowSec = 0.5,
            minVisibility = 0.8f
        )

        var baseTime = 0L
        var triggered = false

        repeat(10) {
            val result = gate.update(createLowConfidenceLandmarks(baseTime))
            if (result.justTriggered) {
                triggered = true
            }
            baseTime += 100
        }

        assertFalse("Should not trigger with low confidence", triggered)
    }

    @Test
    fun `position delta should be calculated correctly`() {
        var baseTime = 0L

        gate.update(createStableLandmarks(baseTime))
        baseTime += 100

        val movedLandmarks = createMovedLandmarks(baseTime, 0.05f, 0f)
        val result = gate.update(movedLandmarks)

        assertTrue("Position delta should be detected", result.positionDelta > 0.04f)
        assertFalse("Should not be stable with large movement", result.isStable)
    }

    @Test
    fun `angle delta should be calculated correctly`() {
        var baseTime = 0L

        gate.update(createStableLandmarks(baseTime))
        baseTime += 100

        val rotatedLandmarks = createRotatedLandmarks(baseTime, 10f)
        val result = gate.update(rotatedLandmarks)

        assertTrue("Angle delta should be detected", result.angleDelta > 8f)
        assertFalse("Should not be stable with large rotation", result.isStable)
    }

    @Test
    fun `stability duration should accumulate correctly`() {
        var baseTime = 0L
        val durations = mutableListOf<Double>()

        repeat(15) {
            val result = gate.update(createStableLandmarks(baseTime))
            durations.add(result.stabilityDuration)
            baseTime += 100
        }

        assertTrue("Duration should increase", durations.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("Final duration should be around 1.4s", durations.last() >= 1.3)
    }

    @Test
    fun `confidence score should reflect visible landmarks`() {
        val fullVisibility = createStableLandmarks(0L)
        val resultFull = gate.update(fullVisibility)
        assertTrue("Full visibility should have high confidence", resultFull.confidenceScore > 0.9f)

        gate.reset()

        val partialVisibility = createPartiallyVisibleLandmarks(100L)
        val resultPartial = gate.update(partialVisibility)
        assertTrue("Partial visibility should have lower confidence",
                  resultPartial.confidenceScore < 0.7f)
    }

    @Test
    fun `large time gap should reset state`() {
        var baseTime = 0L

        repeat(5) {
            gate.update(createStableLandmarks(baseTime))
            baseTime += 100
        }

        baseTime += 2000

        val result = gate.update(createStableLandmarks(baseTime))
        assertEquals(0.0, result.stabilityDuration, 0.01)
        assertFalse(result.isStable)
    }

    private fun createStableLandmarks(timestampMs: Long): PoseLandmarkResult {
        val landmarks = List(33) { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index * 0.001f),
                y = 0.5f + (index * 0.001f),
                z = 0.0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }
        return PoseLandmarkResult(landmarks, landmarks, timestampMs, 20)
    }

    private fun createUnstableLandmarks(timestampMs: Long): PoseLandmarkResult {
        val landmarks = List(33) { index ->
            val offset = (0..100).random() / 1000f
            PoseLandmarkResult.Landmark(
                x = 0.5f + offset,
                y = 0.5f + offset,
                z = 0.0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }
        return PoseLandmarkResult(landmarks, landmarks, timestampMs, 20)
    }

    private fun createLowConfidenceLandmarks(timestampMs: Long): PoseLandmarkResult {
        val landmarks = List(33) { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f,
                y = 0.5f,
                z = 0.0f,
                visibility = 0.3f,
                presence = 0.3f
            )
        }
        return PoseLandmarkResult(landmarks, landmarks, timestampMs, 20)
    }

    private fun createMovedLandmarks(
        timestampMs: Long,
        xOffset: Float,
        yOffset: Float
    ): PoseLandmarkResult {
        val landmarks = List(33) { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + xOffset + (index * 0.001f),
                y = 0.5f + yOffset + (index * 0.001f),
                z = 0.0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }
        return PoseLandmarkResult(landmarks, landmarks, timestampMs, 20)
    }

    private fun createRotatedLandmarks(timestampMs: Long, angleDeg: Float): PoseLandmarkResult {
        val landmarks = List(33) { index ->
            val angle = Math.toRadians(angleDeg.toDouble())
            val baseX = 0.5f + (index * 0.01f)
            val baseY = 0.5f + (index * 0.01f)

            PoseLandmarkResult.Landmark(
                x = (baseX * Math.cos(angle) - baseY * Math.sin(angle)).toFloat(),
                y = (baseX * Math.sin(angle) + baseY * Math.cos(angle)).toFloat(),
                z = 0.0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }
        return PoseLandmarkResult(landmarks, landmarks, timestampMs, 20)
    }

    private fun createPartiallyVisibleLandmarks(timestampMs: Long): PoseLandmarkResult {
        val landmarks = List(33) { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f,
                y = 0.5f,
                z = 0.0f,
                visibility = if (index < 20) 0.9f else 0.1f,
                presence = if (index < 20) 0.9f else 0.1f
            )
        }
        return PoseLandmarkResult(landmarks, landmarks, timestampMs, 20)
    }
}