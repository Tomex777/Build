package com.example.whatsapp.presentation.chatscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    imageRotationDegrees: Int = 0,
    cropSquare: Boolean = false,
    overlayText: String = "",
    onRotateImage: () -> Unit = {},
    onToggleSquareCrop: () -> Unit = {},
    onOverlayTextChange: (String) -> Unit = {},
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val isVideo = mimeType.startsWith("video/")
    val previewPath = if (isVideo) videoThumbnailPath else localPath
    var showTextEditor by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val preview = previewPath?.let(::File)
            if (preview != null && preview.exists()) {
                if (!isVideo && cropSquare) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = preview,
                            contentDescription = fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = imageRotationDegrees.toFloat() },
                        )
                    }
                } else {
                    AsyncImage(
                        model = preview,
                        contentDescription = fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo) {
                                    rotationZ = imageRotationDegrees.toFloat()
                                }
                            },
                    )
                }
            }

            if (!isVideo && overlayText.isNotBlank()) {
                Text(
                    text = overlayText,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .graphicsLayer {
                            shadowElevation = 8.dp.toPx()
                        },
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
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.34f))
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
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )

            if (!isVideo) {
                IconButton(onClick = onToggleSquareCrop) {
                    Icon(
                        imageVector = Icons.Default.CropSquare,
                        contentDescription = if (cropSquare) "Use original crop" else "Square crop",
                        tint = if (cropSquare) Color(0xFFE94B72) else Color.White,
                    )
                }

                IconButton(onClick = onRotateImage) {
                    Icon(
                        imageVector = Icons.Default.CropRotate,
                        contentDescription = "Rotate photo",
                        tint = Color.White,
                    )
                }

                IconButton(onClick = { showTextEditor = !showTextEditor }) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = "Add text",
                        tint = if (showTextEditor || overlayText.isNotBlank()) {
                            Color(0xFFE94B72)
                        } else {
                            Color.White
                        },
                    )
                }
            }
        }

        if (!isVideo && showTextEditor) {
            Surface(
                color = Color(0xD91B2022),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 62.dp, start = 18.dp, end = 18.dp),
            ) {
                TextField(
                    value = overlayText,
                    onValueChange = { onOverlayTextChange(it.take(80)) },
                    placeholder = { Text("Type on photo", color = Color(0xFF98A1A5)) },
                    singleLine = true,
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
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.46f))
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (!isVideo) {
                val activeEdits = buildList {
                    if (cropSquare) add("Square crop")
                    if (imageRotationDegrees % 360 != 0) add("${imageRotationDegrees % 360}°")
                    if (overlayText.isNotBlank()) add("Text")
                }
                if (activeEdits.isNotEmpty()) {
                    Text(
                        text = activeEdits.joinToString(" · "),
                        color = Color(0xFFB9C0C4),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
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

        Text(
            text = fileName,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 76.dp, end = 90.dp),
        )
    }
}
