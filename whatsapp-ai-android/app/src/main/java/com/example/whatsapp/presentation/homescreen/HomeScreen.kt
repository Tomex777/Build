package com.example.whatsapp.presentation.homescreen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavHostController
import com.example.whatsapp.R
import com.example.whatsapp.presentation.bottomnavigation.BottomNavigationBar
import com.example.whatsapp.presentation.chat_box.ChatListBox
import com.example.whatsapp.presentation.chat_box.ChatListModel
import com.example.whatsapp.presentation.navigation.Routes
import com.example.whatsapp.presentation.viewmodels.BaseViewModel
import com.google.firebase.auth.FirebaseAuth
import android.util.Log


@Composable
fun HomeScreen(navHostController: NavHostController, homeBaseViewModel: BaseViewModel) {

    var showPopup by remember {
        mutableStateOf(false)
    }

    val chatData by homeBaseViewModel.chatList.collectAsState()

    val userId = FirebaseAuth.getInstance().currentUser?.uid

    // Log chat data for debugging
    LaunchedEffect(chatData.size) {
        Log.d("HomeScreen", "Chat list updated: ${chatData.size} chats available")
        chatData.forEachIndexed { index, chat ->
            Log.d(
                "HomeScreen",
                "Chat $index: name=${chat.name}, phoneNumber=${chat.phoneNumber}, userId=${chat.userId}"
            )
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var hasCheckedSampleContacts by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Load sample contacts only once per user (check if chats already exist)
    LaunchedEffect(userId, chatData.size) {
        if (userId != null && !hasCheckedSampleContacts) {
            hasCheckedSampleContacts = true
            // Check if user already has chats before adding sample contacts
            homeBaseViewModel.checkAndPreloadSampleContacts(userId)
        }
    }

    Scaffold(
        floatingActionButton = {
            // Add Contact FAB (primary)
            FloatingActionButton(
                onClick = { showPopup = true },
                containerColor = colorResource(id = R.color.light_green),
                modifier = Modifier.size(size = 65.dp),
                contentColor = Color.White
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.add_chat_icon),
                    contentDescription = "Add Contact",
                    modifier = Modifier.size(size = 28.dp),
                    tint = Color.White
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(
                navHostController = navHostController,
                selectedItem = 0,
                onClick = { index ->
                    when (index) {
                        0 -> {
                            if (navHostController.currentDestination?.route != Routes.HomeScreen.route) {
                                navHostController.navigate(Routes.HomeScreen.route)
                            }
                        }

                        1 -> {
                            if (navHostController.currentDestination?.route != Routes.UpdateScreen.route) {
                                navHostController.navigate(Routes.UpdateScreen.route)
                            }
                        }

                        2 -> {
                            if (navHostController.currentDestination?.route != Routes.CommunitiesScreen.route) {
                                navHostController.navigate(Routes.CommunitiesScreen.route)
                            }
                        }

                        3 -> {
                            if (navHostController.currentDestination?.route != Routes.CallScreen.route) {
                                navHostController.navigate(Routes.CallScreen.route)
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        var searchText by remember { mutableStateOf("") }

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = MaterialTheme.colorScheme.surface)
            ) {
                // Top App Bar Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.surface)
                ) {
                    // Top App Bar Row with WhatsApp text and icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "WhatsApp",
                            fontSize = 32.sp,
                            color = colorResource(R.color.light_green),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        Row {
                            IconButton(onClick = {
                                android.widget.Toast.makeText(
                                    context,
                                    "Camera feature coming soon",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.camera),
                                    contentDescription = "Camera",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box {
                                IconButton(onClick = {
                                    showMenu = !showMenu
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.more),
                                        contentDescription = "More",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(color = MaterialTheme.colorScheme.surface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("New Group") },
                                        onClick = {
                                            showMenu = false
                                            android.widget.Toast.makeText(
                                                context,
                                                "New Group feature coming soon",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("New Broadcast") },
                                        onClick = {
                                            showMenu = false
                                            android.widget.Toast.makeText(
                                                context,
                                                "New Broadcast feature coming soon",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Linked Devices") },
                                        onClick = {
                                            showMenu = false
                                            android.widget.Toast.makeText(
                                                context,
                                                "Linked Devices feature coming soon",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Starred Messages") },
                                        onClick = {
                                            showMenu = false
                                            android.widget.Toast.makeText(
                                                context,
                                                "Starred Messages feature coming soon",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Profile") },
                                        onClick = {
                                            showMenu = false
                                            navHostController.navigate("${Routes.UserProfileSetScreen.route}/edit")
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        onClick = {
                                            showMenu = false
                                            navHostController.navigate(Routes.SettingScreen.route)
                                        }
                                    )

                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = { Text("Logout", color = Color.Red) },
                                        onClick = {
                                            showMenu = false
                                            FirebaseAuth.getInstance().signOut()
                                            val sharedPreferences = context.getSharedPreferences(
                                                "app_prefs",
                                                android.content.Context.MODE_PRIVATE
                                            )
                                            sharedPreferences.edit().putBoolean("isSignedIn", false)
                                                .apply()
                                            navHostController.navigate(Routes.WelcomeScreen.route) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Search Bar - Inside the top app bar container, below WhatsApp text
                    TextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Search", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = "Search",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.cross),
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }

                // Horizontal Divider below the entire top bar
                HorizontalDivider()

                // Chat List - starts after the divider
                if (showPopup) {
                    AddUserDialog(
                        onDismiss = { showPopup = false },
                        onUserAdd = { newUser: ChatListModel ->
                            homeBaseViewModel.addChat(newUser)
                            showPopup = false
                        },
                        baseViewModel = homeBaseViewModel
                    )
                }

                val filteredChats = if (searchText.isBlank()) {
                    chatData
                } else {
                    chatData.filter {
                        it.name?.contains(searchText, ignoreCase = true) == true ||
                                it.message?.contains(searchText, ignoreCase = true) == true
                    }
                }

                if (filteredChats.isEmpty() && searchText.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No chats yet",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap the + button to start a new chat",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        items(filteredChats) { chat ->
                            ChatListBox(
                                chatListModel = chat,
                                onClick = {
                                    // Simplified navigation - always use phoneNumber or fallback to name
                                    val phoneNum = chat.phoneNumber ?: chat.name ?: "unknown"
                                    Log.d(
                                        "HomeScreen",
                                        "Navigating to chat: ${chat.name} with identifier: $phoneNum"
                                    )

                                    try {
                                        navHostController.navigate(
                                            Routes.ChatScreen.createRoute(phoneNumber = phoneNum)
                                        )
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "Navigation error: ${e.message}", e)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Error opening chat",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                baseViewModel = homeBaseViewModel
                            )
                        }
                    }
                }
            }

            // AI Chat FAB positioned manually (bottom-right, above the primary FAB)
            FloatingActionButton(
                onClick = {
                    navHostController.navigate(Routes.AiChatScreen.route)
                },
                containerColor = colorResource(id = R.color.light_green),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 90.dp) // Position above the primary FAB
                    .size(size = 65.dp),
                contentColor = Color.White
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.meta_ai_icon),
                    contentDescription = "Meta AI",
                    modifier = Modifier.size(size = 32.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onUserAdd: (ChatListModel) -> Unit,
    baseViewModel: BaseViewModel
) {
    var phoneNumber by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var userFound by remember { mutableStateOf<ChatListModel?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add New Contact",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        errorMessage = null
                        userFound = null
                    },
                    label = { Text("Enter Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSearching,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = colorResource(R.color.light_green),
                        unfocusedIndicatorColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colorResource(R.color.light_green)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Searching...", color = Color.Gray, fontSize = 14.sp)
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }

                userFound?.let { user ->
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "User Found: ${user.name ?: "Unknown"}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = colorResource(R.color.light_green)
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (phoneNumber.isBlank()) {
                            errorMessage = "Please enter a phone number"
                            return@Button
                        }
                        isSearching = true
                        errorMessage = null
                        userFound = null
                        baseViewModel.searchUserByPhoneNumber(phoneNumber) { user ->
                            isSearching = false
                            if (user != null) {
                                userFound = user
                            } else {
                                errorMessage = "User not found"
                            }
                        }
                    },
                    enabled = !isSearching && phoneNumber.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.light_green)
                    )
                ) {
                    Text("Search")
                }
            }
        },
        dismissButton = {
            userFound?.let { user ->
                Button(
                    onClick = {
                        onUserAdd(user)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.light_green)
                    )
                ) {
                    Text("Add To Chat")
                }
            }
        }
    )
}
