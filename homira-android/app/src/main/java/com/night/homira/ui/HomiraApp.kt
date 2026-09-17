package com.night.homira.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class HomiraTab { Keypad, Recents, Contacts, Me }
private enum class HomiraOverlay { None, Settings, EditProfile }

private data class PersonCard(
    val name: String,
    val marker: String,
    val accent: Color,
    val subtitle: String,
    val number: String
)

private data class RecentCall(
    val person: PersonCard,
    val whenText: String,
    val duration: String,
    val incoming: Boolean,
    val video: Boolean,
    val missed: Boolean = false
)

private val mimi = PersonCard("MiMi", "✿", HomiraPink, "Homira", "+234 803 124 5678")
private val hex = PersonCard("Hex", "⚡", HomiraBlue, "Homira", "+234 806 734 2011")
private val ada = PersonCard("Ada", "A", HomiraGreen, "Homira", "+234 802 440 1812")
private val tobi = PersonCard("Tobi", "T", Color(0xFFFFC46B), "Homira", "+234 809 220 4300")

private val people = listOf(mimi, hex, ada, tobi)

private val recentCalls = listOf(
    RecentCall(mimi, "Today, 04:31", "28m", incoming = true, video = false),
    RecentCall(hex, "Yesterday, 22:18", "1h 12m", incoming = false, video = true),
    RecentCall(mimi, "Monday, 19:46", "Missed", incoming = true, video = true, missed = true),
    RecentCall(ada, "Sunday, 13:03", "46m", incoming = true, video = false),
    RecentCall(tobi, "Saturday, 21:11", "9m", incoming = false, video = false)
)

@Composable
fun HomiraApp() {
    HomiraTheme {
        var tab by rememberSaveable { mutableStateOf(HomiraTab.Keypad) }
        var overlay by rememberSaveable { mutableStateOf(HomiraOverlay.None) }
        var activePerson by remember { mutableStateOf<PersonCard?>(null) }
        var activeVideo by rememberSaveable { mutableStateOf(false) }
        var callMinimized by rememberSaveable { mutableStateOf(false) }

        var profileName by rememberSaveable { mutableStateOf("Dawson") }
        var profileUsername by rememberSaveable { mutableStateOf("dawson") }
        var profileAbout by rememberSaveable { mutableStateOf("Available after 6") }
        var profileEmail by rememberSaveable { mutableStateOf("dawson@example.com") }
        var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
        var callCardUri by rememberSaveable { mutableStateOf<String?>(null) }

        val person = activePerson
        when {
            person != null && !callMinimized -> {
                ActiveCallScreen(
                    person = person,
                    startsWithVideo = activeVideo,
                    onMinimize = { callMinimized = true },
                    onEnd = {
                        activePerson = null
                        callMinimized = false
                    }
                )
            }

            overlay == HomiraOverlay.Settings -> SettingsScreen(onBack = { overlay = HomiraOverlay.None })

            overlay == HomiraOverlay.EditProfile -> EditProfileScreen(
                name = profileName,
                username = profileUsername,
                about = profileAbout,
                email = profileEmail,
                avatarUri = avatarUri,
                callCardUri = callCardUri,
                onBack = { overlay = HomiraOverlay.None },
                onSave = { name, username, about, email, avatar, callCard ->
                    profileName = name
                    profileUsername = username
                    profileAbout = about
                    profileEmail = email
                    avatarUri = avatar
                    callCardUri = callCard
                    overlay = HomiraOverlay.None
                }
            )

            else -> {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = HomiraBackground,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        HomiraBottomBar(selected = tab, onSelect = { tab = it })
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedVisibility(visible = person != null && callMinimized) {
                            OngoingCallBanner(
                                person = person ?: mimi,
                                onReturn = { callMinimized = false },
                                onEnd = {
                                    activePerson = null
                                    callMinimized = false
                                }
                            )
                        }

                        AnimatedContent(
                            targetState = tab,
                            label = "homiraMainTab",
                            modifier = Modifier.fillMaxSize()
                        ) { current ->
                            when (current) {
                                HomiraTab.Keypad -> KeypadScreen(
                                    onSettings = { overlay = HomiraOverlay.Settings },
                                    onStartCall = { dialed ->
                                        val matched = people.firstOrNull {
                                            digitsOnly(it.number).endsWith(digitsOnly(dialed).takeLast(10)) && digitsOnly(dialed).length >= 7
                                        }
                                        activePerson = matched ?: PersonCard(
                                            name = dialed.ifBlank { "Unknown" },
                                            marker = "#",
                                            accent = HomiraGreen,
                                            subtitle = "Phone number",
                                            number = dialed
                                        )
                                        activeVideo = false
                                        callMinimized = false
                                    }
                                )

                                HomiraTab.Recents -> RecentsScreen(
                                    onSettings = { overlay = HomiraOverlay.Settings },
                                    onStartCall = { selected, video ->
                                        activePerson = selected
                                        activeVideo = video
                                        callMinimized = false
                                    }
                                )

                                HomiraTab.Contacts -> ContactsScreen(
                                    onSettings = { overlay = HomiraOverlay.Settings },
                                    onStartCall = { selected, video ->
                                        activePerson = selected
                                        activeVideo = video
                                        callMinimized = false
                                    }
                                )

                                HomiraTab.Me -> MeScreen(
                                    name = profileName,
                                    username = profileUsername,
                                    about = profileAbout,
                                    email = profileEmail,
                                    avatarUri = avatarUri,
                                    callCardUri = callCardUri,
                                    onEdit = { overlay = HomiraOverlay.EditProfile },
                                    onSettings = { overlay = HomiraOverlay.Settings }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainHeader(title: String, onSettings: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = HomiraText, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = HomiraText)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Blocked people") },
                    leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                    onClick = { menuOpen = false }
                )
            }
        }
    }
}

@Composable
private fun KeypadScreen(onSettings: () -> Unit, onStartCall: (String) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    val match = people.firstOrNull {
        number.length >= 7 && digitsOnly(it.number).endsWith(digitsOnly(number).takeLast(10))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        MainHeader("Phone", onSettings)
        Spacer(Modifier.height(20.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (number.isBlank()) "Enter number" else formatDialNumber(number),
                    color = if (number.isBlank()) HomiraMuted else HomiraText,
                    fontSize = if (number.isBlank()) 20.sp else 29.sp,
                    fontWeight = if (number.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                if (match != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(match, 28)
                        Spacer(Modifier.width(8.dp))
                        Text("${match.name} · Homira", color = match.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (number.length >= 7) {
                    Text("Not in your Homira contacts", color = HomiraMuted, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        DialPad(
            onDigit = { if (number.length < 18) number += it },
            onDelete = { if (number.isNotEmpty()) number = number.dropLast(1) }
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(58.dp))
            Spacer(Modifier.width(34.dp))
            Surface(
                modifier = Modifier
                    .size(68.dp)
                    .clickable(enabled = number.isNotBlank()) { onStartCall(number) },
                shape = CircleShape,
                color = if (number.isNotBlank()) HomiraGreen else HomiraSurfaceRaised
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Call, contentDescription = "Call", tint = if (number.isNotBlank()) Color.Black else HomiraMuted, modifier = Modifier.size(29.dp))
                }
            }
            Spacer(Modifier.width(34.dp))
            IconButton(onClick = { if (number.isNotEmpty()) number = number.dropLast(1) }, modifier = Modifier.size(58.dp)) {
                Icon(Icons.Rounded.Backspace, contentDescription = "Delete digit", tint = if (number.isNotBlank()) HomiraText else Color.Transparent)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun DialPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    Surface(
                        modifier = Modifier
                            .size(74.dp)
                            .clickable {
                                if (key.first == "0" && key.second == "+") onDigit("0") else onDigit(key.first)
                            },
                        shape = CircleShape,
                        color = HomiraSurface
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(key.first, color = HomiraText, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                            if (key.second.isNotBlank()) Text(key.second, color = HomiraMuted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentsScreen(onSettings: () -> Unit, onStartCall: (PersonCard, Boolean) -> Unit) {
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    val visibleCalls = if (missedOnly) recentCalls.filter { it.missed } else recentCalls

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { MainHeader("Recents", onSettings) }
        item {
            Text("Quick call", color = HomiraMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                people.forEach { person ->
                    QuickCallChip(person = person, onVoice = { onStartCall(person, false) }, onVideo = { onStartCall(person, true) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Call history", color = HomiraText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FilterPill("All", !missedOnly) { missedOnly = false }
                Spacer(Modifier.width(8.dp))
                FilterPill("Missed", missedOnly) { missedOnly = true }
            }
        }
        items(visibleCalls) { call ->
            RecentCallRow(call = call, onClick = { onStartCall(call.person, call.video) })
        }
    }
}

@Composable
private fun QuickCallChip(person: PersonCard, onVoice: () -> Unit, onVideo: () -> Unit) {
    Card(
        modifier = Modifier.width(154.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomiraSurface)
    ) {
        Column(Modifier.padding(14.dp)) {
            PersonAvatar(person, 52)
            Spacer(Modifier.height(10.dp))
            Text(person.name, color = HomiraText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(person.subtitle, color = HomiraMuted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniCallButton(Icons.Rounded.Call, person.accent, "Voice call", onVoice)
                MiniCallButton(Icons.Rounded.Videocam, person.accent, "Video call", onVideo)
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(99.dp),
        color = if (selected) HomiraText else HomiraSurface
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (selected) HomiraBackground else HomiraMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ContactsScreen(onSettings: () -> Unit, onStartCall: (PersonCard, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = people.filter { it.name.contains(query, ignoreCase = true) || it.number.contains(query) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { MainHeader("Contacts", onSettings) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search people or numbers") },
                shape = RoundedCornerShape(20.dp)
            )
        }
        items(filtered) { person ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonAvatar(person, 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, color = HomiraText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(person.number, color = HomiraMuted, fontSize = 13.sp)
                }
                MiniCallButton(Icons.Rounded.Call, HomiraGreen, "Voice call") { onStartCall(person, false) }
                Spacer(Modifier.width(7.dp))
                MiniCallButton(Icons.Rounded.Videocam, HomiraBlue, "Video call") { onStartCall(person, true) }
            }
        }
    }
}

@Composable
private fun MeScreen(
    name: String,
    username: String,
    about: String,
    email: String,
    avatarUri: String?,
    callCardUri: String?,
    onEdit: () -> Unit,
    onSettings: () -> Unit
) {
    val avatarBitmap = rememberBitmap(avatarUri)
    val callCardBitmap = rememberBitmap(callCardUri)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { MainHeader("Me", onSettings) }

        item {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = HomiraSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .background(HomiraSurfaceRaised)
                ) {
                    if (callCardBitmap != null) {
                        Image(
                            bitmap = callCardBitmap,
                            contentDescription = "Call card",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Add a call card", color = HomiraMuted, fontSize = 13.sp)
                        }
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = "Profile photo",
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(modifier = Modifier.size(96.dp), shape = CircleShape, color = HomiraGreen.copy(alpha = .15f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(name.take(1).uppercase(), color = HomiraGreen, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Surface(modifier = Modifier.size(30.dp), shape = CircleShape, color = HomiraGreen) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(name, color = HomiraText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("@$username", color = HomiraMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(about, color = HomiraText.copy(alpha = .82f), fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HomiraText, contentColor = HomiraBackground)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit profile", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            SectionTitle("Your profile")
            InfoRow(Icons.Rounded.Phone, "Phone number", "+234 ••• ••••")
            InfoRow(Icons.Rounded.Person, "Username", "@$username")
            InfoRow(Icons.Rounded.Email, "Email", email)
            InfoRow(Icons.Rounded.Info, "About", about)
        }

        item {
            SectionTitle("Profile media")
            InfoRow(Icons.Rounded.AddAPhoto, "Profile photo", if (avatarUri == null) "Add photo" else "Photo selected", onClick = onEdit)
            InfoRow(Icons.Rounded.CameraAlt, "Call card", if (callCardUri == null) "Add image" else "Image selected", onClick = onEdit)
        }
    }
}

@Composable
private fun EditProfileScreen(
    name: String,
    username: String,
    about: String,
    email: String,
    avatarUri: String?,
    callCardUri: String?,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String?, String?) -> Unit
) {
    var editedName by rememberSaveable { mutableStateOf(name) }
    var editedUsername by rememberSaveable { mutableStateOf(username) }
    var editedAbout by rememberSaveable { mutableStateOf(about) }
    var editedEmail by rememberSaveable { mutableStateOf(email) }
    var editedAvatar by rememberSaveable { mutableStateOf(avatarUri) }
    var editedCallCard by rememberSaveable { mutableStateOf(callCardUri) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) editedAvatar = uri.toString()
    }
    val callCardPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) editedCallCard = uri.toString()
    }

    val avatarBitmap = rememberBitmap(editedAvatar)
    val callCardBitmap = rememberBitmap(editedCallCard)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText) }
                Text("Edit profile", color = HomiraText, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    onSave(editedName, editedUsername, editedAbout, editedEmail, editedAvatar, editedCallCard)
                }) {
                    Text("Save", color = HomiraGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = HomiraSurface)) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable { callCardPicker.launch("image/*") }
                            .background(HomiraSurfaceRaised),
                        contentAlignment = Alignment.Center
                    ) {
                        if (callCardBitmap != null) {
                            Image(bitmap = callCardBitmap, contentDescription = "Call card", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = HomiraMuted)
                                Spacer(Modifier.height(6.dp))
                                Text("Choose call card image", color = HomiraMuted, fontSize = 13.sp)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(18.dp)
                            .clickable { avatarPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(bitmap = avatarBitmap, contentDescription = "Avatar", modifier = Modifier.size(88.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Surface(modifier = Modifier.size(88.dp), shape = CircleShape, color = HomiraGreen.copy(alpha = .13f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = HomiraGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { ProfileTextField("Name", editedName) { editedName = it } }
        item { ProfileTextField("Username", editedUsername) { editedUsername = it.replace(" ", "").lowercase() } }
        item { ProfileTextField("About", editedAbout) { editedAbout = it } }
        item { ProfileTextField("Email", editedEmail) { editedEmail = it } }
    }
}

@Composable
private fun ProfileTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = label != "About",
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    var lowData by rememberSaveable { mutableStateOf(false) }
    var protectIp by rememberSaveable { mutableStateOf(false) }
    var callNotifications by rememberSaveable { mutableStateOf(true) }
    var darkMode by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText) }
                Text("Settings", color = HomiraText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionTitle("Calls")
            SettingsToggle(Icons.Rounded.DataSaverOn, "Use less data for calls", "Reduce call bitrate on mobile data", lowData) { lowData = it }
            SettingsRow(Icons.Rounded.PhoneInTalk, "Call quality", "Automatic")
            SettingsRow(Icons.Rounded.Notifications, "Ringtone", "Default")
        }

        item {
            SectionTitle("Privacy")
            SettingsToggle(Icons.Rounded.Lock, "Protect IP in calls", "Route calls through TURN when enabled", protectIp) { protectIp = it }
            SettingsRow(Icons.Rounded.Block, "Blocked people", "Manage")
            SettingsRow(Icons.Rounded.Security, "Account security", "PIN and recovery")
        }

        item {
            SectionTitle("Notifications")
            SettingsToggle(Icons.Rounded.Notifications, "Call notifications", "Incoming and missed calls", callNotifications) { callNotifications = it }
        }

        item {
            SectionTitle("Appearance")
            SettingsToggle(Icons.Rounded.Palette, "Dark appearance", "Use Homira's dark theme", darkMode) { darkMode = it }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = HomiraMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HomiraText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, color = HomiraMuted, fontSize = 13.sp)
        }
    }
    HorizontalDivider(color = HomiraLine.copy(alpha = .6f))
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String) {
    InfoRow(icon, title, subtitle, onClick = {})
}

@Composable
private fun SettingsToggle(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HomiraText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = HomiraMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
    HorizontalDivider(color = HomiraLine.copy(alpha = .6f))
}

@Composable
private fun PersonAvatar(person: PersonCard, size: Int) {
    Surface(modifier = Modifier.size(size.dp), shape = CircleShape, color = person.accent.copy(alpha = .13f)) {
        Box(contentAlignment = Alignment.Center) {
            Text(person.marker, color = person.accent, fontSize = (size * .38f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MiniCallButton(icon: ImageVector, accent: Color, label: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(42.dp).clickable(onClick = onClick), shape = CircleShape, color = accent.copy(alpha = .13f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun RecentCallRow(call: RecentCall, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(call.person, 50)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(call.person.name, color = if (call.missed) HomiraDanger else HomiraText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (call.incoming) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
                    contentDescription = null,
                    tint = if (call.missed) HomiraDanger else HomiraMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text("${call.whenText}  •  ${call.duration}", color = if (call.missed) HomiraDanger else HomiraMuted, fontSize = 13.sp)
            }
        }
        Icon(if (call.video) Icons.Rounded.Videocam else Icons.Rounded.Call, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun HomiraBottomBar(selected: HomiraTab, onSelect: (HomiraTab) -> Unit) {
    NavigationBar(containerColor = HomiraBackground, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
        NavigationBarItem(
            selected = selected == HomiraTab.Keypad,
            onClick = { onSelect(HomiraTab.Keypad) },
            icon = { Icon(Icons.Rounded.Dialpad, contentDescription = null) },
            label = { Text("Keypad") }
        )
        NavigationBarItem(
            selected = selected == HomiraTab.Recents,
            onClick = { onSelect(HomiraTab.Recents) },
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
            label = { Text("Recents") }
        )
        NavigationBarItem(
            selected = selected == HomiraTab.Contacts,
            onClick = { onSelect(HomiraTab.Contacts) },
            icon = { Icon(Icons.Rounded.Contacts, contentDescription = null) },
            label = { Text("Contacts") }
        )
        NavigationBarItem(
            selected = selected == HomiraTab.Me,
            onClick = { onSelect(HomiraTab.Me) },
            icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            label = { Text("Me") }
        )
    }
}

@Composable
private fun OngoingCallBanner(person: PersonCard, onReturn: () -> Unit, onEnd: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onReturn), color = HomiraGreen.copy(alpha = .12f)) {
        Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PhoneInTalk, contentDescription = null, tint = HomiraGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("Call with ${person.name}", color = HomiraText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onEnd, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "End call", tint = HomiraDanger, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun ActiveCallScreen(person: PersonCard, startsWithVideo: Boolean, onMinimize: () -> Unit, onEnd: () -> Unit) {
    var muted by rememberSaveable { mutableStateOf(false) }
    var speaker by rememberSaveable { mutableStateOf(false) }
    var video by rememberSaveable { mutableStateOf(startsWithVideo) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var connected by rememberSaveable { mutableStateOf(false) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(650)
        connected = true
        while (true) {
            delay(1_000)
            seconds += 1
        }
    }
    LaunchedEffect(video, controlsVisible) {
        if (video && controlsVisible) {
            delay(3_500)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (video) Color.Black else HomiraBackground)
            .safeDrawingPadding()
            .clickable { if (video) controlsVisible = true }
    ) {
        if (video) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera preview", color = HomiraMuted, fontSize = 14.sp)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = !video || controlsVisible) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMinimize) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize call", tint = HomiraText)
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = HomiraText)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (sharing) "Stop sharing screen" else "Share screen") },
                                leadingIcon = { Icon(Icons.Rounded.ScreenShare, contentDescription = null) },
                                onClick = {
                                    sharing = !sharing
                                    menuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Call info") },
                                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                onClick = { menuOpen = false }
                            )
                        }
                    }
                }
            }

            if (!video) {
                Spacer(Modifier.height(12.dp))
                Text(person.name, color = HomiraText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(if (connected) formatDuration(seconds) else "Connecting…", color = HomiraMuted, fontSize = 15.sp)
                Spacer(Modifier.weight(.32f))
                PersonAvatar(person, 184)
                Spacer(Modifier.weight(.32f))
            } else {
                Spacer(Modifier.height(16.dp))
                AnimatedVisibility(visible = controlsVisible) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(person.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (connected) formatDuration(seconds) else "Connecting…", color = Color.White.copy(alpha = .72f), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            AnimatedVisibility(visible = muted && (!video || controlsVisible)) {
                Surface(shape = RoundedCornerShape(99.dp), color = HomiraDanger.copy(alpha = .16f)) {
                    Row(modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MicOff, contentDescription = null, tint = HomiraDanger, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("You're muted", color = HomiraText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            AnimatedVisibility(visible = sharing && (!video || controlsVisible)) {
                Text("Sharing your screen", color = HomiraGreen, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            AnimatedVisibility(visible = !video || controlsVisible) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallControl(Icons.Rounded.MicOff, "Mute", muted) { muted = !muted }
                        CallControl(Icons.Rounded.VolumeUp, "Speaker", speaker) { speaker = !speaker }
                        CallControl(Icons.Rounded.Videocam, "Video", video) { video = !video; controlsVisible = true }
                    }
                    Spacer(Modifier.height(26.dp))
                    Surface(modifier = Modifier.size(68.dp).clickable(onClick = onEnd), shape = CircleShape, color = HomiraDanger) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CallControl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(56.dp).clickable(onClick = onClick), shape = CircleShape, color = if (active) HomiraText else HomiraSurfaceRaised) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = if (active) HomiraBackground else HomiraText, modifier = Modifier.size(23.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = HomiraMuted, fontSize = 11.sp)
    }
}

@Composable
private fun rememberBitmap(uriString: String?) = remember(uriString) {
    if (uriString.isNullOrBlank()) return@remember null
    runCatching {
        val context = HomiraBitmapContextHolder.context
        if (context == null) null else {
            context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }
    }.getOrNull()
}

private object HomiraBitmapContextHolder {
    var context: android.content.Context? = null
}

@Composable
private fun BitmapContextBinder() {
    val context = LocalContext.current
    HomiraBitmapContextHolder.context = context
}

private fun digitsOnly(value: String): String = value.filter { it.isDigit() }

private fun formatDialNumber(value: String): String {
    val digits = value.filter { it.isDigit() }
    return when {
        digits.length <= 4 -> digits
        digits.length <= 7 -> "${digits.take(4)} ${digits.drop(4)}"
        digits.length <= 11 -> "${digits.take(4)} ${digits.drop(4).take(3)} ${digits.drop(7)}"
        else -> value
    }
}

private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
