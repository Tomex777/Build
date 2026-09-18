package com.example.whatsapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.whatsapp.presentation.ai.AiChatScreen
import com.example.whatsapp.presentation.callscreen.CallScreen
import com.example.whatsapp.presentation.chatscreen.ChatScreen
import com.example.whatsapp.presentation.communitiesscreen.CommunitiesScreen
import com.example.whatsapp.presentation.homescreen.HomeScreen
import com.example.whatsapp.presentation.profile.userProfilesetScreen
import com.example.whatsapp.presentation.settings.SettingsScreen
import com.example.whatsapp.presentation.settings.AccountSettingsScreen
import com.example.whatsapp.presentation.settings.PrivacySettingsScreen
import com.example.whatsapp.presentation.settings.ChatsSettingsScreen
import com.example.whatsapp.presentation.settings.NotificationsSettingsScreen
import com.example.whatsapp.presentation.settings.StorageSettingsScreen
import com.example.whatsapp.presentation.settings.HelpSettingsScreen
import com.example.whatsapp.presentation.settings.InviteSettingsScreen
import com.example.whatsapp.presentation.settings.AccountCenterSettingsScreen
import com.example.whatsapp.presentation.splashscreen.SplashScreen
import com.example.whatsapp.presentation.updatescreen.UpdateScreen
import com.example.whatsapp.presentation.userregistrationscreen.UserRegistrationScreen
import com.example.whatsapp.presentation.viewmodels.BaseViewModel
import com.example.whatsapp.presentation.welcomescreen.WelcomeScreen
import androidx.navigation.navArgument

@Composable
fun WhatsAppNavigationSystem() {
    val navController = rememberNavController()
    NavHost(startDestination = Routes.SplashScreen.route, navController = navController ) {

        composable(Routes.SplashScreen.route) {
            SplashScreen(navController)
        }

        composable(Routes.WelcomeScreen.route){
            WelcomeScreen(navController)
        }

        composable(Routes.UserRegistrationScreen.route){
            UserRegistrationScreen(navController)
        }

        composable(Routes.HomeScreen.route){
            val baseViewModel: BaseViewModel = hiltViewModel()
            HomeScreen(navController, baseViewModel)
        }

        composable(Routes.UpdateScreen.route){
            UpdateScreen(navController)
        }

        composable(Routes.CommunitiesScreen.route){
            CommunitiesScreen(navController)
        }

        composable(Routes.CallScreen.route){
            CallScreen(navController)
        }

        composable(Routes.UserProfileSetScreen.route) {
            // This is the setup mode (after OTP verification)
            userProfilesetScreen(
                navHostController = navController,
                isSetupMode = true
            )
        }
        
        composable("${Routes.UserProfileSetScreen.route}/edit") {
            // This is the edit mode (from More menu)
            userProfilesetScreen(
                navHostController = navController,
                isSetupMode = false
            )
        }

        composable(Routes.SettingScreen.route) {
            SettingsScreen(navHostController = navController)
        }
        
        composable(Routes.AccountSettingsScreen.route) {
            AccountSettingsScreen(navHostController = navController)
        }
        
        composable(Routes.PrivacySettingsScreen.route) {
            PrivacySettingsScreen(navHostController = navController)
        }
        
        composable(Routes.ChatsSettingsScreen.route) {
            ChatsSettingsScreen(navHostController = navController)
        }
        
        composable(Routes.NotificationsSettingsScreen.route) {
            NotificationsSettingsScreen(navHostController = navController)
        }
        
        composable(Routes.StorageSettingsScreen.route) {
            StorageSettingsScreen(navHostController = navController)
        }
        
        composable(Routes.HelpSettingsScreen.route) {
            HelpSettingsScreen(navHostController = navController)
        }
        
        composable(Routes.InviteSettingsScreen.route) {
            InviteSettingsScreen(navHostController = navController)
        }
        
        composable(Routes.AccountCenterSettingsScreen.route) {
            AccountCenterSettingsScreen(navHostController = navController)
        }

        composable(
            route = Routes.ChatScreen.ROUTE_WITH_ARG,
            arguments = listOf(
                navArgument("phoneNumber") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            val baseViewModel: BaseViewModel = hiltViewModel()
            ChatScreen(phoneNumber, navController, baseViewModel)
        }
        
        composable(Routes.AiChatScreen.route) {
            AiChatScreen(navController)
        }


    }

}