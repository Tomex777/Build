package com.tomex777.annie

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Conformance L3: failure messages of the registry path equal the legacy bridge messages; only the
 * typed code is new. Do not edit an expectation without adding a row to test/resources/golden-changes.md.
 */
class OperationGoldenTest {
    private val calls = mutableListOf<String>()

    private val backend = object : AndroidCapabilityBackend {
        override suspend fun speak(ownerPackageId: String, text: String, languageTag: String?, queueMode: String) =
            JSONObject().put("utteranceId", "u1").also { calls += "speak:$queueMode:$languageTag" }
        override suspend fun ttsStatus(ownerPackageId: String, utteranceId: String) = JSONObject().put("status", "missing")
        override suspend fun stopSpeech(ownerPackageId: String) =
            JSONObject().put("status", "stopped").put("stopped", true).put("cancelledUtterances", 0)
        override suspend fun recognizeText(imageFile: File) = JSONObject()
        override suspend fun listen(languageTag: String?, prompt: String?) = JSONObject()
        override suspend fun pickTextDocument(mimeType: String) = JSONObject()
        override suspend fun inspectMedia(mediaFile: File) = JSONObject()
        override suspend fun postNotification(ownerPackageId: String, key: String?, title: String, text: String): JSONObject {
            if (title == "ratelimited") throw IllegalStateException("Notification rate limit exceeded for this package; try again later")
            return JSONObject()
        }
        override suspend fun updateNotification(ownerPackageId: String, key: String, title: String, text: String): JSONObject =
            throw IllegalStateException("Notification key does not exist for this package")
        override suspend fun cancelNotification(ownerPackageId: String, key: String) = JSONObject()
    }

    private val assets = object : PackageAssetResolver {
        override fun resolveAssetFile(projectId: String, logicalId: String): File {
            require(logicalId.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}"))) { "Invalid package asset ID" }
            error("Package asset is not declared: $logicalId")
        }
    }

    private val registry = OperationRegistry().apply { register(CoreAndroidOperationProvider(backend, assets)) }

    private fun invocation(op: String) = registry.get(op)!!.let { d ->
        OperationInvocation("pkg", true, d.capabilities, d.permissions.toSet(), d.permissions.toSet(), projectId = "p")
    }

    private fun failure(op: String, input: String): AnnieError =
        runCatching { runBlocking { registry.invoke(op, invocation(op), input) } }.exceptionOrNull() as? AnnieError
            ?: error("expected failure for $op $input")

    private fun expect(op: String, input: String, message: String, code: AnnieErrorCode) {
        val error = failure(op, input)
        assertEquals("$op $input", message, error.message)
        assertEquals("$op $input", code, error.code)
    }

    @Test fun ttsSpeakFailuresKeepLegacyText() {
        expect("android.tts.speak", """{"text":"hi","queue":"insert"}""", "TTS queue must be add or flush", AnnieErrorCode.INVALID_ARGUMENT)
        expect("android.tts.speak", """{"text":""}""", "TTS text must be 1-2000 characters", AnnieErrorCode.INVALID_ARGUMENT)
        expect("android.tts.speak", JSONObject().put("text", "x".repeat(2001)).toString(), "TTS text must be 1-2000 characters", AnnieErrorCode.INVALID_ARGUMENT)
        expect("android.tts.speak", """{"text":"hi","language":"english please!!"}""", "Android bridge language must be a short BCP-47 style tag", AnnieErrorCode.INVALID_ARGUMENT)
    }

    @Test fun assetOperationsKeepLegacyText() {
        expect("android.ocr.asset", """{"assetId":"/sdcard/foo.png"}""", "Invalid package asset ID", AnnieErrorCode.INVALID_ARGUMENT)
        expect("android.media.inspectAsset", """{"assetId":"../clip"}""", "Invalid package asset ID", AnnieErrorCode.INVALID_ARGUMENT)
        expect("android.ocr.asset", """{"assetId":"nope"}""", "Package asset is not declared: nope", AnnieErrorCode.NOT_FOUND)
    }

    @Test fun documentPickerRejectsDisallowedMime() {
        expect("android.documents.pickText", """{"mimeType":"application/zip"}""", "Document picker MIME type is not allowlisted", AnnieErrorCode.INVALID_ARGUMENT)
    }

    @Test fun missingNotificationKeyMapsToNotFound() {
        val error = failure("android.notifications.update", """{"key":"k","title":"t","text":"x"}""")
        assertEquals("Notification key does not exist for this package", error.message)
        assertEquals(AnnieErrorCode.NOT_FOUND, error.code)
    }

    @Test fun notificationRateLimitKeepsLegacyTextAndIsRetryable() {
        val error = failure("android.notifications.post", """{"title":"ratelimited","text":"x"}""")
        assertEquals("Notification rate limit exceeded for this package; try again later", error.message)
        assertEquals(AnnieErrorCode.RATE_LIMITED, error.code)
        assertTrue(error.retryable)
    }

    @Test fun notificationFieldMessagesAreLegacy() {
        expect("android.notifications.post", """{"title":"","text":"x"}""", "Notification title must be 1-80 characters", AnnieErrorCode.INVALID_ARGUMENT)
        expect("android.notifications.post", """{"title":"t","text":""}""", "Notification text must be 1-500 characters", AnnieErrorCode.INVALID_ARGUMENT)
    }

    @Test fun looseScriptGetsNotAPackage() = runBlocking {
        val d = registry.get("android.device.info")!!
        val loose = OperationInvocation("", false, emptySet(), emptySet(), emptySet())
        val error = runCatching { registry.invoke(d.id, loose, "{}") }.exceptionOrNull() as AnnieError
        assertEquals("Only imported packages can use Android bridge APIs", error.message)
        assertEquals(AnnieErrorCode.NOT_A_PACKAGE, error.code)
    }

    @Test fun successShapesMatchFieldObservations() = runBlocking {
        val stop = JSONObject(registry.invoke("android.tts.stop", invocation("android.tts.stop"), "{}"))
        assertEquals("""{"status":"stopped","stopped":true,"cancelledUtterances":0}""".let(::JSONObject).toString(), stop.toString())
        val status = JSONObject(registry.invoke("android.tts.status", invocation("android.tts.status"), """{"utteranceId":"zzz"}"""))
        assertEquals("missing", status.getString("status")) // unknown id is a result, not an error
        val info = JSONObject(registry.invoke("android.device.info", invocation("android.device.info"), "{}"))
        assertEquals(setOf("platform", "apiLevel", "locale"), info.keys().asSequence().toSet()) // leak test
    }

    @Test fun legacyOptionalEmptyStringsStillPass() = runBlocking {
        registry.invoke("android.tts.speak", invocation("android.tts.speak"), """{"text":"hi","language":"","queue":""}""")
        assertTrue(calls.single().startsWith("speak:add:"))
    }
}
