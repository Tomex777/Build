#!/usr/bin/env python3
"""ABR: replace generic Material slider with functional proprietary visual control."""
from pathlib import Path
import re

src = Path("EchoSpace/app/src/main/java/com/nexa/echospace/MainActivity.kt")
s = src.read_text()

def replace_once(old, new, description):
    global s
    count = s.count(old)
    if count != 1:
        raise RuntimeError(f"{description}: expected 1 occurrence, found {count}")
    s = s.replace(old, new, 1)

replace_once(
    "import kotlin.math.sin\n",
    "import kotlin.math.sin\n"
    "import androidx.compose.foundation.gestures.awaitEachGesture\n"
    "import androidx.compose.foundation.gestures.awaitFirstDown\n",
    "gesture imports"
)

replace_once(
    """                Slider(
                    value = sound.gain.coerceIn(0f, 1f),
                    onValueChange = { level -> onGain(sound.id, level) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .semantics { contentDescription = sound.kind.title + " level" },
                    colors = SliderDefaults.colors(
                        thumbColor = tint,
                        activeTrackColor = tint,
                        inactiveTrackColor = Color(0xFF3E5161)
                    )
                )""",
    """                EchoLayerGain(
                    value = sound.gain,
                    onValueChange = { level -> onGain(sound.id, level) },
                    tint = tint,
                    label = sound.kind.title + " level",
                    modifier = Modifier.fillMaxWidth()
                )""",
    "delete default Material slider and use bespoke control"
)

# Remove unused legacy Canvas slider: do not leave a second implementation
# that could accidentally be reintroduced by a later edit.
legacy = re.search(
    r"@Composable\s*\nprivate fun ThickGainControl\(.*?(?=\n@Composable\n)",
    s, flags=re.S
)
if legacy is None:
    raise RuntimeError("Legacy ThickGainControl not found")
s = s[:legacy.start()] + s[legacy.end():]

custom = """@Composable
private fun EchoLayerGain(
    value: Float,
    onValueChange: (Float) -> Unit,
    tint: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    val currentUpdate by rememberUpdatedState(onValueChange)
    val amount = value.coerceIn(0f, 1f)
    Canvas(
        modifier.height(70.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(amount, 0f..1f)
                setProgress {
                    currentUpdate(it.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    // Whole 70dp control is interactive; parent scroll won't steal drags.
                    val minX = 24.dp.toPx()
                    val range = (size.width.toFloat() - 2f * minX).coerceAtLeast(1f)
                    fun updateAt(x: Float) {
                        currentUpdate(((x - minX) / range).coerceIn(0f, 1f))
                    }
                    updateAt(first.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val point = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!point.pressed) break
                        updateAt(point.position.x)
                        point.consume()
                    }
                }
            }
    ) {
        val inset = 24.dp.toPx()
        val left = inset
        val width = (size.width - 2f * inset).coerceAtLeast(1f)
        val midY = size.height * 0.43f
        val height = 11.dp.toPx()
        val filled = width * amount
        drawRoundRect(
            color = Color(0xFF243947),
            topLeft = Offset(left, midY - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2f)
        )
        if (filled > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(tint.copy(alpha = 0.65f), tint)
                ),
                topLeft = Offset(left, midY - height / 2f),
                size = Size(filled, height),
                cornerRadius = CornerRadius(height / 2f)
            )
        }
        // Fine engraved dB-style guide ticks, rather than a system control.
        for (index in 0..20) {
            val position = left + width * index / 20f
            val large = index % 5 == 0
            drawRoundRect(
                color = if (index / 20f <= amount)
                    tint.copy(alpha = 0.58f) else Color(0xFF667C8A),
                topLeft = Offset(position - 0.75.dp.toPx(), midY + 15.dp.toPx()),
                size = Size(1.5.dp.toPx(), if (large) 9.dp.toPx() else 5.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        val thumb = Offset(left + filled, midY)
        drawCircle(tint.copy(alpha = 0.14f), 24.dp.toPx(), thumb)
        drawRoundRect(
            color = Color(0xFFF5FAFC),
            topLeft = Offset(thumb.x - 8.dp.toPx(), thumb.y - 17.dp.toPx()),
            size = Size(16.dp.toPx(), 34.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(thumb.x - 1.5.dp.toPx(), thumb.y - 7.dp.toPx()),
            size = Size(3.dp.toPx(), 14.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx())
        )
    }
}

"""

replace_once(
    "@Composable\nprivate fun BrandMark(",
    custom + "@Composable\nprivate fun BrandMark(",
    "insert custom gain implementation"
)

# ABR regression guard: no default sliders anywhere in the produced native UI.
assert "Slider(" not in s, "Material/Android default slider reappeared"
assert "SliderDefaults." not in s, "A default slider style remains"
assert "private fun ThickGainControl(" not in s
src.write_text(s)

print("ECHOSPACE_ABR_CUSTOM_GAIN_READY",len(s),flush=True)
