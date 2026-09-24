package app.nami.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.nami.compat.aniyomi.AniyomiExtensionRegistry
import app.nami.data.local.NamiDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = NamiDatabase(applicationContext)
        val sourceRegistry = AniyomiExtensionRegistry(applicationContext)

        setContent {
            NamiApp(
                sourceRegistry = sourceRegistry,
                database = database,
            )
        }
    }
}
