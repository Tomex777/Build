package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightCapabilityRouteEntity
import com.example.whatsapp.data.night.NightProviderModelEntity
import com.example.whatsapp.data.night.NightProviderProfileEntity

private val RouteBg = Color(0xFF0B0F11)
private val RouteText = Color(0xFFE7EAEC)
private val RouteMuted = Color(0xFF9CA5A9)
private val RouteAccent = Color(0xFF21C063)

private data class CapabilityItem(
    val id: String,
    val title: String,
    val subtitle: String,
)

private val capabilityItems = listOf(
    CapabilityItem("vision", "Vision", "Fallback when the selected chat model cannot inspect images"),
    CapabilityItem("image_generation", "Image generation", "Create images from chat through an image-capable model"),
    CapabilityItem("stt", "Speech to text", "Transcribe voice notes and recordings"),
    CapabilityItem("tts", "Text to speech", "Speak Night's responses"),
    CapabilityItem("translation", "Voice translation", "Translate spoken audio"),
    CapabilityItem("live_voice", "Live voice", "Provider used for realtime voice conversations"),
)

@Composable
fun NightCapabilityRoutesScreen(
    profiles: List<NightProviderProfileEntity>,
    models: List<NightProviderModelEntity>,
    routes: List<NightCapabilityRouteEntity>,
    onBack: () -> Unit,
    onSetRoute: (
        capability: String,
        profile: NightProviderProfileEntity,
        model: NightProviderModelEntity?,
        useSelectedFirst: Boolean,
    ) -> Unit,
    onClearRoute: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<CapabilityItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RouteBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = RouteText)
            }
            Text("Capability routing", color = RouteText, fontSize = 22.sp)
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    "Night keeps the selected chat AI in control. These routes only handle capabilities it cannot perform.",
                    color = RouteMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }

            items(capabilityItems, key = { it.id }) { item ->
                val route = routes.firstOrNull { it.capability == item.id }
                val profile = profiles.firstOrNull { it.id == route?.providerProfileId }
                val model = models.firstOrNull { it.id == route?.modelId }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = item }
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        item.title,
                        color = RouteText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        item.subtitle,
                        color = RouteMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    val modelBasedRoute =
                        item.id == "vision" ||
                            item.id == "image_generation" ||
                            item.id == "live_voice"
                    val hasCapableModel =
                        profile != null &&
                            models.any {
                                it.profileId == profile.id &&
                                    it.isEnabled &&
                                    it.capabilities
                                        .split(",")
                                        .map { capabilityName -> capabilityName.trim() }
                                        .contains(item.id)
                            }
                    val routeUnavailable =
                        profile == null ||
                            !profile.isEnabled ||
                            (route?.modelId != null && (model == null || !model.isEnabled)) ||
                            (
                                route != null &&
                                    route.modelId == null &&
                                    modelBasedRoute &&
                                    !hasCapableModel
                                )
                    Text(
                        when {
                            profile == null -> "Not configured"
                            route?.modelId != null && model == null ->
                                profile.displayName + " • routed model unavailable"
                            model != null ->
                                profile.displayName + " • " + model.displayName +
                                    if (model.isEnabled) "" else " • disabled"
                            modelBasedRoute ->
                                profile.displayName + " • any capable model" +
                                    when {
                                        !profile.isEnabled -> " • disabled"
                                        !hasCapableModel -> " • unavailable"
                                        else -> ""
                                    }
                            else ->
                                profile.displayName +
                                    if (profile.isEnabled) "" else " • disabled"
                        },
                        color = if (routeUnavailable) RouteMuted else RouteAccent,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
    }

    editing?.let { item ->
        RoutePickerDialog(
            capability = item,
            profiles = profiles,
            models = models,
            current = routes.firstOrNull { it.capability == item.id },
            onDismiss = { editing = null },
            onClear = {
                onClearRoute(item.id)
                editing = null
            },
            onSelect = { profile, model ->
                onSetRoute(
                    item.id,
                    profile,
                    model,
                    item.id == "vision",
                )
                editing = null
            },
        )
    }
}

@Composable
private fun RoutePickerDialog(
    capability: CapabilityItem,
    profiles: List<NightProviderProfileEntity>,
    models: List<NightProviderModelEntity>,
    current: NightCapabilityRouteEntity?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSelect: (NightProviderProfileEntity, NightProviderModelEntity?) -> Unit,
) {
    val candidates: List<Pair<NightProviderProfileEntity, NightProviderModelEntity?>> =
        when (capability.id) {
            "vision", "image_generation" -> profiles
                .filter {
                    it.serviceKind == "chat" &&
                        it.isEnabled &&
                        (capability.id != "image_generation" || it.providerType == "azure")
                }
                .flatMap { profile ->
                    val capableModels = models.filter {
                        it.profileId == profile.id &&
                            it.isEnabled &&
                            it.capabilities
                                .split(",")
                                .map { capabilityName -> capabilityName.trim() }
                                .contains(capability.id)
                    }
                    buildList {
                        if (capableModels.isNotEmpty()) {
                            add(profile to null)
                            addAll(capableModels.map { model -> profile to model })
                        }
                    }
                }

            "live_voice" -> profiles
                .filter {
                    it.providerType == "azure" &&
                        it.isEnabled &&
                        (it.serviceKind == "chat" || it.serviceKind == "live_voice")
                }
                .flatMap { profile ->
                    val capableModels = models.filter {
                        it.profileId == profile.id &&
                            it.isEnabled &&
                            it.capabilities
                                .split(",")
                                .map { capabilityName -> capabilityName.trim() }
                                .contains("live_voice")
                    }
                    buildList {
                        if (capableModels.isNotEmpty()) {
                            add(profile to null)
                            addAll(capableModels.map { model -> profile to model })
                        }
                    }
                }

            else -> profiles
                .filter { it.serviceKind == "speech" && it.isEnabled }
                .map { it to null }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = { Text(capability.title, color = RouteText) },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    when (capability.id) {
                        "vision" -> "Add a vision-capable chat model first."
                        "image_generation" -> "Add an image-generation-capable Azure chat model first."
                        "live_voice" -> "Add an enabled Azure model with Live Voice capability first."
                        else -> "Add an Azure Speech profile first."
                    },
                    color = RouteMuted,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    candidates.forEach { (profile, model) ->
                        val selected =
                            current?.providerProfileId == profile.id &&
                                current.modelId == model?.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(profile, model) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    profile.displayName,
                                    color = if (selected) RouteAccent else RouteText,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    model?.displayName
                                        ?: when (capability.id) {
                                            "vision", "image_generation", "live_voice" -> "Any enabled capable model"
                                            else -> profile.providerType.replaceFirstChar { it.uppercase() }
                                        },
                                    color = RouteMuted,
                                    fontSize = 11.sp,
                                )
                            }
                            if (selected) {
                                Icon(Icons.Default.Check, "Selected", tint = RouteAccent)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) {
                TextButton(onClick = onClear) {
                    Text("Clear route", color = Color(0xFFFF6B78))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = RouteMuted)
            }
        },
    )
}
