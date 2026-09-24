package com.tomex777.annie

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

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
        compose.onNodeWithTag("slash_suggestions").assertExists()
        compose.onNodeWithText("/anime").assertExists()
        compose.onNodeWithText("Anime menu").assertExists()
        compose.onNodeWithText("/anime search").assertExists()
        compose.onNodeWithText("Search anime").assertExists()
        compose.onNodeWithText("/anime").performClick()
        assertEquals("/anime", selected)
    }

    @Test fun approvedMangaDetailsCardShowsCoverMetadataGenresSynopsisAndActions() {
        val actions = mutableListOf<String>()
        compose.setContent { MangaResultMessage(manga) { actions += it } }
        compose.onNodeWithTag("manga_details_card").assertExists()
        compose.onNodeWithText("Moonlit Archive").assertExists()
        compose.onNodeWithText("Yuna Mori · 2024 · 28 chapters · Ongoing").assertExists()
        compose.onNodeWithText("Fantasy").assertExists()
        compose.onNodeWithText("Mystery").assertExists()
        compose.onNodeWithText(manga.summary).assertExists()
        compose.onNodeWithText("Last read chapter · Not started").assertExists()
        compose.onNodeWithTag("manga_action_Continue reading").performClick()
        compose.onNodeWithTag("manga_action_Chapters").performClick()
        assertEquals(listOf("reader", "chapters"), actions)
    }

    @Test fun searchResultsUseDetailsActionAndDoNotShowSelectTitleFooter() {
        var selected = 0
        compose.setContent { CatalogCard(manga) { selected++ } }
        compose.onNodeWithTag("catalog_details_action").assertExists()
        compose.onNodeWithText("Select this title  ›").assertDoesNotExist()
        compose.onNodeWithTag("catalog_result_card").performClick()
        assertEquals(1, selected)
    }

    @Test fun choosingEpisodeDateRangeReturnsOneRangeSpecificUnavailableResult() {
        for (range in listOf("Today", "This week", "All")) {
            val result = recentEpisodesUnavailableMessage(range)
            assertTrue(result.contains(range))
            assertTrue(result.contains("Connect an anime extension"))
            assertFalse(result.contains("Today, This week, All"))
        }
    }

    @Test fun liveChatLayoutKeepsStatusBarTopBarConversationAndComposerOrderedWithIme() {
        compose.onNodeWithTag("top_bar").assertExists()
        compose.onNodeWithTag("conversation").assertExists()
        compose.onNodeWithTag("composer_input").performClick().performTextInput("/ani")
        compose.onNodeWithTag("slash_suggestions").assertExists()
        compose.onNodeWithTag("top_bar").assertExists()
        val top = compose.onNodeWithTag("top_bar").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithTag("composer_input").fetchSemanticsNode().boundsInRoot
        assertTrue("Top bar should remain above the composer while the keyboard is active", top.bottom < input.top)
    }
}
