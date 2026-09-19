package com.example.whatsapp.data.night

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "night_provider_profiles",
    indices = [Index("providerType"), Index("isEnabled")],
)
data class NightProviderProfileEntity(
    @PrimaryKey val id: String,
    val providerType: String, // deepseek | groq | azure
    val displayName: String,
    val secretAlias: String,
    val endpoint: String? = null,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "night_provider_models",
    indices = [Index("profileId"), Index("providerType"), Index("isEnabled")],
)
data class NightProviderModelEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val providerType: String,
    val modelId: String,
    val displayName: String,
    val deploymentName: String? = null,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
