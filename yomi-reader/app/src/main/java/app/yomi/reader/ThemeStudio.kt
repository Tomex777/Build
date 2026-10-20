package app.yomi.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * App chrome only. Reader bars deliberately remain Mihon's pinned dark reader
 * UI so a library color preference never changes the actual comic rendering.
 */
internal data class YomiAppearance(
    val id: String,
    val name: String,
    val primary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val foreground: Color,
    val secondaryText: Color,
    val light: Boolean = false,
)

internal object YomiAppearances {
    const val DEFAULT_ID = "midnight"
    const val CUSTOM_ID = "custom"
    const val DEFAULT_CUSTOM_HEX = "D4E4FF"

    val presets = listOf(
        YomiAppearance("midnight", "Midnight", Color(0xFFD4E4FF), Color(0xFF0A0D12),
            Color(0xFF111722), Color(0xFF1A2230), Color(0xFFE7ECF5), Color(0xFFAAB6C9)),
        YomiAppearance("carbon", "Carbon", Color(0xFFE4E4E4), Color(0xFF08090B),
            Color(0xFF16171B), Color(0xFF292B31), Color(0xFFF0F0F1), Color(0xFFAEAFB5)),
        YomiAppearance("cedar", "Cedar", Color(0xFF9BDBB3), Color(0xFF0A1713),
            Color(0xFF13251E), Color(0xFF1E382C), Color(0xFFE2F5E9), Color(0xFFA6C6B7)),
        YomiAppearance("plum", "Plum", Color(0xFFD8B9FD), Color(0xFF15101C),
            Color(0xFF251B30), Color(0xFF382844), Color(0xFFF4EAFB), Color(0xFFC9B7D5)),
        YomiAppearance("dusk", "Dusk", Color(0xFFF2BE9E), Color(0xFF1D1316),
            Color(0xFF2F2025), Color(0xFF463038), Color(0xFFFFEDE7), Color(0xFFDDC1BE)),
        YomiAppearance("paper", "Paper", Color(0xFF385B9E), Color(0xFFF3F2ED),
            Color(0xFFFFFFFF), Color(0xFFE5E8ED), Color(0xFF202831), Color(0xFF68727D), light = true),
    )

    fun normalizedHex(value: String): String? {
        val stripped = value.trim().removePrefix("#")
        return stripped.uppercase(Locale.ROOT).takeIf { it.matches(Regex("[0-9A-F]{6}")) }
    }

    fun resolve(id: String, customHex: String): YomiAppearance {
        if (id != CUSTOM_ID) return presets.firstOrNull { it.id == id } ?: presets.first()
        val hex = normalizedHex(customHex) ?: DEFAULT_CUSTOM_HEX
        val accent = Color((0xFF000000L or hex.toLong(16)).toInt())
        val base = presets.first()
        return base.copy(id = CUSTOM_ID, name = "Custom", primary = accent)
    }
}

/** Live-applying preset gallery plus a styled, validated custom HEX input. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeStudioSheet(
    selectedId: String,
    customHex: String,
    onSelectPreset: (String) -> Unit,
    onApplyCustomHex: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(customHex) { mutableStateOf(customHex) }
    val validHex = YomiAppearances.normalizedHex(draft)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Theme studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Choose a palette", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            YomiAppearances.presets.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { preset ->
                        AppearanceTile(
                            preset,
                            isSelected = selectedId == preset.id,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectPreset(preset.id) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text("Custom color", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            AppearanceTile(
                YomiAppearances.resolve(YomiAppearances.CUSTOM_ID, customHex),
                isSelected = selectedId == YomiAppearances.CUSTOM_ID,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelectPreset(YomiAppearances.CUSTOM_ID) },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.weight(1f).height(52.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        BasicTextField(
                            value = draft,
                            onValueChange = { candidate ->
                                val clean = candidate.removePrefix("#").uppercase(Locale.ROOT)
                                if (clean.length <= 6 && clean.all { it in '0'..'9' || it in 'A'..'F' }) {
                                    draft = clean
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            modifier = Modifier.weight(1f)
                                .semantics { contentDescription = "Custom theme color in hexadecimal" },
                        )
                    }
                }
                Button(
                    enabled = validHex != null,
                    onClick = { validHex?.let(onApplyCustomHex) },
                ) { Text("Use color") }
            }
        }
    }
}

@Composable
private fun AppearanceTile(
    appearance: YomiAppearance,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val outline = if (isSelected) appearance.primary else appearance.surfaceVariant
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .border(if (isSelected) 2.dp else 1.dp, outline, RoundedCornerShape(14.dp))
            .background(appearance.background)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${appearance.name} theme"
                selected = isSelected
            }
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(appearance.primary))
            Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(appearance.surfaceVariant))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(5.dp)).background(appearance.surface))
            Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(5.dp)).background(appearance.surfaceVariant))
        }
        Text(
            appearance.name,
            color = appearance.foreground,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
