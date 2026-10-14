package studio.artistscene.core

/** Stable across renderer reloads; preserves existing IDs for unambiguous rigs. */
internal fun uniqueRigIds(paths: List<String>): List<String> {
    val reserved = paths.toSet()
    val used = mutableSetOf<String>()
    return paths.map { path ->
        if (used.add(path)) path else {
            var suffix = 2
            var candidate = "$path~$suffix"
            while (candidate in reserved || candidate in used) {
                suffix++
                candidate = "$path~$suffix"
            }
            used.add(candidate)
            candidate
        }
    }
}
