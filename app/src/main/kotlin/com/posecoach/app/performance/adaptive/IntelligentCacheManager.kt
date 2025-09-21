package com.posecoach.app.performance.adaptive

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Intelligent Cache Manager - ML-driven caching system that predicts and preloads resources
 */
class IntelligentCacheManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    data class CacheEntry<T>(
        val key: String,
        val data: T,
        val size: Long,
        val creationTime: Long,
        val lastAccessTime: Long,
        val accessCount: Int,
        val priority: CachePriority,
        val expirationTime: Long = Long.MAX_VALUE,
        val metadata: Map<String, Any> = emptyMap()
    )

    enum class CachePriority {
        CRITICAL,    // Never evict unless expired
        HIGH,        // Evict only under memory pressure
        MEDIUM,      // Standard eviction policy
        LOW,         // Evict frequently
        PRELOAD      // Speculative preload, evict first
    }

    data class CacheStatistics(
        val totalEntries: Int,
        val totalSize: Long,
        val hitRate: Float,
        val missRate: Float,
        val evictionCount: Int,
        val preloadHitRate: Float,
        val memoryUsage: Float,
        val predictiveAccuracy: Float
    )

    data class AccessPattern(
        val key: String,
        val accessTime: Long,
        val frequency: Float,
        val context: Map<String, Any> = emptyMap()
    )

    data class PredictionModel(
        val patterns: MutableMap<String, List<AccessPattern>> = mutableMapOf(),
        val transitions: MutableMap<String, MutableMap<String, Float>> = mutableMapOf(),
        val contextWeights: MutableMap<String, Float> = mutableMapOf(),
        val accuracy: Float = 0f
    )

    companion object {
        private const val DEFAULT_MAX_SIZE = 100L * 1024 * 1024 // 100MB
        private const val DEFAULT_MAX_ENTRIES = 1000
        private const val CLEANUP_INTERVAL_MS = 30000L // 30 seconds
        private const val PREDICTION_INTERVAL_MS = 10000L // 10 seconds
        private const val ACCESS_PATTERN_WINDOW = 100
        private const val MIN_FREQUENCY_THRESHOLD = 0.1f
        private const val PRELOAD_CONFIDENCE_THRESHOLD = 0.7f
    }

    // Cache storage
    private val cache = ConcurrentHashMap<String, CacheEntry<Any>>()
    private val accessPatterns = mutableListOf<AccessPattern>()
    private val predictionModel = PredictionModel()

    // Configuration
    private var maxCacheSize = DEFAULT_MAX_SIZE
    private var maxEntries = DEFAULT_MAX_ENTRIES
    private var enablePredictivePreloading = true
    private var enableAdaptiveEviction = true

    // Statistics
    private var hitCount = 0L
    private var missCount = 0L
    private var evictionCount = 0L
    private var preloadHitCount = 0L
    private var preloadMissCount = 0L

    // State flows
    private val _cacheStatistics = MutableStateFlow(calculateStatistics())
    val cacheStatistics: StateFlow<CacheStatistics> = _cacheStatistics.asStateFlow()

    private val _memoryPressure = MutableStateFlow(0f)
    val memoryPressure: StateFlow<Float> = _memoryPressure.asStateFlow()

    init {
        startCacheManagement()
        startPredictiveEngine()
    }

    private fun startCacheManagement() {
        scope.launch {
            while (isActive) {
                try {
                    performCacheCleanup()
                    updateMemoryPressure()
                    _cacheStatistics.value = calculateStatistics()
                    delay(CLEANUP_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in cache management")
                    delay(CLEANUP_INTERVAL_MS * 2)
                }
            }
        }
    }

    private fun startPredictiveEngine() {
        scope.launch {
            while (isActive) {
                try {
                    if (enablePredictivePreloading) {
                        updatePredictionModel()
                        performPredictivePreloading()
                    }
                    delay(PREDICTION_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in predictive engine")
                    delay(PREDICTION_INTERVAL_MS * 2)
                }
            }
        }
    }

    /**
     * Store data in cache with intelligent prioritization
     */
    fun <T : Any> put(
        key: String,
        data: T,
        priority: CachePriority = CachePriority.MEDIUM,
        expirationTimeMs: Long = Long.MAX_VALUE,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val size = estimateSize(data)
        val currentTime = SystemClock.elapsedRealtime()

        // Check if we need to make space
        if (shouldEvictForNewEntry(size)) {
            evictLeastValuable(size)
        }

        val entry = CacheEntry(
            key = key,
            data = data,
            size = size,
            creationTime = currentTime,
            lastAccessTime = currentTime,
            accessCount = 1,
            priority = priority,
            expirationTime = if (expirationTimeMs == Long.MAX_VALUE) Long.MAX_VALUE
                           else currentTime + expirationTimeMs,
            metadata = metadata
        )

        cache[key] = entry
        recordAccess(key, currentTime)

        Timber.v("Cached: $key (size: $size bytes, priority: $priority)")
    }

    /**
     * Retrieve data from cache with access pattern learning
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String, expectedType: Class<T>): T? {
        val entry = cache[key]
        val currentTime = SystemClock.elapsedRealtime()

        return if (entry != null && !isExpired(entry)) {
            // Update access statistics
            val updatedEntry = entry.copy(
                lastAccessTime = currentTime,
                accessCount = entry.accessCount + 1
            )
            cache[key] = updatedEntry

            hitCount++
            recordAccess(key, currentTime)

            // Check if the priority should be upgraded based on access pattern
            if (shouldUpgradePriority(updatedEntry)) {
                upgradePriority(key, updatedEntry)
            }

            try {
                entry.data as T
            } catch (e: ClassCastException) {
                Timber.e(e, "Cache type mismatch for key: $key")
                cache.remove(key)
                missCount++
                null
            }
        } else {
            if (entry != null && isExpired(entry)) {
                cache.remove(key)
                Timber.v("Removed expired cache entry: $key")
            }
            missCount++
            recordMiss(key, currentTime)
            null
        }
    }

    /**
     * Predictive preloading based on access patterns
     */
    suspend fun preloadPredictedResources(prediction: PredictiveResourceManager.ResourcePrediction) {
        if (!enablePredictivePreloading) return

        val predictedKeys = predictNextAccess(getCurrentContext(prediction))

        predictedKeys.forEach { (key, confidence) ->
            if (confidence > PRELOAD_CONFIDENCE_THRESHOLD && !cache.containsKey(key)) {
                preloadResource(key, confidence)
            }
        }
    }

    private fun getCurrentContext(prediction: PredictiveResourceManager.ResourcePrediction): Map<String, Any> {
        return mapOf(
            "cpu_usage" to prediction.predictedCpuUsage,
            "memory_usage" to prediction.predictedMemoryUsage,
            "quality_level" to prediction.recommendedQualityLevel.ordinal,
            "time_of_day" to (System.currentTimeMillis() % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000),
            "battery_level" to prediction.predictedBatteryDrainRate
        )
    }

    private suspend fun preloadResource(key: String, confidence: Float) {
        try {
            // This would be implemented to load the actual resource
            val mockData = "preloaded_$key"
            put(
                key = key,
                data = mockData,
                priority = CachePriority.PRELOAD,
                expirationTimeMs = 300000L, // 5 minutes
                metadata = mapOf(
                    "preloaded" to true,
                    "confidence" to confidence,
                    "preload_time" to SystemClock.elapsedRealtime()
                )
            )
            Timber.d("Preloaded resource: $key (confidence: $confidence)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to preload resource: $key")
        }
    }

    private fun predictNextAccess(context: Map<String, Any>): List<Pair<String, Float>> {
        val predictions = mutableListOf<Pair<String, Float>>()

        // Analyze recent access patterns
        val recentPatterns = accessPatterns.takeLast(50)
        val patternFrequency = recentPatterns.groupBy { it.key }
            .mapValues { (_, patterns) ->
                patterns.size.toFloat() / recentPatterns.size
            }

        // Calculate context similarity weights
        patternFrequency.forEach { (key, frequency) ->
            if (frequency > MIN_FREQUENCY_THRESHOLD) {
                val contextSimilarity = calculateContextSimilarity(key, context)
                val temporalScore = calculateTemporalScore(key)
                val transitionScore = calculateTransitionScore(key)

                val confidence = (frequency * 0.4f +
                                contextSimilarity * 0.3f +
                                temporalScore * 0.2f +
                                transitionScore * 0.1f).coerceIn(0f, 1f)

                if (confidence > MIN_FREQUENCY_THRESHOLD) {
                    predictions.add(key to confidence)
                }
            }
        }

        return predictions.sortedByDescending { it.second }.take(10)
    }

    private fun calculateContextSimilarity(key: String, currentContext: Map<String, Any>): Float {
        val keyPatterns = predictionModel.patterns[key] ?: return 0f
        if (keyPatterns.isEmpty()) return 0f

        val similarities = keyPatterns.map { pattern ->
            val contextSimilarity = pattern.context.entries.map { (contextKey, value) ->
                val currentValue = currentContext[contextKey]
                if (currentValue != null && value is Number && currentValue is Number) {
                    1f - abs(value.toFloat() - currentValue.toFloat()).coerceAtMost(1f)
                } else if (currentValue == value) {
                    1f
                } else {
                    0f
                }
            }.average().toFloat()

            contextSimilarity
        }

        return similarities.average().toFloat()
    }

    private fun calculateTemporalScore(key: String): Float {
        val keyPatterns = predictionModel.patterns[key] ?: return 0f
        if (keyPatterns.isEmpty()) return 0f

        val currentTime = SystemClock.elapsedRealtime()
        val recentAccesses = keyPatterns.filter {
            currentTime - it.accessTime < 300000L // Last 5 minutes
        }

        return (recentAccesses.size.toFloat() / keyPatterns.size).coerceIn(0f, 1f)
    }

    private fun calculateTransitionScore(key: String): Float {
        val recentAccess = accessPatterns.lastOrNull()?.key ?: return 0f
        val transitions = predictionModel.transitions[recentAccess] ?: return 0f
        return transitions[key] ?: 0f
    }

    private fun updatePredictionModel() {
        // Update access patterns
        val recentPatterns = accessPatterns.takeLast(ACCESS_PATTERN_WINDOW)

        recentPatterns.groupBy { it.key }.forEach { (key, patterns) ->
            predictionModel.patterns[key] = patterns
        }

        // Update transition probabilities
        for (i in 0 until recentPatterns.size - 1) {
            val current = recentPatterns[i].key
            val next = recentPatterns[i + 1].key

            val currentTransitions = predictionModel.transitions.getOrPut(current) { mutableMapOf() }
            currentTransitions[next] = currentTransitions.getOrDefault(next, 0f) + 1f
        }

        // Normalize transition probabilities
        predictionModel.transitions.values.forEach { transitions ->
            val total = transitions.values.sum()
            if (total > 0) {
                transitions.replaceAll { _, count -> count / total }
            }
        }

        // Update model accuracy based on recent predictions
        updateModelAccuracy()
    }

    private fun updateModelAccuracy() {
        val totalPreloads = preloadHitCount + preloadMissCount
        predictionModel.accuracy = if (totalPreloads > 0) {
            preloadHitCount.toFloat() / totalPreloads
        } else 0f
    }

    private fun recordAccess(key: String, accessTime: Long) {
        val pattern = AccessPattern(
            key = key,
            accessTime = accessTime,
            frequency = calculateAccessFrequency(key)
        )

        accessPatterns.add(pattern)

        // Keep only recent patterns
        if (accessPatterns.size > ACCESS_PATTERN_WINDOW * 2) {
            accessPatterns.subList(0, accessPatterns.size - ACCESS_PATTERN_WINDOW).clear()
        }

        // Check if this was a successful preload prediction
        val entry = cache[key]
        if (entry?.metadata?.get("preloaded") == true) {
            preloadHitCount++
        }
    }

    private fun recordMiss(key: String, accessTime: Long) {
        // Record missed prediction if this was expected to be preloaded
        val wasPreloadPredicted = predictionModel.patterns[key]?.any {
            accessTime - it.accessTime < PREDICTION_INTERVAL_MS
        } ?: false

        if (wasPreloadPredicted) {
            preloadMissCount++
        }
    }

    private fun calculateAccessFrequency(key: String): Float {
        val recentAccesses = accessPatterns.filter { it.key == key }
        return recentAccesses.size.toFloat() / max(accessPatterns.size, 1)
    }

    private fun performCacheCleanup() {
        removeExpiredEntries()

        if (enableAdaptiveEviction) {
            adaptiveEviction()
        }
    }

    private fun removeExpiredEntries() {
        val currentTime = SystemClock.elapsedRealtime()
        val expiredKeys = cache.filter { (_, entry) ->
            isExpired(entry)
        }.keys

        expiredKeys.forEach { key ->
            cache.remove(key)
            evictionCount++
        }

        if (expiredKeys.isNotEmpty()) {
            Timber.d("Removed ${expiredKeys.size} expired cache entries")
        }
    }

    private fun adaptiveEviction() {
        val memoryPressure = calculateMemoryPressure()
        _memoryPressure.value = memoryPressure

        if (memoryPressure > 0.8f) {
            // High memory pressure - aggressive eviction
            evictByScore(0.3f)
        } else if (memoryPressure > 0.6f) {
            // Medium memory pressure - moderate eviction
            evictByScore(0.1f)
        }
    }

    private fun evictByScore(targetReduction: Float) {
        val totalSize = cache.values.sumOf { it.size }
        val targetEvictionSize = (totalSize * targetReduction).toLong()
        var evictedSize = 0L

        // Calculate eviction scores for all entries
        val scoredEntries = cache.values.map { entry ->
            val score = calculateEvictionScore(entry)
            entry to score
        }.sortedBy { it.second } // Lower scores get evicted first

        for ((entry, _) in scoredEntries) {
            if (evictedSize >= targetEvictionSize) break
            if (entry.priority == CachePriority.CRITICAL) continue

            cache.remove(entry.key)
            evictedSize += entry.size
            evictionCount++
        }

        if (evictedSize > 0) {
            Timber.d("Adaptive eviction: removed ${evictedSize / 1024}KB")
        }
    }

    private fun calculateEvictionScore(entry: CacheEntry<Any>): Float {
        val currentTime = SystemClock.elapsedRealtime()
        val age = (currentTime - entry.creationTime) / 1000f // seconds
        val timeSinceAccess = (currentTime - entry.lastAccessTime) / 1000f // seconds
        val accessFrequency = entry.accessCount.toFloat() / max(age, 1f)

        val priorityWeight = when (entry.priority) {
            CachePriority.CRITICAL -> 100f
            CachePriority.HIGH -> 10f
            CachePriority.MEDIUM -> 1f
            CachePriority.LOW -> 0.5f
            CachePriority.PRELOAD -> 0.1f
        }

        val sizeWeight = 1f + (entry.size / 1024f) // Larger entries have slightly higher eviction score

        // Lower score = more likely to be evicted
        return (accessFrequency * priorityWeight) / (sizeWeight * (1f + timeSinceAccess))
    }

    private fun shouldEvictForNewEntry(newEntrySize: Long): Boolean {
        val currentSize = cache.values.sumOf { it.size }
        val currentEntries = cache.size

        return (currentSize + newEntrySize > maxCacheSize) ||
               (currentEntries >= maxEntries)
    }

    private fun evictLeastValuable(spaceNeeded: Long) {
        var freedSpace = 0L
        val entriesToEvict = cache.values
            .filter { it.priority != CachePriority.CRITICAL }
            .sortedBy { calculateEvictionScore(it) }

        for (entry in entriesToEvict) {
            cache.remove(entry.key)
            freedSpace += entry.size
            evictionCount++

            if (freedSpace >= spaceNeeded) break
        }
    }

    private fun shouldUpgradePriority(entry: CacheEntry<Any>): Boolean {
        if (entry.priority == CachePriority.CRITICAL) return false

        val currentTime = SystemClock.elapsedRealtime()
        val age = (currentTime - entry.creationTime) / 1000f
        val accessRate = entry.accessCount.toFloat() / max(age, 1f)

        return accessRate > 0.1f && entry.accessCount > 5
    }

    private fun upgradePriority(key: String, entry: CacheEntry<Any>) {
        val newPriority = when (entry.priority) {
            CachePriority.PRELOAD -> CachePriority.LOW
            CachePriority.LOW -> CachePriority.MEDIUM
            CachePriority.MEDIUM -> CachePriority.HIGH
            CachePriority.HIGH -> CachePriority.HIGH
            CachePriority.CRITICAL -> CachePriority.CRITICAL
        }

        if (newPriority != entry.priority) {
            cache[key] = entry.copy(priority = newPriority)
            Timber.v("Upgraded cache priority for $key: ${entry.priority} -> $newPriority")
        }
    }

    private fun isExpired(entry: CacheEntry<Any>): Boolean {
        return entry.expirationTime != Long.MAX_VALUE &&
               SystemClock.elapsedRealtime() > entry.expirationTime
    }

    private fun estimateSize(data: Any): Long {
        // Simplified size estimation
        return when (data) {
            is String -> data.length * 2L // Approximate UTF-16 encoding
            is ByteArray -> data.size.toLong()
            is IntArray -> data.size * 4L
            is FloatArray -> data.size * 4L
            is LongArray -> data.size * 8L
            is List<*> -> data.size * 8L // Approximate
            is Map<*, *> -> data.size * 16L // Approximate
            else -> 64L // Default estimate
        }
    }

    private fun calculateMemoryPressure(): Float {
        val currentSize = cache.values.sumOf { it.size }
        return (currentSize.toFloat() / maxCacheSize).coerceIn(0f, 1f)
    }

    private fun updateMemoryPressure() {
        _memoryPressure.value = calculateMemoryPressure()
    }

    private fun calculateStatistics(): CacheStatistics {
        val totalAccess = hitCount + missCount
        val totalPreloads = preloadHitCount + preloadMissCount

        return CacheStatistics(
            totalEntries = cache.size,
            totalSize = cache.values.sumOf { it.size },
            hitRate = if (totalAccess > 0) hitCount.toFloat() / totalAccess else 0f,
            missRate = if (totalAccess > 0) missCount.toFloat() / totalAccess else 0f,
            evictionCount = evictionCount.toInt(),
            preloadHitRate = if (totalPreloads > 0) preloadHitCount.toFloat() / totalPreloads else 0f,
            memoryUsage = calculateMemoryPressure(),
            predictiveAccuracy = predictionModel.accuracy
        )
    }

    // Public API methods
    fun clearCache() {
        val size = cache.size
        cache.clear()
        Timber.i("Cleared cache: $size entries removed")
    }

    fun clearLowPriorityCache() {
        val lowPriorityKeys = cache.filter { (_, entry) ->
            entry.priority == CachePriority.LOW || entry.priority == CachePriority.PRELOAD
        }.keys

        lowPriorityKeys.forEach { key ->
            cache.remove(key)
            evictionCount++
        }

        Timber.i("Cleared low priority cache: ${lowPriorityKeys.size} entries removed")
    }

    fun invalidate(key: String) {
        val removed = cache.remove(key)
        if (removed != null) {
            Timber.d("Invalidated cache entry: $key")
        }
    }

    fun invalidatePattern(pattern: String) {
        val regex = pattern.toRegex()
        val keysToRemove = cache.keys.filter { key -> regex.matches(key) }

        keysToRemove.forEach { key ->
            cache.remove(key)
        }

        Timber.i("Invalidated ${keysToRemove.size} cache entries matching pattern: $pattern")
    }

    fun contains(key: String): Boolean {
        val entry = cache[key]
        return entry != null && !isExpired(entry)
    }

    fun size(): Int = cache.size

    fun sizeInBytes(): Long = cache.values.sumOf { it.size }

    fun setMaxCacheSize(sizeBytes: Long) {
        maxCacheSize = sizeBytes
        Timber.i("Max cache size set to: ${sizeBytes / 1024}KB")
    }

    fun setMaxEntries(count: Int) {
        maxEntries = count
        Timber.i("Max cache entries set to: $count")
    }

    fun enablePredictivePreloading(enable: Boolean) {
        enablePredictivePreloading = enable
        Timber.i("Predictive preloading: ${if (enable) "enabled" else "disabled"}")
    }

    fun enableAdaptiveEviction(enable: Boolean) {
        enableAdaptiveEviction = enable
        Timber.i("Adaptive eviction: ${if (enable) "enabled" else "disabled"}")
    }

    fun getCacheInfo(): Map<String, Any> {
        val stats = calculateStatistics()
        return mapOf(
            "statistics" to mapOf(
                "total_entries" to stats.totalEntries,
                "total_size_kb" to stats.totalSize / 1024,
                "hit_rate" to stats.hitRate,
                "miss_rate" to stats.missRate,
                "eviction_count" to stats.evictionCount,
                "preload_hit_rate" to stats.preloadHitRate,
                "memory_usage" to stats.memoryUsage,
                "predictive_accuracy" to stats.predictiveAccuracy
            ),
            "configuration" to mapOf(
                "max_size_kb" to maxCacheSize / 1024,
                "max_entries" to maxEntries,
                "predictive_preloading" to enablePredictivePreloading,
                "adaptive_eviction" to enableAdaptiveEviction
            ),
            "model" to mapOf(
                "pattern_count" to predictionModel.patterns.size,
                "transition_count" to predictionModel.transitions.size,
                "model_accuracy" to predictionModel.accuracy
            )
        )
    }

    fun exportCacheStatistics(): String {
        val stats = calculateStatistics()
        return """
            === Intelligent Cache Statistics ===
            Total Entries: ${stats.totalEntries}
            Total Size: ${stats.totalSize / 1024}KB
            Hit Rate: ${"%.2f".format(stats.hitRate * 100)}%
            Miss Rate: ${"%.2f".format(stats.missRate * 100)}%
            Eviction Count: ${stats.evictionCount}
            Preload Hit Rate: ${"%.2f".format(stats.preloadHitRate * 100)}%
            Memory Usage: ${"%.2f".format(stats.memoryUsage * 100)}%
            Predictive Accuracy: ${"%.2f".format(stats.predictiveAccuracy * 100)}%

            === Configuration ===
            Max Size: ${maxCacheSize / 1024}KB
            Max Entries: $maxEntries
            Predictive Preloading: $enablePredictivePreloading
            Adaptive Eviction: $enableAdaptiveEviction

            === ML Model ===
            Access Patterns: ${predictionModel.patterns.size}
            Transitions: ${predictionModel.transitions.size}
            Model Accuracy: ${"%.2f".format(predictionModel.accuracy * 100)}%
        """.trimIndent()
    }

    fun resetStatistics() {
        hitCount = 0L
        missCount = 0L
        evictionCount = 0L
        preloadHitCount = 0L
        preloadMissCount = 0L
        accessPatterns.clear()
        predictionModel.patterns.clear()
        predictionModel.transitions.clear()
        Timber.i("Cache statistics reset")
    }

    fun shutdown() {
        scope.cancel()
        Timber.i("Intelligent cache manager shutdown")
    }
}