package com.example.whatsapp.data.night

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "night_appearance")
data class NightAppearanceEntity(
    @PrimaryKey val id: String = "global",
    val userBubbleColor: Long = 0xFF7E112EL,
    val aiBubbleColor: Long = 0xFF242625L,
    val wallpaperTopColor: Long = 0xFF6A0011L,
    val wallpaperMiddleColor: Long = 0xFF8F0018L,
    val wallpaperBottomColor: Long = 0xFF4F000EL,
    val accentColor: Long = 0xFFCF4A69L,
    val fontFamilyKey: String = "system",
    val messageFontScale: Float = 1.0f,
    val updatedAt: Long = 0L,
)
