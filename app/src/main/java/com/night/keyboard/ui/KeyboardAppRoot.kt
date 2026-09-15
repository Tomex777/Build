package com.night.keyboard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.night.keyboard.ui.screens.ClipboardScreen
import com.night.keyboard.ui.screens.EditorScreen
import com.night.keyboard.ui.screens.HomeScreen
import com.night.keyboard.ui.screens.SettingsScreen

private data class Destination(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun KeyboardAppRoot() {
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route ?: "home"
    val items = listOf(
        Destination("home", "Home") { Icon(Icons.Outlined.Home, null) },
        Destination("editor", "Editor") { Icon(Icons.Outlined.Tune, null) },
        Destination("clipboard", "Clipboard") { Icon(Icons.Outlined.ContentPaste, null) },
        Destination("settings", "Settings") { Icon(Icons.Outlined.Settings, null) },
    )
    Scaffold(bottomBar = {
        NavigationBar {
            items.forEach { item ->
                NavigationBarItem(
                    selected = current == item.route,
                    onClick = {
                        nav.navigate(item.route) {
                            launchSingleTop = true
                            popUpTo("home") { saveState = true }
                            restoreState = true
                        }
                    },
                    icon = item.icon,
                    label = { Text(item.label) },
                    modifier = Modifier.testTag("nav_${item.route}"),
                )
            }
        }
    }) { insets ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(insets)) {
            composable("home") {
                HomeScreen(
                    onOpenEditor = { nav.navigate("editor") },
                    onOpenClipboard = { nav.navigate("clipboard") },
                )
            }
            composable("editor") { EditorScreen() }
            composable("clipboard") { ClipboardScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
