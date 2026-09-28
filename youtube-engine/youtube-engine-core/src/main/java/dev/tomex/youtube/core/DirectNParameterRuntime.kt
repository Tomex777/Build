package dev.tomex.youtube.core

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class DirectNParameterProgram(
    val functionName: String,
    val source: String
)

interface NParameterFunctionRuntime {
    suspend fun transform(program: DirectNParameterProgram, input: String): String?
}

/**
 * Executes only one extracted n-transform function, never the complete remote player bundle.
 */
class QuickJsNParameterFunctionRuntime(
    private val memoryLimitBytes: Long = 16L * 1024 * 1024,
    private val maxStackBytes: Long = 512L * 1024,
    private val evaluationTimeoutMs: Long = 600,
    private val wallTimeoutMs: Long = 1_200
) : NParameterFunctionRuntime {
    init {
        require(memoryLimitBytes in 8L * 1024 * 1024..64L * 1024 * 1024)
        require(maxStackBytes in 256L * 1024..2L * 1024 * 1024)
        require(evaluationTimeoutMs in 100..5_000)
        require(wallTimeoutMs in evaluationTimeoutMs..8_000)
    }

    override suspend fun transform(program: DirectNParameterProgram, input: String): String? {
        if (input.isBlank() || input.length > 4096) return null
        if (!IDENTIFIER.matches(program.functionName)) return null
        if (program.source.isBlank() || program.source.length > 128 * 1024) return null
        val invocation = buildString {
            append("var ").append(program.functionName).append(";\n")
            append(program.source).append("\n")
            append(program.functionName)
                .append("(")
                .append(JSONObject.quote(input))
                .append(")")
        }
        return withContext(Dispatchers.Default) {
            val quickJs = try {
                QuickJs.create(Dispatchers.Default)
            } catch (_: Exception) {
                return@withContext null
            }
            try {
                quickJs.memoryLimit = memoryLimitBytes
                quickJs.maxStackSize = maxStackBytes
                quickJs.evaluationTimeoutMillis = evaluationTimeoutMs
                withTimeout(wallTimeoutMs) {
                    quickJs.evaluate<String?>(invocation, filename = "youtube-n-transform.js")
                }?.takeIf { it.isNotBlank() && it.length <= 4096 && it != input }
            } catch (_: TimeoutCancellationException) {
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } finally {
                quickJs.close()
            }
        }
    }

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z_" + '$' + "][A-Za-z0-9_" + '$' + "]*")
    }
}

/**
 * Extracts exactly one named function from player JavaScript. No dependencies or surrounding
 * player bootstrap code are copied. Unknown shapes fail closed.
 */
object PlayerScriptDirectNParameterExtractor {
    private val identifier = "[A-Za-z_" + '$' + "][A-Za-z0-9_" + '$' + "]*"

    fun extract(script: String, functionName: String): DirectNParameterProgram? {
        if (script.isBlank() || script.length > 8 * 1024 * 1024) return null
        if (!Regex(identifier).matches(functionName)) return null
        val escaped = Regex.escape(functionName)
        val assigned = Regex(
            """(?<![A-Za-z0-9_$])$escaped\s*=\s*function\s*\(([^)]{0,512})\)\s*\{"""
        ).find(script)
        val declared = Regex(
            """\bfunction\s+$escaped\s*\(([^)]{0,512})\)\s*\{"""
        ).find(script)
        val match = listOfNotNull(assigned, declared).minByOrNull { it.range.first } ?: return null
        val firstArgument = match.groupValues[1]
            .split(',')
            .firstOrNull()
            ?.trim()
            ?.takeIf { Regex(identifier).matches(it) }
            ?: return null
        val openBrace = match.range.last
        val closeBrace = findMatchingBrace(script, openBrace, 128 * 1024) ?: return null
        var source = script.substring(match.range.first, closeBrace + 1)
        if (assigned === match) source += ";"
        source = removeKnownEarlyReturn(source, firstArgument)
        if (source.length > 128 * 1024) return null
        return DirectNParameterProgram(functionName, source)
    }

    private fun removeKnownEarlyReturn(source: String, firstArgument: String): String {
        val guard = Regex(
            """;?\s*if\s*\(\s*typeof\s+$identifier\s*===?\s*(["'])undefined\1\s*\)\s*return\s+${Regex.escape(firstArgument)}\s*;"""
        )
        return guard.replaceFirst(source, ";")
    }

    private fun findMatchingBrace(source: String, openBrace: Int, maxChars: Int): Int? {
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
                if (depth == 0) return index
                if (depth < 0) return null
            }
            index++
        }
        return null
    }
}
