package app.nami.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NamiApplication

        setContent {
            NamiApp(
                sourceRegistry = app.sourceRegistry,
                installedSourceRegistry = app.installedSourceRegistry,
                sourceEnablementStore = app.sourceEnablementStore,
                database = app.database,
                downloadManager = app.downloadManager,
            )
        }
    }
}
