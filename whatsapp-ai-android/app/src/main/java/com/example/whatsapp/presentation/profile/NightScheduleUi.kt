package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightScheduledTaskEntity
import java.text.DateFormat
import java.util.Date

private val ScheduleBg = Color(0xFF0B0F11)
private val ScheduleText = Color(0xFFE7EAEC)
private val ScheduleMuted = Color(0xFF9CA5A9)
private val ScheduleAccent = Color(0xFF21C063)

@Composable
fun NightScheduleDialog(
    initialPrompt: String,
    onDismiss: () -> Unit,
    onSchedule: (prompt: String, delayMs: Long, repeatMinutes: Long?) -> Unit,
) {
    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var delayMs by remember { mutableStateOf(60 * 60 * 1000L) }
    var repeatMinutes by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = { Text("Schedule with Night", color = ScheduleText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("What should Night do?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = ScheduleText,
                        unfocusedTextColor = ScheduleText,
                        cursorColor = ScheduleAccent,
                    ),
                )

                Text("When", color = ScheduleText, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "10 min" to 10 * 60 * 1000L,
                        "1 hour" to 60 * 60 * 1000L,
                        "6 hours" to 6 * 60 * 60 * 1000L,
                        "Tomorrow" to 24 * 60 * 60 * 1000L,
                    ).forEach { (label, value) ->
                        FilterChip(
                            selected = delayMs == value,
                            onClick = { delayMs = value },
                            label = { Text(label) },
                        )
                    }
                }

                Text("Repeat", color = ScheduleText, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Once" to null,
                        "Daily" to 24L * 60L,
                        "Weekly" to 7L * 24L * 60L,
                    ).forEach { (label, value) ->
                        FilterChip(
                            selected = repeatMinutes == value,
                            onClick = { repeatMinutes = value },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSchedule(prompt.trim(), delayMs, repeatMinutes) },
                enabled = prompt.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScheduleAccent,
                    contentColor = Color(0xFF07110B),
                ),
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ScheduleMuted)
            }
        },
    )
}

@Composable
fun NightScheduledTasksScreen(
    tasks: List<NightScheduledTaskEntity>,
    onBack: () -> Unit,
    onDelete: (NightScheduledTaskEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScheduleBg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ScheduleText)
            }
            Text(
                "Scheduled",
                color = ScheduleText,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f),
            )
        }

        if (tasks.isEmpty()) {
            Text(
                "No scheduled Night tasks.",
                color = ScheduleMuted,
                modifier = Modifier.padding(22.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.prompt,
                                color = ScheduleText,
                                fontSize = 14.sp,
                                maxLines = 2,
                            )
                            Text(
                                DateFormat.getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT,
                                ).format(Date(task.runAt)) +
                                    " • " + task.state +
                                    (task.repeatMinutes?.let { " • repeats" } ?: ""),
                                color = ScheduleMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(task) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete schedule",
                                tint = Color(0xFFFF6B78),
                            )
                        }
                    }
                }
            }
        }
    }
}
