package com.example.whatsapp.presentation.chatscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NightChoiceDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, options: List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    val options = remember {
        mutableStateListOf("", "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = {
            Text(
                text = "Options",
                color = Color(0xFFECEDEE),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    text = "Create choices for you and Night. No votes or percentages.",
                    color = Color(0xFF9EA7AB),
                )

                ChoiceField(
                    value = title,
                    onValueChange = { title = it.take(180) },
                    label = "Question or prompt",
                )

                options.forEachIndexed { index, value ->
                    ChoiceField(
                        value = value,
                        onValueChange = { options[index] = it.take(100) },
                        label = "Option " + (index + 1),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            if (options.size > 2) {
                                options.removeAt(options.lastIndex)
                            }
                        },
                        enabled = options.size > 2,
                    ) {
                        Text("Remove", color = Color(0xFF9EA7AB))
                    }

                    TextButton(
                        onClick = {
                            if (options.size < 6) {
                                options.add("")
                            }
                        },
                        enabled = options.size < 6,
                    ) {
                        Text("Add option", color = Color(0xFFCF4A69))
                    }
                }
            }
        },
        confirmButton = {
            val clean = options.map { it.trim() }.filter { it.isNotBlank() }
            Button(
                onClick = {
                    onCreate(title.trim(), clean)
                },
                enabled = title.trim().isNotBlank() && clean.size >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFCF4A69),
                    contentColor = Color(0xFF10161A),
                ),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9EA7AB))
            }
        },
    )
}

@Composable
private fun ChoiceField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Color(0xFFECEDEE),
            unfocusedTextColor = Color(0xFFECEDEE),
            cursorColor = Color(0xFFCF4A69),
        ),
    )
}
