package com.example.whatsapp.data.night

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "night_scheduled_tasks",
    indices = [Index("chatId"), Index("runAt"), Index("state")],
)
data class NightScheduledTaskEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val prompt: String,
    val runAt: Long,
    val repeatMinutes: Long? = null,
    val state: String = "scheduled",
    val createdAt: Long,
    val updatedAt: Long,
)
