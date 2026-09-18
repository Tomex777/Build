package com.example.whatsapp.models

data class Message(
    val senderPhoneNumber: String = "",
    val message: String = "",
    val timestamp: Long = 0L
)
