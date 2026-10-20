package com.night.browser

import java.net.URI
import java.net.URLDecoder
import java.util.ArrayDeque
import java.util.Locale

data class JourneyRow(val node: JourneyNode, val depth: Int, val matchesSearch: Boolean)

/**
 * Pure, deterministic operations on a local Journey tree.
 * Nodes have immutable identities so the old v1 on-disk format remains readable.
 */
object JourneyGraph {
    private val privateParams = setOf(
        "access_token", "refresh_token", "id_token", "token", "key", "api_key",
        "apikey", "authorization", "auth", "code", "state", "session",
        "sessionid", "sid", "password", "passwd", "pass", "secret",
        "client_secret", "jwt", "otp", "email", "phone", "signature", "sig"
    )

    /**
     * Keep useful routes, strip fragments, embedded credentials and known secret parameters.
     * This is not a general promise that arbitrary webpages never put secrets in their paths.
     */
    fun safeUrl(input: String): String? {
        if (input.length > 8192) return null
        val parsed = runCatching { URI(input.trim()) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = parsed.host?.lowercase(Locale.ROOT) ?: return null
        if (host.isBlank()) return null
        val renderedHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        val port = if (parsed.port >= 0) ":${parsed.port}" else ""
        val path = parsed.rawPath?.ifEmpty { "/" } ?: "/"
        val cleanedQuery = parsed.rawQuery?.split('&')?.filter { pair ->
            val rawKey = pair.substringBefore('=')
            val decoded = runCatching { URLDecoder.decode(rawKey, "UTF-8") }
                .getOrDefault(rawKey).lowercase(Locale.ROOT)
            decoded !in privateParams && privateParams.none { decoded.endsWith("_$it") } &&
                !decoded.contains("secret") && !decoded.contains("token") &&
                !decoded.contains("password")
        }?.filter { it.isNotBlank() }?.joinToString("&").orEmpty()
        return buildString {
            append(scheme).append("://").append(renderedHost).append(port).append(path)
            if (cleanedQuery.isNotEmpty()) append('?').append(cleanedQuery)
        }
    }

    /** Data upgrades and rolling-cap removal cannot leave edges pointing at deleted nodes. */
    fun repair(nodes: List<JourneyNode>, maxNodes: Int = 600): List<JourneyNode> {
        val kept = nodes.takeLast(maxNodes.coerceAtLeast(0))
        val ids = kept.mapTo(HashSet()) { it.id }
        return kept.map { node ->
            if (node.parent == node.id || node.parent !in ids) node.copy(parent = null) else node
        }
    }

    /** A deletion always removes the selected subtree; survivors never become orphans. */
    fun subtreeIds(nodes: List<JourneyNode>, rootId: String): Set<String> {
        val children = nodes.groupBy { it.parent }
        val visited = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.add(rootId)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (!visited.add(id)) continue
            children[id].orEmpty().forEach { stack.add(it.id) }
        }
        return visited
    }

    /**
     * Parent-first preorder, never the flat chronological log. Search preserves the
     * ancestors of matches so the context of a matching branch is visible.
     */
    fun rows(journey: Journey, query: String = ""): List<JourneyRow> {
        val nodes = journey.nodes
        val byId = nodes.associateBy { it.id }
        val children = nodes.groupBy { it.parent }
        val visited = mutableSetOf<String>()
        val traversal = ArrayList<JourneyRow>(nodes.size)
        fun walk(root: JourneyNode) {
            val stack = ArrayDeque<Pair<JourneyNode, Int>>()
            stack.add(root to 0)
            while (stack.isNotEmpty()) {
                val (node, depth) = stack.removeLast()
                if (!visited.add(node.id)) continue
                traversal.add(JourneyRow(node, depth, true))
                children[node.id].orEmpty().asReversed().forEach {
                    stack.add(it to (depth + 1))
                }
            }
        }
        nodes.filter { it.parent == null || it.parent == it.id || it.parent !in byId }
            .forEach(::walk)
        // Recover cyclic/legacy malformed components rather than hiding nodes forever.
        nodes.filterNot { it.id in visited }.forEach(::walk)

        val needle = query.trim()
        if (needle.isEmpty()) return traversal
        val matches = nodes.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.url.contains(needle, ignoreCase = true)
        }.mapTo(HashSet()) { it.id }
        val included = matches.toMutableSet()
        matches.forEach { id ->
            var parent = byId[id]?.parent
            val chain = mutableSetOf(id)
            while (parent != null && chain.add(parent)) {
                included.add(parent)
                parent = byId[parent]?.parent
            }
        }
        return traversal.filter { it.node.id in included }
            .map { it.copy(matchesSearch = it.node.id in matches) }
    }
}
