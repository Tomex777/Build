package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
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
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
)

@Composable
fun NightYouTab(
    onTabSelected: (MainTab) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val rows = listOf(
        YouRow(Icons.Default.AutoAwesome, "AI models", "Choose the model used in new chats"),
        YouRow(Icons.Default.Memory, "Memory", "Chat summaries and cross-chat references"),
        YouRow(Icons.Default.Storage, "Library & storage", "Manage Night-owned files"),
        YouRow(Icons.Default.Palette, "Appearance", "Theme, wallpaper and chat style"),
        YouRow(Icons.Default.Security, "Privacy", "Local data, permissions and retention"),
        YouRow(Icons.Default.Settings, "Settings", "General Night settings"),
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
                        text = "Dawson",
                        color = YouText,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 13.dp),
                    )
                    Text(
                        text = "Your Night profile",
                        color = YouMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )

                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InfoPill("3 chats")
                        InfoPill("Library ready")
                        InfoPill("Memory on")
                    }
                }
            }

            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (row.title == "Settings") onSettingsClick()
                        }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = YouSurface,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = row.icon,
                                contentDescription = null,
                                tint = YouMuted,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(13.dp))

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

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = YouMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        color = YouSurface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = text,
            color = YouMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}
