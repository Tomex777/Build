package com.example.whatsapp.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.whatsapp.R
import com.example.whatsapp.presentation.navigation.Routes
import com.example.whatsapp.presentation.viewmodels.ThemeViewModel

@Composable
fun SettingsScreen(
    navHostController: NavHostController,
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    val isDarkMode by themeViewModel.isDarkMode.collectAsState()
    var selectedTheme by remember { mutableStateOf(if (isDarkMode) "Dark" else "Light") }
    
    // Update selected theme when dark mode changes
    androidx.compose.runtime.LaunchedEffect(isDarkMode) {
        selectedTheme = if (isDarkMode) "Dark" else "Light"
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = 30.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navHostController.popBackStack() }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_arrow_back_24),
                        contentDescription = "Back",
                        tint = Color.Black
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(Color.White)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Account Section - Only show heading, navigate to sub-screen
            SettingsSection(
                title = "Account",
                items = listOf(
                    SettingsItem(
                        icon = R.drawable.outline_phone_24,
                        title = "Account",
                        onClick = { navHostController.navigate(Routes.AccountSettingsScreen.route) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Privacy Section - Only show heading
            SettingsSection(
                title = "Privacy",
                items = listOf(
                    SettingsItem(
                        icon = R.drawable.outline_visibility_24,
                        title = "Privacy",
                        onClick = { navHostController.navigate(Routes.PrivacySettingsScreen.route) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chats Section - Only show heading
            SettingsSection(
                title = "Chats",
                items = listOf(
                    SettingsItem(
                        icon = R.drawable.outline_light_mode_24,
                        title = "Chats",
                        onClick = { navHostController.navigate(Routes.ChatsSettingsScreen.route) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Notifications Section - Only show heading
            SettingsSection(
                title = "Notifications",
                items = listOf(
                    SettingsItem(
                        icon = R.drawable.baseline_notifications_none_24,
                        title = "Notifications",
                        onClick = { navHostController.navigate(Routes.NotificationsSettingsScreen.route) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Storage and Data Section - Only show heading
            SettingsSection(
                title = "Storage and Data",
                items = listOf(
                    SettingsItem(
                        icon = R.drawable.outline_storage_24,
                        title = "Storage and Data",
                        onClick = { navHostController.navigate(Routes.StorageSettingsScreen.route) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Help Section - Only show heading
            SettingsSection(
                title = "Help",
                items = listOf(
                    SettingsItem(
                        icon = R.drawable.baseline_help_outline_24,
                        title = "Help",
                        onClick = { navHostController.navigate(Routes.HelpSettingsScreen.route) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Invite a Friend Section - Only show heading
            SettingsSection(
                title = "Invite a Friend",
                items = listOf(
                    SettingsItem(
                        icon = R.drawable.outline_share_24,
                        title = "Invite a Friend",
                        onClick = { navHostController.navigate(Routes.InviteSettingsScreen.route) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Account Center Section - Only show heading
            SettingsSection(
                title = "Account Center",
                items = listOf(
                    SettingsItem(
                        icon = R.drawable.outline_account_circle_24,
                        title = "Account Center",
                        onClick = { navHostController.navigate(Routes.AccountCenterSettingsScreen.route) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTheme = "Light"
                                themeViewModel.setDarkMode(false)
                                showThemeDialog = false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Light Mode", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        RadioButton(
                            selected = selectedTheme == "Light",
                            onClick = {
                                selectedTheme = "Light"
                                themeViewModel.setDarkMode(false)
                                showThemeDialog = false
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorResource(R.color.light_green)
                            )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTheme = "Dark"
                                themeViewModel.setDarkMode(true)
                                showThemeDialog = false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        RadioButton(
                            selected = selectedTheme == "Dark",
                            onClick = {
                                selectedTheme = "Dark"
                                themeViewModel.setDarkMode(true)
                                showThemeDialog = false
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorResource(R.color.light_green)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    items: List<SettingsItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            items.forEachIndexed { index, item ->
                SettingsOptionItem(item = item)
                if (index < items.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsOptionItem(item: SettingsItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = if (item.isDestructive) Color.Red else colorResource(R.color.light_green),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.title,
            fontSize = 16.sp,
            color = if (item.isDestructive) Color.Red else Color.Black
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = ">",
            color = Color.Gray,
            fontSize = 18.sp
        )
    }
}

data class SettingsItem(
    @androidx.annotation.DrawableRes val icon: Int,
    val title: String,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false
)

