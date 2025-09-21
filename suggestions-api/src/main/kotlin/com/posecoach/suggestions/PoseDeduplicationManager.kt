package com.posecoach.suggestions

import com.posecoach.suggestions.models.PoseLandmarksData
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class PoseDeduplicationManager(
    private val deduplicationWindowMs: Long = 5000L,
    private val maxCacheSize: Int = 100
) {
    private data class CacheEntry(
        val hash: String,
        val timestamp: Long
    )

    private val recentPoses = ConcurrentHashMap<String, Long>()

    fun shouldProcessPose(landmarks: PoseLandmarksData): Boolean {
        val currentTime = System.currentTimeMillis()
        val poseHash = landmarks.hash()

        cleanupOldEntries(currentTime)

        val lastSeen = recentPoses[poseHash]
        if (lastSeen != null) {
            val timeSinceLastSeen = currentTime - lastSeen
            if (timeSinceLastSeen < deduplicationWindowMs) {
                Timber.d("Pose hash $poseHash seen ${timeSinceLastSeen}ms ago, skipping")
                return false
            }
        }

        recentPoses[poseHash] = currentTime
        Timber.d("Processing new/expired pose hash: $poseHash")
        return true
    }

    private fun cleanupOldEntries(currentTime: Long) {
        if (recentPoses.size > maxCacheSize) {
            val cutoffTime = currentTime - deduplicationWindowMs
            recentPoses.entries.removeIf { entry ->
                entry.value < cutoffTime
            }
            Timber.d("Cleaned up old pose entries, remaining: ${recentPoses.size}")
        }
    }

    fun clear() {
        recentPoses.clear()
        Timber.d("Pose deduplication cache cleared")
    }

    fun getCacheSize(): Int = recentPoses.size
}