package com.example.whatsapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.presentation.chatscreen.NightMediaComposerScreen
import com.example.whatsapp.ui.theme.WhatsappTheme
import java.io.File
import java.io.FileOutputStream

private const val NIGHT_PREVIEW_VIDEO_PATH = "night.preview.videoPath"
private const val NIGHT_PREVIEW_IMAGE_PATH = "night.preview.imagePath"
private const val NIGHT_PREVIEW_EXPORT_PREFS = "night_media_preview"

class MediaEditorPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val requestedVideo =
            intent.getStringExtra(NIGHT_PREVIEW_VIDEO_PATH)
                ?.let(::File)
                ?.takeIf { it.isFile && it.length() > 0L }

        val requestedImage =
            intent.getStringExtra(NIGHT_PREVIEW_IMAGE_PATH)
                ?.let(::File)
                ?.takeIf { it.isFile && it.length() > 0L }

        val fallback = File(cacheDir, "night-editor-preview.jpg")
        if (requestedVideo == null && requestedImage == null && !fallback.exists()) {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.bilal)
            FileOutputStream(fallback).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
            }
            bitmap.recycle()
        }

        val preview = requestedVideo ?: requestedImage ?: fallback
        val mimeType =
            when {
                requestedVideo != null -> "video/mp4"
                preview.extension.equals("png", ignoreCase = true) -> "image/png"
                else -> "image/jpeg"
            }
        val fileName =
            when {
                requestedVideo != null -> "Night test video.mp4"
                requestedImage != null -> requestedImage.name
                else -> "Night photo.jpg"
            }
        val prefs =
            getSharedPreferences(
                NIGHT_PREVIEW_EXPORT_PREFS,
                Context.MODE_PRIVATE,
            )
        prefs.edit().clear().commit()

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightMediaComposerScreen(
                    localPath = preview.absolutePath,
                    mimeType = mimeType,
                    fileName = fileName,
                    videoThumbnailPath = null,
                    caption = if (requestedVideo != null) "Night real video edit" else "A Night media edit",
                    onCaptionChange = {},
                    onCancel = { finish() },
                    onPreparedSend = { path, exportedMime, exportedName ->
                        runCatching {
                            val source = File(path)
                            check(source.isFile && source.length() > 0L)
                            val stable =
                                File(
                                    filesDir,
                                    if (exportedMime.startsWith("video/")) {
                                        "night-media-preview-export.mp4"
                                    } else {
                                        "night-media-preview-export.jpg"
                                    },
                                )
                            if (source.absolutePath != stable.absolutePath) {
                                source.copyTo(stable, overwrite = true)
                            }
                            prefs.edit()
                                .putString("exportPath", stable.absolutePath)
                                .putString("exportMime", exportedMime)
                                .putString("exportName", exportedName)
                                .putLong("exportBytes", stable.length())
                                .remove("exportError")
                                .commit()
                        }.onFailure { failure ->
                            prefs.edit()
                                .putString(
                                    "exportError",
                                    failure.message ?: failure.javaClass.simpleName,
                                )
                                .commit()
                        }
                    },
                )
            }
        }
    }
}
