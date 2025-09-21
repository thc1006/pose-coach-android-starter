package com.posecoach.corepose.utils

import android.graphics.Bitmap
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Test suite for ObjectPool utility classes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ObjectPoolTest {

    @Nested
    @DisplayName("ObjectPool Basic Functionality Tests")
    inner class ObjectPoolBasicTests {

        @Test
        fun `should create objects when pool is empty`() {
            // Given
            var createdCount = 0
            val pool = ObjectPool<String>(capacity = 5) {
                createdCount++
                "Object $createdCount"
            }

            // When
            val obj1 = pool.acquire()
            val obj2 = pool.acquire()

            // Then
            assertEquals("Object 1", obj1)
            assertEquals("Object 2", obj2)
            assertEquals(2, createdCount)
        }

        @Test
        fun `should reuse released objects`() {
            // Given
            var createdCount = 0
            val pool = ObjectPool<String>(capacity = 5) {
                createdCount++
                "Object $createdCount"
            }

            // When
            val obj1 = pool.acquire()
            pool.release(obj1!!)
            val obj2 = pool.acquire()

            // Then
            assertEquals(obj1, obj2, "Should reuse the same object")
            assertEquals(1, createdCount, "Should not create new object when reusing")
        }

        @Test
        fun `should respect capacity limits`() {
            // Given
            val pool = ObjectPool<String>(capacity = 2) { "Object" }

            val obj1 = pool.acquire()!!
            val obj2 = pool.acquire()!!
            val obj3 = pool.acquire()!!

            // When
            val released1 = pool.release(obj1)
            val released2 = pool.release(obj2)
            val released3 = pool.release(obj3) // Should exceed capacity

            // Then
            assertTrue(released1, "Should accept first release")
            assertTrue(released2, "Should accept second release")
            assertFalse(released3, "Should reject release when at capacity")
        }

        @Test
        fun `should clear pool correctly`() {
            // Given
            val pool = ObjectPool<String>(capacity = 5) { "Object" }

            repeat(3) {
                val obj = pool.acquire()!!
                pool.release(obj)
            }

            // When
            pool.clear()

            // Then
            val stats = pool.getStats()
            assertEquals(0, stats.currentSize, "Pool should be empty after clear")
        }

        @Test
        fun `should track statistics correctly`() {
            // Given
            val pool = ObjectPool<String>(capacity = 5) { "Object" }

            // When
            val obj1 = pool.acquire()!! // Create 1
            val obj2 = pool.acquire()!! // Create 2
            pool.release(obj1) // Release 1
            val obj3 = pool.acquire()!! // Reuse 1
            pool.release(obj2) // Release 2

            // Then
            val stats = pool.getStats()
            assertEquals(2, stats.totalCreated, "Should track total created")
            assertEquals(1, stats.totalReused, "Should track total reused")
            assertEquals(1, stats.currentSize, "Should track current pool size")
            assertEquals(5, stats.capacity, "Should track capacity")
            assertEquals(20.0, stats.utilizationPercent, 0.1, "Should calculate utilization")
            assertEquals(50.0, stats.reuseRate, 0.1, "Should calculate reuse rate")
        }

        @Test
        fun `should pre-warm pool correctly`() {
            // Given
            var createdCount = 0
            val pool = ObjectPool<String>(capacity = 5) {
                createdCount++
                "Object $createdCount"
            }

            // When
            pool.preWarm(3)

            // Then
            val stats = pool.getStats()
            assertEquals(3, stats.currentSize, "Should pre-warm with 3 objects")
            assertEquals(3, createdCount, "Should create 3 objects during pre-warm")

            // Acquiring should reuse pre-warmed objects
            val obj = pool.acquire()
            assertEquals("Object 1", obj, "Should reuse pre-warmed object")
            assertEquals(3, createdCount, "Should not create new object when reusing pre-warmed")
        }

        @Test
        fun `should limit pre-warm to capacity`() {
            // Given
            val pool = ObjectPool<String>(capacity = 3) { "Object" }

            // When
            pool.preWarm(10) // Request more than capacity

            // Then
            val stats = pool.getStats()
            assertEquals(3, stats.currentSize, "Should limit pre-warm to capacity")
        }

        @Test
        fun `should handle factory returning null`() {
            // Given
            val pool = ObjectPool<String>(capacity = 5) { null }

            // When
            val obj = pool.acquire()

            // Then
            assertNull(obj, "Should return null when factory returns null")
        }
    }

    @Nested
    @DisplayName("BitmapPool Specialized Tests")
    inner class BitmapPoolTests {

        @Test
        fun `should acquire bitmap with specific dimensions`() {
            // Given
            val pool = BitmapPool(capacity = 5)

            // When
            val bitmap = pool.acquireBitmap(640, 480, Bitmap.Config.ARGB_8888)

            // Then
            assertNotNull(bitmap, "Should create bitmap")
            assertEquals(640, bitmap!!.width)
            assertEquals(480, bitmap.height)
            assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
        }

        @Test
        fun `should reuse compatible bitmaps`() {
            // Given
            val pool = BitmapPool(capacity = 5)
            val mockBitmap = mockk<Bitmap> {
                every { width } returns 640
                every { height } returns 480
                every { config } returns Bitmap.Config.ARGB_8888
                every { isRecycled } returns false
            }

            // When
            pool.release(mockBitmap)
            val reusedBitmap = pool.acquireBitmap(640, 480, Bitmap.Config.ARGB_8888)

            // Then
            assertEquals(mockBitmap, reusedBitmap, "Should reuse compatible bitmap")
        }

        @Test
        fun `should not reuse incompatible bitmaps`() {
            // Given
            val pool = BitmapPool(capacity = 5)
            val mockBitmap = mockk<Bitmap> {
                every { width } returns 320 // Different size
                every { height } returns 240
                every { config } returns Bitmap.Config.ARGB_8888
                every { isRecycled } returns false
            }

            // When
            pool.release(mockBitmap)
            val newBitmap = pool.acquireBitmap(640, 480, Bitmap.Config.ARGB_8888)

            // Then
            assertNotEquals(mockBitmap, newBitmap, "Should not reuse incompatible bitmap")
        }

        @Test
        fun `should not release recycled bitmaps`() {
            // Given
            val pool = BitmapPool(capacity = 5)
            val recycledBitmap = mockk<Bitmap> {
                every { isRecycled } returns true
            }

            // When
            val released = pool.release(recycledBitmap)

            // Then
            assertFalse(released, "Should not release recycled bitmap")
        }

        @Test
        fun `should recycle all bitmaps on recycleAll`() {
            // Given
            val pool = BitmapPool(capacity = 3)
            val mockBitmaps = (1..3).map { index ->
                mockk<Bitmap> {
                    every { width } returns 640
                    every { height } returns 480
                    every { config } returns Bitmap.Config.ARGB_8888
                    every { isRecycled } returns false
                    every { recycle() } just Runs
                }
            }

            mockBitmaps.forEach { pool.release(it) }

            // When
            pool.recycleAll()

            // Then
            mockBitmaps.forEach { bitmap ->
                verify { bitmap.recycle() }
            }

            val stats = pool.getStats()
            assertEquals(0, stats.currentSize, "Pool should be empty after recycleAll")
        }
    }

    @Nested
    @DisplayName("PoolManager Tests")
    inner class PoolManagerTests {

        @Test
        fun `should register and retrieve pools`() {
            // Given
            val manager = PoolManager()
            val stringPool = ObjectPool<String>(capacity = 5) { "String" }
            val intPool = ObjectPool<Int>(capacity = 3) { 42 }

            // When
            manager.registerPool("strings", stringPool)
            manager.registerPool("integers", intPool)

            // Then
            val retrievedStringPool: ObjectPool<String>? = manager.getPool("strings")
            val retrievedIntPool: ObjectPool<Int>? = manager.getPool("integers")

            assertEquals(stringPool, retrievedStringPool)
            assertEquals(intPool, retrievedIntPool)
        }

        @Test
        fun `should return null for unregistered pool`() {
            // Given
            val manager = PoolManager()

            // When
            val pool: ObjectPool<String>? = manager.getPool("nonexistent")

            // Then
            assertNull(pool, "Should return null for unregistered pool")
        }

        @Test
        fun `should clear all pools`() {
            // Given
            val manager = PoolManager()
            val pool1 = ObjectPool<String>(capacity = 5) { "String" }
            val pool2 = ObjectPool<Int>(capacity = 3) { 42 }

            manager.registerPool("pool1", pool1)
            manager.registerPool("pool2", pool2)

            // Pre-populate pools
            pool1.acquire()?.let { pool1.release(it) }
            pool2.acquire()?.let { pool2.release(it) }

            // When
            manager.clearAll()

            // Then
            assertEquals(0, pool1.getStats().currentSize)
            assertEquals(0, pool2.getStats().currentSize)
        }

        @Test
        fun `should calculate total statistics`() {
            // Given
            val manager = PoolManager()
            val pool1 = ObjectPool<String>(capacity = 5) { "String" }
            val pool2 = ObjectPool<Int>(capacity = 3) { 42 }

            manager.registerPool("pool1", pool1)
            manager.registerPool("pool2", pool2)

            // Create some activity
            pool1.acquire()?.let { pool1.release(it) } // 1 created, 1 reused
            pool1.acquire() // 1 reused
            pool2.acquire()?.let { pool2.release(it) } // 1 created, 1 reused
            pool2.acquire() // 1 reused

            // When
            val totalStats = manager.getTotalStats()

            // Then
            assertEquals(2, totalStats.poolCount)
            assertEquals(8, totalStats.totalCapacity) // 5 + 3
            assertEquals(1, totalStats.totalSize) // 1 object in pool1
            assertEquals(2, totalStats.totalCreated)
            assertEquals(2, totalStats.totalReused)
            assertEquals(12.5, totalStats.overallUtilization, 0.1) // 1/8 * 100
            assertEquals(100.0, totalStats.overallReuseRate, 0.1) // 2/2 * 100
        }

        @Test
        fun `should log statistics without errors`() {
            // Given
            val manager = PoolManager()
            val pool = ObjectPool<String>(capacity = 5) { "String" }
            manager.registerPool("test", pool)

            // When/Then - should not throw exception
            assertDoesNotThrow {
                manager.logAllStats()
            }
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    inner class ThreadSafetyTests {

        @Test
        fun `should be thread safe for concurrent acquire and release`() {
            // Given
            val pool = ObjectPool<String>(capacity = 10) { "Object" }
            val exceptions = mutableListOf<Exception>()

            // When - concurrent access from multiple threads
            val threads = (1..10).map { threadId ->
                Thread {
                    try {
                        repeat(100) {
                            val obj = pool.acquire()
                            if (obj != null) {
                                // Simulate some work
                                Thread.sleep(1)
                                pool.release(obj)
                            }
                        }
                    } catch (e: Exception) {
                        exceptions.add(e)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then
            assertTrue(exceptions.isEmpty(), "Should not have exceptions from concurrent access")

            val stats = pool.getStats()
            assertTrue(stats.totalCreated >= 0, "Should have valid statistics")
            assertTrue(stats.totalReused >= 0, "Should have valid reuse count")
        }

        @Test
        fun `should handle concurrent pool operations`() {
            // Given
            val pool = ObjectPool<Int>(capacity = 5) { 42 }
            val results = mutableListOf<Int>()
            val exceptions = mutableListOf<Exception>()

            // When - concurrent acquire/release/clear operations
            val threads = listOf(
                Thread { // Acquire thread
                    try {
                        repeat(50) {
                            val obj = pool.acquire()
                            if (obj != null) {
                                results.add(obj)
                                Thread.sleep(1)
                                pool.release(obj)
                            }
                        }
                    } catch (e: Exception) {
                        exceptions.add(e)
                    }
                },
                Thread { // Statistics thread
                    try {
                        repeat(20) {
                            pool.getStats()
                            Thread.sleep(5)
                        }
                    } catch (e: Exception) {
                        exceptions.add(e)
                    }
                },
                Thread { // Clear thread
                    try {
                        Thread.sleep(50)
                        pool.clear()
                    } catch (e: Exception) {
                        exceptions.add(e)
                    }
                }
            )

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then
            assertTrue(exceptions.isEmpty(), "Should handle concurrent operations safely")
            assertTrue(results.isNotEmpty(), "Should successfully acquire objects")
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    inner class PerformanceTests {

        @Test
        fun `should perform efficiently with high activity`() {
            // Given
            val pool = ObjectPool<String>(capacity = 100) { "PerformanceObject" }
            val iterations = 10000

            // When
            val startTime = System.currentTimeMillis()

            repeat(iterations) {
                val obj = pool.acquire()
                if (obj != null) {
                    pool.release(obj)
                }
            }

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // Then
            val avgTimePerOperation = duration.toDouble() / iterations
            assertTrue(avgTimePerOperation < 0.1, // Less than 0.1ms per operation
                "Should perform efficiently: ${avgTimePerOperation}ms per operation")

            val stats = pool.getStats()
            assertTrue(stats.reuseRate > 80.0, "Should achieve high reuse rate")
        }

        @Test
        fun `should maintain performance with varying pool sizes`() {
            // Given
            val poolSizes = listOf(1, 10, 100, 1000)
            val iterations = 1000

            // When/Then
            poolSizes.forEach { poolSize ->
                val pool = ObjectPool<String>(capacity = poolSize) { "Object" }

                val startTime = System.nanoTime()
                repeat(iterations) {
                    val obj = pool.acquire()
                    if (obj != null) pool.release(obj)
                }
                val endTime = System.nanoTime()

                val avgTimeNs = (endTime - startTime).toDouble() / iterations
                assertTrue(avgTimeNs < 100_000, // Less than 100 microseconds
                    "Pool size $poolSize should maintain performance: ${avgTimeNs / 1000}μs per operation")
            }
        }
    }
}