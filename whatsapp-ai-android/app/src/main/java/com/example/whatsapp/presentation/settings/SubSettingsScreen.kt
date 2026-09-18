package com.example.whatsapp.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.whatsapp.R

@Composable
fun SubSettingsScreen(
    title: String,
    items: List<SettingsItem>,
    navHostController: NavHostController
) {
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
                    text = title,
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
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
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
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AccountSettingsScreen(navHostController: NavHostController) {
    SubSettingsScreen(
        title = "Account",
        items = listOf(
            SettingsItem(
                icon = R.drawable.outline_phone_24,
                title = "Phone number",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_security_24,
                title = "Security",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_key_24,
                title = "Two-step verification",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_phone_24,
                title = "Change number",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_file_download_24,
                title = "Request account info",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_stop_24,
                title = "Delete my account",
                onClick = { /* TODO */ },
                isDestructive = true
            )
        ),
        navHostController = navHostController
    )
}

@Composable
fun PrivacySettingsScreen(navHostController: NavHostController) {
    SubSettingsScreen(
        title = "Privacy",
        items = listOf(
            SettingsItem(
                icon = R.drawable.outline_visibility_24,
                title = "Last seen & online",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_image_24,
                title = "Profile photo",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_account_circle_24,
                title = "About",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_verified_user_24,
                title = "Status",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_verified_user_24,
                title = "Read receipts",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_groups_24,
                title = "Groups",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_network_cell_24,
                title = "Live location",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_phone_24,
                title = "Calls",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_lock_24,
                title = "Blocked contacts",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_security_24,
                title = "Fingerprint lock",
                onClick = { /* TODO */ }
            )
        ),
        navHostController = navHostController
    )
}

@Composable
fun ChatsSettingsScreen(navHostController: NavHostController) {
    var showThemeDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val themeViewModel: com.example.whatsapp.presentation.viewmodels.ThemeViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val isDarkMode by themeViewModel.isDarkMode.collectAsState()
    var selectedTheme by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(if (isDarkMode) "Dark" else "Light") }
    
    androidx.compose.runtime.LaunchedEffect(isDarkMode) {
        selectedTheme = if (isDarkMode) "Dark" else "Light"
    }
    
    SubSettingsScreen(
        title = "Chats",
        items = listOf(
            SettingsItem(
                icon = R.drawable.outline_light_mode_24,
                title = "Theme",
                onClick = { showThemeDialog = true }
            ),
            SettingsItem(
                icon = R.drawable.outline_image_24,
                title = "Wallpaper",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_history_24,
                title = "Chat history",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_backup_24,
                title = "Chat backup",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_file_download_24,
                title = "Chat transfer",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_tune_24,
                title = "Font size",
                onClick = { /* TODO */ }
            )
        ),
        navHostController = navHostController
    )
    
    // Theme Dialog
    if (showThemeDialog) {
        androidx.compose.material3.AlertDialog(
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
                        androidx.compose.material3.RadioButton(
                            selected = selectedTheme == "Light",
                            onClick = {
                                selectedTheme = "Light"
                                themeViewModel.setDarkMode(false)
                                showThemeDialog = false
                            },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
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
                        androidx.compose.material3.RadioButton(
                            selected = selectedTheme == "Dark",
                            onClick = {
                                selectedTheme = "Dark"
                                themeViewModel.setDarkMode(true)
                                showThemeDialog = false
                            },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
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
fun NotificationsSettingsScreen(navHostController: NavHostController) {
    SubSettingsScreen(
        title = "Notifications",
        items = listOf(
            SettingsItem(
                icon = R.drawable.baseline_notifications_none_24,
                title = "Message notifications",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_notifications_none_24,
                title = "Group notifications",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_phone_24,
                title = "Call notifications",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_volume_up_24,
                title = "Notification tone",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_vibration_24,
                title = "Vibration",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_notifications_none_24,
                title = "Popup notification",
                onClick = { /* TODO */ }
            )
        ),
        navHostController = navHostController
    )
}

@Composable
fun StorageSettingsScreen(navHostController: NavHostController) {
    SubSettingsScreen(
        title = "Storage and Data",
        items = listOf(
            SettingsItem(
                icon = R.drawable.outline_storage_24,
                title = "Storage usage",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_storage_24,
                title = "Manage storage",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_data_usage_24,
                title = "Network usage",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_file_download_24,
                title = "Media auto-download",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_image_24,
                title = "Media upload quality",
                onClick = { /* TODO */ }
            )
        ),
        navHostController = navHostController
    )
}

@Composable
fun HelpSettingsScreen(navHostController: NavHostController) {
    SubSettingsScreen(
        title = "Help",
        items = listOf(
            SettingsItem(
                icon = R.drawable.baseline_help_outline_24,
                title = "Help center",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_account_circle_24,
                title = "Contact us",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_verified_user_24,
                title = "Terms and Privacy Policy",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_info_outline_24,
                title = "App info",
                onClick = { /* TODO */ }
            )
        ),
        navHostController = navHostController
    )
}

@Composable
fun InviteSettingsScreen(navHostController: NavHostController) {
    SubSettingsScreen(
        title = "Invite a Friend",
        items = listOf(
            SettingsItem(
                icon = R.drawable.outline_share_24,
                title = "Share WhatsApp link",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_phone_iphone_24,
                title = "Invite via SMS",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_account_circle_24,
                title = "Invite via Email",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_share_24,
                title = "Invite via Other Apps",
                onClick = { /* TODO */ }
            )
        ),
        navHostController = navHostController
    )
}

@Composable
fun AccountCenterSettingsScreen(navHostController: NavHostController) {
    SubSettingsScreen(
        title = "Account Center",
        items = listOf(
            SettingsItem(
                icon = R.drawable.outline_account_circle_24,
                title = "Connected accounts",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_account_circle_24,
                title = "Profile information",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.outline_key_24,
                title = "Password & security",
                onClick = { /* TODO */ }
            ),
            SettingsItem(
                icon = R.drawable.baseline_tune_24,
                title = "Ad preferences",
                onClick = { /* TODO */ }
            )
        ),
        navHostController = navHostController
    )
}

