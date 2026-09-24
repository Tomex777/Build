package app.nami.runtime

import app.nami.domain.AnimeSearchResult
import app.nami.source.NamiAnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

fun interface NamiSourceRegistry {
    suspend fun installedSources(): List<NamiAnimeSource>
}

sealed interface AnimeSearchItemResult {
    data object Loading : AnimeSearchItemResult

    data class Success(val result: List<AnimeSearchResult>) : AnimeSearchItemResult {
        val isEmpty: Boolean get() = result.isEmpty()
    }

    data class Error(val throwable: Throwable) : AnimeSearchItemResult
}

data class GlobalSearchSection(
    val source: NamiAnimeSource,
    val result: AnimeSearchItemResult,
)

data class SearchFailure(
    val sourceId: String,
    val sourceName: String,
    val cause: Throwable,
)

data class GlobalSearchState(
    val sections: List<GlobalSearchSection> = emptyList(),
) {
    val progress: Int get() = sections.count { it.result !is AnimeSearchItemResult.Loading }
    val total: Int get() = sections.size
}

data class GlobalSearchResult(
    val resultsBySource: Map<String, List<AnimeSearchResult>>,
    val failures: List<SearchFailure>,
)

/**
 * Aniyomi-style global search.
 *
 * Sources begin in stable alphabetical order. As soon as a source returns a non-empty result it
 * moves above loading/empty/error sections. This is intentionally incremental: a fast "Z" source
 * can appear above a still-loading "A" source, matching the reference behavior.
 */
class GlobalAnimeSearch(private val registry: NamiSourceRegistry) {

    fun searchFlow(query: String): Flow<GlobalSearchState> = channelFlow {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            send(GlobalSearchState())
            return@channelFlow
        }

        val sources = registry.installedSources()
        require(sources.map { it.metadata.id }.distinct().size == sources.size) {
            "Nami source IDs must be unique"
        }

        val items = ConcurrentHashMap<String, GlobalSearchSection>()
        sources.forEach { source ->
            items[source.metadata.id] = GlobalSearchSection(source, AnimeSearchItemResult.Loading)
        }

        suspend fun emitSnapshot() {
            send(GlobalSearchState(sortSections(items.values)))
        }

        emitSnapshot()

        supervisorScope {
            sources.forEach { source ->
                launch {
                    val result = runCatching { source.search(normalizedQuery).items }
                        .fold(
                            onSuccess = { AnimeSearchItemResult.Success(it) },
                            onFailure = { AnimeSearchItemResult.Error(it) },
                        )
                    items[source.metadata.id] = GlobalSearchSection(source, result)
                    emitSnapshot()
                }
            }
        }
    }

    suspend fun search(query: String): GlobalSearchResult {
        val finalState = searchFlow(query).last()
        return GlobalSearchResult(
            resultsBySource = finalState.sections.mapNotNull { section ->
                val success = section.result as? AnimeSearchItemResult.Success ?: return@mapNotNull null
                section.source.metadata.id to success.result
            }.toMap(),
            failures = finalState.sections.mapNotNull { section ->
                val error = section.result as? AnimeSearchItemResult.Error ?: return@mapNotNull null
                SearchFailure(section.source.metadata.id, section.source.metadata.name, error.throwable)
            },
        )
    }

    private fun sortSections(sections: Collection<GlobalSearchSection>): List<GlobalSearchSection> {
        return sections.sortedWith(
            compareBy<GlobalSearchSection>(
                { (it.result as? AnimeSearchItemResult.Success)?.isEmpty ?: true },
                { it.source.metadata.name.lowercase() + " (" + it.source.metadata.language.orEmpty() + ")" },
            ),
        )
    }
}
