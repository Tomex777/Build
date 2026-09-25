package app.nami.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

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
