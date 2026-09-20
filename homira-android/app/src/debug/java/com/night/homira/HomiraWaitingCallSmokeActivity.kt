package com.night.homira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.night.homira.ui.HomiraProductionApp

class HomiraWaitingCallSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomiraProductionApp(
                liveMode = false,
                demoWaitingCall = true
            )
        }
    }
}
