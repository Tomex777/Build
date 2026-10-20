package com.night.offlinegames

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Vector-only original board artwork; scales cleanly on phones and tablets. */
fun DrawScope.drawLightsBoard(state: NativeLightsState, hint: Int?) {
    val cell = size.width / NativeLightsEngine.side
    for (index in 0 until NativeLightsEngine.count) {
        val x = (index % NativeLightsEngine.side) * cell
        val y = (index / NativeLightsEngine.side) * cell
        val on = state.board and (1 shl index) != 0
        val center = Offset(x + cell / 2f, y + cell / 2f)
        drawRoundRect(
            color = if (on) Color(0xFF3A6A59) else Color(0xFF27463E),
            topLeft = Offset(x + cell * .045f, y + cell * .045f),
            size = Size(cell * .91f, cell * .91f),
            cornerRadius = CornerRadius(cell * .17f)
        )
        if (on) {
            drawCircle(Color(0xFFF3BB68).copy(alpha = .14f), cell * .39f, center)
            drawCircle(Color(0xFFF3BB68).copy(alpha = .35f), cell * .30f, center)
            drawCircle(Color(0xFFFFD68B), cell * .205f, center)
            drawCircle(Color(0xFFFFF1CE), cell * .09f,
                center + Offset(-cell * .05f, -cell * .055f))
        } else {
            drawCircle(Color(0xFF102D27), cell * .23f, center)
            drawCircle(Color(0xFF355C4D), cell * .12f, center)
        }
        if (hint == index) {
            drawRoundRect(
                Color(0xFFFFE6A3),
                Offset(x + cell * .08f, y + cell * .08f),
                Size(cell * .84f, cell * .84f),
                CornerRadius(cell * .15f), style = Stroke(cell * .045f)
            )
        }
    }
}
