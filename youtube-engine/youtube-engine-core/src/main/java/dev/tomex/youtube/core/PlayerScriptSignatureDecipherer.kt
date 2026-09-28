package dev.tomex.youtube.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.LinkedHashSet

sealed interface SignatureOperation {
    data object Reverse : SignatureOperation
    data class Drop(val count: Int) : SignatureOperation
    data class Swap(val index: Int) : SignatureOperation
}

data class SignatureTransformPlan(val operations: List<SignatureOperation>) {
    init {
        require(operations.isNotEmpty() && operations.size <= 128)
    }

    fun apply(input: String): String? {
        if (input.isBlank() || input.length > 8192) return null
        val chars = input.toMutableList()
        for (operation in operations) {
            when (operation) {
                SignatureOperation.Reverse -> chars.reverse()
                is SignatureOperation.Drop -> {
                    if (operation.count < 0 || operation.count > 4096) return null
                    val count = minOf(operation.count, chars.size)
                    if (count > 0) chars.subList(0, count).clear()
                }
                is SignatureOperation.Swap -> {
                    if (operation.index < 0 || operation.index > 1_000_000 || chars.isEmpty()) return null
                    val index = operation.index % chars.size
                    val first = chars[0]
                    chars[0] = chars[index]
                    chars[index] = first
                }
            }
        }
        return chars.joinToString("").takeIf { it.isNotBlank() }
    }
}

interface PlayerScriptSource {
    suspend fun load(playerJavaScriptUrl: String): String?
}

/**
 * Bounded in-memory cache for immutable, versioned player-script URLs.
 *
 * Successful loads are keyed by the normalized player URL. Failures are deliberately not cached so
 * transient network errors can recover without waiting for a new player revision.
 */
class CachedPlayerScriptSource(
    private val delegate: PlayerScriptSource = HttpPlayerScriptSource(),
    private val maxEntries: Int = 1
) : PlayerScriptSource {
    init { require(maxEntries in 1..4) }

    private val lock = Any()
    private val cache = object : LinkedHashMap<String, String>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxEntries
    }

    override suspend fun load(playerJavaScriptUrl: String): String? {
        val normalized = PlayerUrlTransforms.normalizePlayerJavaScriptUrl(playerJavaScriptUrl) ?: return null
        if (normalized != playerJavaScriptUrl) return null
        synchronized(lock) { cache[normalized]?.let { return it } }
        val loaded = delegate.load(normalized)
            ?.takeIf { it.isNotBlank() && it.length <= 8 * 1024 * 1024 }
            ?: return null
        synchronized(lock) { cache[normalized] = loaded }
        return loaded
    }
}

/**
 * Downloads only the normalized YouTube player script. The script is treated as data and is
 * never evaluated. Redirects stay on HTTPS www.youtube.com and both size and hop count are bounded.
 */
class HttpPlayerScriptSource(
    private val maxBytes: Int = 8 * 1024 * 1024,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000
) : PlayerScriptSource {
    init {
        require(maxBytes in 64 * 1024..16 * 1024 * 1024)
        require(connectTimeoutMs in 1_000..60_000)
        require(readTimeoutMs in 1_000..60_000)
    }

    override suspend fun load(playerJavaScriptUrl: String): String? {
        val normalized = PlayerUrlTransforms.normalizePlayerJavaScriptUrl(playerJavaScriptUrl) ?: return null
        if (normalized != playerJavaScriptUrl) return null
        return withContext(Dispatchers.IO) {
            var currentUrl = normalized
            var connection: HttpURLConnection? = null
            var redirects = 0
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val parsed = runCatching { URL(currentUrl) }.getOrNull() ?: return@withContext null
                    if (!trustedPlayerUrl(parsed)) return@withContext null
                    val candidate = (parsed.openConnection() as HttpURLConnection).apply {
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                        setRequestProperty("Accept", "application/javascript,text/javascript,*/*;q=0.1")
                    }
                    connection = candidate
                    val status = candidate.responseCode
                    if (MediaRedirectPolicy.isRedirect(status)) {
                        val next = MediaRedirectPolicy.nextUrl(currentUrl, candidate.getHeaderField("Location"))
                            ?: return@withContext null
                        candidate.disconnect()
                        connection = null
                        redirects++
                        if (redirects > 3) return@withContext null
                        currentUrl = next
                        continue
                    }
                    if (status !in 200..299) return@withContext null
                    val contentType = candidate.contentType.orEmpty()
                    if (contentType.contains("html", ignoreCase = true)) return@withContext null
                    val declaredLength = candidate.contentLengthLong
                    if (declaredLength > maxBytes) return@withContext null
                    val output = ByteArrayOutputStream(
                        if (declaredLength in 1..maxBytes.toLong()) declaredLength.toInt() else 64 * 1024
                    )
                    candidate.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (output.size() + read > maxBytes) return@withContext null
                            output.write(buffer, 0, read)
                        }
                    }
                    return@withContext output.toByteArray().toString(Charsets.UTF_8)
                }
                @Suppress("UNREACHABLE_CODE")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun trustedPlayerUrl(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) &&
            url.host.equals("www.youtube.com", ignoreCase = true) &&
            url.userInfo == null
}

/**
 * Parses a deliberately tiny subset of player JavaScript into reverse/drop/swap operations.
 * Unknown or ambiguous script shapes return null instead of executing JavaScript or guessing.
 */
object PlayerScriptSignatureParser {
    private val identifier = "[A-Za-z_" + '$' + "][A-Za-z0-9_" + '$' + "]*"
    private val assignedFunction = Regex("""($identifier)\s*=\s*function\s*\(\s*($identifier)\s*\)\s*\{""")
    private val declaredFunction = Regex("""function\s+($identifier)\s*\(\s*($identifier)\s*\)\s*\{""")
    private val helperMethod = Regex(
        """(?:["']?($identifier)["']?)\s*:\s*function\s*\(\s*($identifier)(?:\s*,\s*($identifier))?\s*\)\s*\{"""
    )

    private data class Candidate(val openBrace: Int, val argument: String)
    private enum class HelperKind { REVERSE, DROP, SWAP }

    fun parse(script: String): SignatureTransformPlan? {
        if (script.isBlank() || script.length > 8 * 1024 * 1024) return null
        val candidates = buildList {
            assignedFunction.findAll(script).forEach { add(Candidate(it.range.last, it.groupValues[2])) }
            declaredFunction.findAll(script).forEach { add(Candidate(it.range.last, it.groupValues[2])) }
        }.distinct()
        val plans = mutableListOf<SignatureTransformPlan>()
        for (candidate in candidates.take(512)) {
            val body = extractBlock(script, candidate.openBrace, 64 * 1024) ?: continue
            parseFunctionBody(script, body, candidate.argument)?.let(plans::add)
        }
        return plans.distinct().singleOrNull()
    }

    private fun parseFunctionBody(script: String, body: String, argument: String): SignatureTransformPlan? {
        val arg = Regex.escape(argument)
        val split = Regex("""^$arg\s*=\s*$arg\.split\(\s*(?:""|'')\s*\)$""")
        val join = Regex("""^return\s+$arg\.join\(\s*(?:""|'')\s*\)$""")
        val directReverse = Regex("""^$arg\.reverse\(\s*\)$""")
        val directDrop = Regex("""^$arg\.splice\(\s*0\s*,\s*(\d{1,6})\s*\)$""")
        val dotCall = Regex(
            """^(?:$arg\s*=\s*)?($identifier)\.($identifier)\(\s*$arg(?:\s*,\s*(\d{1,6}))?\s*\)$"""
        )
        val bracketCall = Regex(
            """^(?:$arg\s*=\s*)?($identifier)\[\s*["']([^"']{1,64})["']\s*]\(\s*$arg(?:\s*,\s*(\d{1,6}))?\s*\)$"""
        )
        val helperCache = mutableMapOf<String, Map<String, HelperKind>>()
        val operations = mutableListOf<SignatureOperation>()
        var sawSplit = false
        var sawJoin = false
        for (expression in splitTopLevel(body)) {
            val value = expression.trim()
            if (value.isEmpty()) continue
            when {
                split.matches(value) -> {
                    if (sawSplit) return null
                    sawSplit = true
                }
                join.matches(value) -> sawJoin = true
                directReverse.matches(value) -> operations += SignatureOperation.Reverse
                directDrop.matches(value) -> {
                    val count = directDrop.matchEntire(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                    if (count > 4096) return null
                    operations += SignatureOperation.Drop(count)
                }
                else -> {
                    val call = dotCall.matchEntire(value) ?: bracketCall.matchEntire(value) ?: return null
                    val helperName = call.groupValues[1]
                    val methodName = call.groupValues[2]
                    val numeric = call.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                    val methods = helperCache.getOrPut(helperName) {
                        parseHelperObject(script, helperName) ?: emptyMap()
                    }
                    val kind = methods[methodName] ?: return null
                    when (kind) {
                        HelperKind.REVERSE -> operations += SignatureOperation.Reverse
                        HelperKind.DROP -> {
                            val count = numeric ?: return null
                            if (count > 4096) return null
                            operations += SignatureOperation.Drop(count)
                        }
                        HelperKind.SWAP -> {
                            val index = numeric ?: return null
                            if (index > 1_000_000) return null
                            operations += SignatureOperation.Swap(index)
                        }
                    }
                }
            }
            if (operations.size > 128) return null
        }
        if (!sawSplit || !sawJoin || operations.isEmpty()) return null
        return SignatureTransformPlan(operations.toList())
    }

    private fun parseHelperObject(script: String, helperName: String): Map<String, HelperKind>? {
        val objectStart = Regex(
            """(?:(?:var|let|const)\s+)?${Regex.escape(helperName)}\s*=\s*\{"""
        ).find(script) ?: return null
        val body = extractBlock(script, objectStart.range.last, 64 * 1024) ?: return null
        val output = linkedMapOf<String, HelperKind>()
        for (match in helperMethod.findAll(body).take(64)) {
            val methodName = match.groupValues[1]
            val first = match.groupValues[2]
            val second = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }
            val methodBody = extractBlock(body, match.range.last, 8 * 1024) ?: continue
            classifyHelper(methodBody, first, second)?.let { output[methodName] = it }
        }
        return output.takeIf { it.isNotEmpty() }
    }

    private fun classifyHelper(body: String, first: String, second: String?): HelperKind? {
        val compact = body.replace(Regex("""\s+"""), "")
        if (compact.contains("$first.reverse()")) return HelperKind.REVERSE
        if (second != null && compact.contains("$first.splice(0,$second)")) return HelperKind.DROP
        if (second != null) {
            val indexed = "$first[$second%$first.length]"
            if (compact.contains("$first[0]") && compact.contains(indexed)) return HelperKind.SWAP
        }
        return null
    }

    private fun splitTopLevel(body: String): List<String> {
        val output = mutableListOf<String>()
        var start = 0
        var parentheses = 0
        var brackets = 0
        var braces = 0
        var quote: Char? = null
        var escaped = false
        for (index in body.indices) {
            val value = body[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (value == '\\') escaped = true
                else if (value == quote) quote = null
                continue
            }
            if (value == '\'' || value == '"' || value == '`') {
                quote = value
                continue
            }
            when (value) {
                '(' -> parentheses++
                ')' -> parentheses--
                '[' -> brackets++
                ']' -> brackets--
                '{' -> braces++
                '}' -> braces--
                ';', ',' -> if (parentheses == 0 && brackets == 0 && braces == 0) {
                    output += body.substring(start, index)
                    start = index + 1
                }
            }
            if (parentheses < 0 || brackets < 0 || braces < 0) return emptyList()
        }
        if (quote != null || parentheses != 0 || brackets != 0 || braces != 0) return emptyList()
        if (start <= body.length) output += body.substring(start)
        return output
    }

    private fun extractBlock(source: String, openBrace: Int, maxChars: Int): String? {
        if (openBrace !in source.indices || source[openBrace] != '{') return null
        var depth = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        var index = openBrace
        val limit = minOf(source.length, openBrace + maxChars + 1)
        while (index < limit) {
            val value = source[index]
            val next = source.getOrNull(index + 1)
            if (lineComment) {
                if (value == '\n' || value == '\r') lineComment = false
                index++
                continue
            }
            if (blockComment) {
                if (value == '*' && next == '/') {
                    blockComment = false
                    index += 2
                } else index++
                continue
            }
            if (quote != null) {
                if (escaped) escaped = false
                else if (value == '\\') escaped = true
                else if (value == quote) quote = null
                index++
                continue
            }
            if (value == '/' && next == '/') {
                lineComment = true
                index += 2
                continue
            }
            if (value == '/' && next == '*') {
                blockComment = true
                index += 2
                continue
            }
            if (value == '\'' || value == '"' || value == '`') {
                quote = value
                index++
                continue
            }
            if (value == '{') depth++
            if (value == '}') {
                depth--
                if (depth == 0) return source.substring(openBrace + 1, index)
                if (depth < 0) return null
            }
            index++
        }
        return null
    }
}

/**
 * Bounded production decipherer backed by the player-script parser.
 * Unknown or ambiguous player shapes fail closed; successful deciphering is still unverified until
 * the resulting media URL returns real CDN bytes.
 */
class PlayerScriptSignatureDecipherer(
    private val source: PlayerScriptSource = HttpPlayerScriptSource(),
    private val maxPlans: Int = 8
) : SignatureCipherDecipherer {
    init { require(maxPlans in 1..64) }

    private val lock = Any()
    private val plans = object : LinkedHashMap<String, SignatureTransformPlan>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SignatureTransformPlan>?): Boolean =
            size > maxPlans
    }
    private val failed = LinkedHashSet<String>()

    override suspend fun decipher(playerJavaScriptUrl: String, encryptedSignature: String): String? {
        if (encryptedSignature.isBlank() || encryptedSignature.length > 8192) return null
        val normalized = PlayerUrlTransforms.normalizePlayerJavaScriptUrl(playerJavaScriptUrl) ?: return null
        if (normalized != playerJavaScriptUrl) return null
        synchronized(lock) {
            plans[normalized]?.let { return applyPlan(it, encryptedSignature) }
            if (failed.contains(normalized)) return null
        }
        val script = source.load(normalized)
        val plan = script?.let(PlayerScriptSignatureParser::parse)
        if (plan == null) {
            synchronized(lock) {
                failed += normalized
                while (failed.size > maxPlans) failed.remove(failed.first())
            }
            return null
        }
        synchronized(lock) {
            failed.remove(normalized)
            plans[normalized] = plan
        }
        return applyPlan(plan, encryptedSignature)
    }

    private fun applyPlan(plan: SignatureTransformPlan, input: String): String? =
        plan.apply(input)?.takeIf { it != input }
}
