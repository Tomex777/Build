package com.night.sora.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.night.sora.MainActivity

/**
 * Hosts Sora Core's single music player in a Media3 session so playback can
 * continue outside the activity and Android can expose system media controls.
 */
class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val playback = MusicPlaybackRuntime.get(this)
        val sessionPlayer = object : ForwardingPlayer(playback.sessionPlayer()) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }

            override fun hasNextMediaItem(): Boolean = playback.queue.size > 1
            override fun hasPreviousMediaItem(): Boolean = playback.queue.size > 1
            override fun seekToNext() = playback.skipNext()
            override fun seekToNextMediaItem() = playback.skipNext()
            override fun seekToPrevious() = playback.skipPrevious()
            override fun seekToPreviousMediaItem() = playback.skipPrevious()
        }

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        fun ensureStarted(context: Context) {
            context.applicationContext.startService(
                Intent(context.applicationContext, MusicPlaybackService::class.java)
            )
        }
    }
}
