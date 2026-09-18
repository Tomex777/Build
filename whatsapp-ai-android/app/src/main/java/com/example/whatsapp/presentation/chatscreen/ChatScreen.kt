package com.example.whatsapp.presentation.chatscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.whatsapp.R
import com.example.whatsapp.models.Message
import com.example.whatsapp.presentation.viewmodels.BaseViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

// Helper function to get drawable resource ID for a contact name in ChatScreen
private fun getProfileImageDrawableForChat(contactName: String): Int {
    return when {
        contactName.contains("Ahmad", ignoreCase = true) -> R.drawable.bilal
        contactName.contains("Harib", ignoreCase = true) -> R.drawable.harib
        contactName.contains("Taimoor", ignoreCase = true) -> R.drawable.taimoor
        contactName.contains("Salam", ignoreCase = true) -> R.drawable.abdussalam
        else -> R.drawable.profile_placeholder
    }
}

@Composable
fun ChatScreen(
    phoneNumber: String,
    navHostController: NavHostController,
    baseViewModel: BaseViewModel
) {
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Pair<String, Message>>>(emptyList()) }
    var contactName by remember { mutableStateOf("Contact") }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var currentUserPhone by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()

    // Get current user phone number (with fallback for demo chats)
    LaunchedEffect(Unit) {
        baseViewModel.getCurrentUserPhoneNumber { phone ->
            currentUserPhone = phone ?: "demo_user"
        }
    }

    // Fetch contact name (simplified - works with demo chats)
    LaunchedEffect(phoneNumber) {
        if (phoneNumber.isNotEmpty()) {
            // Map demo chat phone numbers to names
            val demoChatNames = mapOf(
                "+1234567890" to "Muhammad Ahmad",
                "+1234567891" to "Muhammad Harib",
                "+1234567892" to "Taimoor Arshad",
                "+1234567893" to "Abdus Salam"
            )
            
            // Check if it's a demo chat first
            val demoName = demoChatNames[phoneNumber]
            if (demoName != null) {
                contactName = demoName
            } else {
                // Try to fetch from Firebase for real contacts
                if (phoneNumber.startsWith("+") || phoneNumber.length > 10) {
                    baseViewModel.getUserNameByPhoneNumber(phoneNumber) { name ->
                        contactName = name ?: phoneNumber
                    }
                } else {
                    // Use phoneNumber as name for other cases
                    contactName = phoneNumber
                }
            }
        } else {
            contactName = "Contact"
        }
    }

    // Fetch all messages (simplified - works even without real phone numbers)
    LaunchedEffect(phoneNumber, currentUserPhone) {
        if (phoneNumber.isNotEmpty()) {
            val senderPhone = if (currentUserPhone.isNotEmpty()) currentUserPhone else "demo_user"
            baseViewModel.getAllMessages(senderPhone, phoneNumber) { messageList ->
                messages = messageList
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                contactName = contactName,
                phoneNumber = phoneNumber,
                onBackClick = { navHostController.popBackStack() },
                onCallClick = { /* TODO: Implement call */ }
            )
        }
    ) { paddingValues ->
        // WhatsApp-style background pattern
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    // Light gray background with subtle pattern effect
                    Color(0xFFECE5DD)
                )
        ) {
            // Subtle pattern overlay (simulating WhatsApp's background)
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Security Banner
                SecurityBanner()
                
                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                items(messages, key = { it.first }) { (messageId, message) ->
                    MessageBubble(
                        message = message,
                        isSentByMe = message.senderPhoneNumber == currentUserPhone || 
                                     (currentUserPhone.isEmpty() && message.senderPhoneNumber == "demo_user"),
                        onLongPress = {
                            showDeleteDialog = messageId
                        }
                    )
                }
            }

                // Input Bar
                ChatInputBar(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    onSendClick = {
                        if (messageText.isNotBlank() && phoneNumber.isNotEmpty()) {
                            val senderPhone = if (currentUserPhone.isNotEmpty()) currentUserPhone else "demo_user"
                            baseViewModel.sendMessage(senderPhone, phoneNumber, messageText)
                            messageText = ""
                        }
                    },
                    onAttachmentClick = { /* TODO: Implement attachment */ },
                    onCameraClick = { /* TODO: Implement camera */ },
                    onMicClick = { /* TODO: Implement voice message */ },
                    onEmojiClick = { showEmojiPicker = !showEmojiPicker }
                )
                
                // Emoji Picker
                if (showEmojiPicker) {
                    EmojiPicker(
                        onEmojiSelected = { emoji ->
                            messageText += emoji
                            showEmojiPicker = false
                        },
                        onDismiss = { showEmojiPicker = false }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { messageId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val senderPhone = if (currentUserPhone.isNotEmpty()) currentUserPhone else "demo_user"
                        baseViewModel.deleteMessage(
                            senderPhone,
                            phoneNumber,
                            messageId,
                            onSuccess = {
                                showDeleteDialog = null
                            },
                            onFailure = {
                                showDeleteDialog = null
                            }
                        )
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteDialog = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatTopBar(
    contactName: String,
    phoneNumber: String,
    onBackClick: () -> Unit,
    onCallClick: () -> Unit
) {
    // Get profile picture drawable based on contact name
    val profileDrawable = remember(contactName) {
        getProfileImageDrawableForChat(contactName)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.baseline_arrow_back_24),
                contentDescription = "Back",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Profile Picture
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.Gray)
        ) {
            Image(
                painter = painterResource(profileDrawable),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contactName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = phoneNumber,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        IconButton(onClick = onCallClick) {
            Icon(
                painter = painterResource(R.drawable.outline_phone_24),
                contentDescription = "Call",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isSentByMe: Boolean,
    onLongPress: () -> Unit
) {
    val bubbleColor = if (isSentByMe) {
        colorResource(R.color.light_green)
    } else {
        Color(0xFFE5E5E5)
    }

    val textColor = if (isSentByMe) {
        Color.Black
    } else {
        Color.Black
    }

    val alignment = if (isSentByMe) {
        Alignment.CenterEnd
    } else {
        Alignment.CenterStart
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable { onLongPress() }
        ) {
            Text(
                text = message.message,
                color = textColor,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimestamp(message.timestamp),
                color = if (isSentByMe) Color.DarkGray else Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun SecurityBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F2F5))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Messages and calls are end-to-end encrypted. Only people in this chat can read, listen to, or share them. Learn more",
            fontSize = 12.sp,
            color = Color.Gray,
            lineHeight = 16.sp
        )
    }
    HorizontalDivider()
}

@Composable
fun ChatInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit,
    onEmojiClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.mint_green))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji icon on left
        IconButton(onClick = onEmojiClick) {
            Text(
                text = "😊",
                fontSize = 24.sp,
                modifier = Modifier.size(28.dp)
            )
        }

        // Text input field with attachment and camera icons inside
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
        ) {
            TextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Message", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = false,
                maxLines = 4,
                trailingIcon = {
                    Row {
                        IconButton(onClick = onAttachmentClick) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_attach_file_24),
                                contentDescription = "Attachment",
                                tint = Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onCameraClick) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_photo_camera_24),
                                contentDescription = "Camera",
                                tint = Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Mic button on right (circular, green)
        if (messageText.isBlank()) {
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(colorResource(R.color.light_green))
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_mic_24),
                    contentDescription = "Voice Message",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            IconButton(
                onClick = onSendClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(colorResource(R.color.light_green))
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_send_24),
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Common emojis - WhatsApp style
    val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
        "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
        "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
        "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
        "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
        "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈",
        "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾",
        "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿",
        "😾", "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌️", "🤞",
        "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍",
        "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
        "🙏", "✍️", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
        "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️",
        "✝️", "☪️", "🕉", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐",
        "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐",
        "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳",
        "🈶", "🈚", "🈸", "🈺", "🈷️", "✴️", "🆚", "💮", "🉐", "㊙️",
        "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️",
        "🆘", "❌", "⭕", "🛑", "⛔", "📛", "🚫", "💯", "💢", "♨️",
        "🚷", "🚯", "🚳", "🚱", "🔞", "📵", "🚭", "❗", "❕", "❓",
        "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️",
        "🔰", "♻️", "✅", "🈯", "💹", "❇️", "✳️", "❎", "🌐", "💠",
        "Ⓜ️", "🌀", "💤", "🏧", "🚾", "♿", "🅿️", "🈳", "🈂️", "🛂",
        "🛃", "🛄", "🛅", "🚹", "🚺", "🚼", "🚻", "🚮", "🎦", "📶",
        "🈁", "🔣", "ℹ️", "🔤", "🔡", "🔠", "🆖", "🆗", "🆙", "🆒",
        "🆕", "🆓", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣",
        "8️⃣", "9️⃣", "🔟", "🔢", "#️⃣", "*️⃣", "▶️", "⏸", "⏯", "⏹",
        "⏺", "⏭", "⏮", "⏩", "⏪", "⏫", "⏬", "◀️", "🔼", "🔽",
        "➡️", "⬅️", "⬆️", "⬇️", "↗️", "↖️", "↙️", "↘️", "↔️", "↕️",
        "🔄", "↪️", "↩️", "⤴️", "⤵️", "🔀", "🔁", "🔂", "🔄", "🔃",
        "🎵", "🎶", "➕", "➖", "➗", "✖️", "💲", "💱", "™️", "©️",
        "®️", "〰️", "➰", "➿", "🔚", "🔙", "🔛", "🔜", "🔝", "🛐"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            items(emojis.chunked(8)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .clickable { onEmojiSelected(emoji) }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

