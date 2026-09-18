package com.example.whatsapp.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsapp.data.ai.GroqApiService
import com.example.whatsapp.models.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiChatViewModel @Inject constructor() : ViewModel() {

    // In-memory message storage (resets when ViewModel is cleared)
    private val _messages = MutableStateFlow<List<Pair<String, Message>>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping = _isTyping.asStateFlow()

    private var messageIdCounter = 0

    fun sendMessage(messageText: String) {
        if (messageText.isBlank()) return

        // Add user message
        val userMessage = Message(
            senderPhoneNumber = "user",
            message = messageText,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + ("user_${messageIdCounter++}" to userMessage)

        // Show typing indicator
        _isTyping.value = true

        // Send to Groq API
        viewModelScope.launch {
            val result = GroqApiService.sendMessage(
                userMessage = messageText,
                conversationHistory = _messages.value
            )

            _isTyping.value = false

            result.onSuccess { aiResponse ->
                addAiMessage(aiResponse)
            }.onFailure { error ->
                // Enhanced error handling with user-friendly messages
                val msg = error.message ?: "Unknown error"
                val userMessage = if (msg.contains("401")) {
                    "Invalid API key. Please check your Groq console."
                } else if (msg.contains("network", ignoreCase = true)) {
                    "No internet connection."
                } else if (msg.contains("timeout", ignoreCase = true)) {
                    "Request timed out. Please try again."
                } else {
                    "Sorry, AI is unavailable right now."
                }
                addAiMessage(userMessage)
            }
        }
    }

    private fun addAiMessage(messageText: String) {
        val aiMessage = Message(
            senderPhoneNumber = "ai",
            message = messageText,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + ("ai_${messageIdCounter++}" to aiMessage)
    }

    // Clear all messages (called when navigating away)
    fun clearMessages() {
        _messages.value = emptyList()
        _isTyping.value = false
        messageIdCounter = 0
    }
}