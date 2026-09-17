package com.night.sora.playback

import android.content.Context
import com.night.sora.extension.ExtensionManager

/**
 * Process-wide owner for Sora's music playback coordinator.
 *
 * The activity and MediaSessionService intentionally share the same controller so
 * there is only one queue, one resolver state, and one ExoPlayer in Sora Core.
 */
object MusicPlaybackRuntime {
    @Volatile
    private var instance: MusicPlaybackController? = null

    fun get(context: Context, manager: ExtensionManager? = null): MusicPlaybackController {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: MusicPlaybackController(
                context = context.applicationContext,
                manager = manager ?: ExtensionManager(context.applicationContext),
            ).also { instance = it }
        }
    }
}
