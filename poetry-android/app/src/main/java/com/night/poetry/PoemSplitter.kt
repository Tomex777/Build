package com.night.poetry

/**
 * Splits a poem into slide-sized passages without flattening its line breaks.
 *
 * The limit is a conservative character estimate; the card renderer still checks
 * measured text height, since different fonts and screen sizes wrap differently.
 */
internal fun splitStanzas(content: String, maxChars: Int = 230): List<String> {
    require(maxChars >= 16) { "maxChars must be at least 16" }
    val clean = content.replace("\r\n", "\n").replace('\r', '\n').trim('\n')
    if (clean.isBlank()) return emptyList()
    val paragraphs = clean.split(Regex("\n[ \t]*\n+"))
    val pieces = mutableListOf<String>()

    fun emitOversizeLine(line: String) {
        // Only split within a line when it cannot fit otherwise. Prefer a word
        // boundary; hard-split an unusually long single word as a last resort.
        var rest = line
        while (rest.length > maxChars) {
            val boundary = rest.lastIndexOf(' ', startIndex = maxChars)
                .takeIf { it > 0 } ?: maxChars
            pieces += rest.substring(0, boundary)
            rest = rest.substring(boundary).trimStart(' ')
        }
        if (rest.isNotEmpty()) pieces += rest
    }

    for (paragraph in paragraphs) {
        if (paragraph.length <= maxChars) {
            pieces += paragraph
            continue
        }
        var segment = ""
        for (line in paragraph.split('\n')) {
            // A new verse line stays a line, never collapse poetry to plain prose.
            if (line.length > maxChars) {
                if (segment.isNotEmpty()) { pieces += segment; segment = "" }
                emitOversizeLine(line)
                continue
            }
            val combined = if (segment.isEmpty()) line else "$segment\n$line"
            if (combined.length > maxChars && segment.isNotEmpty()) {
                pieces += segment
                segment = line
            } else segment = combined
        }
        if (segment.isNotEmpty()) pieces += segment
    }

    val slides = mutableListOf<String>()
    var current = ""
    for (piece in pieces) {
        val candidate = if (current.isEmpty()) piece else "$current\n\n$piece"
        if (candidate.length > maxChars && current.isNotEmpty()) {
            slides += current
            current = piece
        } else current = candidate
    }
    if (current.isNotEmpty()) slides += current
    return slides
}
