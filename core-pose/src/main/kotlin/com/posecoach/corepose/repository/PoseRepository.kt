package com.posecoach.corepose.repository

import android.graphics.Bitmap
import android.content.Context

interface PoseRepository {
    suspend fun init(context: Context, modelPath: String? = null)
    fun start(listener: PoseDetectionListener)
    fun stop()
    suspend fun detectAsync(bitmap: Bitmap, timestampMs: Long)
    fun isRunning(): Boolean
}