package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.whatsapp.data.night.NightLiveVoiceClient
import com.example.whatsapp.presentation.profile.NightLiveVoiceScreen
import com.example.whatsapp.ui.theme.WhatsappTheme

class NightLiveVoicePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        setContent {
            WhatsappTheme(darkTheme = true) {
                var muted by remember { mutableStateOf(false) }
                var speaker by remember { mutableStateOf(false) }

                NightLiveVoiceScreen(
                    chatTitle = "Night",
                    state = NightLiveVoiceClient.State.LISTENING,
                    error = null,
                    microphoneMuted = muted,
                    speakerEnabled = speaker,
                    onToggleMute = { muted = !muted },
                    onToggleSpeaker = { speaker = !speaker },
                    onEndCall = {},
                )
            }
        }
    }
}
