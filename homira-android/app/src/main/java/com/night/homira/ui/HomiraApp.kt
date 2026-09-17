package com.night.homira.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class HomiraTab { Recents, Keypad, People, You }

private data class PersonCard(
    val name: String,
    val marker: String,
    val accent: Color,
    val subtitle: String
)

private data class RecentCall(
    val person: PersonCard,
    val whenText: String,
    val duration: String,
    val incoming: Boolean,
    val video: Boolean,
    val missed: Boolean = false
)

private val mimi = PersonCard("MiMi", "✿", HomiraPink, "Available")
private val hex = PersonCard("Hex", "⚡", HomiraBlue, "Available")
private val quickPeople = listOf(mimi, hex)

private val recentCalls = listOf(
    RecentCall(mimi, "Today, 04:31", "28m", incoming = true, video = false),
    RecentCall(hex, "Yesterday, 22:18", "1h 12m", incoming = false, video = true),
    RecentCall(mimi, "Monday, 19:46", "Missed", incoming = true, video = true, missed = true),
    RecentCall(hex, "Sunday, 13:03", "46m", incoming = true, video = false)
)

@Composable
fun HomiraApp() {
    HomiraTheme {
        var tab by rememberSaveable { mutableStateOf(HomiraTab.Recents) }
        var activePerson by remember { mutableStateOf<PersonCard?>(null) }
        var showCall by rememberSaveable { mutableStateOf(false) }
        var callMuted by rememberSaveable { mutableStateOf(false) }
        var callSpeaker by rememberSaveable { mutableStateOf(false) }
        var callVideo by rememberSaveable { mutableStateOf(false) }
        var callSharing by rememberSaveable { mutableStateOf(false) }
        var connected by rememberSaveable { mutableStateOf(false) }
        var seconds by rememberSaveable { mutableIntStateOf(0) }
        var displayName by rememberSaveable { mutableStateOf("You") }
        var signedIn by rememberSaveable { mutableStateOf(true) }

        fun startCall(person: PersonCard, video: Boolean) {
            activePerson = person
            showCall = true
            callMuted = false
            callSpeaker = video
            callVideo = video
            callSharing = false
            connected = false
            seconds = 0
        }

        LaunchedEffect(activePerson) {
            if (activePerson == null) return@LaunchedEffect
            delay(650)
            connected = true
            while (activePerson != null) {
                delay(1_000)
                seconds += 1
            }
        }

        val person = activePerson
        if (person != null && showCall) {
            ActiveCallScreen(
                person = person,
                muted = callMuted,
                speaker = callSpeaker,
                video = callVideo,
                sharing = callSharing,
                connected = connected,
                seconds = seconds,
                onMutedChange = { callMuted = it },
                onSpeakerChange = { callSpeaker = it },
                onVideoChange = { callVideo = it },
                onSharingChange = { callSharing = it },
                onMinimize = { showCall = false },
                onEnd = {
                    activePerson = null
                    showCall = false
                    connected = false
                }
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = HomiraBackground,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        HomiraBottomBar(selected = tab, onSelect = { tab = it })
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = tab,
                        label = "homiraTab",
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    ) { current ->
                        when (current) {
                            HomiraTab.Recents -> RecentsScreen(onStartCall = ::startCall)
                            HomiraTab.Keypad -> KeypadScreen(onDial = { number ->
                                startCall(
                                    PersonCard(number, "•", HomiraGreen, "Phone number"),
                                    false
                                )
                            })
                            HomiraTab.People -> PeopleScreen(onStartCall = ::startCall)
                            HomiraTab.You -> YouScreen(
                                displayName = displayName,
                                signedIn = signedIn,
                                onNameChange = { displayName = it },
                                onSignedInChange = { signedIn = it }
                            )
                        }
                    }
                }

                if (person != null) {
                    OngoingCallBanner(
                        person = person,
                        seconds = seconds,
                        muted = callMuted,
                        onReturn = { showCall = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentsScreen(onStartCall: (PersonCard, Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Calls",
                color = HomiraText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        item {
            Text(
                "Quick call",
                color = HomiraMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(quickPeople) { person ->
                    QuickPersonCard(
                        person = person,
                        onVoice = { onStartCall(person, false) },
                        onVideo = { onStartCall(person, true) }
                    )
                }
            }
        }
        item {
            Text(
                "Recent",
                color = HomiraText,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        items(recentCalls) { call ->
            Box(Modifier.padding(horizontal = 20.dp)) {
                RecentCallRow(call = call, onClick = { onStartCall(call.person, call.video) })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun QuickPersonCard(
    person: PersonCard,
    onVoice: () -> Unit,
    onVideo: () -> Unit
) {
    Card(
        modifier = Modifier.width(176.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = HomiraSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            MascotMarker(person = person, size = 66)
            Spacer(Modifier.height(14.dp))
            Text(person.name, color = HomiraText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(person.subtitle, color = HomiraMuted, fontSize = 13.sp)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniCallButton(Icons.Rounded.Call, person.accent, "Voice call", onVoice)
                MiniCallButton(Icons.Rounded.Videocam, person.accent, "Video call", onVideo)
            }
        }
    }
}

@Composable
private fun KeypadScreen(onDial: (String) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Keypad",
            modifier = Modifier.fillMaxWidth(),
            color = HomiraText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(.34f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (number.isEmpty()) "Enter a number" else number,
                color = if (number.isEmpty()) HomiraMuted else HomiraText,
                fontSize = if (number.isEmpty()) 20.sp else 29.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
                enabled = number.isNotEmpty()
            ) {
                Icon(Icons.Rounded.Backspace, contentDescription = "Delete digit", tint = HomiraMuted)
            }
        }
        Spacer(Modifier.height(28.dp))
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { digit ->
                    Surface(
                        modifier = Modifier.size(72.dp).clickable {
                            if (number.length < 22) number += digit
                        },
                        shape = CircleShape,
                        color = HomiraSurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(digit, color = HomiraText, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.size(72.dp).clickable(enabled = number.isNotEmpty()) {
                onDial(number)
            },
            shape = CircleShape,
            color = if (number.isNotEmpty()) HomiraGreen else HomiraSurfaceRaised
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = "Call number",
                    tint = if (number.isNotEmpty()) Color.Black else HomiraMuted,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(Modifier.weight(.24f))
    }
}

@Composable
private fun MascotMarker(person: PersonCard, size: Int) {
    Box(
        modifier = Modifier.size(size.dp).background(person.accent.copy(alpha = .12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            person.marker,
            color = person.accent,
            fontSize = (size * .42f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MiniCallButton(
    icon: ImageVector,
    accent: Color,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(43.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = accent.copy(alpha = .13f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RecentCallRow(call: RecentCall, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MascotMarker(person = call.person, size = 50)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                call.person.name,
                color = if (call.missed) HomiraDanger else HomiraText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (call.incoming) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
                    contentDescription = null,
                    tint = if (call.missed) HomiraDanger else HomiraMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "${call.whenText}  •  ${call.duration}",
                    color = if (call.missed) HomiraDanger else HomiraMuted,
                    fontSize = 13.sp
                )
            }
        }
        Icon(
            imageVector = if (call.video) Icons.Rounded.Videocam else Icons.Rounded.Call,
            contentDescription = if (call.video) "Video call" else "Voice call",
            tint = HomiraMuted,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun PeopleScreen(onStartCall: (PersonCard, Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("People", color = HomiraText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
        }
        items(quickPeople) { person ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = HomiraSurface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MascotMarker(person, 56)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, color = HomiraText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(person.subtitle, color = HomiraMuted, fontSize = 13.sp)
                    }
                    MiniCallButton(Icons.Rounded.Call, person.accent, "Voice call") {
                        onStartCall(person, false)
                    }
                    Spacer(Modifier.width(8.dp))
                    MiniCallButton(Icons.Rounded.Videocam, person.accent, "Video call") {
                        onStartCall(person, true)
                    }
                }
            }
        }
    }
}

@Composable
private fun YouScreen(
    displayName: String,
    signedIn: Boolean,
    onNameChange: (String) -> Unit,
    onSignedInChange: (Boolean) -> Unit
) {
    var editOpen by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf(displayName) }
    var detailTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var logoutConfirm by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("You", color = HomiraText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = HomiraSurface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).background(HomiraGreen.copy(alpha = .13f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = HomiraGreen, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(displayName, color = HomiraText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(if (signedIn) "Signed in" else "Not signed in", color = HomiraMuted, fontSize = 13.sp)
                    }
                    if (signedIn) {
                        IconButton(onClick = {
                            draftName = displayName
                            editOpen = true
                        }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit profile", tint = HomiraMuted)
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup(
                rows = listOf(
                    SettingItem(Icons.Rounded.Person, "Profile") {
                        draftName = displayName
                        editOpen = true
                    },
                    SettingItem(Icons.Rounded.Lock, "Account") { detailTitle = "Account" },
                    SettingItem(Icons.Rounded.Shield, "Privacy") { detailTitle = "Privacy" },
                    SettingItem(Icons.Rounded.Notifications, "Notifications") { detailTitle = "Notifications" },
                    SettingItem(Icons.Rounded.Devices, "Linked devices") { detailTitle = "Linked devices" },
                    SettingItem(Icons.Rounded.Settings, "Call settings") { detailTitle = "Call settings" }
                )
            )
        }
        item {
            TextButton(
                onClick = {
                    if (signedIn) logoutConfirm = true else onSignedInChange(true)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (signedIn) Icons.Rounded.Logout else Icons.Rounded.Person,
                    contentDescription = null,
                    tint = if (signedIn) HomiraDanger else HomiraGreen
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (signedIn) "Log out" else "Log in",
                    color = if (signedIn) HomiraDanger else HomiraGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (editOpen) {
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text("Edit profile") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(40) },
                    label = { Text("Display name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftName.isNotBlank()) onNameChange(draftName.trim())
                    editOpen = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editOpen = false }) { Text("Cancel") } }
        )
    }

    detailTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { detailTitle = null },
            title = { Text(title) },
            text = { Text("This section is ready for its real account and settings wiring.") },
            confirmButton = { TextButton(onClick = { detailTitle = null }) { Text("Done") } }
        )
    }

    if (logoutConfirm) {
        AlertDialog(
            onDismissRequest = { logoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("You can sign back in from this screen.") },
            confirmButton = {
                TextButton(onClick = {
                    onSignedInChange(false)
                    logoutConfirm = false
                }) { Text("Log out", color = HomiraDanger) }
            },
            dismissButton = { TextButton(onClick = { logoutConfirm = false }) { Text("Cancel") } }
        )
    }
}

private data class SettingItem(val icon: ImageVector, val title: String, val onClick: () -> Unit)

@Composable
private fun SettingsGroup(rows: List<SettingItem>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomiraSurface)
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = row.onClick).padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(row.icon, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(13.dp))
                Text(row.title, color = HomiraText, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("›", color = HomiraMuted, fontSize = 24.sp)
            }
            if (index != rows.lastIndex) {
                HorizontalDivider(color = HomiraSurfaceRaised, modifier = Modifier.padding(start = 50.dp))
            }
        }
    }
}

@Composable
private fun HomiraBottomBar(selected: HomiraTab, onSelect: (HomiraTab) -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        NavigationBar(
            containerColor = HomiraSurface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                selected = selected == HomiraTab.People,
                onClick = { onSelect(HomiraTab.People) },
                icon = { Icon(Icons.Rounded.PeopleAlt, contentDescription = null) },
                label = { Text("People") }
            )
            NavigationBarItem(
                selected = selected == HomiraTab.You,
                onClick = { onSelect(HomiraTab.You) },
                icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                label = { Text("You") }
            )
        }
    }
}

@Composable
private fun OngoingCallBanner(
    person: PersonCard,
    seconds: Int,
    muted: Boolean,
    onReturn: () -> Unit
) {
    Surface(
        modifier = Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clickable(onClick = onReturn),
        shape = RoundedCornerShape(20.dp),
        color = HomiraSurfaceRaised
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MascotMarker(person, 38)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(person.name, color = HomiraText, fontWeight = FontWeight.SemiBold)
                Text(formatDuration(seconds), color = HomiraMuted, fontSize = 12.sp)
            }
            if (muted) {
                Icon(Icons.Rounded.MicOff, contentDescription = "Muted", tint = HomiraDanger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text("Return", color = HomiraGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ActiveCallScreen(
    person: PersonCard,
    muted: Boolean,
    speaker: Boolean,
    video: Boolean,
    sharing: Boolean,
    connected: Boolean,
    seconds: Int,
    onMutedChange: (Boolean) -> Unit,
    onSpeakerChange: (Boolean) -> Unit,
    onVideoChange: (Boolean) -> Unit,
    onSharingChange: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    onEnd: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

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
            .clickable { if (video) controlsVisible = !controlsVisible }
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = !video || controlsVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMinimize) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize call", tint = HomiraText)
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More call options", tint = HomiraText)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (sharing) "Stop sharing screen" else "Share screen") },
                                leadingIcon = { Icon(Icons.Rounded.ScreenShare, contentDescription = null) },
                                onClick = {
                                    onSharingChange(!sharing)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Call info") },
                                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                onClick = {
                                    showInfo = true
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (video) 6.dp else 12.dp))
            AnimatedVisibility(visible = !video || controlsVisible) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(person.name, color = HomiraText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (connected) formatDuration(seconds) else "Connecting…",
                        color = if (connected) HomiraMuted else person.accent,
                        fontSize = 15.sp
                    )
                }
            }

            if (muted) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(99.dp), color = HomiraDanger.copy(alpha = .14f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MicOff, contentDescription = null, tint = HomiraDanger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("You’re muted", color = HomiraDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (sharing) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(99.dp), color = HomiraGreen.copy(alpha = .12f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ScreenShare, contentDescription = null, tint = HomiraGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sharing your screen", color = HomiraGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.weight(.30f))
            ActiveMascotStage(person = person, video = video)
            Spacer(Modifier.weight(.30f))

            AnimatedVisibility(visible = !video || controlsVisible) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallControl(Icons.Rounded.MicOff, if (muted) "Unmute" else "Mute", muted) {
                            onMutedChange(!muted)
                        }
                        CallControl(Icons.Rounded.VolumeUp, "Speaker", speaker) {
                            onSpeakerChange(!speaker)
                        }
                        CallControl(Icons.Rounded.Videocam, if (video) "Video off" else "Video", video) {
                            onVideoChange(!video)
                            controlsVisible = true
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    Surface(
                        modifier = Modifier.size(70.dp).clickable(onClick = onEnd),
                        shape = CircleShape,
                        color = HomiraDanger
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(31.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Call info") },
            text = {
                Text(
                    "${person.name}\n${if (video) "Video call" else "Voice call"}\n${formatDuration(seconds)}${if (muted) "\nMicrophone muted" else ""}${if (sharing) "\nScreen sharing on" else ""}"
                )
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun ActiveMascotStage(person: PersonCard, video: Boolean) {
    Box(
        modifier = Modifier
            .size(if (video) 250.dp else 205.dp)
            .background(person.accent.copy(alpha = if (video) .18f else .12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(person.marker, color = person.accent, fontSize = 68.sp, fontWeight = FontWeight.Bold)
            Text(
                "${person.name} artwork slot",
                color = person.accent.copy(alpha = .9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CallControl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(58.dp).clickable(onClick = onClick),
            shape = CircleShape,
            color = if (active) HomiraText else HomiraSurfaceRaised
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) HomiraBackground else HomiraText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = HomiraMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%02d:%02d".format(minutes, remaining)
}
