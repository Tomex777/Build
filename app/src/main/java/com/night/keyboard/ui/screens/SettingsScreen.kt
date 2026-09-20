package com.night.keyboard.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.night.keyboard.data.prefs.OneHandedMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }

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
