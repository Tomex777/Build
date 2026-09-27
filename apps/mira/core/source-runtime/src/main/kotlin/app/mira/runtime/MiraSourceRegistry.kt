package app.mira.runtime

import app.mira.domain.ContentSearchResult
import app.mira.source.MiraSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout

fun interface MiraSourceRegistry {
    suspend fun installedSources(): List<MiraSource>
}

data class SourceSearchFailure(
    val sourceId: String,
    val sourceName: String,
    val cause: Throwable,
)

data class GlobalSearchResult(
    val resultsBySource: Map<String, List<ContentSearchResult>>,
    val failures: List<SourceSearchFailure>,
)

class GlobalMediaSearch(
    private val registry: MiraSourceRegistry,
) {
    suspend fun search(
        query: String,
        timeoutMillis: Long = 30_000L,
    ): GlobalSearchResult = supervisorScope {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            return@supervisorScope GlobalSearchResult(emptyMap(), emptyList())
        }

        val sources = registry.installedSources().filter { it.metadata.capabilities.searchable }
        require(sources.map { it.metadata.id }.distinct().size == sources.size) {
            "Mira source IDs must be unique"
        }

        val work = sources.map { source ->
            async {
                try {
                    val page = withTimeout(timeoutMillis) { source.search(normalized, 1) }
                    source.metadata.id to Result.success(
                        page.items.map { item ->
                            item.copy(ref = item.ref.copy(sourceId = source.metadata.id))
                        },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    source.metadata.id to Result.failure<List<ContentSearchResult>>(failure)
                }
            }
        }.map { it.await() }

        GlobalSearchResult(
            resultsBySource = work.mapNotNull { (sourceId, result) ->
                result.getOrNull()?.let { sourceId to it }
            }.toMap(),
            failures = work.mapNotNull { (sourceId, result) ->
                val failure = result.exceptionOrNull() ?: return@mapNotNull null
                val source = sources.first { it.metadata.id == sourceId }
                SourceSearchFailure(sourceId, source.metadata.name, failure)
            },
        )
    }
}
