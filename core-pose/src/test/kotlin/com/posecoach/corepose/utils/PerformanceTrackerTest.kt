package com.posecoach.corepose.utils

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class PerformanceTrackerTest {
    private lateinit var tracker: PerformanceTracker

    @Before
    fun setup() {
        tracker = PerformanceTracker(windowSize = 10, targetFps = 30)
    }

    @Test
    fun `recordInferenceTime should track inference times`() {
        tracker.recordInferenceTime(20)
        tracker.recordInferenceTime(25)
        tracker.recordInferenceTime(30)

        val metrics = tracker.getMetrics()

        assertEquals(25.0, metrics.avgInferenceTimeMs, 0.1)
        assertEquals(20L, metrics.minInferenceTimeMs)
        assertEquals(30L, metrics.maxInferenceTimeMs)
    }

    @Test
    fun `window size should limit tracked samples`() {
        for (i in 1..20) {
            tracker.recordInferenceTime(i.toLong())
        }

        val metrics = tracker.getMetrics()
        assertEquals(15.5, metrics.avgInferenceTimeMs, 0.1)
        assertEquals(11L, metrics.minInferenceTimeMs)
        assertEquals(20L, metrics.maxInferenceTimeMs)
    }

    @Test
    fun `isPerformanceGood should detect good performance`() {
        for (i in 1..5) {
            tracker.recordInferenceTime(15)
            Thread.sleep(33)
        }

        val metrics = tracker.getMetrics()
        assertTrue(metrics.isPerformanceGood)
        assertEquals(15.0, metrics.avgInferenceTimeMs, 0.1)
    }

    @Test
    fun `isPerformanceGood should detect poor performance`() {
        for (i in 1..5) {
            tracker.recordInferenceTime(50)
            Thread.sleep(33)
        }

        val metrics = tracker.getMetrics()
        assertFalse(metrics.isPerformanceGood)
        assertEquals(50.0, metrics.avgInferenceTimeMs, 0.1)
    }

    @Test
    fun `reset should clear all metrics`() {
        tracker.recordInferenceTime(20)
        tracker.recordInferenceTime(25)

        tracker.reset()
        val metrics = tracker.getMetrics()

        assertEquals(0.0, metrics.avgInferenceTimeMs, 0.1)
        assertEquals(0L, metrics.minInferenceTimeMs)
        assertEquals(0L, metrics.maxInferenceTimeMs)
        assertEquals(0.0, metrics.avgFps, 0.1)
    }

    @Test
    fun `getMetrics should return empty metrics when no data`() {
        val metrics = tracker.getMetrics()

        assertEquals(0.0, metrics.avgInferenceTimeMs, 0.1)
        assertEquals(0L, metrics.minInferenceTimeMs)
        assertEquals(0L, metrics.maxInferenceTimeMs)
        assertEquals(0.0, metrics.avgFps, 0.1)
        assertEquals(0, metrics.droppedFrames)
        assertFalse(metrics.isPerformanceGood)
    }
}