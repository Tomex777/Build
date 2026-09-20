package com.night.keyboard.ime

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

enum class EmojiArtKind {
    SMILE, GRIN, HEART_EYES, SUNGLASSES, SAD, CRY, ANGRY, HEART, SPARKLE, SUN, FLOWER, ROCKET,
}

enum class EmojiCategory(val label: String) {
    SMILEYS("Smileys"),
    SYMBOLS("Symbols"),
    NATURE("Nature"),
    TRAVEL("Travel"),
}

data class EmojiArtEntry(
    val output: String,
    val art: EmojiArtKind,
    val description: String,
    val category: EmojiCategory,
)

val KeyboardEmojiSamples = listOf(
    EmojiArtEntry("🙂", EmojiArtKind.SMILE, "Slight smile", EmojiCategory.SMILEYS),
    EmojiArtEntry("😄", EmojiArtKind.GRIN, "Grinning face", EmojiCategory.SMILEYS),
    EmojiArtEntry("😍", EmojiArtKind.HEART_EYES, "Heart eyes", EmojiCategory.SMILEYS),
    EmojiArtEntry("😎", EmojiArtKind.SUNGLASSES, "Sunglasses", EmojiCategory.SMILEYS),
    EmojiArtEntry("😔", EmojiArtKind.SAD, "Sad face", EmojiCategory.SMILEYS),
    EmojiArtEntry("😢", EmojiArtKind.CRY, "Crying face", EmojiCategory.SMILEYS),
    EmojiArtEntry("😠", EmojiArtKind.ANGRY, "Angry face", EmojiCategory.SMILEYS),
    EmojiArtEntry("❤️", EmojiArtKind.HEART, "Heart love", EmojiCategory.SYMBOLS),
    EmojiArtEntry("✨", EmojiArtKind.SPARKLE, "Sparkles shine", EmojiCategory.SYMBOLS),
    EmojiArtEntry("☀️", EmojiArtKind.SUN, "Sun sunny", EmojiCategory.NATURE),
    EmojiArtEntry("🌸", EmojiArtKind.FLOWER, "Flower blossom", EmojiCategory.NATURE),
    EmojiArtEntry("🚀", EmojiArtKind.ROCKET, "Rocket space travel", EmojiCategory.TRAVEL),
)

@Composable
fun KeyboardEmojiArtwork(kind: EmojiArtKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (kind) {
            EmojiArtKind.SMILE -> drawFace(FaceMood.SMILE)
            EmojiArtKind.GRIN -> drawFace(FaceMood.GRIN)
            EmojiArtKind.HEART_EYES -> drawFace(FaceMood.HEART_EYES)
            EmojiArtKind.SUNGLASSES -> drawFace(FaceMood.SUNGLASSES)
            EmojiArtKind.SAD -> drawFace(FaceMood.SAD)
            EmojiArtKind.CRY -> drawFace(FaceMood.CRY)
            EmojiArtKind.ANGRY -> drawFace(FaceMood.ANGRY)
            EmojiArtKind.HEART -> drawHeartArtwork()
            EmojiArtKind.SPARKLE -> drawSparkleArtwork()
            EmojiArtKind.SUN -> drawSunArtwork()
            EmojiArtKind.FLOWER -> drawFlowerArtwork()
            EmojiArtKind.ROCKET -> drawRocketArtwork()
        }
    }
}

private enum class FaceMood { SMILE, GRIN, HEART_EYES, SUNGLASSES, SAD, CRY, ANGRY }

private val Face = Color(0xFFFFC94D)
private val FaceShade = Color(0xFFF5A623)
private val Ink = Color(0xFF202124)
private val Red = Color(0xFFE64B5D)
private val Blue = Color(0xFF5BA8FF)
private val White = Color(0xFFF8FAFC)

private fun DrawScope.drawFace(mood: FaceMood) {
    val d = min(size.width, size.height)
    val c = center
    val r = d * .42f
    drawCircle(Face, r, c)
    drawArc(
        FaceShade.copy(alpha = .25f),
        startAngle = 35f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(c.x - r * .83f, c.y - r * .83f),
        size = Size(r * 1.66f, r * 1.66f),
        style = Stroke(width = d * .055f),
    )
    when (mood) {
        FaceMood.HEART_EYES -> {
            drawMiniHeart(Offset(c.x - r * .38f, c.y - r * .18f), r * .2f)
            drawMiniHeart(Offset(c.x + r * .38f, c.y - r * .18f), r * .2f)
        }
        FaceMood.SUNGLASSES -> {
            val lensR = r * .24f
            drawRoundRect(Ink, topLeft = Offset(c.x - r * .72f, c.y - r * .37f), size = Size(lensR * 2.2f, lensR * 1.45f))
            drawRoundRect(Ink, topLeft = Offset(c.x + r * .18f, c.y - r * .37f), size = Size(lensR * 2.2f, lensR * 1.45f))
            drawLine(Ink, Offset(c.x - r * .08f, c.y - r * .2f), Offset(c.x + r * .12f, c.y - r * .2f), strokeWidth = d * .05f)
        }
        FaceMood.ANGRY -> {
            drawLine(Ink, Offset(c.x - r * .58f, c.y - r * .38f), Offset(c.x - r * .22f, c.y - r * .23f), strokeWidth = d * .055f)
            drawLine(Ink, Offset(c.x + r * .58f, c.y - r * .38f), Offset(c.x + r * .22f, c.y - r * .23f), strokeWidth = d * .055f)
            drawCircle(Ink, r * .075f, Offset(c.x - r * .35f, c.y - r * .08f))
            drawCircle(Ink, r * .075f, Offset(c.x + r * .35f, c.y - r * .08f))
        }
        else -> {
            drawCircle(Ink, r * .075f, Offset(c.x - r * .34f, c.y - r * .18f))
            drawCircle(Ink, r * .075f, Offset(c.x + r * .34f, c.y - r * .18f))
        }
    }

    when (mood) {
        FaceMood.SMILE, FaceMood.HEART_EYES, FaceMood.SUNGLASSES -> drawMouthArc(c, r, happy = true)
        FaceMood.GRIN -> {
            val mouth = Rect(c.x - r * .48f, c.y + r * .05f, c.x + r * .48f, c.y + r * .53f)
            drawArc(Ink, 0f, 180f, true, mouth.topLeft, mouth.size)
            drawRect(White, topLeft = Offset(mouth.left + r * .08f, mouth.top + r * .05f), size = Size(mouth.width - r * .16f, r * .13f))
        }
        FaceMood.SAD, FaceMood.CRY, FaceMood.ANGRY -> drawMouthArc(c, r, happy = false)
    }

    if (mood == FaceMood.CRY) {
        val tear = Path().apply {
            moveTo(c.x + r * .43f, c.y + r * .02f)
            cubicTo(c.x + r * .62f, c.y + r * .27f, c.x + r * .62f, c.y + r * .42f, c.x + r * .43f, c.y + r * .48f)
            cubicTo(c.x + r * .24f, c.y + r * .42f, c.x + r * .27f, c.y + r * .24f, c.x + r * .43f, c.y + r * .02f)
            close()
        }
        drawPath(tear, Blue)
    }
}

private fun DrawScope.drawMouthArc(c: Offset, r: Float, happy: Boolean) {
    val box = Size(r * .9f, r * .62f)
    val topLeft = Offset(c.x - box.width / 2f, if (happy) c.y + r * .02f else c.y + r * .27f)
    drawArc(
        color = Ink,
        startAngle = if (happy) 15f else 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = topLeft,
        size = box,
        style = Stroke(width = r * .1f),
    )
}

private fun DrawScope.drawMiniHeart(c: Offset, r: Float) {
    val p = heartPath(c, r)
    drawPath(p, Red)
}

private fun DrawScope.drawHeartArtwork() {
    val d = min(size.width, size.height)
    drawPath(heartPath(center, d * .39f), Red)
    drawPath(heartPath(Offset(center.x - d * .12f, center.y - d * .08f), d * .24f), Color(0xFFFF6B7D).copy(alpha = .7f))
}

private fun heartPath(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y + r * .82f)
    cubicTo(c.x - r * 1.1f, c.y + r * .1f, c.x - r * .86f, c.y - r * .75f, c.x - r * .35f, c.y - r * .75f)
    cubicTo(c.x - r * .08f, c.y - r * .75f, c.x, c.y - r * .5f, c.x, c.y - r * .35f)
    cubicTo(c.x, c.y - r * .5f, c.x + r * .08f, c.y - r * .75f, c.x + r * .35f, c.y - r * .75f)
    cubicTo(c.x + r * .86f, c.y - r * .75f, c.x + r * 1.1f, c.y + r * .1f, c.x, c.y + r * .82f)
    close()
}

private fun DrawScope.drawSparkleArtwork() {
    val d = min(size.width, size.height)
    fun star(c: Offset, r: Float, color: Color) {
        val p = Path().apply {
            moveTo(c.x, c.y - r); lineTo(c.x + r * .22f, c.y - r * .22f); lineTo(c.x + r, c.y)
            lineTo(c.x + r * .22f, c.y + r * .22f); lineTo(c.x, c.y + r); lineTo(c.x - r * .22f, c.y + r * .22f)
            lineTo(c.x - r, c.y); lineTo(c.x - r * .22f, c.y - r * .22f); close()
        }
        drawPath(p, color)
    }
    star(Offset(center.x - d * .1f, center.y), d * .3f, Color(0xFFFFD65C))
    star(Offset(center.x + d * .25f, center.y - d * .25f), d * .15f, Color(0xFFFFEEA8))
}

private fun DrawScope.drawSunArtwork() {
    val d = min(size.width, size.height)
    val r = d * .25f
    drawCircle(Color(0xFFFFC338), r, center)
    repeat(8) { index ->
        rotate(index * 45f, center) {
            drawLine(Color(0xFFFFC338), Offset(center.x, center.y - d * .43f), Offset(center.x, center.y - d * .34f), strokeWidth = d * .06f)
        }
    }
}

private fun DrawScope.drawFlowerArtwork() {
    val d = min(size.width, size.height)
    val petal = Color(0xFFFF8CB8)
    repeat(5) { index ->
        val angle = Math.toRadians((index * 72.0) - 90.0)
        val p = Offset(center.x + kotlin.math.cos(angle).toFloat() * d * .21f, center.y + kotlin.math.sin(angle).toFloat() * d * .21f)
        drawCircle(petal, d * .18f, p)
    }
    drawCircle(Color(0xFFFFD45C), d * .15f, center)
}

private fun DrawScope.drawRocketArtwork() {
    val d = min(size.width, size.height)
    val body = Path().apply {
        moveTo(center.x, center.y - d * .4f)
        cubicTo(center.x + d * .24f, center.y - d * .18f, center.x + d * .25f, center.y + d * .18f, center.x, center.y + d * .28f)
        cubicTo(center.x - d * .25f, center.y + d * .18f, center.x - d * .24f, center.y - d * .18f, center.x, center.y - d * .4f)
        close()
    }
    drawPath(body, Color(0xFFE9EEF5))
    drawCircle(Blue, d * .09f, Offset(center.x, center.y - d * .08f))
    val leftFin = Path().apply { moveTo(center.x - d * .16f, center.y + d * .08f); lineTo(center.x - d * .34f, center.y + d * .29f); lineTo(center.x - d * .12f, center.y + d * .22f); close() }
    val rightFin = Path().apply { moveTo(center.x + d * .16f, center.y + d * .08f); lineTo(center.x + d * .34f, center.y + d * .29f); lineTo(center.x + d * .12f, center.y + d * .22f); close() }
    drawPath(leftFin, Red); drawPath(rightFin, Red)
    val flame = Path().apply { moveTo(center.x - d * .08f, center.y + d * .26f); lineTo(center.x, center.y + d * .46f); lineTo(center.x + d * .08f, center.y + d * .26f); close() }
    drawPath(flame, Color(0xFFFFA62B))
}
