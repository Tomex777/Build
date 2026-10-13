package com.tomex777.annie

import java.io.BufferedReader
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import org.json.JSONObject

/** Parse real SSE frames; truncated responses must not be reported as complete. */
internal suspend fun consumeAnthropicStream(reader: BufferedReader, onDelta: suspend (String) -> Unit): String {
    val result = StringBuilder()
    val deadline = System.nanoTime() + 120_000_000_000L
    var complete = false
        var eventData = StringBuilder()
        while (true) {
            coroutineContext.ensureActive()
            if (System.nanoTime() >= deadline) throw AnnieError(AnnieErrorCode.TIMEOUT, "AI response timed out", "ai.generate")
            val line = reader.readLine() ?: break
            require(line.length <= 64_000 && eventData.length <= 64_000) { "AI event exceeds the size limit" }
            if (line.startsWith("data:")) {
                if (eventData.isNotEmpty()) eventData.append('\n')
                eventData.append(line.removePrefix("data:").trimStart())
            } else if (line.isEmpty() && eventData.isNotEmpty()) {
                val event = JSONObject(eventData.toString())
                eventData = StringBuilder()
                when (event.optString("type")) {
                    "content_block_delta" -> {
                        val delta = event.optJSONObject("delta")
                        if (delta?.optString("type") == "text_delta") {
                            val chunk = delta.optString("text")
                            result.append(chunk)
                            require(result.toString().toByteArray().size <= 32_000) { "AI response exceeds the size limit" }
                            onDelta(chunk)
                        }
                    }
                    "error" -> throw AnnieError(AnnieErrorCode.NETWORK_ERROR, "AI provider interrupted the response", "ai.generate")
                    "message_stop" -> { complete = true; break }
                }
            }
        }
    if (!complete) throw AnnieError(AnnieErrorCode.NETWORK_ERROR, "AI response ended before completion", "ai.generate")
    return result.toString()
}
