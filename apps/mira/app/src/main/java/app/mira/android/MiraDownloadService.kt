package app.mira.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MiraDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: MiraDownloadManager
    private lateinit var notifications: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        manager = (application as MiraApplication).downloadManager
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:mira-downloads")
            .apply { setReferenceCounted(false) }

        startForegroundNow()
        scope.launch {
            combine(manager.statuses, manager.globalPaused) { statuses, paused ->
                statuses.values.toList() to paused
            }.collect { (statuses, paused) ->
                val runnable = statuses.count {
                    it.state == MiraDownloadState.QUEUED ||
                        it.state == MiraDownloadState.DOWNLOADING ||
                        it.state == MiraDownloadState.WAITING_FOR_NETWORK
                }
                if (runnable > 0 && !paused) {
                    acquireWakeLock()
                    val notification = buildNotification(statuses, paused)
                    if (!foreground) {
                        foreground = true
                        startForeground(NOTIFICATION_ID, notification)
                    } else {
                        notifications.notify(NOTIFICATION_ID, notification)
                    }
                } else {
                    releaseWakeLock()
                    if (foreground) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        foreground = false
                    }
                    if (runnable == 0) {
                        notifications.cancel(NOTIFICATION_ID)
                        stopSelf()
                    } else {
                        notifications.notify(NOTIFICATION_ID, buildNotification(statuses, paused))
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNow()
        when (intent?.action) {
            ACTION_PAUSE_ALL -> manager.pauseAll()
            ACTION_RESUME_ALL -> manager.resumeAll()
            ACTION_START, null -> manager.kickScheduler()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNow() {
        if (foreground) return
        foreground = true
        startForeground(
            NOTIFICATION_ID,
            buildNotification(manager.statuses.value.values.toList(), manager.globalPaused.value),
        )
    }

    private fun buildNotification(
        statuses: List<MiraDownloadStatus>,
        paused: Boolean,
    ): Notification {
        val active = statuses.count { it.state == MiraDownloadState.DOWNLOADING }
        val waiting = statuses.count { it.state == MiraDownloadState.WAITING_FOR_NETWORK }
        val queued = statuses.count { it.state == MiraDownloadState.QUEUED }
        val title = when {
            paused -> "Mira downloads paused"
            active > 0 -> "Downloading $active ${if (active == 1) "item" else "items"}"
            waiting > 0 -> "Waiting for network"
            queued > 0 -> "Preparing downloads"
            else -> "Mira downloads"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            20,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val action = if (paused) ACTION_RESUME_ALL else ACTION_PAUSE_ALL
        val actionIntent = Intent(this, MiraDownloadService::class.java).setAction(action)
        val actionPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                21,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                this,
                21,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(title)
            .setContentText("Movies and TV downloads continue outside the app")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(!paused && active + waiting + queued > 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Resume all" else "Pause all",
                actionPending,
            )
            .apply {
                if (!paused && active + waiting > 0) setProgress(0, 0, true)
            }
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background movie and TV download progress"
                setShowBadge(false)
            },
        )
    }

    private fun acquireWakeLock() {
        wakeLock?.let { if (!it.isHeld) it.acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        const val ACTION_START = "app.mira.android.download.START"
        const val ACTION_PAUSE_ALL = "app.mira.android.download.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "app.mira.android.download.RESUME_ALL"
        private const val CHANNEL_ID = "mira_downloads"
        private const val NOTIFICATION_ID = 0x4D495241
    }
}
