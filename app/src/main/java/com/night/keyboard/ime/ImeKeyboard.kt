@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.night.keyboard.ime

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.keyboard.data.prefs.KeyboardPreferenceState
import com.night.keyboard.data.prefs.OneHandedMode
import com.night.keyboard.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class ToolPanel { NONE, CLIPBOARD, EMOJI, VOICE, EDITOR, TONE, RESEARCH }

@Composable
fun ImeKeyboard(
    controller: KeyboardController,
    themeFlow: Flow<ThemeSnapshot>,
    preferenceFlow: Flow<KeyboardPreferenceState>,
    clipboardFlow: Flow<List<ClipboardItem>>,
    sensitiveFieldFlow: Flow<Boolean>,
) {
    val theme by themeFlow.collectAsState(initial = ThemeSnapshot())
    val prefs by preferenceFlow.collectAsState(initial = KeyboardPreferenceState())
    val clips by clipboardFlow.collectAsState(initial = emptyList())
    val sensitiveField by sensitiveFieldFlow.collectAsState(initial = false)
    val privateMode = sensitiveField || prefs.incognito

    var layer by remember { mutableStateOf(KeyboardLayer.LETTERS) }
    var shift by remember { mutableStateOf(ShiftState.OFF) }
    var panel by remember { mutableStateOf(ToolPanel.NONE) }
    var textVersion by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf(listOf("I’m", "the", "thank you")) }
    var lastCorrection by remember { mutableStateOf<Autocorrection?>(null) }

    fun textChanged(clearCorrection: Boolean = true) {
        if (clearCorrection) lastCorrection = null
        textVersion++
    }

    LaunchedEffect(textVersion, privateMode) {
        suggestions = if (privateMode) emptyList() else SuggestionEngine.suggest(controller.textBeforeCursor())
    }

    val widthFraction = if (prefs.oneHandedMode == OneHandedMode.OFF) 1f else .82f
    val alignment = when (prefs.oneHandedMode) {
        OneHandedMode.OFF -> Alignment.Center
        OneHandedMode.LEFT -> Alignment.CenterStart
        OneHandedMode.RIGHT -> Alignment.CenterEnd
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = Color(theme.backgroundArgb.toInt()),
            modifier = Modifier.fillMaxWidth(widthFraction),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 5.dp)) {
                Toolbar(
                    panel = panel,
                    privateMode = privateMode,
                    onPanel = { panel = if (panel == it) ToolPanel.NONE else it },
                    controller = controller,
                )

                if (panel != ToolPanel.NONE) {
                    ToolPanelContent(
                        panel = panel,
                        clips = if (privateMode) emptyList() else clips,
                        serverUrl = prefs.serverUrl,
                        privateMode = privateMode,
                        controller = controller,
                        onCommitted = { textChanged() },
                    )
                }

                if (prefs.suggestions && !privateMode && panel == ToolPanel.NONE) {
                    SuggestionStrip(
                        suggestions = suggestions,
                        correction = lastCorrection,
                        onSuggestion = {
                            controller.replaceCurrentWord(it)
                            controller.commit(" ")
                            textChanged()
                        },
                        onUndoCorrection = { correction ->
                            if (controller.undoAutocorrect(correction)) {
                                lastCorrection = null
                                textChanged(clearCorrection = false)
                            }
                        },
                    )
                }

                if (prefs.numberRow && layer == KeyboardLayer.LETTERS) {
                    NumberRow(
                        controller = controller,
                        onTextChanged = { textChanged() },
                        theme = theme,
                        hapticsEnabled = prefs.haptics,
                    )
                }

                KeyboardRows(
                    rows = KeyboardLayoutFactory.rows(layer),
                    layer = layer,
                    shift = shift,
                    theme = theme,
                    secondaryVisible = prefs.secondaryCharacters,
                    hapticsEnabled = prefs.haptics,
                    controller = controller,
                    onLayer = { layer = it; panel = ToolPanel.NONE; lastCorrection = null },
                    onShift = { shift = it },
                    onOpenEmoji = {
                        panel = if (panel == ToolPanel.EMOJI) ToolPanel.NONE else ToolPanel.EMOJI
                    },
                    onSpace = {
                        val correction = if (prefs.autocorrect && !privateMode) {
                            SuggestionEngine.autocorrect(
                                controller.currentWord(),
                                prefs.autocorrectAggression,
                            )
                        } else {
                            null
                        }
                        if (correction != null) {
                            controller.replaceCurrentWord(correction.replacement)
                        }
                        controller.commit(" ")
                        lastCorrection = correction
                        textChanged(clearCorrection = false)
                    },
                    onTextChanged = { textChanged() },
                )
            }
        }
    }
}

@Composable
private fun Toolbar(
    panel: ToolPanel,
    privateMode: Boolean,
    onPanel: (ToolPanel) -> Unit,
    controller: KeyboardController,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(KeyboardIcons.Clipboard, "Clipboard", panel == ToolPanel.CLIPBOARD) {
            onPanel(ToolPanel.CLIPBOARD)
        }
        ToolButton(KeyboardIcons.Emoji, "Emoji", panel == ToolPanel.EMOJI) {
            onPanel(ToolPanel.EMOJI)
        }
        ToolButton(KeyboardIcons.Voice, "Voice input", panel == ToolPanel.VOICE) {
            onPanel(ToolPanel.VOICE)
        }
        ToolButton(KeyboardIcons.Editor, "Editor", panel == ToolPanel.EDITOR) {
            onPanel(ToolPanel.EDITOR)
        }
        ToolButton(KeyboardIcons.Tone, "Tone", panel == ToolPanel.TONE) {
            onPanel(ToolPanel.TONE)
        }
        ToolButton(KeyboardIcons.Research, "Research", panel == ToolPanel.RESEARCH) {
            onPanel(ToolPanel.RESEARCH)
        }
        Spacer(Modifier.width(2.dp))
        ToolButton(KeyboardIcons.InputPicker, "Input picker", false) {
            controller.showInputPicker()
        }
        if (privateMode) {
            Text(
                "Private",
                color = Color(0xFF9AA2AA),
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 5.dp),
            )
        }
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
    privateMode: Boolean,
    controller: KeyboardController,
    onCommitted: () -> Unit,
) {
    Surface(
        color = Color(0xFF0B0E11),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        if (
            privateMode &&
            panel in setOf(
                ToolPanel.CLIPBOARD,
                ToolPanel.VOICE,
                ToolPanel.EDITOR,
                ToolPanel.TONE,
                ToolPanel.RESEARCH,
            )
        ) {
            StatusPanel(
                "Private mode",
                "Clipboard history, suggestions, autocorrect, voice and online writing tools are disabled for this field.",
            )
            return@Surface
        }

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
                    Text(
                        "Clipboard is empty",
                        color = Color(0xFF8F99A4),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(9.dp),
                    )
                }
            }

            ToolPanel.EMOJI -> EmojiPanel(controller, onCommitted)

            ToolPanel.VOICE -> StatusPanel(
                "Voice input",
                if (serverUrl.isBlank()) {
                    "Set your HTTPS Whisper server URL in the app before voice input is enabled."
                } else {
                    "Server configured. Microphone capture remains local until you explicitly start a voice request."
                },
            )

            ToolPanel.EDITOR -> OnlineToolPanel(
                tool = OnlineKeyboardTool.EDITOR,
                title = "Editor",
                message = "Fix spelling, grammar and punctuation without changing meaning.",
                serverUrl = serverUrl,
                controller = controller,
                onCommitted = onCommitted,
            )

            ToolPanel.TONE -> OnlineToolPanel(
                tool = OnlineKeyboardTool.TONE,
                title = "Tone",
                message = "Rewrite selected text with a chosen tone.",
                serverUrl = serverUrl,
                controller = controller,
                onCommitted = onCommitted,
            )

            ToolPanel.RESEARCH -> OnlineToolPanel(
                tool = OnlineKeyboardTool.RESEARCH,
                title = "Contextual Research",
                message = "Research the selected text or current context without adding a generic chatbot.",
                serverUrl = serverUrl,
                controller = controller,
                onCommitted = onCommitted,
            )

            ToolPanel.NONE -> Unit
        }
    }
}

@Composable
private fun StatusPanel(title: String, message: String) {
    Column(Modifier.padding(11.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(
            message,
            color = Color(0xFF8F99A4),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun OnlineToolPanel(
    tool: OnlineKeyboardTool,
    title: String,
    message: String,
    serverUrl: String,
    controller: KeyboardController,
    onCommitted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember(tool, serverUrl) { mutableStateOf(false) }
    var result by remember(tool, serverUrl) { mutableStateOf("") }
    var error by remember(tool, serverUrl) { mutableStateOf("") }
    var tone by remember(tool) { mutableStateOf("Friendly") }

    val selected = controller.selectedText()
    val context = selected.ifBlank { controller.textBeforeCursor() }.takeLast(800)

    Column(Modifier.padding(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                if (serverUrl.isBlank()) "Not configured" else "Ready",
                color = if (serverUrl.isBlank()) Color(0xFFE4B661) else Color(0xFF78D69C),
                fontSize = 9.sp,
            )
        }
        Text(
            message,
            color = Color(0xFF8F99A4),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 3.dp),
        )

        if (tool == OnlineKeyboardTool.TONE) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf("Friendly", "Professional", "Concise", "Casual").forEach { option ->
                    FilterChip(
                        selected = tone == option,
                        onClick = { tone = option },
                        label = { Text(option, fontSize = 9.sp) },
                    )
                }
            }
        }

        if (context.isNotBlank()) {
            Text(
                if (selected.isNotBlank()) "Selected: ${selected.take(100)}" else "Context: ${context.takeLast(100)}",
                color = Color(0xFFC9D1D9),
                fontSize = 10.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = Color(0xFFFF9C9C),
                fontSize = 9.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (result.isNotBlank()) {
            Text(
                result,
                color = Color(0xFFF2F3F5),
                fontSize = 10.sp,
                maxLines = 5,
                modifier = Modifier.padding(top = 7.dp),
            )
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (tool == OnlineKeyboardTool.RESEARCH) {
                    OutlinedButton(
                        onClick = {
                            controller.commit(result)
                            onCommitted()
                        },
                    ) { Text("Insert", fontSize = 10.sp) }
                } else {
                    OutlinedButton(
                        onClick = {
                            controller.replaceSelectionOrCurrentWord(result)
                            onCommitted()
                        },
                    ) { Text("Replace", fontSize = 10.sp) }
                }
                TextButton(onClick = { result = ""; error = "" }) {
                    Text("Clear", fontSize = 10.sp)
                }
            }
        } else {
            Button(
                enabled = !loading && serverUrl.isNotBlank() && context.isNotBlank(),
                onClick = {
                    loading = true
                    error = ""
                    scope.launch {
                        KeyboardOnlineClient(serverUrl)
                            .run(
                                tool = tool,
                                text = context,
                                tone = if (tool == OnlineKeyboardTool.TONE) tone else null,
                            )
                            .onSuccess { result = it }
                            .onFailure { error = it.message ?: "Request failed." }
                        loading = false
                    }
                },
                modifier = Modifier.padding(top = 7.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (selected.isNotBlank()) "Send selected text" else "Send current context",
                    fontSize = 10.sp,
                )
            }
            Text(
                "Nothing is sent until you tap Send.",
                color = Color(0xFF78828C),
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EmojiPanel(controller: KeyboardController, onCommitted: () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        KeyboardEmojiSamples.forEach { entry ->
            Surface(
                onClick = { controller.commit(entry.output); onCommitted() },
                color = Color.Transparent,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.semantics { contentDescription = entry.description },
            ) {
                Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                    KeyboardEmojiArtwork(entry.art, Modifier.size(35.dp))
                }
            }
        }
    }
}

@Composable
private fun SuggestionStrip(
    suggestions: List<String>,
    correction: Autocorrection?,
    onSuggestion: (String) -> Unit,
    onUndoCorrection: (Autocorrection) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
        if (correction != null) {
            Box(
                Modifier.weight(1f).height(41.dp).combinedClickable(
                    onClick = { onUndoCorrection(correction) },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "↶ ${correction.original}",
                    color = Color(0xFFBFD7FF),
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
        suggestions.take(if (correction == null) 3 else 2).forEach { suggestion ->
            Box(
                Modifier.weight(1f).height(41.dp).combinedClickable(
                    onClick = { onSuggestion(suggestion) },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(suggestion, color = Color(0xFFF2F3F5), fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NumberRow(
    controller: KeyboardController,
    onTextChanged: () -> Unit,
    theme: ThemeSnapshot,
    hapticsEnabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(theme.horizontalGapDp.dp)) {
        "1234567890".forEach { c ->
            val id = "number_$c"
            val widthScale = theme.overrides[id]?.widthScale ?: 1f
            ImeKey(
                key = KeySpec(id, c.toString(), output = c.toString()),
                displayLabel = c.toString(),
                theme = theme,
                secondaryVisible = false,
                hapticsEnabled = hapticsEnabled,
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
    hapticsEnabled: Boolean,
    controller: KeyboardController,
    onLayer: (KeyboardLayer) -> Unit,
    onShift: (ShiftState) -> Unit,
    onOpenEmoji: () -> Unit,
    onSpace: () -> Unit,
    onTextChanged: () -> Unit,
) {
    var lastShiftTapAt by remember { mutableLongStateOf(0L) }

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
                ) {
                    key.label.uppercase()
                } else {
                    key.label
                }
                val widthScale = theme.overrides[key.id]?.widthScale ?: 1f
                val effectiveWeight = (key.weight * widthScale).coerceAtLeast(.2f)

                when (key.special) {
                    SpecialKey.SPACE -> SpacebarKey(
                        key = key,
                        theme = theme,
                        modifier = Modifier.weight(effectiveWeight),
                        hapticsEnabled = hapticsEnabled,
                        onSpace = onSpace,
                        onCursor = { controller.moveCursor(it) },
                    )

                    SpecialKey.BACKSPACE -> RepeatBackspaceKey(
                        key = key,
                        theme = theme,
                        modifier = Modifier.weight(effectiveWeight),
                        hapticsEnabled = hapticsEnabled,
                        onBackspace = { controller.backspace(); onTextChanged() },
                    )

                    else -> ImeKey(
                        key = key,
                        displayLabel = display,
                        theme = theme,
                        secondaryVisible = secondaryVisible,
                        hapticsEnabled = hapticsEnabled,
                        modifier = Modifier.weight(effectiveWeight),
                        onClick = {
                            when (key.special) {
                                SpecialKey.SHIFT -> {
                                    val now = SystemClock.uptimeMillis()
                                    val doubleTapWindow = ViewConfiguration.getDoubleTapTimeout().toLong()
                                    val isSecondTap = shift == ShiftState.ONCE &&
                                        lastShiftTapAt > 0L &&
                                        now - lastShiftTapAt <= doubleTapWindow
                                    if (isSecondTap) {
                                        lastShiftTapAt = 0L
                                        onShift(ShiftState.LOCKED)
                                    } else {
                                        lastShiftTapAt = if (shift == ShiftState.OFF) now else 0L
                                        onShift(if (shift == ShiftState.OFF) ShiftState.ONCE else ShiftState.OFF)
                                    }
                                }

                                SpecialKey.ENTER -> {
                                    controller.enter()
                                    onTextChanged()
                                }

                                SpecialKey.EMOJI -> onOpenEmoji()
                                SpecialKey.NUMBERS -> onLayer(KeyboardLayer.SYMBOLS)
                                SpecialKey.LETTERS -> onLayer(KeyboardLayer.LETTERS)
                                SpecialKey.MORE_SYMBOLS -> onLayer(KeyboardLayer.SYMBOLS_MORE)
                                SpecialKey.LESS_SYMBOLS -> onLayer(KeyboardLayer.SYMBOLS)
                                SpecialKey.BACKSPACE, SpecialKey.SPACE -> Unit
                                else -> rawOutput?.let { raw ->
                                    val output = if (
                                        layer == KeyboardLayer.LETTERS &&
                                        raw.length == 1 &&
                                        raw[0].isLetter() &&
                                        shift != ShiftState.OFF
                                    ) {
                                        raw.uppercase()
                                    } else {
                                        raw
                                    }
                                    controller.commit(output)
                                    if (shift == ShiftState.ONCE) onShift(ShiftState.OFF)
                                    onTextChanged()
                                }
                            }
                        },
                        onLongClick = if (secondary != null && key.special == null) {
                            { controller.commit(secondary); onTextChanged() }
                        } else {
                            null
                        },
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
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
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
    val borderWidth = if (borderEnabled) {
        (style.borderWidthDp ?: theme.borderWidthDp).dp
    } else {
        0.dp
    }
    val secondary = key.secondary
    val specialIcon = when (key.special) {
        SpecialKey.SHIFT -> KeyboardIcons.Shift
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
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(radius))
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                },
                onLongClick = onLongClick?.let { longClick ->
                    {
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
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
    hapticsEnabled: Boolean,
    onSpace: () -> Unit,
    onCursor: (Int) -> Boolean,
) {
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
            .semantics { contentDescription = "Spacebar" }
            .pointerInput(onSpace, onCursor, hapticsEnabled) {
                val stepPx = 18.dp.toPx()
                val longPressMillis = viewConfiguration.longPressTimeoutMillis
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val downTime = down.uptimeMillis
                    val startX = down.position.x
                    val startY = down.position.y
                    var previousX = down.position.x
                    var accumulated = 0f
                    var trackpadActive = false
                    var maxMovement = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        val elapsed = change.uptimeMillis - downTime
                        maxMovement = maxOf(
                            maxMovement,
                            abs(change.position.x - startX),
                            abs(change.position.y - startY),
                        )

                        val dx = change.position.x - previousX
                        if (!trackpadActive && elapsed >= longPressMillis && change.pressed) {
                            trackpadActive = true
                            tracking = true
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }

                        if (trackpadActive && change.pressed) {
                            accumulated += dx
                            while (abs(accumulated) >= stepPx) {
                                val direction = if (accumulated > 0f) 1 else -1
                                if (onCursor(direction) && hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                accumulated -= direction * stepPx
                            }
                            change.consume()
                        }

                        previousX = change.position.x
                        if (!change.pressed) {
                            if (!trackpadActive && elapsed < longPressMillis && maxMovement <= touchSlop) {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSpace()
                            }
                            break
                        }
                    }

                    tracking = false
                }
            },
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
