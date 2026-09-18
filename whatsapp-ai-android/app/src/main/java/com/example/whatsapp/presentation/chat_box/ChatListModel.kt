package com.example.whatsapp.presentation.chat_box

import android.graphics.Bitmap


data class ChatListModel(
    val name: String? = null,          // Contact name
    val phoneNumber: String? = null,   // Phone number
    val image: Bitmap? = null,            // Drawable resource (optional)
    val userId: String? = null,        // User ID
    val time: String? = null,          // Message time
    val message: String? = null,       // Last message
    val profileImage: String? = null   // Firebase image URL / Base64
){
    constructor() : this(null, null, null, null, null, null, null)
}
