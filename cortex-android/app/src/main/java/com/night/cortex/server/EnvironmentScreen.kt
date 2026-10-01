package com.night.cortex.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.cortex.ui.theme.CortexAccent
import com.night.cortex.ui.theme.CortexDanger
import com.night.cortex.ui.theme.CortexLine
import com.night.cortex.ui.theme.CortexMuted
import com.night.cortex.ui.theme.CortexSurface
import com.night.cortex.ui.theme.CortexSurface2
import com.night.cortex.ui.theme.CortexText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnvironmentPage(
    state: ServerPanelState,
    onReveal: (String) -> Unit,
    onHide: (String) -> Unit,
    onSave: (String, String) -> Unit,
) {
    val environment = state.environment
    var editing by remember { mutableStateOf<EnvironmentVariable?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("environment-screen-root")
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("Environment", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Managed MSCC variables",
                color = CortexMuted,
                fontSize = 10.sp,
            )
        }
        HorizontalDivider(color = CortexLine)

        if (environment == null) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Environment unavailable", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    "The connected Cortex Agent does not expose managed environment settings.",
                    color = CortexMuted,
                    fontSize = 10.sp,
                )
            }
        } else if (environment.entries.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("No managed variables", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Only server-whitelisted MSCC variables appear here.",
                    color = CortexMuted,
                    fontSize = 10.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (environment.restartRequired) {
                    item {
                        Surface(
                            color = CortexSurface2,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "Restart MSCC to apply the latest environment change.",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                color = CortexText,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                items(environment.entries, key = { it.key }) { entry ->
                    val revealed = state.revealedEnvironment[entry.key]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.loading) { editing = entry }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    entry.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (entry.requiresRestart) {
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        "RESTART",
                                        color = CortexMuted,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                entry.key,
                                color = CortexMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            if (entry.description.isNotBlank()) {
                                Text(
                                    entry.description,
                                    color = CortexMuted,
                                    fontSize = 9.sp,
                                    maxLines = 2,
                                )
                            }
                            Text(
                                when {
                                    entry.secret && revealed != null -> revealed.ifBlank { "Not set" }
                                    entry.secret && entry.hasValue -> "••••••••"
                                    entry.secret -> "Not set"
                                    entry.value.isBlank() -> "Not set"
                                    else -> entry.value
                                },
                                color = if (entry.secret && revealed != null) CortexText else CortexMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                        }

                        if (entry.secret && entry.hasValue) {
                            TextButton(
                                onClick = {
                                    if (revealed == null) onReveal(entry.key) else onHide(entry.key)
                                },
                                enabled = !state.loading,
                            ) {
                                Icon(
                                    if (revealed == null) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    null,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (revealed == null) "Reveal" else "Hide", fontSize = 9.sp)
                            }
                        }
                        IconButton(
                            onClick = { editing = entry },
                            enabled = !state.loading,
                        ) {
                            Icon(Icons.Rounded.Edit, "Edit ${entry.label}", tint = CortexAccent)
                        }
                    }
                    HorizontalDivider(color = CortexLine)
                }
            }
        }
    }

    editing?.let { entry ->
        val revealed = state.revealedEnvironment[entry.key]
        EnvironmentEditSheet(
            entry = entry,
            initialValue = if (entry.secret) revealed.orEmpty() else entry.value,
            busy = state.loading,
            onDismiss = { editing = null },
            onSave = { value ->
                onSave(entry.key, value)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnvironmentEditSheet(
    entry: EnvironmentVariable,
    initialValue: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(entry.key, initialValue) { mutableStateOf(initialValue) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding(),
        containerColor = CortexSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Edit ${entry.label}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(entry.key, color = CortexMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            if (entry.secret && initialValue.isBlank() && entry.hasValue) {
                Text(
                    "The saved value is masked. Enter a replacement, or reveal it from the Environment screen first.",
                    color = CortexMuted,
                    fontSize = 10.sp,
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = { if (!it.contains('\n') && it.length <= 4096) value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Value") },
                singleLine = true,
                visualTransformation = if (entry.secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            )
            if (entry.requiresRestart) {
                Text(
                    "MSCC restart required after saving.",
                    color = CortexMuted,
                    fontSize = 9.sp,
                )
            }
            Button(
                onClick = { onSave(value) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Save")
            }
            if (entry.secret && value.isBlank() && entry.hasValue) {
                Text(
                    "Saving a blank value clears this variable.",
                    color = CortexDanger,
                    fontSize = 9.sp,
                )
            }
        }
    }
}
