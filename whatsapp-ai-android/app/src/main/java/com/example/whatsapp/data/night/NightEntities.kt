package com.example.whatsapp.data.night

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "night_chats")
data class NightChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessagePreview: String = "",
    val selectedProvider: String? = null,
    val selectedProviderProfileId: String? = null,
    val selectedModel: String? = null,
    val latestSummary: String = "",
    val summaryUpdatedAt: Long? = null,
    val lastSummarizedMessageAt: Long? = null,
    val summaryDirty: Boolean = false,
)

@Entity(
    tableName = "night_messages",
    foreignKeys = [
        ForeignKey(
            entity = NightChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("chatId"), Index("createdAt"), Index("replyToMessageId"), Index("libraryFileId")],
)
data class NightMessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String,
    val type: String,
    val text: String = "",
    val createdAt: Long,
    val replyToMessageId: String? = null,
    val libraryFileId: String? = null,
    val payloadJson: String = "{}",
    val deliveryState: String = "sent",
)

@Entity(
    tableName = "night_summary_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = NightChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("chatId"), Index("createdAt")],
)
data class NightSummaryCheckpointEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val summary: String,
    val fromMessageAt: Long,
    val toMessageAt: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "night_library_items",
    indices = [Index("createdAt"), Index("sourceMessageId")],
)
data class NightLibraryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val createdAt: Long,
    val sourceChatId: String? = null,
    val sourceMessageId: String? = null,
)
