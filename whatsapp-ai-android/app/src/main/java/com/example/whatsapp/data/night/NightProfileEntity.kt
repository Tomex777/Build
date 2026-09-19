package com.example.whatsapp.data.night

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "night_profile")
data class NightProfileEntity(
    @PrimaryKey val id: String = "me",
    val displayName: String = "Dawson",
    val updatedAt: Long = 0L,
)
