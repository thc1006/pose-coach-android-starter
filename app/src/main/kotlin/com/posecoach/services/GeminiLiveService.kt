package com.posecoach.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.posecoach.R

/**
 * Gemini Live API Background Service
 * Referenced in AndroidManifest.xml but previously missing
 */
class GeminiLiveService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "gemini_live_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gemini Live Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for Gemini Live API"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pose Coach Live")
            .setContentText("Live coaching is active")
            .setSmallIcon(R.drawable.ic_coaching_active)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
