package app.nami.android.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.nami.android.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NamiUiScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun realAnimeSogoFlowRendersPlayerAndScreenshots() {
        dismissSystemUiAnrIfPresent()
        waitForText("Sources", timeoutMillis = 90_000)
        capture("01-home.png")

        composeRule.onNodeWithTag("global-search-field").performTextInput("Bleach")
        composeRule.waitForIdle()
        capture("02-search.png")

        composeRule.onNodeWithTag("global-search-field").performImeAction()
        waitForAnimeCard(TARGET_ANIME, timeoutMillis = 150_000)
        capture("03-search-results-bleach.png")

        composeRule.onAllNodesWithContentDescription("Open anime: $TARGET_ANIME")[0].performClick()
        waitForText("Episodes", timeoutMillis = 120_000)
        capture("04-anime-details.png")

        waitForText("Episode 8", timeoutMillis = 120_000)
        composeRule.onAllNodesWithText("Episode 8")[0].performScrollTo()
        composeRule.waitForIdle()
        capture("05-episodes.png")

        composeRule.onNodeWithContentDescription("Play Episode 8").performClick()
        waitForDescription("Nami player video output active", timeoutMillis = 150_000)

        showPlayerControls()
        capture("07-player-controls.png")

        composeRule.onNodeWithContentDescription("Pause").performClick()
        waitForDescription("Play", timeoutMillis = 15_000)
        composeRule.onNodeWithContentDescription("Play").performClick()
        waitForDescription("Pause", timeoutMillis = 30_000)
        composeRule.onNodeWithContentDescription("Seek forward 10 seconds").performClick()

        composeRule.onNodeWithContentDescription("Subtitles").performClick()
        waitUntil(20_000, "real AnimeSogo subtitle choices") {
            composeRule.onAllNodesWithTag("subtitle-external-option")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        val subtitleChoices = composeRule.onAllNodesWithTag("subtitle-external-option")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue(
            "AnimeSogo player did not expose its real external subtitle choices",
            subtitleChoices.isNotEmpty(),
        )
        composeRule.onAllNodesWithTag("subtitle-external-option")[0].performClick()
        waitForDescription("Subtitles active", timeoutMillis = 30_000)

        showPlayerControls()
        composeRule.onNodeWithContentDescription("Nami player video output active").performClick()
        composeRule.waitForIdle()
        capture("08-player-video-visible.png")

        device.pressBack()
        waitForText("Episodes", timeoutMillis = 60_000)
        composeRule.onAllNodesWithText("Episode 8")[0].performScrollTo()

        val downloads = composeRule.onAllNodesWithContentDescription("Download")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("Episode list did not expose a download action", downloads.isNotEmpty())
        composeRule.onAllNodesWithContentDescription("Download")[0].performClick()
        waitUntil(90_000, "episode download to enter a visible state") {
            hasDescription("Pause download") ||
                hasDescription("Pause queued download") ||
                hasDescription("Downloaded")
        }

        device.pressBack()
        waitForText("Library", timeoutMillis = 30_000)
        composeRule.onNodeWithText("Library").performClick()
        waitForDescription("Downloads", timeoutMillis = 30_000)
        val libraryTop = composeRule.onNodeWithTag("library-top-bar")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(
            "Library still has a root-level top inset before its own TopAppBar: top=$libraryTop",
            libraryTop <= 2f,
        )
        capture("06-library.png")
        composeRule.onNodeWithContentDescription("Downloads").performClick()
        waitForText("Downloads", timeoutMillis = 30_000)
        capture("06-downloads.png")

        val downloadMenuDescription = "Download menu for Episode 8"
        waitForDescription(downloadMenuDescription, timeoutMillis = 30_000)
        composeRule.onNodeWithContentDescription(downloadMenuDescription).performClick()
        waitUntil(15_000, "Aniyomi-style download row action") {
            hasText("Cancel") || hasText("Delete") || hasText("Remove")
        }
        when {
            hasText("Cancel") -> composeRule.onNodeWithText("Cancel").performClick()
            hasText("Delete") -> composeRule.onNodeWithText("Delete").performClick()
            hasText("Remove") -> composeRule.onNodeWithText("Remove").performClick()
        }
        waitForText("No downloads", timeoutMillis = 30_000)

        device.pressBack()
        waitForDescription("Settings", timeoutMillis = 30_000)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Extensions", timeoutMillis = 60_000)
        capture("09-sources.png")
    }

    private fun waitForAnimeCard(title: String, timeoutMillis: Long) {
        val description = "Open anime: $title"
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var lastScrollFailure: Throwable? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            dismissSystemUiAnrIfPresent()
            if (hasDescription(description)) return
            runCatching {
                composeRule.onNodeWithTag("global-search-results")
                    .performScrollToNode(hasContentDescription(description))
            }.onFailure { lastScrollFailure = it }
            composeRule.waitForIdle()
            if (hasDescription(description)) return
            SystemClock.sleep(750)
        }
        runCatching { capture("99-ui-timeout.png") }
        composeRule.onRoot(useUnmergedTree = true).printToLog("NamiUiSmoke")
        throw AssertionError("Timed out waiting for anime card '$title' after scrolling global results", lastScrollFailure)
    }

    private fun dismissSystemUiAnrIfPresent() {
        if (device.hasObject(By.textContains("System UI"))) {
            device.findObject(By.text("Wait"))?.click()
            device.waitForIdle()
        }
    }

    private fun showPlayerControls() {
        if (!hasDescription("Pause") && !hasDescription("Play")) {
            val description = if (hasDescription("Nami player video output active")) {
                "Nami player video output active"
            } else {
                "Nami player"
            }
            composeRule.onNodeWithContentDescription(description).performClick()
        }
        waitUntil(20_000, "player controls") {
            hasDescription("Pause") || hasDescription("Play")
        }
    }

    private fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis, "text '$text'") {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun waitForDescription(description: String, timeoutMillis: Long) {
        waitUntil(timeoutMillis, "content description '$description'") {
            hasDescription(description)
        }
    }

    private fun hasDescription(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    private fun hasText(text: String): Boolean =
        composeRule.onAllNodesWithText(text)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    private fun waitUntil(
        timeoutMillis: Long,
        description: String,
        predicate: () -> Boolean,
    ) {
        try {
            composeRule.waitUntil(timeoutMillis) { predicate() }
        } catch (failure: Throwable) {
            throw AssertionError("Timed out waiting for $description", failure)
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val root = composeRule.activity.filesDir
        val directory = File(root, "nami-screenshots")
        assertTrue(
            "Could not create screenshot directory",
            directory.mkdirs() || directory.isDirectory,
        )
        assertTrue("Could not capture $name", device.takeScreenshot(File(directory, name)))
    }

    private companion object {
        const val TARGET_ANIME = "Bleach: Thousand-Year Blood War - The Calamity"
    }
}
