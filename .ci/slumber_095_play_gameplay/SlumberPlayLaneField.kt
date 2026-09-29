package com.night.pianohub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.night.pianohub.music.LessonMoment
import com.night.pianohub.music.PianoNote
import com.night.pianohub.music.PianoNotes
import kotlin.math.roundToInt

private val PlayPurple = Color(0xFF8E73FF)
private const val ApproachLeadMs = 3_300f

@Composable
fun SlumberPlayLaneField(
    moments: List<LessonMoment>,
    playheadMs: Long,
    tempoBpm: Int,
    viewport: Float,
    currentIndex: Int,
    expectedMidis: Set<Int>,
    pressedMidis: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val beatMs = 60_000.0 / tempoBpm
    val whiteNotes = remember { PianoNotes.full88.filterNot { it.isBlack } }
    val maxStart = (whiteNotes.size - PianoKeyboardConfig.VISIBLE_WHITE_KEYS).coerceAtLeast(0)
    val start = (viewport.coerceIn(0f, 1f) * maxStart).roundToInt().coerceIn(0, maxStart)
    val visibleWhites = whiteNotes.drop(start).take(PianoKeyboardConfig.VISIBLE_WHITE_KEYS)

    Canvas(modifier) {
        if (visibleWhites.isEmpty()) return@Canvas
        val whiteWidth = size.width / PianoKeyboardConfig.VISIBLE_WHITE_KEYS
        val targetY = size.height * .86f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF171326), Color(0xFF0D0E17), Color(0xFF07080D)),
                startY = 0f,
                endY = size.height,
            ),
            size = size,
        )
        repeat(PianoKeyboardConfig.VISIBLE_WHITE_KEYS) { index ->
            val midi = visibleWhites[index].midi
            val laneColor = when {
                midi in pressedMidis -> PlayPurple.copy(alpha = .19f)
                midi in expectedMidis -> PlayPurple.copy(alpha = .10f)
                index % 2 == 0 -> Color.White.copy(alpha = .025f)
                else -> Color.White.copy(alpha = .010f)
            }
            drawRect(laneColor, Offset(index * whiteWidth, 0f), Size(whiteWidth, targetY + 22f))
        }
        listOf(.17f, .34f, .51f, .68f).forEach { fraction ->
            val y = targetY * fraction
            drawLine(Color.White.copy(alpha = .045f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        repeat(PianoKeyboardConfig.VISIBLE_WHITE_KEYS + 1) { index ->
            val x = index * whiteWidth
            drawLine(Color.White.copy(alpha = .085f), Offset(x, 0f), Offset(x, targetY + 22f), 1f)
        }
        repeat(PianoKeyboardConfig.VISIBLE_WHITE_KEYS) { index ->
            val midi = visibleWhites[index].midi
            val active = midi in expectedMidis || midi in pressedMidis
            drawRoundRect(
                if (active) PlayPurple.copy(alpha = .24f) else Color.White.copy(alpha = .055f),
                Offset(index * whiteWidth + whiteWidth * .08f, targetY - 15f),
                Size(whiteWidth * .84f, 27f),
                CornerRadius(7f, 7f),
            )
        }
        drawRect(PlayPurple.copy(alpha = .16f), Offset(0f, targetY - 5f), Size(size.width, 12f))
        drawLine(Color(0xFFD8D0FF), Offset(0f, targetY), Offset(size.width, targetY), 3f)
        drawLine(PlayPurple.copy(alpha = .55f), Offset(0f, targetY + 8f), Offset(size.width, targetY + 8f), 2f)

        moments.forEachIndexed { index, moment ->
            val deltaMs = moment.beat * beatMs - playheadMs
            val bottomY = targetY - (deltaMs / ApproachLeadMs).toFloat() * targetY
            val durationMs = moment.durationBeats * beatMs
            val tileHeight = ((durationMs / ApproachLeadMs) * targetY).toFloat().coerceIn(20f, size.height * .30f)
            if (bottomY < -16f || bottomY - tileHeight > size.height) return@forEachIndexed

            val chordXs = moment.notes.mapNotNull { xForPlayMidi(it, visibleWhites, whiteWidth) }
            if (chordXs.size > 1) {
                val linkY = bottomY - (tileHeight * .34f).coerceAtMost(24f)
                drawLine(
                    PlayPurple.copy(alpha = if (index == currentIndex) .34f else .18f),
                    Offset(chordXs.min(), linkY),
                    Offset(chordXs.max(), linkY),
                    5f,
                )
            }
            moment.notes.forEach { midi ->
                val x = xForPlayMidi(midi, visibleWhites, whiteWidth) ?: return@forEach
                val isBlack = PianoNotes.full88.firstOrNull { it.midi == midi }?.isBlack == true
                val tileWidth = whiteWidth * if (isBlack) .60f else .84f
                val tileTop = bottomY - tileHeight
                val body = when {
                    index < currentIndex -> PlayPurple.copy(alpha = .20f)
                    index == currentIndex -> Color(0xFFC7BCFF)
                    else -> PlayPurple
                }
                drawRoundRect(
                    Color.Black.copy(alpha = if (index == currentIndex) .55f else .38f),
                    Offset(x - tileWidth / 2f + 4f, tileTop + 7f),
                    Size(tileWidth, tileHeight),
                    CornerRadius(9f, 9f),
                )
                drawRoundRect(body, Offset(x - tileWidth / 2f, tileTop), Size(tileWidth, tileHeight), CornerRadius(9f, 9f))
                drawRoundRect(
                    Color.White.copy(alpha = if (index == currentIndex) .40f else .16f),
                    Offset(x - tileWidth / 2f + 2f, tileTop + 2f),
                    Size((tileWidth - 4f).coerceAtLeast(1f), (tileHeight * .18f).coerceAtLeast(5f)),
                    CornerRadius(7f, 7f),
                )
                drawRoundRect(
                    Color(0xFF4E3BAF).copy(alpha = if (index < currentIndex) .15f else .80f),
                    Offset(x - tileWidth / 2f, bottomY - 8f),
                    Size(tileWidth, 8f),
                    CornerRadius(5f, 5f),
                )
                if (index == currentIndex) {
                    drawRoundRect(
                        Color.White.copy(alpha = .72f),
                        Offset(x - tileWidth / 2f, tileTop),
                        Size(tileWidth, tileHeight),
                        CornerRadius(9f, 9f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2f),
                    )
                }
            }
        }
    }
}

private fun xForPlayMidi(midi: Int, visibleWhites: List<PianoNote>, whiteWidth: Float): Float? {
    val whiteIndex = visibleWhites.indexOfFirst { it.midi == midi }
    if (whiteIndex >= 0) return (whiteIndex + .5f) * whiteWidth
    val previousWhiteIndex = visibleWhites.indexOfFirst { it.midi == midi - 1 }
    if (previousWhiteIndex >= 0 && previousWhiteIndex < visibleWhites.lastIndex) return (previousWhiteIndex + 1f) * whiteWidth
    return null
}
