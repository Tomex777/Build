package com.tomex777.annie

/**
 * Shared compatibility/version and identity validators. The manifest archive routes import
 * validation here; the operation registry uses the resulting dialect mode.
 */
internal enum class ValidationMode { LEGACY, STRICT }

internal class VersionRange private constructor(private val comparators: List<Pair<String, List<Int>>>) {
    fun matches(version: String): Boolean {
        val actual = parseVersion(version) ?: return false
        return comparators.all { (operator, target) ->
            val order = compare(actual, target)
            when (operator) {
                ">=" -> order >= 0
                ">" -> order > 0
                "<=" -> order <= 0
                "<" -> order < 0
                else -> order == 0
            }
        }
    }

    companion object {
        private val PART = Regex("^(>=|<=|>|<|=)?(\\d+(?:\\.\\d+){0,2})$")

        /** Space-separated comparators, all of which must hold, e.g. ">=1 <3". Null when malformed. */
        fun parse(text: String): VersionRange? {
            val parts = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            if (parts.isEmpty() || parts.size > 4) return null
            val comparators = parts.map { part ->
                val match = PART.matchEntire(part) ?: return null
                match.groupValues[1].ifEmpty { "=" } to (parseVersion(match.groupValues[2]) ?: return null)
            }
            return VersionRange(comparators)
        }

        fun parseVersion(text: String): List<Int>? {
            val numbers = text.trim().split('.').map { it.toIntOrNull() ?: return null }
            return numbers.takeIf { it.isNotEmpty() && it.size <= 3 && it.all { n -> n >= 0 } }
        }

        private fun compare(a: List<Int>, b: List<Int>): Int {
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}

internal object ApiCompatibility {
    /** apiVersion is the contract dialect: 1 = legacy permissive validation, >= 2 = strict. Null when invalid. */
    fun modeFor(apiVersion: String): ValidationMode? {
        val number = apiVersion.trim().takeIf { it.matches(Regex("[1-9][0-9]{0,2}")) }?.toInt() ?: return null
        return if (number >= 2) ValidationMode.STRICT else ValidationMode.LEGACY
    }

    /** Returns an import error message, or null when the package is compatible with this Annie. */
    fun check(apiVersion: String, requiresAnnie: String?, currentApi: Int): String? {
        if (modeFor(apiVersion) == null) return "apiVersion must be a whole number of 1 or more"
        if (apiVersion.trim().toInt() > currentApi) {
            return "Package uses apiVersion $apiVersion but this Annie supports up to $currentApi. Update Annie or lower apiVersion."
        }
        if (requiresAnnie == null) return null
        val range = VersionRange.parse(requiresAnnie)
            ?: return "requires.annie '$requiresAnnie' is not a valid range. Use comparators like \">=1 <3\"."
        if (!range.matches(currentApi.toString())) {
            return "Package requires Annie API '$requiresAnnie' but this Annie provides API $currentApi."
        }
        return null
    }
}

internal object ManifestIdentity {
    private val PUBLISHER_ID = Regex("[a-z0-9][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)+")
    private val LABEL = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
    const val MAX_HOSTS = 32

    fun validatePublisher(id: String?, name: String?): String? {
        if (id == null || !PUBLISHER_ID.matches(id) || id.length > 128) {
            return "publisher.id must be a lower-case reverse-DNS id such as com.example"
        }
        if (name.isNullOrBlank() || name.length > 80) return "publisher.name must be 1-80 characters"
        return null
    }

    /** Allowed: "api.example.com" or "*.cdn.example.com" (leading wildcard only, at least two labels after it). */
    fun validateHost(pattern: String): String? {
        val host = pattern.removePrefix("*.")
        val labels = host.split('.')
        val wildcard = pattern.startsWith("*.")
        val ok = pattern.length <= 253 && labels.all { LABEL.matches(it) } &&
            labels.size >= (if (wildcard) 2 else 1) && !host.all { it.isDigit() || it == '.' }
        return if (ok) null else "network host '$pattern' must be a host name or *.host.name (no scheme, port, path or IP address)"
    }

    fun validateHosts(patterns: List<String>): String? {
        if (patterns.size > MAX_HOSTS) return "network.hosts allows at most $MAX_HOSTS entries"
        patterns.firstNotNullOfOrNull(::validateHost)?.let { return it }
        if (patterns.map { it.lowercase() }.toSet().size != patterns.size) return "network hosts contain duplicates"
        return null
    }

    /** A wildcard matches any depth of subdomain but never the bare domain. */
    fun hostAllowed(patterns: List<String>, host: String): Boolean {
        val target = host.lowercase().trimEnd('.')
        return patterns.any { pattern ->
            if (pattern.startsWith("*.")) target.endsWith(pattern.substring(1)) && target.length > pattern.length - 1
            else target == pattern
        }
    }
}
