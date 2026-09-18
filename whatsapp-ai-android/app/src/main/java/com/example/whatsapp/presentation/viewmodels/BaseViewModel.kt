package com.example.whatsapp.presentation.viewmodels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.whatsapp.models.Message
import com.example.whatsapp.presentation.chat_box.ChatListModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.io.encoding.ExperimentalEncodingApi
import javax.inject.Inject

@HiltViewModel
class BaseViewModel @Inject constructor() : ViewModel() {

    fun searchUserByPhoneNumber(phoneNumber: String, callback: (ChatListModel?) -> Unit) {

        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Log.e("BaseViewModel", "User is Not Authenticated")
            callback(null)
            return
        }

        val databaseReference = FirebaseDatabase.getInstance().getReference("users")
        databaseReference.orderByChild("phoneNumber").equalTo(phoneNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            val firstChild = snapshot.children.firstOrNull()
                            if (firstChild != null) {
                                try {
                                    // Safely parse user data manually
                                    val name = firstChild.child("name").value as? String
                                    val phoneNumberValue = firstChild.child("phoneNumber").value as? String
                                    val userId = firstChild.child("userId").value as? String
                                    val time = firstChild.child("time").value as? String
                                    val message = firstChild.child("message").value as? String
                                    val profileImage = firstChild.child("profileImage").value as? String
                                    
                                    val user = ChatListModel(
                                        name = name,
                                        phoneNumber = phoneNumberValue,
                                        userId = userId,
                                        time = time,
                                        message = message,
                                        profileImage = profileImage,
                                        image = null
                                    )
                                    callback(user)
                                } catch (e: Exception) {
                                    Log.e("BaseViewModel", "Error parsing user data: ${e.message}", e)
                                    callback(null)
                                }
                            } else {
                                callback(null)
                            }
                        } else {
                            callback(null)
                        }
                    } catch (e: Exception) {
                        Log.e("BaseViewModel", "Error processing user search: ${e.message}", e)
                        callback(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(
                        "BaseViewModel",
                        "Error Fetching User: ${error.message}, Details: ${error.details}"
                    )
                    callback(null)
                }
            }
            )


    }


    fun getChatForUser(userId: String, callback: (List<ChatListModel>) -> Unit) {
        Log.d("BaseViewModel", "getChatForUser called for userId: $userId")
        
        val chatList = mutableListOf<ChatListModel>()
        var loadedFromPrimary = false
        
        // Try loading from users/$userId/chats first (primary location)
        val userChatRef = FirebaseDatabase.getInstance().getReference("users/$userId/chats")
        userChatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.exists() && snapshot.children.count() > 0) {
                        loadedFromPrimary = true
                        Log.d("BaseViewModel", "Loading chats from users/$userId/chats (${snapshot.children.count()} chats found)")
                        
                        for (childSnapshot in snapshot.children) {
                            try {
                                val name = childSnapshot.child("name").value as? String
                                var phoneNumber = childSnapshot.child("phoneNumber").value as? String
                                val userIdValue = childSnapshot.child("userId").value as? String ?: userId
                                val time = childSnapshot.child("time").value as? String
                                val message = childSnapshot.child("message").value as? String
                                val profileImage = childSnapshot.child("profileImage").value as? String
                                
                                // If phoneNumber is missing, try to get it from the contact's user data
                                if (phoneNumber.isNullOrEmpty() && name != null) {
                                    // Try to find phone number by name in users collection
                                    getUserPhoneNumberByName(name) { foundPhone ->
                                        phoneNumber = foundPhone
                                    }
                                }
                                
                                // Only add if we have at least a name or phoneNumber
                                if (name != null || !phoneNumber.isNullOrEmpty()) {
                                    chatList.add(
                                        ChatListModel(
                                            name = name,
                                            phoneNumber = phoneNumber,
                                            userId = userIdValue,
                                            time = time,
                                            message = message,
                                            profileImage = profileImage,
                                            image = null
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("BaseViewModel", "Error parsing chat item in getChatForUser: ${e.message}", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BaseViewModel", "Error processing chat data in getChatForUser: ${e.message}", e)
                }
                
                // If we didn't load from primary location, try root chats node
                if (!loadedFromPrimary) {
                    loadChatsFromRootForUser(userId, chatList) { finalList ->
                        _chatList.value = finalList
                        callback(finalList)
                    }
                } else {
                    _chatList.value = chatList
                    callback(chatList)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BaseViewModel", "Error Fetching User Chats from users/$userId/chats: ${error.message}")
                // Fallback to root chats node
                loadChatsFromRootForUser(userId, chatList) { finalList ->
                    _chatList.value = finalList
                    callback(finalList)
                }
            }
        })
    }
    
    private fun loadChatsFromRootForUser(userId: String, existingList: MutableList<ChatListModel>, callback: (List<ChatListModel>) -> Unit) {
        val chatRef = FirebaseDatabase.getInstance().getReference("chats")
        chatRef.orderByChild("userId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatList = existingList.toMutableList()
                    try {
                        if (snapshot.exists()) {
                            Log.d("BaseViewModel", "Loading chats from root chats node (${snapshot.children.count()} chats found)")
                            
                            for (childSnapshot in snapshot.children) {
                                try {
                                    val name = childSnapshot.child("name").value as? String
                                    var phoneNumber = childSnapshot.child("phoneNumber").value as? String
                                    val userIdValue = childSnapshot.child("userId").value as? String ?: userId
                                    val time = childSnapshot.child("time").value as? String
                                    val message = childSnapshot.child("message").value as? String
                                    val profileImage = childSnapshot.child("profileImage").value as? String
                                    
                                    // Ensure userId matches current user (filter out other users' chats)
                                    if (userIdValue == userId) {
                                        // If phoneNumber is missing, try to get it from the contact's user data
                                        if (phoneNumber.isNullOrEmpty() && name != null) {
                                            getUserPhoneNumberByName(name) { foundPhone ->
                                                phoneNumber = foundPhone
                                            }
                                        }
                                        
                                        // Only add if we have at least a name or phoneNumber
                                        if (name != null || !phoneNumber.isNullOrEmpty()) {
                                            // Check if this chat already exists (avoid duplicates)
                                            val exists = chatList.any { 
                                                it.name == name && it.phoneNumber == phoneNumber 
                                            }
                                            if (!exists) {
                                                chatList.add(
                                                    ChatListModel(
                                                        name = name,
                                                        phoneNumber = phoneNumber,
                                                        userId = userIdValue,
                                                        time = time,
                                                        message = message,
                                                        profileImage = profileImage,
                                                        image = null
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("BaseViewModel", "Error parsing chat item from root: ${e.message}", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BaseViewModel", "Error processing chat data from root: ${e.message}", e)
                    }
                    callback(chatList)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BaseViewModel", "Error fetching chat data from root: ${error.message}")
                    callback(existingList)
                }
            })
    }
    
    fun getUserPhoneNumberByName(name: String, callback: (String?) -> Unit) {
        val databaseReference = FirebaseDatabase.getInstance().getReference("users")
        databaseReference.orderByChild("name").equalTo(name)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val firstChild = snapshot.children.firstOrNull()
                        val phoneNumber = firstChild?.child("phoneNumber")?.value as? String
                        callback(phoneNumber)
                    } else {
                        callback(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(null)
                }
            })
    }


    private val _chatList = MutableStateFlow<List<ChatListModel>>(emptyList())
    val chatList = _chatList.asStateFlow()
    
    // Hardcoded demo chats that always show (not dependent on Firebase)
    private val demoChats = listOf(
        ChatListModel(
            name = "Muhammad Ahmad",
            phoneNumber = "+1234567890",
            userId = null,
            message = "Hey! How are you?",
            time = "10:30 AM"
        ),
        ChatListModel(
            name = "Muhammad Harib",
            phoneNumber = "+1234567891",
            userId = null,
            message = "See you tomorrow!",
            time = "Yesterday"
        ),
        ChatListModel(
            name = "Taimoor Arshad",
            phoneNumber = "+1234567892",
            userId = null,
            message = "Thanks for the help!",
            time = "2 days ago"
        ),
        ChatListModel(
            name = "Abdus Salam",
            phoneNumber = "+1234567893",
            userId = null,
            message = "Can we meet today?",
            time = "3 days ago"
        )
    )
    
    // Helper function to get drawable resource name for a contact (returns drawable name string)
    fun getProfileImageDrawableName(contactName: String?): String {
        if (contactName == null) return "profile_placeholder"
        
        // Map contact names to drawable resource names
        return when {
            contactName.contains("Ahmad", ignoreCase = true) -> "bilal"
            contactName.contains("Harib", ignoreCase = true) -> "harib"
            contactName.contains("Taimoor", ignoreCase = true) -> "taimoor"
            contactName.contains("Salam", ignoreCase = true) -> "abdussalam"
            else -> "profile_placeholder"
        }
    }


    init {
        // Always show demo chats immediately
        _chatList.value = demoChats
        loadChatData()
    }


    private fun loadChatData() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId != null) {
            Log.d("BaseViewModel", "loadChatData called for userId: $currentUserId")
            
            // Start with demo chats, then merge Firebase chats if available
            val mergedChatList = demoChats.toMutableList()
            
            // Try loading from users/$userId/chats first (primary location)
            val userChatRef = FirebaseDatabase.getInstance().getReference("users/$currentUserId/chats")
            userChatRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            Log.d("BaseViewModel", "Loading chats from users/$currentUserId/chats (${snapshot.children.count()} chats found)")
                            
                            for (childSnapshot in snapshot.children) {
                                try {
                                    val name = childSnapshot.child("name").value as? String
                                    val phoneNumber = childSnapshot.child("phoneNumber").value as? String
                                    val userId = childSnapshot.child("userId").value as? String ?: currentUserId
                                    val time = childSnapshot.child("time").value as? String
                                    val message = childSnapshot.child("message").value as? String
                                    val profileImage = childSnapshot.child("profileImage").value as? String
                                    
                                    // Ensure userId matches current user (filter out other users' chats)
                                    if (userId == currentUserId && (name != null || !phoneNumber.isNullOrEmpty())) {
                                        // Check if this chat already exists in demo chats (by phone number)
                                        val exists = mergedChatList.any { it.phoneNumber == phoneNumber }
                                        if (!exists) {
                                            mergedChatList.add(
                                                ChatListModel(
                                                    name = name,
                                                    phoneNumber = phoneNumber,
                                                    userId = userId,
                                                    time = time,
                                                    message = message,
                                                    profileImage = profileImage,
                                                    image = null
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("BaseViewModel", "Error parsing chat item: ${e.message}", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BaseViewModel", "Error processing chat data: ${e.message}", e)
                    }
                    
                    // Always update with merged list (demo chats + Firebase chats)
                    _chatList.value = mergedChatList
                    Log.d("BaseViewModel", "Updated chatList with ${mergedChatList.size} total chats (demo + Firebase)")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BaseViewModel", "Error fetching chat data from users/$currentUserId/chats: ${error.message}")
                    // Keep demo chats even if Firebase fails
                    _chatList.value = demoChats
                }
            })
        } else {
            Log.w("BaseViewModel", "loadChatData called but user is not authenticated - showing demo chats only")
            _chatList.value = demoChats
        }
    }
    
    private fun loadChatDataFromRoot(currentUserId: String) {
        val chatRef = FirebaseDatabase.getInstance().getReference("chats")
        chatRef.orderByChild("userId").equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Only merge if we don't have chats from users/$userId/chats
                    val existingChats = _chatList.value
                    if (existingChats.isEmpty()) {
                        val chatList = mutableListOf<ChatListModel>()
                        try {
                            if (snapshot.exists()) {
                                Log.d("BaseViewModel", "Loading chats from root chats node (${snapshot.children.count()} chats found)")
                                
                                for (childSnapshot in snapshot.children) {
                                    try {
                                        val name = childSnapshot.child("name").value as? String
                                        var phoneNumber = childSnapshot.child("phoneNumber").value as? String
                                        val userId = childSnapshot.child("userId").value as? String ?: currentUserId
                                        val time = childSnapshot.child("time").value as? String
                                        val message = childSnapshot.child("message").value as? String
                                        val profileImage = childSnapshot.child("profileImage").value as? String
                                        
                                        // Ensure userId matches current user (filter out other users' chats)
                                        if (userId == currentUserId) {
                                            // If phoneNumber is missing, try to get it from the contact's user data
                                            if (phoneNumber.isNullOrEmpty() && name != null) {
                                                getUserPhoneNumberByName(name) { foundPhone ->
                                                    phoneNumber = foundPhone
                                                }
                                            }
                                            
                                            // Only add if we have at least a name or phoneNumber
                                            if (name != null || !phoneNumber.isNullOrEmpty()) {
                                                chatList.add(
                                                    ChatListModel(
                                                        name = name,
                                                        phoneNumber = phoneNumber,
                                                        userId = userId,
                                                        time = time,
                                                        message = message,
                                                        profileImage = profileImage,
                                                        image = null
                                                    )
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("BaseViewModel", "Error parsing chat item: ${e.message}", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("BaseViewModel", "Error processing chat data: ${e.message}", e)
                        }
                        _chatList.value = chatList
                        Log.d("BaseViewModel", "Updated chatList with ${chatList.size} chats from root chats node")
                    } else {
                        // Merge with existing chats (avoid duplicates)
                        val mergedList = existingChats.toMutableList()
                        try {
                            if (snapshot.exists()) {
                                for (childSnapshot in snapshot.children) {
                                    try {
                                        val name = childSnapshot.child("name").value as? String
                                        var phoneNumber = childSnapshot.child("phoneNumber").value as? String
                                        val userId = childSnapshot.child("userId").value as? String ?: currentUserId
                                        val time = childSnapshot.child("time").value as? String
                                        val message = childSnapshot.child("message").value as? String
                                        val profileImage = childSnapshot.child("profileImage").value as? String
                                        
                                        if (userId == currentUserId) {
                                            if (phoneNumber.isNullOrEmpty() && name != null) {
                                                getUserPhoneNumberByName(name) { foundPhone ->
                                                    phoneNumber = foundPhone
                                                }
                                            }
                                            
                                            if (name != null || !phoneNumber.isNullOrEmpty()) {
                                                // Check if this chat already exists (avoid duplicates)
                                                val exists = mergedList.any { 
                                                    it.name == name && it.phoneNumber == phoneNumber 
                                                }
                                                if (!exists) {
                                                    mergedList.add(
                                                        ChatListModel(
                                                            name = name,
                                                            phoneNumber = phoneNumber,
                                                            userId = userId,
                                                            time = time,
                                                            message = message,
                                                            profileImage = profileImage,
                                                            image = null
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("BaseViewModel", "Error parsing chat item: ${e.message}", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("BaseViewModel", "Error processing chat data: ${e.message}", e)
                        }
                        _chatList.value = mergedList
                        Log.d("BaseViewModel", "Merged chatList with ${mergedList.size} total chats")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BaseViewModel", "Error fetching chat data from root: ${error.message}")
                }
            })
    }


    fun addChat(newChat: ChatListModel) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            // Add to both locations for consistency
            // Primary location: users/$userId/chats
            val userChatRef = FirebaseDatabase.getInstance().getReference("users/$currentUserId/chats").push()
            val chatWithUser = newChat.copy(userId = currentUserId)
            
            // Ensure phoneNumber is set
            val chatToAdd = if (chatWithUser.phoneNumber.isNullOrEmpty()) {
                Log.w("BaseViewModel", "Warning: Adding chat without phoneNumber")
                chatWithUser
            } else {
                chatWithUser
            }
            
            userChatRef.setValue(chatToAdd).addOnSuccessListener {
                Log.d("BaseViewModel", "Chat added successfully to users/$currentUserId/chats")
                
                // Also add to root chats node for backward compatibility
                val rootChatRef = FirebaseDatabase.getInstance().getReference("chats").push()
                rootChatRef.setValue(chatToAdd).addOnSuccessListener {
                    Log.d("BaseViewModel", "Chat added successfully to root chats node")
                }.addOnFailureListener { exception ->
                    Log.e("BaseViewModel", "Error adding chat to root: ${exception.message}")
                }
            }.addOnFailureListener { exception ->
                Log.e("BaseViewModel", "Error adding chat to database: ${exception.message}")
            }
        } else {
            Log.e("BaseViewModel", "User is not authenticated")
        }
    }


    private val dataBaseReference = FirebaseDatabase.getInstance().reference

    fun sendMessage(senderPhoneNumber: String, receiverPhoneNumber: String, messageText: String) {
        try {
            val messageId = dataBaseReference.push().key ?: return
            val message = Message(
                senderPhoneNumber = senderPhoneNumber,
                message = messageText,
                timestamp = System.currentTimeMillis()
            )

            // Simplified: Store messages in a single location for the current user
            // This works with Firebase Free Tier without complex real-time sync
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            
            // Store messages under current user's messages node
            dataBaseReference.child("user_messages")
                .child(currentUserId)
                .child(receiverPhoneNumber)
                .child(messageId)
                .setValue(message)
                .addOnSuccessListener {
                    Log.d("BaseViewModel", "Message sent successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("BaseViewModel", "Error sending message: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("BaseViewModel", "Error in sendMessage: ${e.message}", e)
        }
    }

    fun getMessage(
        senderPhoneNumber: String,
        receiverPhoneNumber: String,
        onNewMessage: (Message) -> Unit
    ) {
        val messageRef = dataBaseReference.child("messages")
            .child(senderPhoneNumber)
            .child(receiverPhoneNumber)

        messageRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val messageText = snapshot.child("message").value as? String ?: ""
                    val senderPhone = snapshot.child("senderPhoneNumber").value as? String ?: ""
                    
                    // Handle timestamp - it might be Long or String
                    val timestampValue = snapshot.child("timestamp").value
                    val timestamp = when (timestampValue) {
                        is Long -> timestampValue
                        is String -> timestampValue.toLongOrNull() ?: System.currentTimeMillis()
                        else -> System.currentTimeMillis()
                    }
                    
                    val message = Message(
                        senderPhoneNumber = senderPhone,
                        message = messageText,
                        timestamp = timestamp
                    )
                    onNewMessage(message)
                } catch (e: Exception) {
                    Log.e("BaseViewModel", "Error parsing message: ${e.message}", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BaseViewModel", "Error fetching messages: ${error.message}")
            }
        })

    }

    fun getAllMessages(
        senderPhoneNumber: String,
        receiverPhoneNumber: String,
        onMessagesLoaded: (List<Pair<String, Message>>) -> Unit
    ) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId == null) {
                Log.w("BaseViewModel", "User not authenticated, returning empty messages")
                onMessagesLoaded(emptyList())
                return
            }

            // Simplified: Load messages from user_messages node
            val messageRef = dataBaseReference.child("user_messages")
                .child(currentUserId)
                .child(receiverPhoneNumber)

            messageRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<Pair<String, Message>>()
                    try {
                        if (snapshot.exists()) {
                            snapshot.children.forEach { child ->
                                try {
                                    val messageId = child.key ?: return@forEach
                                    
                                    // Safely parse message fields
                                    val messageText = child.child("message").value as? String ?: ""
                                    val senderPhone = child.child("senderPhoneNumber").value as? String ?: senderPhoneNumber
                                    
                                    // Handle timestamp - it might be Long or String
                                    val timestampValue = child.child("timestamp").value
                                    val timestamp = when (timestampValue) {
                                        is Long -> timestampValue
                                        is String -> timestampValue.toLongOrNull() ?: System.currentTimeMillis()
                                        else -> System.currentTimeMillis()
                                    }
                                    
                                    val message = Message(
                                        senderPhoneNumber = senderPhone,
                                        message = messageText,
                                        timestamp = timestamp
                                    )
                                    messages.add(Pair(messageId, message))
                                } catch (e: Exception) {
                                    Log.e("BaseViewModel", "Error parsing message item: ${e.message}", e)
                                }
                            }
                        }
                        // Sort by timestamp
                        val sortedMessages = messages.sortedBy { it.second.timestamp }
                        onMessagesLoaded(sortedMessages)
                    } catch (e: Exception) {
                        Log.e("BaseViewModel", "Error processing messages: ${e.message}", e)
                        onMessagesLoaded(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BaseViewModel", "Error fetching all messages: ${error.message}")
                    onMessagesLoaded(emptyList())
                }
            })
        } catch (e: Exception) {
            Log.e("BaseViewModel", "Error in getAllMessages: ${e.message}", e)
            onMessagesLoaded(emptyList())
        }
    }

    fun deleteMessage(
        senderPhoneNumber: String,
        receiverPhoneNumber: String,
        messageId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId == null) {
                onFailure("User not authenticated")
                return
            }

            // Simplified: Delete from user_messages node
            val messageRef = dataBaseReference.child("user_messages")
                .child(currentUserId)
                .child(receiverPhoneNumber)
                .child(messageId)

            messageRef.removeValue()
                .addOnSuccessListener {
                    Log.d("BaseViewModel", "Message deleted successfully")
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    Log.e("BaseViewModel", "Error deleting message: ${exception.message}")
                    onFailure(exception.message ?: "Failed to delete message")
                }
        } catch (e: Exception) {
            Log.e("BaseViewModel", "Error in deleteMessage: ${e.message}", e)
            onFailure(e.message ?: "Failed to delete message")
        }
    }


    fun fetchLastMessageForChat(
        senderPhoneNumber: String,
        receiverPhoneNumber: String,
        onLastMessageFetched: (String, String) -> Unit
    ) {
        val chatRef = FirebaseDatabase.getInstance().reference
            .child("messages")
            .child(senderPhoneNumber)
            .child(receiverPhoneNumber)

        chatRef.orderByChild("timestamp").limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            val lastMessageChild = snapshot.children.firstOrNull()
                            if (lastMessageChild != null) {
                                val lastMessage = lastMessageChild.child("message").value as? String
                                
                                // Handle timestamp - it might be Long or String
                                val timestampValue = lastMessageChild.child("timestamp").value
                                val timestamp = when (timestampValue) {
                                    is Long -> timestampValue.toString()
                                    is String -> timestampValue
                                    else -> null
                                }
                                
                                onLastMessageFetched(lastMessage ?: "No message", timestamp ?: "--:--")
                            } else {
                                onLastMessageFetched("No message", "--:--")
                            }
                        } else {
                            onLastMessageFetched("No message", "--:--")
                        }
                    } catch (e: Exception) {
                        Log.e("BaseViewModel", "Error fetching last message: ${e.message}", e)
                        onLastMessageFetched("No message", "--:--")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BaseViewModel", "Error fetching last message: ${error.message}")
                    onLastMessageFetched("No message", "--:--")
                }
            })
    }


    fun loadChatList(
        currentUserPhoneNumber: String,
        onChatListLoaded: (List<ChatListModel>) -> Unit
    ) {

        val chatList = mutableListOf<ChatListModel>()
        val chatRef = FirebaseDatabase.getInstance().reference
            .child("chats")
            .child(currentUserPhoneNumber)

        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                if (snapshot.exists()) {
                    snapshot.children.forEach { child ->

                        val phoneNumber = child.key ?: return@forEach
                        val name = child.child("name").value as? String ?: "Unknown"
                        val image = child.child("image").value as? String

                        val profileImageBitmap = image?.let { decodeBase64ToBitmap(it) }

                        fetchLastMessageForChat(
                            currentUserPhoneNumber,
                            phoneNumber
                        ) { lastMessage, time ->

                            chatList.add(
                                ChatListModel(
                                    name = name,
                                    image = profileImageBitmap,
                                    message = lastMessage,
                                    time = time
                                )
                            )

                            if (chatList.size == snapshot.childrenCount.toInt()) {
                                onChatListLoaded(chatList)
                            }
                        }
                    }
                } else {
                    onChatListLoaded(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onChatListLoaded(emptyList())
            }
        })

    }


    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64ToBitmap(base64Image: String): Bitmap? {
        return try {
            val decodedByte =
                android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)

            BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
        } catch (e: Exception) {
            null
        }
    }


    @OptIn(ExperimentalEncodingApi::class)
    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedByte =
                android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)

            val inputStream: InputStream = ByteArrayInputStream(decodedByte)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    fun getUserNameByPhoneNumber(phoneNumber: String, callback: (String?) -> Unit) {
        val databaseReference = FirebaseDatabase.getInstance().getReference("users")
        databaseReference.orderByChild("phoneNumber").equalTo(phoneNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            val firstChild = snapshot.children.firstOrNull()
                            if (firstChild != null) {
                                try {
                                    // Safely get name field
                                    val name = firstChild.child("name").value as? String
                                    callback(name)
                                } catch (e: Exception) {
                                    Log.e("BaseViewModel", "Error parsing user name: ${e.message}", e)
                                    callback(null)
                                }
                            } else {
                                callback(null)
                            }
                        } else {
                            callback(null)
                        }
                    } catch (e: Exception) {
                        Log.e("BaseViewModel", "Error processing user name fetch: ${e.message}", e)
                        callback(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BaseViewModel", "Error fetching user name: ${error.message}")
                    callback(null)
                }
            })
    }

    fun getCurrentUserPhoneNumber(callback: (String?) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            callback(null)
            return
        }
        
        // First try to get from FirebaseAuth
        val phoneFromAuth = currentUser.phoneNumber
        if (!phoneFromAuth.isNullOrEmpty()) {
            callback(phoneFromAuth)
            return
        }
        
        // If not available, get from Firebase database
        val userId = currentUser.uid
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.exists()) {
                        val phoneNumber = snapshot.child("phoneNumber").value as? String
                        callback(phoneNumber)
                    } else {
                        callback(null)
                    }
                } catch (e: Exception) {
                    Log.e("BaseViewModel", "Error getting current user phone: ${e.message}", e)
                    callback(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BaseViewModel", "Error fetching current user phone: ${error.message}")
                callback(null)
            }
        })
    }

    fun checkAndPreloadSampleContacts(currentUserId: String) {
        val chatRef = FirebaseDatabase.getInstance().getReference("chats")
        
        // Check if user already has any chats
        chatRef.orderByChild("userId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Only add sample contacts if user has no existing chats
                    if (!snapshot.exists() || snapshot.children.count() == 0) {
                        preloadSampleContacts(currentUserId)
                    } else {
                        Log.d("BaseViewModel", "User already has chats, skipping sample contacts")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BaseViewModel", "Error checking existing chats: ${error.message}")
                    // If check fails, don't add sample contacts to avoid duplicates
                }
            })
    }

    private fun preloadSampleContacts(currentUserId: String) {
        val sampleContacts = listOf(
            ChatListModel(
                name = "Muhammad Ahmad",
                phoneNumber = "+1234567890",
                userId = currentUserId,
                message = "Hey! How are you?",
                time = "10:30 AM"
            ),
            ChatListModel(
                name = "Muhammad Harib",
                phoneNumber = "+1234567891",
                userId = currentUserId,
                message = "See you tomorrow!",
                time = "Yesterday"
            ),
            ChatListModel(
                name = "Taimoor Arshad",
                phoneNumber = "+1234567892",
                userId = currentUserId,
                message = "Thanks for the help!",
                time = "2 days ago"
            ),
            ChatListModel(
                name = "Abdus Salam",
                phoneNumber = "+1234567893",
                userId = currentUserId,
                message = "Can we meet today?",
                time = "3 days ago"
            )
        )

        sampleContacts.forEach { contact ->
            val chatRef = FirebaseDatabase.getInstance().getReference("chats").push()
            val chatWithUser = contact.copy(userId = currentUserId)
            chatRef.setValue(chatWithUser).addOnSuccessListener {
                Log.d("BaseViewModel", "Sample contact ${contact.name} added successfully")
            }.addOnFailureListener { exception ->
                Log.e("BaseViewModel", "Error adding sample contact: ${exception.message}")
            }
        }
    }

}