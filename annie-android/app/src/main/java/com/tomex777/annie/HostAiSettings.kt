package com.tomex777.annie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun HostAiSettings(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(HostAiConfig.model(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSavedKey by remember { mutableStateOf(!HostAiConfig.key(context).isNullOrBlank()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Host AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use your Anthropic API key. Packages need your AI permission to send prompts to the provider. Provider usage may cost money.")
                AnnieSettingsInput(
                    value = key,
                    onValueChange = { key = it; error = null },
                    label = if (hasSavedKey) "API key · configured" else "API key",
                    secret = true,
                    tag = "host_ai_key_input",
                )
                AnnieSettingsInput(
                    value = model,
                    onValueChange = { model = it; error = null },
                    label = "Model ID",
                    tag = "host_ai_model_input",
                )
                error?.let { Text(it, color = Color(0xFFFF9B91)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    HostAiConfig.save(context, key.ifBlank { HostAiConfig.key(context).orEmpty() }, model)
                }.onSuccess { onDismiss() }.onFailure { error = it.message ?: "Could not save settings" }
            }) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasSavedKey) TextButton(onClick = {
                    runCatching { HostAiConfig.save(context, "", HostAiConfig.model(context)) }
                        .onSuccess { hasSavedKey = false; key = ""; onDismiss() }
                        .onFailure { error = it.message ?: "Could not remove key" }
                }) { Text("Remove key") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
