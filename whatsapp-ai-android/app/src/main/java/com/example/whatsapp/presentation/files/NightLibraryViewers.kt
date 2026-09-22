package com.example.whatsapp.presentation.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ViewerBg = Color(0xFF0B0F11)
private val ViewerText = Color(0xFFE7EAEC)
private val ViewerMuted = Color(0xFF9CA5A9)

@Composable
fun NightLibraryTextEditorScreen(
    localPath: String,
    displayName: String,
    onBack: () -> Unit,
) {
    var content by remember(localPath) { mutableStateOf("") }
    var original by remember(localPath) { mutableStateOf("") }
    var status by remember(localPath) { mutableStateOf("Loading…") }

    LaunchedEffect(localPath) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { File(localPath).readText() }
        }
        loaded.onSuccess {
            content = it
            original = it
            status = ""
        }.onFailure {
            status = it.message ?: "Could not read this file."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewerBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ViewerText)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = ViewerText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (status.isNotBlank()) {
                    Text(status, color = ViewerMuted, fontSize = 10.sp)
                }
            }
            TextButton(
                enabled = content != original,
                onClick = {
                    status = "Saving…"
                },
            ) {
                Text("Save")
            }
        }

        if (content != original && status == "Saving…") {
            LaunchedEffect(content, localPath) {
                val saved = withContext(Dispatchers.IO) {
                    runCatching { File(localPath).writeText(content) }
                }
                saved.onSuccess {
                    original = content
                    status = "Saved"
                }.onFailure {
                    status = it.message ?: "Could not save."
                }
            }
        }

        OutlinedTextField(
            value = content,
            onValueChange = {
                content = it
                if (status == "Saved") status = ""
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = ViewerText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(10.dp),
        )
    }
}

@Composable
fun NightLibraryAudioScreen(
    displayName: String,
    isPlaying: Boolean,
    progress: Float,
    positionLabel: String,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewerBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ViewerText)
            }
            Text(
                text = displayName,
                color = ViewerText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = ViewerText,
                )
            }
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = positionLabel,
                color = ViewerMuted,
                fontSize = 12.sp,
            )
        }
    }
}
