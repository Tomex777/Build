package app.nami.compat.aniyomi

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import java.util.concurrent.ConcurrentHashMap

interface AniyomiConfigurableSourceHandle {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}

interface AniyomiBrowserSourceHandle {
    fun browserHeaders(url: String): Map<String, String>
}

internal object AniyomiBrowserSessionRegistry {
    private val providers = ConcurrentHashMap<Long, () -> Map<String, String>>()

    fun register(source: AnimeSource) {
        val http = source as? AnimeHttpSource ?: return
        providers[source.id] = {
            http.headers.names().associateWith { name -> http.headers[name].orEmpty() }
        }
    }

    fun headers(sourceId: Long?): Map<String, String> =
        sourceId?.let { providers[it]?.invoke() }.orEmpty()
}
