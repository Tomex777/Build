package com.tomex777.annie

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * L3 (pinned form): the real [CoreAndroidOperationProvider] driven through the real registry with
 * a fake backend. The old `when` dispatcher is gone, so these pin today's observable behaviour:
 * which layer answers, exact messages, and the public error code. Change one only on purpose.
 */
class GoldenBridgeBehaviourTest {
    private class FakeBackend : AndroidCapabilityBackend {
        var failure: Throwable? = null
        val calls = mutableListOf<String>()
        var lastPostKey: String? = "unset"

        private fun hit(name: String): JSONObject {
            calls += name
            failure?.let { throw it }
            return JSONObject().put("hit", name)
        }
        override suspend fun speak(ownerPackageId: String, text: String, languageTag: String?, queueMode: String) = hit("speak")
        override suspend fun ttsStatus(ownerPackageId: String, utteranceId: String): JSONObject {
            calls += "status"
            return JSONObject().put("utteranceId", utteranceId).put("status", "missing")
        }
        override suspend fun stopSpeech(ownerPackageId: String): JSONObject {
            calls += "stop"
            return JSONObject().put("status", "stopped").put("stopped", true).put("cancelledUtterances", 0)
        }
        override suspend fun recognizeText(imageFile: File) = hit("ocr")
        override suspend fun listen(languageTag: String?, prompt: String?) = hit("listen")
        override suspend fun pickTextDocument(mimeType: String) = hit("pick")
        override suspend fun inspectMedia(mediaFile: File) = hit("media")
        override suspend fun postNotification(ownerPackageId: String, key: String?, title: String, text: String): JSONObject {
            lastPostKey = key
            return hit("post")
        }
        override suspend fun updateNotification(ownerPackageId: String, key: String, title: String, text: String) = hit("update")
        override suspend fun cancelNotification(ownerPackageId: String, key: String) = hit("cancel")
    }

    private class FakeAssets : PackageAssetResolver {
        val requested = mutableListOf<Pair<String, String>>()
        var failure: Throwable? = null
        var extension = "png"
        override fun resolveAssetFile(projectId: String, logicalId: String): File {
            requested += projectId to logicalId
            failure?.let { throw it }
            return File("/fake/$logicalId.$extension")
        }
    }

    private val allCapabilities = setOf(
        ANDROID_DEVICE_INFO_CAPABILITY, ANDROID_TTS_CAPABILITY, ANDROID_OCR_CAPABILITY, ANDROID_STT_CAPABILITY,
        ANDROID_DOCUMENTS_CAPABILITY, ANDROID_MEDIA_CAPABILITY, ANDROID_NOTIFICATIONS_CAPABILITY,
    )
    private val allPermissions = setOf(
        ANDROID_DEVICE_INFO_PERMISSION, ANDROID_TTS_PERMISSION, ANDROID_TTS_CONTROL_PERMISSION,
        ANDROID_OCR_PERMISSION, ANDROID_STT_PERMISSION, ANDROID_DOCUMENTS_PERMISSION,
        ANDROID_MEDIA_PERMISSION, ANDROID_NOTIFICATIONS_PERMISSION, ANDROID_NOTIFICATIONS_MANAGE_PERMISSION,
    )

    /** Deliberately dotted and different from the project id, as in a real imported package. */
    private val invocation = OperationInvocation(
        packageId = "com.example.androidtest",
        isPackage = true,
        declaredCapabilities = allCapabilities,
        declaredPermissions = allPermissions,
        grantedPermissions = allPermissions,
        projectId = "android_bridge_test",
        chatId = "chat-1",
    )

    private val backend = FakeBackend()
    private val assets = FakeAssets()
    private val registry = OperationRegistry().apply { register(CoreAndroidOperationProvider(backend, assets)) }

    private fun call(id: String, input: String = "{}", who: OperationInvocation = invocation): JSONObject =
        runBlocking { JSONObject(registry.invoke(id, who, input)) }

    private fun fail(id: String, input: String = "{}", who: OperationInvocation = invocation): AnnieError =
        runBlocking {
            try {
                registry.invoke(id, who, input)
                throw AssertionError("expected $id to fail")
            } catch (error: AnnieError) {
                error
            }
        }

    // ---------- L1-style locks ----------

    @Test fun operationIdsAreLocked() {
        val expected = listOf(
            "android.device.info", "android.documents.pickText", "android.media.inspectAsset",
            "android.notifications.cancel", "android.notifications.post", "android.notifications.update",
            "android.ocr.asset", "android.stt.listen", "android.tts.speak", "android.tts.status", "android.tts.stop",
        )
        assertEquals(expected, registry.all().map { it.id }.sorted())
    }

    @Test fun permissionMatrixMatchesTheSpec() {
        val expected = mapOf(
            "android.device.info" to ANDROID_DEVICE_INFO_PERMISSION,
            "android.tts.speak" to ANDROID_TTS_PERMISSION,
            "android.tts.status" to ANDROID_TTS_CONTROL_PERMISSION,
            "android.tts.stop" to ANDROID_TTS_CONTROL_PERMISSION,
            "android.ocr.asset" to ANDROID_OCR_PERMISSION,
            "android.stt.listen" to ANDROID_STT_PERMISSION,
            "android.documents.pickText" to ANDROID_DOCUMENTS_PERMISSION,
            "android.media.inspectAsset" to ANDROID_MEDIA_PERMISSION,
            "android.notifications.post" to ANDROID_NOTIFICATIONS_PERMISSION,
            "android.notifications.update" to ANDROID_NOTIFICATIONS_MANAGE_PERMISSION,
            "android.notifications.cancel" to ANDROID_NOTIFICATIONS_MANAGE_PERMISSION,
        )
        expected.forEach { (id, permission) ->
            assertEquals(id, listOf(permission), registry.get(id)!!.permissions)
        }
    }

    @Test fun everyOperationDeclaresTheErrorsItsBackendCanRaise() {
        val needed = setOf(
            AnnieErrorCode.FOREGROUND_REQUIRED, AnnieErrorCode.RATE_LIMITED, AnnieErrorCode.NOT_FOUND,
            AnnieErrorCode.TIMEOUT, AnnieErrorCode.CANCELLED, AnnieErrorCode.UNSUPPORTED, AnnieErrorCode.INVALID_ARGUMENT,
        )
        registry.all().forEach { assertTrue(it.id, it.errors.containsAll(needed)) }
    }

    // ---------- gates ----------

    @Test fun looseScriptMessageIsExact() {
        val error = fail("android.device.info", who = invocation.copy(isPackage = false))
        assertEquals(AnnieErrorCode.NOT_A_PACKAGE, error.code)
        assertEquals("Only imported packages can use Android bridge APIs", error.message)
    }

    @Test fun ttsStatusNeedsTheControlPermissionNotJustSpeak() {
        val onlySpeak = invocation.copy(grantedPermissions = setOf(ANDROID_TTS_PERMISSION))
        val error = fail("android.tts.status", """{"utteranceId":"u1"}""", onlySpeak)
        assertEquals(AnnieErrorCode.NOT_GRANTED, error.code)
        assertEquals(ANDROID_TTS_CONTROL_PERMISSION, error.permission)
        assertTrue(backend.calls.isEmpty())
    }

    // ---------- results ----------

    @Test fun deviceInfoLeaksNothingBeyondThreeKeys() {
        val keys = call("android.device.info").keys().asSequence().toSet()
        assertEquals(setOf("platform", "apiLevel", "locale"), keys)
    }

    @Test fun ttsStopAndUnknownStatusAreResultsNotErrors() {
        val stop = call("android.tts.stop")
        assertEquals("stopped", stop.getString("status"))
        assertTrue(stop.getBoolean("stopped"))
        assertEquals("missing", call("android.tts.status", """{"utteranceId":"nope"}""").getString("status"))
    }

    @Test fun blankNotificationKeyReachesTheBackendAsNull() {
        call("android.notifications.post", """{"title":"t","text":"x"}""")
        assertNull(backend.lastPostKey)
        call("android.notifications.post", """{"key":"status","title":"t","text":"x"}""")
        assertEquals("status", backend.lastPostKey)
    }

    // ---------- input errors: which layer answers ----------

    @Test fun ttsQueueOutsideEnumIsRejectedBySchemaBeforeTheProviderText() {
        val error = fail("android.tts.speak", """{"text":"hi","queue":"insert"}""")
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
        assertEquals("Field 'queue' must be one of add, flush", error.message)
    }

    @Test fun ttsTextBoundsAreSchemaMessages() {
        assertEquals("Field 'text' must be at least 1 characters",
            fail("android.tts.speak", """{"text":""}""").message)
        val long = JSONObject().put("text", "a".repeat(2001)).toString()
        assertEquals("Field 'text' must be at most 2000 characters", fail("android.tts.speak", long).message)
    }

    @Test fun whitespaceOnlyTtsTextReachesTheLegacyProviderMessage() {
        val error = fail("android.tts.speak", """{"text":"   "}""")
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
        assertEquals("TTS text must be 1-2000 characters", error.message)
    }

    @Test fun badLanguageTagIsASchemaFormatError() {
        val error = fail("android.tts.speak", """{"text":"hi","language":"english please!!"}""")
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
        assertEquals("Field 'language' has an invalid format", error.message)
    }

    @Test fun documentMimeOutsideAllowlistUsesTheProviderMessage() {
        val error = fail("android.documents.pickText", """{"mimeType":"image/png"}""")
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
        assertEquals("Document picker MIME type is not allowlisted", error.message)
    }

    // ---------- backend errors map to public codes ----------

    @Test fun backgroundedPickerIsForegroundRequired() {
        backend.failure = IllegalStateException("Document selection requires Annie to be open in the foreground")
        val error = fail("android.documents.pickText", """{"mimeType":"text/plain"}""")
        assertEquals(AnnieErrorCode.FOREGROUND_REQUIRED, error.code)
        assertFalse(error.retryable)
    }

    @Test fun unknownNotificationKeyIsNotFound() {
        backend.failure = IllegalArgumentException("Notification key does not exist for this package")
        val error = fail("android.notifications.update", """{"key":"k","title":"t","text":"x"}""")
        assertEquals(AnnieErrorCode.NOT_FOUND, error.code)
        assertEquals("Notification key does not exist for this package", error.message)
    }

    @Test fun notificationRateLimitKeepsItsCodeAndMessage() {
        backend.failure = IllegalStateException("Notification rate limit exceeded for this package; try again later")
        val error = fail("android.notifications.post", """{"title":"t","text":"x"}""")
        assertEquals(AnnieErrorCode.RATE_LIMITED, error.code)
        assertTrue(error.retryable)
        assertEquals("Notification rate limit exceeded for this package; try again later", error.message)
    }

    // ---------- assets ----------

    @Test fun assetOperationsResolveByProjectIdNotPackageId() {
        call("android.ocr.asset", """{"assetId":"scan"}""")
        assets.extension = "mp3"
        call("android.media.inspectAsset", """{"assetId":"clip"}""")
        assertEquals(
            listOf("android_bridge_test" to "scan", "android_bridge_test" to "clip"),
            assets.requested,
        )
    }

    @Test fun assetOperationsNeedAProjectIdentity() {
        val error = fail("android.ocr.asset", """{"assetId":"scan"}""", invocation.copy(projectId = null))
        assertEquals(AnnieErrorCode.NOT_A_PACKAGE, error.code)
        assertTrue(assets.requested.isEmpty())
    }

    @Test fun ocrRejectsAudioAssets() {
        assets.extension = "mp3"
        val error = fail("android.ocr.asset", """{"assetId":"clip"}""")
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
        assertEquals("OCR accepts only a declared PNG, JPEG, WebP, or BMP package asset", error.message)
        assertFalse("ocr" in backend.calls)
    }

    @Test fun mediaInspectionRejectsImageAssets() {
        val error = fail("android.media.inspectAsset", """{"assetId":"scan"}""")
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
        assertEquals("Media inspection accepts only a declared audio or video package asset", error.message)
    }

    @Test fun undeclaredAssetIsNotFoundWithTheOriginalMessage() {
        assets.failure = IllegalStateException("Package asset is not declared: nope")
        val error = fail("android.ocr.asset", """{"assetId":"nope"}""")
        assertEquals(AnnieErrorCode.NOT_FOUND, error.code)
        assertEquals("Package asset is not declared: nope", error.message)
        assertEquals("android.ocr.asset", error.operation)
    }

    @Test fun malformedAssetIdIsInvalidArgument() {
        assets.failure = IllegalArgumentException("Invalid package asset ID")
        val error = fail("android.media.inspectAsset", """{"assetId":"../clip"}""")
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
        assertEquals("Invalid package asset ID", error.message)
    }
}