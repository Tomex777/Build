package com.night.echospace.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.night.echospace.MainActivity
import com.night.echospace.model.SceneStore
import com.night.echospace.model.Scenes
import com.night.echospace.model.SoundSource

/** Keeps soundscapes playing with the screen off and displays a media playback notification. */
class SpatialAudioService : Service() {
    inner class LocalBinder : Binder() { fun service(): SpatialAudioService = this@SpatialAudioService }
    private val binder = LocalBinder()
    // Never open an AudioTrack just because the editor binds: audio output is
    // created on the first explicit Play, and retried after a route failure.
    private var engine: AudioEngine? = null
    private var currentSources: List<SoundSource> = emptyList()
    private var currentHeading: Float = 0f
    private var currentLiving: Boolean = true
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var scheduledStop: Runnable? = null
    private var stopAtMs = 0L
    private var playing = false
    private var focusRequest: AudioFocusRequest? = null
    private var sessionVolume = 0.76f
    private var currentSceneTitle = "Your environment"
    private var noisyRegistered = false
    // Never continue spatial headphone playback through a suddenly exposed speaker.
    private val becomingNoisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) pause()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val routeFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(becomingNoisy, routeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(becomingNoisy, routeFilter)
        }
        noisyRegistered = true
        val store = SceneStore(this)
        val restored = store.restore()
        currentSceneTitle = restored?.first ?: Scenes.rainChamber.title
        currentSources = restored?.second ?: Scenes.rainChamber.sources
        sessionVolume = 1f // Only Android's physical volume keys set final loudness.
        currentHeading = store.heading()
        currentLiving = store.living()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Soundscape playback", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun updateAudio(title: String, sources: List<SoundSource>, volume: Float, heading: Float, living: Boolean = true) {
        val titleChanged = currentSceneTitle != title
        currentSceneTitle = title
        sessionVolume = volume.coerceIn(0f, 1f)
        currentSources = sources.toList()
        currentHeading = heading
        currentLiving = living
        engine?.setSources(currentSources)
        engine?.setMasterVolume(sessionVolume)
        engine?.setHeading(currentHeading)
        engine?.setLiving(currentLiving)
        // Dragging a marker updates the audio instantly. Rebuilding the Android
        // notification on every drag event causes needless main-thread work.
        if (playing && titleChanged) notificationManager().notify(NOTIFICATION_ID, notification())
    }

    fun isPlaying(): Boolean = playing
    fun error(): String? = engine?.failure
    fun remainingSeconds(): Long = if (stopAtMs == 0L) 0L else
        ((stopAtMs - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)

    fun setSleepTimer(minutes: Int) {
        scheduledStop?.let(handler::removeCallbacks)
        scheduledStop = null
        stopAtMs = 0L
        if (minutes > 0) {
            stopAtMs = System.currentTimeMillis() + minutes.toLong() * 60_000L
            val task = Runnable { stopAtMs = 0L; scheduledStop = null; pause() }
            scheduledStop = task
            handler.postDelayed(task, minutes.toLong() * 60_000L)
        }
        if (playing) notificationManager().notify(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE, ACTION_STOP -> pause()
        }
        return START_NOT_STICKY
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun play() {
        if (!playing) {
            if (!requestFocus()) {
                pause()
                return
            }
            // A failed or unplugged audio route must not permanently brick Play.
            // Rebuilding only after explicit Play also avoids idle decoder/track
            // allocation merely from entering EchoSpace.
            if (engine?.failure != null) {
                engine?.close()
                engine = null
            }
            if (engine == null) {
                engine = AudioEngine(applicationContext) { message ->
                    handler.post {
                        // Retain failures even if the user was switching activities.
                        getSharedPreferences("echospace_audio", MODE_PRIVATE).edit()
                            .putString("last_error", message.take(160)).apply()
                        if (playing) pause()
                    }
                }
            }
            engine?.setSources(currentSources)
            engine?.setMasterVolume(sessionVolume)
            engine?.setHeading(currentHeading)
            engine?.setLiving(currentLiving)
            playing = true
            // Android requires foreground promptly after startForegroundService.
            startForeground(NOTIFICATION_ID, notification())
            engine?.setPlaying(true)
        }
        notificationManager().notify(NOTIFICATION_ID, notification())
    }

    private fun pause() {
        playing = false
        engine?.setPlaying(false)
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAction = if (playing) ACTION_PAUSE else ACTION_PLAY
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, SpatialAudioService::class.java).setAction(toggleAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = if (stopAtMs > 0) "Sleep timer active • Stereo headphones recommended"
            else "Spatial soundscape • Offline"
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("EchoSpace · $currentSceneTitle")
            .setContentText(detail)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_pause,
                if (playing) "Pause" else "Play",
                toggle
            ).build())
            .build()
    }

    override fun onDestroy() {
        if (noisyRegistered) {
            unregisterReceiver(becomingNoisy)
            noisyRegistered = false
        }
        scheduledStop?.let(handler::removeCallbacks)
        engine?.close()
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.night.echospace.PLAY"
        const val ACTION_PAUSE = "com.night.echospace.PAUSE"
        const val ACTION_STOP = "com.night.echospace.STOP"
        private const val CHANNEL_ID = "echospace_playback"
        private const val NOTIFICATION_ID = 510
    }
}
