package eu.kanade.presentation.more.settings.screen.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Torri-owned, Figma-inspired color picker. Nothing here opens Android's system color dialog.
 * The 2D plane controls saturation (horizontal) and value (vertical).
 */
@Composable
internal fun TorriColorPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onHsvChanged: (hue: Float, saturation: Float, brightness: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestUpdate by rememberUpdatedState(onHsvChanged)
    val latestHue by rememberUpdatedState(hue)
    val latestSaturation by rememberUpdatedState(saturation)
    val latestBrightness by rememberUpdatedState(brightness)

    val colorPlaneDescription = stringResource(MR.strings.torri_theme_color_plane)
    val hueDescription = stringResource(MR.strings.torri_theme_hue)

    Column(modifier = modifier) {
        Text(
            text = stringResource(MR.strings.torri_theme_color_plane),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(194.dp)
                .semantics { contentDescription = colorPlaneDescription }
                .clip(RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        latestUpdate(
                            latestHue,
                            (position.x / size.width).coerceIn(0f, 1f),
                            (1f - position.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            latestUpdate(
                                latestHue,
                                (position.x / size.width).coerceIn(0f, 1f),
                                (1f - position.y / size.height).coerceIn(0f, 1f),
                            )
                        },
                    ) { change, _ ->
                        latestUpdate(
                            latestHue,
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            (1f - change.position.y / size.height).coerceIn(0f, 1f),
                        )
                        change.consume()
                    }
                },
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.hsv(hue, 1f, 1f)),
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                ),
            )
            val margin = 11.dp.toPx()
            val thumb = Offset(
                (saturation * size.width).coerceIn(margin, size.width - margin),
                ((1f - brightness) * size.height).coerceIn(margin, size.height - margin),
            )
            drawCircle(
                color = Color.Black,
                radius = 11.dp.toPx(),
                center = thumb,
                style = Stroke(width = 5.dp.toPx()),
            )
            drawCircle(
                color = Color.White,
                radius = 11.dp.toPx(),
                center = thumb,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(MR.strings.torri_theme_hue),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics { contentDescription = hueDescription }
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        latestUpdate(
                            (position.x / size.width * 359f).coerceIn(0f, 359f),
                            latestSaturation,
                            latestBrightness,
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            latestUpdate(
                                (position.x / size.width * 359f).coerceIn(0f, 359f),
                                latestSaturation,
                                latestBrightness,
                            )
                        },
                    ) { change, _ ->
                        latestUpdate(
                            (change.position.x / size.width * 359f).coerceIn(0f, 359f),
                            latestSaturation,
                            latestBrightness,
                        )
                        change.consume()
                    }
                },
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                ),
            )
            val radius = 12.dp.toPx()
            val center = Offset(
                (hue / 359f * size.width).coerceIn(radius, size.width - radius),
                size.height / 2f,
            )
            drawCircle(
                color = Color.Black,
                radius = 12.dp.toPx(),
                center = center,
                style = Stroke(width = 5.dp.toPx()),
            )
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
