package com.example.whatsapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.whatsapp.presentation.navigation.WhatsAppNavigationSystem
import com.example.whatsapp.presentation.viewmodels.ThemeViewModel
import com.example.whatsapp.ui.theme.WhatsappTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()
            
            WhatsappTheme(darkTheme = isDarkMode) {
                WhatsAppNavigationSystem()
            }
        }
    }
}

