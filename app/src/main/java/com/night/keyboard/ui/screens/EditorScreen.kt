package com.night.keyboard.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.night.keyboard.data.theme.ThemeCodec
import com.night.keyboard.model.KeySpec
import com.night.keyboard.model.KeyStyleOverride
import com.night.keyboard.model.KeyboardLayer
import com.night.keyboard.model.KeyboardLayoutFactory
import com.night.keyboard.model.ThemeSnapshot
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsState()
    val context = LocalContext.current

    var selected by remember { mutableStateOf(setOf<String>()) }
    var typed by remember { mutableStateOf("") }
    var previewLayer by remember { mutableStateOf(KeyboardLayer.LETTERS) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    var hue by remember { mutableFloatStateOf(214f) }
    var saturation by remember { mutableFloatStateOf(.85f) }
    var lightness by remember { mutableFloatStateOf(.62f) }
    var fillHex by remember { mutableStateOf("#6EA8FF") }
    var labelHex by remember { mutableStateOf("#F6F6F6") }
    var borderHex by remember { mutableStateOf("#6EA8FF") }
    var decoration by remember { mutableStateOf("") }

    var baseHeight by remember { mutableFloatStateOf(theme.keyHeightDp) }
    var horizontalGap by remember { mutableFloatStateOf(theme.horizontalGapDp) }
    var verticalGap by remember { mutableFloatStateOf(theme.verticalGapDp) }
    var selectedWidth by remember { mutableFloatStateOf(1f) }
    var selectedHeight by remember { mutableFloatStateOf(theme.keyHeightDp) }
    var borderWidth by remember { mutableFloatStateOf(0f) }
    var shadowElevation by remember { mutableFloatStateOf(0f) }
    var fontFamily by remember { mutableStateOf("System") }

    val liveColor = hslToColor(hue, saturation, lightness)
    val previewRows = KeyboardLayoutFactory.rows(previewLayer)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(ThemeCodec.encode(theme))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()?.let(viewModel::importTheme)
    }

    LaunchedEffect(theme.keyHeightDp, theme.horizontalGapDp, theme.verticalGapDp) {
        baseHeight = theme.keyHeightDp
        horizontalGap = theme.horizontalGapDp
        verticalGap = theme.verticalGapDp
    }

    LaunchedEffect(selected, theme.overrides, theme.keyHeightDp) {
        val first = selected.firstOrNull()
        val style = first?.let(theme.overrides::get)
        selectedWidth = style?.widthScale ?: 1f
        selectedHeight = style?.heightDp ?: theme.keyHeightDp
        borderWidth = style?.borderWidthDp ?: theme.borderWidthDp
        shadowElevation = style?.shadowElevationDp ?: 0f
        fontFamily = style?.fontFamilyName ?: "System"
        decoration = style?.decorationText.orEmpty()
        fillHex = argbHex(style?.fillArgb ?: theme.keyFillArgb)
        labelHex = argbHex(style?.labelArgb ?: theme.keyLabelArgb)
        borderHex = argbHex(style?.borderArgb ?: theme.borderArgb)
    }

    fun applyFill(color: Color) {
        val argb = color.toArgb().toLong() and 0xFFFFFFFFL
        fillHex = argbHex(argb)
        viewModel.updateSelected(selected) { old ->
            old.copy(fillArgb = argb, invisibleFill = false)
        }
    }

    fun selectAt(position: Offset) {
        if (previewSize.width <= 0 || previewSize.height <= 0) return
        val rowHeight = previewSize.height.toFloat() / previewRows.size.coerceAtLeast(1)
        val rowIndex = (position.y / rowHeight).toInt().coerceIn(0, previewRows.lastIndex)
        val row = previewRows[rowIndex]
        val totalWeight = row.sumOf { key ->
            ((key.weight * (theme.overrides[key.id]?.widthScale ?: 1f)).coerceAtLeast(.2f)).toDouble()
        }.toFloat()
        val normalizedX = (position.x / previewSize.width.toFloat()).coerceIn(0f, .9999f)
        var cursor = 0f
        row.forEach { key ->
            val weight = (key.weight * (theme.overrides[key.id]?.widthScale ?: 1f)).coerceAtLeast(.2f)
            val end = cursor + weight / totalWeight
            if (normalizedX in cursor until end) {
                selected = selected + key.id
                typed = ""
                return
            }
            cursor = end
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Key editor", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                "Tap keys, type letters, or drag across the preview to select a group.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    KeyboardLayer.LETTERS to "Letters",
                    KeyboardLayer.SYMBOLS to "Symbols",
                    KeyboardLayer.SYMBOLS_MORE to "More symbols",
                ).forEach { (layer, label) ->
                    FilterChip(
                        selected = previewLayer == layer,
                        onClick = {
                            previewLayer = layer
                            selected = emptySet()
                            typed = ""
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(theme.backgroundArgb.toInt())),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .onSizeChanged { previewSize = it }
                        .pointerInput(previewLayer, theme.overrides, previewSize) {
                            detectDragGestures(
                                onDragStart = { position -> selectAt(position) },
                                onDrag = { change, _ ->
                                    selectAt(change.position)
                                    change.consume()
                                },
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(theme.verticalGapDp.dp),
                ) {
                    previewRows.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(theme.horizontalGapDp.dp),
                        ) {
                            row.forEach { key ->
                                val widthScale = theme.overrides[key.id]?.widthScale ?: 1f
                                EditorKey(
                                    key = key,
                                    theme = theme,
                                    selected = key.id in selected,
                                    modifier = Modifier.weight((key.weight * widthScale).coerceAtLeast(.2f)),
                                ) {
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
                supportingText = {
                    Text("${selected.size} key${if (selected.size == 1) "" else "s"} selected")
                },
                singleLine = true,
            )
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { selected = previewRows.flatten().map { it.id }.toSet() },
                    label = { Text("Select page") },
                )
                AssistChip(
                    onClick = { selected = emptySet(); typed = "" },
                    label = { Text("Clear selection") },
                )
                AssistChip(
                    onClick = { selected = "qwertyuiop".map(Char::toString).toSet() },
                    label = { Text("Top row") },
                )
                AssistChip(onClick = { selected = setOf("space") }, label = { Text("Spacebar") })
                AssistChip(
                    onClick = { viewModel.resetSelected(selected) },
                    label = { Text("Reset selected") },
                )
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
                if (selected.isEmpty()) {
                    "Select one or more keys for per-key sizing"
                } else {
                    "Selected key width ${"%.2f".format(selectedWidth)}×"
                },
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
        }

        item {
            Text("Fill color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            ColorPreview(liveColor)
            HueWheel(hue = hue) { value ->
                hue = value
                applyFill(hslToColor(value, saturation, lightness))
            }
            Text("Hue ${hue.toInt()}°", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hue,
                onValueChange = { value ->
                    hue = value
                    applyFill(hslToColor(value, saturation, lightness))
                },
                valueRange = 0f..360f,
            )
            Text("Saturation ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = saturation,
                onValueChange = { value ->
                    saturation = value
                    applyFill(hslToColor(hue, value, lightness))
                },
            )
            Text("Lightness ${(lightness * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = lightness,
                onValueChange = { value ->
                    lightness = value
                    applyFill(hslToColor(hue, saturation, value))
                },
            )
            HexColorField("Fill hex", fillHex) { value ->
                fillHex = value
                parseArgb(value)?.let { argb ->
                    viewModel.updateSelected(selected) {
                        it.copy(fillArgb = argb, invisibleFill = false)
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    0xFF111317L, 0xFF2B2F36L, 0xFFFFFFFFL, 0xFF6EA8FFL,
                    0xFF78D69CL, 0xFFE4B661L, 0xFFFF7A90L, 0xFFB99AFFL,
                ).forEach { argb ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(Color(argb.toInt()), RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { applyFill(Color(argb.toInt())) },
                    )
                }
            }
        }

        item {
            Text("Border & shadow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.updateSelected(selected) {
                            it.copy(fillArgb = 0, fillAlpha = 0f, borderEnabled = false, invisibleFill = false)
                        }
                    },
                    label = { Text("Borderless") },
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.updateSelected(selected) {
                            it.copy(fillArgb = 0x1AFFFFFF, fillAlpha = .10f, borderEnabled = false, invisibleFill = false)
                        }
                    },
                    label = { Text("Ghost") },
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.updateSelected(selected) {
                            it.copy(invisibleFill = true, fillAlpha = 0f, borderEnabled = false)
                        }
                    },
                    label = { Text("Invisible fill") },
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.updateSelected(selected) {
                            it.copy(borderEnabled = true, borderArgb = 0xFF6EA8FF, borderWidthDp = 1f)
                        }
                    },
                    label = { Text("Border") },
                )
            }
            Text("Border thickness ${"%.1f".format(borderWidth)} dp", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = borderWidth,
                onValueChange = { value ->
                    borderWidth = value
                    viewModel.updateSelected(selected) {
                        it.copy(borderEnabled = value > 0f, borderWidthDp = value)
                    }
                },
                valueRange = 0f..4f,
                enabled = selected.isNotEmpty(),
            )
            HexColorField("Border hex", borderHex) { value ->
                borderHex = value
                parseArgb(value)?.let { argb ->
                    viewModel.updateSelected(selected) { it.copy(borderArgb = argb, borderEnabled = true) }
                }
            }
            Text("Shadow ${shadowElevation.toInt()} dp", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = shadowElevation,
                onValueChange = { value ->
                    shadowElevation = value
                    viewModel.updateSelected(selected) { it.copy(shadowElevationDp = value) }
                },
                valueRange = 0f..14f,
                enabled = selected.isNotEmpty(),
            )
        }

        item {
            var radius by remember { mutableFloatStateOf(theme.cornerRadiusDp) }
            var labelSize by remember { mutableFloatStateOf(theme.labelSizeSp) }
            Text("Corner radius ${radius.toInt()} dp", style = MaterialTheme.typography.labelMedium)
            Slider(
                radius,
                {
                    radius = it
                    viewModel.updateSelected(selected) { old -> old.copy(cornerRadiusDp = it) }
                },
                valueRange = 0f..28f,
            )
            Text("Label size ${labelSize.toInt()} sp", style = MaterialTheme.typography.labelMedium)
            Slider(
                labelSize,
                {
                    labelSize = it
                    viewModel.updateSelected(selected) { old -> old.copy(labelSizeSp = it) }
                },
                valueRange = 12f..28f,
            )
            HexColorField("Label hex", labelHex) { value ->
                labelHex = value
                parseArgb(value)?.let { argb ->
                    viewModel.updateSelected(selected) { it.copy(labelArgb = argb) }
                }
            }
        }

        item {
            Text("Labels & decoration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    false,
                    { viewModel.updateSelected(selected) { it.copy(bold = !(it.bold ?: false)) } },
                    label = { Text("Bold") },
                )
                FilterChip(
                    false,
                    { viewModel.updateSelected(selected) { it.copy(italic = !(it.italic ?: false)) } },
                    label = { Text("Italic") },
                )
                FilterChip(
                    theme.secondaryCharactersVisible,
                    {
                        viewModel.updateBase {
                            it.copy(secondaryCharactersVisible = !it.secondaryCharactersVisible)
                        }
                    },
                    label = { Text("Secondary chars") },
                )
            }

            Text("Font family", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("System", "Serif", "Monospace", "Cursive").forEach { family ->
                    FilterChip(
                        selected = fontFamily == family,
                        onClick = {
                            fontFamily = family
                            viewModel.updateSelected(selected) { it.copy(fontFamilyName = family) }
                        },
                        label = { Text(family, fontFamily = fontFamilyFor(family)) },
                    )
                }
            }

            OutlinedTextField(
                value = decoration,
                onValueChange = { value ->
                    decoration = value.take(4)
                    viewModel.updateSelected(selected) {
                        it.copy(decorationText = decoration.ifBlank { null })
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Key decoration") },
                supportingText = {
                    Text("Use a short symbol or emoji. Select Spacebar first to decorate the spacebar.")
                },
                singleLine = true,
            )
        }

        item {
            Text("Theme files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = viewModel::duplicateTheme) { Text("Duplicate") }
                OutlinedButton(
                    onClick = { exportLauncher.launch("${theme.name.ifBlank { "Keyboard" }}.json") },
                ) { Text("Export") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                ) { Text("Import") }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, theme.name)
                            putExtra(Intent.EXTRA_TEXT, ThemeCodec.encode(theme))
                        }
                        context.startActivity(Intent.createChooser(intent, "Share keyboard theme"))
                    },
                ) { Text("Share") }
            }
        }
    }
}

@Composable
private fun EditorKey(
    key: KeySpec,
    theme: ThemeSnapshot,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
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
    val outline = if (selected) {
        Color(theme.accentArgb.toInt())
    } else {
        Color((style.borderArgb ?: theme.borderArgb).toInt())
    }
    val borderWidth = if (selected) {
        2.dp
    } else if (borderEnabled) {
        (style.borderWidthDp ?: theme.borderWidthDp).dp
    } else {
        0.dp
    }
    val secondary = key.secondary

    Box(
        modifier = modifier
            .height((style.heightDp ?: theme.keyHeightDp).coerceIn(34f, 80f).dp)
            .then(
                if ((style.shadowElevationDp ?: 0f) > 0f) {
                    Modifier.shadow(
                        (style.shadowElevationDp ?: 0f).dp,
                        RoundedCornerShape(radius),
                        clip = false,
                    )
                } else {
                    Modifier
                },
            )
            .background(fill, RoundedCornerShape(radius))
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, outline, RoundedCornerShape(radius))
                } else {
                    Modifier
                },
            )
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
            fontFamily = fontFamilyFor(style.fontFamilyName),
        )
        style.decorationText?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text.take(4),
                color = Color((style.decorationArgb ?: style.labelArgb ?: theme.keyLabelArgb).toInt()),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
            )
        }
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

@Composable
private fun HueWheel(hue: Float, onHue: (Float) -> Unit) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(118.dp)
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val angle = Math.toDegrees(
                        atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble()),
                    ).toFloat()
                    onHue((angle + 450f) % 360f)
                }
            },
    ) {
        val radius = size.minDimension * .35f
        val dot = size.minDimension * .055f
        repeat(48) { index ->
            val h = index * (360f / 48f)
            val angle = (h - 90f) * (PI.toFloat() / 180f)
            val point = Offset(
                center.x + cos(angle) * radius,
                center.y + sin(angle) * radius,
            )
            drawCircle(hslToColor(h, .9f, .58f), dot, point)
        }
        val angle = (hue - 90f) * (PI.toFloat() / 180f)
        val selectedPoint = Offset(
            center.x + cos(angle) * radius,
            center.y + sin(angle) * radius,
        )
        drawCircle(Color.White, dot * .65f, selectedPoint)
    }
}

@Composable
private fun HexColorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }
            onValueChange(filtered.take(9))
        },
        label = { Text(label) },
        supportingText = { Text("Use #RRGGBB or #AARRGGBB") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = value.isNotBlank() && parseArgb(value) == null,
    )
}

private fun parseArgb(raw: String): Long? {
    val clean = raw.trim().removePrefix("#")
    if (clean.length !in setOf(6, 8) || clean.any { it !in "0123456789abcdefABCDEF" }) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) 0xFF000000L or value else value
}

private fun argbHex(argb: Long): String = "#%08X".format(argb and 0xFFFFFFFFL)

private fun fontFamilyFor(name: String?): FontFamily = when (name) {
    "Serif" -> FontFamily.Serif
    "Monospace" -> FontFamily.Monospace
    "Cursive" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
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
    return Color(
        channel(hh + 1f / 3f),
        channel(hh),
        channel(hh - 1f / 3f),
        1f,
    )
}
