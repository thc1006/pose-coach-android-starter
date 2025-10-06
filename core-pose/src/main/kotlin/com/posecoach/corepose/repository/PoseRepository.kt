package com.posecoach.corepose.repository

import android.graphics.Bitmap

interface PoseRepository {
    suspend fun detectAsync(bitmap: Bitmap, timestampMs: Long)
    suspend fun detectAsync(bitmap: Bitmap, timestampMs: Long, rotationDegrees: Int)
    fun release()
}