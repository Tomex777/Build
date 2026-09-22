package com.example.whatsapp.presentation.chatscreen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private data class EditorColorOption(
    val name: String,
    val composeColor: Color,
    val androidColor: Int,
)

private val EditorColorOptions = listOf(
    EditorColorOption("White", Color.White, AndroidColor.WHITE),
    EditorColorOption("Red", Color(0xFFFF4D67), AndroidColor.rgb(255, 77, 103)),
    EditorColorOption("Yellow", Color(0xFFFFD43B), AndroidColor.rgb(255, 212, 59)),
    EditorColorOption("Green", Color(0xFF35D07F), AndroidColor.rgb(53, 208, 127)),
    EditorColorOption("Blue", Color(0xFF4EA5FF), AndroidColor.rgb(78, 165, 255)),
    EditorColorOption("Black", Color.Black, AndroidColor.BLACK),
)

private val EditorBrushSizes = listOf(
    "Thin" to 6f,
    "Medium" to 12f,
    "Thick" to 22f,
)

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
    var discardDialogOpen by remember(localPath) { mutableStateOf(false) }
    var imageEdited by remember(localPath) { mutableStateOf(false) }
    val initialCaption = remember(localPath) { caption }
    var brushColor by remember(localPath) { mutableStateOf(EditorColorOptions.first()) }
    var brushSize by remember(localPath) { mutableStateOf(EditorBrushSizes[1].second) }
    var textColor by remember(localPath) { mutableStateOf(EditorColorOptions.first()) }

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

    fun closeDrawingMode() {
        drawing = false
        photoEditor?.setBrushDrawingMode(false)
    }

    fun requestBack() {
        if (exporting) return
        when {
            textDialogOpen -> textDialogOpen = false
            emojiOpen -> emojiOpen = false
            cropMode -> {
                cropMode = false
                cropView = null
            }
            drawing -> closeDrawingMode()
            !isVideo && (imageEdited || caption != initialCaption) -> discardDialogOpen = true
            else -> onCancel()
        }
    }

    // Keep the proven video back path unchanged. Image editing intercepts back so
    // accidental exits cannot silently discard markup/crop/text work.
    BackHandler(enabled = !isVideo || cropMode || emojiOpen) {
        requestBack()
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
            val flattened = File(dir, "flatten_" + System.currentTimeMillis() + ".png")
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
                cropView = null
                imageEdited = true
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
            val stamp = System.currentTimeMillis()
            // PhotoEditor writes PNG bytes regardless of the file extension. Stage
            // that output honestly, then encode the final Night attachment as a
            // real JPEG so filename, MIME type and file signature always agree.
            val staged = File(context.cacheDir, "night_image_stage_" + stamp + ".png")
            val output = File(dir, "night_image_" + stamp + ".jpg")
            when (val result = editor.saveAsFile(staged.absolutePath)) {
                is SaveFileResult.Success -> {
                    val jpegSaved = withContext(Dispatchers.IO) {
                        runCatching {
                            val bitmap = BitmapFactory.decodeFile(staged.absolutePath)
                                ?: error("Could not decode the flattened image.")
                            try {
                                FileOutputStream(output).use { stream ->
                                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream))
                                }
                            } finally {
                                bitmap.recycle()
                                runCatching { staged.delete() }
                            }
                            output.isFile && output.length() > 0L
                        }.getOrDefault(false)
                    }

                    if (jpegSaved) {
                        exporting = false
                        onPreparedSend(output.absolutePath, "image/jpeg", output.name)
                    } else {
                        runCatching { output.delete() }
                        runCatching { staged.delete() }
                        exporting = false
                        exportError = "Could not encode the edited image."
                    }
                }
                is SaveFileResult.Failure -> {
                    runCatching { staged.delete() }
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
                        if (isVideo) {
                            onCancel()
                        } else {
                            requestBack()
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
                    when {
                        isVideo -> {
                            IconButton(onClick = { muted = !muted }) {
                                Icon(
                                    imageVector = if (muted) Icons.Default.MicOff else Icons.Default.VolumeUp,
                                    contentDescription = if (muted) "Unmute" else "Mute",
                                    tint = if (muted) EditorAccent else Color.White,
                                )
                            }
                            // Video is frozen: retain its existing top Done/export action.
                            IconButton(onClick = ::finish) {
                                Icon(Icons.Default.Check, "Done", tint = Color.White)
                            }
                        }

                        cropMode -> {
                            IconButton(onClick = { cropView?.rotateImage(90) }) {
                                Icon(Icons.Default.RotateRight, "Rotate crop", tint = Color.White)
                            }
                            IconButton(onClick = ::applyCrop) {
                                Icon(Icons.Default.Check, "Apply crop", tint = Color.White)
                            }
                        }

                        drawing -> {
                            IconButton(
                                onClick = {
                                    photoEditor?.undo()
                                    imageEdited = true
                                },
                            ) {
                                Icon(Icons.Default.Undo, "Undo", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    photoEditor?.redo()
                                    imageEdited = true
                                },
                            ) {
                                Icon(Icons.Default.Redo, "Redo", tint = Color.White)
                            }
                            IconButton(onClick = ::closeDrawingMode) {
                                Icon(Icons.Default.Check, "Done drawing", tint = Color.White)
                            }
                        }

                        else -> {
                            IconButton(onClick = ::enterCropMode) {
                                Icon(Icons.Default.Crop, "Crop", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    imageRotation = (imageRotation + 90f) % 360f
                                    photoEditorView?.source?.rotation = imageRotation
                                    imageEdited = true
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
                                    drawing = true
                                    imageEdited = true
                                    photoEditor?.setBrushDrawingMode(true)
                                    photoEditor?.brushColor = brushColor.androidColor
                                    photoEditor?.brushSize = brushSize
                                }
                            ) {
                                Icon(Icons.Default.Brush, "Draw", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    photoEditor?.undo()
                                    imageEdited = true
                                },
                            ) {
                                Icon(Icons.Default.Undo, "Undo", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    photoEditor?.redo()
                                    imageEdited = true
                                },
                            ) {
                                Icon(Icons.Default.Redo, "Redo", tint = Color.White)
                            }
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val sourceAspect = remember(workingPath) {
                    readEditorImageAspectRatio(workingPath)
                }
                val quarterTurns =
                    ((imageRotation / 90f).toInt() % 4 + 4) % 4
                val displayAspect =
                    if (!isVideo && quarterTurns % 2 == 1) {
                        1f / sourceAspect
                    } else {
                        sourceAspect
                    }
                val availableAspect =
                    if (maxHeight.value > 0f) {
                        maxWidth.value / maxHeight.value
                    } else {
                        displayAspect
                    }
                val editorWidth =
                    if (displayAspect >= availableAspect) {
                        maxWidth
                    } else {
                        maxHeight * displayAspect
                    }
                val editorHeight =
                    if (displayAspect >= availableAspect) {
                        maxWidth / displayAspect
                    } else {
                        maxHeight
                    }
                val editorModifier =
                    Modifier.size(
                        width = editorWidth,
                        height = editorHeight,
                    )

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
                                modifier = editorModifier,
                            )
                        }
                    }

                    else -> {
                        key(workingPath) {
                            AndroidView(
                                factory = { ctx ->
                                    PhotoEditorView(ctx).also { editorView ->
                                        editorView.source.scaleType =
                                            android.widget.ImageView.ScaleType.FIT_CENTER
                                        editorView.source.adjustViewBounds = false
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
                                modifier = editorModifier,
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

            if (!isVideo && drawing && !cropMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EditorBar)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Draw", color = Color.White, fontSize = 13.sp)
                        EditorColorOptions.forEach { option ->
                            Surface(
                                color = option.composeColor,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(if (brushColor == option) 30.dp else 26.dp)
                                    .semantics { contentDescription = "Brush " + option.name },
                                onClick = {
                                    brushColor = option
                                    photoEditor?.brushColor = option.androidColor
                                },
                            ) {
                                if (brushColor == option) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (option.name in setOf("White", "Yellow")) Color.Black else Color.White,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EditorBrushSizes.forEach { (label, size) ->
                            Surface(
                                color = if (brushSize == size) EditorAccent else Color(0xFF30383B),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = "Brush size " + label },
                                onClick = {
                                    brushSize = size
                                    photoEditor?.brushSize = size
                                },
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 7.dp),
                                )
                            }
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
                        modifier = Modifier.semantics {
                            contentDescription = "Trim range"
                        },
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
                        imageEdited = true
                        emojiOpen = false
                    },
                    onDismiss = { emojiOpen = false },
                )
            }
        }
    }

    if (discardDialogOpen && !isVideo) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Discard changes?") },
            text = { Text("Your image edits and caption changes won’t be saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialogOpen = false
                        onCancel()
                    },
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) {
                    Text("Keep editing")
                }
            },
        )
    }

    if (textDialogOpen && !isVideo && !cropMode) {
        AlertDialog(
            onDismissRequest = { textDialogOpen = false },
            title = { Text("Add text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = textDraft,
                        onValueChange = { textDraft = it.take(180) },
                        maxLines = 4,
                        placeholder = { Text("Text") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditorColorOptions.forEach { option ->
                            Surface(
                                color = option.composeColor,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(if (textColor == option) 32.dp else 28.dp)
                                    .semantics { contentDescription = "Text color " + option.name },
                                onClick = { textColor = option },
                            ) {
                                if (textColor == option) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (option.name in setOf("White", "Yellow")) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = textDraft.trim()
                        if (value.isNotEmpty()) {
                            photoEditor?.addText(
                                text = value,
                                colorCodeTextView = textColor.androidColor,
                            )
                            imageEdited = true
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

private fun readEditorImageAspectRatio(path: String): Float {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, options)

    val width = options.outWidth
    val height = options.outHeight
    return if (width > 0 && height > 0) {
        (width.toFloat() / height.toFloat()).coerceIn(0.08f, 12f)
    } else {
        1f
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
