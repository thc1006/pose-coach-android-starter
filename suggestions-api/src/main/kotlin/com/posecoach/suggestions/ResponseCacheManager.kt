package com.posecoach.suggestions

import android.content.Context
import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Intelligent response caching system for pose suggestions
 * Uses pose similarity hashing and LRU eviction with persistence
 */
class ResponseCacheManager(private val context: Context) {

    private val mutex = Mutex()
    private val memoryCache = mutableMapOf<String, CacheEntry>()
    private val accessOrder = mutableListOf<String>() // For LRU tracking

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private val cacheDir by lazy {
        File(context.cacheDir, "pose_suggestions").apply {
            if (!exists()) mkdirs()
        }
    }

    companion object {
        private const val MAX_MEMORY_CACHE_SIZE = 50
        private const val MAX_DISK_CACHE_SIZE = 200
        private const val CACHE_VERSION = 1
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
        private const val POSE_SIMILARITY_THRESHOLD = 0.15f // 15% difference threshold
        private const val INDEX_FILE_NAME = "cache_index.json"
    }

    init {
        // Load cache index on initialization
        loadCacheIndex()
    }

    /**
     * Get cached suggestions for similar pose
     * @param landmarks The pose landmarks to find suggestions for
     * @return Cached suggestions if found and valid, null otherwise
     */
    suspend fun getCachedSuggestions(landmarks: PoseLandmarksData): PoseSuggestionsResponse? {
        return mutex.withLock {
            val poseHash = calculatePoseHash(landmarks)

            // Try exact match first
            val exactMatch = memoryCache[poseHash]
            if (exactMatch != null && !exactMatch.isExpired()) {
                updateAccessOrder(poseHash)
                Timber.d("Cache hit (exact): $poseHash")
                return@withLock exactMatch.response
            }

            // Try similarity matching
            val similarEntry = findSimilarCachedPose(landmarks)
            if (similarEntry != null) {
                updateAccessOrder(similarEntry.first)
                Timber.d("Cache hit (similar): ${similarEntry.first} for $poseHash")
                return@withLock similarEntry.second.response
            }

            // Try loading from disk
            val diskEntry = loadFromDisk(poseHash)
            if (diskEntry != null && !diskEntry.isExpired()) {
                memoryCache[poseHash] = diskEntry
                updateAccessOrder(poseHash)
                Timber.d("Cache hit (disk): $poseHash")
                return@withLock diskEntry.response
            }

            Timber.d("Cache miss: $poseHash")
            null
        }
    }

    /**
     * Cache pose suggestions with intelligent similarity grouping
     */
    suspend fun cacheSuggestions(
        landmarks: PoseLandmarksData,
        response: PoseSuggestionsResponse
    ) {
        mutex.withLock {
            val poseHash = calculatePoseHash(landmarks)
            val entry = CacheEntry(
                poseHash = poseHash,
                landmarks = landmarks,
                response = response,
                timestamp = System.currentTimeMillis(),
                accessCount = 1
            )

            // Add to memory cache
            memoryCache[poseHash] = entry
            updateAccessOrder(poseHash)

            // Persist to disk
            saveToDisk(poseHash, entry)

            // Cleanup if needed
            if (memoryCache.size > MAX_MEMORY_CACHE_SIZE) {
                evictLeastRecentlyUsed()
            }

            Timber.d("Cached suggestions for pose: $poseHash")
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats {
        return mutex.withLock {
            val diskFiles = cacheDir.listFiles()?.size ?: 0
            val totalMemorySize = memoryCache.size
            val expiredCount = memoryCache.values.count { it.isExpired() }

            CacheStats(
                memoryEntries = totalMemorySize,
                diskEntries = diskFiles,
                expiredEntries = expiredCount,
                hitRate = calculateHitRate(),
                totalSize = estimateCacheSize()
            )
        }
    }

    /**
     * Clear all cached data
     */
    suspend fun clearCache() {
        mutex.withLock {
            memoryCache.clear()
            accessOrder.clear()
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            Timber.d("Cache cleared")
        }
    }

    /**
     * Clean expired entries
     */
    suspend fun cleanExpiredEntries() {
        mutex.withLock {
            val expiredKeys = memoryCache.filterValues { it.isExpired() }.keys
            expiredKeys.forEach { key ->
                memoryCache.remove(key)
                accessOrder.remove(key)
                File(cacheDir, "$key.json").delete()
            }

            Timber.d("Cleaned ${expiredKeys.size} expired entries")
        }
    }

    private fun calculatePoseHash(landmarks: PoseLandmarksData): String {
        // Use key landmarks for hashing (torso and head for stability)
        val keyLandmarks = landmarks.landmarks.filter { landmark ->
            landmark.index in listOf(0, 11, 12, 23, 24) // nose, shoulders, hips
        }

        val hashInput = keyLandmarks.joinToString(",") { landmark ->
            "${(landmark.x * 100).toInt()}_${(landmark.y * 100).toInt()}"
        }

        return MessageDigest.getInstance("SHA-256")
            .digest(hashInput.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16) // Use first 16 characters
    }

    private fun findSimilarCachedPose(landmarks: PoseLandmarksData): Pair<String, CacheEntry>? {
        return memoryCache.entries.find { (_, entry) ->
            !entry.isExpired() && isPoseSimilar(landmarks, entry.landmarks)
        }?.let { it.key to it.value }
    }

    private fun isPoseSimilar(pose1: PoseLandmarksData, pose2: PoseLandmarksData): Boolean {
        if (pose1.landmarks.size != pose2.landmarks.size) return false

        // Compare key landmarks only for efficiency
        val keyIndices = listOf(0, 11, 12, 15, 16, 23, 24) // nose, shoulders, wrists, hips

        val differences = keyIndices.mapNotNull { index ->
            val landmark1 = pose1.landmarks.find { it.index == index }
            val landmark2 = pose2.landmarks.find { it.index == index }

            if (landmark1 != null && landmark2 != null) {
                val xDiff = abs(landmark1.x - landmark2.x)
                val yDiff = abs(landmark1.y - landmark2.y)
                kotlin.math.sqrt((xDiff * xDiff + yDiff * yDiff).toDouble()).toFloat()
            } else null
        }

        val avgDifference = differences.average().toFloat()
        return avgDifference < POSE_SIMILARITY_THRESHOLD
    }

    private fun updateAccessOrder(key: String) {
        accessOrder.remove(key)
        accessOrder.add(key)

        memoryCache[key]?.let { entry ->
            memoryCache[key] = entry.copy(accessCount = entry.accessCount + 1)
        }
    }

    private fun evictLeastRecentlyUsed() {
        if (accessOrder.isNotEmpty()) {
            val lruKey = accessOrder.removeAt(0)
            memoryCache.remove(lruKey)
            Timber.d("Evicted LRU entry: $lruKey")
        }
    }

    private fun saveToDisk(key: String, entry: CacheEntry) {
        try {
            val file = File(cacheDir, "$key.json")
            val cacheData = DiskCacheEntry(
                version = CACHE_VERSION,
                poseHash = entry.poseHash,
                response = entry.response,
                timestamp = entry.timestamp,
                accessCount = entry.accessCount
            )

            file.writeText(json.encodeToString(cacheData))
        } catch (e: Exception) {
            Timber.w(e, "Failed to save cache entry to disk: $key")
        }
    }

    private fun loadFromDisk(key: String): CacheEntry? {
        return try {
            val file = File(cacheDir, "$key.json")
            if (!file.exists()) return null

            val cacheData = json.decodeFromString<DiskCacheEntry>(file.readText())
            if (cacheData.version != CACHE_VERSION) {
                file.delete()
                return null
            }

            // Create minimal landmarks data for the entry
            val landmarks = PoseLandmarksData(emptyList(), cacheData.timestamp)

            CacheEntry(
                poseHash = cacheData.poseHash,
                landmarks = landmarks,
                response = cacheData.response,
                timestamp = cacheData.timestamp,
                accessCount = cacheData.accessCount
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to load cache entry from disk: $key")
            null
        }
    }

    private fun loadCacheIndex() {
        // Implementation for loading cache index
        // This would restore access order and metadata
    }

    private fun calculateHitRate(): Float {
        // This would track hit/miss ratio over time
        return 0.0f // Placeholder
    }

    private fun estimateCacheSize(): Long {
        return cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    private data class CacheEntry(
        val poseHash: String,
        val landmarks: PoseLandmarksData,
        val response: PoseSuggestionsResponse,
        val timestamp: Long,
        val accessCount: Int
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS
        }
    }

    @kotlinx.serialization.Serializable
    private data class DiskCacheEntry(
        val version: Int,
        val poseHash: String,
        val response: PoseSuggestionsResponse,
        val timestamp: Long,
        val accessCount: Int
    )
}

data class CacheStats(
    val memoryEntries: Int,
    val diskEntries: Int,
    val expiredEntries: Int,
    val hitRate: Float,
    val totalSize: Long
)