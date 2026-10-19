package com.night.soundatlas

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Owns the offline audio engine independently of the world-editor Activity.
 * Playback survives leaving the editor or turning off the display.
 * Never auto-start playback after Android kills the process.
 */
class AtlasPlaybackService:Service() {
    inner class LocalBinder:Binder() { fun service():AtlasPlaybackService=this@AtlasPlaybackService }
    private val binder=LocalBinder()
    private val main=Handler(Looper.getMainLooper())
    private lateinit var engine:AudioEngine
    private lateinit var manager:AudioManager
    private var focusRequest:AudioFocusRequest?=null
    private var playing=false
    private var listening=false
    private var shouldResume=false
    private var world=AtlasPresets.create(0)
    var onChanged:(()->Unit)?=null

    private val focusListener=AudioManager.OnAudioFocusChangeListener { focus ->
        main.post {
            when(focus) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if(shouldResume && !playing) {
                        shouldResume=false
                        startSession()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    shouldResume=playing
                    pauseSession(abandonFocus=false)
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    shouldResume=false
                    pauseSession(abandonFocus=true)
                }
            }
        }
    }
    private val noisy=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            if(intent?.action==AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                shouldResume=false
                pauseSession()
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        manager=getSystemService(Context.AUDIO_SERVICE) as AudioManager
        engine=AudioEngine(assets){ error ->
            main.post {
                if(playing){
                    getSharedPreferences("atlas-audio-error",MODE_PRIVATE).edit()
                        .putString("last_error",error.take(180)).apply()
                    pauseSession()
                }
            }
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"World sound",NotificationManager.IMPORTANCE_LOW)
        )
        val filter=IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if(Build.VERSION.SDK_INT>=33) {
            registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisy,filter)
        }
        listening=true
        try {
            getSharedPreferences("atlas-worlds",MODE_PRIVATE)
                .getString("last",null)?.let { world=AtlasCodec.decode(it) }
        } catch(_:Exception){}
        engine.update(world)
    }
    override fun onBind(intent:Intent?):IBinder=binder
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action) {
            ACTION_PLAY -> {
                val source=intent.getStringExtra("world")
                if(source!=null) {
                    try {world=AtlasCodec.decode(source)}catch(_:Exception){}
                }
                startSession()
            }
            ACTION_PAUSE -> pauseSession()
        }
        return START_NOT_STICKY
    }
    fun update(scene:World) {
        world=scene.copyForAudio()
        engine.update(world)
        onChanged?.invoke()
    }
    fun isPlaying():Boolean = playing && engine.isPlaying
    fun pause()=pauseSession()
    private fun startSession() {
        if(playing){
            engine.update(world)
            onChanged?.invoke()
            return
        }
        val request=focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener)
            .setAcceptsDelayedFocusGain(false)
            .build().also{focusRequest=it}
        if(manager.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stopSelf()
            onChanged?.invoke()
            return
        }
        try {
            // Required before async AudioTrack setup; foreground service
            // is legitimately started by an explicit user Play action.
            playing=true
            shouldResume=false
            startForeground(NOTIFICATION_ID,notification())
            engine.start(world)
            onChanged?.invoke()
        } catch(e:Exception) {
            getSharedPreferences("atlas-audio-error",MODE_PRIVATE).edit()
                .putString("last_error",e.message?:"Playback failed").apply()
            pauseSession()
        }
    }
    private fun pauseSession(abandonFocus:Boolean=true) {
        engine.stop()
        playing=false
        if(abandonFocus){
            focusRequest?.let(manager::abandonAudioFocusRequest)
            shouldResume=false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        if(abandonFocus)stopSelf()
        onChanged?.invoke()
    }
    private fun notification():Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause=PendingIntent.getService(this,1,
            Intent(this,AtlasPlaybackService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.atlas_ic_play)
            .setContentTitle("Sound Atlas · "+world.name)
            .setContentText("Spatial world playing")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(R.drawable.atlas_ic_pause,"Pause",pause).build())
            .build()
    }
    override fun onDestroy() {
        if(listening){unregisterReceiver(noisy);listening=false}
        onChanged=null
        engine.close()
        focusRequest?.let(manager::abandonAudioFocusRequest)
        super.onDestroy()
    }
    companion object {
        const val ACTION_PLAY="com.night.soundatlas.action.PLAY"
        const val ACTION_PAUSE="com.night.soundatlas.action.PAUSE"
        private const val CHANNEL="atlas_soundscape"
        private const val NOTIFICATION_ID=5543
    }
}
