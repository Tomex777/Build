/*
 * Adapted for Sora from Aniyomi's player surface and mpv-android BaseMPVView.
 *
 * Aniyomi: https://github.com/aniyomiorg/aniyomi
 * Copyright 2024 Abdallah Mehiz and Aniyomi contributors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.night.sora.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPVLib
import java.io.File

/**
 * Thin Sora adapter around Aniyomi's libmpv playback stack.
 *
 * Sora owns session/source/progress state. This view owns only the actual
 * Aniyomi-style mpv rendering/playback engine.
 */
class AniyomiPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var initialized = false
    private var surfaceReady = false
    private var pending: PendingPlayback? = null

    fun initialize() {
        if (initialized) return

        val mpvDir = File(context.filesDir, "mpv").apply { mkdirs() }
        val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }

        MPVLib.create(context, "warn")
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", mpvDir.absolutePath)
        MPVLib.setOptionString("gpu-shader-cache-dir", cacheDir.absolutePath)
        MPVLib.setOptionString("icc-cache-dir", cacheDir.absolutePath)

        // Mobile defaults intentionally mirror the current Aniyomi player.
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("hwdec", "auto")
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("input-default-bindings", "yes")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("demuxer-max-bytes", (64 * 1024 * 1024).toString())
        MPVLib.setOptionString("demuxer-max-back-bytes", (64 * 1024 * 1024).toString())
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

        MPVLib.init()
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "yes")
        holder.addCallback(this)
        initialized = true
    }

    fun play(
        url: String,
        headers: Map<String, String>,
        positionMs: Long = 0L,
    ) {
        if (!initialized) initialize()
        val request = PendingPlayback(url, headers, positionMs.coerceAtLeast(0L))
        pending = request
        if (surfaceReady) load(request)
    }

    fun pause() {
        if (initialized) MPVLib.setPropertyBoolean("pause", true)
    }

    fun resume() {
        if (initialized) MPVLib.setPropertyBoolean("pause", false)
    }

    fun togglePause() {
        val paused = runCatching { MPVLib.getPropertyBoolean("pause") ?: false }.getOrDefault(false)
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
        if (!initialized) 0L
        else runCatching { ((MPVLib.getPropertyDouble("time-pos") ?: 0.0) * 1000.0).toLong() }.getOrDefault(0L)

    fun durationMs(): Long =
        if (!initialized) 0L
        else runCatching { ((MPVLib.getPropertyDouble("duration") ?: 0.0) * 1000.0).toLong() }.getOrDefault(0L)

    fun isPaused(): Boolean =
        if (!initialized) true
        else runCatching { MPVLib.getPropertyBoolean("pause") ?: true }.getOrDefault(true)

    fun isBuffering(): Boolean =
        if (!initialized) false
        else runCatching { MPVLib.getPropertyBoolean("paused-for-cache") ?: false }.getOrDefault(false)

    fun destroyPlayer() {
        pending = null
        if (!initialized) return
        holder.removeCallback(this)
        runCatching { MPVLib.setPropertyBoolean("pause", true) }
        runCatching { MPVLib.detachSurface() }
        runCatching { MPVLib.destroy() }
        surfaceReady = false
        initialized = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!initialized) return
        surfaceReady = true
        MPVLib.attachSurface(holder.surface)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setPropertyString("vo", "gpu")
        pending?.let(::load)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!initialized) return
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!initialized) return
        surfaceReady = false
        runCatching { MPVLib.setPropertyString("vo", "null") }
        runCatching { MPVLib.setOptionString("force-window", "no") }
        runCatching { MPVLib.detachSurface() }
    }

    private fun load(request: PendingPlayback) {
        if (!surfaceReady || !initialized) return

        val headerValue = request.headers.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString(",") { "${it.key}: ${it.value}" }

        if (headerValue.isNotBlank()) {
            MPVLib.setPropertyString("http-header-fields", headerValue)
        } else {
            MPVLib.setPropertyString("http-header-fields", "")
        }

        MPVLib.command(arrayOf("loadfile", request.url, "replace"))
        if (request.positionMs > 0L) {
            MPVLib.setPropertyDouble("time-pos", request.positionMs / 1000.0)
        }
        MPVLib.setPropertyBoolean("pause", false)
        pending = null
    }

    private data class PendingPlayback(
        val url: String,
        val headers: Map<String, String>,
        val positionMs: Long,
    )
}
