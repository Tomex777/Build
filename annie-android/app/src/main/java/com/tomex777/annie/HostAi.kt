package com.tomex777.annie

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

internal object HostAiConfig {
    private const val ALIAS = "annie.host.ai.v1"
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(context: Context, key: String, model: String) {
        require(model.matches(Regex("[A-Za-z0-9_.:-]{1,100}"))) { "Enter a valid model ID" }
        val prefs = context.getSharedPreferences("annie_host_ai_credentials", Context.MODE_PRIVATE)
        if (key.isBlank()) prefs.edit().remove("key").commit()
        else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            check(prefs.edit().putString("key", encoded).commit()) { "Could not save the AI key" }
        }
        context.getSharedPreferences("annie_host_ai", Context.MODE_PRIVATE).edit().putString("model", model).apply()
    }
    fun key(context: Context): String? {
        val encoded = context.getSharedPreferences("annie_host_ai_credentials", Context.MODE_PRIVATE).getString("key", null) ?: return null
        return runCatching {
            val parts = encoded.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            }
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }
    fun model(context: Context): String = context.getSharedPreferences("annie_host_ai", Context.MODE_PRIVATE)
        .getString("model", "claude-sonnet-5") ?: "claude-sonnet-5"
}

internal fun hostAiOperationDefinitions(): List<OperationDefinition> = listOf("generate", "stream").map { name ->
    OperationDefinition(
        id = "ai.$name", namespace = "ai", name = name, capability = "ai",
        capabilities = if (name == "stream") setOf("ai", "messages") else setOf("ai"),
        permissions = if (name == "stream") listOf("ai.generate", MESSAGES_POST_PERMISSION) else listOf("ai.generate"),
        provider = "host-ai", since = 2,
        input = OperationInputSchema(linkedMapOf("prompt" to OperationProperty("string", required = true, minLength = 1, maxLength = 12_000))),
        js = JsBinding(positional = listOf("prompt"), returns = if (name == "stream") "AnnieMessageHandle" else "{ text: string }"),
        errors = AnnieErrorCode.entries.toSet(),
        docs = "Uses the host's configured AI provider. Scripts never receive its API key.",
    )
}

internal interface HostAiBackend {
    suspend fun generate(prompt: String, onDelta: suspend (String) -> Unit): String
}

/** Fixed HTTPS endpoint; redirects are disabled so host credentials cannot leave this provider. */
internal class AnthropicHostAiBackend(private val context: Context) : HostAiBackend {
    override suspend fun generate(prompt: String, onDelta: suspend (String) -> Unit): String = withContext(Dispatchers.IO) {
        val key = HostAiConfig.key(context)?.takeIf(String::isNotBlank)
            ?: throw AnnieError(AnnieErrorCode.UNAVAILABLE, "Configure Host AI in Script Studio first", "ai.generate")
        val connection = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; instanceFollowRedirects = false
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        val body = JSONObject().put("model", HostAiConfig.model(context)).put("max_tokens", 2048).put("stream", true)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            if (connection.responseCode !in 200..299) throw AnnieError(
                AnnieErrorCode.NETWORK_ERROR, "AI provider rejected the request (HTTP ${connection.responseCode})", "ai.generate")
            connection.inputStream.bufferedReader().use { reader ->
                consumeAnthropicStream(reader, onDelta)
            }
        } finally { connection.disconnect() }
    }
}

internal class HostAiOperationProvider(
    private val context: Context,
    private val backend: HostAiBackend = AnthropicHostAiBackend(context),
    private val granted: (String) -> Set<String>,
) : OperationProvider {
    override val id = "host-ai"
    override val version = "2"
    override val operations = hostAiOperationDefinitions()
    private companion object {
        // Share limits across foreground screens and background ScriptWorkspace instances.
        val requestWindows = ConcurrentHashMap<String, ArrayDeque<Long>>()
    }
    private fun enforceRate(projectId: String, operation: String) {
        val requests = requestWindows.getOrPut(projectId) { ArrayDeque() }
        synchronized(requests) {
            val now = android.os.SystemClock.elapsedRealtime()
            while (requests.isNotEmpty() && now - requests.first() >= 60_000L) requests.removeFirst()
            if (requests.size >= 5) throw AnnieError(AnnieErrorCode.RATE_LIMITED,
                "Too many AI requests; wait before trying again", operation, retryable = true, retryAfterMs = 60_000L)
            requests.addLast(now)
        }
    }
    override suspend fun invoke(operation: OperationDefinition, invocation: OperationInvocation, input: JSONObject): JSONObject {
        val projectId = invocation.projectId ?: error("Missing package identity")
        fun checkGrant() {
            if (!granted(projectId).containsAll(operation.permissions)) throw AnnieError(
                AnnieErrorCode.NOT_GRANTED, "AI permission was revoked", operation.id)
        }
        checkGrant()
        enforceRate(projectId, operation.id)
        val prompt = input.getString("prompt")
        if (operation.name == "generate") {
            val answer = backend.generate(prompt) { checkGrant() }
            checkGrant()
            require(answer.toByteArray().size <= 32_000) { "AI response exceeds the size limit" }
            return JSONObject().put("text", answer)
        }
        val chatId = invocation.chatId?.takeIf(String::isNotBlank) ?: error("Streaming AI needs an active chat")
        val handle = ChatHistoryStore.sendScriptMessage(context, chatId,
            JSONObject().put("type", "text").put("text", "").put("streaming", true).toString(), projectId)
            ?: error("Chat no longer exists")
        val text = StringBuilder()
        fun publish(streaming: Boolean, failed: Boolean = false) {
            val payload = JSONObject().put("type", "text").put("text", text.toString()).put("streaming", streaming).put("streamFailed", failed)
            check(ChatHistoryStore.updateScriptMessage(context, handle.id, projectId, payload.toString()) != null) { "Stream message no longer exists" }
        }
        try {
            backend.generate(prompt) { chunk ->
                checkGrant(); text.append(chunk)
                require(text.toString().toByteArray().size <= 32_000) { "AI response exceeds the size limit" }
                publish(true)
            }
            checkGrant()
            publish(false)
            return handle.toJson()
        } catch (failure: Throwable) {
            runCatching { publish(false, failed = true) }
            throw failure
        }
    }
}
