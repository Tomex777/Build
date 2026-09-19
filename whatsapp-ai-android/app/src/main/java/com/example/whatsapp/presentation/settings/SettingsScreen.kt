package com.example.whatsapp.presentation.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.example.whatsapp.presentation.shell.ModernSettingsScreen

@Composable
fun SettingsScreen(
    navHostController: NavHostController,
) {
    ModernSettingsScreen(
        onBack = { navHostController.popBackStack() },
    )
}
