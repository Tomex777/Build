package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        compose.setContent {
            AnnieTheme {
                ExtensionsManagerContent(
                    projects = listOf(disabled),
                    onToggle = { project, enabled -> toggled = project.id to enabled },
                    onConfigure = { configured = it.id },
                    onOpenStudio = {},
                )
            }
        }

        compose.onNodeWithTag("extensions_manager").assertIsDisplayed()
        compose.onNodeWithText("AniList").assertIsDisplayed()
        compose.onNodeWithText("Wikidata").assertIsDisplayed()
        compose.onNodeWithText("TVmaze").assertIsDisplayed()
        compose.onNodeWithTag("extension_project_media-source").assertIsDisplayed()
        compose.onNodeWithText("Media Source").assertIsDisplayed()
        compose.onNodeWithText("Package 1.2.3 · API 1").assertIsDisplayed()
        compose.onNodeWithTag("extension_toggle_media-source").assertIsOff().performClick()
        compose.runOnIdle { assertEquals("media-source" to true, toggled) }
        compose.onNodeWithText("Enable to configure").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, configured) }
    }
}
