package com.tomex777.annie

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieGeckoBrowserUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualColdStartShowsBlankAnnieHomeAndPrivateTabWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val libraryPrefs = context.getSharedPreferences("annie_gecko_library_v1", Context.MODE_PRIVATE)
        val initialTabs = prefs.all
        val initialLibrary = libraryPrefs.all
        val shown = mutableStateOf(true)
        try {
            compose.runOnUiThread {
                prefs.edit().clear().commit()
                libraryPrefs.edit().clear().commit()
            }
            compose.setContent {
                AnnieTheme {
                    if (shown.value) AnnieGeckoStandaloneBrowser(
                        onExit = {}, onOwn = {}, onRelease = {},
                    )
                }
            }
            compose.onNodeWithTag("annie_gecko_browser").assertIsDisplayed()
            compose.onNodeWithTag("annie_browser_home").assertIsDisplayed()
            compose.onNodeWithText("Annie Browser").assertIsDisplayed()
            compose.onNodeWithTag("annie_browser_tabs_webview").assertDoesNotExist()
            saveEmulatorScreenshot("annie-gecko-real-cold-home")
            compose.onNodeWithContentDescription("Browser menu").performClick()
            compose.onNodeWithText("New private tab").performClick()
            compose.onNodeWithTag("annie_browser_home").assertIsDisplayed()
            compose.onNodeWithText("Private tab").assertIsDisplayed()
            saveEmulatorScreenshot("annie-gecko-real-private-home")
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.runOnIdle {
                listOf(prefs to initialTabs, libraryPrefs to initialLibrary).forEach { (target, saved) ->
                    val edit = target.edit().clear()
                    saved.forEach { (key, value) ->
                        when (value) {
                            is String -> edit.putString(key, value)
                            is Int -> edit.putInt(key, value)
                            is Long -> edit.putLong(key, value)
                            is Boolean -> edit.putBoolean(key, value)
                        }
                    }
                    edit.commit()
                }
            }
        }
    }

    @Test fun addressSubmissionDismissesKeyboardAndBrowserControlsRespectSystemBars() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = GeckoLocalFixture()
        val shown = mutableStateOf(true)
        val snapshots = listOf("annie_gecko_tabs_v1", "annie_gecko_library_v1").map {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).let { prefs -> prefs to prefs.all }
        }
        try {
            compose.runOnUiThread {
                compose.activity.enableEdgeToEdge()
                snapshots.first().first.edit().clear()
                    .putString("open", JSONArray().put(fixture.baseUrl + "/one").toString())
                    .putInt("selected", 0).commit()
            }
            compose.setContent {
                AnnieTheme { if (shown.value) AnnieGeckoStandaloneBrowser(onExit = {}, onOwn = {}, onRelease = {}) }
            }
            compose.onNodeWithTag("annie_gecko_browser").assertIsDisplayed()
            // Native page pixels assert that Gecko actually navigated and rendered;
            // this compact chrome intentionally does not duplicate the title strip.
            waitForNativePageColor(32, 48, 64)
            val address = compose.onNodeWithTag("annie_browser_tabs_address")
            val back = compose.onNodeWithTag("annie_browser_back")
            val addressBounds = address.fetchSemanticsNode().boundsInRoot
            val backBounds = back.fetchSemanticsNode().boundsInRoot
            compose.runOnUiThread {
                val decor = compose.activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)!!
                assertTrue("URL field overlaps status bar", addressBounds.top >=
                    insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
                assertTrue("Navigation controls overlap system navigation bar", backBounds.bottom <=
                    decor.height - insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            }
            address.performClick()
            address.performTextReplacement(fixture.baseUrl + "/two")
            address.performImeAction()
            address.assertIsNotFocused()
            waitForNativePageColor(48, 64, 32)
            compose.waitUntil(8_000) {
                var hidden = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    hidden = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == false
                }
                hidden
            }
            // Search the genuine Gecko document through the imported Omni
            // finder before returning to normal page navigation.
            compose.onNodeWithContentDescription("Browser menu").performClick()
            compose.onNodeWithText("Find in page").performClick()
            compose.onNodeWithTag("annie_browser_find_input").performTextInput("Gecko")
            compose.onNodeWithTag("annie_browser_find_bar").assertIsDisplayed()
            saveEmulatorScreenshot("annie-gecko-real-find-in-page")
            compose.onNodeWithContentDescription("Close find in page").performClick()

            // Address entry must also dismiss an open browser panel and hand
            // focus to the reattached page, including the explicit Go button.
            compose.onNodeWithContentDescription("Browser menu").performClick()
            compose.onNodeWithText("Downloads", substring = false).performClick()
            address.performClick()
            address.performTextReplacement(fixture.baseUrl + "/one")
            compose.onNodeWithTag("annie_browser_go").performClick()
            address.assertIsNotFocused()
            compose.onNodeWithTag("annie_browser_tabs_webview").assertIsDisplayed()
            waitForNativePageColor(32, 48, 64)
            saveEmulatorScreenshot("annie-gecko-system-bars-and-keyboard")
        } finally {
            // Wait for disposal before restoring preferences; destroy persists tabs.
            compose.runOnIdle { shown.value = false }
            fixture.close()
            compose.runOnIdle {
                snapshots.forEach { (prefs, values) ->
                    val edit = prefs.edit().clear()
                    values.forEach { (key, value) ->
                        when (value) {
                            is String -> edit.putString(key, value)
                            is Int -> edit.putInt(key, value)
                        }
                    }
                    edit.commit()
                }
            }
        }
    }

    private fun waitForNativePageColor(red: Int, green: Int, blue: Int) {
        val viewport = compose.onNodeWithTag("annie_browser_tabs_webview").fetchSemanticsNode().boundsInRoot
        // Sample plain page background, outside the history fixture's center
        // activation button and the text at the top of the page.
        val x = (viewport.left + viewport.width * 0.1f).toInt()
        val y = (viewport.top + viewport.height * 0.8f).toInt()
        compose.waitUntil(10_000) {
            val pixels = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                val actual = pixels.getPixel(x, y)
                kotlin.math.abs(android.graphics.Color.red(actual) - red) <= 8 &&
                    kotlin.math.abs(android.graphics.Color.green(actual) - green) <= 8 &&
                    kotlin.math.abs(android.graphics.Color.blue(actual) - blue) <= 8
            } finally { pixels.recycle() }
        }
    }

}
