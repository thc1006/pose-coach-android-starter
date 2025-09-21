package com.posecoach.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

/**
 * Pose Analysis Background Service
 * Referenced in AndroidManifest.xml but previously missing
 */
class PoseAnalysisService : Service() {

    override fun onCreate() {
        super.onCreate()
        Timber.d("PoseAnalysisService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("PoseAnalysisService started")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("PoseAnalysisService destroyed")
    }
}
