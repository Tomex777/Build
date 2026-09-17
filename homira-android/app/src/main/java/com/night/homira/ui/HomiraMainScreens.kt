package com.night.homira.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun KeypadScreen(onSettings: () -> Unit, onStartCall: (String) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    val match = homiraPeople.firstOrNull {
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
        DialPad(onDigit = { if (number.length < 18) number += it })
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
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = "Call",
                        tint = if (number.isNotBlank()) Color.Black else HomiraMuted,
                        modifier = Modifier.size(29.dp)
                    )
                }
            }
            Spacer(Modifier.width(34.dp))
            IconButton(onClick = { if (number.isNotEmpty()) number = number.dropLast(1) }, modifier = Modifier.size(58.dp)) {
                Icon(
                    Icons.Rounded.Backspace,
                    contentDescription = "Delete digit",
                    tint = if (number.isNotBlank()) HomiraText else Color.Transparent
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun DialPad(onDigit: (String) -> Unit) {
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
                        modifier = Modifier.size(74.dp).clickable { onDigit(key.first) },
                        shape = CircleShape,
                        color = HomiraSurface
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(key.first, color = HomiraText, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                            if (key.second.isNotBlank()) {
                                Text(key.second, color = HomiraMuted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecentsScreen(onSettings: () -> Unit, onStartCall: (PersonCard, Boolean) -> Unit) {
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    val visibleCalls = if (missedOnly) homiraRecentCalls.filter { it.missed } else homiraRecentCalls

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
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
                homiraPeople.forEach { person ->
                    QuickCallChip(
                        person = person,
                        onVoice = { onStartCall(person, false) },
                        onVideo = { onStartCall(person, true) }
                    )
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
private fun RecentCallRow(call: RecentCall, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(call.person, 50)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                call.person.name,
                color = if (call.missed) HomiraDanger else HomiraText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (call.incoming) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
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
            if (call.video) Icons.Rounded.Videocam else Icons.Rounded.Call,
            contentDescription = null,
            tint = HomiraMuted,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
internal fun ContactsScreen(onSettings: () -> Unit, onStartCall: (PersonCard, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = homiraPeople.filter {
        it.name.contains(query, ignoreCase = true) || it.number.contains(query)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonAvatar(person, 52)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
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
