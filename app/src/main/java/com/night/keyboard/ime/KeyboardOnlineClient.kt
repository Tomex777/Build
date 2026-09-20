package com.night.keyboard.ime

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class OnlineKeyboardTool(val path: String) {
    EDITOR("editor"),
    TONE("tone"),
    RESEARCH("research"),
}

class KeyboardOnlineClient(
    private val baseUrl: String,
) {
    suspend fun run(
        tool: OnlineKeyboardTool,
        text: String,
        tone: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = URL(baseUrl.trim().trimEnd('/'))
            require(root.protocol.equals("https", ignoreCase = true)) {
                "Keyboard online tools require HTTPS."
            }
            require(text.isNotBlank()) { "Nothing selected to send." }

            val endpoint = URL(root.toString().trimEnd('/') + "/v1/keyboard/" + tool.path)
            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 12_000
                connection.readTimeout = 30_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")

                val payload = JSONObject()
                    .put("text", text)
                    .put("source", "keyboard")
                if (!tone.isNullOrBlank()) payload.put("tone", tone)

                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()

                require(code in 200..299) {
                    "Server returned HTTP $code" + if (body.isBlank()) "" else ": " + body.take(180)
                }

                val parsed = runCatching { JSONObject(body) }.getOrNull()
                val result = parsed?.optString("text")
                    ?.takeIf { it.isNotBlank() }
                    ?: parsed?.optString("result")?.takeIf { it.isNotBlank() }
                    ?: parsed?.optString("output")?.takeIf { it.isNotBlank() }
                    ?: body.trim()

                require(result.isNotBlank()) { "Server returned an empty result." }
                result
            } finally {
                connection.disconnect()
            }
        }
    }
}
