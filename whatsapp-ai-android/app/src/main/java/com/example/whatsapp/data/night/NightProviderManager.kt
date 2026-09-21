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
        secrets.remove(profile.secretAlias)
        repository.deleteProviderProfile(profile.id)
    }

    suspend fun deleteModel(model: NightProviderModelEntity) {
        repository.deleteProviderModel(model.id)
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
