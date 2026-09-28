#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/slumber-087")
path = root / "app/src/main/java/com/night/pianohub/ui/components/PianoKeyboard.kt"
text = path.read_text()

start_marker = "private fun DrawScope.drawKeyboard("
end_marker = "@Composable\nfun PianoNavigator("
start = text.index(start_marker)
end = text.index(end_marker, start)

replacement = r'''private fun DrawScope.drawKeyboard(
    geometry: KeyboardGeometry,
    tradition: MusicTradition,
    labelMode: KeyLabelMode,
    tonicMidi: Int? = null,
    pressedMidis: Set<Int>,
    pressProgress: Map<Int, Float>,
) {
    val violet = Color(0xFF8F72FF)
    val violetLight = Color(0xFFE8E0FF)
    val whiteTop = Color(0xFFFFFFFF)
    val whiteBottom = Color(0xFFE7E8EE)
    val whiteEdge = Color(0xFFB8BBC7)
    val blackTop = Color(0xFF202433)
    val blackBottom = Color(0xFF05060A)
    val blackEdge = Color(0xFF3A4054)
    val keyShadow = Color(0xFF000000).copy(alpha = .58f)

    // Historical 0.7.0/0.8.6 keybed lip. This is part of the proven tactile
    // renderer and keeps the whole keyboard visually raised from the surface.
    drawRect(
        color = Color(0xFF060810),
        topLeft = Offset(0f, size.height * .965f),
        size = Size(size.width, size.height * .035f),
    )

    geometry.whites.forEach { key ->
        val pressed = key.note.midi in pressedMidis
        // Keep the later 0.8.7 animated key travel, but render the actual face
        // with the historical full-height 3D key geometry at rest.
        val pressY = 5.5f * (pressProgress[key.note.midi] ?: 0f)
        val radius = CornerRadius(7f, 7f)
        drawRoundRect(
            color = keyShadow,
            topLeft = key.rect.topLeft + Offset(0f, 7f + pressY),
            size = key.rect.size,
            cornerRadius = radius,
        )
        if (pressed) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White, violetLight, Color(0xFFB6A2FF)),
                    startY = key.rect.top + pressY,
                    endY = key.rect.bottom + pressY,
                ),
                topLeft = key.rect.topLeft + Offset(0f, pressY),
                size = key.rect.size,
                cornerRadius = radius,
            )
            drawRoundRect(
                color = violet.copy(alpha = .92f),
                topLeft = key.rect.topLeft + Offset(1.5f, 1.5f + pressY),
                size = Size(key.rect.width - 3f, key.rect.height - 3f),
                cornerRadius = radius,
                style = Stroke(width = 3.5f),
            )
            drawRoundRect(
                color = violet.copy(alpha = .18f),
                topLeft = Offset(key.rect.left - 5f, key.rect.top + pressY),
                size = Size(key.rect.width + 10f, key.rect.height),
                cornerRadius = radius,
            )
        } else {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(whiteTop, Color(0xFFF9F9FB), whiteBottom),
                    startY = key.rect.top + pressY,
                    endY = key.rect.bottom + pressY,
                ),
                topLeft = key.rect.topLeft + Offset(0f, pressY),
                size = key.rect.size,
                cornerRadius = radius,
            )
            drawRoundRect(
                color = whiteEdge,
                topLeft = key.rect.topLeft + Offset(0f, pressY),
                size = key.rect.size,
                cornerRadius = radius,
                style = Stroke(width = 1.2f),
            )
        }
        drawLine(
            color = Color.White.copy(alpha = .72f),
            start = Offset(key.rect.left + 3f, key.rect.top + pressY + 2f),
            end = Offset(key.rect.right - 3f, key.rect.top + pressY + 2f),
            strokeWidth = 1.4f,
        )

        // Preserve the newer tonic-aware labeling behavior.
        val label = PianoNotes.labelFor(key.note, tradition, labelMode, tonicMidi)
        if (label.isNotBlank()) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(77, 82, 99)
                    textSize = (key.rect.width * .28f).coerceIn(17f, 26f)
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawText(label, key.rect.center.x, key.rect.bottom - 18f + pressY, paint)
            }
        }
    }

    geometry.blacks.forEach { key ->
        val pressed = key.note.midi in pressedMidis
        val pressY = 5.5f * (pressProgress[key.note.midi] ?: 0f)
        val radius = CornerRadius(6f, 6f)
        drawRoundRect(
            color = keyShadow,
            topLeft = key.rect.topLeft + Offset(0f, 7f + pressY),
            size = key.rect.size,
            cornerRadius = radius,
        )
        drawRoundRect(
            brush = if (pressed) {
                Brush.verticalGradient(
                    listOf(Color(0xFFB9A8FF), Color(0xFF7255E8), Color(0xFF231A4D)),
                    startY = key.rect.top + pressY,
                    endY = key.rect.bottom + pressY,
                )
            } else {
                Brush.verticalGradient(
                    listOf(blackTop, Color(0xFF11131B), blackBottom),
                    startY = key.rect.top + pressY,
                    endY = key.rect.bottom + pressY,
                )
            },
            topLeft = key.rect.topLeft + Offset(0f, pressY),
            size = key.rect.size,
            cornerRadius = radius,
        )
        drawRoundRect(
            color = if (pressed) violetLight.copy(alpha = .78f) else blackEdge,
            topLeft = key.rect.topLeft + Offset(0f, pressY),
            size = key.rect.size,
            cornerRadius = radius,
            style = Stroke(width = if (pressed) 2.4f else 1.2f),
        )
        drawLine(
            color = Color.White.copy(alpha = if (pressed) .36f else .16f),
            start = Offset(key.rect.left + 4f, key.rect.top + pressY + 3f),
            end = Offset(key.rect.right - 4f, key.rect.top + pressY + 3f),
            strokeWidth = 1.3f,
        )

        // Black-key labels were added later; keep them while restoring depth.
        val label = PianoNotes.labelFor(key.note, tradition, labelMode, tonicMidi)
        if (label.isNotBlank()) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(231, 232, 239)
                    textSize = (key.rect.width * .25f).coerceIn(12f, 19f)
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawText(label, key.rect.center.x, key.rect.bottom - 12f + pressY, paint)
            }
        }
    }
}

'''

updated = text[:start] + replacement + text[end:]
path.write_text(updated)

# Guard the surgical nature of this restoration. The historical face/shadow
# geometry must be present while all later interaction features remain intact.
checks = {
    "animated key travel": "val pressY = 5.5f * (pressProgress[key.note.midi] ?: 0f)",
    "historical white shadow": "Offset(0f, 7f + pressY)",
    "historical white gradient": "listOf(whiteTop, Color(0xFFF9F9FB), whiteBottom)",
    "historical black gradient": "listOf(blackTop, Color(0xFF11131B), blackBottom)",
    "tonic-aware labels": "PianoNotes.labelFor(key.note, tradition, labelMode, tonicMidi)",
    "multitouch pointer ownership": "val active = mutableMapOf<androidx.compose.ui.input.pointer.PointerId, PianoNote>()",
    "glissando": "Moving one finger creates a",
}
for name, needle in checks.items():
    if needle not in updated:
        raise SystemExit(f"restoration lost required feature: {name}")

for forbidden in (
    "listOf(Color(0xFFDDE0E7), Color(0xFFAEB3C0))",
    "val topSize = Size(key.rect.width, (key.rect.height - 9f).coerceAtLeast(1f))",
):
    if forbidden in updated:
        raise SystemExit(f"0.8.7 replacement geometry still present: {forbidden}")

print(f"Restored historical 3D keyboard renderer in {path}")
