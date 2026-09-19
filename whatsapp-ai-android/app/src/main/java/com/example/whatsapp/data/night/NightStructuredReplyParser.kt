package com.example.whatsapp.data.night

import org.json.JSONObject

data class NightChoicePayload(
    val title: String,
    val options: List<String>,
)

data class NightParsedReply(
    val text: String,
    val choice: NightChoicePayload? = null,
)

object NightStructuredReplyParser {
    private const val PREFIX = "NIGHT_OPTIONS:"

    fun parse(raw: String): NightParsedReply {
        val lines = raw.lines()
        val optionLineIndex = lines.indexOfLast {
            it.trimStart().startsWith(PREFIX)
        }

        if (optionLineIndex < 0) {
            return NightParsedReply(text = raw.trim())
        }

        val line = lines[optionLineIndex].trim()
        val jsonText = line.removePrefix(PREFIX).trim()

        val choice = runCatching {
            val json = JSONObject(jsonText)
            val title = json.optString("title").trim()
            val array = json.optJSONArray("options") ?: return@runCatching null

            val options = buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotBlank()) add(value)
                }
            }.take(6)

            if (title.isBlank() || options.size < 2) {
                null
            } else {
                NightChoicePayload(
                    title = title,
                    options = options,
                )
            }
        }.getOrNull()

        if (choice == null) {
            return NightParsedReply(text = raw.trim())
        }

        val visibleText = lines
            .filterIndexed { index, _ -> index != optionLineIndex }
            .joinToString("\n")
            .trim()

        return NightParsedReply(
            text = visibleText,
            choice = choice,
        )
    }
}
