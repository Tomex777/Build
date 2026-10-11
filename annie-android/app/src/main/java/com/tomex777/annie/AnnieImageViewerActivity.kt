package com.tomex777.annie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage

/**
 * An image's full-screen surface is a genuine Activity, not a Dialog constrained by
 * the caller's chat layout or the device's default dialog window width.
 * The Activity is internal and accepts no exported intents.
 */
internal class AnnieImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val source = intent.getStringExtra(EXTRA_URI).orEmpty()
        val projectId = intent.getStringExtra(EXTRA_PROJECT).orEmpty()
        val caption = intent.getStringExtra(EXTRA_CAPTION).orEmpty()
        val image = source.takeIf(String::isNotBlank)?.let {
            resolvePackageResourceUri(this, projectId, it)
        }
        setContent {
            AnnieTheme {
                FullScreenImageContent(
                    image, caption,
                    onEdit = { startActivity(AnnieImageEditorActivity.intent(this, source, projectId)) },
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_URI = "annie.image.uri"
        private const val EXTRA_PROJECT = "annie.image.project"
        private const val EXTRA_CAPTION = "annie.image.caption"

        fun intent(context: Context, uri: String, projectId: String, caption: String): Intent =
            Intent(context, AnnieImageViewerActivity::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_PROJECT, projectId)
                .putExtra(EXTRA_CAPTION, caption.take(500))
    }
}

@Composable
private fun FullScreenImageContent(
    model: String?, caption: String, onEdit: () -> Unit, onClose: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF030811))
            .clipToBounds().onSizeChanged { viewport = it }
            .testTag("script_image_fullscreen_viewport")
    ) {
        if (model == null) {
            Text("This image is unavailable.", color = Color.White, modifier = Modifier.padding(24.dp))
        } else {
            AsyncImage(
                model = model,
                contentDescription = caption.ifBlank { "Full-screen image" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translation.x
                        translationY = translation.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = viewport.width * (nextScale - 1f) / 2f
                            val maxY = viewport.height * (nextScale - 1f) / 2f
                            scale = nextScale
                            translation = if (nextScale == 1f) Offset.Zero else Offset(
                                (translation.x + pan.x).coerceIn(-maxX, maxX),
                                (translation.y + pan.y).coerceIn(-maxY, maxY),
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2f
                            translation = Offset.Zero
                        })
                    }
                    .testTag("script_image_fullscreen")
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xCC030811)),
                horizontalArrangement = Arrangement.End,
            ) {
                if (model != null) {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("script_image_edit"),
                    ) { Text("Edit", color = Color.White) }
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("script_image_close")
                ) { Text("Close", color = Color.White) }
            }
            Spacer(Modifier.weight(1f))
            if (caption.isNotBlank()) {
                Text(
                    caption, color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xCC030811)).padding(16.dp)
                        .testTag("script_image_caption")
                )
            }
        }
    }
}
