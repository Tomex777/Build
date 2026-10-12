package com.tomex777.annie

import java.net.URI

/** Bundles only project-local CSS and classic JS into Canvas's existing offline sandbox. */
internal object ScriptWebPreview {
    private val tags = Regex("""<!--.*?-->|<script\b(?:[^\"'>]|\"[^\"]*\"|'[^']*')*>.*?</script\s*>|<link\b(?:[^\"'>]|\"[^\"]*\"|'[^']*')*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val attributes = Regex("""([\w-]+)\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s>]+))""")

    fun entry(files: Map<String, String>, selected: String): String? {
        if (selected.endsWith(".html", true)) return selected
        val folder = selected.substringBeforeLast('/', "")
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        return listOf(prefix + "index.html", prefix + "main.html")
            .firstOrNull { it in files }
    }

    fun bundle(files: Map<String, String>, entry: String): String {
        val html = files[entry] ?: error("Preview HTML file is missing: $entry")
        val deferred = mutableListOf<String>()
        val bundled = tags.replace(html) { match ->
            val tag = match.value
            if (tag.startsWith("<!--")) return@replace tag
            val opening = Regex("""^<\w+\b(?:[^"'>]|"[^"]*"|'[^']*')*>""").find(tag)?.value.orEmpty()
            val attrs = attributes.findAll(opening).associate { attribute ->
                attribute.groupValues[1].lowercase() to attribute.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
            }
            val script = tag.startsWith("<script", true)
            val reference = if (script) attrs["src"] else attrs["href"]
            if (reference == null || (!script && "stylesheet" !in attrs["rel"].orEmpty().lowercase().split(Regex("\\s+")))) {
                return@replace tag
            }
            if (script) require(attrs["type"].orEmpty().lowercase() != "module") {
                "Offline project preview currently supports classic JavaScript, not module imports"
            }
            val path = resolve(entry, reference)
            require(path.endsWith(if (script) ".js" else ".css", true)) { "Unsupported preview dependency: $path" }
            val source = files[path] ?: error("Preview dependency is missing: $path")
            val replacement = if (script) "<script>" + source.replace(Regex("</script", RegexOption.IGNORE_CASE)) { "<\\/script" } + "</script>"
            else "<style>" + source.replace(Regex("</style", RegexOption.IGNORE_CASE)) { "<\\/style" } + "</style>"
            if (script && Regex("""\sdefer(?:\s|>|/)""", RegexOption.IGNORE_CASE).containsMatchIn(opening)) {
                deferred += replacement
                ""
            } else replacement
        }
        return bundled + deferred.joinToString("")
    }

    private fun resolve(entry: String, reference: String): String {
        val uri = URI(reference.trim())
        require(uri.scheme == null && uri.rawAuthority == null && !uri.path.orEmpty().startsWith('/') && '\\' !in reference) {
            "Preview dependencies must be relative files in this project"
        }
        val parts = entry.substringBeforeLast('/', "").split('/').filter(String::isNotEmpty).toMutableList()
        for (part in uri.path.orEmpty().split('/')) when (part) {
            "", "." -> Unit
            ".." -> { require(parts.isNotEmpty()) { "Preview dependency leaves the project" }; parts.removeAt(parts.lastIndex) }
            else -> { require('\\' !in part) { "Invalid preview dependency" }; parts += part }
        }
        return parts.joinToString("/")
    }
}
