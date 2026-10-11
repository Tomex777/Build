package com.night.cortex

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.night.cortex.server.CortexServerApp
import com.night.cortex.ui.theme.CortexBackground
import com.night.cortex.ui.theme.CortexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.rgb(11, 16, 24)),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.rgb(6, 9, 15)),
        )
        setContent {
            CortexTheme {
                Box(Modifier.fillMaxSize().background(CortexBackground)) {
                    CortexServerApp()
                }
            }
        }
    }
}
