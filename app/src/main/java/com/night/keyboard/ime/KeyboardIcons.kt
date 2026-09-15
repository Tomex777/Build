package com.night.keyboard.ime

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Keyboard-owned icon family.
 *
 * Every icon uses the same 24x24 viewport, rounded caps/joins and 1.8-unit
 * optical stroke. This keeps the IME toolbar and utility keys visually
 * coherent instead of mixing Material icons with Unicode glyphs.
 */
object KeyboardIcons {
    val Clipboard: ImageVector = lineIcon("KeyboardClipboard") {
        moveTo(9f, 5f); lineTo(7f, 5f); cubicTo(5.9f, 5f, 5f, 5.9f, 5f, 7f); lineTo(5f, 19f)
        cubicTo(5f, 20.1f, 5.9f, 21f, 7f, 21f); lineTo(17f, 21f); cubicTo(18.1f, 21f, 19f, 20.1f, 19f, 19f); lineTo(19f, 7f)
        cubicTo(19f, 5.9f, 18.1f, 5f, 17f, 5f); lineTo(15f, 5f)
        moveTo(9f, 5f); cubicTo(9f, 3.9f, 9.9f, 3f, 11f, 3f); lineTo(13f, 3f); cubicTo(14.1f, 3f, 15f, 3.9f, 15f, 5f)
        lineTo(15f, 6.5f); lineTo(9f, 6.5f); close()
    }

    val Emoji: ImageVector = lineIcon("KeyboardEmoji") {
        moveTo(12f, 3.5f); cubicTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f); cubicTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
        cubicTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f); cubicTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f); close()
        moveTo(8.6f, 9.4f); lineTo(8.62f, 9.4f)
        moveTo(15.38f, 9.4f); lineTo(15.4f, 9.4f)
        moveTo(8.2f, 13.6f); cubicTo(9f, 15.2f, 10.25f, 16f, 12f, 16f); cubicTo(13.75f, 16f, 15f, 15.2f, 15.8f, 13.6f)
    }

    val Voice: ImageVector = lineIcon("KeyboardVoice") {
        moveTo(12f, 4f); cubicTo(10.35f, 4f, 9f, 5.35f, 9f, 7f); lineTo(9f, 12f); cubicTo(9f, 13.65f, 10.35f, 15f, 12f, 15f)
        cubicTo(13.65f, 15f, 15f, 13.65f, 15f, 12f); lineTo(15f, 7f); cubicTo(15f, 5.35f, 13.65f, 4f, 12f, 4f); close()
        moveTo(6.5f, 11.5f); lineTo(6.5f, 12.2f); cubicTo(6.5f, 15.25f, 8.95f, 17.7f, 12f, 17.7f); cubicTo(15.05f, 17.7f, 17.5f, 15.25f, 17.5f, 12.2f); lineTo(17.5f, 11.5f)
        moveTo(12f, 17.7f); lineTo(12f, 21f); moveTo(9.2f, 21f); lineTo(14.8f, 21f)
    }

    val Editor: ImageVector = lineIcon("KeyboardEditor") {
        moveTo(5f, 19f); lineTo(8.1f, 18.35f); lineTo(18.5f, 7.95f); cubicTo(19.2f, 7.25f, 19.2f, 6.15f, 18.5f, 5.45f); cubicTo(17.8f, 4.75f, 16.7f, 4.75f, 16f, 5.45f)
        lineTo(5.6f, 15.85f); close(); moveTo(14.7f, 6.75f); lineTo(17.25f, 9.3f)
        moveTo(7.2f, 7.1f); lineTo(7.2f, 4.6f); moveTo(5.95f, 5.85f); lineTo(8.45f, 5.85f)
        moveTo(19.3f, 14.4f); lineTo(19.3f, 17.6f); moveTo(17.7f, 16f); lineTo(20.9f, 16f)
    }

    val Tone: ImageVector = lineIcon("KeyboardTone") {
        moveTo(4f, 6f); lineTo(9f, 6f); moveTo(13f, 6f); lineTo(20f, 6f); moveTo(11f, 4f); lineTo(11f, 8f)
        moveTo(4f, 12f); lineTo(13f, 12f); moveTo(17f, 12f); lineTo(20f, 12f); moveTo(15f, 10f); lineTo(15f, 14f)
        moveTo(4f, 18f); lineTo(7f, 18f); moveTo(11f, 18f); lineTo(20f, 18f); moveTo(9f, 16f); lineTo(9f, 20f)
    }

    val Research: ImageVector = lineIcon("KeyboardResearch") {
        moveTo(10.5f, 4.5f); cubicTo(7.2f, 4.5f, 4.5f, 7.2f, 4.5f, 10.5f); cubicTo(4.5f, 13.8f, 7.2f, 16.5f, 10.5f, 16.5f)
        cubicTo(13.8f, 16.5f, 16.5f, 13.8f, 16.5f, 10.5f); cubicTo(16.5f, 7.2f, 13.8f, 4.5f, 10.5f, 4.5f); close()
        moveTo(15f, 15f); lineTo(20f, 20f)
        moveTo(19f, 4f); lineTo(19f, 7f); moveTo(17.5f, 5.5f); lineTo(20.5f, 5.5f)
    }

    val InputPicker: ImageVector = lineIcon("KeyboardInputPicker") {
        moveTo(4f, 6f); cubicTo(4f, 5.45f, 4.45f, 5f, 5f, 5f); lineTo(19f, 5f); cubicTo(19.55f, 5f, 20f, 5.45f, 20f, 6f); lineTo(20f, 16f)
        cubicTo(20f, 16.55f, 19.55f, 17f, 19f, 17f); lineTo(13.8f, 17f); moveTo(4f, 6f); lineTo(4f, 16f); cubicTo(4f, 16.55f, 4.45f, 17f, 5f, 17f); lineTo(10.2f, 17f)
        moveTo(7f, 8.5f); lineTo(8f, 8.5f); moveTo(10f, 8.5f); lineTo(11f, 8.5f); moveTo(13f, 8.5f); lineTo(14f, 8.5f); moveTo(16f, 8.5f); lineTo(17f, 8.5f)
        moveTo(7f, 12f); lineTo(8f, 12f); moveTo(10f, 12f); lineTo(11f, 12f); moveTo(13f, 12f); lineTo(17f, 12f)
        moveTo(12f, 15f); lineTo(12f, 21f); moveTo(9.7f, 18.7f); lineTo(12f, 21f); lineTo(14.3f, 18.7f)
    }

    val Shift: ImageVector = lineIcon("KeyboardShift") {
        moveTo(5f, 11f); lineTo(12f, 4f); lineTo(19f, 11f); lineTo(15.5f, 11f); lineTo(15.5f, 20f); lineTo(8.5f, 20f); lineTo(8.5f, 11f); close()
    }

    val Backspace: ImageVector = lineIcon("KeyboardBackspace") {
        moveTo(9f, 6f); lineTo(20f, 6f); cubicTo(20.55f, 6f, 21f, 6.45f, 21f, 7f); lineTo(21f, 17f); cubicTo(21f, 17.55f, 20.55f, 18f, 20f, 18f); lineTo(9f, 18f)
        lineTo(3f, 12f); close(); moveTo(12f, 9.5f); lineTo(17f, 14.5f); moveTo(17f, 9.5f); lineTo(12f, 14.5f)
    }

    val Enter: ImageVector = lineIcon("KeyboardEnter") {
        moveTo(19f, 5f); lineTo(19f, 11f); cubicTo(19f, 12.65f, 17.65f, 14f, 16f, 14f); lineTo(6f, 14f)
        moveTo(9f, 10.5f); lineTo(5.5f, 14f); lineTo(9f, 17.5f)
    }

    val Globe: ImageVector = lineIcon("KeyboardGlobe") {
        moveTo(12f, 3.5f); cubicTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f); cubicTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
        cubicTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f); cubicTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f); close()
        moveTo(3.9f, 9f); lineTo(20.1f, 9f); moveTo(3.9f, 15f); lineTo(20.1f, 15f)
        moveTo(12f, 3.5f); cubicTo(9.8f, 5.8f, 8.6f, 8.65f, 8.6f, 12f); cubicTo(8.6f, 15.35f, 9.8f, 18.2f, 12f, 20.5f)
        moveTo(12f, 3.5f); cubicTo(14.2f, 5.8f, 15.4f, 8.65f, 15.4f, 12f); cubicTo(15.4f, 15.35f, 14.2f, 18.2f, 12f, 20.5f)
    }

    private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()
}
