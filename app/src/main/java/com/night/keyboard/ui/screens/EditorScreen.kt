package com.night.keyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.night.keyboard.model.KeySpec
import com.night.keyboard.model.KeyStyleOverride
import com.night.keyboard.model.KeyboardLayoutFactory
import com.night.keyboard.model.ThemeSnapshot

@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var typed by remember { mutableStateOf("") }
    var hue by remember { mutableStateOf(214f) }
    var saturation by remember { mutableStateOf(.85f) }
    var lightness by remember { mutableStateOf(.62f) }
    var baseHeight by remember { mutableFloatStateOf(theme.keyHeightDp) }
    var horizontalGap by remember { mutableFloatStateOf(theme.horizontalGapDp) }
    var verticalGap by remember { mutableFloatStateOf(theme.verticalGapDp) }
    var selectedWidth by remember { mutableFloatStateOf(1f) }
    var selectedHeight by remember { mutableFloatStateOf(theme.keyHeightDp) }
    val liveColor = hslToColor(hue, saturation, lightness)

    LaunchedEffect(theme.keyHeightDp, theme.horizontalGapDp, theme.verticalGapDp) {
        baseHeight = theme.keyHeightDp
        horizontalGap = theme.horizontalGapDp
        verticalGap = theme.verticalGapDp
    }
    LaunchedEffect(selected, theme.overrides, theme.keyHeightDp) {
        val first = selected.firstOrNull()
        selectedWidth = first?.let { theme.overrides[it]?.widthScale } ?: 1f
        selectedHeight = first?.let { theme.overrides[it]?.heightDp } ?: theme.keyHeightDp
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Key editor", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Tap keys or type characters below to select them instantly.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(theme.backgroundArgb.toInt())), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(theme.verticalGapDp.dp)) {
                    KeyboardLayoutFactory.letterRows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(theme.horizontalGapDp.dp)) {
                            row.forEach { key ->
                                val widthScale = theme.overrides[key.id]?.widthScale ?: 1f
                                EditorKey(key, theme, key.id in selected, Modifier.weight((key.weight * widthScale).coerceAtLeast(.2f))) {
                                    selected = if (key.id in selected) selected - key.id else selected + key.id
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = typed,
                onValueChange = { value ->
                    typed = value.filter(Char::isLetter).lowercase()
                    selected = typed.map(Char::toString).toSet()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Type keys to select") },
                placeholder = { Text("e.g. sybuaiwkve") },
                supportingText = { Text("${selected.size} key${if (selected.size == 1) "" else "s"} selected") },
                singleLine = true,
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { selected = KeyboardLayoutFactory.letterRows.flatten().map { it.id }.toSet() }, label = { Text("Select all") })
                AssistChip(onClick = { selected = emptySet(); typed = "" }, label = { Text("Clear selection") })
                AssistChip(onClick = { selected = "qwertyuiop".map(Char::toString).toSet() }, label = { Text("Top row") })
                AssistChip(onClick = { selected = setOf("space") }, label = { Text("Spacebar") })
            }
        }
        item {
            Text("Layout & density", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Keyboard-only sizing. This does not change Android display DPI.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text("Base key height ${baseHeight.toInt()} dp", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = baseHeight,
                onValueChange = { value ->
                    baseHeight = value
                    viewModel.updateBase { it.copy(keyHeightDp = value) }
                },
                valueRange = 40f..68f,
            )
            Text("Horizontal gap ${"%.1f".format(horizontalGap)} dp", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = horizontalGap,
                onValueChange = { value ->
                    horizontalGap = value
                    viewModel.updateBase { it.copy(horizontalGapDp = value) }
                },
                valueRange = 0f..5f,
            )
            Text("Vertical gap ${"%.1f".format(verticalGap)} dp", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = verticalGap,
                onValueChange = { value ->
                    verticalGap = value
                    viewModel.updateBase { it.copy(verticalGapDp = value) }
                },
                valueRange = 0f..5f,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                if (selected.isEmpty()) "Select one or more keys for per-key sizing" else "Selected key width ${"%.2f".format(selectedWidth)}×",
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = selectedWidth,
                onValueChange = { value ->
                    selectedWidth = value
                    viewModel.updateSelected(selected) { old -> old.copy(widthScale = value) }
                },
                valueRange = .55f..2f,
                enabled = selected.isNotEmpty(),
            )
            Text("Selected key height ${selectedHeight.toInt()} dp", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = selectedHeight,
                onValueChange = { value ->
                    selectedHeight = value
                    viewModel.updateSelected(selected) { old -> old.copy(heightDp = value) }
                },
                valueRange = 34f..80f,
                enabled = selected.isNotEmpty(),
            )
            Text(
                "Per-key width and height are saved as overrides, so changing global density later will not erase custom-sized keys.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Text("Live HSL color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            ColorPreview(liveColor)
            Text("Hue ${hue.toInt()}°", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hue,
                onValueChange = { value ->
                    hue = value
                    val argb = hslToColor(value, saturation, lightness).toArgb().toLong() and 0xFFFFFFFFL
                    viewModel.updateSelected(selected) { old -> old.copy(fillArgb = argb, invisibleFill = false) }
                },
                valueRange = 0f..360f,
            )
            Text("Saturation ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(value = saturation, onValueChange = { value ->
                saturation = value
                val argb = hslToColor(hue, value, lightness).toArgb().toLong() and 0xFFFFFFFFL
                viewModel.updateSelected(selected) { old -> old.copy(fillArgb = argb, invisibleFill = false) }
            })
            Text("Lightness ${(lightness * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(value = lightness, onValueChange = { value ->
                lightness = value
                val argb = hslToColor(hue, saturation, value).toArgb().toLong() and 0xFFFFFFFFL
                viewModel.updateSelected(selected) { old -> old.copy(fillArgb = argb, invisibleFill = false) }
            })
        }
        item {
            Text("Key style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(false, { viewModel.updateSelected(selected) { it.copy(fillArgb = 0, fillAlpha = 0f, borderEnabled = false, invisibleFill = false) } }, label = { Text("Borderless") })
                FilterChip(false, { viewModel.updateSelected(selected) { it.copy(fillArgb = 0x1AFFFFFF, fillAlpha = .10f, borderEnabled = false, invisibleFill = false) } }, label = { Text("Ghost") })
                FilterChip(false, { viewModel.updateSelected(selected) { it.copy(invisibleFill = true, fillAlpha = 0f, borderEnabled = false) } }, label = { Text("Invisible fill") })
                FilterChip(false, { viewModel.updateSelected(selected) { it.copy(borderEnabled = true, borderArgb = 0xFF6EA8FF, borderWidthDp = 1f) } }, label = { Text("Border") })
            }
        }
        item {
            var radius by remember { mutableStateOf(theme.cornerRadiusDp) }
            var labelSize by remember { mutableStateOf(theme.labelSizeSp) }
            Text("Corner radius ${radius.toInt()} dp", style = MaterialTheme.typography.labelMedium)
            Slider(radius, { radius = it; viewModel.updateSelected(selected) { old -> old.copy(cornerRadiusDp = it) } }, valueRange = 0f..28f)
            Text("Label size ${labelSize.toInt()} sp", style = MaterialTheme.typography.labelMedium)
            Slider(labelSize, { labelSize = it; viewModel.updateSelected(selected) { old -> old.copy(labelSizeSp = it) } }, valueRange = 12f..28f)
        }
        item {
            Text("Labels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(false, { viewModel.updateSelected(selected) { it.copy(bold = !(it.bold ?: false)) } }, label = { Text("Bold") })
                FilterChip(false, { viewModel.updateSelected(selected) { it.copy(italic = !(it.italic ?: false)) } }, label = { Text("Italic") })
                FilterChip(theme.secondaryCharactersVisible, { viewModel.updateBase { it.copy(secondaryCharactersVisible = !it.secondaryCharactersVisible) } }, label = { Text("Secondary chars") })
            }
        }
    }
}

@Composable
private fun EditorKey(key: KeySpec, theme: ThemeSnapshot, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val style = theme.overrides[key.id] ?: KeyStyleOverride()
    val radius = (style.cornerRadiusDp ?: theme.cornerRadiusDp).dp
    val borderEnabled = style.borderEnabled ?: theme.borderEnabled
    val customFill = style.fillArgb
    val fill = when {
        style.invisibleFill == true -> Color.Transparent
        customFill != null -> Color(customFill.toInt()).copy(alpha = style.fillAlpha ?: 1f)
        else -> Color(theme.keyFillArgb.toInt())
    }
    val label = Color((style.labelArgb ?: theme.keyLabelArgb).toInt())
    val outline = if (selected) Color(theme.accentArgb.toInt()) else Color((style.borderArgb ?: theme.borderArgb).toInt())
    val borderWidth = if (selected) 2.dp else if (borderEnabled) (style.borderWidthDp ?: theme.borderWidthDp).dp else 0.dp
    val secondary = key.secondary

    Box(
        modifier = modifier
            .height((style.heightDp ?: theme.keyHeightDp).coerceIn(34f, 80f).dp)
            .background(fill, RoundedCornerShape(radius))
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, outline, RoundedCornerShape(radius)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (theme.secondaryCharactersVisible && secondary != null) {
            Text(
                secondary,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 5.dp),
                color = Color(theme.secondaryLabelArgb.toInt()),
                fontSize = 8.sp,
            )
        }
        Text(
            key.label,
            color = label,
            fontSize = (style.labelSizeSp ?: theme.labelSizeSp).sp,
            fontWeight = if (style.bold == true) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (style.italic == true) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

@Composable
private fun ColorPreview(color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(58.dp).height(34.dp).background(color, RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(10.dp))
        Text("#%08X".format(color.toArgb()), style = MaterialTheme.typography.bodySmall)
    }
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val hh = ((h % 360f) + 360f) % 360f / 360f
    if (s <= 0f) return Color(l, l, l, 1f)
    val q = if (l < .5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun channel(tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < .5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return Color(channel(hh + 1f / 3f), channel(hh), channel(hh - 1f / 3f), 1f)
}
