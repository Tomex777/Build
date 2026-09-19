package com.example.whatsapp.data.night

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NightRepository private constructor(
    private val dao: NightDao,
) {
    fun observeChats(): Flow<List<NightChatEntity>> = dao.observeChats()
    fun observeMessages(chatId: String): Flow<List<NightMessageEntity>> = dao.observeMessages(chatId)
    fun observeLibrary(): Flow<List<NightLibraryItemEntity>> = dao.observeLibrary()
    fun observeProviderProfiles(): Flow<List<NightProviderProfileEntity>> = dao.observeProviderProfiles()
    fun observeModels(profileId: String): Flow<List<NightProviderModelEntity>> = dao.observeModels(profileId)
    fun observeAppearance(): Flow<NightAppearanceEntity?> = dao.observeAppearance()
    fun observeProfile(): Flow<NightProfileEntity?> = dao.observeProfile()

    suspend fun ensureProfile(): NightProfileEntity {
        val existing = dao.getProfile()
        if (existing != null) return existing
        return NightProfileEntity(updatedAt = System.currentTimeMillis()).also(dao::upsertProfile)
    }

    suspend fun setDisplayName(name: String) {
        val current = ensureProfile()
        dao.upsertProfile(
            current.copy(
                displayName = name.trim().ifBlank { current.displayName },
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun ensureAppearance(): NightAppearanceEntity {
        val existing = dao.getAppearance()
        if (existing != null) return existing
        return NightAppearanceEntity(updatedAt = System.currentTimeMillis()).also(dao::upsertAppearance)
    }

    suspend fun setAppearance(appearance: NightAppearanceEntity) {
        dao.upsertAppearance(appearance.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun ensureChat(
        chatId: String,
        title: String,
        now: Long = System.currentTimeMillis(),
    ): NightChatEntity {
        val existing = dao.getChat(chatId)
        if (existing != null) return existing
        return NightChatEntity(
            id = chatId,
            title = title,
            createdAt = now,
            updatedAt = now,
        ).also(dao::upsertChat)
    }

    suspend fun createChat(
        title: String = "New chat",
        now: Long = System.currentTimeMillis(),
    ): NightChatEntity {
        val chat = NightChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertChat(chat)
        return chat
    }

    suspend fun appendText(
        chatId: String,
        role: String,
        text: String,
        replyToMessageId: String? = null,
        now: Long = System.currentTimeMillis(),
    ): NightMessageEntity {
        val chat = requireNotNull(dao.getChat(chatId)) { "Unknown chat: " + chatId }
        val message = NightMessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = role,
            type = "text",
            text = text,
            createdAt = now,
            replyToMessageId = replyToMessageId,
        )
        dao.appendMessage(chat.copy(updatedAt = now, lastMessagePreview = text.take(120)), message)
        return message
    }

    suspend fun appendMessage(message: NightMessageEntity) {
        val chat = requireNotNull(dao.getChat(message.chatId)) { "Unknown chat: " + message.chatId }
        dao.appendMessage(
            chat.copy(
                updatedAt = message.createdAt,
                lastMessagePreview = message.text.take(120),
            ),
            message,
        )
    }

    suspend fun getChat(chatId: String): NightChatEntity? = dao.getChat(chatId)
    suspend fun getChats(): List<NightChatEntity> = dao.getChats()
    suspend fun getMessages(chatId: String): List<NightMessageEntity> = dao.getMessages(chatId)
    suspend fun getMessage(messageId: String): NightMessageEntity? = dao.getMessage(messageId)
    suspend fun renameChat(chatId: String, title: String) = dao.renameChat(chatId, title, System.currentTimeMillis())
    suspend fun clearChat(chatId: String) = dao.clearMessages(chatId)
    suspend fun deleteChat(chatId: String) = dao.deleteChat(chatId)

    suspend fun setChatModel(
        chatId: String,
        provider: String?,
        profileId: String?,
        model: String?,
    ) = dao.setChatModel(
        chatId = chatId,
        provider = provider,
        profileId = profileId,
        model = model,
        updatedAt = System.currentTimeMillis(),
    )

    suspend fun latestSummary(chatId: String): String = dao.getChat(chatId)?.latestSummary.orEmpty()

    suspend fun unsummarizedMessages(chatId: String): List<NightMessageEntity> {
        val chat = dao.getChat(chatId) ?: return emptyList()
        return dao.getMessagesAfter(chatId, chat.lastSummarizedMessageAt ?: 0L)
    }

    suspend fun commitSummary(
        chatId: String,
        summary: String,
        fromMessageAt: Long,
        toMessageAt: Long,
    ) {
        val now = System.currentTimeMillis()
        dao.insertCheckpoint(
            NightSummaryCheckpointEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                summary = summary,
                fromMessageAt = fromMessageAt,
                toMessageAt = toMessageAt,
                createdAt = now,
            )
        )
        dao.commitSummary(chatId, summary, now, toMessageAt)
    }

    suspend fun getProviderProfile(id: String): NightProviderProfileEntity? = dao.getProviderProfile(id)
    suspend fun getProviderModel(id: String): NightProviderModelEntity? = dao.getProviderModel(id)
    suspend fun defaultProviderProfile(serviceKind: String): NightProviderProfileEntity? =
        dao.getDefaultProviderProfile(serviceKind)
    suspend fun defaultProviderModel(profileId: String): NightProviderModelEntity? =
        dao.getDefaultProviderModel(profileId)

    suspend fun upsertProviderProfile(profile: NightProviderProfileEntity) = dao.upsertProviderProfile(profile)
    suspend fun deleteProviderProfile(id: String) = dao.deleteProviderProfile(id)
    suspend fun upsertProviderModel(model: NightProviderModelEntity) = dao.upsertProviderModel(model)
    suspend fun deleteProviderModel(id: String) = dao.deleteProviderModel(id)
    suspend fun capabilityRoute(capability: String): NightCapabilityRouteEntity? = dao.getCapabilityRoute(capability)
    suspend fun setCapabilityRoute(route: NightCapabilityRouteEntity) = dao.upsertCapabilityRoute(route)

    suspend fun addLibraryItem(item: NightLibraryItemEntity) = dao.upsertLibraryItem(item)
    suspend fun getLibraryItem(id: String): NightLibraryItemEntity? = dao.getLibraryItem(id)

    companion object {
        @Volatile private var instance: NightRepository? = null

        fun get(context: Context): NightRepository =
            instance ?: synchronized(this) {
                instance ?: NightRepository(
                    NightDatabase.get(context).nightDao()
                ).also { instance = it }
            }
    }
}
