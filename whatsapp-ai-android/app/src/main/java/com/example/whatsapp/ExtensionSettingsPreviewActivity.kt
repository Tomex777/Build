package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.whatsapp.extensions.settings.NightExtensionSettingsExamples
import com.example.whatsapp.extensions.settings.NightExtensionSettingsPanel
import com.example.whatsapp.extensions.settings.NightExtensionSettingsStore
import com.example.whatsapp.ui.theme.WhatsappTheme

class ExtensionSettingsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContent {
            WhatsappTheme(darkTheme = true) {
                val store = remember { NightExtensionSettingsStore(applicationContext) }
                var selected by remember { mutableStateOf("anime") }
                var lastAction by remember { mutableStateOf("") }

                val schema =
                    if (selected == "music") {
                        NightExtensionSettingsExamples.music
                    } else {
                        NightExtensionSettingsExamples.anime
                    }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor(0xFF101416))
                        .navigationBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 42.dp, start = 10.dp, end = 10.dp),
                    ) {
                        TextButton(
                            onClick = { selected = "anime" },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Anime settings tab" },
                        ) {
                            Text("Anime")
                        }
                        TextButton(
                            onClick = { selected = "music" },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Music settings tab" },
                        ) {
                            Text("Music")
                        }
                    }

                    if (lastAction.isNotBlank()) {
                        Text(
                            text = "Action handled: $lastAction",
                            color = ComposeColor.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ComposeColor(0xFF20272A))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        NightExtensionSettingsPanel(
                            schema = schema,
                            store = store,
                            onAction = { extensionId, actionId ->
                                lastAction = "$extensionId:$actionId"
                            },
                        )
                    }
                }
            }
        }
    }
}
