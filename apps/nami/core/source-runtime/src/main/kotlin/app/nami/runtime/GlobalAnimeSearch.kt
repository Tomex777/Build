package app.nami.runtime

import app.nami.domain.AnimeSearchResult
import app.nami.source.NamiAnimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

fun interface NamiSourceRegistry {
    suspend fun installedSources(): List<NamiAnimeSource>
}

data class SearchFailure(val sourceId: String, val sourceName: String, val cause: Throwable)
data class GlobalSearchResult(
    val resultsBySource: Map<String, List<AnimeSearchResult>>,
    val failures: List<SearchFailure>,
)

/** A source failure is isolated; successful sources still contribute their real results. */
class GlobalAnimeSearch(private val registry: NamiSourceRegistry) {
    suspend fun search(query: String): GlobalSearchResult {
        val sources = registry.installedSources()
        return supervisorScope {
            val results = sources.map { source ->
                async {
                    runCatching { source.metadata.id to source.search(query).items }
                        .fold(
                            onSuccess = { it to null },
                            onFailure = { null to SearchFailure(source.metadata.id, source.metadata.name, it) },
                        )
                }
            }.map { it.await() }
            GlobalSearchResult(
                resultsBySource = results.mapNotNull { it.first }.toMap(),
                failures = results.mapNotNull { it.second },
            )
        }
    }
}
