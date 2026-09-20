package com.night.homira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.night.homira.data.LiveProfile
import com.night.homira.ui.HomiraProductionApp

class HomiraLiveSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomiraProductionApp(
                initialProfile = LiveProfile(
                    id = "smoke-user",
                    displayName = "Smoke User",
                    username = "smoke",
                    phoneE164 = "+2348012345678",
                    email = "smoke@example.com"
                ),
                liveMode = true
            )
        }
    }
}
