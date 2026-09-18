package com.night.extensionchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.night.extensionchat.ui.ExtensionChatApp
import com.night.extensionchat.ui.ExtensionChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExtensionChatTheme {
                ExtensionChatApp()
            }
        }
    }
}
