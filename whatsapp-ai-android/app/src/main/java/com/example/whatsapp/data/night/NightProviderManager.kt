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
        makeDefault: Boolean,
    ): NightProviderProfileEntity {
        require(apiKey.isNotBlank()) { "API key is required." }
        if (providerType == "azure") {
            require(!endpoint.isNullOrBlank()) { "Azure endpoint is required." }
        }

        val id = UUID.randomUUID().toString()
        val alias = "provider_" + id
        secrets.put(alias, apiKey)

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
