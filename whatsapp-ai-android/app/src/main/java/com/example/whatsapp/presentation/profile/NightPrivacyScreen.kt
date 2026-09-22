package com.example.whatsapp.presentation.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrivacyBg = Color(0xFF0B0F11)
private val PrivacyText = Color(0xFFE7EAEC)
private val PrivacyMuted = Color(0xFF9CA5A9)

@Composable
fun NightPrivacyScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrivacyBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = PrivacyText)
            }
            Text(
                text = "Privacy",
                color = PrivacyText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        PrivacyInfoRow(
            icon = Icons.Default.Key,
            title = "Provider credentials",
            subtitle = "API keys stay in Night's encrypted on-device credential store.",
        )
        PrivacyInfoRow(
            icon = Icons.Default.Folder,
            title = "Chats & Library",
            subtitle = "Night stores conversation and Library state locally on this device.",
        )
        PrivacyInfoRow(
            icon = Icons.Default.Settings,
            title = "Extensions",
            subtitle = "Installed extensions use Night's extension contract instead of unrestricted Android file access.",
        )
        PrivacyInfoRow(
            icon = Icons.Default.Security,
            title = "Android permissions",
            subtitle = "Review camera, microphone, notification and file permissions in Android settings.",
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.packageName),
                    )
                )
            },
        )
    }
}

@Composable
private fun PrivacyInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrivacyMuted,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PrivacyText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = PrivacyMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = PrivacyMuted,
            )
        }
    }
}
