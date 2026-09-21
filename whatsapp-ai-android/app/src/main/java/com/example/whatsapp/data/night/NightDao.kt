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

    @Query("SELECT * FROM night_chats ORDER BY updatedAt DESC")
    suspend fun getChats(): List<NightChatEntity>

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

    @Query("SELECT * FROM night_messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessagesAcrossChats(limit: Int): List<NightMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: NightMessageEntity)

    @Query("DELETE FROM night_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

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

    @Query("SELECT COUNT(*) FROM night_messages WHERE chatId = :chatId AND createdAt > :after")
    suspend fun countMessagesAfter(chatId: String, after: Long): Int

    @Query("UPDATE night_chats SET summaryDirty = :dirty WHERE id = :chatId")
    suspend fun setSummaryDirtyState(chatId: String, dirty: Boolean)

    @Transaction
    suspend fun commitSummaryCheckpoint(
        checkpoint: NightSummaryCheckpointEntity,
        summary: String,
        updatedAt: Long,
    ) {
        insertCheckpoint(checkpoint)
        commitSummary(
            chatId = checkpoint.chatId,
            summary = summary,
            updatedAt = updatedAt,
            toMessageAt = checkpoint.toMessageAt,
        )
        setSummaryDirtyState(
            chatId = checkpoint.chatId,
            dirty = countMessagesAfter(
                chatId = checkpoint.chatId,
                after = checkpoint.toMessageAt,
            ) > 0,
        )
    }

    @Query("UPDATE night_chats SET summaryDirty = 1, updatedAt = :updatedAt, lastMessagePreview = :preview WHERE id = :chatId")
    suspend fun markSummaryDirty(chatId: String, updatedAt: Long, preview: String)

    @Query("SELECT * FROM night_library_items ORDER BY createdAt DESC")
    fun observeLibrary(): Flow<List<NightLibraryItemEntity>>

    @Query("SELECT * FROM night_library_items ORDER BY createdAt DESC")
    suspend fun getLibraryItems(): List<NightLibraryItemEntity>

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

    @Query("SELECT * FROM night_provider_profiles WHERE serviceKind = :serviceKind AND isEnabled = 1 ORDER BY isDefault DESC, updatedAt DESC")
    suspend fun getEnabledProviderProfiles(serviceKind: String): List<NightProviderProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviderProfile(profile: NightProviderProfileEntity)

    @Query("UPDATE night_provider_profiles SET isDefault = 0 WHERE serviceKind = :serviceKind")
    suspend fun clearDefaultProviderProfiles(serviceKind: String)

    @Query("DELETE FROM night_provider_profiles WHERE id = :id")
    suspend fun deleteProviderProfile(id: String)

    @Query("DELETE FROM night_provider_models WHERE profileId = :profileId")
    suspend fun deleteProviderModelsForProfile(profileId: String)

    @Query("DELETE FROM night_capability_routes WHERE providerProfileId = :profileId")
    suspend fun deleteCapabilityRoutesForProfile(profileId: String)

    @Query("UPDATE night_chats SET selectedProvider = NULL, selectedProviderProfileId = NULL, selectedModel = NULL, updatedAt = :updatedAt WHERE selectedProviderProfileId = :profileId")
    suspend fun clearChatProviderSelection(profileId: String, updatedAt: Long)

    @Transaction
    suspend fun deleteProviderGraph(profileId: String, updatedAt: Long) {
        clearChatProviderSelection(profileId, updatedAt)
        deleteCapabilityRoutesForProfile(profileId)
        deleteProviderModelsForProfile(profileId)
        deleteProviderProfile(profileId)
    }

    @Query("SELECT * FROM night_provider_models WHERE profileId = :profileId AND isEnabled = 1 ORDER BY displayName")
    fun observeModels(profileId: String): Flow<List<NightProviderModelEntity>>

    @Query("SELECT * FROM night_provider_models WHERE profileId = :profileId AND isEnabled = 1 ORDER BY isDefault DESC, updatedAt DESC")
    suspend fun getEnabledProviderModels(profileId: String): List<NightProviderModelEntity>

    @Query("SELECT * FROM night_provider_models ORDER BY providerType, displayName")
    fun observeAllProviderModels(): Flow<List<NightProviderModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviderModel(model: NightProviderModelEntity)

    @Query("UPDATE night_provider_models SET isDefault = 0 WHERE profileId = :profileId")
    suspend fun clearDefaultProviderModels(profileId: String)

    @Query("SELECT * FROM night_provider_models WHERE id = :id LIMIT 1")
    suspend fun getProviderModel(id: String): NightProviderModelEntity?

    @Query("SELECT * FROM night_provider_models WHERE profileId = :profileId AND isDefault = 1 AND isEnabled = 1 LIMIT 1")
    suspend fun getDefaultProviderModel(profileId: String): NightProviderModelEntity?

    @Query("SELECT * FROM night_provider_profiles WHERE serviceKind = :serviceKind AND isDefault = 1 AND isEnabled = 1 LIMIT 1")
    suspend fun getDefaultProviderProfile(serviceKind: String): NightProviderProfileEntity?

    @Query("DELETE FROM night_provider_models WHERE id = :id")
    suspend fun deleteProviderModel(id: String)

    @Query("UPDATE night_chats SET selectedModel = NULL, updatedAt = :updatedAt WHERE selectedModel = :modelId")
    suspend fun clearChatModelSelection(modelId: String, updatedAt: Long)

    @Query("UPDATE night_capability_routes SET modelId = NULL, updatedAt = :updatedAt WHERE modelId = :modelId")
    suspend fun clearCapabilityRouteModel(modelId: String, updatedAt: Long)

    @Transaction
    suspend fun deleteProviderModelGraph(modelId: String, updatedAt: Long) {
        clearChatModelSelection(modelId, updatedAt)
        clearCapabilityRouteModel(modelId, updatedAt)
        deleteProviderModel(modelId)
    }

    @Query("SELECT * FROM night_capability_routes WHERE capability = :capability AND isEnabled = 1 LIMIT 1")
    suspend fun getCapabilityRoute(capability: String): NightCapabilityRouteEntity?

    @Query("SELECT * FROM night_capability_routes ORDER BY capability")
    fun observeCapabilityRoutes(): Flow<List<NightCapabilityRouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCapabilityRoute(route: NightCapabilityRouteEntity)

    @Query("DELETE FROM night_capability_routes WHERE capability = :capability")
    suspend fun deleteCapabilityRoute(capability: String)

    @Query("SELECT * FROM night_appearance WHERE id = 'global' LIMIT 1")
    fun observeAppearance(): Flow<NightAppearanceEntity?>

    @Query("SELECT * FROM night_appearance WHERE id = 'global' LIMIT 1")
    suspend fun getAppearance(): NightAppearanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppearance(appearance: NightAppearanceEntity)

    @Query("SELECT * FROM night_profile WHERE id = 'me' LIMIT 1")
    fun observeProfile(): Flow<NightProfileEntity?>

    @Query("SELECT * FROM night_profile WHERE id = 'me' LIMIT 1")
    suspend fun getProfile(): NightProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: NightProfileEntity)

    @Query("SELECT * FROM night_scheduled_tasks ORDER BY runAt ASC")
    fun observeScheduledTasks(): Flow<List<NightScheduledTaskEntity>>

    @Query("SELECT * FROM night_scheduled_tasks WHERE id = :id LIMIT 1")
    suspend fun getScheduledTask(id: String): NightScheduledTaskEntity?

    @Query("SELECT * FROM night_scheduled_tasks ORDER BY runAt ASC")
    suspend fun getScheduledTasks(): List<NightScheduledTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduledTask(task: NightScheduledTaskEntity)

    @Query("DELETE FROM night_scheduled_tasks WHERE id = :id")
    suspend fun deleteScheduledTask(id: String)

    @Transaction
    suspend fun appendMessage(chat: NightChatEntity, message: NightMessageEntity) {
        upsertChat(chat)
        upsertMessage(message)
        markSummaryDirty(chat.id, message.createdAt, message.text.take(120))
    }
}
