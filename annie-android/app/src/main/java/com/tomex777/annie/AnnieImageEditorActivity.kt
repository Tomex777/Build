package com.tomex777.annie

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Non-destructive bitmap edits. The original picture is never overwritten.
 * A square crop is centered; users can combine it with rotation and mirroring.
 */
internal data class AnnieImageEdits(
    val quarterTurns: Int = 0,
    val mirrored: Boolean = false,
    val squareCrop: Boolean = false,
)

internal fun applyAnnieImageEdits(source: Bitmap, edits: AnnieImageEdits): Bitmap {
    val matrix = Matrix().apply {
        if (edits.mirrored) postScale(-1f, 1f)
        postRotate(((edits.quarterTurns % 4) + 4) % 4 * 90f)
    }
    val rotated = if (!edits.mirrored && edits.quarterTurns % 4 == 0) source
        else Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (!edits.squareCrop) return rotated
    val size = minOf(rotated.width, rotated.height)
    return Bitmap.createBitmap(
        rotated, (rotated.width - size) / 2, (rotated.height - size) / 2, size, size
    )
}

/**
 * Edit is a separate Android screen rather than a narrow chat Dialog. Loading and
 * transforms run off the UI thread. Saving goes through the Android document picker,
 * so no broad storage permission is needed on Android 8 through Android 16.
 */
internal class AnnieImageEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val source = intent.getStringExtra(EXTRA_URI).orEmpty()
        val projectId = intent.getStringExtra(EXTRA_PROJECT).orEmpty()
        val image = source.takeIf(String::isNotBlank)?.let {
            resolvePackageResourceUri(this, projectId, it)
        }
        setContent { AnnieTheme { AnnieImageEditorScreen(image, onClose = ::finish) } }
    }

    companion object {
        private const val EXTRA_URI = "annie.image.edit.uri"
        private const val EXTRA_PROJECT = "annie.image.edit.project"

        fun intent(context: Context, uri: String, projectId: String) =
            Intent(context, AnnieImageEditorActivity::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_PROJECT, projectId)
    }
}

private suspend fun loadEditableAnnieBitmap(context: Context, source: String): Bitmap? =
    withContext(Dispatchers.IO) {
        val data = annieImageRequestData(source) ?: return@withContext null
        val request = ImageRequest.Builder(context)
            .data(data).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
        val drawable = result.drawable
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return@withContext null
        // Keep a bounded editable copy even when the original is a giant image.
        val ratio = minOf(1f, 4096f / maxOf(intrinsicWidth, intrinsicHeight))
        val width = (intrinsicWidth * ratio).toInt().coerceAtLeast(1)
        val height = (intrinsicHeight * ratio).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        bitmap
    }

@Composable
internal fun AnnieImageEditorScreen(source: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember(source) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(source) { mutableStateOf(source != null) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var edits by remember(source) { mutableStateOf(AnnieImageEdits()) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        if (source != null) {
            runCatching { loadEditableAnnieBitmap(context, source) }.onSuccess {
                original = it
                if (it == null) error = "Couldn't read this image for editing."
            }.onFailure { error = it.message ?: "Image couldn't be opened." }
        } else error = "No image was provided."
        loading = false
    }

    val output by produceState<Bitmap?>(initialValue = null, original, edits) {
        value = original?.let { input ->
            withContext(Dispatchers.Default) { applyAnnieImageEdits(input, edits) }
        }
    }
    val saveCopy = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val bitmap = output
        if (uri != null && bitmap != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                "Couldn't encode the edited image"
                            }
                        } ?: throw IllegalStateException("Couldn't create the image file")
                    }
                }.onSuccess {
                    saved = true
                    error = null
                }.onFailure {
                    error = "Save failed: " + (it.message ?: "unknown error")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF030811))
        .statusBarsPadding().navigationBarsPadding().testTag("annie_image_editor")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("Close", color = Color.White) }
            Text("Edit image", color = Color.White)
            TextButton(
                enabled = output != null,
                onClick = { saveCopy.launch("Annie-edited.png") },
                modifier = Modifier.testTag("annie_image_save"),
            ) { Text("Save copy", color = Color(0xFF54D6AE)) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center) {
            if (output != null) {
                Image(
                    bitmap = output!!.asImageBitmap(),
                    contentDescription = "Edited image preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().testTag("annie_image_edit_preview"),
                )
            } else Text(
                if (loading) "Loading image…" else error ?: "Preparing image…",
                color = Color.White
            )
        }
        if (saved) Text("Saved a separate edited copy.", color = Color(0xFF54D6AE),
            modifier = Modifier.padding(12.dp).testTag("annie_image_saved"))
        error?.let { Text(it, color = Color(0xFFFF9B91), modifier = Modifier.padding(12.dp)) }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = {
                edits = edits.copy(quarterTurns = (edits.quarterTurns + 3) % 4)
                saved = false
            }, enabled = original != null,
                modifier = Modifier.testTag("annie_image_rotate_left")) {
                Text("↶ Rotate", color = Color.White)
            }
            TextButton(onClick = {
                edits = edits.copy(quarterTurns = (edits.quarterTurns + 1) % 4)
                saved = false
            }, enabled = original != null,
                modifier = Modifier.testTag("annie_image_rotate_right")) {
                Text("Rotate ↷", color = Color.White)
            }
            TextButton(onClick = {
                edits = edits.copy(mirrored = !edits.mirrored)
                saved = false
            }, enabled = original != null,
                modifier = Modifier.testTag("annie_image_flip")) {
                Text("Flip", color = Color.White)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = {
                edits = edits.copy(squareCrop = !edits.squareCrop)
                saved = false
            }, enabled = original != null,
                modifier = Modifier.testTag("annie_image_crop")) {
                Text(if (edits.squareCrop) "Original ratio" else "Square crop",
                    color = Color.White)
            }
            TextButton(onClick = { edits = AnnieImageEdits(); saved = false },
                enabled = original != null,
                modifier = Modifier.testTag("annie_image_reset")) {
                Text("Reset edits", color = Color.White)
            }
        }
    }
}
