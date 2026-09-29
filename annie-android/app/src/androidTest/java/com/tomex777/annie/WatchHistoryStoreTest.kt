package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WatchHistoryStoreTest {
    @get:Rule val compose = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val item = CatalogItem(
        id = 404,
        mediaType = "ANIME",
        title = "Resume Me",
        image = "https://img.test/resume.jpg",
        year = 2026,
        status = "RELEASING",
        episodes = 12,
        chapters = null,
    )

    @Before fun resetBefore() {
        WatchHistoryStore.clear(context)
    }

    @After fun resetAfter() {
        WatchHistoryStore.clear(context)
    }

    @Test fun watchProgressSurvivesStoreRoundTripAndCompletedTitlesLeaveContinueList() {
        WatchHistoryStore.record(
            context = context,
            item = item,
            positionMs = 65_000L,
            durationMs = 300_000L,
            mediaUri = "https://media.test/episode.m3u8",
            videoConfigJson = """{"quality":"1080p"}""",
            mode = PlayerMode.STREAMING,
            updatedAt = 10L,
        )

        val saved = WatchHistoryStore.read(context).single()
        assertEquals(item.title, saved.title)
        assertEquals(65_000L, saved.positionMs)
        assertEquals("https://media.test/episode.m3u8", saved.mediaUri)
        assertEquals("Resume · Resume Me · 1:05", WatchHistoryStore.actionLabel(saved))
        assertEquals(saved, WatchHistoryStore.continueWatching(context, setOf("ANIME")).single())

        WatchHistoryStore.record(
            context = context,
            item = item,
            positionMs = 296_000L,
            durationMs = 300_000L,
            mediaUri = saved.mediaUri,
            videoConfigJson = saved.videoConfigJson,
            mode = PlayerMode.STREAMING,
            updatedAt = 20L,
        )

        assertTrue(WatchHistoryStore.read(context).single().completed)
        assertTrue(WatchHistoryStore.continueWatching(context).isEmpty())
    }

    @Test fun seriesCardShowsPersistedLastWatchedStateAndResumeAction() {
        WatchHistoryStore.record(
            context = context,
            item = item,
            positionMs = 125_000L,
            durationMs = 300_000L,
            mediaUri = "https://media.test/episode.m3u8",
            videoConfigJson = null,
            mode = PlayerMode.STREAMING,
        )

        compose.setContent {
            AnnieTheme {
                SeriesCardMessage(item) {}
            }
        }

        compose.onNodeWithTag("anime_last_watched").assertIsDisplayed()
        compose.onNodeWithText("Last watched: 2:05 of 5:00").assertIsDisplayed()
        compose.onNodeWithText("Resume").assertIsDisplayed()
        compose.onNodeWithTag("anime_action_play").assertIsDisplayed()
    }
}
