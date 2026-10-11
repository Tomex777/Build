package com.tomex777.annie

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Gecko one", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Gecko two", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(8_000) {
                var hidden = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    hidden = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == false
                }
                hidden
            }
            // Address entry must also dismiss an open browser panel and hand
            // focus to the reattached page, including the explicit Go button.
            compose.onNodeWithContentDescription("Browser menu").performClick()
            compose.onNodeWithText("Downloads", substring = false).performClick()
            address.performClick()
            address.performTextReplacement(fixture.baseUrl + "/one")
            compose.onNodeWithTag("annie_browser_go").performClick()
            address.assertIsNotFocused()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Gecko one", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("annie_browser_tabs_webview").assertIsDisplayed()
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
}
