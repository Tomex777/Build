package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightProviderModelEntity
import com.example.whatsapp.data.night.NightProviderProfileEntity

@Composable
fun NightAiSelectorScreen(
    profiles: List<NightProviderProfileEntity>,
    models: List<NightProviderModelEntity>,
    selectedProfileId: String?,
    selectedModelId: String?,
    onBack: () -> Unit,
    onSelect: (NightProviderProfileEntity, NightProviderModelEntity) -> Unit,
) {
    val chatProfiles = profiles.filter { it.serviceKind == "chat" && it.isEnabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F11))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFE7EAEC))
            }
            Text("Choose AI", color = Color(0xFFE7EAEC), fontSize = 22.sp)
        }

        if (chatProfiles.isEmpty()) {
            Text(
                "Add a DeepSeek, Groq, or Azure chat profile in You → AI & providers first.",
                color = Color(0xFF9CA5A9),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(22.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            ) {
                chatProfiles.forEach { profile ->
                    item(key = "p_" + profile.id) {
                        Text(
                            profile.displayName,
                            color = Color(0xFFE7EAEC),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                        Text(
                            profile.providerType.replaceFirstChar { it.uppercase() },
                            color = Color(0xFF9CA5A9),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    val profileModels = models.filter { it.profileId == profile.id && it.isEnabled }
                    if (profileModels.isEmpty()) {
                        item(key = "empty_" + profile.id) {
                            Text(
                                "No models configured for this profile.",
                                color = Color(0xFF707A7F),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(profileModels, key = { it.id }) { model ->
                            val selected =
                                selectedProfileId == profile.id && selectedModelId == model.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(profile, model) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        model.displayName,
                                        color = if (selected) Color(0xFF21C063) else Color(0xFFE7EAEC),
                                        fontSize = 15.sp,
                                    )
                                    Text(
                                        model.deploymentName ?: model.modelId,
                                        color = Color(0xFF9CA5A9),
                                        fontSize = 11.sp,
                                    )
                                }

                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        "Selected",
                                        tint = Color(0xFF21C063),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
