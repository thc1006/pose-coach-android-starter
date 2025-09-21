package com.posecoach.corepose.utils

import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generic object pool for memory optimization and reduced GC pressure.
 * Thread-safe implementation suitable for high-performance scenarios.
 *
 * @param T The type of objects to pool
 * @param capacity Maximum number of objects to keep in the pool
 * @param factory Function to create new objects when pool is empty
 */
class ObjectPool<T>(
    private val capacity: Int,
    private val factory: () -> T?
) {
    private val pool = ConcurrentLinkedQueue<T>()
    private val currentSize = AtomicInteger(0)
    private val totalCreated = AtomicInteger(0)
    private val totalReused = AtomicInteger(0)

    /**
     * Acquire an object from the pool or create a new one.
     * @return Object from pool or newly created object, null if factory returns null
     */
    fun acquire(): T? {
        val pooledObject = pool.poll()
        return if (pooledObject != null) {
            currentSize.decrementAndGet()
            totalReused.incrementAndGet()
            Timber.v("Reused object from pool, size: ${currentSize.get()}")
            pooledObject
        } else {
            val newObject = factory()
            if (newObject != null) {
                totalCreated.incrementAndGet()
                Timber.v("Created new object, total created: ${totalCreated.get()}")
            }
            newObject
        }
    }

    /**
     * Release an object back to the pool.
     * @param obj Object to return to the pool
     * @return true if object was added to pool, false if pool is at capacity
     */
    fun release(obj: T): Boolean {
        return if (currentSize.get() < capacity) {
            pool.offer(obj)
            currentSize.incrementAndGet()
            Timber.v("Released object to pool, size: ${currentSize.get()}")
            true
        } else {
            Timber.v("Pool at capacity, discarding object")
            false
        }
    }

    /**
     * Clear all objects from the pool.
     */
    fun clear() {
        pool.clear()
        currentSize.set(0)
        Timber.d("Pool cleared")
    }

    /**
     * Get current pool statistics.
     */
    fun getStats(): PoolStats {
        return PoolStats(
            currentSize = currentSize.get(),
            capacity = capacity,
            totalCreated = totalCreated.get(),
            totalReused = totalReused.get()
        )
    }

    /**
     * Pre-warm the pool by creating objects up to the specified count.
     * @param count Number of objects to pre-create (capped at capacity)
     */
    fun preWarm(count: Int) {
        val actualCount = minOf(count, capacity)
        repeat(actualCount) {
            val obj = factory()
            if (obj != null) {
                release(obj)
            }
        }
        Timber.i("Pre-warmed pool with ${currentSize.get()} objects")
    }

    data class PoolStats(
        val currentSize: Int,
        val capacity: Int,
        val totalCreated: Int,
        val totalReused: Int
    ) {
        val utilizationPercent: Double = if (capacity > 0) (currentSize.toDouble() / capacity) * 100 else 0.0
        val reuseRate: Double = if (totalCreated > 0) (totalReused.toDouble() / totalCreated) * 100 else 0.0

        override fun toString(): String {
            return "PoolStats(size=$currentSize/$capacity [${String.format("%.1f", utilizationPercent)}%], " +
                   "created=$totalCreated, reused=$totalReused [${String.format("%.1f", reuseRate)}%])"
        }
    }
}

/**
 * Specialized object pool for bitmaps with automatic recycling.
 */
class BitmapPool(private val capacity: Int) {
    private val pool = ObjectPool<android.graphics.Bitmap>(capacity) { null }

    fun acquireBitmap(width: Int, height: Int, config: android.graphics.Bitmap.Config): android.graphics.Bitmap? {
        val pooled = pool.acquire()
        return if (pooled != null &&
                   pooled.width == width &&
                   pooled.height == height &&
                   pooled.config == config &&
                   !pooled.isRecycled) {
            pooled
        } else {
            // Return unsuitable bitmap to pool or recycle
            pooled?.let {
                if (!it.isRecycled) pool.release(it)
            }
            // Create new bitmap
            try {
                android.graphics.Bitmap.createBitmap(width, height, config)
            } catch (e: OutOfMemoryError) {
                Timber.e(e, "Out of memory creating bitmap ${width}x${height}")
                null
            }
        }
    }

    fun acquire(): android.graphics.Bitmap? = pool.acquire()

    fun release(obj: android.graphics.Bitmap): Boolean {
        return if (!obj.isRecycled) {
            pool.release(obj)
        } else {
            Timber.v("Attempted to release recycled bitmap")
            false
        }
    }

    fun clear() = pool.clear()

    fun getStats() = pool.getStats()

    fun recycleAll() {
        val stats = pool.getStats()
        repeat(stats.currentSize) {
            pool.acquire()?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
        pool.clear()
        Timber.i("Recycled all bitmaps in pool")
    }
}

/**
 * Pool manager for coordinating multiple object pools.
 */
class PoolManager {
    private val pools = mutableMapOf<String, ObjectPool<*>>()

    fun <T> registerPool(name: String, pool: ObjectPool<T>) {
        pools[name] = pool
        Timber.d("Registered pool: $name")
    }

    fun <T> getPool(name: String): ObjectPool<T>? {
        @Suppress("UNCHECKED_CAST")
        return pools[name] as? ObjectPool<T>
    }

    fun clearAll() {
        pools.forEach { (name, pool) ->
            pool.clear()
            Timber.d("Cleared pool: $name")
        }
    }

    fun logAllStats() {
        pools.forEach { (name, pool) ->
            Timber.i("Pool $name: ${pool.getStats()}")
        }
    }

    fun getTotalStats(): TotalStats {
        var totalSize = 0
        var totalCapacity = 0
        var totalCreated = 0
        var totalReused = 0

        pools.values.forEach { pool ->
            val stats = pool.getStats()
            totalSize += stats.currentSize
            totalCapacity += stats.capacity
            totalCreated += stats.totalCreated
            totalReused += stats.totalReused
        }

        return TotalStats(
            poolCount = pools.size,
            totalSize = totalSize,
            totalCapacity = totalCapacity,
            totalCreated = totalCreated,
            totalReused = totalReused
        )
    }

    data class TotalStats(
        val poolCount: Int,
        val totalSize: Int,
        val totalCapacity: Int,
        val totalCreated: Int,
        val totalReused: Int
    ) {
        val overallUtilization: Double = if (totalCapacity > 0) (totalSize.toDouble() / totalCapacity) * 100 else 0.0
        val overallReuseRate: Double = if (totalCreated > 0) (totalReused.toDouble() / totalCreated) * 100 else 0.0
    }
}