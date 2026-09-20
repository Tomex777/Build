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

private fun NightProviderModelEntity.supportsCapability(capability: String): Boolean =
    capabilities
        .split(",")
        .map { it.trim().lowercase() }
        .contains(capability.trim().lowercase())
            ?: repository.defaultProviderProfile("chat")
            ?: return null

        val model = chat.selectedModel
            ?.let { repository.getProviderModel(it) }
            ?.takeIf { it.profileId == profile.id && it.isEnabled }
            ?: repository.defaultProviderModel(profile.id)
            ?: return null

        return NightResolvedModel(profile, model)
    }

    suspend fun resolveChatCandidates(chatId: String): List<NightResolvedModel> {
        val primary = resolveChatModel(chatId)
        val candidates = mutableListOf<NightResolvedModel>()
        if (primary != null) candidates += primary

        repository.enabledProviderProfiles("chat").forEach { profile ->
            repository.enabledProviderModels(profile.id).forEach { model ->
                candidates += NightResolvedModel(profile, model)
            }
        }

        return candidates.distinctBy { it.profile.id + ":" + it.model.id }
    }

    suspend fun resolveCapability(
        chatId: String,
        capability: String,
    ): NightResolvedModel? {
        val selected = resolveChatModel(chatId)
        val selectedSupports = selected?.model?.capabilities
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.contains(capability.lowercase())
            ?: false

        val route = repository.capabilityRoute(capability)

        if (selectedSupports && (route == null || route.useSelectedChatModelFirst)) {
            return selected
        }

        if (route != null) {
            val profile = repository.getProviderProfile(route.providerProfileId)
                ?.takeIf { it.isEnabled }
            if (profile != null) {
                val model = route.modelId
                    ?.let { repository.getProviderModel(it) }
                    ?.takeIf {
                        it.profileId == profile.id &&
                            it.isEnabled &&
                            it.supportsCapability(capability)
                    }
                    ?: repository.defaultProviderModel(profile.id)
                        ?.takeIf { it.isEnabled && it.supportsCapability(capability) }

                if (model != null) {
                    return NightResolvedModel(profile, model)
                }
            }
        }

        return if (selectedSupports) selected else null
    }
}
