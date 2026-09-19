package com.example.whatsapp.data.night

data class NightResolvedModel(
    val profile: NightProviderProfileEntity,
    val model: NightProviderModelEntity,
)

class NightCapabilityRouter(
    private val repository: NightRepository,
) {
    suspend fun resolveChatModel(chatId: String): NightResolvedModel? {
        val chat = repository.getChat(chatId) ?: return null

        val profile = chat.selectedProviderProfileId
            ?.let { repository.getProviderProfile(it) }
            ?: repository.defaultProviderProfile("chat")
            ?: return null

        val model = chat.selectedModel
            ?.let { repository.getProviderModel(it) }
            ?.takeIf { it.profileId == profile.id && it.isEnabled }
            ?: repository.defaultProviderModel(profile.id)
            ?: return null

        return NightResolvedModel(profile, model)
    }

    suspend fun resolveCapability(
        chatId: String,
        capability: String,
    ): NightResolvedModel? {
        val selected = resolveChatModel(chatId)

        if (
            selected != null &&
            selected.model.capabilities
                .split(",")
                .map { it.trim().lowercase() }
                .contains(capability.lowercase())
        ) {
            return selected
        }

        val route = repository.capabilityRoute(capability) ?: return null
        val profile = repository.getProviderProfile(route.providerProfileId) ?: return null

        val model = route.modelId
            ?.let { repository.getProviderModel(it) }
            ?.takeIf { it.profileId == profile.id && it.isEnabled }
            ?: repository.defaultProviderModel(profile.id)
            ?: return null

        return NightResolvedModel(profile, model)
    }
}
