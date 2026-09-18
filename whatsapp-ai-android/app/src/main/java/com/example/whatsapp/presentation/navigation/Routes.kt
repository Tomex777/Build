package com.example.whatsapp.presentation.navigation

sealed class Routes(val route: String) {
    data object SplashScreen : Routes("splash_screen")
    data object WelcomeScreen : Routes("welcome_screen")
    data object UserRegistrationScreen : Routes("user_registration_screen")
    data object HomeScreen : Routes("home_screen")
    data object UpdateScreen : Routes("update_screen")
    data object CommunitiesScreen : Routes("communities_screen")
    data object CallScreen : Routes("call_screen")

    data object UserProfileSetScreen : Routes("user_profile_screen")

    data object SettingScreen : Routes("setting_screen")
    
    data object AccountSettingsScreen : Routes("account_settings_screen")
    data object PrivacySettingsScreen : Routes("privacy_settings_screen")
    data object ChatsSettingsScreen : Routes("chats_settings_screen")
    data object NotificationsSettingsScreen : Routes("notifications_settings_screen")
    data object StorageSettingsScreen : Routes("storage_settings_screen")
    data object HelpSettingsScreen : Routes("help_settings_screen")
    data object InviteSettingsScreen : Routes("invite_settings_screen")
    data object AccountCenterSettingsScreen : Routes("account_center_settings_screen")

    data object ChatScreen : Routes("chat_screen") {

        const val ROUTE_WITH_ARG = "chat_screen/{phoneNumber}"

        fun createRoute(phoneNumber: String) =
            "chat_screen/$phoneNumber"
    }
    
    data object AiChatScreen : Routes("ai_chat_screen")


}