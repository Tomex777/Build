package com.tomex777.annie

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ReportedFlowsTest {
    @get:Rule val compose = createComposeRule()

    private val manga = CatalogItem(
        id = 9, mediaType = "MANGA", title = "Moonlit Archive", image = "",
        year = 2024, status = "RELEASING", episodes = null, chapters = 28,
        creator = "Yuna Mori", genres = listOf("Fantasy", "Mystery"),
        summary = "A librarian uncovers a hidden archive beneath the moonlit city."
    )

    @Test fun slashAutocompleteAppearsAsTheUserTypesAndCanFillTheComposer() {
        var selected = ""
        compose.setContent {
            Composer(value = "/ani", onValueChange = {}, onSuggestionSelected = { selected = it }, onSend = {}, onMenu = {})
        }
        compose.onNodeWithTag("slash_suggestions").assertIsDisplayed()
        compose.onNodeWithText("/anime").assertIsDisplayed()
        compose.onNodeWithText("Browse anime").assertIsDisplayed()
        compose.onNodeWithText("/anime search").assertIsDisplayed()
        compose.onNodeWithText("Search the catalog").assertIsDisplayed()
        compose.onNodeWithText("/anime recent").assertIsDisplayed()
        compose.onNodeWithText("New episodes").assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("/anime", substring = false).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("/anime search", substring = false).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("/anime recent", substring = false).fetchSemanticsNodes().size)
        compose.onNodeWithText("/anime").performClick()
        assertEquals("/anime", selected)
    }

    @Test fun approvedMangaDetailsCardShowsCoverMetadataGenresSynopsisAndActions() {
        val actions = mutableListOf<String>()
        compose.setContent { MangaResultMessage(manga) { actions += it } }
        compose.onNodeWithTag("manga_details_card").assertIsDisplayed()
        compose.onNodeWithTag("manga_cover_artwork").assertIsDisplayed()
        compose.onNodeWithText("Moonlit Archive").assertIsDisplayed()
        compose.onNodeWithText("Yuna Mori · 2024 · 28 chapters · Ongoing").assertIsDisplayed()
        compose.onNodeWithText("Fantasy").assertIsDisplayed()
        compose.onNodeWithText("Mystery").assertIsDisplayed()
        compose.onNodeWithText(manga.summary).assertIsDisplayed()
        compose.onNodeWithText("Last read chapter · Not started").assertIsDisplayed()
        compose.onNodeWithTag("manga_action_Continue reading").performClick()
        compose.onNodeWithTag("manga_action_Chapters").performClick()
        assertEquals(listOf("reader", "chapters"), actions)
    }

    @Test fun searchResultsUseDetailsActionAndDoNotShowSelectTitleFooter() {
        var selected = 0
        compose.setContent { CatalogCard(manga) { selected++ } }
        compose.onNodeWithTag("catalog_details_action").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Select this title  ›").fetchSemanticsNodes().size)
        compose.onNodeWithTag("catalog_details_action").performClick()
        assertEquals(1, selected)
    }

    @Test fun approvedActionColorsRemainCyanPurpleTealAndCyan() {
        assertEquals(Color(0xFF42B9F5), actionColor("Search anime"))
        assertEquals(Color(0xFFB68CFF), actionColor("Recently aired"))
        assertEquals(Color(0xFF54D6AE), actionColor("Continue watching"))
        assertEquals(Color(0xFF42B9F5), actionColor("Downloads"))
    }

    @Test fun choosingEpisodeDateRangeReturnsOneRangeSpecificUnavailableResult() {
        for (range in listOf("Today", "This week", "All")) {
            val result = recentEpisodesUnavailableMessage(range)
            assertTrue(result.contains(range))
            assertTrue(result.contains("Connect an anime extension"))
            assertFalse(result.contains("Today, This week, All"))
        }
    }

    @Test fun selectingEpisodeRangeReplacesChoicesWithOneUnavailableResult() {
        compose.setContent {
            val range = remember { mutableStateOf<String?>(null) }
            val selected = range.value
            if (selected == null) {
                ChatBubble(
                    entry = ChatEntry(1, false, "No episodes found yet.", menuTitle = "New anime episodes", actions = listOf("Today", "This week", "All")),
                    onCatalogClick = {},
                    onActionClick = { _, action -> range.value = action },
                    onOpenSource = {},
                    onSeriesAction = { _, _, _ -> }
                )
            } else {
                androidx.compose.material3.Text(recentEpisodesUnavailableMessage(selected))
            }
        }
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("No episodes found for Today. Connect an anime extension to check availability.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("This week").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("All").fetchSemanticsNodes().size)
    }
}
