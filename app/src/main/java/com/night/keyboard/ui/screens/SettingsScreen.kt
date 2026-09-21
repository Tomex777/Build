package com.night.keyboard.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.night.keyboard.data.prefs.OneHandedMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> microphoneGranted = granted }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                "Look, typing, feel, privacy, and the three focused online tools.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SettingsSection("Look") {
                ToggleRow(
                    "Number row",
                    "Optional row above QWERTY; the 123 mode key is always present.",
                    state.numberRow,
                    viewModel::numberRow,
                )
                ToggleRow(
                    "Secondary characters",
                    "Show long-press symbols on letter keys.",
                    state.secondaryCharacters,
                    viewModel::secondary,
                )
                Text(
                    "One-handed keyboard",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OneHandedMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.oneHandedMode == mode,
                            onClick = { viewModel.oneHanded(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        OneHandedMode.OFF -> "Full width"
                                        OneHandedMode.LEFT -> "Left"
                                        OneHandedMode.RIGHT -> "Right"
                                    },
                                )
                            },
                        )
                    }
                }
                ToggleRow(
                    "Floating keyboard",
                    "Use a narrower keyboard lifted above the bottom edge.",
                    state.floatingKeyboard,
                    viewModel::floatingKeyboard,
                )
                if (state.floatingKeyboard) {
                    Text(
                        "Floating width ${state.floatingWidthPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                    Slider(
                        value = state.floatingWidthPercent.toFloat(),
                        onValueChange = { viewModel.floatingWidth(it.toInt()) },
                        valueRange = 60f..96f,
                        steps = 17,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    Text(
                        "Bottom lift ${state.floatingLiftDp} dp",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                    Slider(
                        value = state.floatingLiftDp.toFloat(),
                        onValueChange = { viewModel.floatingLift(it.toInt()) },
                        valueRange = 0f..96f,
                        steps = 11,
                        modifier = Modifier.padding(horizontal = 14.dp, bottom = 8.dp),
                    )
                }
            }
        }

        item {
            SettingsSection("Typing") {
                ToggleRow(
                    "Autocorrect",
                    "Correct likely misspellings locally when you press Space.",
                    state.autocorrect,
                    viewModel::autocorrect,
                )
                if (state.autocorrect) {
                    Text(
                        "Autocorrect strength",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1 to "Low", 2 to "Medium", 3 to "High").forEach { (value, label) ->
                            FilterChip(
                                selected = state.autocorrectAggression == value,
                                onClick = { viewModel.autocorrectAggression(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                ToggleRow(
                    "Suggestions",
                    "Show local word predictions above the keyboard.",
                    state.suggestions,
                    viewModel::suggestions,
                )
                ToggleRow(
                    "Swipe typing",
                    "Glide across letters to enter a word without lifting your finger.",
                    state.swipeTyping,
                    viewModel::swipeTyping,
                )
                if (state.swipeTyping) {
                    ToggleRow(
                        "Swipe trail",
                        "Draw a short live trail while gliding across the letter keys.",
                        state.swipeTrail,
                        viewModel::swipeTrail,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Personal dictionary", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Words you type are learned only on-device and never in private fields.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = viewModel::clearLearnedWords) {
                        Text("Clear")
                    }
                }
            }
        }

        item {
            SettingsSection("Feel") {
                ToggleRow(
                    "Haptic feedback",
                    "Use vibration cues for keys, long-press symbols, Backspace and cursor tracking.",
                    state.haptics,
                    viewModel::haptics,
                )
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Microphone", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (microphoneGranted) {
                                "Granted. Voice input still records and uploads only after explicit taps."
                            } else {
                                "Required only for the Voice toolbar tool."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!microphoneGranted) {
                        OutlinedButton(
                            onClick = {
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                        ) { Text("Allow") }
                    } else {
                        Text("Allowed", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            SettingsSection("Editor · Tone · Contextual Research") {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    label = { Text("Your HTTPS server URL") },
                    placeholder = { Text("https://your-server.example") },
                    supportingText = {
                        Text(
                            "Text is sent only after you explicitly tap Send inside Editor, Tone or Contextual Research.",
                        )
                    },
                    singleLine = true,
                )
                Button(
                    onClick = { viewModel.serverUrl(serverUrl) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Save server URL")
                }
                Text(
                    "Editor fixes writing. Tone rewrites style. Contextual Research gathers relevant background. Normal typing is never sent to this server.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        item {
            SettingsSection("Privacy") {
                ToggleRow(
                    "Incognito mode",
                    "Disable clipboard capture, suggestions, autocorrect and online writing tools until you turn this off.",
                    state.incognito,
                    viewModel::incognito,
                )
                Text(
                    "Password, PIN, OTP, security-code and payment-like fields automatically use the same private behavior even when Incognito is off.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
        )
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(content = content)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
