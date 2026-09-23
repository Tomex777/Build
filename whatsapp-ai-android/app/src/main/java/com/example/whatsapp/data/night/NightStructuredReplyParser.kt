package com.example.whatsapp.data.night

import org.json.JSONObject

data class NightChoicePayload(
    val title: String,
    val options: List<String>,
    val multiple: Boolean = false,
)

data class NightChoiceSelection(
    val messageId: String,
    val index: Int,
)

data class NightParsedReply(
    val text: String,
    val choice: NightChoicePayload? = null,
    val choiceSelection: NightChoiceSelection? = null,
)

object NightStructuredReplyParser {
    private const val OPTIONS_PREFIX = "NIGHT_OPTIONS:"
    private const val SELECTION_PREFIX = "NIGHT_CHOICE_SELECTION:"

    fun parse(raw: String): NightParsedReply {
        var choice: NightChoicePayload? = null
        var selection: NightChoiceSelection? = null
        val visibleLines = mutableListOf<String>()

        raw.lines().forEach { original ->
            val line = original.trim()

            when {
                line.startsWith(OPTIONS_PREFIX) -> {
                    val parsed = parseOptions(
                        line.removePrefix(OPTIONS_PREFIX).trim()
                    )
                    if (parsed != null) {
                        choice = parsed
                    } else {
                        visibleLines += original
                    }
                }

                line.startsWith(SELECTION_PREFIX) -> {
                    val parsed = parseSelection(
                        line.removePrefix(SELECTION_PREFIX).trim()
                    )
                    if (parsed != null) {
                        selection = parsed
                    } else {
                        visibleLines += original
                    }
                }

                else -> visibleLines += original
            }
        }

        val visibleText = visibleLines.joinToString("\n").trim()
        return NightParsedReply(
            text = choice?.let {
                NightChoiceResponsePolicy.suppressDuplicateOptionList(
                    visibleText,
                    it.title,
                    it.options,
                )
            } ?: visibleText,
            choice = choice,
            choiceSelection = selection,
        )
    }

    private fun parseOptions(jsonText: String): NightChoicePayload? =
        runCatching {
            val json = JSONObject(jsonText)
            val title = json.optString("title").trim()
            val array = json.optJSONArray("options") ?: return@runCatching null

            val options = buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotBlank()) add(value)
                }
            }.take(8)

            if (title.isBlank() || options.size < 2) {
                null
            } else {
                NightChoicePayload(
                    title = title,
                    options = options,
                    multiple = json.optBoolean("multiple", false),
                )
            }
        }.getOrNull()

    private fun parseSelection(jsonText: String): NightChoiceSelection? =
        runCatching {
            val json = JSONObject(jsonText)
            val messageId = json.optString("messageId").trim()
            if (messageId.isBlank() || !json.has("index")) return@runCatching null

            val index = json.optInt("index", -1)
            if (index < 0) {
                null
            } else {
                NightChoiceSelection(
                    messageId = messageId,
                    index = index,
                )
            }
        }.getOrNull()
}

internal object NightChoiceResponsePolicy {
    fun suppressDuplicateOptionList(
        text: String,
        title: String,
        options: List<String>,
    ): String {
        if (text.isBlank() || options.size < 2) return text
        val normalizedTitle = normalize(title)
        val normalizedOptions = options.map(::normalize).filter { it.isNotBlank() }.toSet()
        val lines = text.lines()
        val normalizedLines = lines.map { normalize(it) }
        if (normalizedTitle !in normalizedLines) return text
        if (!normalizedOptions.all { it in normalizedLines }) return text

        val remaining = lines.filterNot { line ->
            val normalized = normalize(line)
            normalized.isBlank() || normalized == normalizedTitle || normalized in normalizedOptions
        }.joinToString("\n").trim()
        if (remaining.isBlank()) return ""
        val onlySelectionNudge = remaining.lines().all { line ->
            line.length <= 220 && Regex("(?i)\\b(choose|pick|option|select|let me know|which one)\\b")
                .containsMatchIn(line)
        }
        return if (onlySelectionNudge) "" else remaining
    }

    private fun normalize(raw: String): String = raw
        .replace(Regex("^\\s*\\d+\\s*[\\p{Punct}\\p{M}]*\\s*"), "")
        .replace("**", "")
        .replace("`", "")
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '/' || it.isWhitespace() }
        .replace(Regex("\\s+"), " ")
        .trim()
}
