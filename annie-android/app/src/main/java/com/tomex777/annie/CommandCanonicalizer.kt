package com.tomex777.annie

/**
 * Resolves slash-command name collisions across packages (M0b step 8). Pure so it can be unit tested.
 *
 * Rules (contract v0.2 section 6):
 *  - Never drop a command. Every colliding command survives.
 *  - The oldest installed package (ties: lowest script id) keeps /<name>.
 *  - Every newer one becomes /<package-slug>:<name>; its aliases are dropped because they would be ambiguous.
 *  - [ScriptCommand.collision] is true for all of them; [ScriptCommand.collidesWith] names the package that kept the plain name.
 *  - An alias that equals another command's final name, or an alias already claimed by an older command, is removed
 *    from the newer command and marks it as a collision.
 * Package slug: lower-case package id, every run of characters outside [a-z0-9_-] becomes "_", trimmed of "_" and "-"
 * at both ends, at most 48 characters; falls back to the script id.
 */
internal object CommandCanonicalizer {
    fun packageSlug(packageId: String?, fallback: String): String =
        packageId?.lowercase()
            ?.replace(Regex("[^a-z0-9_-]+"), "_")
            ?.trim('_', '-')
            ?.take(48)
            ?.ifBlank { fallback }
            ?: fallback

    fun canonicalize(source: List<ScriptCommand>, installedAt: (String) -> Long): List<ScriptCommand> {
        val grouped = source.groupBy { it.name.lowercase() }
        val used = mutableSetOf<String>()
        val renamed = source.map { command ->
            val peers = grouped[command.name.lowercase()].orEmpty()
            if (peers.size <= 1) {
                used += command.name.lowercase()
                command.copy(collision = false, collidesWith = null, sourceId = command.sourceId ?: command.name)
            } else {
                val oldest = peers.minWithOrNull(
                    compareBy<ScriptCommand> { installedAt(it.scriptId) }.thenBy { it.scriptId },
                )!!
                if (command.scriptId == oldest.scriptId) {
                    used += command.name.lowercase()
                    command.copy(collision = true, collidesWith = null, sourceId = command.sourceId ?: command.name)
                } else {
                    val slug = packageSlug(command.packageId, command.scriptId)
                    var canonical = slug + ":" + command.name
                    if (!used.add(canonical.lowercase())) canonical = slug + "-" + command.scriptId + ":" + command.name
                    command.copy(
                        name = canonical,
                        usage = command.usage.replaceFirst(Regex("^/[^\\s]+"), "/" + canonical),
                        aliases = emptyList(),
                        collision = true,
                        collidesWith = oldest.packageId ?: oldest.scriptId,
                        sourceId = command.sourceId ?: command.name,
                    )
                }
            }
        }
        return resolveAliasCollisions(renamed, installedAt)
    }

    private fun resolveAliasCollisions(commands: List<ScriptCommand>, installedAt: (String) -> Long): List<ScriptCommand> {
        val names = commands.mapTo(hashSetOf()) { it.name.lowercase() }
        val claimed = hashSetOf<String>()
        val filtered = hashMapOf<Pair<String, String>, List<String>>()
        commands.sortedWith(compareBy<ScriptCommand> { installedAt(it.scriptId) }.thenBy { it.scriptId }).forEach { command ->
            val own = command.name.lowercase()
            filtered[command.scriptId to command.name] = command.aliases.filter { alias ->
                val key = alias.lowercase()
                key == own || (key !in names && claimed.add(key))
            }
        }
        return commands.map { command ->
            val kept = filtered.getValue(command.scriptId to command.name)
            if (kept.size == command.aliases.size) command
            else command.copy(aliases = kept, collision = true)
        }
    }
}
