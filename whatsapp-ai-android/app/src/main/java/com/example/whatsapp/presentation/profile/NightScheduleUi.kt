package com.example.whatsapp.presentation.profile

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightScheduledTaskEntity
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private val ScheduleBg = Color(0xFF0B0F11)
private val ScheduleText = Color(0xFFE7EAEC)
private val ScheduleMuted = Color(0xFF9CA5A9)
private val ScheduleAccent = Color(0xFF21C063)
private val SchedulePanel = Color(0xFF20272A)

@Composable
fun NightScheduleDialog(
    initialPrompt: String,
    onDismiss: () -> Unit,
    onSchedule: (prompt: String, delayMs: Long, repeatMinutes: Long?) -> Unit,
) {
    val context = LocalContext.current
    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var runAtMillis by remember { mutableStateOf(System.currentTimeMillis() + 60 * 60 * 1000L) }
    var quickSelection by remember { mutableStateOf("1 hour") }
    var repeatMinutes by remember { mutableStateOf<Long?>(null) }
    var customRepeat by remember { mutableStateOf(false) }
    var customRepeatText by remember { mutableStateOf("60") }

    fun openDateTimePicker() {
        val current = Calendar.getInstance().apply { timeInMillis = runAtMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance().apply {
                    timeInMillis = runAtMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        selectedDate.set(Calendar.HOUR_OF_DAY, hour)
                        selectedDate.set(Calendar.MINUTE, minute)
                        selectedDate.set(Calendar.SECOND, 0)
                        selectedDate.set(Calendar.MILLISECOND, 0)
                        runAtMillis = selectedDate.timeInMillis
                        quickSelection = ""
                    },
                    current.get(Calendar.HOUR_OF_DAY),
                    current.get(Calendar.MINUTE),
                    false,
                ).show()
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = {
            Column {
                Text("Schedule with Night", color = ScheduleText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("Choose exactly when it should run.", color = ScheduleMuted, fontSize = 11.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("Task", color = ScheduleText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Surface(
                    color = SchedulePanel,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BasicTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(1200) },
                        textStyle = TextStyle(color = ScheduleText, fontSize = 14.sp, lineHeight = 19.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (prompt.isBlank()) {
                                    Text("What should Night do?", color = ScheduleMuted, fontSize = 14.sp)
                                }
                                inner()
                            }
                        },
                    )
                }

                Text("When", color = ScheduleText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    listOf(
                        "10 min" to 10 * 60 * 1000L,
                        "1 hour" to 60 * 60 * 1000L,
                        "6 hours" to 6 * 60 * 60 * 1000L,
                        "Tomorrow" to 24 * 60 * 60 * 1000L,
                    ).forEach { (label, delay) ->
                        FilterChip(
                            selected = quickSelection == label,
                            onClick = {
                                runAtMillis = System.currentTimeMillis() + delay
                                quickSelection = label
                            },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }

                Surface(
                    color = SchedulePanel,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ::openDateTimePicker,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = ScheduleAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(runAtMillis)),
                                color = ScheduleText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text("Tap to choose a specific date and time", color = ScheduleMuted, fontSize = 10.sp)
                        }
                    }
                }

                Text("Repeat", color = ScheduleText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    listOf(
                        "Once" to null,
                        "Daily" to 24L * 60L,
                        "Weekly" to 7L * 24L * 60L,
                    ).forEach { (label, value) ->
                        FilterChip(
                            selected = !customRepeat && repeatMinutes == value,
                            onClick = {
                                customRepeat = false
                                repeatMinutes = value
                            },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                    FilterChip(
                        selected = customRepeat,
                        onClick = { customRepeat = true },
                        label = { Text("Custom", maxLines = 1) },
                    )
                }

                if (customRepeat) {
                    Surface(
                        color = SchedulePanel,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Every", color = ScheduleMuted, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = customRepeatText,
                                onValueChange = { customRepeatText = it.filter(Char::isDigit).take(6) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(color = ScheduleText, fontSize = 14.sp),
                                modifier = Modifier.weight(1f),
                            )
                            Text("minutes", color = ScheduleMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val customValue = customRepeatText.toLongOrNull()?.takeIf { it >= 1L }
            val effectiveRepeat = if (customRepeat) customValue else repeatMinutes
            val delayMs = (runAtMillis - System.currentTimeMillis()).coerceAtLeast(60_000L)
            Button(
                onClick = { onSchedule(prompt.trim(), delayMs, effectiveRepeat) },
                enabled = prompt.trim().isNotBlank() && (!customRepeat || customValue != null),
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
            .statusBarsPadding().navigationBarsPadding(),
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
                    Surface(
                        color = SchedulePanel,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
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
                                        (task.repeatMinutes?.let { " • every " + it + " min" } ?: ""),
                                    color = ScheduleMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 4.dp),
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
}
