package com.night.keyboard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Look, typing, feel, privacy, and the three focused online tools.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingsSection("Look") { ToggleRow("Number row", "Optional row above QWERTY; the 123 mode key is always present.", state.numberRow, viewModel::numberRow); ToggleRow("Secondary characters", "Show long-press symbols on letter keys.", state.secondaryCharacters, viewModel::secondary) } }
        item { SettingsSection("Typing") { ToggleRow("Autocorrect", "Corrections will stay on-device when the prediction engine lands.", state.autocorrect, viewModel::autocorrect); ToggleRow("Suggestions", "Show prediction strip above the keyboard.", state.suggestions, viewModel::suggestions); Text("Autocorrect aggression and swipe typing are intentionally not faked yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp)) } }
        item { SettingsSection("Feel") { ToggleRow("Haptic feedback", "Short vibration on key press.", state.haptics, viewModel::haptics); Text("Custom click sound recording and trail tuning will come after the IME input path is stable.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp)) } }
        item {
            SettingsSection("Editor · Tone · Contextual Research") {
                OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, modifier = Modifier.fillMaxWidth().padding(12.dp), label = { Text("Your server URL") }, placeholder = { Text("https://your-server.example") }, supportingText = { Text("Only these selected tools may send text online. The keyboard does not send normal typing.") }, singleLine = true)
                Button(onClick = { viewModel.serverUrl(serverUrl) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { Text("Save server URL") }
                Text("Editor fixes writing. Tone rewrites style. Contextual Research gathers relevant background. No generic chatbot surface is planned.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
            }
        }
        item { SettingsSection("Privacy") { Text("Password/payment fields will suppress learning, clipboard capture, and online tools. Clipboard retention is configurable per item and pinned clips do not expire.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp)) } }
    }
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp)); Card(shape = RoundedCornerShape(18.dp)) { Column(content = content) } }
}

@Composable private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
