package com.example.whatsapp.presentation.chatscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun NightMediaComposerScreen(
    localPath: String,
    mimeType: String,
    fileName: String,
    videoThumbnailPath: String?,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val isVideo = mimeType.startsWith("video/")
    val previewPath = if (isVideo) videoThumbnailPath else localPath

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A0B))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Cancel",
                    tint = Color.White,
                )
            }

            Text(
                text = if (isVideo) "Video" else "Photo",
                color = Color.White,
                fontSize = 19.sp,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = fileName,
                color = Color(0xFF9EA7AB),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val preview = previewPath?.let(::File)
            if (preview != null && preview.exists()) {
                AsyncImage(
                    model = preview,
                    contentDescription = fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isVideo) {
                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    shape = CircleShape,
                    modifier = Modifier.size(68.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video preview",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111719))
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                color = Color(0xFF20272A),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.weight(1f),
            ) {
                TextField(
                    value = caption,
                    onValueChange = { onCaptionChange(it.take(1024)) },
                    placeholder = {
                        Text(
                            text = "Add a caption…",
                            color = Color(0xFF8F999E),
                        )
                    },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFE94B72),
                    ),
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            Surface(
                color = Color(0xFFE94B72),
                shape = CircleShape,
                modifier = Modifier.size(50.dp),
                onClick = onSend,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send media",
                        tint = Color(0xFF111416),
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
    }
}
