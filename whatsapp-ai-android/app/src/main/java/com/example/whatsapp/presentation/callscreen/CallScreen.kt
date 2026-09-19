package com.example.whatsapp.presentation.callscreen

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.example.whatsapp.presentation.navigation.Routes
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernCallsTab

@Composable
fun CallScreen(navHostController: NavHostController) {
    ModernCallsTab(
        onTabSelected = { tab ->
            val route = when (tab) {
                MainTab.Chats -> Routes.HomeScreen.route
                MainTab.Updates -> Routes.UpdateScreen.route
                MainTab.Communities -> Routes.CommunitiesScreen.route
                MainTab.Calls -> Routes.CallScreen.route
            }
            if (navHostController.currentDestination?.route != route) {
                navHostController.navigate(route) { launchSingleTop = true }
            }
        },
        onSettingsClick = {
            navHostController.navigate(Routes.SettingScreen.route)
        },
    )
}
