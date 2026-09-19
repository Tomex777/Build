package com.example.whatsapp.presentation.homescreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import com.example.whatsapp.presentation.navigation.Routes
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernChatsTab
import com.example.whatsapp.presentation.viewmodels.BaseViewModel

@Composable
fun HomeScreen(
    navHostController: NavHostController,
    homeBaseViewModel: BaseViewModel,
) {
    val chats by homeBaseViewModel.chatList.collectAsState()

    ModernChatsTab(
        chats = chats,
        onTabSelected = { tab ->
            val route = when (tab) {
                MainTab.Chats -> Routes.HomeScreen.route
                MainTab.Updates -> Routes.UpdateScreen.route
                MainTab.Communities -> Routes.CommunitiesScreen.route
                MainTab.Calls -> Routes.CallScreen.route
            }
            if (navHostController.currentDestination?.route != route) {
                navHostController.navigate(route) {
                    launchSingleTop = true
                }
            }
        },
        onChatClick = { chat ->
            val identifier = chat.phoneNumber ?: chat.name ?: return@ModernChatsTab
            navHostController.navigate(Routes.ChatScreen.createRoute(identifier))
        },
        onSettingsClick = {
            navHostController.navigate(Routes.SettingScreen.route)
        },
    )
}
