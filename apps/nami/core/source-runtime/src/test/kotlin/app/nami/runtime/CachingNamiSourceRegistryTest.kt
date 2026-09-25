package app.nami.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CachingNamiSourceRegistryTest {

    @Test
    fun repeatedReadsReuseOneSnapshot() = runBlocking {
        var calls = 0
        var now = 1_000L
        val registry = CachingNamiSourceRegistry(
            delegate = NamiSourceRegistry {
                calls++
                emptyList()
            },
            ttlMillis = 60_000,
            clockMillis = { now },
        )

        registry.installedSources()
        now += 500
        registry.installedSources()
        now += 500
        registry.installedSources()

        assertEquals(1, calls)
    }

    @Test
    fun ttlExpiryRefreshesInstalledExtensions() = runBlocking {
        var calls = 0
        var now = 10_000L
        val registry = CachingNamiSourceRegistry(
            delegate = NamiSourceRegistry {
                calls++
                emptyList()
            },
            ttlMillis = 1_000,
            clockMillis = { now },
        )

        registry.installedSources()
        now += 999
        registry.installedSources()
        assertEquals(1, calls)

        now += 1
        registry.installedSources()
        assertEquals(2, calls)
    }

    @Test
    fun explicitInvalidationForcesImmediateRefresh() = runBlocking {
        var calls = 0
        val registry = CachingNamiSourceRegistry(
            delegate = NamiSourceRegistry {
                calls++
                emptyList()
            },
            ttlMillis = 60_000,
            clockMillis = { 100L },
        )

        registry.installedSources()
        registry.invalidate()
        registry.installedSources()

        assertEquals(2, calls)
    }

    @Test
    fun zeroTtlAlwaysDelegates() = runBlocking {
        var calls = 0
        val registry = CachingNamiSourceRegistry(
            delegate = NamiSourceRegistry {
                calls++
                emptyList()
            },
            ttlMillis = 0,
            clockMillis = { 100L },
        )

        registry.installedSources()
        registry.installedSources()

        assertEquals(2, calls)
    }
}
