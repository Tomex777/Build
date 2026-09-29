package com.tomex777.annie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest

/** Owns Annie's in-chat music playback independently of any Activity or chat bubble. */
class MusicPlaybackService : Service() {
    data class Snapshot(
        val stream: String? = null,
        val title: String = "",
        val artist: String = "",
        val artwork: String = "",
        val durationMs: Int = 0,
        val positionMs: Int = 0,
        val playing: Boolean = false,
        val prepared: Boolean = false,
    )

    inner class LocalBinder : Binder() {
        fun service(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var resumeAfterFocusGain = false
    private var artworkBitmap: Bitmap? = null
    private var requestedArtwork: String? = null
    @Volatile private var snapshot = Snapshot()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                player?.setVolume(1f, 1f)
                if (resumeAfterFocusGain) {
                    resumeAfterFocusGain = false
                    player?.start()
                    publish(playing = true)
                }
                updateSessionAndNotification()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.2f, 0.2f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterFocusGain = snapshot.playing
                pausePlayer(abandonFocusAfterPause = false)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocusGain = false
                pausePlayer()
                abandonAudioFocus()
            }
        }
    }

    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pausePlayer()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        session = MediaSession(this, "AnnieMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumePlayer()
                override fun onPause() = pausePlayer()
                override fun onStop() = stopPlayback()
                override fun onSeekTo(pos: Long) = seekTo(pos.toInt())
            }, handler)
            isActive = true
        }
        registerReceiver(noisyReceiver, android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_STREAM)
                if (!url.isNullOrBlank()) play(
                    url = url,
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty(),
                    artwork = intent.getStringExtra(EXTRA_ARTWORK).orEmpty(),
                )
            }
            ACTION_TOGGLE -> if (snapshot.playing) pausePlayer() else resumePlayer()
            ACTION_PAUSE -> pausePlayer()
            ACTION_STOP -> stopPlayback()
            ACTION_SEEK -> seekTo(intent.getIntExtra(EXTRA_POSITION, 0))
        }
        return START_NOT_STICKY
    }

    fun currentSnapshot(): Snapshot = snapshot.copy(positionMs = currentPosition())

    internal fun sessionPlaybackStateForTest(): Int? = session?.controller?.playbackState?.state

    fun play(url: String, title: String, artist: String, artwork: String) {
        if (snapshot.stream == url && snapshot.prepared) {
            resumePlayer()
            return
        }
        if (!requestAudioFocus()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        runCatching {
            player?.release()
            player = null
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnPreparedListener { ready ->
                    publish(durationMs = ready.duration.coerceAtLeast(0), prepared = true)
                    if (hasAudioFocus) {
                        ready.start()
                        publish(playing = true)
                    }
                    updateSessionAndNotification()
                }
                setOnCompletionListener {
                    publish(playing = false, positionMs = 0)
                    updateSessionAndNotification()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Music playback failed ($what/$extra)")
                    publish(playing = false, prepared = false)
                    abandonAudioFocus()
                    true
                }
                setDataSource(this@MusicPlaybackService, Uri.parse(url))
                prepareAsync()
            }
            publish(stream = url, title = title.ifBlank { "Untitled track" }, artist = artist, artwork = artwork,
                durationMs = 0, positionMs = 0, playing = false, prepared = false)
            updateSessionAndNotification()
        }.onFailure {
            Log.w(TAG, "Unable to start music playback", it)
            publish(playing = false, prepared = false)
            abandonAudioFocus()
        }
    }

    fun togglePlayback() = if (snapshot.playing) pausePlayer() else resumePlayer()

    fun seekTo(positionMs: Int) {
        val activePlayer = player ?: return
        runCatching { activePlayer.seekTo(positionMs.coerceAtLeast(0)) }
        publish(positionMs = positionMs.coerceAtLeast(0))
        updateSessionAndNotification()
    }

    private fun resumePlayer() {
        val activePlayer = player ?: return
        if (!requestAudioFocus()) return
        runCatching {
            if (snapshot.prepared && !activePlayer.isPlaying) activePlayer.start()
            publish(playing = activePlayer.isPlaying)
            updateSessionAndNotification()
        }.onFailure { Log.w(TAG, "Unable to resume music playback", it) }
    }

    private fun pausePlayer(abandonFocusAfterPause: Boolean = true) {
        runCatching { if (player?.isPlaying == true) player?.pause() }
        publish(playing = false)
        updateSessionAndNotification()
        if (abandonFocusAfterPause) abandonAudioFocus()
    }

    private fun stopPlayback() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        publish(stream = null, playing = false, prepared = false, positionMs = 0, durationMs = 0)
        abandonAudioFocus()
        updateSessionAndNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun currentPosition(): Int = runCatching {
        if (snapshot.prepared) player?.currentPosition?.coerceAtLeast(0) ?: snapshot.positionMs else snapshot.positionMs
    }.getOrDefault(snapshot.positionMs)

    private fun publish(
        stream: String? = snapshot.stream,
        title: String = snapshot.title,
        artist: String = snapshot.artist,
        artwork: String = snapshot.artwork,
        durationMs: Int = snapshot.durationMs,
        positionMs: Int = snapshot.positionMs,
        playing: Boolean = snapshot.playing,
        prepared: Boolean = snapshot.prepared,
    ) {
        snapshot = Snapshot(stream, title, artist, artwork, durationMs, positionMs, playing, prepared)
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(focusListener, handler)
                .setWillPauseWhenDucked(false)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let(audioManager::abandonAudioFocusRequest)
        else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        hasAudioFocus = false
    }

    private fun updateSessionAndNotification() {
        loadArtworkIfNeeded()
        val current = currentSnapshot()
        val state = if (current.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val metadata = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, current.title)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, current.artist)
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, current.durationMs.toLong())
        artworkBitmap?.let { metadata.putBitmap(android.media.MediaMetadata.METADATA_KEY_ART, it) }
        session?.setMetadata(metadata.build())
        session?.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO)
            .setState(state, current.positionMs.toLong(), if (current.playing) 1f else 0f)
            .build())
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun loadArtworkIfNeeded() {
        val artwork = snapshot.artwork
        if (artwork == requestedArtwork) return
        requestedArtwork = artwork
        artworkBitmap = null
        if (artwork.isBlank()) return
        ImageLoader(this).enqueue(
            ImageRequest.Builder(this)
                .data(artwork)
                .size(512, 512)
                .allowHardware(false)
                .target(onSuccess = { drawable ->
                    val bitmap = runCatching { drawable.toBitmap() }.getOrNull() ?: return@target
                    handler.post {
                        if (snapshot.artwork == artwork) {
                            artworkBitmap = bitmap
                            updateSessionAndNotification()
                        }
                    }
                })
                .build()
        )
    }

    private fun buildNotification(): Notification {
        val current = snapshot
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val toggleIntent = PendingIntent.getService(this, 2, actionIntent(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 3, actionIntent(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.annie_notification_mark)
            .setContentTitle(current.title.ifBlank { "Annie music" })
            .setContentText(current.artist.ifBlank { "Playback controls" })
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(current.playing)
            .addAction(Notification.Action.Builder(
                if (current.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (current.playing) "Pause" else "Play", toggleIntent).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0))
            .build()
    }

    private fun actionIntent(action: String): Intent = Intent(this, MusicPlaybackService::class.java).setAction(action)

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music playback", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Controls for music playing in Annie"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        session?.isActive = false
        session?.release()
        session = null
        player?.release()
        player = null
        abandonAudioFocus()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.tomex777.annie.music.PLAY"
        const val ACTION_TOGGLE = "com.tomex777.annie.music.TOGGLE"
        const val ACTION_PAUSE = "com.tomex777.annie.music.PAUSE"
        const val ACTION_STOP = "com.tomex777.annie.music.STOP"
        const val ACTION_SEEK = "com.tomex777.annie.music.SEEK"
        const val EXTRA_STREAM = "stream"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ARTWORK = "artwork"
        const val EXTRA_POSITION = "position"
        private const val CHANNEL_ID = "annie_music_playback"
        private const val NOTIFICATION_ID = 4201
        private const val TAG = "MusicPlaybackService"

        fun start(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(context, MusicPlaybackService::class.java).setAction(action).apply(extras)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
