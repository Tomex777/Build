package com.nice.juicewrld

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * System media-session bridge for the real shared MediaPlayer. The service
 * holds no second playback engine and never simulates downloaded recordings.
 */
class PlaybackService : Service() {
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: FanPlayer
    private lateinit var session: MediaSession
    private lateinit var notifications: NotificationManager
    private var isForeground = false
    private var lastNotificationKey: String? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        player = FanRuntime.player(application as Application)
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Music playback", NotificationManager.IMPORTANCE_LOW)
        )
        session = MediaSession(this, "NIGHT-JuiceWRLD").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    val current = player.state.value
                    if (!current.isPlaying) current.activeSongId?.let(player::play)
                }
                override fun onPause() = player.pause()
                override fun onSkipToNext() = player.skip(1)
                override fun onSkipToPrevious() = player.skip(-1)
                override fun onSeekTo(pos: Long) {
                    val duration = player.state.value.durationMs
                    if (duration > 0) player.seekTo(pos.toFloat() / duration)
                }
            })
            isActive = true
        }
        worker.launch {
            player.state.collect { state ->
                publishSession(state)
                val song = state.activeSongId?.let(FanSongs::find)
                if (song == null) return@collect
                val status = "${song.id}/${state.isPlaying}/${state.isPreparing}"
                if (status != lastNotificationKey) {
                    lastNotificationKey = status
                    val notification = buildNotification(state, song)
                    if (state.isPlaying || state.isPreparing) {
                        showForeground(notification)
                    } else {
                        if (isForeground) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                            isForeground = false
                        }
                        notifications.notify(NOTIFICATION_ID, notification)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires a foreground notification immediately after a
        // user-initiated startForegroundService call (within five seconds).
        if (!isForeground) {
            val state = player.state.value
            val song = state.activeSongId?.let(FanSongs::find)
            showForeground(buildNotification(state, song))
        }
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val state = player.state.value
                val currentId = state.activeSongId
                if (state.isPlaying) player.pause()
                else if (currentId != null) player.play(currentId)
            }
            ACTION_PREV -> player.skip(-1)
            ACTION_NEXT -> player.skip(1)
        }
        return START_NOT_STICKY
    }

    private fun showForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun publishSession(state: FanPlayerState) {
        val song = state.activeSongId?.let(FanSongs::find)
        if (song != null) {
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Juice WRLD")
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs.toLong())
                    .build()
            )
        }
        val playbackStatus = when {
            state.isPreparing -> PlaybackState.STATE_BUFFERING
            state.isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(
                    playbackStatus, state.positionMs.toLong(),
                    if (state.isPlaying) 1f else 0f,
                    SystemClock.elapsedRealtime()
                )
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
    }

    private fun command(action: String, request: Int): PendingIntent {
        return PendingIntent.getService(
            this, request, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(state: FanPlayerState, song: FanSong?): Notification {
        val openApp = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val middle = if (state.isPlaying) "Pause" else "Play"
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(song?.title ?: "Juice WRLD")
            .setContentText(song?.album ?: "999")
            .setContentIntent(openApp)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(state.isPlaying)
            .addAction(Notification.Action(
                R.drawable.ic_stat_music, "Previous", command(ACTION_PREV, 2)
            ))
            .addAction(Notification.Action(
                if (state.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                middle, command(ACTION_TOGGLE, 3)
            ))
            .addAction(Notification.Action(
                R.drawable.ic_stat_music, "Next", command(ACTION_NEXT, 4)
            ))
            .setStyle(
                Notification.MediaStyle().setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        if (::player.isInitialized) player.pause()
        worker.cancel()
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "juice_audio_playback"
        private const val NOTIFICATION_ID = 999
        private const val ACTION_TOGGLE = "com.nice.juicewrld.TOGGLE_PLAY"
        private const val ACTION_PREV = "com.nice.juicewrld.PREVIOUS"
        private const val ACTION_NEXT = "com.nice.juicewrld.NEXT"
        @Volatile private var running = false

        fun ensureStarted(context: Context) {
            if (running) return
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, PlaybackService::class.java)
                )
            } catch (_: RuntimeException) {
                // Android 12+ can refuse foreground starts initiated after an
                // activity entered background; playback itself still reports
                // any native decoding/focus error to the user.
            }
        }
    }
}
