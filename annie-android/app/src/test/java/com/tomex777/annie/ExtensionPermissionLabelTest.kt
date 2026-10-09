package com.tomex777.annie

import org.json.JSONObject
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every permission a registry operation can require must have a plain-language label on the grant screen. */
class ExtensionPermissionLabelTest {
    private class NoBackend : AndroidCapabilityBackend {
        override suspend fun speak(ownerPackageId: String, text: String, languageTag: String?, queueMode: String) = JSONObject()
        override suspend fun ttsStatus(ownerPackageId: String, utteranceId: String) = JSONObject()
        override suspend fun stopSpeech(ownerPackageId: String) = JSONObject()
        override suspend fun recognizeText(imageFile: File) = JSONObject()
        override suspend fun listen(languageTag: String?, prompt: String?) = JSONObject()
        override suspend fun pickTextDocument(mimeType: String) = JSONObject()
        override suspend fun inspectMedia(mediaFile: File) = JSONObject()
        override suspend fun postNotification(ownerPackageId: String, key: String?, title: String, text: String) = JSONObject()
        override suspend fun updateNotification(ownerPackageId: String, key: String, title: String, text: String) = JSONObject()
        override suspend fun cancelNotification(ownerPackageId: String, key: String) = JSONObject()
    }

    @Test fun everyRegistryPermissionHasAHandWrittenLabel() {
        val resolver = object : PackageAssetResolver {
            override fun resolveAssetFile(projectId: String, logicalId: String): File = File("unused")
        }
        val definitions = CoreAndroidOperationProvider(NoBackend(), resolver).operations + downloadOperationDefinitions() + messageOperationDefinitions()
        val permissions = definitions.flatMap { it.permissions }.toSet()
        assertTrue(permissions.isNotEmpty())
        permissions.forEach { permission ->
            val fallback = permission.substringAfterLast('.').replace('_', ' ').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            assertNotEquals("$permission has no plain-language label in extensionPermissionLabel", fallback, extensionPermissionLabel(permission))
        }
    }
}
