package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.whatsapp.presentation.chatscreen.CurrentWhatsAppConversation
import com.example.whatsapp.presentation.chatscreen.richPreviewMessagesPageOne
import com.example.whatsapp.presentation.chatscreen.richPreviewMessagesPageTwo
import com.example.whatsapp.ui.theme.WhatsappTheme

class RichBubblePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val page = intent.getIntExtra("page", 1)

        setContent {
            WhatsappTheme(darkTheme = true) {
                var text by remember { mutableStateOf("") }

                CurrentWhatsAppConversation(
                    contactName = "Assistant",
                    subtitle = "Local • Hybrid",
                    messages = if (page == 1) {
                        richPreviewMessagesPageOne()
                    } else {
                        richPreviewMessagesPageTwo()
                    },
                    messageText = text,
                    onMessageTextChange = { text = it },
                    onBackClick = {},
                    onSendClick = { text = "" },
                    onCallClick = {},
                    onAttachmentClick = {},
                    onCameraClick = {},
                    onMicClick = {},
                    onEmojiClick = {},
                    autoScrollToLatest = false,
                )
            }
        }
    }
}
