package com.example.whatsapp

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

class MediaEditorPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val preview = File(cacheDir, "night-editor-preview.jpg")
        if (!preview.exists()) {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.bilal)
            FileOutputStream(preview).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
            }
            bitmap.recycle()
        }

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightMediaComposerScreen(
                    localPath = preview.absolutePath,
                    mimeType = "image/jpeg",
                    fileName = "Night photo.jpg",
                    videoThumbnailPath = null,
                    caption = "A Night media edit",
                    onCaptionChange = {},
                    onCancel = {},
                    onPreparedSend = { _, _, _ -> },
                )
            }
        }
    }
}
