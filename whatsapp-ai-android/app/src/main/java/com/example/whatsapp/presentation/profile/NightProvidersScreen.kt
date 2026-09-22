package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.whatsapp.data.night.NightProviderKeySummary
import com.example.whatsapp.data.night.NightProviderModelEntity
import com.example.whatsapp.data.night.NightProviderProfileEntity

private val ProviderBg = Color(0xFF0B0F11)
private val ProviderText = Color(0xFFE7EAEC)
private val ProviderMuted = Color(0xFF9CA5A9)

@Composable
fun NightProvidersScreen(
    profiles: List<NightProviderProfileEntity>,
    models: List<NightProviderModelEntity>,
    onBack: () -> Unit,
    onCapabilityRoutingClick: () -> Unit,
    onMcpServersClick: () -> Unit,
    onExtensionsClick: () -> Unit,
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
    onSetProfileEnabled: (NightProviderProfileEntity, Boolean) -> Unit,
    onMakeProfileDefault: (NightProviderProfileEntity) -> Unit,
    onSetModelEnabled: (NightProviderModelEntity, Boolean) -> Unit,
    onMakeModelDefault: (NightProviderModelEntity) -> Unit,
    onEditProfile: (
        NightProviderProfileEntity,
        String,
        String?,
        String?,
        String,
        String?,
        String?,
    ) -> Unit,
    onEditModel: (
        NightProviderModelEntity,
        String,
        String,
        String?,
        Set<String>,
    ) -> Unit,
    onTestModel: (NightProviderProfileEntity, NightProviderModelEntity) -> Unit,
    providerKeys: (NightProviderProfileEntity) -> List<NightProviderKeySummary>,
    onAddProviderKey: (NightProviderProfileEntity, String, String?) -> Unit,
    onDeleteProviderKey: (NightProviderProfileEntity, String) -> Unit,
) {
    var showAddProfile by remember { mutableStateOf(false) }
    var addModelFor by remember { mutableStateOf<NightProviderProfileEntity?>(null) }
    var addKeyFor by remember { mutableStateOf<NightProviderProfileEntity?>(null) }
    var editProfile by remember { mutableStateOf<NightProviderProfileEntity?>(null) }
    var editModelFor by remember {
        mutableStateOf<Pair<NightProviderProfileEntity, NightProviderModelEntity>?>(null)
    }
    var deleteProfileConfirm by remember {
        mutableStateOf<NightProviderProfileEntity?>(null)
    }
    var deleteModelConfirm by remember {
        mutableStateOf<Pair<NightProviderProfileEntity, NightProviderModelEntity>?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProviderBg)
            .statusBarsPadding().navigationBarsPadding(),
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
                Text("Routing", color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showAddProfile = true }) {
                Icon(Icons.Default.Add, "Add provider", tint = MaterialTheme.colorScheme.primary)
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
                    "Keys stay encrypted on this device. Night automatically fails over across enabled chat profiles/models. A Groq chat profile can hold multiple keys and rotates through that key pool when one is rate-limited or unavailable.",
                    color = ProviderMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExtensionsClick)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Integrations",
                            color = ProviderText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Manage Night extensions and MCP connections in one place",
                            color = ProviderMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        "Manage",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                }
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
                            onDeleteProfile = { deleteProfileConfirm = profile },
                            onDeleteModel = { model -> deleteModelConfirm = profile to model },
                            onSetProfileEnabled = { enabled -> onSetProfileEnabled(profile, enabled) },
                            onMakeProfileDefault = { onMakeProfileDefault(profile) },
                            onSetModelEnabled = onSetModelEnabled,
                            onMakeModelDefault = onMakeModelDefault,
                            onEditProfile = { editProfile = profile },
                            onEditModel = { model -> editModelFor = profile to model },
                            onTestModel = { model -> onTestModel(profile, model) },
                            keys = providerKeys(profile),
                            onAddKey = { addKeyFor = profile },
                            onDeleteKey = { keyId -> onDeleteProviderKey(profile, keyId) },
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

    addKeyFor?.let { profile ->
        AddGroqKeyDialog(
            profile = profile,
            onDismiss = { addKeyFor = null },
            onAdd = { key, label ->
                onAddProviderKey(profile, key, label)
                addKeyFor = null
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

    editProfile?.let { profile ->
        EditProviderDialog(
            profile = profile,
            onDismiss = { editProfile = null },
            onSave = { name, endpoint, region, language, voiceName, replacementKey ->
                onEditProfile(
                    profile,
                    name,
                    endpoint,
                    region,
                    language,
                    voiceName,
                    replacementKey,
                )
                editProfile = null
            },
        )
    }

    editModelFor?.let { (profile, model) ->
        EditModelDialog(
            profile = profile,
            model = model,
            onDismiss = { editModelFor = null },
            onSave = { modelId, name, deployment, caps ->
                onEditModel(model, modelId, name, deployment, caps)
                editModelFor = null
            },
        )
    }

    deleteProfileConfirm?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteProfileConfirm = null },
            containerColor = Color(0xFF151B1E),
            title = { Text("Delete provider?", color = ProviderText) },
            text = {
                Text(
                    "Delete " + profile.displayName +
                        "? Night will remove its encrypted key, models, chat selections, and capability routes. Chats can fall back to another enabled provider if one is available.",
                    color = ProviderMuted,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteProfileConfirm = null
                        onDeleteProfile(profile)
                    },
                ) {
                    Text("Delete", color = Color(0xFFFF6B78))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteProfileConfirm = null }) {
                    Text("Cancel", color = ProviderMuted)
                }
            },
        )
    }

    deleteModelConfirm?.let { (profile, model) ->
        AlertDialog(
            onDismissRequest = { deleteModelConfirm = null },
            containerColor = Color(0xFF151B1E),
            title = { Text("Delete model?", color = ProviderText) },
            text = {
                Text(
                    "Delete " + model.displayName + " from " + profile.displayName +
                        "? Chats selecting it will fall back to another enabled model or provider. Capability routes pinned to it will fall back to this provider.",
                    color = ProviderMuted,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteModelConfirm = null
                        onDeleteModel(model)
                    },
                ) {
                    Text("Delete", color = Color(0xFFFF6B78))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteModelConfirm = null }) {
                    Text("Cancel", color = ProviderMuted)
                }
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
    onSetProfileEnabled: (Boolean) -> Unit,
    onMakeProfileDefault: () -> Unit,
    onSetModelEnabled: (NightProviderModelEntity, Boolean) -> Unit,
    onMakeModelDefault: (NightProviderModelEntity) -> Unit,
    onEditProfile: () -> Unit,
    onEditModel: (NightProviderModelEntity) -> Unit,
    onTestModel: (NightProviderModelEntity) -> Unit,
    keys: List<NightProviderKeySummary>,
    onAddKey: () -> Unit,
    onDeleteKey: (String) -> Unit,
) {
    var modelMenuFor by remember { mutableStateOf<String?>(null) }

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
                    color = if (profile.isEnabled) ProviderText else ProviderMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    buildString {
                        append(profile.serviceKind.replace("_", " "))
                        if (profile.isDefault) append(" • default")
                        if (!profile.isEnabled) append(" • disabled")
                        profile.endpoint?.let {
                            append(" • ")
                            append(it.removePrefix("https://").take(34))
                        }
                    },
                    color = ProviderMuted,
                    fontSize = 11.sp,
                )
            }
            IconButton(onClick = onEditProfile) {
                Icon(Icons.Default.Edit, "Edit profile", tint = ProviderMuted)
            }
            IconButton(onClick = onDeleteProfile) {
                Icon(Icons.Default.Delete, "Delete profile", tint = Color(0xFFFF6B78))
            }
        }

        Row(
            modifier = Modifier.padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onSetProfileEnabled(!profile.isEnabled) }) {
                Text(
                    if (profile.isEnabled) "Disable" else "Enable",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                )
            }
            if (!profile.isDefault) {
                TextButton(onClick = onMakeProfileDefault) {
                    Text("Make default", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
            }
            if (profile.serviceKind != "speech") {
                TextButton(onClick = onAddModel) {
                    Text("Add model", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
            }
        }

        if (profile.providerType.equals("groq", ignoreCase = true) && profile.serviceKind == "chat") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = keys.size.toString() +
                        (if (keys.size == 1) " key" else " keys") +
                        " • automatic rotation",
                    color = ProviderMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddKey) {
                    Text("Add key", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
            }

            keys.forEach { key ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = key.label + " ••••" + key.suffix,
                        color = ProviderMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onDeleteKey(key.id) },
                        enabled = keys.size > 1,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            if (keys.size > 1) "Delete Groq key" else "Keep at least one Groq key",
                            tint = if (keys.size > 1) ProviderMuted else ProviderMuted.copy(alpha = 0.35f),
                        )
                    }
                }
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
                        color = if (model.isEnabled) ProviderText else ProviderMuted,
                        fontSize = 13.sp,
                    )
                    Text(
                        buildString {
                            append(model.deploymentName ?: model.modelId)
                            if (model.isDefault) append(" • default")
                            if (!model.isEnabled) append(" • disabled")
                            if (model.capabilities.isNotBlank()) {
                                append(" • ")
                                append(model.capabilities)
                            }
                        },
                        color = ProviderMuted,
                        fontSize = 10.sp,
                    )
                }
                TextButton(
                    onClick = { onTestModel(model) },
                    enabled = model.isEnabled,
                ) {
                    Text(
                        "Test",
                        color = if (model.isEnabled) MaterialTheme.colorScheme.primary else ProviderMuted,
                        fontSize = 10.sp,
                    )
                }
                Box {
                    IconButton(onClick = { modelMenuFor = model.id }) {
                        Icon(
                            Icons.Default.MoreVert,
                            "Model actions",
                            tint = ProviderMuted,
                        )
                    }
                    DropdownMenu(
                        expanded = modelMenuFor == model.id,
                        onDismissRequest = { modelMenuFor = null },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(if (model.isEnabled) "Disable model" else "Enable model")
                            },
                            onClick = {
                                modelMenuFor = null
                                onSetModelEnabled(model, !model.isEnabled)
                            },
                        )
                        if (!model.isDefault) {
                            DropdownMenuItem(
                                text = { Text("Make default") },
                                onClick = {
                                    modelMenuFor = null
                                    onMakeModelDefault(model)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit model") },
                            onClick = {
                                modelMenuFor = null
                                onEditModel(model)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete model", color = Color(0xFFFF6B78)) },
                            onClick = {
                                modelMenuFor = null
                                onDeleteModel(model)
                            },
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun EditProviderDialog(
    profile: NightProviderProfileEntity,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String, String?, String?) -> Unit,
) {
    var name by remember(profile.id) { mutableStateOf(profile.displayName) }
    var endpoint by remember(profile.id) { mutableStateOf(profile.endpoint.orEmpty()) }
    var region by remember(profile.id) { mutableStateOf(profile.region.orEmpty()) }
    var language by remember(profile.id) { mutableStateOf(profile.language) }
    var voiceName by remember(profile.id) { mutableStateOf(profile.voiceName.orEmpty()) }
    var replacementKey by remember(profile.id) { mutableStateOf("") }
    val groqPool =
        profile.providerType.equals("groq", ignoreCase = true) &&
            profile.serviceKind == "chat"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = { Text("Edit " + profile.displayName, color = ProviderText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProviderField(name, { name = it }, "Profile name")
                ProviderField(
                    endpoint,
                    { endpoint = it },
                    if (profile.providerType == "azure") "Azure endpoint" else "Endpoint (optional)",
                )
                if (profile.providerType == "azure" && profile.serviceKind == "speech") {
                    ProviderField(region, { region = it }, "Azure region (optional)")
                }
                if (profile.serviceKind == "speech" || profile.serviceKind == "live_voice") {
                    ProviderField(language, { language = it }, "Language")
                    ProviderField(voiceName, { voiceName = it }, "Voice name (optional)")
                }
                if (groqPool) {
                    Text(
                        "Groq API keys are managed with the key-pool controls on the provider card.",
                        color = ProviderMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                } else {
                    ProviderField(
                        replacementKey,
                        { replacementKey = it },
                        "New API key (leave blank to keep current)",
                        secret = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        endpoint.ifBlank { null },
                        region.ifBlank { null },
                        language,
                        voiceName.ifBlank { null },
                        replacementKey.ifBlank { null },
                    )
                },
                enabled = when {
                    profile.providerType != "azure" -> true
                    profile.serviceKind == "speech" -> endpoint.isNotBlank() || region.isNotBlank()
                    else -> endpoint.isNotBlank()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
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
private fun EditModelDialog(
    profile: NightProviderProfileEntity,
    model: NightProviderModelEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, Set<String>) -> Unit,
) {
    val currentCaps = remember(model.id) {
        model.capabilities.split(",").map { it.trim().lowercase() }.toSet()
    }
    var modelId by remember(model.id) { mutableStateOf(model.modelId) }
    var name by remember(model.id) { mutableStateOf(model.displayName) }
    var deployment by remember(model.id) { mutableStateOf(model.deploymentName.orEmpty()) }
    var vision by remember(model.id) { mutableStateOf("vision" in currentCaps) }
    var tools by remember(model.id) { mutableStateOf("tools" in currentCaps) }
    var imageGeneration by remember(model.id) {
        mutableStateOf("image_generation" in currentCaps)
    }
    var liveVoice by remember(model.id) { mutableStateOf("live_voice" in currentCaps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = { Text("Edit " + model.displayName, color = ProviderText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProviderField(modelId, { modelId = it }, "Model ID")
                ProviderField(name, { name = it }, "Display name")
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = liveVoice,
                                onCheckedChange = { liveVoice = it },
                            )
                            Text("Live voice", color = ProviderText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        modelId,
                        name,
                        deployment.ifBlank { null },
                        buildSet {
                            if (vision) add("vision")
                            if (tools) add("tools")
                            if (imageGeneration) add("image_generation")
                            if (liveVoice) add("live_voice")
                        },
                    )
                },
                enabled = modelId.isNotBlank() || deployment.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
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
                        ProviderField(
                            voiceName,
                            { voiceName = it },
                            "Realtime voice name (alloy or Azure neural voice)",
                        )
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
                    containerColor = MaterialTheme.colorScheme.primary,
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
private fun AddGroqKeyDialog(
    profile: NightProviderProfileEntity,
    onDismiss: () -> Unit,
    onAdd: (String, String?) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151B1E),
        title = {
            Text(
                "Add Groq key",
                color = ProviderText,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Add another key to " + profile.displayName +
                        ". Night rotates keys automatically and skips a key temporarily after rate-limit or authentication errors.",
                    color = ProviderMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                ProviderField(label, { label = it }, "Key label (optional)")
                ProviderField(key, { key = it }, "API key", secret = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        key.trim(),
                        label.trim().ifBlank { null },
                    )
                },
                enabled = key.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
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
    var liveVoice by remember { mutableStateOf(profile.serviceKind == "live_voice") }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = liveVoice,
                                onCheckedChange = { liveVoice = it },
                            )
                            Text("Live voice", color = ProviderText)
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
                        if (profile.serviceKind == "live_voice" || liveVoice) add("live_voice")
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
                    containerColor = MaterialTheme.colorScheme.primary,
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
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}