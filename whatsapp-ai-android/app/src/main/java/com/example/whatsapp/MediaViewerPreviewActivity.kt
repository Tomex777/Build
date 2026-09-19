package com.example.whatsapp

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

class MediaViewerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        // A local still is intentionally routed through VLC for CI so the video-player
        // control chrome is rendered without relying on network media.
        val playerPreview = File(cacheDir, "night-player-preview.jpg")
        if (!playerPreview.exists()) {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.bilal)
            FileOutputStream(playerPreview).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
            }
            bitmap.recycle()
        }

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightMediaViewerScreen(
                    items = listOf(
                        NightChatMediaItem(
                            id = "preview-video-1",
                            localPath = playerPreview.absolutePath,
                            mimeType = "video/mp4",
                            caption = "Night full-screen video player",
                            time = "20:31",
                            sender = "Night",
                            duration = "0:10",
                        ),
                        NightChatMediaItem(
                            id = "preview-image-1",
                            localPath = playerPreview.absolutePath,
                            mimeType = "image/jpeg",
                            caption = "Swipe left and right through chat media.",
                            time = "20:32",
                            sender = "You",
                        ),
                    ),
                    initialIndex = 0,
                    onBack = {},
                    onEdit = {},
                )
            }
        }
    }
}
