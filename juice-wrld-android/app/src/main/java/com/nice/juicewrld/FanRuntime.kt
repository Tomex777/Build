package com.nice.juicewrld

import android.app.Application

/**
 * One playback owner per application process. The Activity can be recreated
 * while the foreground service preserves the same native MediaPlayer.
 * Instrumentation tests can instantiate their own FanPlayer in isolation.
 */
internal object FanRuntime {
    @Volatile private var shared: FanPlayer? = null

    @Synchronized fun player(app: Application): FanPlayer =
        shared ?: FanPlayer(app).also { shared = it }

    fun isShared(candidate: FanPlayer): Boolean = shared === candidate
}
