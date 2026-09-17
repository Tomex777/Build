package com.night.homira.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class HomiraTab { Calls, People, You }

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

private val mimi = PersonCard(
    name = "MiMi",
    marker = "✿",
    accent = HomiraPink,
    subtitle = "Available"
)

private val hex = PersonCard(
    name = "Hex",
    marker = "⚡",
    accent = HomiraBlue,
    subtitle = "Available"
)

private val recentCalls = listOf(
    RecentCall(mimi, "Today, 04:31", "28m", incoming = true, video = false),
    RecentCall(hex, "Yesterday, 22:18", "1h 12m", incoming = false, video = true),
    RecentCall(mimi, "Monday, 19:46", "Missed", incoming = true, video = true, missed = true),
    RecentCall(hex, "Sunday, 13:03", "46m", incoming = true, video = false)
)

@Composable
fun HomiraApp() {
    HomiraTheme {
        var tab by rememberSaveable { mutableStateOf(HomiraTab.Calls) }
        var activePerson by remember { mutableStateOf<PersonCard?>(null) }
        var activeVideo by rememberSaveable { mutableStateOf(false) }

        val person = activePerson
        if (person != null) {
            ActiveCallScreen(
                person = person,
                startsWithVideo = activeVideo,
                onEnd = { activePerson = null }
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = HomiraBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    HomiraBottomBar(
                        selected = tab,
                        onSelect = { tab = it }
                    )
                }
            ) { innerPadding ->
                AnimatedContent(
                    targetState = tab,
                    label = "homiraTab",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) { current ->
                    when (current) {
                        HomiraTab.Calls -> CallsScreen(
                            onStartCall = { selectedPerson, video ->
                                activeVideo = video
                                activePerson = selectedPerson
                            }
                        )
                        HomiraTab.People -> PeopleScreen(
                            onStartCall = { selectedPerson, video ->
                                activeVideo = video
                                activePerson = selectedPerson
                            }
                        )
                        HomiraTab.You -> YouScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsScreen(onStartCall: (PersonCard, Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Homira",
                        color = HomiraText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Closer, without the noise.",
                        color = HomiraMuted,
                        fontSize = 15.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = HomiraGreen.copy(alpha = .10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = HomiraGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Private",
                            color = HomiraGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Quick call",
                color = HomiraMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                QuickPersonCard(
                    person = mimi,
                    modifier = Modifier.weight(1f),
                    onVoice = { onStartCall(mimi, false) },
                    onVideo = { onStartCall(mimi, true) }
                )
                QuickPersonCard(
                    person = hex,
                    modifier = Modifier.weight(1f),
                    onVoice = { onStartCall(hex, false) },
                    onVideo = { onStartCall(hex, true) }
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Recent",
                    modifier = Modifier.weight(1f),
                    color = HomiraText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {}) {
                    Text("See all", color = HomiraMuted)
                }
            }
        }

        items(recentCalls) { call ->
            RecentCallRow(
                call = call,
                onClick = { onStartCall(call.person, call.video) }
            )
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun QuickPersonCard(
    person: PersonCard,
    modifier: Modifier,
    onVoice: () -> Unit,
    onVideo: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "${person.name}Pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = .985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cardPulse"
    )

    Card(
        modifier = modifier.scale(pulse),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = HomiraSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            MascotMarker(person = person, size = 72)
            Spacer(Modifier.height(15.dp))
            Text(
                person.name,
                color = HomiraText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(person.subtitle, color = HomiraMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniCallButton(
                    icon = Icons.Rounded.Call,
                    accent = person.accent,
                    label = "Voice call",
                    onClick = onVoice
                )
                MiniCallButton(
                    icon = Icons.Rounded.Videocam,
                    accent = person.accent,
                    label = "Video call",
                    onClick = onVideo
                )
            }
        }
    }
}

@Composable
private fun MascotMarker(person: PersonCard, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(person.accent.copy(alpha = .12f), CircleShape),
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
        modifier = Modifier
            .size(43.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = accent.copy(alpha = .13f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RecentCallRow(call: RecentCall, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
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
            contentDescription = null,
            tint = HomiraMuted,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun PeopleScreen(onStartCall: (PersonCard, Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("People", color = HomiraText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Your small circle.", color = HomiraMuted, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
        }
        items(listOf(mimi, hex)) { person ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = HomiraSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MascotMarker(person, 56)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, color = HomiraText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Available for a call", color = HomiraMuted, fontSize = 13.sp)
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
private fun YouScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("You", color = HomiraText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Privacy first, by default.", color = HomiraMuted, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
        }

        item {
            PrivacyCard(
                icon = Icons.Rounded.Shield,
                title = "Call content stays private",
                detail = "Voice, video and screen sharing are not designed to be stored in Supabase."
            )
        }
        item {
            PrivacyCard(
                icon = Icons.Rounded.WifiTethering,
                title = "Direct when possible",
                detail = "1-to-1 calls will prefer a direct WebRTC path. TURN is only the fallback."
            )
        }
        item {
            PrivacyCard(
                icon = Icons.Rounded.History,
                title = "Local call history",
                detail = "Detailed call history is planned to live on your device instead of a central call-content log."
            )
        }
        item {
            PrivacyCard(
                icon = Icons.Rounded.Security,
                title = "Minimal metadata",
                detail = "The backend will keep only what the call setup actually needs."
            )
        }
    }
}

@Composable
private fun PrivacyCard(icon: ImageVector, title: String, detail: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomiraSurface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(HomiraGreen.copy(alpha = .10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = HomiraGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(title, color = HomiraText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(detail, color = HomiraMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun HomiraBottomBar(selected: HomiraTab, onSelect: (HomiraTab) -> Unit) {
    NavigationBar(
        containerColor = HomiraBackground,
        tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        NavigationBarItem(
            selected = selected == HomiraTab.Calls,
            onClick = { onSelect(HomiraTab.Calls) },
            icon = { Icon(Icons.Rounded.Call, contentDescription = null) },
            label = { Text("Calls") }
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

@Composable
private fun ActiveCallScreen(
    person: PersonCard,
    startsWithVideo: Boolean,
    onEnd: () -> Unit
) {
    var muted by rememberSaveable { mutableStateOf(false) }
    var speaker by rememberSaveable { mutableStateOf(false) }
    var video by rememberSaveable { mutableStateOf(startsWithVideo) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var connected by rememberSaveable { mutableStateOf(false) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(650)
        connected = true
        while (true) {
            delay(1_000)
            seconds += 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEnd) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = HomiraMuted)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(person.name, color = HomiraText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                if (connected) formatDuration(seconds) else "Connecting…",
                color = if (connected) HomiraMuted else person.accent,
                fontSize = 15.sp
            )

            Spacer(Modifier.weight(.28f))
            ActiveMascotStage(person = person, connected = connected)
            Spacer(Modifier.weight(.28f))

            AnimatedVisibility(visible = sharing) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = HomiraSurfaceRaised
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ScreenShare,
                            contentDescription = null,
                            tint = HomiraGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Screen sharing preview", color = HomiraText, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControl(Icons.Rounded.MicOff, "Mute", muted) { muted = !muted }
                CallControl(Icons.Rounded.VolumeUp, "Speaker", speaker) { speaker = !speaker }
                CallControl(Icons.Rounded.Videocam, "Video", video) { video = !video }
                CallControl(Icons.Rounded.ScreenShare, "Share", sharing) { sharing = !sharing }
            }

            Spacer(Modifier.height(28.dp))
            Surface(
                modifier = Modifier
                    .size(70.dp)
                    .clickable(onClick = onEnd),
                shape = CircleShape,
                color = HomiraDanger
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(31.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActiveMascotStage(person: PersonCard, connected: Boolean) {
    val transition = rememberInfiniteTransition(label = "callAnimation")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_350, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring"
    )
    val barOne by transition.animateFloat(
        initialValue = .35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "bar1"
    )
    val barTwo by transition.animateFloat(
        initialValue = 1f,
        targetValue = .42f,
        animationSpec = infiniteRepeatable(tween(790), RepeatMode.Reverse),
        label = "bar2"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(ringScale)
                    .alpha(if (connected) .28f else .14f)
                    .background(person.accent.copy(alpha = .16f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(184.dp)
                    .background(person.accent.copy(alpha = .12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        person.marker,
                        color = person.accent,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${person.name} artwork slot",
                        color = person.accent.copy(alpha = .9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(barOne, barTwo, .72f, barTwo, barOne).forEach { level ->
                Box(
                    Modifier
                        .width(5.dp)
                        .height((10 + 23 * level).dp)
                        .background(person.accent, RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun CallControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(55.dp)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = if (active) HomiraText else HomiraSurfaceRaised
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) HomiraBackground else HomiraText,
                    modifier = Modifier.size(23.dp)
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
