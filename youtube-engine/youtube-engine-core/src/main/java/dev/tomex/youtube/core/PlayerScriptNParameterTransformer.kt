package dev.tomex.youtube.core

import java.util.LinkedHashMap
import java.util.LinkedHashSet

sealed interface NParameterOperation {
    data object Reverse : NParameterOperation
    data class Drop(val count: Int) : NParameterOperation
    data class Swap(val index: Int) : NParameterOperation
    data class RotateLeft(val count: Int) : NParameterOperation
    data class RotateRight(val count: Int) : NParameterOperation
}

data class NParameterTransformPlan(val operations: List<NParameterOperation>) {
    init {
        require(operations.isNotEmpty() && operations.size <= 128)
    }

    fun apply(input: String): String? {
        if (input.isBlank() || input.length > 4096) return null
        val chars = input.toMutableList()
        for (operation in operations) {
            when (operation) {
                NParameterOperation.Reverse -> chars.reverse()
                is NParameterOperation.Drop -> {
                    if (operation.count < 0 || operation.count > 4096) return null
                    val count = minOf(operation.count, chars.size)
                    if (count > 0) chars.subList(0, count).clear()
                }
                is NParameterOperation.Swap -> {
                    if (operation.index < 0 || operation.index > 1_000_000 || chars.isEmpty()) return null
                    val index = operation.index % chars.size
                    val first = chars[0]
                    chars[0] = chars[index]
                    chars[index] = first
                }
                is NParameterOperation.RotateLeft -> {
                    if (operation.count < 0 || operation.count > 4096 || chars.isEmpty()) return null
                    repeat(operation.count % chars.size) { chars.add(chars.removeAt(0)) }
                }
                is NParameterOperation.RotateRight -> {
                    if (operation.count < 0 || operation.count > 4096 || chars.isEmpty()) return null
                    repeat(operation.count % chars.size) { chars.add(0, chars.removeAt(chars.lastIndex)) }
                }
            }
        }
        return chars.joinToString("").takeIf { it.isNotBlank() }
    }
}

data class PlayerScriptUrlBuilderCandidate(
    val functionName: String,
    val urlClassName: String
)

data class NParameterParserDiagnostics(
    val candidateFunctions: Int,
    val hintedFunctions: Int,
    val markerFunctions: Int,
    val parsedPlans: Int,
    val urlConstructorFunctions: Int = 0,
    val urlClassCandidates: List<String> = emptyList(),
    val urlBuilderCandidates: List<PlayerScriptUrlBuilderCandidate> = emptyList()
)

/**
 * Bounded parser for the small array-mutation subset used by supported n transforms.
 *
 * The player JavaScript is treated only as data. A function is considered only when it is tied to
 * an n-parameter call site, or contains YouTube's n-transform exception marker. Unknown statements,
 * computed calls and ambiguous plans fail closed.
 */
object PlayerScriptNParameterParser {
    private val identifier = "[A-Za-z_" + '$' + "][A-Za-z0-9_" + '$' + "]*"
    private val assignedFunction = Regex(
        """(?:(?:var|let|const)\s+)?($identifier)\s*=\s*function\s*\(\s*($identifier)\s*\)\s*\{"""
    )
    private val declaredFunction = Regex("""function\s+($identifier)\s*\(\s*($identifier)\s*\)\s*\{""")
    private val assignedUrlConstructorFunction = Regex(
        """(?:(?:var|let|const)\s+)?($identifier)\s*=\s*function\s*\(\s*($identifier)(?:\s*,[^)]{0,512})?\)\s*\{"""
    )
    private val declaredUrlConstructorFunction = Regex(
        """function\s+($identifier)\s*\(\s*($identifier)(?:\s*,[^)]{0,512})?\)\s*\{"""
    )
    private val alrSet = Regex("""\.set\(\s*["']alr["']\s*,\s*["']yes["']\s*\)""")
    private val helperMethod = Regex(
        """(?:["']?($identifier)["']?)\s*:\s*function\s*\(\s*($identifier)(?:\s*,\s*($identifier))?\s*\)\s*\{"""
    )
    private val nSetCall = Regex(
        """\.set\(\s*["']n["']\s*,\s*($identifier)\s*\(\s*$identifier\s*\)\s*\)"""
    )
    private val nGetThenCall = Regex(
        """\.get\(\s*["']n["']\s*\)[\s\S]{0,384}?=\s*($identifier)\s*\(\s*$identifier\s*\)"""
    )

    private data class Candidate(val name: String, val openBrace: Int, val argument: String, val body: String)
    private data class UrlConstructorCandidate(
        val name: String,
        val openBrace: Int,
        val argument: String,
        val body: String
    )
    private enum class HelperKind { REVERSE, DROP, SWAP }

    fun parse(script: String): NParameterTransformPlan? = parseWithDiagnostics(script).first

    fun inspect(script: String): NParameterParserDiagnostics = parseWithDiagnostics(script).second

    private fun parseWithDiagnostics(script: String): Pair<NParameterTransformPlan?, NParameterParserDiagnostics> {
        if (script.isBlank() || script.length > 8 * 1024 * 1024) {
            return null to NParameterParserDiagnostics(0, 0, 0, 0)
        }
        val candidates = buildList {
            assignedFunction.findAll(script).take(1024).forEach { match ->
                extractBlock(script, match.range.last, 96 * 1024)?.let { body ->
                    add(Candidate(match.groupValues[1], match.range.last, match.groupValues[2], body))
                }
            }
            declaredFunction.findAll(script).take(1024).forEach { match ->
                extractBlock(script, match.range.last, 96 * 1024)?.let { body ->
                    add(Candidate(match.groupValues[1], match.range.last, match.groupValues[2], body))
                }
            }
        }.distinctBy { Triple(it.name, it.openBrace, it.argument) }

        val hintedNames = linkedSetOf<String>().apply {
            nSetCall.findAll(script).take(64).forEach { add(it.groupValues[1]) }
            nGetThenCall.findAll(script).take(64).forEach { add(it.groupValues[1]) }
        }
        val markerCandidates = candidates.filter { body ->
            "enhanced_except_" in body.body || Regex("""[A-Za-z0-9-]+_w8_""").containsMatchIn(body.body)
        }
        val selected = when {
            hintedNames.isNotEmpty() -> candidates.filter { it.name in hintedNames }
            markerCandidates.isNotEmpty() -> markerCandidates
            else -> emptyList()
        }
        val plans = selected.take(64).mapNotNull { parseFunctionBody(script, it.body, it.argument) }.distinct()
        val urlConstructors = findUrlConstructors(script)
        val urlBuilderCandidates = urlConstructors.mapNotNull { candidate ->
            val arg = Regex.escape(candidate.argument)
            Regex("""$arg\s*=\s*new\s+($identifier)\.($identifier)\(\s*$arg(?:\s*,[^)]{0,128})?\)""")
                .find(candidate.body)
                ?.let {
                    PlayerScriptUrlBuilderCandidate(
                        functionName = candidate.name,
                        urlClassName = "${it.groupValues[1]}.${it.groupValues[2]}"
                    )
                }
        }.distinct().take(16)
        val diagnostics = NParameterParserDiagnostics(
            candidateFunctions = candidates.size,
            hintedFunctions = candidates.count { it.name in hintedNames },
            markerFunctions = markerCandidates.size,
            parsedPlans = plans.size,
            urlConstructorFunctions = urlConstructors.size,
            urlClassCandidates = urlBuilderCandidates.map { it.urlClassName }.distinct().take(16),
            urlBuilderCandidates = urlBuilderCandidates
        )
        return plans.singleOrNull() to diagnostics
    }

    private fun findUrlConstructors(script: String): List<UrlConstructorCandidate> {
        val candidates = mutableListOf<UrlConstructorCandidate>()
        val patterns = listOf(assignedUrlConstructorFunction, declaredUrlConstructorFunction)
        for (pattern in patterns) {
            for (match in pattern.findAll(script).take(256)) {
                val body = extractBlock(script, match.range.last, 96 * 1024) ?: continue
                if (!alrSet.containsMatchIn(body)) continue
                val argument = match.groupValues[2]
                val arg = Regex.escape(argument)
                if (!Regex("""$arg\s*=\s*new\s+$identifier\.$identifier\(\s*$arg(?:\s*,[^)]{0,128})?\)""")
                        .containsMatchIn(body)
                ) continue
                candidates += UrlConstructorCandidate(match.groupValues[1], match.range.last, argument, body)
            }
        }
        return candidates.distinctBy { it.openBrace }.take(64)
    }

    private fun parseFunctionBody(script: String, body: String, argument: String): NParameterTransformPlan? {
        val statements = splitTopLevel(body)
        if (statements.isEmpty()) return null
        var work = argument
        var sawSplit = false
        var sawJoin = false
        val operations = mutableListOf<NParameterOperation>()
        val helperCache = mutableMapOf<String, Map<String, HelperKind>>()

        for (raw in statements) {
            val value = raw.trim()
            if (value.isEmpty() || value == "'use strict'" || value == "\"use strict\"") continue
            val split = Regex(
                """^(?:(?:var|let|const)\s+)?($identifier)\s*=\s*${Regex.escape(argument)}\.split\(\s*(?:""|'')\s*\)$"""
            ).matchEntire(value)
            if (split != null) {
                if (sawSplit) return null
                work = split.groupValues[1]
                sawSplit = true
                continue
            }
            val escapedWork = Regex.escape(work)
            if (Regex("""^return\s+$escapedWork\.join\(\s*(?:""|'')\s*\)$""").matches(value)) {
                sawJoin = true
                continue
            }
            if (!sawSplit) return null
            when {
                Regex("""^(?:$escapedWork\s*=\s*)?$escapedWork\.reverse\(\s*\)$""").matches(value) ->
                    operations += NParameterOperation.Reverse
                Regex("""^$escapedWork\.splice\(\s*0\s*,\s*(\d{1,6})\s*\)$""").matches(value) -> {
                    val count = Regex("""^$escapedWork\.splice\(\s*0\s*,\s*(\d{1,6})\s*\)$""")
                        .matchEntire(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                    if (count > 4096) return null
                    operations += NParameterOperation.Drop(count)
                }
                Regex("""^$escapedWork\s*=\s*$escapedWork\.slice\(\s*(\d{1,6})\s*\)$""").matches(value) -> {
                    val count = Regex("""^$escapedWork\s*=\s*$escapedWork\.slice\(\s*(\d{1,6})\s*\)$""")
                        .matchEntire(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                    if (count > 4096) return null
                    operations += NParameterOperation.Drop(count)
                }
                Regex("""^$escapedWork\.push\(\s*$escapedWork\.shift\(\s*\)\s*\)$""").matches(value) ->
                    operations += NParameterOperation.RotateLeft(1)
                Regex("""^$escapedWork\.unshift\(\s*$escapedWork\.pop\(\s*\)\s*\)$""").matches(value) ->
                    operations += NParameterOperation.RotateRight(1)
                else -> {
                    val dotCall = Regex(
                        """^(?:$escapedWork\s*=\s*)?($identifier)\.($identifier)\(\s*$escapedWork(?:\s*,\s*(\d{1,6}))?\s*\)$"""
                    ).matchEntire(value)
                    val bracketCall = Regex(
                        """^(?:$escapedWork\s*=\s*)?($identifier)\[\s*["']([^"']{1,64})["']\s*]\(\s*$escapedWork(?:\s*,\s*(\d{1,6}))?\s*\)$"""
                    ).matchEntire(value)
                    val call = dotCall ?: bracketCall ?: return null
                    val helperName = call.groupValues[1]
                    val methodName = call.groupValues[2]
                    val numeric = call.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                    val methods = helperCache.getOrPut(helperName) {
                        parseHelperObject(script, helperName) ?: emptyMap()
                    }
                    when (methods[methodName] ?: return null) {
                        HelperKind.REVERSE -> operations += NParameterOperation.Reverse
                        HelperKind.DROP -> {
                            val count = numeric ?: return null
                            if (count > 4096) return null
                            operations += NParameterOperation.Drop(count)
                        }
                        HelperKind.SWAP -> {
                            val index = numeric ?: return null
                            if (index > 1_000_000) return null
                            operations += NParameterOperation.Swap(index)
                        }
                    }
                }
            }
            if (operations.size > 128) return null
        }
        if (!sawSplit || !sawJoin || operations.isEmpty()) return null
        return NParameterTransformPlan(operations.toList())
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
            if (value == '\'' || value == '"' || value.code == 96) {
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
            if (value == '\'' || value == '"' || value.code == 96) {
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
 * Bounded production n transformer backed by the parser. Unknown current player shapes are cached
 * per player URL and remain N_PARAMETER_REQUIRED rather than being guessed or executed.
 */
class PlayerScriptNParameterTransformer(
    private val source: PlayerScriptSource = HttpPlayerScriptSource(),
    private val maxPlans: Int = 8
) : NParameterTransformer {
    init { require(maxPlans in 1..64) }

    private val lock = Any()
    private val plans = object : LinkedHashMap<String, NParameterTransformPlan>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NParameterTransformPlan>?): Boolean =
            size > maxPlans
    }
    private val failed = LinkedHashSet<String>()

    override suspend fun transform(playerJavaScriptUrl: String, input: String): String? {
        if (input.isBlank() || input.length > 4096) return null
        val normalized = PlayerUrlTransforms.normalizePlayerJavaScriptUrl(playerJavaScriptUrl) ?: return null
        if (normalized != playerJavaScriptUrl) return null
        synchronized(lock) {
            plans[normalized]?.let { return applyPlan(it, input) }
            if (failed.contains(normalized)) return null
        }
        val script = source.load(normalized)
        val plan = script?.let(PlayerScriptNParameterParser::parse)
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
        return applyPlan(plan, input)
    }

    private fun applyPlan(plan: NParameterTransformPlan, input: String): String? =
        plan.apply(input)?.takeIf { it != input }
}
