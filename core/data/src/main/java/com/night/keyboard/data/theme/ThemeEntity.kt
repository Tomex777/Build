package com.night.keyboard.data.theme

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "themes")
data class ThemeEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val snapshotJson: String, val createdAt: Long, val updatedAt: Long, val active: Boolean)
