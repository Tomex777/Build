package com.night.homira.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MainHeader(title: String, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = HomiraText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = HomiraText)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Blocked people") },
                    leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                    onClick = { expanded = false }
                )
            }
        }
    }
}

@Composable
internal fun PersonAvatar(person: PersonCard, size: Int) {
    Surface(modifier = Modifier.size(size.dp), shape = CircleShape, color = person.accent.copy(alpha = .13f)) {
        Box(contentAlignment = Alignment.Center) {
            Text(person.marker, color = person.accent, fontSize = (size * .38f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun MiniCallButton(icon: ImageVector, accent: Color, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = accent.copy(alpha = .13f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
internal fun QuickCallChip(person: PersonCard, onVoice: () -> Unit, onVideo: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = HomiraSurface) {
        Column(modifier = Modifier.width(154.dp).padding(14.dp)) {
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
internal fun HomiraBottomBar(selected: HomiraTab, onSelect: (HomiraTab) -> Unit) {
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
internal fun SectionTitle(title: String) {
    Text(
        title,
        color = HomiraMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
internal fun InfoRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HomiraText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, color = HomiraMuted, fontSize = 13.sp)
        }
    }
    HorizontalDivider(color = HomiraLine.copy(alpha = .6f))
}

@Composable
internal fun rememberProfileImage(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(uriString, context) {
        if (uriString.isNullOrBlank()) return@remember null
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

@Composable
internal fun ProfileImage(bitmap: ImageBitmap?, fallbackLetter: String, size: Int) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Profile photo",
            modifier = Modifier.size(size.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(modifier = Modifier.size(size.dp), shape = CircleShape, color = HomiraGreen.copy(alpha = .14f)) {
            Box(contentAlignment = Alignment.Center) {
                Text(fallbackLetter, color = HomiraGreen, fontSize = (size * .38f).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
