package com.example.whatsapp.data.night

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.whatsapp.MainActivity
import com.example.whatsapp.R

object NightNotificationHelper {
    private const val CHANNEL_SCHEDULES = "night_schedules"

    fun notifyScheduledResult(
        context: Context,
        task: NightScheduledTaskEntity,
        reply: String,
    ) {
        val app = context.applicationContext
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(app)

        val intent = Intent(app, MainActivity::class.java)
            .putExtra("night_chat_id", task.chatId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            app,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(app, CHANNEL_SCHEDULES)
            .setSmallIcon(R.drawable.ic_night)
            .setContentTitle("Night")
            .setContentText(reply.ifBlank { task.prompt }.take(140))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(reply.ifBlank { task.prompt }.take(1_500))
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(app)
            .notify(task.id.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCHEDULES,
                "Night scheduled tasks",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Results from reminders and scheduled Night AI tasks"
            }
        )
    }
}
