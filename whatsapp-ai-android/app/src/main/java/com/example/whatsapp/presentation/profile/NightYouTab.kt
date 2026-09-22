package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernAppScaffold

private val YouBg = Color(0xFF0B0F11)
private val YouSurface = Color(0xFF171C1F)
private val YouText = Color(0xFFE7EAEC)
private val YouMuted = Color(0xFF9CA5A9)
private val YouAccent = Color(0xFF21C063)

private data class YouRow(
    val id: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
)

@Composable
fun NightYouTab(
    displayName: String,
    onTabSelected: (MainTab) -> Unit,
    onProfileClick: () -> Unit,
    onProvidersClick: () -> Unit,
    onMemoryClick: () -> Unit,
    onSchedulesClick: () -> Unit,
    onLibraryStorageClick: () -> Unit,
    onBrowserClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val rows = listOf(
        YouRow("providers", Icons.Default.AutoAwesome, "AI & providers", "DeepSeek, Groq, Azure, models and keys"),
        YouRow("memory", Icons.Default.Memory, "Memory", "Chat summaries and cross-chat references"),
        YouRow("scheduled", Icons.Default.Schedule, "Scheduled", "Tasks Night will run later"),
        YouRow("storage", Icons.Default.Storage, "Library & storage", "Manage Night-owned files"),
        YouRow("browser", Icons.Default.Language, "Browser", "Open Night's full browser"),
        YouRow("appearance", Icons.Default.Palette, "Appearance", "Bubbles, wallpaper, font and text size"),
        YouRow("privacy", Icons.Default.Security, "Privacy", "Local data, permissions and retention"),
        YouRow("settings", Icons.Default.Settings, "Settings", "General Night settings"),
    )

    ModernAppScaffold(
        selectedTab = MainTab.You,
        onTabSelected = onTabSelected,
        title = "You",
        onSettingsClick = onSettingsClick,
        showCamera = false,
        showSearch = false,
        showMenu = false,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(YouBg),
            contentPadding = PaddingValues(bottom = 92.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onProfileClick)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        color = YouSurface,
                        shape = CircleShape,
                        modifier = Modifier.size(92.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = YouAccent,
                                modifier = Modifier.size(46.dp),
                            )
                        }
                    }

                    Text(
                        text = displayName,
                        color = YouText,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 13.dp),
                    )
                    Text(
                        text = "Your Night profile • tap to edit",
                        color = YouMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            items(rows, key = { it.id }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (row.id) {
                                "providers" -> onProvidersClick()
                                "memory" -> onMemoryClick()
                                "scheduled" -> onSchedulesClick()
                                "storage" -> onLibraryStorageClick()
                                "browser" -> onBrowserClick()
                                "appearance" -> onAppearanceClick()
                                "privacy" -> onPrivacyClick()
                                else -> onSettingsClick()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = YouMuted,
                        modifier = Modifier.size(24.dp),
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.title,
                            color = YouText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = row.subtitle,
                            color = YouMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
