package com.tomex777.annie

import android.os.Build
import org.json.JSONObject
import java.util.Locale

internal class CoreAndroidOperationProvider(
    private val androidCapabilities: AndroidCapabilityBackend,
    private val files: ScriptFiles,
) : OperationProvider {
    override val id = "core"
    override val version = "1"

    override val operations = listOf(
        op("android.device", "info", ANDROID_DEVICE_INFO_CAPABILITY, ANDROID_DEVICE_INFO_PERMISSION, schema()),
        op("android.tts", "speak", ANDROID_TTS_CAPABILITY, ANDROID_TTS_PERMISSION, schema(
            "text" to OperationProperty("string", required = true, minLength = 1, maxLength = 2000),
            "language" to OperationProperty("string", maxLength = 35, pattern = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")),
            "queue" to OperationProperty("string", enumValues = setOf("add", "flush")),
        )),
        op("android.tts", "status", ANDROID_TTS_CAPABILITY, ANDROID_TTS_CONTROL_PERMISSION, schema(
            "utteranceId" to OperationProperty("string", required = true, maxLength = 96, pattern = Regex("[A-Za-z0-9._-]+")),
        )),
        op("android.tts", "stop", ANDROID_TTS_CAPABILITY, ANDROID_TTS_CONTROL_PERMISSION, schema()),
        op("android.ocr", "asset", ANDROID_OCR_CAPABILITY, ANDROID_OCR_PERMISSION, schema(
            "assetId" to OperationProperty("string", required = true, maxLength = 128),
        )),
        op("android.stt", "listen", ANDROID_STT_CAPABILITY, ANDROID_STT_PERMISSION, schema(
            "language" to OperationProperty("string", maxLength = 35, pattern = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")),
            "prompt" to OperationProperty("string", maxLength = 160),
        )),
        op("android.documents", "pickText", ANDROID_DOCUMENTS_CAPABILITY, ANDROID_DOCUMENTS_PERMISSION, schema(
            "mimeType" to OperationProperty("string", maxLength = 64),
        )),
        op("android.media", "inspectAsset", ANDROID_MEDIA_CAPABILITY, ANDROID_MEDIA_PERMISSION, schema(
            "assetId" to OperationProperty("string", required = true, maxLength = 128),
        )),
        op("android.notifications", "post", ANDROID_NOTIFICATIONS_CAPABILITY, ANDROID_NOTIFICATIONS_PERMISSION, schema(
            "key" to OperationProperty("string", maxLength = 64, pattern = Regex("[A-Za-z0-9._-]*")),
            "title" to OperationProperty("string", required = true, minLength = 1, maxLength = 80),
            "text" to OperationProperty("string", required = true, minLength = 1, maxLength = 500),
        )),
        op("android.notifications", "update", ANDROID_NOTIFICATIONS_CAPABILITY, ANDROID_NOTIFICATIONS_MANAGE_PERMISSION, schema(
            "key" to OperationProperty("string", required = true, maxLength = 64, pattern = Regex("[A-Za-z0-9._-]+")),
            "title" to OperationProperty("string", required = true, minLength = 1, maxLength = 80),
            "text" to OperationProperty("string", required = true, minLength = 1, maxLength = 500),
        )),
        op("android.notifications", "cancel", ANDROID_NOTIFICATIONS_CAPABILITY, ANDROID_NOTIFICATIONS_MANAGE_PERMISSION, schema(
            "key" to OperationProperty("string", required = true, maxLength = 64, pattern = Regex("[A-Za-z0-9._-]+")),
        )),
    )

    override suspend fun invoke(operation: OperationDefinition, invocation: OperationInvocation, input: JSONObject): JSONObject {
        fun languageTag(): String? = input.optString("language").trim().takeIf(String::isNotBlank)?.also { tag ->
            require(tag.length <= 35 && tag.matches(Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*"))) {
                "Android bridge language must be a short BCP-47 style tag"
            }
        }

        return when (operation.id) {
            "android.device.info" -> JSONObject()
                .put("platform", "android")
                .put("apiLevel", Build.VERSION.SDK_INT)
                .put("locale", Locale.getDefault().toLanguageTag())

            "android.tts.speak" -> {
                val text = input.optString("text")
                val queueMode = input.optString("queue", "add").trim().lowercase().ifBlank { "add" }
                require(text.isNotBlank() && text.length <= 2_000) { "TTS text must be 1-2000 characters" }
                require(queueMode in setOf("add", "flush")) { "TTS queue must be add or flush" }
                androidCapabilities.speak(invocation.packageId, text, languageTag(), queueMode)
            }

            "android.tts.status" -> {
                val utteranceId = input.optString("utteranceId").trim()
                require(utteranceId.isNotBlank() && utteranceId.length <= 96 && utteranceId.matches(Regex("[A-Za-z0-9._-]+"))) {
                    "TTS status requires a valid package-owned utterance ID"
                }
                androidCapabilities.ttsStatus(invocation.packageId, utteranceId)
            }

            "android.tts.stop" -> androidCapabilities.stopSpeech(invocation.packageId)

            "android.ocr.asset" -> {
                val assetId = input.optString("assetId").trim()
                require(assetId.isNotBlank() && assetId.length <= 128) { "OCR requires a package asset ID" }
                val image = files.resolveAssetFile(invocation.packageId, assetId)
                require(image.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "bmp")) {
                    "OCR accepts only a declared PNG, JPEG, WebP, or BMP package asset"
                }
                androidCapabilities.recognizeText(image)
            }

            "android.stt.listen" -> {
                val prompt = input.optString("prompt").trim()
                require(prompt.length <= 160) { "STT prompt is too long" }
                androidCapabilities.listen(languageTag(), prompt.takeIf(String::isNotBlank))
            }

            "android.documents.pickText" -> {
                val mimeType = input.optString("mimeType", "text/*").trim().ifBlank { "text/*" }
                require(mimeType in setOf("text/*", "text/plain", "text/csv", "application/json", "application/xml")) {
                    "Document picker MIME type is not allowlisted"
                }
                androidCapabilities.pickTextDocument(mimeType)
            }

            "android.media.inspectAsset" -> {
                val assetId = input.optString("assetId").trim()
                require(assetId.isNotBlank() && assetId.length <= 128) { "Media inspection requires a package asset ID" }
                val media = files.resolveAssetFile(invocation.packageId, assetId)
                require(media.extension.lowercase() in setOf(
                    "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac",
                    "mp4", "webm", "mkv", "ts", "m4v",
                )) { "Media inspection accepts only a declared audio or video package asset" }
                androidCapabilities.inspectMedia(media)
            }

            "android.notifications.post" -> {
                val key = input.optString("key").trim()
                val title = input.optString("title").trim()
                val text = input.optString("text").trim()
                require(title.isNotBlank() && title.length <= 80) { "Notification title must be 1-80 characters" }
                require(text.isNotBlank() && text.length <= 500) { "Notification text must be 1-500 characters" }
                androidCapabilities.postNotification(invocation.packageId, key.takeIf(String::isNotBlank), title, text)
            }

            "android.notifications.update" -> {
                val key = input.optString("key").trim()
                val title = input.optString("title").trim()
                val text = input.optString("text").trim()
                require(title.isNotBlank() && title.length <= 80) { "Notification title must be 1-80 characters" }
                require(text.isNotBlank() && text.length <= 500) { "Notification text must be 1-500 characters" }
                androidCapabilities.updateNotification(invocation.packageId, key, title, text)
            }

            "android.notifications.cancel" -> {
                val key = input.optString("key").trim()
                androidCapabilities.cancelNotification(invocation.packageId, key)
            }

            else -> throw AnnieError(
                AnnieErrorCode.UNSUPPORTED,
                "Operation is not available: \${operation.id}",
                operation.id,
            )
        }
    }

    private fun op(namespace: String, name: String, capability: String, permission: String, input: OperationInputSchema) =
        OperationDefinition(
            id = "\$namespace.\$name",
            namespace = namespace,
            name = name,
            capability = capability,
            permissions = listOf(permission),
            provider = id,
            since = 1,
            input = input,
        )

    private fun schema(vararg properties: Pair<String, OperationProperty>) =
        OperationInputSchema(linkedMapOf(*properties), additionalProperties = false)
}
