package com.tomex777.annie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val AssistPanel = Color(0xFF102139)
private val AssistField = Color(0xFF0A1725)
private val AssistBorder = Color(0xFF294562)
private val AssistText = Color(0xFFEEF5FF)
private val AssistMuted = Color(0xFF9CB2CC)
private val AssistBlue = Color(0xFF42B9F5)
private val AssistDanger = Color(0xFFFF7586)
private val AssistWarning = Color(0xFFFFC86A)

@Composable
internal fun ScriptAssistDialog(
    fileName: String,
    currentSource: String,
    assistant: ScriptAssistant,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onInsert: (String) -> Unit,
    onCreateNew: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers = remember(assistant) { assistant.availableProviders() }
    var providerId by remember { mutableStateOf(providers.firstOrNull()?.id.orEmpty()) }
    var providerMenu by remember { mutableStateOf(false) }
    var instruction by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var proposal by remember { mutableStateOf<String?>(null) }

    val selectedProvider = providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()
    val proposed = proposal
    if (proposed == null) {
        AlertDialog(
            onDismissRequest = { if (!loading) onDismiss() },
            containerColor = AssistPanel,
            titleContentColor = AssistText,
            textContentColor = AssistMuted,
            title = { Text("Script Assist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Describe the script change. Annie sends the current file and scripting contract to the selected provider.",
                        color = AssistMuted,
                        fontSize = 12.sp,
                    )
                    Box {
                        Surface(
                            color = AssistField,
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, AssistBorder),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = providers.size > 1) { providerMenu = true },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(selectedProvider?.displayName ?: "No provider", color = AssistText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                if (providers.size > 1) Text("⌄", color = AssistMuted)
                            }
                        }
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = { providerId = provider.id; providerMenu = false },
                                )
                            }
                        }
                    }
                    AssistTextArea(
                        value = instruction,
                        onValueChange = { instruction = it },
                        hint = "Example: Create a /chess command that sends a native chess board.",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 170.dp),
                    )
                    AssistSecretField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        hint = "Provider API key",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "The key is kept only in this open Assist dialog.",
                        color = AssistMuted,
                        fontSize = 10.sp,
                    )
                    error?.let { Text(it, color = AssistDanger, fontSize = 11.sp) }
                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.width(16.dp), strokeWidth = 2.dp, color = AssistBlue)
                            Text("Generating proposal…", color = AssistMuted, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !loading && instruction.isNotBlank() && apiKey.isNotBlank() && selectedProvider != null,
                    onClick = {
                        val provider = selectedProvider ?: return@TextButton
                        loading = true
                        error = null
                        scope.launch {
                            runCatching {
                                assistant.generate(
                                    providerId = provider.id,
                                    request = ScriptAssistRequest(instruction, fileName, currentSource),
                                    apiKey = apiKey,
                                )
                            }.onSuccess {
                                proposal = it
                            }.onFailure {
                                error = it.message ?: "Script Assist failed"
                            }
                            loading = false
                        }
                    },
                ) { Text("Generate", color = if (loading) AssistMuted else AssistBlue) }
            },
            dismissButton = {
                TextButton(enabled = !loading, onClick = onDismiss) { Text("Cancel", color = AssistMuted) }
            },
        )
    } else {
        val validation = remember(proposed) { ScriptAssistValidator.validate(proposed) }
        val diff = remember(currentSource, proposed) { buildScriptAssistDiff(currentSource, proposed) }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = AssistPanel,
            titleContentColor = AssistText,
            textContentColor = AssistMuted,
            title = { Text("Review proposal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "${selectedProvider?.displayName ?: "Provider"} · $fileName",
                        color = AssistMuted,
                        fontSize = 11.sp,
                    )
                    if (validation.errors.isNotEmpty()) {
                        validation.errors.forEach { Text(it, color = AssistDanger, fontSize = 11.sp) }
                    }
                    if (validation.warnings.isNotEmpty()) {
                        validation.warnings.forEach { Text(it, color = AssistWarning, fontSize = 11.sp) }
                    }
                    Surface(
                        color = AssistField,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AssistBorder),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                    ) {
                        Text(
                            diff,
                            color = AssistText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                                .padding(10.dp).testTag("script_assist_diff"),
                        )
                    }
                    Text(
                        "Nothing is written until you choose an action below.",
                        color = AssistMuted,
                        fontSize = 10.sp,
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        TextButton(enabled = validation.canApply, onClick = { onApply(proposed) }) {
                            Text("Apply", color = if (validation.canApply) AssistBlue else AssistMuted)
                        }
                        TextButton(enabled = validation.canApply, onClick = { onInsert(proposed) }) {
                            Text("Insert", color = if (validation.canApply) AssistBlue else AssistMuted)
                        }
                    }
                    Row {
                        TextButton(enabled = validation.canApply, onClick = { onCreateNew(proposed) }) {
                            Text("Create new file", color = if (validation.canApply) AssistBlue else AssistMuted)
                        }
                        TextButton(onClick = { proposal = null; error = null }) {
                            Text("Back", color = AssistMuted)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = AssistMuted) }
            },
        )
    }
}

@Composable
private fun AssistTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier,
) {
    Surface(
        color = AssistField,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, AssistBorder),
        modifier = modifier,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = AssistText, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(hint, color = AssistMuted, fontSize = 12.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun AssistSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier,
) {
    Surface(
        color = AssistField,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, AssistBorder),
        modifier = modifier,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = AssistText, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(hint, color = AssistMuted, fontSize = 12.sp)
                    inner()
                }
            },
        )
    }
}
