package com.night.keyboard.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.night.keyboard.ime.KeyboardInputMethodService

private data class SetupStatus(val enabled: Boolean, val selected: Boolean) { val complete get() = enabled && selected }

@Composable
fun HomeScreen(onOpenEditor: () -> Unit, onOpenClipboard: () -> Unit) {
    val context = LocalContext.current
    var setup by remember { mutableStateOf(readSetupStatus(context)) }
    LaunchedEffect(Unit) { setup = readSetupStatus(context) }
    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Keyboard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text("Fast typing, deep per-key customization, a serious clipboard, and focused writing tools.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!setup.complete) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Finish setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("This card disappears once Keyboard is enabled and selected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SetupRow("Enable Keyboard", setup.enabled) { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        SetupRow("Select Keyboard", setup.selected) { context.getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
                        OutlinedButton(onClick = { setup = readSetupStatus(context) }, modifier = Modifier.fillMaxWidth()) { Text("Refresh status") }
                    }
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = androidx.compose.ui.graphics.Color(0xFF78D69C)); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text("Keyboard is active", fontWeight = FontWeight.Bold); Text("Setup complete", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item { Button(onClick = { context.getSystemService(InputMethodManager::class.java).showInputMethodPicker() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Keyboard, null); Spacer(Modifier.width(8.dp)); Text("Open input picker") } }
        item { Text("Quick access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("Per-key editor", "Select keys, style them, keep tweaking.", Icons.Outlined.Palette, Modifier.weight(1f), onOpenEditor)
                QuickCard("Clipboard", "Search, pin, reorder, expire, undo.", Icons.Outlined.ContentPaste, Modifier.weight(1f), onOpenClipboard)
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PrivacyTip, null); Spacer(Modifier.width(11.dp)); Column { Text("Privacy first", fontWeight = FontWeight.Bold); Text("Sensitive fields are excluded from learning and clipboard capture.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable private fun SetupRow(title: String, done: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Keyboard, null, tint = if (done) androidx.compose.ui.graphics.Color(0xFF78D69C) else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp)); Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        if (!done) OutlinedButton(onClick = onClick) { Text(if (title.startsWith("Enable")) "Enable" else "Select") }
    }
}

@Composable private fun QuickCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { Icon(icon, null); Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
}

private fun readSetupStatus(context: Context): SetupStatus {
    val imm = context.getSystemService(InputMethodManager::class.java)
    val component = ComponentName(context, KeyboardInputMethodService::class.java).flattenToShortString()
    val enabled = imm.enabledInputMethodList.any { info -> ComponentName(info.packageName, info.serviceName).flattenToShortString() == component }
    val selected = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == component
    return SetupStatus(enabled, selected)
}
