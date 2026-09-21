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
}
