package com.example.whatsapp.presentation.chatscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.example.whatsapp.models.Message
import com.example.whatsapp.presentation.viewmodels.BaseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    phoneNumber: String,
    navHostController: NavHostController,
    baseViewModel: BaseViewModel,
) {
    var messageText by remember { mutableStateOf("") }
    var currentUserPhone by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("Contact") }
    var messages by remember { mutableStateOf<List<Pair<String, Message>>>(emptyList()) }

    LaunchedEffect(Unit) {
        baseViewModel.getCurrentUserPhoneNumber { phone ->
            currentUserPhone = phone ?: "demo_user"
        }
    }

    LaunchedEffect(phoneNumber) {
        val demoNames = mapOf(
            "+1234567890" to "Muhammad Ahmad",
            "+1234567891" to "Muhammad Harib",
            "+1234567892" to "Taimoor Arshad",
            "+1234567893" to "Abdus Salam",
        )

        contactName = demoNames[phoneNumber] ?: phoneNumber.ifBlank { "Contact" }

        if (demoNames[phoneNumber] == null && phoneNumber.isNotBlank()) {
            baseViewModel.getUserNameByPhoneNumber(phoneNumber) { name ->
                if (!name.isNullOrBlank()) contactName = name
            }
        }
    }

    LaunchedEffect(phoneNumber, currentUserPhone) {
        if (phoneNumber.isNotBlank()) {
            val sender = currentUserPhone.ifBlank { "demo_user" }
            baseViewModel.getAllMessages(sender, phoneNumber) { items ->
                messages = items
            }
        }
    }

    val visualMessages = remember(messages, currentUserPhone) {
        messages.map { (id, message) ->
            WhatsAppVisualMessage.TextMessage(
                id = id,
                text = message.message,
                time = formatClockTime(message.timestamp),
                mine = message.senderPhoneNumber == currentUserPhone ||
                    (currentUserPhone.isBlank() && message.senderPhoneNumber == "demo_user"),
                read = true,
            )
        }
    }

    CurrentWhatsAppConversation(
        contactName = contactName,
        subtitle = "Business Account",
        messages = visualMessages,
        messageText = messageText,
        onMessageTextChange = { messageText = it },
        onBackClick = { navHostController.popBackStack() },
        onSendClick = {
            if (messageText.isNotBlank() && phoneNumber.isNotBlank()) {
                val sender = currentUserPhone.ifBlank { "demo_user" }
                baseViewModel.sendMessage(sender, phoneNumber, messageText)
                messageText = ""
            }
        },
        onCallClick = {},
        onAttachmentClick = {},
        onCameraClick = {},
        onMicClick = {},
    )
}

private fun formatClockTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
