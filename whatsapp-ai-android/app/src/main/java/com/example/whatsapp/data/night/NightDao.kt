package com.example.whatsapp.data.night

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NightDao {
    @Query("SELECT * FROM night_chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<NightChatEntity>>

    @Query("SELECT * FROM night_chats WHERE id = :chatId LIMIT 1")
    suspend fun getChat(chatId: String): NightChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: NightChatEntity)

    @Query("DELETE FROM night_chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("UPDATE night_chats SET title = :title, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun renameChat(chatId: String, title: String, updatedAt: Long)

    @Query("UPDATE night_chats SET selectedProvider = :provider, selectedProviderProfileId = :profileId, selectedModel = :model, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun setChatModel(
        chatId: String,
        provider: String?,
        profileId: String?,
        model: String?,
        updatedAt: Long,
    )

    @Query("SELECT * FROM night_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeMessages(chatId: String): Flow<List<NightMessageEntity>>

    @Query("SELECT * FROM night_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getMessages(chatId: String): List<NightMessageEntity>

    @Query("SELECT * FROM night_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessage(messageId: String): NightMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: NightMessageEntity)

    @Query("DELETE FROM night_messages WHERE chatId = :chatId")
    suspend fun clearMessages(chatId: String)

    @Query("SELECT * FROM night_messages WHERE chatId = :chatId AND createdAt > :after ORDER BY createdAt ASC")
    suspend fun getMessagesAfter(chatId: String, after: Long): List<NightMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoint(checkpoint: NightSummaryCheckpointEntity)

    @Query("SELECT * FROM night_summary_checkpoints WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestCheckpoint(chatId: String): NightSummaryCheckpointEntity?

    @Query("SELECT * FROM night_summary_checkpoints WHERE chatId = :chatId ORDER BY createdAt DESC")
    suspend fun checkpoints(chatId: String): List<NightSummaryCheckpointEntity>

    @Query("UPDATE night_chats SET latestSummary = :summary, summaryUpdatedAt = :updatedAt, lastSummarizedMessageAt = :toMessageAt, summaryDirty = 0 WHERE id = :chatId")
    suspend fun commitSummary(chatId: String, summary: String, updatedAt: Long, toMessageAt: Long)

    @Query("UPDATE night_chats SET summaryDirty = 1, updatedAt = :updatedAt, lastMessagePreview = :preview WHERE id = :chatId")
    suspend fun markSummaryDirty(chatId: String, updatedAt: Long, preview: String)

    @Query("SELECT * FROM night_library_items ORDER BY createdAt DESC")
    fun observeLibrary(): Flow<List<NightLibraryItemEntity>>

    @Query("SELECT * FROM night_library_items WHERE id = :id LIMIT 1")
    suspend fun getLibraryItem(id: String): NightLibraryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryItem(item: NightLibraryItemEntity)

    @Query("DELETE FROM night_library_items WHERE id = :id")
    suspend fun deleteLibraryItem(id: String)

    @Query("SELECT * FROM night_provider_profiles ORDER BY providerType, displayName")
    fun observeProviderProfiles(): Flow<List<NightProviderProfileEntity>>

    @Query("SELECT * FROM night_provider_profiles WHERE id = :id LIMIT 1")
    suspend fun getProviderProfile(id: String): NightProviderProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviderProfile(profile: NightProviderProfileEntity)

    @Query("DELETE FROM night_provider_profiles WHERE id = :id")
    suspend fun deleteProviderProfile(id: String)

    @Query("SELECT * FROM night_provider_models WHERE profileId = :profileId AND isEnabled = 1 ORDER BY displayName")
    fun observeModels(profileId: String): Flow<List<NightProviderModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviderModel(model: NightProviderModelEntity)

    @Query("DELETE FROM night_provider_models WHERE id = :id")
    suspend fun deleteProviderModel(id: String)

    @Query("SELECT * FROM night_capability_routes WHERE capability = :capability AND isEnabled = 1 LIMIT 1")
    suspend fun getCapabilityRoute(capability: String): NightCapabilityRouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCapabilityRoute(route: NightCapabilityRouteEntity)

    @Query("SELECT * FROM night_appearance WHERE id = 'global' LIMIT 1")
    fun observeAppearance(): Flow<NightAppearanceEntity?>

    @Query("SELECT * FROM night_appearance WHERE id = 'global' LIMIT 1")
    suspend fun getAppearance(): NightAppearanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppearance(appearance: NightAppearanceEntity)

    @Transaction
    suspend fun appendMessage(chat: NightChatEntity, message: NightMessageEntity) {
        upsertChat(chat)
        upsertMessage(message)
        markSummaryDirty(chat.id, message.createdAt, message.text.take(120))
    }
}
