package app.nami.runtime

import app.nami.source.NamiAnimeSource

/**
 * Persistent policy for whether an installed source participates in normal Nami runtime work.
 * Sources default to enabled; callers only need to persist explicit opt-outs.
 */
interface SourceEnablementStore {
    fun isEnabled(sourceId: String): Boolean
    fun setEnabled(sourceId: String, enabled: Boolean)
}

/**
 * Runtime registry seen by global search, library resolution, and download retry.
 *
 * The delegate still owns installed-source discovery/caching. This wrapper applies user enablement
 * at read time so toggles take effect immediately without rescanning installed APKs.
 */
class EnabledNamiSourceRegistry(
    private val installedRegistry: NamiSourceRegistry,
    private val enablementStore: SourceEnablementStore,
) : NamiSourceRegistry {

    override suspend fun installedSources(): List<NamiAnimeSource> =
        installedRegistry.installedSources()
            .filter { source -> enablementStore.isEnabled(source.metadata.id) }
}
