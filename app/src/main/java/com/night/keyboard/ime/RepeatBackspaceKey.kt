package com.night.keyboard.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.night.keyboard.model.KeySpec
import com.night.keyboard.model.KeyStyleOverride
import com.night.keyboard.model.ThemeSnapshot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Backspace owns its pointer stream so a stationary press can repeat deletes
 * without requiring fake movement. A normal tap deletes exactly once; after
 * the initial delay it repeats until the same pointer is released/cancelled.
 */
@Composable
fun RepeatBackspaceKey(
    key: KeySpec,
    theme: ThemeSnapshot,
    modifier: Modifier,
    onBackspace: () -> Unit,
) {
    val style = theme.overrides[key.id] ?: KeyStyleOverride()
    val radius = (style.cornerRadiusDp ?: theme.cornerRadiusDp).dp
    val borderEnabled = style.borderEnabled ?: theme.borderEnabled
    val fill = when {
        style.invisibleFill == true -> Color.Transparent
        style.fillArgb != null -> Color(style.fillArgb.toInt()).copy(alpha = style.fillAlpha ?: 1f)
        else -> Color(theme.keyFillArgb.toInt())
    }
    val labelColor = Color((style.labelArgb ?: theme.keyLabelArgb).toInt())
    val borderColor = Color((style.borderArgb ?: theme.borderArgb).toInt())
    val borderWidth = if (borderEnabled) (style.borderWidthDp ?: theme.borderWidthDp).dp else 0.dp

    Box(
        modifier
            .height((style.heightDp ?: theme.keyHeightDp).coerceIn(34f, 80f).dp)
            .padding(horizontal = 1.dp)
            .background(fill, RoundedCornerShape(radius))
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, RoundedCornerShape(radius))
                else Modifier,
            )
            .semantics {
                contentDescription = "Backspace"
                role = Role.Button
                onClick {
                    onBackspace()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onBackspace()
                        coroutineScope {
                            val repeatJob = launch {
                                delay(380)
                                while (isActive) {
                                    onBackspace()
                                    delay(55)
                                }
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                repeatJob.cancel()
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            KeyboardIcons.Backspace,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
