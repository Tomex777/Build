/*
 * Adapted for Sora from Aniyomi's GestureHandler.
 *
 * Aniyomi: https://github.com/aniyomiorg/aniyomi
 * Copyright 2024 Abdallah Mehiz and Aniyomi contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.night.sora.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sora-sized port of Aniyomi's core player gestures:
 * - tap controls
 * - double-tap seek
 * - horizontal drag seek
 * - vertical brightness / volume gestures
 * - long-press temporary 2x speed
 */
@Composable
fun AniyomiPlayerGestureLayer(
    player: AniyomiPlayerView?,
    durationMs: Long,
    positionMs: Long,
    locked: Boolean,
    playbackSpeed: Float,
    onToggleControls: () -> Unit,
    onSeekPreview: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityForPlayer() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier
            .fillMaxSize()
            .safeGesturesPadding()
            .pointerInput(player, locked) {
                detectTapGestures(
                    onTap = {
                        onToggleControls()
                    },
                    onDoubleTap = { offset ->
                        if (locked) return@detectTapGestures
                        when {
                            offset.x < size.width * 2f / 5f -> player?.seekBy(-10_000L)
                            offset.x > size.width * 3f / 5f -> player?.seekBy(10_000L)
                            else -> player?.togglePause()
                        }
                    },
                    onLongPress = {
                        if (locked) return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        player?.setSpeed(2f)
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (!locked) player?.setSpeed(playbackSpeed)
                    },
                )
            }
            .pointerInput(player, locked, durationMs, positionMs) {
                if (locked || durationMs <= 0L) return@pointerInput
                var startPosition = positionMs
                var accumulated = 0f

                detectHorizontalDragGestures(
                    onDragStart = {
                        startPosition = player?.positionMs() ?: positionMs
                        accumulated = 0f
                    },
                    onDragCancel = { onSeekPreview(null) },
                    onDragEnd = {
                        val target = (startPosition + horizontalSeekDelta(accumulated, size.width, durationMs))
                            .coerceIn(0L, durationMs)
                        player?.seekTo(target)
                        onSeekPreview(null)
                    },
                ) { change, amount ->
                    change.consume()
                    accumulated += amount
                    val target = (startPosition + horizontalSeekDelta(accumulated, size.width, durationMs))
                        .coerceIn(0L, durationMs)
                    onSeekPreview(target)
                }
            }
            .pointerInput(player, locked, activity) {
                if (locked) return@pointerInput
                var accumulated = 0f
                var startBrightness = activity?.window?.attributes?.screenBrightness
                    ?.takeIf { it >= 0f } ?: 0.5f
                var startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                var brightnessGesture = false

                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        accumulated = 0f
                        startBrightness = activity?.window?.attributes?.screenBrightness
                            ?.takeIf { it >= 0f } ?: 0.5f
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        brightnessGesture = offset.x > size.width / 2f
                    },
                ) { change, amount ->
                    change.consume()
                    accumulated += amount
                    val fraction = (-accumulated / size.height).coerceIn(-1f, 1f)

                    if (brightnessGesture) {
                        activity?.window?.let { window ->
                            val attrs = window.attributes
                            attrs.screenBrightness = (startBrightness + fraction).coerceIn(0.02f, 1f)
                            window.attributes = attrs
                        }
                    } else {
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val next = (startVolume + fraction * max).roundToInt().coerceIn(0, max)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                    }
                }
            },
    )
}


private fun horizontalSeekDelta(dragPx: Float, widthPx: Int, durationMs: Long): Long {
    if (widthPx <= 0) return 0L
    val normalized = (dragPx / widthPx.toFloat()).coerceIn(-1f, 1f)
    // Match Aniyomi's intent: precise scrub for short content, larger travel for long episodes.
    val maxTravel = (durationMs.toDouble() * 0.35).coerceAtMost(10 * 60_000.0)
    return (normalized.toDouble() * maxTravel).toLong()
}

private tailrec fun Context.findActivityForPlayer(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivityForPlayer()
    else -> null
}
