package com.tomex777.annie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class ScriptAssistRequest(
    val instruction: String,
    val fileName: String,
    val currentSource: String,
)

internal data class ScriptAssistValidation(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val canApply: Boolean get() = errors.isEmpty()
}

internal interface ScriptAssistantProvider {
    val id: String
    val displayName: String
    suspend fun generate(request: ScriptAssistRequest, apiKey: String): String
}

internal class ScriptAssistant(
    private val providers: List<ScriptAssistantProvider> = listOf(AnthropicScriptAssistantProvider()),
) {
    fun availableProviders(): List<ScriptAssistantProvider> = providers

    suspend fun generate(providerId: String, request: ScriptAssistRequest, apiKey: String): String {
        require(apiKey.isNotBlank()) { "API key is required" }
        val provider = providers.firstOrNull { it.id == providerId }
            ?: error("Unknown Script Assist provider")
        return stripMarkdownFence(provider.generate(request, apiKey)).trim()
    }
}

internal class AnthropicScriptAssistantProvider(
    private val endpoint: String = "https://api.anthropic.com/v1/messages",
    private val model: String = "claude-sonnet-5",
) : ScriptAssistantProvider {
    override val id: String = "anthropic"
    override val displayName: String = "Claude (Anthropic)"

    override suspend fun generate(request: ScriptAssistRequest, apiKey: String): String =
        withContext(Dispatchers.IO) {
            val prompt = buildString {
                appendLine("Requested change:")
                appendLine(request.instruction.trim())
                appendLine()
                appendLine("Current file: ${request.fileName}")
                appendLine("Current source:")
                appendLine(request.currentSource)
                appendLine()
                appendLine("Return the complete proposed JavaScript source for this file only.")
            }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 6_000)
                .put("system", ANNIE_SCRIPT_ASSIST_SYSTEM)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", prompt)
                    )
                )

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val message = runCatching {
                        JSONObject(response).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    error(message.ifBlank { "Script Assist request failed (HTTP $status)" })
                }
                val json = JSONObject(response)
                val content = json.optJSONArray("content") ?: error("Provider returned no content")
                val text = buildString {
                    for (index in 0 until content.length()) {
                        val block = content.optJSONObject(index) ?: continue
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }
                text.takeIf(String::isNotBlank) ?: error("Provider returned an empty proposal")
            } finally {
                connection.disconnect()
            }
        }
}

internal object ScriptAssistValidator {
    fun validate(source: String): ScriptAssistValidation {
        val errors = buildList {
            if (source.isBlank()) add("Proposal is empty.")
            if (source.length > 500_000) add("Proposal is too large for Script Studio.")
            if (source.contains("```")) add("Proposal still contains Markdown code fences.")
        }
        val warnings = buildList {
            if ("annie." !in source) add("This file does not reference Annie's scripting API.")
            if ("eval(" in source || "new Function(" in source) add("Proposal contains dynamic code evaluation; review it carefully.")
            if (Regex("""(?:fetch|XMLHttpRequest)\s*\(""").containsMatchIn(source)) {
                add("Use annie.http.request(...) instead of browser-only HTTP APIs.")
            }
        }
        return ScriptAssistValidation(errors, warnings)
    }
}

internal fun buildScriptAssistDiff(before: String, after: String): String {
    if (before == after) return "No changes."
    val oldLines = before.lines()
    val newLines = after.lines()
    var prefix = 0
    while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++
    var suffix = 0
    while (
        suffix < oldLines.size - prefix &&
        suffix < newLines.size - prefix &&
        oldLines[oldLines.lastIndex - suffix] == newLines[newLines.lastIndex - suffix]
    ) suffix++

    val contextBefore = oldLines.subList((prefix - 2).coerceAtLeast(0), prefix)
    val contextAfterStart = (oldLines.size - suffix).coerceAtLeast(prefix)
    val contextAfter = oldLines.subList(contextAfterStart, minOf(oldLines.size, contextAfterStart + 2))
    val removed = oldLines.subList(prefix, oldLines.size - suffix)
    val added = newLines.subList(prefix, newLines.size - suffix)

    return buildString {
        contextBefore.forEach { appendLine("  $it") }
        removed.forEach { appendLine("- $it") }
        added.forEach { appendLine("+ $it") }
        contextAfter.forEach { appendLine("  $it") }
    }.trimEnd()
}

internal fun scriptAssistInsertedFragment(before: String, after: String): String {
    if (before == after) return ""
    val oldLines = before.lines()
    val newLines = after.lines()
    var prefix = 0
    while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++
    var suffix = 0
    while (
        suffix < oldLines.size - prefix &&
        suffix < newLines.size - prefix &&
        oldLines[oldLines.lastIndex - suffix] == newLines[newLines.lastIndex - suffix]
    ) suffix++
    return newLines.subList(prefix, newLines.size - suffix).joinToString("\n")
}

private fun stripMarkdownFence(value: String): String {
    val trimmed = value.trim()
    if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) return trimmed
    val firstNewline = trimmed.indexOf('\n')
    if (firstNewline < 0) return trimmed
    return trimmed.substring(firstNewline + 1, trimmed.length - 3).trim()
}

private const val ANNIE_SCRIPT_ASSIST_SYSTEM = """You are Script Assist inside Annie, a native Android commands-first media/script app.
Write JavaScript for Annie's existing local QuickJS runtime. Do not invent browser globals or undocumented native APIs.
Return JavaScript source only, with no Markdown fences and no explanation.

Annie script contract:
- Register slash commands with annie.commands.register({ name, aliases?, description?, usage?, keywords?, capabilities?, suggestions?, async execute(ctx) { ... } }).
- A suggestion is { label, input } and must describe an action the script really accepts.
- Register follow-up sessions with annie.sessions.register({ name, async onMessage(ctx) { ... } }); start/end using ctx.session.start(name) and ctx.session.end().
- Register native message actions with annie.actions.register(name, async (payload, ctx) => { ... }).
- HTTP: await annie.http.request({ url, method?, headers?, body?, timeoutMs?, browserSession? }).
- Browser: annie.browser.open(spec), annie.browser.session(id), await annie.browser.clear(id).
- Storage: await annie.storage.get(key), await annie.storage.set(key, value).
- Files: annie.files.readText(path), writeText(path, text), delete(path), list(path).
- Logging: annie.log.info/warn/error(...).
- Native structured messages: annie.messages.text(text), image(value), music(value), video(value), options(value), progress(value), browser(value).
- Direct structured return objects may use type: text, image, music, video, options, progress, or browser.
- Images can use annie.image.chess(fen) for a native chess-board asset.
- ctx includes text, args, command, chatId, messageId and replyTo.
Keep command behavior deterministic. Kotlin/Compose owns rendering; never build an HTML/WebView UI to imitate Annie messages.
"""
