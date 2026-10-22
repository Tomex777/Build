package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.browser.engine.AnnieBrowserEngineBridge
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device test of a real Omni-derived built-in Gecko WebExtension, not a mock. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniExtensionTest {
    @Test fun bundledAnnieRebrandedExtensionInstallsDisabledOnRealGecko() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        lateinit var bridge: AnnieBrowserEngineBridge
        instrumentation.runOnMainSync {
            bridge = AnnieBrowserEngineBridge(
                instrumentation.targetContext,
                AnnieGeckoRuntime.get(instrumentation.targetContext)
            )
            // This tool intercepts page copy/touch/key events: it must be OFF
            // except when the user opts in. The test only loads it disabled.
            bridge.setUniversalCopy(enabled = false) { done.countDown() }
        }
        assertTrue("Gecko WebExtension install timeout", done.await(30, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertTrue("Annie-bundled upstream WebExtension must actually install", bridge.isUniversalCopyInstalled())
        }
    }
}
