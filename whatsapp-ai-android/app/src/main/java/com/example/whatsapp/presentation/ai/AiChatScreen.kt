package com.example.whatsapp.presentation.ai

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.whatsapp.R
import com.example.whatsapp.models.Message
import com.example.whatsapp.presentation.chatscreen.ChatInputBar
import com.example.whatsapp.presentation.chatscreen.EmojiPicker
import com.example.whatsapp.presentation.chatscreen.formatTimestamp

@Composable
fun AiChatScreen(
    navHostController: NavHostController,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    
    val listState = rememberLazyListState()
    
    // Clear messages when screen is first opened (fresh start)
    LaunchedEffect(Unit) {
        viewModel.clearMessages()
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            AiChatTopBar(
                onBackClick = { 
                    viewModel.clearMessages()
                    navHostController.popBackStack() 
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFECE5DD))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Welcome Message (shown only when no messages)
                if (messages.isEmpty() && !isTyping) {
                    WelcomeMessage()
                }
                
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
                        AiMessageBubble(
                            message = message,
                            isSentByMe = message.senderPhoneNumber == "user"
                        )
                    }
                    
                    // Typing indicator
                    if (isTyping) {
                        item {
                            TypingIndicator()
                        }
                    }
                }
                
                // Input Bar (reuse from ChatScreen)
                ChatInputBar(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    onSendClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(messageText)
                            messageText = ""
                        }
                    },
                    onAttachmentClick = { /* Not needed for AI */ },
                    onCameraClick = { /* Not needed for AI */ },
                    onMicClick = { /* Not needed for AI */ },
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
}

@Composable
fun AiChatTopBar(
    onBackClick: () -> Unit
) {
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
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Meta AI Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Gray)
        ) {
            Icon(
                painter = painterResource(R.drawable.meta_ai_icon),
                contentDescription = "Meta AI",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                tint = Color.Unspecified
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Meta AI Text
        Text(
            text = "Meta AI",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        
        // No call/video icons - just spacing
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
fun WelcomeMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hello! I'm here to help. Ask me anything!",
                fontSize = 16.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun AiMessageBubble(
    message: Message,
    isSentByMe: Boolean
) {
    val bubbleColor = if (isSentByMe) {
        colorResource(R.color.light_green)
    } else {
        // Light blue background for AI messages
        Color(0xFFDCF8C6) // Light blue-green, similar to WhatsApp AI
    }

    val textColor = Color.Black
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
fun TypingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFDCF8C6)) // Light blue background like AI messages
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Meta AI is typing",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            // Animated dots
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                )
            }
        }
    }
}

