package com.tomex777.annie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Owns all media and file transfers independently of any Activity or Compose screen. */
class DownloadTransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloader: AnnieMediaDownloader? = null
    private var lastNotificationAt = 0L
    private var lastNotificationText = ""
    private var lastPersistedAt = 0L
    private val removingIds = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundCompat(notification("Restoring downloads"))
        downloader = AnnieMediaDownloader(applicationContext) { changed ->
            if (changed.id !in removingIds) {
                persistProgress(changed)
                updateNotification()
                stopWhenIdle()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(notification("Managing downloads"))
        scope.launch {
            when (intent?.action) {
                ACTION_ENQUEUE, ACTION_RESUME -> intent.getStringExtra(EXTRA_ID)
                    ?.also(removingIds::remove)
                    ?.let { DownloadStore.find(this@DownloadTransferService, it) }
                    ?.let { downloader?.resume(it) }
                ACTION_PAUSE -> intent.getStringExtra(EXTRA_ID)
                    ?.let { DownloadStore.find(this@DownloadTransferService, it) }
                    ?.let { downloader?.pause(it) }
                ACTION_REMOVE -> intent.getStringExtra(EXTRA_ID)
                    ?.let { DownloadStore.find(this@DownloadTransferService, it) }
                    ?.let { item ->
                        removingIds.add(item.id)
                        downloader?.remove(item) {
                            DownloadStore.remove(this@DownloadTransferService, item.id)
                            updateNotification(force = true)
                            stopWhenIdle()
                        }
                    }
                else -> restoreActiveDownloads()
            }
            updateNotification(force = true)
            stopWhenIdle()
        }
        return START_STICKY
    }

    private fun restoreActiveDownloads() {
        DownloadStore.read(this).filter {
            it.sourceUrl.isNotBlank() && it.state in ACTIVE_DOWNLOAD_STATES
        }.forEach { item -> downloader?.resume(item.copy(state = DownloadState.QUEUED, failureReason = "")) }
    }

    private fun activeDownloads(): List<DownloadItem> = DownloadStore.read(this).filter {
        it.state in ACTIVE_DOWNLOAD_STATES
    }

    private fun persistProgress(changed: DownloadItem) {
        val previous = DownloadStore.find(this, changed.id)
        val now = SystemClock.elapsedRealtime()
        val terminal = changed.state !in ACTIVE_DOWNLOAD_STATES
        if (previous == null || previous.state != changed.state || terminal || now - lastPersistedAt >= PERSIST_INTERVAL_MS) {
            DownloadStore.update(this, changed)
            lastPersistedAt = now
        }
    }

    private fun updateNotification(force: Boolean = false) {
        val active = activeDownloads()
        val current = active.firstOrNull { it.state in setOf(DownloadState.DOWNLOADING, DownloadState.WAITING_FOR_CONNECTION) }
        val title = when {
            active.isEmpty() -> "No active downloads"
            active.size == 1 -> current?.title ?: active.first().title
            else -> "${active.size} active downloads"
        }
        val text = when {
            active.isEmpty() -> "Annie downloads are up to date"
            current?.state == DownloadState.WAITING_FOR_CONNECTION -> "Waiting for connection · ${current.title}"
            current != null && current.bytesTotal > 0L ->
                "Downloading · ${(current.progress * 100).toInt()}% · ${current.title}"
            current != null -> "Downloading · ${current.title}"
            else -> "Queued · ${active.first().title}"
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && text == lastNotificationText && now - lastNotificationAt < NOTIFICATION_INTERVAL_MS) return
        lastNotificationText = text
        lastNotificationAt = now
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, title))
    }

    private fun stopWhenIdle() {
        if (activeDownloads().isNotEmpty()) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(text: String, title: String = "Annie downloads"): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 20, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(activeDownloads().isNotEmpty())
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Progress for active Annie downloads"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        downloader?.close()
        downloader = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ENQUEUE = "com.tomex777.annie.download.ENQUEUE"
        const val ACTION_RESUME = "com.tomex777.annie.download.RESUME"
        const val ACTION_PAUSE = "com.tomex777.annie.download.PAUSE"
        const val ACTION_REMOVE = "com.tomex777.annie.download.REMOVE"
        const val EXTRA_ID = "download_id"
        private const val CHANNEL_ID = "annie_downloads"
        private const val NOTIFICATION_ID = 4202
        private const val NOTIFICATION_INTERVAL_MS = 500L
        private const val PERSIST_INTERVAL_MS = 750L
        private val ACTIVE_DOWNLOAD_STATES = setOf(
            DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.WAITING_FOR_CONNECTION,
        )

        internal fun enqueue(context: Context, item: DownloadItem) {
            DownloadStore.update(context, item.copy(state = DownloadState.QUEUED, failureReason = ""))
            send(context, ACTION_ENQUEUE, item.id)
        }

        internal fun pause(context: Context, item: DownloadItem) = send(context, ACTION_PAUSE, item.id)

        internal fun resume(context: Context, item: DownloadItem) {
            DownloadStore.update(context, item.copy(state = DownloadState.QUEUED, failureReason = ""))
            send(context, ACTION_RESUME, item.id)
        }

        internal fun remove(context: Context, item: DownloadItem) = send(context, ACTION_REMOVE, item.id)

        internal fun restore(context: Context) {
            if (DownloadStore.read(context).any {
                    it.sourceUrl.isNotBlank() && it.state in ACTIVE_DOWNLOAD_STATES
                }
            ) send(context, null, null)
        }

        private fun send(context: Context, action: String?, id: String?) {
            val intent = Intent(context, DownloadTransferService::class.java).setAction(action)
            if (id != null) intent.putExtra(EXTRA_ID, id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
