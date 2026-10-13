package com.tomex777.annie

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
internal fun HostAiSettings(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(HostAiConfig.model(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Host AI") }, text = {
        Column {
            Text("Use your Anthropic API key. Packages need your AI permission to send prompts to the provider. Provider usage may cost money.")
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID") }, singleLine = true)
            error?.let { Text(it) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching { HostAiConfig.save(context, key.ifBlank { HostAiConfig.key(context).orEmpty() }, model) }
            .onSuccess { onDismiss() }.onFailure { error = it.message }
    }) { Text("Save") } }, dismissButton = {
        TextButton(onClick = { HostAiConfig.save(context, "", HostAiConfig.model(context)); onDismiss() }) { Text("Remove key") }
    })
}
