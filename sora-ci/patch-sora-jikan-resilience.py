from pathlib import Path

path = Path('sora-overlay/app/src/main/java/com/night/sora/catalog/JikanCatalogClient.kt')
text = path.read_text()

old = '''    private fun requestJsonWithRetry(url: String): JSONObject {
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            try {
                throttle()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Sora-Android/0.4")
                val code = connection.responseCode
                if (code == 429 && attempt == 0) {
                    connection.disconnect()
                    Thread.sleep(1_100)
                    return@repeat
                }
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                connection.disconnect()
                if (code !in 200..299) error("Jikan HTTP $code")
                return JSONObject(body)
            } catch (t: Throwable) {
                lastError = t
                if (attempt == 0) Thread.sleep(450)
            }
        }
        throw lastError ?: IllegalStateException("Jikan request failed")
    }
'''

new = '''    private fun requestJsonWithRetry(url: String): JSONObject {
        var lastError: Throwable? = null
        repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
            try {
                throttle()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Sora-Android/0.4")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.let { BufferedReader(InputStreamReader(it)).use(BufferedReader::readText) }.orEmpty()
                val retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                connection.disconnect()

                if (code in 200..299) return JSONObject(body)

                val retryable = code == 408 || code == 425 || code == 429 || code in 500..599
                if (!retryable || attempt == MAX_REQUEST_ATTEMPTS - 1) {
                    error("Jikan HTTP $code")
                }

                val serverDelayMs = retryAfterSeconds?.times(1_000L)
                Thread.sleep(serverDelayMs ?: retryDelayMs(attempt))
            } catch (t: Throwable) {
                lastError = t
                if (attempt < MAX_REQUEST_ATTEMPTS - 1) Thread.sleep(retryDelayMs(attempt))
            }
        }
        throw lastError ?: IllegalStateException("Jikan request failed")
    }

    private fun retryDelayMs(attempt: Int): Long = when (attempt) {
        0 -> 900L
        1 -> 1_800L
        else -> 3_000L
    }
'''

if text.count(old) != 1:
    raise SystemExit(f'expected one Jikan retry block, found {text.count(old)}')
text = text.replace(old, new, 1)

old_constants = '''        private const val BASE_URL = "https://api.jikan.moe/v4"
        private const val MIN_REQUEST_GAP_MS = 380L
'''
new_constants = '''        private const val BASE_URL = "https://api.jikan.moe/v4"
        private const val MIN_REQUEST_GAP_MS = 380L
        private const val MAX_REQUEST_ATTEMPTS = 3
'''
if text.count(old_constants) != 1:
    raise SystemExit('Jikan constants marker mismatch')
text = text.replace(old_constants, new_constants, 1)

path.write_text(text)
print('Hardened Jikan retry/backoff for 429, transient 4xx, and 5xx responses.')
