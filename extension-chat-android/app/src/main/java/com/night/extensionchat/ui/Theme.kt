package com.night.extensionchat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ChatBackground = Color(0xFF0B141A)
val ChatTopBar = Color(0xFF202C33)
val ChatIncoming = Color(0xFF202C33)
val ChatOutgoing = Color(0xFF005C4B)
val ChatComposerSurface = Color(0xFF202C33)
val ChatCard = Color(0xFF172229)
val ChatCardRaised = Color(0xFF1F2C34)
val ChatStroke = Color(0xFF314149)
val ChatText = Color(0xFFE9EDEF)
val ChatMuted = Color(0xFF8696A0)
val ChatAccent = Color(0xFF00A884)
val ChatAccentStrong = Color(0xFF00C29A)
val ChatLink = Color(0xFF53BDEB)

private val ChatColors = darkColorScheme(
    primary = ChatAccent,
    onPrimary = Color.Black,
    primaryContainer = ChatOutgoing,
    onPrimaryContainer = ChatText,
    background = ChatBackground,
    onBackground = ChatText,
    surface = ChatTopBar,
    onSurface = ChatText,
    surfaceVariant = ChatIncoming,
    onSurfaceVariant = ChatMuted,
    outline = ChatStroke,
)

@Composable
fun ExtensionChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChatColors,
        content = content,
    )
}
