// Night final independent screenshot validation
package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.whatsapp.presentation.chatscreen.CurrentWhatsAppConversation
import com.example.whatsapp.presentation.chatscreen.whatsappPreviewMessages
import com.example.whatsapp.ui.theme.WhatsappTheme

class ChatPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val showAttachments = intent.getBooleanExtra("attachments", false)

        setContent {
            WhatsappTheme(darkTheme = true) {
                var text by remember { mutableStateOf("") }

                CurrentWhatsAppConversation(
                    contactName = "Night",
                    subtitle = "GPT-6 Astra • Memory on",
                    messages = whatsappPreviewMessages(),
                    messageText = text,
                    onMessageTextChange = { text = it },
                    onBackClick = {},
                    onSendClick = { text = "" },
                    onCallClick = {},
                    onAttachmentClick = {},
                    onCameraClick = {},
                    onMicClick = {},
                    onAttachmentAction = {},
                    autoScrollToLatest = false,
                    attachmentsInitiallyOpen = showAttachments,
                )
            }
        }
    }
}
