package app.nami.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.nami.compat.aniyomi.AniyomiExtensionRegistry
import app.nami.runtime.CompositeNamiSourceRegistry
import app.nami.runtime.NamiSourceRegistry
import app.nami.source.jikan.JikanAnimeSource
import app.nami.data.local.NamiDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = NamiDatabase(applicationContext)
        val sourceRegistry = CompositeNamiSourceRegistry(
            NamiSourceRegistry { AniyomiExtensionRegistry(applicationContext).installedSources() },
            NamiSourceRegistry { listOf(JikanAnimeSource()) },
        )
        val downloadManager = NamiDownloadManager(applicationContext, database)

        setContent {
            NamiApp(
                sourceRegistry = sourceRegistry,
                database = database,
                downloadManager = downloadManager,
            )
        }
    }
}
