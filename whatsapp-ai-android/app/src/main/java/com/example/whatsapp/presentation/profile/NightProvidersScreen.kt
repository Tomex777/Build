package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightProviderModelEntity
import com.example.whatsapp.data.night.NightProviderProfileEntity

private val ProviderBg = Color(0xFF0B0F11)
private val ProviderText = Color(0xFFE7EAEC)
private val ProviderMuted = Color(0xFF9CA5A9)
private val ProviderAccent = Color(0xFF21C063)

@Composable
fun NightProvidersScreen(
    profiles: List<NightProviderProfileEntity>,
    models: List<NightProviderModelEntity>,
    onBack: () -> Unit,
    onCapabilityRoutingClick: () -> Unit,
    onAddProfile: (
        providerType: String,
        serviceKind: String,
        displayName: String,
        apiKey: String,
        endpoint: String?,
        region: String?,
        language: String,
        voiceName: String?,
        makeDefault: Boolean,
    ) -> Unit,
    onAddModel: (
        profile: NightProviderProfileEntity,
        modelId: String,
        displayName: String,
        deploymentName: String?,
        capabilities: Set<String>,
        makeDefault: Boolean,
    ) -> Unit,
    onDeleteProfile: (NightProviderProfileEntity) -> Unit,
    onDeleteModel: (NightProviderModelEntity) -> Unit,
    onTestModel: (NightProviderProfileEntity, NightProviderModelEntity) -> Unit,
) {
    var showAddProfile by remember { mutableStateOf(false) }
    var addModelFor by remember { mutableStateOf<NightProviderProfileEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProviderBg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ProviderText)
            }
            Text(
                "AI & providers",
                color = ProviderText,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCapabilityRoutingClick) {
                Text("Routing", color = ProviderAccent)
            }
            IconButton(onClick = { showAddProfile = true }) {
                Icon(Icons.Default.Add, "Add provider", tint = ProviderAccent)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    "Keys stay encrypted on this device. Night automatically fails over across enabled chat profiles/models; multiple Groq profiles act as key rotation when a key is rate-limited or unavailable.",
                    color = ProviderMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            listOf("deepseek", "groq", "azure").forEach { provider ->
                item(key = "header_" + provider) {
                    Text(
                        provider.replaceFirstChar { it.uppercase() },
                        color = ProviderText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                val providerProfiles = profiles.filter { it.providerType == provider }
                if (providerProfiles.isEmpty()) {
                    item(key = "empty_" + provider) {
                        Text(
                            "No " + provider.replaceFirstChar { it.uppercase() } + " profiles yet.",
                            color = ProviderMuted,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    items(providerProfiles, key = { it.id }) { profile ->
                        ProviderProfileRow(
                            profile = profile,
                            models = models.filter { it.profileId == profile.id },
                            onAddModel = { addModelFor = profile },
                            onDeleteProfile = { onDeleteProfile(profile) },
                            onDeleteModel = onDeleteModel,
                            onTestModel = { model -> onTestModel(profile, model) },
                        )
                    }
                }
            }
        }
    }

    if (showAddProfile) {
        AddProviderDialog(
            onDismiss = { showAddProfile = false },
            onAdd = { provider, service, name, key, endpoint, region, language, voiceName, makeDefault ->
                onAddProfile(provider, service, name, key, endpoint, region, language, voiceName, makeDefault)
                showAddProfile = false
            },
        )
    }

    addModelFor?.let { profile ->
        AddModelDialog(
            profile = profile,
            onDismiss = { addModelFor = null },
            onAdd = { modelId, name, deployment, caps, makeDefault ->
                onAddModel(profile, modelId, name, deployment, caps, makeDefault)
                addModelFor = null
            },
        )
    }
}

@Composable
private fun ProviderProfileRow(
    profile: NightProviderProfileEntity,
    models: List<NightProviderModelEntity>,
    onAddModel: () -> Unit,
    onDeleteProfile: () -> Unit,
    onDeleteModel: (NightProviderModelEntity) -> Unit,
    onTestModel: (NightProviderModelEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.displayName,
                    color = ProviderText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    buildString {
                        append(profile.serviceKind.replace("_", " "))
                        if (profile.isDefault) append(" • default")
                        profile.endpoint?.let {
                            append(" • ")
                            append(it.removePrefix("https://").take(34))
                        }
                    },
                    color = ProviderMuted,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = onAddModel) {
                Text("Add model", color = ProviderAccent)
            }
            IconButton(onClick = onDeleteProfile) {
                Icon(Icons.Default.Delete, "Delete profile", tint = Color(0xFFFF6B78))
            }
        }

        models.forEach { model ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.displayName,
                        color = ProviderText,
                        fontSize = 13.sp,
                    )
                    Text(
                        buildString {
                            append(model.deploymentName ?: model.modelId)
                            if (model.isDefault) append(" • default")
                            if (model.capabilities.isNotBlank()) {
                                append(" • ")
                                append(model.capabilities)
                            }
                        },
                        color = ProviderMuted,
                        fontSize = 10.sp,
                    )
                }
                TextButton(onClick = { onTestModel(model) }) {
                    Text("Test", color = ProviderAccent, fontSize = 11.sp)
                }
                IconButton(onClick = { onDeleteModel(model) }) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete model",
                        tint = ProviderMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String?, String?, String, String?, Boolean) -> Unit,
) {
    var provider by remember { mutableStateOf("deepseek") }
    var service by remember { mutableStateOf("chat") }
    var name by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en-US") }
    var voiceName by remember { mutableStateOf("") }
    var makeDefault by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = { Text("Add provider", color = ProviderText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("deepseek", "groq", "azure").forEach { item ->
                        FilterChip(
                            selected = provider == item,
                            onClick = {
                                provider = item
                                if (item != "azure") service = "chat"
                            },
                            label = { Text(item.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }

                if (provider == "azure") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("chat", "speech", "live_voice").forEach { item ->
                            FilterChip(
                                selected = service == item,
                                onClick = { service = item },
                                label = { Text(item.replace("_", " ")) },
                            )
                        }
                    }
                }

                ProviderField(name, { name = it }, "Profile name")
                ProviderField(
                    key,
                    { key = it },
                    "API key",
                    secret = true,
                )

                if (provider == "azure") {
                    ProviderField(
                        endpoint,
                        { endpoint = it },
                        if (service == "speech") "Speech resource endpoint (optional)" else "Azure endpoint",
                    )
                    ProviderField(
                        region,
                        { region = it },
                        if (service == "speech") "Speech region" else "Region (optional)",
                    )

                    if (service == "speech") {
                        ProviderField(language, { language = it }, "Speech language")
                        ProviderField(voiceName, { voiceName = it }, "TTS voice name (optional)")
                    } else if (service == "live_voice") {
                        ProviderField(voiceName, { voiceName = it }, "Realtime voice name (optional)")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = makeDefault,
                        onCheckedChange = { makeDefault = it },
                    )
                    Text("Use as default for this service", color = ProviderText, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        provider,
                        service,
                        name,
                        key,
                        endpoint.ifBlank { null },
                        region.ifBlank { null },
                        language,
                        voiceName.ifBlank { null },
                        makeDefault,
                    )
                },
                enabled = key.isNotBlank() && (
                    provider != "azure" ||
                        (service == "speech" && (endpoint.isNotBlank() || region.isNotBlank())) ||
                        (service != "speech" && endpoint.isNotBlank())
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProviderAccent,
                    contentColor = Color(0xFF07110B),
                ),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ProviderMuted)
            }
        },
    )
}

@Composable
private fun AddModelDialog(
    profile: NightProviderProfileEntity,
    onDismiss: () -> Unit,
    onAdd: (String, String, String?, Set<String>, Boolean) -> Unit,
) {
    var modelId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var deployment by remember { mutableStateOf("") }
    var vision by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var imageGeneration by remember { mutableStateOf(false) }
    var makeDefault by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = {
            Text(
                "Add model to " + profile.displayName,
                color = ProviderText,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProviderField(modelId, { modelId = it }, "Model ID")
                ProviderField(name, { name = it }, "Display name (optional)")

                if (profile.providerType == "azure" && profile.serviceKind == "chat") {
                    ProviderField(deployment, { deployment = it }, "Deployment name (optional)")
                }

                if (profile.serviceKind == "chat") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vision, onCheckedChange = { vision = it })
                        Text("Vision", color = ProviderText)
                        Spacer(Modifier.width(10.dp))
                        Checkbox(checked = tools, onCheckedChange = { tools = it })
                        Text("Agent tools", color = ProviderText)
                    }
                    if (profile.providerType == "azure") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = imageGeneration,
                                onCheckedChange = { imageGeneration = it },
                            )
                            Text("Image generation", color = ProviderText)
                        }
                    }
                    Text(
                        "Agent tools lets Night search the web, read files, schedule actions, change appearance, create options, and call extensions.",
                        color = ProviderMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                    Text("Default model for this profile", color = ProviderText, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val caps = buildSet {
                        if (vision) add("vision")
                        if (tools) add("tools")
                        if (imageGeneration) add("image_generation")
                    }
                    onAdd(
                        modelId,
                        name,
                        deployment.ifBlank { null },
                        caps,
                        makeDefault,
                    )
                },
                enabled = modelId.isNotBlank() || deployment.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProviderAccent,
                    contentColor = Color(0xFF07110B),
                ),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ProviderMuted)
            }
        },
    )
}

@Composable
private fun ProviderField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = ProviderText,
            unfocusedTextColor = ProviderText,
            cursorColor = ProviderAccent,
        ),
    )
}
