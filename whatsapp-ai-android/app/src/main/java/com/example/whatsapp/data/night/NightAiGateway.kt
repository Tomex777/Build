package com.example.whatsapp.data.night

import android.content.Context
import android.util.Base64
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class NightAiGateway private constructor(
    private val repository: NightRepository,
    private val router: NightCapabilityRouter,
    private val secrets: NightSecretStore,
    private val http: OkHttpClient,
) {
    suspend fun reply(
        chatId: String,
        displayName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val selected = router.resolveChatModel(chatId)
                ?: error("No chat AI is configured yet.")

            val messages = repository.getMessages(chatId)
            val otherChats = repository.getChats()
                .filter { it.id != chatId && it.latestSummary.isNotBlank() }
                .take(12)
            val latestUserText = messages
                .lastOrNull { it.role == "user" && it.text.isNotBlank() }
                ?.text
                .orEmpty()
            val recallHits = if (latestUserText.isBlank()) {
                emptyList()
            } else {
                NightRecallEngine(repository).findRelevant(
                    currentChatId = chatId,
                    query = latestUserText,
                )
            }

            val system = buildString {
                append("You are Night, the user's private AI assistant. ")
                append("The user's preferred name is ")
                append(displayName)
                append(". Use that name naturally when appropriate. ")
                append("Stay within the current conversation, but use the compact summaries below when relevant. ")
                append("Night supports a two-person Options card. When a compact set of choices would genuinely help, ")
                append("you may add exactly one final line in this format: ")
                append("NIGHT_OPTIONS:{\"title\":\"Question\",\"options\":[\"Option 1\",\"Option 2\"]}. ")
                append("Use 2 to 6 concise options. This is not a poll: never include votes, percentages, or imaginary participants. ")
                append("The NIGHT_OPTIONS line is machine-readable and will not be shown as normal chat text. ")
                append("When the user asks you to choose from an existing Options card, you may add exactly one final line: ")
                append("NIGHT_CHOICE_SELECTION:{\"messageId\":\"the-choice-message-id\",\"index\":0}. ")
                append("Indexes are zero-based. Only select an option that exists in that card. ")
                if (otherChats.isNotEmpty()) {
                    append("\n\nOther Night chat summaries:\n")
                    otherChats.forEach {
                        append("- ")
                        append(it.title)
                        append(": ")
                        append(it.latestSummary.take(800))
                        append("\n")
                    }
                }

                if (recallHits.isNotEmpty()) {
                    append("\nExact older-message references that may be relevant:\n")
                    recallHits.forEach { hit ->
                        append("- [")
                        append(hit.chatTitle)
                        append("] ")
                        append(if (hit.message.role == "assistant") "Night: " else "User: ")
                        append(hit.message.text.take(700))
                        hit.libraryItem?.let { file ->
                            append(" [Library file: ")
                            append(file.name)
                            append(", id=")
                            append(file.id)
                            append("]")
                        }
                        append("\n")
                    }
                    append("Use these only when they are relevant to the user's current request. ")
                    append("Do not claim an attachment's contents unless Night supplied those contents separately.")
                }
            }

            val payloadMessages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))

            val selectedHasVision = supports(selected.model, "vision")

            for (message in messages.takeLast(60)) {
                val role = when (message.role.lowercase()) {
                    "assistant" -> "assistant"
                    "system" -> "system"
                    else -> "user"
                }

                if (message.type == "choice") {
                    val choicePayload = runCatching { JSONObject(message.payloadJson) }.getOrNull()
                    val optionsArray = choicePayload?.optJSONArray("options")
                    val options = buildList {
                        if (optionsArray != null) {
                            for (index in 0 until optionsArray.length()) {
                                val value = optionsArray.optString(index).trim()
                                if (value.isNotBlank()) add(value)
                            }
                        }
                    }

                    val choiceText = buildString {
                        append(message.text.ifBlank { "Choose an option" })
                        append("\n[Options message id: ")
                        append(message.id)
                        append("]")
                        if (options.isNotEmpty()) {
                            append("\nOptions:")
                            options.forEachIndexed { index, option ->
                                append("\n")
                                append(index)
                                append(": ")
                                append(option)
                            }
                        }

                        if (choicePayload?.has("selectedIndex") == true &&
                            !choicePayload.isNull("selectedIndex")
                        ) {
                            val selected = choicePayload.optInt("selectedIndex", -1)
                            if (selected in options.indices) {
                                append("\nSelected: ")
                                append(options[selected])
                                choicePayload.optString("selectedBy")
                                    .takeIf { it.isNotBlank() }
                                    ?.let {
                                        append(" by ")
                                        append(it)
                                    }
                            }
                        }
                    }

                    payloadMessages.put(
                        JSONObject()
                            .put("role", role)
                            .put("content", choiceText)
                    )
                    continue
                }

                if (message.type == "image" && role == "user") {
                    val payload = runCatching { JSONObject(message.payloadJson) }.getOrNull()
                    val path = payload?.optString("localPath").orEmpty()
                    val mime = payload?.optString("mimeType").orEmpty()
                        .ifBlank { "image/jpeg" }

                    if (selectedHasVision && path.isNotBlank() && File(path).exists()) {
                        payloadMessages.put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("type", "text")
                                                .put(
                                                    "text",
                                                    message.text.ifBlank { "Please inspect this image." }
                                                )
                                        )
                                        .put(imagePart(path, mime))
                                )
                        )
                    } else {
                        val fallback = router.resolveCapability(chatId, "vision")
                        val visionText =
                            if (
                                fallback != null &&
                                path.isNotBlank() &&
                                File(path).exists()
                            ) {
                                runCatching {
                                    analyzeImage(
                                        resolved = fallback,
                                        localPath = path,
                                        mimeType = mime,
                                        requestText = message.text,
                                    )
                                }.getOrNull()
                            } else {
                                null
                            }

                        val combined = buildString {
                            append(message.text.ifBlank { "Image attached." })
                            append("\n\n")
                            if (!visionText.isNullOrBlank()) {
                                append("[Vision analysis from ")
                                append(fallback?.profile?.displayName ?: "fallback")
                                append(": ")
                                append(visionText)
                                append("]")
                            } else {
                                append("[Image attached, but no working Vision fallback is configured.]")
                            }
                        }

                        payloadMessages.put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", combined)
                        )
                    }
                } else if (message.text.isNotBlank()) {
                    val decorated = when (message.type) {
                        "file" -> message.text + "\n[File attached in Night Library.]"
                        "voice" -> message.text + "\n[Voice note attached in Night Library.]"
                        else -> message.text
                    }

                    payloadMessages.put(
                        JSONObject()
                            .put("role", role)
                            .put("content", decorated)
                    )
                }
            }

            performChat(selected, payloadMessages)
        }
    }

    suspend fun summarize(
        chatId: String,
        displayName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val pending = repository.unsummarizedMessages(chatId)
            if (pending.isEmpty()) {
                return@runCatching repository.latestSummary(chatId)
            }

            val resolved = router.resolveChatModel(chatId)
                ?: return@runCatching localSummary(pending)

            val chat = repository.getChat(chatId) ?: error("Chat not found.")
            val transcript = pending.joinToString("\n") {
                (if (it.role == "assistant") "Night" else displayName) + ": " + it.text
            }

            val prompt = buildString {
                append("Update the running summary of this private Night conversation. ")
                append("Preserve decisions, preferences, unresolved tasks, referenced files, and facts useful later. ")
                append("Be compact and factual. Do not invent anything.\n\n")
                append("Previous summary:\n")
                append(chat.latestSummary.ifBlank { "(none)" })
                append("\n\nNew messages:\n")
                append(transcript.take(12000))
            }

            val messages = JSONArray()
                .put(
                    JSONObject()
                        .put("role", "system")
                        .put("content", "You maintain compact persistent memory for Night.")
                )
                .put(JSONObject().put("role", "user").put("content", prompt))

            runCatching { performChat(resolved, messages) }
                .getOrElse { localSummary(pending) }
        }
    }

    private fun analyzeImage(
        resolved: NightResolvedModel,
        localPath: String,
        mimeType: String,
        requestText: String,
    ): String {
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "You are Night's Vision helper. Describe only what is useful for the user's request. Be factual and concise."
                    )
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("type", "text")
                                    .put(
                                        "text",
                                        requestText.ifBlank {
                                            "Describe this image so another AI can reason about it."
                                        }
                                    )
                            )
                            .put(imagePart(localPath, mimeType))
                    )
            )

        return performChat(resolved, messages)
    }

    private fun imagePart(
        localPath: String,
        mimeType: String,
    ): JSONObject {
        val file = File(localPath)
        require(file.exists()) { "Image file is missing." }
        require(file.length() <= 20L * 1024L * 1024L) {
            "Image is too large for the current Vision request."
        }

        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val dataUrl = "data:" + mimeType + ";base64," + encoded

        return JSONObject()
            .put("type", "image_url")
            .put(
                "image_url",
                JSONObject().put("url", dataUrl)
            )
    }

    private fun performChat(
        resolved: NightResolvedModel,
        messages: JSONArray,
    ): String {
        val key = secrets.get(resolved.profile.secretAlias)
            ?: error("The saved API key for " + resolved.profile.displayName + " is missing.")

        val body = JSONObject()
            .put("model", resolved.model.deploymentName ?: resolved.model.modelId)
            .put("messages", messages)
            .put("stream", false)

        val requestBuilder = Request.Builder()
            .url(chatEndpoint(resolved.profile))
            .post(
                body.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .header("Content-Type", "application/json")

        when (resolved.profile.providerType.lowercase()) {
            "azure" -> requestBuilder.header("api-key", key)
            else -> requestBuilder.header("Authorization", "Bearer " + key)
        }

        http.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("AI request failed (" + response.code + "): " + extractError(raw))
            }

            val choices = JSONObject(raw).optJSONArray("choices")
                ?: error("Provider returned no choices.")
            if (choices.length() == 0) error("Provider returned an empty response.")

            val content = choices
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .trim()

            if (content.isBlank()) error("Provider returned an empty message.")
            return content
        }
    }

    private fun supports(
        model: NightProviderModelEntity,
        capability: String,
    ): Boolean =
        model.capabilities
            .split(",")
            .map { it.trim().lowercase() }
            .contains(capability.lowercase())

    private fun chatEndpoint(profile: NightProviderProfileEntity): String =
        when (profile.providerType.lowercase()) {
            "deepseek" -> (profile.endpoint ?: "https://api.deepseek.com")
                .trimEnd('/') + "/chat/completions"
            "groq" -> (profile.endpoint ?: "https://api.groq.com/openai/v1")
                .trimEnd('/') + "/chat/completions"
            "azure" -> {
                val base = requireNotNull(profile.endpoint) {
                    "Azure profile needs an endpoint."
                }.trimEnd('/')
                if (base.endsWith("/openai/v1")) {
                    base + "/chat/completions"
                } else {
                    base + "/openai/v1/chat/completions"
                }
            }
            else -> error("Unsupported provider: " + profile.providerType)
        }

    private fun localSummary(messages: List<NightMessageEntity>): String {
        if (messages.isEmpty()) return ""
        return messages
            .filter { it.text.isNotBlank() }
            .takeLast(12)
            .joinToString(" • ") {
                val speaker = if (it.role == "assistant") "Night" else "You"
                speaker + ": " + it.text.take(180)
            }
            .take(2400)
    }

    private fun extractError(raw: String): String =
        runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: raw.take(300)
        }.getOrElse { raw.take(300) }

    companion object {
        @Volatile private var instance: NightAiGateway? = null

        fun get(context: Context): NightAiGateway =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                val repository = NightRepository.get(app)
                instance ?: NightAiGateway(
                    repository = repository,
                    router = NightCapabilityRouter(repository),
                    secrets = NightSecretStore.get(app),
                    http = OkHttpClient.Builder().build(),
                ).also { instance = it }
            }
    }
}
