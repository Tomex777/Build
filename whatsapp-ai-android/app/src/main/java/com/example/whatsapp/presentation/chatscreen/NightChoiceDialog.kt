package com.example.whatsapp.presentation.chatscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ChoiceBg = Color(0xFF151B1E)
private val ChoicePanel = Color(0xFF20272A)
private val ChoiceText = Color(0xFFECEDEE)
private val ChoiceMuted = Color(0xFF9EA7AB)

@Composable
fun NightChoiceDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, options: List<String>, multiple: Boolean) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    var title by remember { mutableStateOf("") }
    var multiple by remember { mutableStateOf(false) }
    val options = remember { mutableStateListOf("", "") }

    fun move(from: Int, to: Int) {
        if (from !in options.indices || to !in options.indices || from == to) return
        val value = options.removeAt(from)
        options.add(to, value)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ChoiceBg,
        title = {
            Column {
                Text("Options", color = ChoiceText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Create a question Night can answer with you.",
                    color = ChoiceMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Question", color = ChoiceText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                ChoiceInput(
                    value = title,
                    onValueChange = { title = it.take(180) },
                    placeholder = "What do you want to choose?",
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !multiple,
                        onClick = { multiple = false },
                        label = { Text("Single choice") },
                    )
                    FilterChip(
                        selected = multiple,
                        onClick = { multiple = true },
                        label = { Text("Multiple choice") },
                    )
                }

                Text("Options", color = ChoiceText, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                options.forEachIndexed { index, value ->
                    Surface(
                        color = ChoicePanel,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = accent.copy(alpha = 0.18f),
                                shape = CircleShape,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        (index + 1).toString(),
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(Modifier.width(9.dp))
                            BasicTextField(
                                value = value,
                                onValueChange = { options[index] = it.take(100) },
                                singleLine = true,
                                textStyle = TextStyle(color = ChoiceText, fontSize = 14.sp),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box {
                                        if (value.isBlank()) {
                                            Text("Option " + (index + 1), color = ChoiceMuted, fontSize = 14.sp)
                                        }
                                        inner()
                                    }
                                },
                            )
                            IconButton(
                                onClick = { move(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.Default.ArrowUpward, "Move option up", tint = ChoiceMuted, modifier = Modifier.size(17.dp))
                            }
                            IconButton(
                                onClick = { move(index, index + 1) },
                                enabled = index < options.lastIndex,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.Default.ArrowDownward, "Move option down", tint = ChoiceMuted, modifier = Modifier.size(17.dp))
                            }
                            IconButton(
                                onClick = { if (options.size > 2) options.removeAt(index) },
                                enabled = options.size > 2,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.Default.Delete, "Remove option", tint = Color(0xFFFF7C8D), modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { if (options.size < 8) options.add("") },
                    enabled = options.size < 8,
                ) {
                    Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add option", color = accent)
                }

                Text(
                    if (multiple) "People can select more than one option." else "One option can be selected.",
                    color = ChoiceMuted,
                    fontSize = 10.sp,
                )
            }
        },
        confirmButton = {
            val clean = options.map { it.trim() }.filter { it.isNotBlank() }
            Button(
                onClick = { onCreate(title.trim(), clean, multiple) },
                enabled = title.trim().isNotBlank() && clean.size >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF10161A),
                ),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ChoiceMuted)
            }
        },
    )
}

@Composable
private fun ChoiceInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Surface(
        color = ChoicePanel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = ChoiceText, fontSize = 15.sp, lineHeight = 20.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) {
                        Text(placeholder, color = ChoiceMuted, fontSize = 15.sp)
                    }
                    inner()
                }
            },
        )
    }
}
