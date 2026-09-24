package app.nami.runtime

import app.nami.domain.AnimeSearchResult
import app.nami.source.NamiAnimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    val completedOrder: Long? = null,
    val elapsedMillis: Long? = null,
)

data class SearchFailure(
    val sourceId: String,
    val sourceName: String,
    val cause: Throwable,
    val stage: String = "search",
    val elapsedMillis: Long? = null,
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
    val responseOrder: List<String> = resultsBySource.keys.toList(),
)

/**
 * Aniyomi-style global search over the Nami source contract.
 *
 * Initial sections appear alphabetically. Non-empty results then rise in the order each
 * source finishes, while a slow or failed source remains isolated to its own section.
 */
class GlobalAnimeSearch(private val registry: NamiSourceRegistry) {

    fun searchFlow(query: String, timeoutMillis: Long = 30_000): Flow<GlobalSearchState> = channelFlow {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            send(GlobalSearchState())
            return@channelFlow
        }

        val sources = registry.installedSources()
            .filter { it.metadata.capabilities.searchable }
            .sortedBy { it.metadata.name.lowercase() }
        require(sources.map { it.metadata.id }.distinct().size == sources.size) {
            "Nami source IDs must be unique"
        }

        val items = ConcurrentHashMap<String, GlobalSearchSection>()
        sources.forEach { source ->
            items[source.metadata.id] = GlobalSearchSection(source, AnimeSearchItemResult.Loading)
        }

        val completionOrder = AtomicLong()
        suspend fun emitSnapshot() {
            send(GlobalSearchState(sortSections(items.values)))
        }

        emitSnapshot()

        supervisorScope {
            sources.forEach { source ->
                launch {
                    val startedAt = System.nanoTime()
                    val terminalResult = try {
                        val page = withTimeout(timeoutMillis) { source.search(normalizedQuery, page = 1) }
                        AnimeSearchItemResult.Success(
                            page.items.map { item ->
                                item.copy(ref = item.ref.copy(sourceId = source.metadata.id))
                            },
                        )
                    } catch (cancelled: CancellationException) {
                        if (cancelled !is TimeoutCancellationException) throw cancelled
                        AnimeSearchItemResult.Error(TimeoutException("Source search timed out"))
                    } catch (failure: Throwable) {
                        AnimeSearchItemResult.Error(failure)
                    }

                    items[source.metadata.id] = GlobalSearchSection(
                        source = source,
                        result = terminalResult,
                        completedOrder = completionOrder.getAndIncrement(),
                        elapsedMillis = elapsedMillis(startedAt),
                    )
                    emitSnapshot()
                }
            }
        }
    }

    suspend fun search(query: String, timeoutMillis: Long = 30_000): GlobalSearchResult {
        val finalState = searchFlow(query, timeoutMillis).last()
        val successfulSections = finalState.sections.filter {
            it.result is AnimeSearchItemResult.Success && !it.result.isEmpty
        }
        return GlobalSearchResult(
            resultsBySource = finalState.sections.mapNotNull { section ->
                val success = section.result as? AnimeSearchItemResult.Success ?: return@mapNotNull null
                section.source.metadata.id to success.result
            }.toMap(),
            failures = finalState.sections.mapNotNull { section ->
                val error = section.result as? AnimeSearchItemResult.Error ?: return@mapNotNull null
                SearchFailure(
                    section.source.metadata.id,
                    section.source.metadata.name,
                    error.throwable,
                    elapsedMillis = section.elapsedMillis,
                )
            },
            responseOrder = successfulSections.map { it.source.metadata.id },
        )
    }

    private fun sortSections(sections: Collection<GlobalSearchSection>): List<GlobalSearchSection> =
        sections.sortedWith(
            compareBy<GlobalSearchSection>(
                { if ((it.result as? AnimeSearchItemResult.Success)?.isEmpty == false) 0 else 1 },
                { it.completedOrder ?: Long.MAX_VALUE },
                { it.source.metadata.name.lowercase() + " (" + it.source.metadata.language.orEmpty() + ")" },
            ),
        )

    private fun elapsedMillis(startedAtNanos: Long): Long =
        ((System.nanoTime() - startedAtNanos) / 1_000_000).coerceAtLeast(0)
}

private class TimeoutException(message: String) : Exception(message)
