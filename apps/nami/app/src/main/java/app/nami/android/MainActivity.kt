package app.nami.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.nami.data.local.NamiDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = NamiDatabase(applicationContext)
        val sourceRegistry = (application as NamiApplication).sourceRegistry
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
