package com.example.whatsapp.data.night

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightProviderAdminInstrumentedTest {

    @Test
    fun adminCanToggleAndChangeDefaults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = NightRepository.get(context)
        val manager = NightProviderManager.get(context)
        val suffix = UUID.randomUUID().toString()

        val first = manager.addProfile(
            providerType = "groq",
            serviceKind = "chat",
            displayName = "First " + suffix,
            apiKey = "gsk_first_" + suffix,
            endpoint = null,
            region = null,
            makeDefault = true,
        )
        val second = manager.addProfile(
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Second " + suffix,
            apiKey = "gsk_second_" + suffix,
            endpoint = null,
            region = null,
            makeDefault = false,
        )
        val firstModel = manager.addModel(
            profile = first,
            modelId = "first-model-" + suffix,
            displayName = "First model",
            deploymentName = null,
            capabilities = emptySet(),
            makeDefault = true,
        )
        val secondModel = manager.addModel(
            profile = first,
            modelId = "second-model-" + suffix,
            displayName = "Second model",
            deploymentName = null,
            capabilities = emptySet(),
            makeDefault = false,
        )

        manager.setProfileEnabled(first, false)
        assertEquals(false, requireNotNull(repository.getProviderProfile(first.id)).isEnabled)

        manager.makeProfileDefault(second)
        val updatedSecond = requireNotNull(repository.getProviderProfile(second.id))
        assertEquals(true, updatedSecond.isEnabled)
        assertEquals(true, updatedSecond.isDefault)
        assertEquals(false, requireNotNull(repository.getProviderProfile(first.id)).isDefault)

        manager.setModelEnabled(firstModel, false)
        assertEquals(false, requireNotNull(repository.getProviderModel(firstModel.id)).isEnabled)

        manager.makeModelDefault(secondModel)
        val updatedSecondModel = requireNotNull(repository.getProviderModel(secondModel.id))
        assertEquals(true, updatedSecondModel.isEnabled)
        assertEquals(true, updatedSecondModel.isDefault)
        assertEquals(false, requireNotNull(repository.getProviderModel(firstModel.id)).isDefault)

        manager.deleteProfile(first)
        manager.deleteProfile(second)
    }

    @Test
    fun deletingProfileClearsChatsModelsAndCapabilityRoutes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = NightRepository.get(context)
        val manager = NightProviderManager.get(context)
        val suffix = UUID.randomUUID().toString()
        val profile = manager.addProfile(
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Delete profile " + suffix,
            apiKey = "gsk_profile_" + suffix,
            endpoint = null,
            region = null,
            makeDefault = false,
        )
        val model = manager.addModel(
            profile = profile,
            modelId = "model-" + suffix,
            displayName = "Model " + suffix,
            deploymentName = null,
            capabilities = setOf("vision"),
            makeDefault = false,
        )
        val chat = repository.createChat("Provider delete test")
        repository.setChatModel(chat.id, profile.providerType, profile.id, model.id)
        val capability = "admin-profile-" + suffix
        repository.setCapabilityRoute(
            NightCapabilityRouteEntity(
                id = "route-" + suffix,
                capability = capability,
                providerProfileId = profile.id,
                modelId = model.id,
                updatedAt = System.currentTimeMillis(),
            )
        )

        manager.deleteProfile(profile)

        assertNull(repository.getProviderProfile(profile.id))
        assertNull(repository.getProviderModel(model.id))
        assertNull(repository.capabilityRoute(capability))
        val updatedChat = requireNotNull(repository.getChat(chat.id))
        assertNull(updatedChat.selectedProvider)
        assertNull(updatedChat.selectedProviderProfileId)
        assertNull(updatedChat.selectedModel)
    }

    @Test
    fun deletingModelKeepsProfileButClearsModelReferences() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = NightRepository.get(context)
        val manager = NightProviderManager.get(context)
        val suffix = UUID.randomUUID().toString()
        val profile = manager.addProfile(
            providerType = "deepseek",
            serviceKind = "chat",
            displayName = "Keep profile " + suffix,
            apiKey = "sk_model_" + suffix,
            endpoint = null,
            region = null,
            makeDefault = false,
        )
        val model = manager.addModel(
            profile = profile,
            modelId = "model-" + suffix,
            displayName = "Delete model " + suffix,
            deploymentName = null,
            capabilities = setOf("vision"),
            makeDefault = false,
        )
        val chat = repository.createChat("Model delete test")
        repository.setChatModel(chat.id, profile.providerType, profile.id, model.id)
        val capability = "admin-model-" + suffix
        repository.setCapabilityRoute(
            NightCapabilityRouteEntity(
                id = "route-model-" + suffix,
                capability = capability,
                providerProfileId = profile.id,
                modelId = model.id,
                updatedAt = System.currentTimeMillis(),
            )
        )

        manager.deleteModel(model)

        assertNotNull(repository.getProviderProfile(profile.id))
        assertNull(repository.getProviderModel(model.id))
        val updatedChat = requireNotNull(repository.getChat(chat.id))
        assertEquals(profile.id, updatedChat.selectedProviderProfileId)
        assertNull(updatedChat.selectedModel)
        val route = requireNotNull(repository.capabilityRoute(capability))
        assertEquals(profile.id, route.providerProfileId)
        assertNull(route.modelId)

        manager.deleteProfile(profile)
    }

    @Test
    fun editingProviderAndModelPersistsAdminChanges() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = NightRepository.get(context)
        val manager = NightProviderManager.get(context)
        val secrets = NightSecretStore.get(context)
        val suffix = UUID.randomUUID().toString()

        val chatProfile = manager.addProfile(
            providerType = "azure",
            serviceKind = "chat",
            displayName = "Azure chat " + suffix,
            apiKey = "old-key-" + suffix,
            endpoint = "https://old-" + suffix + ".openai.azure.com",
            region = null,
            makeDefault = false,
        )
        val model = manager.addModel(
            profile = chatProfile,
            modelId = "old-model-" + suffix,
            displayName = "Old model",
            deploymentName = "old-deployment-" + suffix,
            capabilities = setOf("vision"),
            makeDefault = false,
        )
        val speechProfile = manager.addProfile(
            providerType = "azure",
            serviceKind = "speech",
            displayName = "Azure speech " + suffix,
            apiKey = "speech-key-" + suffix,
            endpoint = null,
            region = "eastus",
            language = "en-US",
            voiceName = "en-US-JennyNeural",
            makeDefault = false,
        )

        manager.updateProfile(
            profile = chatProfile,
            displayName = "Edited Azure chat",
            endpoint = "  https://edited-" + suffix + ".openai.azure.com  ",
            region = null,
            language = "en-US",
            voiceName = null,
            replacementApiKey = "replacement-key-" + suffix,
        )
        manager.updateModel(
            model = model,
            modelId = "edited-model-" + suffix,
            displayName = "Edited model",
            deploymentName = "edited-deployment-" + suffix,
            capabilities = setOf("tools", "image_generation", "live_voice"),
        )
        manager.updateProfile(
            profile = speechProfile,
            displayName = "Edited Azure speech",
            endpoint = null,
            region = "  canadacentral  ",
            language = "fr-CA",
            voiceName = "fr-CA-SylvieNeural",
            replacementApiKey = null,
        )

        val updatedChatProfile = requireNotNull(repository.getProviderProfile(chatProfile.id))
        assertEquals("Edited Azure chat", updatedChatProfile.displayName)
        assertEquals(
            "https://edited-" + suffix + ".openai.azure.com",
            updatedChatProfile.endpoint,
        )
        assertEquals(
            "replacement-key-" + suffix,
            secrets.get(updatedChatProfile.secretAlias),
        )

        val updatedModel = requireNotNull(repository.getProviderModel(model.id))
        assertEquals("edited-model-" + suffix, updatedModel.modelId)
        assertEquals("Edited model", updatedModel.displayName)
        assertEquals("edited-deployment-" + suffix, updatedModel.deploymentName)
        assertEquals(
            setOf("text", "tools", "image_generation", "live_voice"),
            updatedModel.capabilities.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet(),
        )

        val updatedSpeechProfile = requireNotNull(repository.getProviderProfile(speechProfile.id))
        assertEquals("Edited Azure speech", updatedSpeechProfile.displayName)
        assertEquals("canadacentral", updatedSpeechProfile.region)
        assertEquals("fr-CA", updatedSpeechProfile.language)
        assertEquals("fr-CA-SylvieNeural", updatedSpeechProfile.voiceName)

        manager.deleteProfile(updatedChatProfile)
        manager.deleteProfile(updatedSpeechProfile)
    }

    @Test
    fun groqEditKeepsApiKeysInKeyPoolControls() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = NightProviderManager.get(context)
        val secrets = NightSecretStore.get(context)
        val suffix = UUID.randomUUID().toString()
        val profile = manager.addProfile(
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Groq pool " + suffix,
            apiKey = "gsk_original_" + suffix,
            endpoint = null,
            region = null,
            makeDefault = false,
        )
        val before = secrets.getProviderCredentials(profile.secretAlias).map { it.secret }

        val result = runCatching {
            manager.updateProfile(
                profile = profile,
                displayName = "Groq pool edited",
                endpoint = null,
                region = null,
                language = "en-US",
                voiceName = null,
                replacementApiKey = "gsk_replacement_" + suffix,
            )
        }

        assertEquals(true, result.isFailure)
        assertEquals(before, secrets.getProviderCredentials(profile.secretAlias).map { it.secret })
        manager.deleteProfile(profile)
    }


    @Test
    fun profileOnlyCapabilityRouteUsesAnyEnabledCapableModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = NightRepository.get(context)
        val manager = NightProviderManager.get(context)
        val suffix = UUID.randomUUID().toString()
        val capability = "admin-capability-" + suffix
        val profile = manager.addProfile(
            providerType = "azure",
            serviceKind = "chat",
            displayName = "Capability profile " + suffix,
            apiKey = "capability-key-" + suffix,
            endpoint = "https://capability-" + suffix + ".openai.azure.com",
            region = null,
            makeDefault = false,
        )
        manager.addModel(
            profile = profile,
            modelId = "default-model-" + suffix,
            displayName = "Default without capability",
            deploymentName = null,
            capabilities = emptySet(),
            makeDefault = true,
        )
        val capable = manager.addModel(
            profile = profile,
            modelId = "capable-model-" + suffix,
            displayName = "Capable fallback",
            deploymentName = null,
            capabilities = setOf(capability),
            makeDefault = false,
        )
        val chat = repository.createChat("Capability fallback test")
        repository.setCapabilityRoute(
            NightCapabilityRouteEntity(
                id = "route-capability-" + suffix,
                capability = capability,
                providerProfileId = profile.id,
                modelId = null,
                useSelectedChatModelFirst = false,
                updatedAt = System.currentTimeMillis(),
            )
        )

        val resolved = NightCapabilityRouter(repository).resolveCapability(chat.id, capability)

        assertEquals(profile.id, resolved?.profile?.id)
        assertEquals(capable.id, resolved?.model?.id)
        manager.deleteProfile(profile)
    }

}
