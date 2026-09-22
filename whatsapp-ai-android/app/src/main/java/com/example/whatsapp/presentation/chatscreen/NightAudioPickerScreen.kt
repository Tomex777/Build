package com.example.whatsapp.presentation.chatscreen

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NightAudioPickerItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val artist: String,
    val durationMs: Long,
    val sizeBytes: Long,
)

@Composable
fun NightAudioPickerScreen(
    onBack: () -> Unit,
    onSelect: (Uri) -> Unit,
    onBrowseFiles: () -> Unit,
    accentColor: Color = Color(0xFFD44368),
) {
    val context = LocalContext.current
    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf<List<NightAudioPickerItem>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) {
            audio = emptyList()
            return@LaunchedEffect
        }
        loading = true
        audio = withContext(Dispatchers.IO) {
            queryDeviceAudio(context)
        }
        loading = false
    }

    val filtered = remember(audio, query) {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isBlank()) {
            audio
        } else {
            audio.filter {
                it.name.lowercase(Locale.getDefault()).contains(needle) ||
                    it.artist.lowercase(Locale.getDefault()).contains(needle)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F11))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFE7EAEC),
                )
            }
            Text(
                text = "Choose audio",
                color = Color(0xFFE7EAEC),
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBrowseFiles) {
                Text("Browse", color = accentColor)
            }
        }

        if (!permissionGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = Color(0xFF171C1F),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    Text(
                        text = "Let Night show audio on this device",
                        color = Color(0xFFE7EAEC),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Permission is only used to list audio you can choose. You can use Browse instead without giving Night library access.",
                        color = Color(0xFF9CA5A9),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(permission) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Allow audio access")
                    }
                }
            }
            return@Column
        }

        Surface(
            color = Color(0xFF171C1F),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF9CA5A9),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(9.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(120) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFFE7EAEC),
                        fontSize = 14.sp,
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                text = "Search songs and audio",
                                color = Color(0xFF9CA5A9),
                                fontSize = 14.sp,
                            )
                        }
                        inner()
                    },
                )
            }
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Loading audio…", color = Color(0xFF9CA5A9), fontSize = 12.sp)
                }
            }

            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (audio.isEmpty()) {
                            "No device audio found. Tap Browse to choose a file."
                        } else {
                            "No audio matches your search."
                        },
                        color = Color(0xFF9CA5A9),
                        fontSize = 13.sp,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item.uri) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = Color(0xFF171C1F),
                                shape = CircleShape,
                                modifier = Modifier.size(46.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(23.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = Color(0xFFE7EAEC),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        if (item.artist.isNotBlank() && item.artist != "<unknown>") {
                                            append(item.artist)
                                            append(" • ")
                                        }
                                        append(formatAudioDuration(item.durationMs))
                                        if (item.sizeBytes > 0L) {
                                            append(" • ")
                                            append(formatAudioSize(item.sizeBytes))
                                        }
                                    },
                                    color = Color(0xFF9CA5A9),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun queryDeviceAudio(context: android.content.Context): List<NightAudioPickerItem> {
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE,
    )
    val result = mutableListOf<NightAudioPickerItem>()

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        MediaStore.Audio.Media.DATE_ADDED + " DESC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val name = cursor.getString(nameIndex).orEmpty().ifBlank { "Audio" }
            val artist = cursor.getString(artistIndex).orEmpty()
            val duration = cursor.getLong(durationIndex).coerceAtLeast(0L)
            val size = cursor.getLong(sizeIndex).coerceAtLeast(0L)
            result += NightAudioPickerItem(
                id = id,
                uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                ),
                name = name,
                artist = artist,
                durationMs = duration,
                sizeBytes = size,
            )
        }
    }
    return result
}

private fun formatAudioDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun formatAudioSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L ->
        String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L ->
        String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
