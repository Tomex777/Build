package com.example.whatsapp.presentation.chatscreen

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.canhub.cropper.CropImageView
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.SaveFileResult
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EditorAccent = Color(0xFFE94B72)
private val EditorBar = Color(0xFF111719)

@Composable
fun NightMediaComposerScreen(
    localPath: String,
    mimeType: String,
    fileName: String,
    videoThumbnailPath: String?,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onCancel: () -> Unit,
    onPreparedSend: (localPath: String, mimeType: String, fileName: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isVideo = mimeType.startsWith("video/")

    var workingPath by remember(localPath) { mutableStateOf(localPath) }
    var photoEditor by remember(workingPath) { mutableStateOf<PhotoEditor?>(null) }
    var photoEditorView by remember(workingPath) { mutableStateOf<PhotoEditorView?>(null) }
    var cropView by remember(workingPath) { mutableStateOf<CropImageView?>(null) }
    var cropMode by remember(localPath) { mutableStateOf(false) }
    var drawing by remember(workingPath) { mutableStateOf(false) }
    var imageRotation by remember(workingPath) { mutableStateOf(0f) }
    var textDialogOpen by remember(localPath) { mutableStateOf(false) }
    var textDraft by remember(localPath) { mutableStateOf("") }
    var emojiOpen by remember(localPath) { mutableStateOf(false) }

    var exporting by remember(localPath) { mutableStateOf(false) }
    var exportError by remember(localPath) { mutableStateOf<String?>(null) }
    var transformer by remember(localPath) { mutableStateOf<Transformer?>(null) }

    val durationMs = remember(localPath, isVideo) {
        if (isVideo) readEditorVideoDuration(localPath) else 0L
    }
    val durationSeconds = (durationMs / 1000f).coerceAtLeast(1f)
    var trimRange by remember(localPath, durationSeconds) {
        mutableStateOf(0f..durationSeconds)
    }
    var muted by remember(localPath) { mutableStateOf(false) }

    BackHandler(enabled = cropMode || emojiOpen) {
        when {
            emojiOpen -> emojiOpen = false
            cropMode -> {
                cropMode = false
                cropView = null
            }
        }
    }

    DisposableEffect(localPath) {
        onDispose {
            transformer?.cancel()
            transformer = null
            if (workingPath != localPath) {
                runCatching { File(workingPath).delete() }
            }
        }
    }

    fun enterCropMode() {
        if (exporting || isVideo) return
        val editor = photoEditor
        if (editor == null) {
            cropMode = true
            return
        }

        exporting = true
        exportError = null
        scope.launch {
            val dir = File(context.cacheDir, "night_crop_stage").apply { mkdirs() }
            val flattened = File(dir, "flatten_" + System.currentTimeMillis() + ".jpg")
            when (val result = editor.saveAsFile(flattened.absolutePath)) {
                is SaveFileResult.Success -> {
                    val previous = workingPath
                    workingPath = flattened.absolutePath
                    cropMode = true
                    exporting = false
                    if (previous != localPath) {
                        runCatching { File(previous).delete() }
                    }
                }
                is SaveFileResult.Failure -> {
                    exporting = false
                    exportError = result.exception.message ?: "Could not prepare the image for cropping."
                }
            }
        }
    }

    fun applyCrop() {
        if (exporting) return
        val view = cropView ?: return
        val bitmap = runCatching { view.getCroppedImage() }.getOrNull()
        if (bitmap == null) {
            exportError = "The crop is still loading."
            return
        }

        exporting = true
        exportError = null
        scope.launch {
            val dir = File(context.cacheDir, "night_crop_stage").apply { mkdirs() }
            val output = File(dir, "crop_" + System.currentTimeMillis() + ".jpg")
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    FileOutputStream(output).use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream))
                    }
                    true
                }.getOrDefault(false)
            }
            bitmap.recycle()

            if (saved) {
                val previous = workingPath
                workingPath = output.absolutePath
                cropMode = false
                exporting = false
                if (previous != localPath) {
                    runCatching { File(previous).delete() }
                }
            } else {
                exporting = false
                exportError = "Could not save the crop."
            }
        }
    }

    fun exportImageAndSend() {
        val editor = photoEditor
        if (editor == null) {
            onPreparedSend(workingPath, mimeType, fileName)
            return
        }

        exporting = true
        exportError = null
        scope.launch {
            val dir = File(context.filesDir, "night_media_edits").apply { mkdirs() }
            val output = File(dir, "night_image_" + System.currentTimeMillis() + ".jpg")
            when (val result = editor.saveAsFile(output.absolutePath)) {
                is SaveFileResult.Success -> {
                    exporting = false
                    onPreparedSend(output.absolutePath, "image/jpeg", output.name)
                }
                is SaveFileResult.Failure -> {
                    exporting = false
                    exportError = result.exception.message ?: "Could not save the edited image."
                }
            }
        }
    }

    fun exportVideoAndSend() {
        val safeDuration = durationMs.coerceAtLeast(1L)
        val startMs = (trimRange.start * 1000f).toLong().coerceIn(0L, safeDuration)
        val minimumEnd = (startMs + 1L).coerceAtMost(safeDuration)
        val endMs = (trimRange.endInclusive * 1000f).toLong().coerceIn(minimumEnd, safeDuration)
        val trimmed = startMs > 120L || endMs < durationMs - 120L

        if (!trimmed && !muted) {
            onPreparedSend(localPath, mimeType, fileName)
            return
        }

        exporting = true
        exportError = null

        val dir = File(context.filesDir, "night_media_edits").apply { mkdirs() }
        val output = File(dir, "night_video_" + System.currentTimeMillis() + ".mp4")
        val inputItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(localPath)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val edited = EditedMediaItem.Builder(inputItem)
            .setRemoveAudio(muted)
            .build()

        val builtTransformer = Transformer.Builder(context)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult,
                    ) {
                        exporting = false
                        transformer = null
                        onPreparedSend(output.absolutePath, "video/mp4", output.name)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        exporting = false
                        transformer = null
                        runCatching { output.delete() }
                        exportError = exportException.message ?: "Could not export the edited video."
                    }
                }
            )
            .build()

        transformer = builtTransformer
        builtTransformer.start(edited, output.absolutePath)
    }

    fun finish() {
        if (exporting) return
        when {
            cropMode -> applyCrop()
            isVideo -> exportVideoAndSend()
            else -> exportImageAndSend()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.76f))
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (cropMode) {
                            cropMode = false
                        } else {
                            onCancel()
                        }
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isVideo) {
                        IconButton(onClick = { muted = !muted }) {
                            Icon(
                                imageVector = if (muted) Icons.Default.MicOff else Icons.Default.VolumeUp,
                                contentDescription = if (muted) "Unmute" else "Mute",
                                tint = if (muted) EditorAccent else Color.White,
                            )
                        }
                    } else if (cropMode) {
                        IconButton(onClick = { cropView?.rotateImage(90) }) {
                            Icon(Icons.Default.RotateRight, "Rotate crop", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = ::enterCropMode) {
                            Icon(Icons.Default.Crop, "Crop", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                imageRotation = (imageRotation + 90f) % 360f
                                photoEditorView?.source?.rotation = imageRotation
                            },
                        ) {
                            Icon(Icons.Default.RotateRight, "Rotate", tint = Color.White)
                        }
                        IconButton(onClick = { emojiOpen = !emojiOpen }) {
                            Icon(
                                Icons.Default.SentimentSatisfiedAlt,
                                "Emoji",
                                tint = if (emojiOpen) EditorAccent else Color.White,
                            )
                        }
                        IconButton(onClick = { textDialogOpen = true }) {
                            Icon(Icons.Default.TextFields, "Text", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                drawing = !drawing
                                photoEditor?.setBrushDrawingMode(drawing)
                                if (drawing) {
                                    photoEditor?.brushColor = AndroidColor.WHITE
                                    photoEditor?.brushSize = 12f
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Brush,
                                "Draw",
                                tint = if (drawing) EditorAccent else Color.White,
                            )
                        }
                        IconButton(onClick = { photoEditor?.undo() }) {
                            Icon(Icons.Default.Undo, "Undo", tint = Color.White)
                        }
                        IconButton(onClick = { photoEditor?.redo() }) {
                            Icon(Icons.Default.Redo, "Redo", tint = Color.White)
                        }
                    }

                    IconButton(onClick = ::finish) {
                        Icon(Icons.Default.Check, if (cropMode) "Apply crop" else "Done", tint = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isVideo -> {
                        NightVlcVideoSurface(
                            path = localPath,
                            active = true,
                            showControls = true,
                            onToggleControls = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    cropMode -> {
                        key(workingPath) {
                            AndroidView(
                                factory = { ctx ->
                                    CropImageView(ctx).also { view ->
                                        view.guidelines = CropImageView.Guidelines.ON
                                        view.setImageUriAsync(Uri.fromFile(File(workingPath)))
                                        cropView = view
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    else -> {
                        key(workingPath) {
                            AndroidView(
                                factory = { ctx ->
                                    PhotoEditorView(ctx).also { editorView ->
                                        editorView.source.setImageURI(Uri.fromFile(File(workingPath)))
                                        photoEditorView = editorView
                                        photoEditor = PhotoEditor.Builder(ctx, editorView)
                                            .setPinchTextScalable(true)
                                            .setClipSourceImage(true)
                                            .build()
                                        if (imageRotation != 0f) {
                                            editorView.source.rotation = imageRotation
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                if (exporting) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = CircleShape,
                        modifier = Modifier.size(76.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = EditorAccent,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }
            }

            if (isVideo) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EditorBar)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Trim", color = Color.White, fontSize = 13.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            formatEditorTime(trimRange.start) + " – " + formatEditorTime(trimRange.endInclusive),
                            color = Color(0xFFB8BEC1),
                            fontSize = 11.sp,
                        )
                    }
                    RangeSlider(
                        value = trimRange,
                        onValueChange = { trimRange = it },
                        valueRange = 0f..durationSeconds,
                    )
                }
            }

            exportError?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFFFF8CA0),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EditorBar)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            if (!cropMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EditorBar)
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
                            placeholder = { Text("Add a caption…", color = Color(0xFF8F999E)) },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = EditorAccent,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    Surface(
                        color = EditorAccent,
                        shape = CircleShape,
                        modifier = Modifier.size(50.dp),
                        onClick = ::finish,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Send,
                                "Send media",
                                tint = Color(0xFF111416),
                                modifier = Modifier.size(25.dp),
                            )
                        }
                    }
                }
            }
        }

        if (emojiOpen && !isVideo && !cropMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 78.dp),
            ) {
                EmojiPicker(
                    onEmojiSelected = { emoji ->
                        photoEditor?.addEmoji(emoji)
                        emojiOpen = false
                    },
                    onDismiss = { emojiOpen = false },
                )
            }
        }
    }

    if (textDialogOpen && !isVideo && !cropMode) {
        AlertDialog(
            onDismissRequest = { textDialogOpen = false },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it.take(180) },
                    maxLines = 4,
                    placeholder = { Text("Text") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = textDraft.trim()
                        if (value.isNotEmpty()) {
                            photoEditor?.addText(
                                text = value,
                                colorCodeTextView = AndroidColor.WHITE,
                            )
                        }
                        textDraft = ""
                        textDialogOpen = false
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { textDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun readEditorVideoDuration(path: String): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
    } catch (_: Throwable) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatEditorTime(secondsFloat: Float): String {
    val total = secondsFloat.toLong().coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0) {
        hours.toString() + ":" + minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
    } else {
        minutes.toString() + ":" + seconds.toString().padStart(2, '0')
    }
}
