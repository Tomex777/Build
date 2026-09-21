package com.night.keyboard.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.night.keyboard.data.clipboard.ClipboardRepository
import com.night.keyboard.ui.theme.KeyboardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardHarnessActivity : ComponentActivity() {
    @Inject lateinit var clipboardRepository: ClipboardRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val items by clipboardRepository.items.collectAsState(initial = emptyList())
            val latest = items.maxByOrNull { it.createdAt }
            KeyboardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Keyboard Clipboard QA", style = MaterialTheme.typography.titleLarge)
                        Text("Clip count: ${items.size}", modifier = Modifier.padding(top = 8.dp))
                        Text(
                            "Latest clip: ${latest?.text ?: "<none>"}",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
