package app.nami.android

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground process anchor for Nami downloads.
 *
 * Transfer ownership remains process-wide in [NamiDownloadManager], but all active queue
 * execution is kept alive by this foreground service instead of by an Activity. If Android
 * recreates the process/service, the manager restores the persisted queue and this service
 * kicks the scheduler again.
 */
class NamiDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: NamiDownloadManager
    private lateinit var notificationManager: NotificationManager
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        manager = (application as NamiApplication).downloadManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            packageName + ":nami-downloads",
        ).apply {
            setReferenceCounted(false)
        }

        observerJob = scope.launch {
            combine(manager.statuses, manager.globalPaused) { statuses, paused ->
                EngineSnapshot(statuses.values.toList(), paused)
            }.collect(::onEngineSnapshot)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded()

        when (intent?.action) {
            ACTION_PAUSE_ALL -> manager.pauseAll()
            ACTION_RESUME_ALL -> manager.resumeAll()
            ACTION_START, null -> Unit
        }

        if (!manager.globalPaused.value) {
            manager.kickScheduler()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundIfNeeded() {
        if (foregroundStarted) return
        foregroundStarted = true
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                EngineSnapshot(
                    statuses = manager.statuses.value.values.toList(),
                    globallyPaused = manager.globalPaused.value,
                ),
            ),
        )
    }

    private fun onEngineSnapshot(snapshot: EngineSnapshot) {
        val runnable = snapshot.statuses.count {
            it.state == NamiDownloadState.QUEUED ||
                it.state == NamiDownloadState.DOWNLOADING ||
                it.state == NamiDownloadState.WAITING_FOR_NETWORK
        }
        val globallyPausedItems = snapshot.statuses.count {
            it.state == NamiDownloadState.PAUSED &&
                it.pauseReason == NamiPauseReason.GLOBAL
        }

        if (runnable > 0 && !snapshot.globallyPaused) {
            acquireWakeLock()
            val notification = buildNotification(snapshot)
            if (!foregroundStarted) {
                foregroundStarted = true
                startForeground(NOTIFICATION_ID, notification)
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            return
        }

        releaseWakeLock()

        if (snapshot.globallyPaused && globallyPausedItems > 0) {
            val pausedNotification = buildNotification(snapshot)
            notificationManager.notify(NOTIFICATION_ID, pausedNotification)
            if (foregroundStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                foregroundStarted = false
            }
            stopSelf()
            return
        }

        if (runnable == 0) {
            if (foregroundStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                foregroundStarted = false
            }
            notificationManager.cancel(NOTIFICATION_ID)
            stopSelf()
        }
    }

    private fun buildNotification(snapshot: EngineSnapshot): Notification {
        val active = snapshot.statuses.count { it.state == NamiDownloadState.DOWNLOADING }
        val waiting = snapshot.statuses.count {
            it.state == NamiDownloadState.WAITING_FOR_NETWORK
        }
        val queued = snapshot.statuses.count { it.state == NamiDownloadState.QUEUED }
        val globalPaused = snapshot.statuses.count {
            it.state == NamiDownloadState.PAUSED &&
                it.pauseReason == NamiPauseReason.GLOBAL
        }

        val title = when {
            snapshot.globallyPaused && globalPaused > 0 -> "Downloads paused"
            active > 0 -> "Downloading $active " + if (active == 1) "episode" else "episodes"
            waiting > 0 -> "Waiting for network"
            queued > 0 -> "Preparing downloads"
            else -> "Nami downloads"
        }

        val details = buildList {
            if (queued > 0) add("$queued queued")
            if (waiting > 0) add("$waiting waiting")
            if (globalPaused > 0) add("$globalPaused paused")
        }.joinToString(" · ").ifBlank {
            if (active > 0) "Downloads continue in the background" else "No active downloads"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val actionIntent = Intent(this, NamiDownloadService::class.java).setAction(
            if (snapshot.globallyPaused) ACTION_RESUME_ALL else ACTION_PAUSE_ALL,
        )
        val actionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                101,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                this,
                101,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(details)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(!snapshot.globallyPaused && (active + waiting + queued) > 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (snapshot.globallyPaused) {
                    android.R.drawable.ic_media_play
                } else {
                    android.R.drawable.ic_media_pause
                },
                if (snapshot.globallyPaused) "Resume all" else "Pause all",
                actionPendingIntent,
            )
            .apply {
                if (!snapshot.globallyPaused && active + waiting > 0) {
                    setProgress(0, 0, true)
                }
            }
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background anime download progress"
                setShowBadge(false)
            },
        )
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) {
            lock.acquire()
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
        }
    }

    private data class EngineSnapshot(
        val statuses: List<NamiDownloadStatus>,
        val globallyPaused: Boolean,
    )

    companion object {
        const val ACTION_START = "app.nami.android.download.START"
        const val ACTION_PAUSE_ALL = "app.nami.android.download.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "app.nami.android.download.RESUME_ALL"

        private const val CHANNEL_ID = "nami_downloads"
        private const val NOTIFICATION_ID = 0x4E414D49
    }
}
