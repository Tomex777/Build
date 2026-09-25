package app.nami.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Application-scoped cache for installed sources.
 *
 * Package scans and extension class loading are expensive, so ordinary search/library reads share
 * one immutable snapshot. [invalidate] makes the next read refresh immediately. A generation
 * counter prevents an in-flight scan from re-caching stale data after a package-change event.
 */
class CachingNamiSourceRegistry(
    private val delegate: NamiSourceRegistry,
    private val ttlMillis: Long = 60_000,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : NamiSourceRegistry {

    private data class Snapshot(
        val sources: List<app.nami.source.NamiAnimeSource>,
        val loadedAtMillis: Long,
        val generation: Long,
    )

    private val mutex = Mutex()
    private val generation = AtomicLong(0)

    @Volatile
    private var snapshot: Snapshot? = null

    override suspend fun installedSources(): List<app.nami.source.NamiAnimeSource> {
        val currentGeneration = generation.get()
        snapshot
            ?.takeIf { it.generation == currentGeneration && !isExpired(it) }
            ?.let { return it.sources }

        return mutex.withLock {
            val lockedGeneration = generation.get()
            snapshot
                ?.takeIf { it.generation == lockedGeneration && !isExpired(it) }
                ?.let { return@withLock it.sources }

            val loaded = delegate.installedSources().toList()
            val completedGeneration = generation.get()

            if (completedGeneration == lockedGeneration) {
                snapshot = Snapshot(
                    sources = loaded,
                    loadedAtMillis = clockMillis(),
                    generation = completedGeneration,
                )
            }

            loaded
        }
    }

    fun invalidate() {
        generation.incrementAndGet()
        snapshot = null
    }

    private fun isExpired(snapshot: Snapshot): Boolean {
        if (ttlMillis <= 0) return true
        return clockMillis() - snapshot.loadedAtMillis >= ttlMillis
    }
}
