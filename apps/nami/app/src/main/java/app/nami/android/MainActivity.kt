package app.nami.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.nami.data.local.NamiDatabase
import app.nami.runtime.NamiSourceRegistry

class MainActivity : ComponentActivity() {

    private val sourceRegistry = NamiSourceRegistry { emptyList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = NamiDatabase(applicationContext)
        setContent {
            NamiApp(
                sourceRegistry = sourceRegistry,
                database = database,
            )
        }
    }
}
