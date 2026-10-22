package com.tomex777.annie

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

/** Runs the original Omni Gecko finder against real DOM content. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniFindInPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun findNextAndClearActOnLiveGeckoPage() {
        val server = GeckoLocalFixture()
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var model: AnnieGeckoBrowserModel
        lateinit var tab: AnnieGeckoTab
        try {
            compose.runOnUiThread {
                model = AnnieGeckoBrowserModel(app)
                val prior = model.tabs.toList()
                tab = model.newTab(address = server.baseUrl + "/one", autoLoad = false)
                prior.forEach(model::close)
            }
            compose.setContent {
                AndroidView(
                    factory = { GeckoView(it).apply { setSession(tab.session) } },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { it.releaseSession() }
                )
            }
            compose.runOnUiThread { assertTrue(model.load(tab, server.baseUrl + "/one")) }
            compose.waitUntil(45_000) { tab.title == "Gecko one" && !tab.loading }
            compose.runOnUiThread {
                model.openFindInPage()
                model.updateFindInPage("Local browser acceptance")
            }
            compose.waitUntil(45_000) { model.findController.total >= 1 }
            assertTrue(model.findController.found)
            assertTrue(model.findController.current > 0)
            compose.runOnUiThread {
                model.findNext()
                model.findPrevious()
                model.closeFindInPage()
            }
            assertEquals("", model.findController.query)
            assertEquals(0, model.findController.current)
            assertEquals(0, model.findController.total)
        } finally {
            compose.runOnUiThread { if (::model.isInitialized) model.destroy() }
            server.close()
        }
    }
}
