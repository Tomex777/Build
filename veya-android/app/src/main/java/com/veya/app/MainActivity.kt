package com.veya.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.veya.app.ui.VeyaApp

class MainActivity : ComponentActivity() {
    private var sharedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedText = extractSharedText(intent)
        setContent {
            VeyaApp(initialUrl = sharedText.orEmpty())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = extractSharedText(intent)
        setContent { VeyaApp(initialUrl = sharedText.orEmpty()) }
    }

    private fun extractSharedText(intent: Intent?): String? {
        return when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString

            Intent.ACTION_SEND -> {
                if (intent.type != "text/plain") return null
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?.split(Regex("\\s+"))
                    ?.firstOrNull {
                        it.startsWith("https://") ||
                            it.startsWith("http://")
                    }
            }

            else -> null
        }
    }
}
