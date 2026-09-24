package app.nami.runtime

import app.nami.source.NamiAnimeSource

/**
 * Combines independent source providers. A failure while enumerating one provider does not
 * prevent the remaining providers from contributing sources to Browse and global search.
 */
class CompositeNamiSourceRegistry(
    private val registries: List<NamiSourceRegistry>,
) : NamiSourceRegistry {
    override suspend fun installedSources(): List<NamiAnimeSource> =
        registries.flatMap { registry ->
            runCatching { registry.installedSources() }.getOrDefault(emptyList())
        }.distinctBy { it.metadata.id }

    companion object {
        operator fun invoke(vararg registries: NamiSourceRegistry) =
            CompositeNamiSourceRegistry(registries.toList())
    }
}
