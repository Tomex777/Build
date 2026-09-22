package com.example.whatsapp.data.night

import android.content.Context
import java.util.UUID

class NightProviderManager private constructor(
    private val repository: NightRepository,
    private val secrets: NightSecretStore,
    private val dao: NightDao,
) {
    suspend fun addProfile(
        providerType: String,
        serviceKind: String,
        displayName: String,
        apiKey: String,
        endpoint: String?,
        region: String?,
        language: String = "en-US",
        voiceName: String? = null,
        makeDefault: Boolean,
    ): NightProviderProfileEntity {
        require(apiKey.isNotBlank()) { "API key is required." }
        if (providerType == "azure") {
            when (serviceKind) {
                "chat", "live_voice" ->
                    require(!endpoint.isNullOrBlank()) { "Azure endpoint is required for this service." }
                "speech" ->
                    require(!endpoint.isNullOrBlank() || !region.isNullOrBlank()) {
                        "Azure Speech needs a resource endpoint or region."
                    }
            }
        }

        val id = UUID.randomUUID().toString()
        val alias = "provider_" + id
        if (providerType.equals("groq", ignoreCase = true) && serviceKind == "chat") {
            secrets.initializeProviderKeyPool(alias, apiKey)
        } else {
            secrets.put(alias, apiKey)
        }

        if (makeDefault) {
            dao.clearDefaultProviderProfiles(serviceKind)
        }

        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = id,
            providerType = providerType,
            serviceKind = serviceKind,
            displayName = displayName.trim().ifBlank {
                providerType.replaceFirstChar { it.uppercase() }
            },
            secretAlias = alias,
            endpoint = endpoint?.trim()?.ifBlank { null },
            region = region?.trim()?.ifBlank { null },
            language = language.trim().ifBlank { "en-US" },
            voiceName = voiceName?.trim()?.ifBlank { null },
            capabilities = when (serviceKind) {
                "speech" -> "stt,tts,translation"
                "live_voice" -> "live_voice"
                else -> "chat"
            },
            isEnabled = true,
            isDefault = makeDefault,
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertProviderProfile(profile)
        ensureBundledChatModel(profile)
        return profile
    }

    suspend fun addModel(
        profile: NightProviderProfileEntity,
        modelId: String,
        displayName: String,
        deploymentName: String?,
        capabilities: Set<String>,
        makeDefault: Boolean,
    ): NightProviderModelEntity {
        require(modelId.isNotBlank() || !deploymentName.isNullOrBlank()) {
            "Model or deployment name is required."
        }

        if (makeDefault) {
            dao.clearDefaultProviderModels(profile.id)
        }

        val now = System.currentTimeMillis()
        val model = NightProviderModelEntity(
            id = UUID.randomUUID().toString(),
            profileId = profile.id,
            providerType = profile.providerType,
            modelId = modelId.trim().ifBlank { deploymentName.orEmpty().trim() },
            displayName = displayName.trim().ifBlank {
                deploymentName?.trim().takeUnless { it.isNullOrBlank() }
                    ?: modelId.trim()
            },
            deploymentName = deploymentName?.trim()?.ifBlank { null },
            capabilities = (setOf("text") + capabilities)
                .map { it.lowercase() }
                .distinct()
                .joinToString(","),
            isEnabled = true,
            isDefault = makeDefault,
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertProviderModel(model)
        return model
    }

    suspend fun updateProfile(
        profile: NightProviderProfileEntity,
        displayName: String,
        endpoint: String?,
        region: String?,
        language: String,
        voiceName: String?,
        replacementApiKey: String? = null,
    ) {
        val normalizedEndpoint = endpoint?.trim()?.ifBlank { null }
        val normalizedRegion = region?.trim()?.ifBlank { null }
        if (profile.providerType == "azure") {
            when (profile.serviceKind) {
                "chat", "live_voice" ->
                    require(!normalizedEndpoint.isNullOrBlank()) {
                        "Azure endpoint is required for this service."
                    }
                "speech" ->
                    require(!normalizedEndpoint.isNullOrBlank() || !normalizedRegion.isNullOrBlank()) {
                        "Azure Speech needs a resource endpoint or region."
                    }
            }
        }

        replacementApiKey
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { newKey ->
                require(
                    !(profile.providerType.equals("groq", ignoreCase = true) &&
                        profile.serviceKind == "chat")
                ) {
                    "Use the Groq key-pool controls to add or remove Groq keys."
                }
                secrets.put(profile.secretAlias, newKey)
            }

        repository.upsertProviderProfile(
            profile.copy(
                displayName = displayName.trim().ifBlank { profile.displayName },
                endpoint = normalizedEndpoint,
                region = normalizedRegion,
                language = language.trim().ifBlank { "en-US" },
                voiceName = voiceName?.trim()?.ifBlank { null },
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateModel(
        model: NightProviderModelEntity,
        modelId: String,
        displayName: String,
        deploymentName: String?,
        capabilities: Set<String>,
    ) {
        val deployment = deploymentName?.trim()?.ifBlank { null }
        val normalizedModelId = modelId.trim().ifBlank {
            deployment ?: model.modelId
        }
        require(normalizedModelId.isNotBlank()) {
            "Model or deployment name is required."
        }
        val profile = repository.getProviderProfile(model.profileId)
            ?: error("Provider profile was not found.")
        val normalizedCapabilities = buildSet {
            add("text")
            capabilities.forEach { add(it.lowercase()) }
            if (profile.serviceKind == "live_voice") add("live_voice")
        }.joinToString(",")

        repository.upsertProviderModel(
            model.copy(
                modelId = normalizedModelId,
                displayName = displayName.trim().ifBlank {
                    deployment ?: normalizedModelId
                },
                deploymentName = deployment,
                capabilities = normalizedCapabilities,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setProfileEnabled(
        profile: NightProviderProfileEntity,
        enabled: Boolean,
    ) {
        repository.upsertProviderProfile(
            profile.copy(
                isEnabled = enabled,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun makeProfileDefault(profile: NightProviderProfileEntity) {
        dao.clearDefaultProviderProfiles(profile.serviceKind)
        repository.upsertProviderProfile(
            profile.copy(
                isEnabled = true,
                isDefault = true,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setModelEnabled(
        model: NightProviderModelEntity,
        enabled: Boolean,
    ) {
        repository.upsertProviderModel(
            model.copy(
                isEnabled = enabled,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun makeModelDefault(model: NightProviderModelEntity) {
        dao.clearDefaultProviderModels(model.profileId)
        repository.upsertProviderModel(
            model.copy(
                isEnabled = true,
                isDefault = true,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun ensureProviderOnboardingDefaults() {
        repository.enabledProviderProfiles("chat").forEach { profile ->
            ensureBundledChatModel(profile)
        }
    }

    private suspend fun ensureBundledChatModel(
        profile: NightProviderProfileEntity,
    ): NightProviderModelEntity? {
        if (
            profile.serviceKind != "chat" ||
            !profile.providerType.equals("groq", ignoreCase = true)
        ) {
            return null
        }

        val existing = repository.getProviderModels(profile.id)
        if (existing.isNotEmpty()) {
            val enabled = existing.filter { it.isEnabled }
            val currentDefault = enabled.firstOrNull { it.isDefault }
            if (currentDefault != null) return currentDefault

            val onlyEnabled = enabled.singleOrNull() ?: return null
            makeModelDefault(onlyEnabled)
            return repository.getProviderModel(onlyEnabled.id)
        }

        return addModel(
            profile = profile,
            modelId = "openai/gpt-oss-20b",
            displayName = "GPT-OSS 20B",
            deploymentName = null,
            capabilities = setOf("tools"),
            makeDefault = true,
        )
    }

    fun keySummaries(profile: NightProviderProfileEntity): List<NightProviderKeySummary> =
        secrets.getProviderKeySummaries(profile.secretAlias)

    suspend fun addProviderKey(
        profile: NightProviderProfileEntity,
        apiKey: String,
        label: String? = null,
    ): NightProviderKeySummary {
        require(profile.providerType.equals("groq", ignoreCase = true)) {
            "Key pools are currently supported for Groq chat profiles."
        }
        require(profile.serviceKind == "chat") {
            "Groq key rotation is only available for chat profiles."
        }

        val credential = secrets.addProviderCredential(
            alias = profile.secretAlias,
            secret = apiKey,
            label = label,
        )
        repository.upsertProviderProfile(
            profile.copy(updatedAt = System.currentTimeMillis())
        )
        return NightProviderKeySummary(
            id = credential.id,
            label = credential.label,
            suffix = credential.secret.takeLast(4),
        )
    }

    suspend fun deleteProviderKey(
        profile: NightProviderProfileEntity,
        credentialId: String,
    ) {
        require(profile.providerType.equals("groq", ignoreCase = true)) {
            "Key pools are currently supported for Groq chat profiles."
        }
        check(secrets.removeProviderCredential(profile.secretAlias, credentialId)) {
            "A provider must keep at least one API key."
        }
        repository.upsertProviderProfile(
            profile.copy(updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteProfile(profile: NightProviderProfileEntity) {
        dao.deleteProviderGraph(
            profileId = profile.id,
            updatedAt = System.currentTimeMillis(),
        )
        secrets.remove(profile.secretAlias)
    }

    suspend fun deleteModel(model: NightProviderModelEntity) {
        dao.deleteProviderModelGraph(
            modelId = model.id,
            updatedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        @Volatile private var instance: NightProviderManager? = null

        fun get(context: Context): NightProviderManager =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                val db = NightDatabase.get(app)
                instance ?: NightProviderManager(
                    repository = NightRepository.get(app),
                    secrets = NightSecretStore.get(app),
                    dao = db.nightDao(),
                ).also { instance = it }
            }
    }
}
