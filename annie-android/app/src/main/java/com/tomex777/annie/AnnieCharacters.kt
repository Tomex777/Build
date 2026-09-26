package com.tomex777.annie

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.random.Random

internal data class AnnieCharacter(
    val id: String,
    val name: String,
    val tone: String,
    val greeting: String,
    val accent: Color,
    val accentDeep: Color,
    val avatarStyle: Int,
)

internal object AnnieCharacters {
    val all: List<AnnieCharacter> = listOf(
        AnnieCharacter("nova", "Nova", "bright, curious, concise", "Hey — Nova here. What are we making or finding?", Color(0xFF75C8FF), Color(0xFF244B86), 0),
        AnnieCharacter("aster", "Aster", "calm, observant, dry-witted", "Aster here. Give me a command or tell me what you want.", Color(0xFFB59CFF), Color(0xFF4B367F), 1),
        AnnieCharacter("kiko", "Kiko", "playful, quick, practical", "Yo, Kiko here. What are we doing?", Color(0xFFFF9FC8), Color(0xFF7A365D), 2),
        AnnieCharacter("ren", "Ren", "focused, understated, precise", "Ren online. What should we run?", Color(0xFF81E6C1), Color(0xFF245E55), 3),
        AnnieCharacter("sol", "Sol", "warm, energetic, direct", "Sol here. Pick a command or throw me a task.", Color(0xFFFFC36B), Color(0xFF7A4F1E), 4),
        AnnieCharacter("tavi", "Tavi", "inventive, lightly chaotic, helpful", "Tavi reporting in. What are we trying?", Color(0xFF7EE7F2), Color(0xFF245B68), 5),
        AnnieCharacter("yuna", "Yuna", "gentle, clear, organized", "Hi, Yuna here. What do you want to open or run?", Color(0xFFFFB6A3), Color(0xFF75453A), 6),
        AnnieCharacter("niko", "Niko", "casual, confident, efficient", "Niko here. Send it.", Color(0xFF8BB8FF), Color(0xFF314C82), 7),
        AnnieCharacter("rumi", "Rumi", "thoughtful, creative, compact", "Rumi here. What are we exploring?", Color(0xFFE3A6FF), Color(0xFF66407B), 8),
        AnnieCharacter("lio", "Lio", "upbeat, practical, friendly", "Lio here. What’s first?", Color(0xFF8DE5A1), Color(0xFF315F3B), 9),
        AnnieCharacter("vela", "Vela", "cool, analytical, composed", "Vela online. What should I run?", Color(0xFF82D8FF), Color(0xFF28536F), 10),
        AnnieCharacter("kiri", "Kiri", "curious, snappy, expressive", "Kiri here. Give me something interesting.", Color(0xFFFF96A8), Color(0xFF793542), 11),
        AnnieCharacter("emi", "Emi", "soft-spoken, attentive, efficient", "Emi here. What do you need?", Color(0xFFFFC5DF), Color(0xFF74465D), 12),
        AnnieCharacter("rowan", "Rowan", "steady, grounded, concise", "Rowan here. Ready when you are.", Color(0xFFA7D18B), Color(0xFF465E35), 13),
        AnnieCharacter("ciel", "Ciel", "airy, clever, lightly teasing", "Ciel here. What are we getting into?", Color(0xFFB0D8FF), Color(0xFF3A587A), 14),
        AnnieCharacter("zuri", "Zuri", "bold, lively, decisive", "Zuri here. Hit me with it.", Color(0xFFFFA56B), Color(0xFF754225), 15),
        AnnieCharacter("theo", "Theo", "methodical, relaxed, useful", "Theo here. What’s the move?", Color(0xFF9FC1D9), Color(0xFF3E5666), 16),
        AnnieCharacter("iris", "Iris", "imaginative, sharp, warm", "Iris here. What should we make happen?", Color(0xFFC8A7FF), Color(0xFF543D76), 17),
        AnnieCharacter("pax", "Pax", "minimal, calm, dependable", "Pax online. What do you want to do?", Color(0xFF8DDDD4), Color(0xFF315D59), 18),
        AnnieCharacter("mae", "Mae", "cheerful, neat, fast", "Mae here. What are we opening?", Color(0xFFFFB88A), Color(0xFF74472E), 19),
        AnnieCharacter("orin", "Orin", "curious, technical, dry", "Orin here. Command?", Color(0xFF94AFFF), Color(0xFF3E4D7B), 20),
        AnnieCharacter("lumi", "Lumi", "bright, gentle, playful", "Lumi here ✦ What are we doing?", Color(0xFFF6D37A), Color(0xFF705A25), 21),
        AnnieCharacter("eden", "Eden", "balanced, thoughtful, practical", "Eden here. What do you want to try?", Color(0xFF91D7B6), Color(0xFF365B4A), 22),
        AnnieCharacter("mavi", "Mavi", "quick, witty, composed", "Mavi here. Send the command.", Color(0xFF71B7E8), Color(0xFF2D536C), 23),
    )

    val default: AnnieCharacter get() = all.first()

    fun byId(id: String?): AnnieCharacter = all.firstOrNull { it.id == id } ?: default

    fun stableIdForExistingChat(chatId: String): String {
        val index = chatId.hashCode().absoluteValue % all.size
        return all[index].id
    }

    fun randomId(exclude: String? = null): String {
        val candidates = if (exclude == null || all.size <= 1) all else all.filterNot { it.id == exclude }
        return candidates[Random.nextInt(candidates.size)].id
    }
}

@Composable
internal fun AnnieCharacterAvatar(
    character: AnnieCharacter,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
) {
    val style = character.avatarStyle
    val skinTones = listOf(
        Color(0xFFF4C7A1), Color(0xFFDFA77D), Color(0xFFB97855), Color(0xFF8B583E), Color(0xFFF0B98B),
    )
    val hairTones = listOf(
        Color(0xFF17202C), Color(0xFF4A2C2A), Color(0xFF6A4B2F), Color(0xFF243850), Color(0xFF5B355E), Color(0xFF2B4A43),
    )
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(character.accent, character.accentDeep)))
            .semantics { contentDescription = "${character.name} avatar" },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val face = skinTones[style % skinTones.size]
            val hair = hairTones[(style / 2) % hairTones.size]
            val cx = this.size.width * 0.5f
            val cy = this.size.height * 0.53f
            val r = this.size.minDimension * 0.27f

            drawCircle(Color.White.copy(alpha = 0.10f), radius = this.size.minDimension * 0.44f, center = Offset(cx, cy))
            drawCircle(face, radius = r, center = Offset(cx, cy + r * 0.08f))

            when (style % 6) {
                0 -> drawArc(hair, 185f, 170f, true, Offset(cx - r * 1.05f, cy - r * 1.12f), Size(r * 2.1f, r * 1.55f))
                1 -> {
                    drawCircle(hair, r * 1.02f, Offset(cx, cy - r * 0.18f))
                    drawRect(face, Offset(cx - r, cy - r * 0.10f), Size(r * 2f, r * 1.18f))
                }
                2 -> {
                    val p = Path().apply {
                        moveTo(cx - r * 1.05f, cy)
                        lineTo(cx - r * 0.72f, cy - r * 1.35f)
                        lineTo(cx - r * 0.15f, cy - r * 0.72f)
                        lineTo(cx + r * 0.25f, cy - r * 1.28f)
                        lineTo(cx + r * 1.05f, cy - r * 0.15f)
                        close()
                    }
                    drawPath(p, hair)
                }
                3 -> {
                    drawArc(hair, 180f, 180f, true, Offset(cx - r * 1.10f, cy - r * 1.18f), Size(r * 2.2f, r * 1.7f))
                    drawCircle(hair, r * 0.42f, Offset(cx + r * 0.95f, cy - r * 0.72f))
                }
                4 -> {
                    drawArc(hair, 185f, 170f, true, Offset(cx - r * 1.12f, cy - r * 1.15f), Size(r * 2.24f, r * 1.72f))
                    drawRect(hair, Offset(cx - r * 1.08f, cy - r * 0.25f), Size(r * 0.32f, r * 1.18f))
                    drawRect(hair, Offset(cx + r * 0.76f, cy - r * 0.25f), Size(r * 0.32f, r * 1.18f))
                }
                else -> {
                    drawCircle(hair, r * 0.58f, Offset(cx - r * 0.55f, cy - r * 0.72f))
                    drawCircle(hair, r * 0.58f, Offset(cx + r * 0.55f, cy - r * 0.72f))
                    drawArc(hair, 185f, 170f, true, Offset(cx - r, cy - r * 1.0f), Size(r * 2f, r * 1.45f))
                }
            }

            val eyeY = cy + r * 0.10f
            val eyeDx = r * 0.37f
            if (style % 4 == 0) {
                drawLine(character.accentDeep, Offset(cx - eyeDx - r * 0.12f, eyeY), Offset(cx - eyeDx + r * 0.12f, eyeY), strokeWidth = r * 0.07f)
                drawCircle(character.accentDeep, r * 0.075f, Offset(cx + eyeDx, eyeY))
            } else {
                drawCircle(character.accentDeep, r * 0.075f, Offset(cx - eyeDx, eyeY))
                drawCircle(character.accentDeep, r * 0.075f, Offset(cx + eyeDx, eyeY))
            }
            drawArc(character.accentDeep, 15f, 150f, false, Offset(cx - r * 0.24f, cy + r * 0.20f), Size(r * 0.48f, r * 0.28f), style = Stroke(r * 0.055f))

            when ((style / 3) % 5) {
                0 -> {
                    drawCircle(character.accentDeep, r * 0.30f, Offset(cx - eyeDx, eyeY), style = Stroke(r * 0.055f))
                    drawCircle(character.accentDeep, r * 0.30f, Offset(cx + eyeDx, eyeY), style = Stroke(r * 0.055f))
                    drawLine(character.accentDeep, Offset(cx - r * 0.07f, eyeY), Offset(cx + r * 0.07f, eyeY), r * 0.05f)
                }
                1 -> drawCircle(character.accent, r * 0.13f, Offset(cx + r * 0.72f, cy - r * 0.55f))
                2 -> drawArc(character.accentDeep, 200f, 140f, false, Offset(cx - r * 1.18f, cy - r * 0.88f), Size(r * 2.36f, r * 1.9f), style = Stroke(r * 0.12f))
                3 -> {
                    drawCircle(character.accentDeep, r * 0.09f, Offset(cx - r * 0.88f, cy + r * 0.18f))
                    drawCircle(character.accentDeep, r * 0.09f, Offset(cx + r * 0.88f, cy + r * 0.18f))
                }
                else -> {
                    val pin = Path().apply {
                        moveTo(cx + r * 0.58f, cy - r * 0.82f)
                        lineTo(cx + r * 0.80f, cy - r * 0.52f)
                        lineTo(cx + r * 0.46f, cy - r * 0.60f)
                        close()
                    }
                    drawPath(pin, character.accent)
                }
            }
        }
    }
}
