package com.night.homira.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

class HomiraScreenShareService : Service() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Screen sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while Homira is sharing your screen in a call."
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Sharing your screen")
            .setContentText("Homira is sharing your screen in a call")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "homira_screen_share"
        private const val NOTIFICATION_ID = 4102
    }
}
