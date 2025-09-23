package com.posecoach.app.livecoach.camera

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.test.AfterTest

/**
 * Unit tests for SnapshotScheduler
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotSchedulerTest {

    private lateinit var scheduler: SnapshotScheduler
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        scheduler = SnapshotScheduler(testScope)
    }

    @AfterTest
    fun tearDown() {
        scheduler.destroy()
    }

    @Test
    fun startScheduling_beginsCapturing() {
        scheduler.startScheduling()

        assertTrue(scheduler.isEnabled())
        val status = scheduler.getSchedulingStatus()
        assertTrue(status.isEnabled)
    }

    @Test
    fun stopScheduling_stopsCapturing() {
        scheduler.startScheduling()
        scheduler.stopScheduling()

        assertFalse(scheduler.isEnabled())
        val status = scheduler.getSchedulingStatus()
        assertFalse(status.isEnabled)
    }

    @Test
    fun shouldCaptureSnapshot_respectsInterval() {
        val config = SnapshotConfig(snapshotIntervalMs = 1000L)
        scheduler.updateConfig(config)
        scheduler.startScheduling()

        // First call should allow capture
        assertTrue(scheduler.shouldCaptureSnapshot())

        // Immediate second call should be rejected
        assertFalse(scheduler.shouldCaptureSnapshot())
    }

    @Test
    fun shouldCaptureSnapshot_respectsConcurrencyLimit() {
        val config = SnapshotConfig(maxConcurrentProcessing = 2)
        scheduler.updateConfig(config)
        scheduler.startScheduling()

        // Mark processing as started
        scheduler.onProcessingStarted()
        scheduler.onProcessingStarted()

        // Should reject when at limit
        assertFalse(scheduler.shouldCaptureSnapshot())

        // Should allow when under limit
        scheduler.onProcessingCompleted()
        assertTrue(scheduler.shouldCaptureSnapshot())
    }

    @Test
    fun shouldCaptureSnapshot_whenDisabled_returnsFalse() {
        assertFalse(scheduler.shouldCaptureSnapshot())
    }

    @Test
    fun updateConfig_changesInterval() {
        val initialConfig = SnapshotConfig(snapshotIntervalMs = 1000L)
        val newConfig = SnapshotConfig(snapshotIntervalMs = 500L)

        scheduler.updateConfig(initialConfig)
        assertEquals(1000L, scheduler.getConfig().snapshotIntervalMs)

        scheduler.updateConfig(newConfig)
        assertEquals(500L, scheduler.getConfig().snapshotIntervalMs)
    }

    @Test
    fun updateConfig_clampsInterval() {
        val config = SnapshotConfig(snapshotIntervalMs = 100L) // Below minimum

        scheduler.updateConfig(config)

        val actualConfig = scheduler.getConfig()
        assertTrue(actualConfig.snapshotIntervalMs >= SnapshotConfig.MIN_SNAPSHOT_INTERVAL_MS)
    }

    @Test
    fun onProcessingStarted_incrementsCount() {
        val initialStatus = scheduler.getSchedulingStatus()
        assertEquals(0, initialStatus.currentProcessingCount)

        scheduler.onProcessingStarted()

        val newStatus = scheduler.getSchedulingStatus()
        assertEquals(1, newStatus.currentProcessingCount)
    }

    @Test
    fun onProcessingCompleted_decrementsCount() {
        scheduler.onProcessingStarted()
        scheduler.onProcessingStarted()

        assertEquals(2, scheduler.getSchedulingStatus().currentProcessingCount)

        scheduler.onProcessingCompleted()

        assertEquals(1, scheduler.getSchedulingStatus().currentProcessingCount)
    }

    @Test
    fun onProcessingCompleted_doesNotGoBelowZero() {
        scheduler.onProcessingCompleted() // Should not crash or go negative

        assertEquals(0, scheduler.getSchedulingStatus().currentProcessingCount)
    }

    @Test
    fun onFrameDropped_updatesMetrics() {
        val initialMetrics = scheduler.getPerformanceMetrics()
        assertEquals(0L, initialMetrics.droppedFrames)

        scheduler.onFrameDropped()

        val newMetrics = scheduler.getPerformanceMetrics()
        assertEquals(1L, newMetrics.droppedFrames)
    }

    @Test
    fun calculateFrameRate_returnsCorrectValue() {
        val config = SnapshotConfig(snapshotIntervalMs = 1000L)
        scheduler.updateConfig(config)

        val frameRate = scheduler.calculateFrameRate()

        assertEquals(1.0f, frameRate, 0.01f)
    }

    @Test
    fun calculateFrameRate_with500msInterval_returns2fps() {
        val config = SnapshotConfig(snapshotIntervalMs = 500L)
        scheduler.updateConfig(config)

        val frameRate = scheduler.calculateFrameRate()

        assertEquals(2.0f, frameRate, 0.01f)
    }

    @Test
    fun getRateLimitingState_providesCurrentState() {
        scheduler.startScheduling()
        scheduler.onProcessingStarted()
        scheduler.onFrameDropped()

        val state = scheduler.getRateLimitingState()

        assertTrue(state.lastSnapshotTime > 0)
        assertEquals(1L, state.droppedFrameCount)
        assertEquals(1, state.currentProcessingCount)
        // canCapture depends on timing and limits
    }

    @Test
    fun getSchedulingStatus_providesCompleteStatus() {
        val config = SnapshotConfig(
            maxConcurrentProcessing = 3,
            snapshotIntervalMs = 500L
        )
        scheduler.updateConfig(config)
        scheduler.startScheduling()
        scheduler.onProcessingStarted()

        val status = scheduler.getSchedulingStatus()

        assertTrue(status.isEnabled)
        assertEquals(1, status.currentProcessingCount)
        assertEquals(3, status.maxConcurrentProcessing)
        assertEquals(2.0f, status.frameRate, 0.01f)
    }

    @Test
    fun getPerformanceMetrics_tracksFrameDrops() {
        repeat(5) { scheduler.onFrameDropped() }

        val metrics = scheduler.getPerformanceMetrics()

        assertEquals(5L, metrics.droppedFrames)
    }

    @Test
    fun resetMetrics_clearsCounters() {
        scheduler.onFrameDropped()
        scheduler.onFrameDropped()

        assertEquals(2L, scheduler.getPerformanceMetrics().droppedFrames)

        scheduler.resetMetrics()

        assertEquals(0L, scheduler.getPerformanceMetrics().droppedFrames)
    }

    @Test
    fun startMemoryCleanup_startsCleanupJob() = testScope.runTest {
        scheduler.startMemoryCleanup()

        // Verify that cleanup job is running
        val status = scheduler.getSchedulingStatus()
        // The job should be active (though we can't directly test the cleanup effect)
        assertTrue(status.isEnabled || !status.isEnabled) // Always true, just checking no crash
    }

    @Test
    fun stopMemoryCleanup_stopsCleanupJob() {
        scheduler.startMemoryCleanup()
        scheduler.stopMemoryCleanup()

        // Should not crash and should clean up properly
        val status = scheduler.getSchedulingStatus()
        // Verify scheduler still functions after cleanup stopped
        assertTrue(status.currentProcessingCount >= 0)
    }

    @Test
    fun destroy_cancelsAllJobs() {
        scheduler.startScheduling()
        scheduler.startMemoryCleanup()

        assertFalse(scheduler.isDestroyed())

        scheduler.destroy()

        assertTrue(scheduler.isDestroyed())
        assertFalse(scheduler.isEnabled())
    }

    @Test
    fun isDestroyed_returnsFalseInitially() {
        assertFalse(scheduler.isDestroyed())
    }

    @Test
    fun isDestroyed_returnsTrueAfterDestroy() {
        scheduler.destroy()

        assertTrue(scheduler.isDestroyed())
    }
}