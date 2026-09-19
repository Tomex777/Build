package com.example.whatsapp.data.night

import android.content.Context
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
            val resolved = router.resolveChatModel(chatId)
                ?: error("No chat AI is configured yet.")

            val key = secrets.get(resolved.profile.secretAlias)
                ?: error("The saved API key for " + resolved.profile.displayName + " is missing.")

            val messages = repository.getMessages(chatId)
            val otherChats = repository.getChats()
                .filter { it.id != chatId && it.latestSummary.isNotBlank() }
                .take(12)

            val system = buildString {
                append("You are Night, the user's private AI assistant. ")
                append("The user's preferred name is ")
                append(displayName)
                append(". Use that name naturally when appropriate. ")
                append("Stay within the current conversation, but use the compact summaries below when relevant. ")
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
            }

            val payloadMessages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))

            messages.takeLast(60).forEach { message ->
                val role = when (message.role.lowercase()) {
                    "assistant" -> "assistant"
                    "system" -> "system"
                    else -> "user"
                }
                if (message.text.isNotBlank()) {
                    payloadMessages.put(
                        JSONObject()
                            .put("role", role)
                            .put("content", message.text)
                    )
                }
            }

            val body = JSONObject()
                .put("model", resolved.model.deploymentName ?: resolved.model.modelId)
                .put("messages", payloadMessages)
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

                val json = JSONObject(raw)
                val choices = json.optJSONArray("choices")
                    ?: error("Provider returned no choices.")
                if (choices.length() == 0) error("Provider returned an empty response.")

                val content = choices
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content")
                    .trim()

                if (content.isBlank()) error("Provider returned an empty message.")
                content
            }
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

            val key = secrets.get(resolved.profile.secretAlias)
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

            val body = JSONObject()
                .put("model", resolved.model.deploymentName ?: resolved.model.modelId)
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put("content", "You maintain compact persistent memory for Night.")
                        )
                        .put(JSONObject().put("role", "user").put("content", prompt))
                )
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
                if (!response.isSuccessful) return@runCatching localSummary(pending)

                JSONObject(raw)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: localSummary(pending)
            }
        }
    }

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
