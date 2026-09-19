package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.example.whatsapp.presentation.chatscreen.NightChatMediaItem
import com.example.whatsapp.presentation.chatscreen.NightMediaViewerScreen
import com.example.whatsapp.ui.theme.WhatsappTheme

class MediaViewerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val image = "android.resource://" + packageName + "/" + R.drawable.bilal

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightMediaViewerScreen(
                    items = listOf(
                        NightChatMediaItem(
                            id = "preview-image-1",
                            localPath = image,
                            mimeType = "image/jpeg",
                            caption = "This is the full-screen Night chat media viewer.",
                            time = "20:31",
                            sender = "Night",
                        ),
                        NightChatMediaItem(
                            id = "preview-image-2",
                            localPath = image,
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
