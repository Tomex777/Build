package com.night.homira.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MeScreen(
    name: String,
    username: String,
    about: String,
    email: String,
    avatarUri: String?,
    callCardUri: String?,
    onEdit: () -> Unit,
    onSettings: () -> Unit
) {
    val avatarBitmap = rememberProfileImage(avatarUri)
    val callCardBitmap = rememberProfileImage(callCardUri)

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = "Profile photo",
                                modifier = Modifier.size(96.dp).clip(CircleShape),
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
internal fun EditProfileScreen(
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

    val avatarBitmap = rememberProfileImage(editedAvatar)
    val callCardBitmap = rememberProfileImage(editedCallCard)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HomiraBackground).safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText)
                }
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
                            Image(
                                bitmap = callCardBitmap,
                                contentDescription = "Call card",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = HomiraMuted)
                                Spacer(Modifier.height(6.dp))
                                Text("Choose call card image", color = HomiraMuted, fontSize = 13.sp)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.clickable { avatarPicker.launch("image/*") },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.size(88.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(modifier = Modifier.size(88.dp), shape = CircleShape, color = HomiraGreen.copy(alpha = .13f)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = HomiraGreen)
                                    }
                                }
                            }
                            Surface(modifier = Modifier.size(28.dp), shape = CircleShape, color = HomiraGreen) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
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
internal fun SettingsScreen(onBack: () -> Unit) {
    var lowData by rememberSaveable { mutableStateOf(false) }
    var protectIp by rememberSaveable { mutableStateOf(false) }
    var callNotifications by rememberSaveable { mutableStateOf(true) }
    var darkAppearance by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HomiraBackground).safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText)
                }
                Text("Settings", color = HomiraText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionTitle("Calls")
            SettingsToggle(
                icon = Icons.Rounded.DataSaverOn,
                title = "Use less data for calls",
                subtitle = "Reduce call bitrate on mobile data",
                checked = lowData,
                onChecked = { lowData = it }
            )
            SettingsRow(Icons.Rounded.PhoneInTalk, "Call quality", "Automatic")
            SettingsRow(Icons.Rounded.Notifications, "Ringtone", "Default")
        }

        item {
            SectionTitle("Privacy")
            SettingsToggle(
                icon = Icons.Rounded.Lock,
                title = "Protect IP in calls",
                subtitle = "Route calls through TURN when enabled",
                checked = protectIp,
                onChecked = { protectIp = it }
            )
            SettingsRow(Icons.Rounded.Block, "Blocked people", "Manage")
            SettingsRow(Icons.Rounded.Security, "Account security", "PIN and recovery")
        }

        item {
            SectionTitle("Notifications")
            SettingsToggle(
                icon = Icons.Rounded.Notifications,
                title = "Call notifications",
                subtitle = "Incoming and missed calls",
                checked = callNotifications,
                onChecked = { callNotifications = it }
            )
        }

        item {
            SectionTitle("Appearance")
            SettingsToggle(
                icon = Icons.Rounded.Palette,
                title = "Dark appearance",
                subtitle = "Use Homira's dark theme",
                checked = darkAppearance,
                onChecked = { darkAppearance = it }
            )
        }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    InfoRow(icon, title, subtitle, onClick = {})
}

@Composable
private fun SettingsToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HomiraText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = HomiraMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
    HorizontalDivider(color = HomiraLine.copy(alpha = .6f))
}
