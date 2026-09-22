package com.example.whatsapp.presentation.communitiesscreen

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.example.whatsapp.presentation.navigation.Routes
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernCommunitiesTab

@Composable
fun CommunitiesScreen(navHostController: NavHostController) {
    ModernCommunitiesTab(
        onTabSelected = { tab ->
            val route = when (tab) {
                MainTab.Chats -> Routes.HomeScreen.route
                MainTab.Updates -> Routes.UpdateScreen.route
                MainTab.Scripts -> Routes.HomeScreen.route
                MainTab.Communities -> Routes.CommunitiesScreen.route
                MainTab.Calls -> Routes.CallScreen.route
                MainTab.You -> Routes.SettingScreen.route
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
