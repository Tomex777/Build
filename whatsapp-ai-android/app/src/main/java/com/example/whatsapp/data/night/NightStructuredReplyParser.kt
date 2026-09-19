package com.example.whatsapp.data.night

import org.json.JSONObject

data class NightChoicePayload(
    val title: String,
    val options: List<String>,
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

        return NightParsedReply(
            text = visibleLines.joinToString("\n").trim(),
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
