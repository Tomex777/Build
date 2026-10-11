package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExtensionsManagerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun installedPackagesAreVisibleToggleableAndConfigurable() {
        val manifest = AnniePackageManifest(
            packageId = "media.source",
            displayName = "Media Source",
            version = "1.2.3",
            apiVersion = AnniePackageManifest.CURRENT_API_VERSION,
            entryPoint = "index.js",
            permissions = setOf("android.device.info"),
            commands = listOf(AnniePackageCommand("search", "Search the catalog")),
            sources = listOf(AnniePackageSource("catalog", "Anime catalog", listOf("anime", "movie"), "search")),
        )
        val disabled = ScriptProject(
            "media-source",
            "Media Source",
            "index.js",
            mapOf("index.js" to ""),
            false,
            manifest,
        )
        var toggled: Pair<String, Boolean>? = null
        var configured: String? = null
        var learnOpened = false
        var installOpened = false
        var uninstalled: String? = null

        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF07111E))) {
                AnnieTheme {
                    ExtensionsManagerContent(
                        projects = listOf(disabled),
                        onToggle = { project, enabled -> toggled = project.id to enabled },
                        onConfigure = { configured = it.id },
                        onOpenStudio = {},
                        onUninstall = { uninstalled = it.id },
                        onLearn = { learnOpened = true },
                        onInstallExtension = { installOpened = true },
                        grantedPermissions = { setOf("android.device.info") },
                    )
                }
            }
        }

        compose.onNodeWithTag("extensions_manager").assertIsDisplayed()
        compose.onNodeWithTag("extensions_install").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, installOpened) }
        compose.onNodeWithText("AniList").assertDoesNotExist()
        compose.onNodeWithText("Wikidata").assertDoesNotExist()
        compose.onNodeWithText("TVmaze").assertDoesNotExist()
        compose.onNodeWithText("Packages").assertIsDisplayed()
        compose.onNodeWithTag("extension_project_media-source").assertIsDisplayed()
        compose.onNodeWithText("Media Source").assertIsDisplayed()
        compose.onNodeWithText("Version 1.2.3").assertIsDisplayed()
        compose.onNodeWithTag("extension_package_id_media-source").assertDoesNotExist()
        compose.onNodeWithTag("extension_source_catalog").assertDoesNotExist()
        compose.onNodeWithText("/search · Search the catalog").assertIsDisplayed()
        compose.onNodeWithTag("extension_command_media-source_search").assertIsDisplayed()
        compose.onNodeWithTag("extension_permission_media-source_android_device_info").performScrollTo()
        compose.onNodeWithText("✓ Device info").assertIsDisplayed()
        saveEmulatorScreenshot("annie-extension-detail-permissions")
        compose.onNodeWithTag("extension_toggle_media-source").performScrollTo()
        compose.onNodeWithTag("extension_toggle_media-source").assertIsOff().performClick()
        compose.runOnIdle { assertEquals("media-source" to true, toggled) }
        compose.onNodeWithTag("extension_configure_media-source").performScrollTo()
        compose.onNodeWithText("Configure").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, configured) }
        compose.onNodeWithTag("extension_uninstall_media-source").performScrollTo().performClick()
        compose.onNodeWithText("Uninstall Media Source?").assertIsDisplayed()
        compose.onNodeWithTag("extension_uninstall_confirm").performClick()
        compose.runOnIdle { assertEquals("media-source", uninstalled) }
        compose.onNodeWithTag("extensions_learn").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, learnOpened) }
        saveEmulatorScreenshot("annie-extensions")
    }

    @Test fun beginnerGuideExplainsAFirstCommandAndAndroidPermissions() {
        var createTapped = false
        var extensionsTapped = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF07111E))) {
                AnnieTheme {
                    AnnieScriptLearningContent(
                        onCreateScript = { createTapped = true },
                        onExtensions = { extensionsTapped = true },
                    )
                }
            }
        }
        compose.onNodeWithTag("script_learning").assertIsDisplayed()
        compose.onNodeWithText("Your first command").assertIsDisplayed()
        compose.onNodeWithTag("script_learning").performScrollToNode(hasTestTag("script_learning_first_command"))
        compose.onNodeWithTag("script_learning_first_command").assertIsDisplayed()
        saveEmulatorScreenshot("annie-scripting-learn")
        compose.onNodeWithTag("script_learning").performScrollToNode(hasText("Use Android features safely"))
        compose.onNodeWithText("Use Android features safely").assertIsDisplayed()
        saveEmulatorScreenshot("annie-scripting-android-capabilities")
        compose.onNodeWithTag("script_learning_create").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, createTapped) }
        compose.onNodeWithTag("script_learning_extensions").performScrollTo()
        compose.onNodeWithTag("script_learning_extensions").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, extensionsTapped) }
    }
}
