package com.tomex777.annie

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Agent 2 tests the actual native Compose controls, not a picture or HTML mock.
 * Engine/network journeys and screenshot measurements belong to the combined
 * Agent 1 / Agent 5 instrumentation gates once the Omni source is wired.
 */
@RunWith(AndroidJUnit4::class)
class AnnieBrowserChromeUiTest {
    @get:Rule val rule = createComposeRule()

    private fun state(url: String = "about:blank") = AnnieBrowserChromeState(
        activeId = "regular",
        tabs = listOf(
            AnnieBrowserChromeTab("regular", "New tab", url, privateMode = false),
            AnnieBrowserChromeTab("private", "Private", "https://example.org", privateMode = true),
        ),
        bookmarks = listOf(AnnieBrowserChromeEntry("Example", "https://example.com")),
    )

    private fun actions(
        navigate: (String) -> Boolean = { true },
        select: (String) -> Unit = {},
        close: (String) -> Unit = {},
    ) = AnnieBrowserChromeActions(
        exit = {},
        navigate = navigate,
        back = {},
        forward = {},
        reload = {},
        newTab = {},
        selectTab = select,
        closeTab = close,
        toggleBookmark = {},
        openSaved = {},
        toggleDesktop = {},
        share = {},
    )

    @Test
    fun blankTabHasActualHomeAndNotAnEmptyGeckoSurface() {
        rule.setContent {
            AnnieBrowserChrome(state(), actions(), webContent = { Box(it) },
                downloadsContent = { Box(it) })
        }
        rule.onNodeWithTag("annie_browser_home").assertIsDisplayed()
        rule.onNodeWithTag("annie_browser_tabs_webview").assertDoesNotExist()
        rule.onNodeWithContentDescription("Go to address").assertIsDisplayed()
        rule.onNodeWithContentDescription("Reload page").assertExists()
    }

    @Test
    fun singleAddressFieldSubmitsToEngineAndResolvesActiveTabOnly() {
        val requests = mutableListOf<String>()
        rule.setContent {
            AnnieBrowserChrome(state(), actions(navigate = { requests.add(it); true }),
                webContent = { Box(it) }, downloadsContent = { Box(it) })
        }
        rule.onNodeWithTag("annie_browser_tabs_address").performTextInput("example.com")
        rule.onNodeWithTag("annie_browser_go").performClick()
        rule.runOnIdle { assertEquals(listOf("example.com"), requests) }
    }

    @Test
    fun tabSwitcherUsesRealTabIdsAndCloseIsDistinctFromSelect() {
        var selected: String? = null
        var closed: String? = null
        rule.setContent {
            AnnieBrowserChrome(state(), actions(
                select = { selected = it },
                close = { closed = it },
            ), webContent = { Box(it) }, downloadsContent = { Box(it) })
        }
        rule.onNodeWithTag("annie_browser_tab_switcher").performClick()
        rule.onNodeWithTag("annie_browser_tabs_grid").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close Private").performClick()
        rule.runOnIdle {
            assertEquals("private", closed)
            assertEquals(null, selected)
        }
        rule.onNodeWithTag("annie_browser_tab_private").performClick()
        rule.runOnIdle { assertEquals("private", selected) }
    }

    @Test
    fun selectingPrivateTabRebindsOmniboxWithoutReusingRegularTabAddress() {
        val selection = mutableStateOf("regular")
        val tabs = state("https://example.com")
        rule.setContent {
            AnnieBrowserChrome(
                tabs.copy(activeId = selection.value),
                actions(select = { selection.value = it }),
                webContent = { Box(it) },
                downloadsContent = { Box(it) },
            )
        }
        rule.onNodeWithTag("annie_browser_tabs_address").assertTextEquals("https://example.com")
        rule.onNodeWithTag("annie_browser_tab_switcher").performClick()
        rule.onNodeWithTag("annie_browser_tab_private").performClick()
        rule.onNodeWithTag("annie_browser_tabs_address").assertTextEquals("https://example.org")
    }

    @Test
    fun bookmarksPanelDispatchesSavedUrlWithoutFakeNavigation() {
        var opened = ""
        val base = actions()
        val handlers = AnnieBrowserChromeActions(
            exit = base.exit, navigate = base.navigate,
            back = base.back, forward = base.forward, reload = base.reload,
            newTab = base.newTab, selectTab = base.selectTab, closeTab = base.closeTab,
            toggleBookmark = base.toggleBookmark,
            openSaved = { opened = it },
            toggleDesktop = base.toggleDesktop, share = base.share,
        )
        rule.setContent {
            AnnieBrowserChrome(state("https://example.net"), handlers,
                webContent = { Box(it) }, downloadsContent = { Box(it) })
        }
        rule.onNodeWithContentDescription("Browser menu").performClick()
        rule.onNodeWithText("Bookmarks").performClick()
        rule.onNodeWithText("Example").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("https://example.com", opened) }
    }

    @Test
    fun realAndroidScreenshotEvidenceForHomeAndTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        rule.setContent {
            AnnieBrowserChrome(state(), actions(), webContent = { Box(it) },
                downloadsContent = { Box(it) })
        }
        rule.waitForIdle()
        context.openFileOutput("annie-agent2-home.png", 0).use { stream ->
            rule.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG, 100, stream
            )
        }
        rule.onNodeWithTag("annie_browser_tab_switcher").performClick()
        rule.waitForIdle()
        context.openFileOutput("annie-agent2-tabs.png", 0).use { stream ->
            rule.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG, 100, stream
            )
        }
    }

    @Test
    fun savedLibraryIsNotInventedForEmptyLists() {
        val empty = AnnieBrowserChromeState(
            tabs = listOf(AnnieBrowserChromeTab("a", "New tab", "about:blank", privateMode = true)),
            activeId = "a",
        )
        assertTrue(empty.isNewTab)
        assertTrue(empty.bookmarks.isEmpty())
        assertTrue(empty.history.isEmpty())
        assertFalse(state("https://example.com").isNewTab)
    }
}
