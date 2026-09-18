/*
 * Adapted for Sora from Aniyomi's player surface and mpv-android BaseMPVView.
 *
 * Aniyomi: https://github.com/aniyomiorg/aniyomi
 * mpv Android library: https://github.com/aniyomiorg/aniyomi-mpv-lib
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.night.sora.ui.player

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceHolder
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib
import java.io.File

/**
 * Sora adapter around Aniyomi's actual [BaseMPVView] lifecycle.
 *
 * Aniyomi owns libmpv initialization, surface attachment/detachment and
 * first-file startup. Sora only supplies resolved streams, headers and
 * progress/control calls.
 */
class AniyomiPlayerView(
    context: Context,
    attrs: AttributeSet,
) : BaseMPVView(context, attrs) {

    private var initialized = false
    private var surfaceReady = false

    fun initialize() {
        if (initialized) return

        val mpvDir = File(context.filesDir, "mpv").apply { mkdirs() }
        val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }

        /*
         * BaseMPVView intentionally sets force-window=no and idle=once after
         * mpv_init(), then owns the SurfaceHolder callbacks. Keep that exact
         * lifecycle instead of recreating it in Sora.
         */
        super.initialize(
            configDir = mpvDir.absolutePath,
            cacheDir = cacheDir.absolutePath,
            logLvl = "warn",
            vo = "gpu",
        )
        initialized = true
    }

    override fun initOptions(vo: String) {
        // Match the mobile defaults used by Aniyomi/mpv-android.
        setVo(vo)
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", "auto")
        MPVLib.setOptionString(
            "hwdec-codecs",
            "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1",
        )
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("input-default-bindings", "yes")
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("tls-verify", "yes")

        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", (cacheMegs * 1024 * 1024).toString())
        MPVLib.setOptionString("demuxer-max-back-bytes", (cacheMegs * 1024 * 1024).toString())

        // Workaround also used by current Aniyomi for mpv issue #14651.
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")
    }

    override fun postInitOptions() {
        // Sora persists progress itself.
        MPVLib.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("paused-for-cache", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("seeking", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("hwdec-current", MPVLib.mpvFormat.MPV_FORMAT_STRING)
    }

    /**
     * Load a Sora-resolved stream.
     *
     * Before surface creation we delegate to BaseMPVView.playFile(), which is
     * exactly how upstream queues its first file. Once the surface is attached
     * we may safely issue loadfile directly for source/quality changes.
     */
    fun play(
        url: String,
        headers: Map<String, String>,
        positionMs: Long = 0L,
    ) {
        if (!initialized) initialize()
        applyHeaders(headers)

        if (surfaceReady) {
            MPVLib.command(arrayOf("loadfile", url, "replace"))
        } else {
            playFile(url)
        }

        // Resume seek is intentionally applied by VideoPlayerScreen only after
        // duration becomes available. loadfile itself is asynchronous.
        @Suppress("UNUSED_VARIABLE")
        val deferredResumeMs = positionMs.coerceAtLeast(0L)
    }

    fun pause() {
        if (initialized) MPVLib.setPropertyBoolean("pause", true)
    }

    fun resume() {
        if (initialized) MPVLib.setPropertyBoolean("pause", false)
    }

    fun togglePause() {
        if (!initialized) return
        val paused = runCatching { MPVLib.getPropertyBoolean("pause") ?: false }
            .getOrDefault(false)
        MPVLib.setPropertyBoolean("pause", !paused)
    }

    fun seekTo(positionMs: Long) {
        if (!initialized) return
        MPVLib.setPropertyDouble("time-pos", positionMs.coerceAtLeast(0L) / 1000.0)
    }

    fun seekBy(deltaMs: Long) {
        seekTo((positionMs() + deltaMs).coerceAtLeast(0L))
    }

    fun setSpeed(speed: Float) {
        if (!initialized) return
        MPVLib.setPropertyDouble("speed", speed.coerceIn(0.25f, 4f).toDouble())
    }

    fun positionMs(): Long =
        if (!initialized) {
            0L
        } else {
            runCatching {
                ((MPVLib.getPropertyDouble("time-pos") ?: 0.0) * 1000.0).toLong()
            }.getOrDefault(0L)
        }

    fun durationMs(): Long =
        if (!initialized) {
            0L
        } else {
            runCatching {
                ((MPVLib.getPropertyDouble("duration") ?: 0.0) * 1000.0).toLong()
            }.getOrDefault(0L)
        }

    fun isPaused(): Boolean =
        if (!initialized) {
            true
        } else {
            runCatching { MPVLib.getPropertyBoolean("pause") ?: true }
                .getOrDefault(true)
        }

    fun isBuffering(): Boolean =
        if (!initialized) {
            false
        } else {
            runCatching { MPVLib.getPropertyBoolean("paused-for-cache") ?: false }
                .getOrDefault(false)
        }

    fun destroyPlayer() {
        if (!initialized) return
        super.destroy()
        surfaceReady = false
        initialized = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        /*
         * Base attaches the surface and consumes any playFile() queued before
         * attachment. Mark ready only after upstream has completed that work.
         */
        super.surfaceCreated(holder)
        surfaceReady = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        super.surfaceDestroyed(holder)
    }

    private fun applyHeaders(headers: Map<String, String>) {
        val headerValue = headers.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString(",") { "${it.key}: ${it.value}" }

        MPVLib.setPropertyString("http-header-fields", headerValue)
    }
}
