package com.night.mirrorchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun UndoGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * .09f
        val p = Path().apply {
            moveTo(w * .30f, w * .32f)
            lineTo(w * .12f, w * .48f)
            lineTo(w * .31f, w * .64f)
        }
        drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(
            color = color,
            startAngle = 205f,
            sweepAngle = 235f,
            useCenter = false,
            topLeft = Offset(w * .21f, w * .20f),
            size = androidx.compose.ui.geometry.Size(w * .60f, w * .60f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun FlipGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * .085f
        drawArc(
            color, 205f, 145f, false,
            Offset(w * .16f, w * .13f),
            androidx.compose.ui.geometry.Size(w * .66f, w * .66f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w * .22f, w * .18f), Offset(w * .13f, w * .33f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * .13f, w * .33f), Offset(w * .30f, w * .34f), stroke, StrokeCap.Round)
        drawArc(
            color, 25f, 145f, false,
            Offset(w * .17f, w * .21f),
            androidx.compose.ui.geometry.Size(w * .66f, w * .66f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w * .78f, w * .82f), Offset(w * .87f, w * .67f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * .87f, w * .67f), Offset(w * .70f, w * .66f), stroke, StrokeCap.Round)
    }
}

@Composable
fun NewGameGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * .08f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * .16f, w * .16f),
            size = androidx.compose.ui.geometry.Size(w * .68f, w * .68f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .08f),
            style = Stroke(stroke),
        )
        drawLine(color, Offset(w * .50f, w * .31f), Offset(w * .50f, w * .69f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * .31f, w * .50f), Offset(w * .69f, w * .50f), stroke, StrokeCap.Round)
    }
}

@Composable
fun ChevronGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * .12f
        val p = Path().apply {
            moveTo(w * .28f, w * .38f)
            lineTo(w * .50f, w * .60f)
            lineTo(w * .72f, w * .38f)
        }
        drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun LockGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * .08f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * .22f, w * .43f),
            size = androidx.compose.ui.geometry.Size(w * .56f, w * .39f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .07f),
            style = Stroke(stroke),
        )
        drawArc(
            color = color,
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * .33f, w * .16f),
            size = androidx.compose.ui.geometry.Size(w * .34f, w * .44f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun ResignGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * .08f
        drawLine(color, Offset(w * .26f, w * .15f), Offset(w * .26f, w * .86f), stroke, StrokeCap.Round)
        val p = Path().apply {
            moveTo(w * .30f, w * .20f)
            lineTo(w * .74f, w * .27f)
            lineTo(w * .61f, w * .48f)
            lineTo(w * .30f, w * .42f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun ExportGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * .08f
        drawLine(color, Offset(w * .50f, w * .12f), Offset(w * .50f, w * .60f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * .32f, w * .30f), Offset(w * .50f, w * .12f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * .68f, w * .30f), Offset(w * .50f, w * .12f), stroke, StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * .18f, w * .48f),
            size = androidx.compose.ui.geometry.Size(w * .64f, w * .38f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f),
            style = Stroke(stroke),
        )
    }
}

@Composable
fun PlayGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val p = Path().apply {
            moveTo(w * .30f, w * .18f)
            lineTo(w * .78f, w * .50f)
            lineTo(w * .30f, w * .82f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun MirrorGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * .075f
        drawCircle(color, radius = w * .19f, center = Offset(w * .35f, w * .38f), style = Stroke(stroke))
        drawCircle(color, radius = w * .19f, center = Offset(w * .65f, w * .62f), style = Stroke(stroke))
        drawLine(color, Offset(w * .45f, w * .50f), Offset(w * .55f, w * .50f), stroke, StrokeCap.Round)
    }
}

@Composable
fun SettingsGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val center = Offset(w * .5f, w * .5f)
        val stroke = w * .075f
        drawCircle(color, radius = w * .22f, center = center, style = Stroke(stroke))
        drawCircle(color, radius = w * .075f, center = center, style = Stroke(stroke))
        val inner = w * .30f
        val outer = w * .41f
        val directions = listOf(
            Offset(0f, -1f), Offset(.707f, -.707f), Offset(1f, 0f), Offset(.707f, .707f),
            Offset(0f, 1f), Offset(-.707f, .707f), Offset(-1f, 0f), Offset(-.707f, -.707f),
        )
        directions.forEach { d ->
            drawLine(
                color,
                Offset(center.x + d.x * inner, center.y + d.y * inner),
                Offset(center.x + d.x * outer, center.y + d.y * outer),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}
