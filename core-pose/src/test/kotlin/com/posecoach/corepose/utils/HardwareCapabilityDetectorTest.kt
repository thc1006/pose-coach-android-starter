package com.posecoach.corepose.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Test suite for HardwareCapabilityDetector.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HardwareCapabilityDetectorTest {

    private lateinit var detector: HardwareCapabilityDetector
    private lateinit var mockContext: Context
    private lateinit var mockActivityManager: ActivityManager

    @BeforeEach
    fun setup() {
        detector = HardwareCapabilityDetector()
        mockContext = mockk(relaxed = true)
        mockActivityManager = mockk(relaxed = true)

        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
    }

    @Nested
    @DisplayName("Capability Detection Tests")
    inner class CapabilityDetectionTests {

        @Test
        fun `should detect high-end device capabilities`() {
            // Given - high-end device specs
            setupHighEndDevice()

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertEquals(HardwareCapabilityDetector.HardwareCapabilities.PerformanceClass.HIGH_END,
                capabilities.performanceClass)
            assertTrue(capabilities.isHighEnd, "Should be classified as high-end")
            assertFalse(capabilities.isLowEnd, "Should not be low-end")
            assertTrue(capabilities.recommendedSettings.useGpuAcceleration,
                "Should recommend GPU acceleration")
            assertTrue(capabilities.recommendedSettings.targetFps >= 24,
                "Should recommend high FPS")
        }

        @Test
        fun `should detect low-end device capabilities`() {
            // Given - low-end device specs
            setupLowEndDevice()

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertEquals(HardwareCapabilityDetector.HardwareCapabilities.PerformanceClass.LOW_END,
                capabilities.performanceClass)
            assertTrue(capabilities.isLowEnd, "Should be classified as low-end")
            assertFalse(capabilities.isHighEnd, "Should not be high-end")
            assertFalse(capabilities.recommendedSettings.useGpuAcceleration,
                "Should not recommend GPU acceleration")
            assertTrue(capabilities.recommendedSettings.targetFps <= 20,
                "Should recommend conservative FPS")
        }

        @Test
        fun `should detect mid-range device capabilities`() {
            // Given - mid-range device specs
            setupMidRangeDevice()

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertEquals(HardwareCapabilityDetector.HardwareCapabilities.PerformanceClass.MID_RANGE,
                capabilities.performanceClass)
            assertTrue(capabilities.isMidRange, "Should be classified as mid-range")
            assertFalse(capabilities.isHighEnd, "Should not be high-end")
            assertFalse(capabilities.isLowEnd, "Should not be low-end")
            assertTrue(capabilities.recommendedSettings.targetFps in 15..30,
                "Should recommend moderate FPS")
        }

        @Test
        fun `should handle memory information correctly`() {
            // Given
            val memInfo = ActivityManager.MemoryInfo().apply {
                totalMem = 8L * 1024 * 1024 * 1024 // 8GB
                availMem = 4L * 1024 * 1024 * 1024 // 4GB available
                lowMemory = false
                threshold = 512L * 1024 * 1024 // 512MB threshold
            }
            every { mockActivityManager.getMemoryInfo(any()) } answers {
                val info = firstArg<ActivityManager.MemoryInfo>()
                info.totalMem = memInfo.totalMem
                info.availMem = memInfo.availMem
                info.lowMemory = memInfo.lowMemory
                info.threshold = memInfo.threshold
            }

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertEquals(8192, capabilities.totalRamMb, "Should detect total RAM correctly")
            assertEquals(4096, capabilities.availableRamMb, "Should detect available RAM correctly")
        }

        @Test
        fun `should detect CPU information`() {
            // Given - CPU info is detected from system

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertTrue(capabilities.cpuCores > 0, "Should detect CPU cores")
            assertTrue(capabilities.cpuCores <= 16, "CPU cores should be reasonable")
            assertTrue(capabilities.maxCpuFrequencyMhz >= 0, "CPU frequency should be non-negative")
        }

        @Test
        fun `should detect battery information`() {
            // Given
            val batteryIntent = mockk<Intent> {
                every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 75
                every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
                every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_DISCHARGING
            }
            every { mockContext.registerReceiver(null, any<IntentFilter>()) } returns batteryIntent

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertEquals(75, capabilities.batteryLevel, "Should detect battery level")
            assertFalse(capabilities.isCharging, "Should detect charging state")
        }

        @Test
        fun `should provide fallback capabilities on error`() {
            // Given - context that throws exceptions
            every { mockContext.getSystemService(any()) } throws RuntimeException("System service error")

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertNotNull(capabilities, "Should provide fallback capabilities")
            assertEquals("Unknown Device", capabilities.deviceName)
            assertEquals(HardwareCapabilityDetector.HardwareCapabilities.PerformanceClass.MID_RANGE,
                capabilities.performanceClass)
            assertTrue(capabilities.recommendedSettings.targetFps > 0,
                "Should have reasonable fallback settings")
        }
    }

    @Nested
    @DisplayName("Performance Classification Tests")
    inner class PerformanceClassificationTests {

        @Test
        fun `should classify flagship device correctly`() {
            // Given - flagship specs
            setupFlagshipDevice()

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertEquals(HardwareCapabilityDetector.HardwareCapabilities.PerformanceClass.FLAGSHIP,
                capabilities.performanceClass)
            assertTrue(capabilities.recommendedSettings.targetFps >= 30,
                "Flagship should support high FPS")
            assertTrue(capabilities.recommendedSettings.maxPersonCount >= 2,
                "Flagship should support multiple person detection")
        }

        @Test
        fun `should adapt recommendations based on battery level`() {
            // Given - high-end device with low battery
            setupHighEndDevice()
            val lowBatteryIntent = mockk<Intent> {
                every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 15 // Low battery
                every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
                every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_DISCHARGING
            }
            every { mockContext.registerReceiver(null, any<IntentFilter>()) } returns lowBatteryIntent

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            // Should still be high-end but may have conservative recommendations due to low battery
            assertTrue(capabilities.isHighEnd, "Should still be classified as high-end")
            assertTrue(capabilities.batteryLevel < 30, "Should detect low battery")
        }

        @Test
        fun `should boost recommendations when charging`() {
            // Given - device that's charging
            setupMidRangeDevice()
            val chargingIntent = mockk<Intent> {
                every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 80
                every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
                every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_CHARGING
            }
            every { mockContext.registerReceiver(null, any<IntentFilter>()) } returns chargingIntent

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertTrue(capabilities.isCharging, "Should detect charging state")
            // Recommendations might be more aggressive when charging
        }
    }

    @Nested
    @DisplayName("Recommended Settings Tests")
    inner class RecommendedSettingsTests {

        @Test
        fun `should provide conservative settings for low-end devices`() {
            // Given
            setupLowEndDevice()

            // When
            val capabilities = detector.detectCapabilities(mockContext)
            val settings = capabilities.recommendedSettings

            // Then
            assertTrue(settings.targetFps <= 20, "Should recommend low FPS")
            assertEquals(1, settings.maxPersonCount, "Should recommend single person detection")
            assertFalse(settings.useGpuAcceleration, "Should not recommend GPU acceleration")
            assertTrue(settings.maxLatencyMs >= 50, "Should allow higher latency")
            assertTrue(settings.adaptiveQuality, "Should enable adaptive quality")
        }

        @Test
        fun `should provide aggressive settings for high-end devices`() {
            // Given
            setupHighEndDevice()

            // When
            val capabilities = detector.detectCapabilities(mockContext)
            val settings = capabilities.recommendedSettings

            // Then
            assertTrue(settings.targetFps >= 24, "Should recommend high FPS")
            assertTrue(settings.maxPersonCount >= 2, "Should support multiple persons")
            assertTrue(settings.useGpuAcceleration, "Should recommend GPU acceleration")
            assertTrue(settings.maxLatencyMs <= 40, "Should target low latency")
            assertTrue(settings.enableObjectPooling, "Should enable object pooling")
        }

        @Test
        fun `should balance settings for mid-range devices`() {
            // Given
            setupMidRangeDevice()

            // When
            val capabilities = detector.detectCapabilities(mockContext)
            val settings = capabilities.recommendedSettings

            // Then
            assertTrue(settings.targetFps in 15..25, "Should recommend moderate FPS")
            assertEquals(1, settings.maxPersonCount, "Should recommend single person")
            assertTrue(settings.maxLatencyMs in 30..50, "Should target moderate latency")
            assertTrue(settings.enableObjectPooling, "Should enable object pooling")
        }

        @Test
        fun `should consider available memory for GPU acceleration`() {
            // Given - mid-range device with low available memory
            val memInfo = ActivityManager.MemoryInfo().apply {
                totalMem = 4L * 1024 * 1024 * 1024 // 4GB total
                availMem = 512L * 1024 * 1024 // Only 512MB available
                lowMemory = true
            }
            every { mockActivityManager.getMemoryInfo(any()) } answers {
                val info = firstArg<ActivityManager.MemoryInfo>()
                info.totalMem = memInfo.totalMem
                info.availMem = memInfo.availMem
                info.lowMemory = memInfo.lowMemory
            }

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertFalse(capabilities.recommendedSettings.useGpuAcceleration,
                "Should not recommend GPU acceleration with low memory")
        }
    }

    @Nested
    @DisplayName("State Management Tests")
    inner class StateManagementTests {

        @Test
        fun `should cache last detected capabilities`() {
            // Given
            setupHighEndDevice()

            // When
            val capabilities1 = detector.detectCapabilities(mockContext)
            val cached = detector.getLastDetectedCapabilities()

            // Then
            assertNotNull(cached, "Should cache capabilities")
            assertEquals(capabilities1.deviceName, cached!!.deviceName)
            assertEquals(capabilities1.performanceClass, cached.performanceClass)
        }

        @Test
        fun `should cache last context`() {
            // Given
            detector.detectCapabilities(mockContext)

            // When
            val cachedContext = detector.getLastContext()

            // Then
            assertEquals(mockContext, cachedContext, "Should cache context")
        }

        @Test
        fun `should return null for cached data before detection`() {
            // When
            val capabilities = detector.getLastDetectedCapabilities()
            val context = detector.getLastContext()

            // Then
            assertNull(capabilities, "Should return null before detection")
            assertNull(context, "Should return null before detection")
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {

        @Test
        fun `should handle activity manager errors gracefully`() {
            // Given
            every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns null

            // When/Then - should not throw exception
            assertDoesNotThrow {
                detector.detectCapabilities(mockContext)
            }
        }

        @Test
        fun `should handle memory info errors gracefully`() {
            // Given
            every { mockActivityManager.getMemoryInfo(any()) } throws RuntimeException("Memory info error")

            // When/Then - should not throw exception
            assertDoesNotThrow {
                detector.detectCapabilities(mockContext)
            }
        }

        @Test
        fun `should handle battery info errors gracefully`() {
            // Given
            every { mockContext.registerReceiver(null, any<IntentFilter>()) } throws SecurityException("Permission denied")

            // When/Then - should not throw exception
            assertDoesNotThrow {
                detector.detectCapabilities(mockContext)
            }
        }

        @Test
        fun `should provide reasonable fallback values`() {
            // Given - all system services fail
            every { mockContext.getSystemService(any()) } throws RuntimeException("All services failed")
            every { mockContext.registerReceiver(null, any<IntentFilter>()) } throws RuntimeException("Battery failed")

            // When
            val capabilities = detector.detectCapabilities(mockContext)

            // Then
            assertTrue(capabilities.totalRamMb > 0, "Should have reasonable RAM value")
            assertTrue(capabilities.cpuCores > 0, "Should have reasonable CPU core count")
            assertTrue(capabilities.batteryLevel in 0..100, "Should have reasonable battery level")
            assertNotNull(capabilities.recommendedSettings, "Should have valid recommended settings")
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    inner class PerformanceTests {

        @Test
        fun `should complete detection quickly`() {
            // Given
            setupMidRangeDevice()

            // When
            val startTime = System.currentTimeMillis()
            detector.detectCapabilities(mockContext)
            val endTime = System.currentTimeMillis()

            // Then
            val detectionTime = endTime - startTime
            assertTrue(detectionTime < 500, // Should complete in less than 500ms
                "Detection should be fast: ${detectionTime}ms")
        }

        @Test
        fun `should handle concurrent detection calls`() {
            // Given
            setupHighEndDevice()
            val results = mutableListOf<HardwareCapabilityDetector.HardwareCapabilities>()
            val exceptions = mutableListOf<Exception>()

            // When - concurrent detection calls
            val threads = (1..5).map {
                Thread {
                    try {
                        val capabilities = detector.detectCapabilities(mockContext)
                        synchronized(results) {
                            results.add(capabilities)
                        }
                    } catch (e: Exception) {
                        synchronized(exceptions) {
                            exceptions.add(e)
                        }
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then
            assertTrue(exceptions.isEmpty(), "Should handle concurrent calls without exceptions")
            assertEquals(5, results.size, "Should complete all detection calls")
            assertTrue(results.all { it.performanceClass != null }, "All results should be valid")
        }
    }

    // Helper methods for setting up mock devices

    private fun setupHighEndDevice() {
        val memInfo = ActivityManager.MemoryInfo().apply {
            totalMem = 8L * 1024 * 1024 * 1024 // 8GB
            availMem = 4L * 1024 * 1024 * 1024 // 4GB available
            lowMemory = false
        }
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memInfo.totalMem
            info.availMem = memInfo.availMem
            info.lowMemory = memInfo.lowMemory
        }

        val batteryIntent = mockk<Intent> {
            every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 80
            every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
            every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_DISCHARGING
        }
        every { mockContext.registerReceiver(null, any<IntentFilter>()) } returns batteryIntent
    }

    private fun setupMidRangeDevice() {
        val memInfo = ActivityManager.MemoryInfo().apply {
            totalMem = 4L * 1024 * 1024 * 1024 // 4GB
            availMem = 2L * 1024 * 1024 * 1024 // 2GB available
            lowMemory = false
        }
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memInfo.totalMem
            info.availMem = memInfo.availMem
            info.lowMemory = memInfo.lowMemory
        }

        val batteryIntent = mockk<Intent> {
            every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 60
            every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
            every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_DISCHARGING
        }
        every { mockContext.registerReceiver(null, any<IntentFilter>()) } returns batteryIntent
    }

    private fun setupLowEndDevice() {
        val memInfo = ActivityManager.MemoryInfo().apply {
            totalMem = 2L * 1024 * 1024 * 1024 // 2GB
            availMem = 512L * 1024 * 1024 // 512MB available
            lowMemory = true
        }
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memInfo.totalMem
            info.availMem = memInfo.availMem
            info.lowMemory = memInfo.lowMemory
        }

        val batteryIntent = mockk<Intent> {
            every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 40
            every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
            every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_DISCHARGING
        }
        every { mockContext.registerReceiver(null, any<IntentFilter>()) } returns batteryIntent
    }

    private fun setupFlagshipDevice() {
        val memInfo = ActivityManager.MemoryInfo().apply {
            totalMem = 12L * 1024 * 1024 * 1024 // 12GB
            availMem = 8L * 1024 * 1024 * 1024 // 8GB available
            lowMemory = false
        }
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memInfo.totalMem
            info.availMem = memInfo.availMem
            info.lowMemory = memInfo.lowMemory
        }

        val batteryIntent = mockk<Intent> {
            every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 90
            every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
            every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns BatteryManager.BATTERY_STATUS_CHARGING
        }
        every { mockContext.registerReceiver(null, any<IntentFilter>()) } returns batteryIntent
    }
}