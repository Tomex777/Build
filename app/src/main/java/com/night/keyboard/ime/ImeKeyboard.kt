@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.night.keyboard.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.keyboard.data.prefs.KeyboardPreferenceState
import com.night.keyboard.model.*
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

private enum class ToolPanel { NONE, CLIPBOARD, EMOJI, VOICE, EDITOR, TONE, RESEARCH }

@Composable
fun ImeKeyboard(
    controller: KeyboardController,
    themeFlow: Flow<ThemeSnapshot>,
    preferenceFlow: Flow<KeyboardPreferenceState>,
    clipboardFlow: Flow<List<ClipboardItem>>,
) {
    val theme by themeFlow.collectAsState(initial = ThemeSnapshot())
    val prefs by preferenceFlow.collectAsState(initial = KeyboardPreferenceState())
    val clips by clipboardFlow.collectAsState(initial = emptyList())
    var layer by remember { mutableStateOf(KeyboardLayer.LETTERS) }
    var shift by remember { mutableStateOf(ShiftState.OFF) }
    var panel by remember { mutableStateOf(ToolPanel.NONE) }
    var textVersion by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf(listOf("I’m", "the", "thank you")) }

    fun textChanged() { textVersion++ }
    LaunchedEffect(textVersion) {
        suggestions = SuggestionEngine.suggest(controller.textBeforeCursor())
    }

    Surface(color = Color(theme.backgroundArgb.toInt()), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 5.dp)) {
            Toolbar(panel, onPanel = { panel = if (panel == it) ToolPanel.NONE else it }, controller)
            if (panel != ToolPanel.NONE) {
                ToolPanelContent(panel, clips, prefs.serverUrl, controller, ::textChanged)
            }
            if (prefs.suggestions && panel == ToolPanel.NONE) {
                SuggestionStrip(suggestions) {
                    controller.replaceCurrentWord(it)
                    controller.commit(" ")
                    textChanged()
                }
            }
            if (prefs.numberRow && layer == KeyboardLayer.LETTERS) {
                NumberRow(controller, ::textChanged, theme)
            }
            KeyboardRows(
                rows = KeyboardLayoutFactory.rows(layer),
                layer = layer,
                shift = shift,
                theme = theme,
                secondaryVisible = prefs.secondaryCharacters,
                controller = controller,
                onLayer = { layer = it; panel = ToolPanel.NONE },
                onShift = { shift = it },
                onOpenEmoji = { panel = if (panel == ToolPanel.EMOJI) ToolPanel.NONE else ToolPanel.EMOJI },
                onTextChanged = ::textChanged,
            )
        }
    }
}

@Composable
private fun Toolbar(panel: ToolPanel, onPanel: (ToolPanel) -> Unit, controller: KeyboardController) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(KeyboardIcons.Clipboard, "Clipboard", panel == ToolPanel.CLIPBOARD) { onPanel(ToolPanel.CLIPBOARD) }
        ToolButton(KeyboardIcons.Emoji, "Emoji", panel == ToolPanel.EMOJI) { onPanel(ToolPanel.EMOJI) }
        ToolButton(KeyboardIcons.Voice, "Voice input", panel == ToolPanel.VOICE) { onPanel(ToolPanel.VOICE) }
        ToolButton(KeyboardIcons.Editor, "Editor", panel == ToolPanel.EDITOR) { onPanel(ToolPanel.EDITOR) }
        ToolButton(KeyboardIcons.Tone, "Tone", panel == ToolPanel.TONE) { onPanel(ToolPanel.TONE) }
        ToolButton(KeyboardIcons.Research, "Research", panel == ToolPanel.RESEARCH) { onPanel(ToolPanel.RESEARCH) }
        Spacer(Modifier.width(2.dp))
        ToolButton(KeyboardIcons.InputPicker, "Input picker", false) { controller.showInputPicker() }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(39.dp).background(
            if (selected) Color(0xFF20262D) else Color.Transparent,
            CircleShape,
        ),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) Color.White else Color(0xFFD6DCE2),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ToolPanelContent(
    panel: ToolPanel,
    clips: List<ClipboardItem>,
    serverUrl: String,
    controller: KeyboardController,
    onCommitted: () -> Unit,
) {
    Surface(
        color = Color(0xFF0B0E11),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        when (panel) {
            ToolPanel.CLIPBOARD -> Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                clips.take(8).forEach { clip ->
                    Surface(
                        onClick = { controller.commit(clip.text); onCommitted() },
                        color = Color(0xFF171C22),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            clip.text,
                            maxLines = 2,
                            fontSize = 11.sp,
                            modifier = Modifier.width(150.dp).padding(9.dp),
                        )
                    }
                }
                if (clips.isEmpty()) {
                    Text("Clipboard is empty", color = Color(0xFF8F99A4), fontSize = 11.sp, modifier = Modifier.padding(9.dp))
                }
            }
            ToolPanel.EMOJI -> EmojiPanel(controller, onCommitted)
            ToolPanel.VOICE -> StatusPanel(
                "Voice input",
                if (serverUrl.isBlank()) "Set your Whisper server URL in the app before voice input is enabled."
                else "Whisper endpoint configured. Recording/stream upload is the next integration gate.",
            )
            ToolPanel.EDITOR -> OnlineToolPanel(
                "Editor",
                "Fix spelling, grammar and punctuation without changing meaning.",
                serverUrl,
                controller.selectedText(),
            )
            ToolPanel.TONE -> OnlineToolPanel(
                "Tone",
                "Rewrite selected text with a chosen tone.",
                serverUrl,
                controller.selectedText(),
            )
            ToolPanel.RESEARCH -> OnlineToolPanel(
                "Contextual Research",
                "Research the selected text or current query without adding a generic chatbot.",
                serverUrl,
                controller.selectedText(),
            )
            ToolPanel.NONE -> Unit
        }
    }
}

@Composable
private fun StatusPanel(title: String, message: String) {
    Column(Modifier.padding(11.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(message, color = Color(0xFF8F99A4), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun OnlineToolPanel(title: String, message: String, serverUrl: String, selectedText: String) {
    Column(Modifier.padding(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                if (serverUrl.isBlank()) "Not configured" else "Server ready",
                color = if (serverUrl.isBlank()) Color(0xFFE4B661) else Color(0xFF78D69C),
                fontSize = 9.sp,
            )
        }
        Text(message, color = Color(0xFF8F99A4), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        if (selectedText.isNotBlank()) {
            Text(
                "Selected: ${selectedText.take(80)}",
                color = Color(0xFFC9D1D9),
                fontSize = 10.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun EmojiPanel(controller: KeyboardController, onCommitted: () -> Unit) {
    val entries = remember { listOf("🙂", "😄", "😍", "😎", "😔", "😢", "😠", "❤️", "✨", "☀️", "🌸", "🚀") }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        entries.forEach { emoji ->
            Surface(
                onClick = { controller.commit(emoji); onCommitted() },
                color = Color.Transparent,
                shape = RoundedCornerShape(9.dp),
            ) {
                Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun SuggestionStrip(suggestions: List<String>, onSuggestion: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
        suggestions.take(3).forEach { suggestion ->
            Box(
                Modifier.weight(1f).height(41.dp).combinedClickable(onClick = { onSuggestion(suggestion) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(suggestion, color = Color(0xFFF2F3F5), fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NumberRow(controller: KeyboardController, onTextChanged: () -> Unit, theme: ThemeSnapshot) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(theme.horizontalGapDp.dp)) {
        "1234567890".forEach { c ->
            val id = "number_$c"
            val widthScale = theme.overrides[id]?.widthScale ?: 1f
            ImeKey(
                key = KeySpec(id, c.toString(), output = c.toString()),
                displayLabel = c.toString(),
                theme = theme,
                secondaryVisible = false,
                modifier = Modifier.weight(widthScale.coerceIn(.4f, 2.5f)),
                onClick = { controller.commit(c.toString()); onTextChanged() },
            )
        }
    }
}

@Composable
private fun KeyboardRows(
    rows: List<List<KeySpec>>,
    layer: KeyboardLayer,
    shift: ShiftState,
    theme: ThemeSnapshot,
    secondaryVisible: Boolean,
    controller: KeyboardController,
    onLayer: (KeyboardLayer) -> Unit,
    onShift: (ShiftState) -> Unit,
    onOpenEmoji: () -> Unit,
    onTextChanged: () -> Unit,
) {
    rows.forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = theme.verticalGapDp.dp),
            horizontalArrangement = Arrangement.spacedBy(theme.horizontalGapDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.forEach { key ->
                val rawOutput = key.output
                val secondary = key.secondary
                val display = if (
                    layer == KeyboardLayer.LETTERS &&
                    rawOutput?.singleOrNull()?.isLetter() == true &&
                    shift != ShiftState.OFF
                ) key.label.uppercase() else key.label
                val widthScale = theme.overrides[key.id]?.widthScale ?: 1f
                val effectiveWeight = (key.weight * widthScale).coerceAtLeast(.2f)

                if (key.special == SpecialKey.SPACE) {
                    SpacebarKey(
                        key,
                        theme,
                        Modifier.weight(effectiveWeight),
                        onSpace = { controller.commit(" "); onTextChanged() },
                        onCursor = { controller.moveCursor(it) },
                    )
                } else {
                    ImeKey(
                        key = key,
                        displayLabel = display,
                        theme = theme,
                        secondaryVisible = secondaryVisible,
                        modifier = Modifier.weight(effectiveWeight),
                        onClick = {
                            when (key.special) {
                                SpecialKey.SHIFT -> onShift(if (shift == ShiftState.OFF) ShiftState.ONCE else ShiftState.OFF)
                                SpecialKey.BACKSPACE -> { controller.backspace(); onTextChanged() }
                                SpecialKey.ENTER -> { controller.enter(); onTextChanged() }
                                SpecialKey.EMOJI -> onOpenEmoji()
                                SpecialKey.NUMBERS -> onLayer(KeyboardLayer.SYMBOLS)
                                SpecialKey.LETTERS -> onLayer(KeyboardLayer.LETTERS)
                                SpecialKey.MORE_SYMBOLS -> onLayer(KeyboardLayer.SYMBOLS_MORE)
                                SpecialKey.LESS_SYMBOLS -> onLayer(KeyboardLayer.SYMBOLS)
                                else -> rawOutput?.let { raw ->
                                    val output = if (
                                        layer == KeyboardLayer.LETTERS &&
                                        raw.length == 1 && raw[0].isLetter() && shift != ShiftState.OFF
                                    ) raw.uppercase() else raw
                                    controller.commit(output)
                                    if (shift == ShiftState.ONCE) onShift(ShiftState.OFF)
                                    onTextChanged()
                                }
                            }
                        },
                        onDoubleClick = if (key.special == SpecialKey.SHIFT) {
                            { onShift(if (shift == ShiftState.LOCKED) ShiftState.OFF else ShiftState.LOCKED) }
                        } else null,
                        onLongClick = if (secondary != null && key.special == null) {
                            { controller.commit(secondary); onTextChanged() }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImeKey(
    key: KeySpec,
    displayLabel: String,
    theme: ThemeSnapshot,
    secondaryVisible: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val style = theme.overrides[key.id] ?: KeyStyleOverride()
    val radius = (style.cornerRadiusDp ?: theme.cornerRadiusDp).dp
    val borderEnabled = style.borderEnabled ?: theme.borderEnabled
    val customFill = style.fillArgb
    val fill = when {
        style.invisibleFill == true -> Color.Transparent
        customFill != null -> Color(customFill.toInt()).copy(alpha = style.fillAlpha ?: 1f)
        else -> Color(theme.keyFillArgb.toInt())
    }
    val labelColor = Color((style.labelArgb ?: theme.keyLabelArgb).toInt())
    val borderColor = Color((style.borderArgb ?: theme.borderArgb).toInt())
    val borderWidth = if (borderEnabled) (style.borderWidthDp ?: theme.borderWidthDp).dp else 0.dp
    val secondary = key.secondary
    val specialIcon = when (key.special) {
        SpecialKey.SHIFT -> KeyboardIcons.Shift
        SpecialKey.BACKSPACE -> KeyboardIcons.Backspace
        SpecialKey.ENTER -> KeyboardIcons.Enter
        SpecialKey.EMOJI -> KeyboardIcons.Emoji
        else -> null
    }

    Box(
        modifier
            .height((style.heightDp ?: theme.keyHeightDp).coerceIn(34f, 80f).dp)
            .padding(horizontal = 1.dp)
            .background(fill, RoundedCornerShape(radius))
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, RoundedCornerShape(radius))
                else Modifier,
            )
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick,
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (secondaryVisible && secondary != null && secondary != "mic") {
            Text(
                secondary,
                Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 6.dp),
                color = Color(theme.secondaryLabelArgb.toInt()),
                fontSize = 8.sp,
            )
        }
        if (specialIcon != null) {
            Icon(
                specialIcon,
                contentDescription = when (key.special) {
                    SpecialKey.SHIFT -> "Shift"
                    SpecialKey.BACKSPACE -> "Backspace"
                    SpecialKey.ENTER -> "Enter"
                    SpecialKey.EMOJI -> "Emoji"
                    else -> null
                },
                tint = labelColor,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(
                displayLabel,
                color = labelColor,
                fontSize = (style.labelSizeSp ?: theme.labelSizeSp).sp,
                fontWeight = if (style.bold == true) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic == true) FontStyle.Italic else FontStyle.Normal,
            )
        }
        if (key.special == SpecialKey.COMMA && secondary == "mic") {
            Icon(
                KeyboardIcons.Voice,
                contentDescription = null,
                tint = Color(theme.secondaryLabelArgb.toInt()),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 5.dp).size(10.dp),
            )
        }
    }
}

@Composable
private fun SpacebarKey(
    key: KeySpec,
    theme: ThemeSnapshot,
    modifier: Modifier,
    onSpace: () -> Unit,
    onCursor: (Int) -> Boolean,
) {
    var accumulated by remember { mutableFloatStateOf(0f) }
    var tracking by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val style = theme.overrides[key.id] ?: KeyStyleOverride()
    val radius = (style.cornerRadiusDp ?: theme.cornerRadiusDp).dp
    val fill = if (style.invisibleFill == true) {
        Color.Transparent
    } else {
        Color((style.fillArgb ?: theme.keyFillArgb).toInt()).copy(alpha = style.fillAlpha ?: 1f)
    }

    Box(
        modifier
            .height((style.heightDp ?: theme.keyHeightDp).coerceIn(34f, 80f).dp)
            .padding(horizontal = 1.dp)
            .background(fill, RoundedCornerShape(radius))
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        tracking = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { tracking = false; accumulated = 0f },
                    onDragCancel = { tracking = false; accumulated = 0f },
                ) { change, drag ->
                    change.consume()
                    accumulated += drag.x
                    val stepPx = 18.dp.toPx()
                    while (abs(accumulated) >= stepPx) {
                        val direction = if (accumulated > 0) 1 else -1
                        if (onCursor(direction)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        accumulated -= direction * stepPx
                    }
                }
            }
            .combinedClickable(onClick = { if (!tracking) onSpace() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (tracking) "cursor" else "",
            color = Color(theme.secondaryLabelArgb.toInt()),
            fontSize = 9.sp,
            modifier = Modifier.alpha(if (tracking) 1f else 0f),
        )
    }
}
