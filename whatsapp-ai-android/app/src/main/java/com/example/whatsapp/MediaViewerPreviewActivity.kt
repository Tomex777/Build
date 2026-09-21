package com.example.whatsapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.presentation.chatscreen.NightChatMediaItem
import com.example.whatsapp.presentation.chatscreen.NightMediaViewerScreen
import com.example.whatsapp.ui.theme.WhatsappTheme
import java.io.File
import java.io.FileOutputStream

private const val NIGHT_PREVIEW_VIDEO_PATH = "night.preview.videoPath"

class MediaViewerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val requestedVideo =
            intent.getStringExtra(NIGHT_PREVIEW_VIDEO_PATH)
                ?.let(::File)
                ?.takeIf { it.isFile && it.length() > 0L }

        val fallback = File(cacheDir, "night-player-preview.jpg")
        if (requestedVideo == null && !fallback.exists()) {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.bilal)
            FileOutputStream(fallback).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
            }
            bitmap.recycle()
        }

        val items =
            if (requestedVideo != null) {
                listOf(
                    NightChatMediaItem(
                        id = "preview-real-video-1",
                        localPath = requestedVideo.absolutePath,
                        mimeType = "video/mp4",
                        caption = "Night real video playback fixture",
                        sender = "Night Video 1",
                    ),
                    NightChatMediaItem(
                        id = "preview-real-video-2",
                        localPath = requestedVideo.absolutePath,
                        mimeType = "video/mp4",
                        caption = "Night real video playback fixture",
                        sender = "Night Video 2",
                    ),
                )
            } else {
                listOf(
                    NightChatMediaItem(
                        id = "preview-video-1",
                        localPath = fallback.absolutePath,
                        mimeType = "video/mp4",
                        caption = "Night full-screen video player",
                        time = "20:31",
                        sender = "Night",
                        duration = "0:10",
                    ),
                    NightChatMediaItem(
                        id = "preview-image-1",
                        localPath = fallback.absolutePath,
                        mimeType = "image/jpeg",
                        caption = "Swipe left and right through chat media.",
                        time = "20:32",
                        sender = "You",
                    ),
                )
            }

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightMediaViewerScreen(
                    items = items,
                    initialIndex = 0,
                    onBack = { finish() },
                    onEdit = { item ->
                        startActivity(
                            Intent(
                                this@MediaViewerPreviewActivity,
                                MediaEditorPreviewActivity::class.java,
                            ).putExtra(
                                NIGHT_PREVIEW_VIDEO_PATH,
                                item.localPath,
                            ),
                        )
                    },
                )
            }
        }
    }
}
