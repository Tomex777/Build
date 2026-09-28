package com.tomex777.annie

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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
            Composer(value = TextFieldValue("/ani"), onValueChange = {}, onSuggestionSelected = { selected = it }, onSend = {}, onMenu = {})
        }
        compose.onNodeWithTag("slash_suggestions").assertIsDisplayed()
        compose.onNodeWithTag("slash_command_/anime").assertIsDisplayed()
        compose.onNodeWithText("Browse anime").assertIsDisplayed()
        compose.onNodeWithText("/anime search").assertIsDisplayed()
        compose.onNodeWithText("Search the catalog").assertIsDisplayed()
        compose.onNodeWithText("/anime recent").assertIsDisplayed()
        compose.onNodeWithText("New episodes").assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("/anime", substring = false).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("/anime search", substring = false).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("/anime recent", substring = false).fetchSemanticsNodes().size)
        compose.onNodeWithTag("slash_command_/anime").performClick()
        assertEquals("/anime", selected)
    }

    @Test fun slashAutocompleteOffersEveryMediaRootAndSharedContinueCommand() {
        val typedCommand = mutableStateOf(TextFieldValue("/anime"))
        compose.setContent {
            Composer(value = typedCommand.value, onValueChange = {}, onSuggestionSelected = {}, onSend = {}, onMenu = {})
        }
        for (command in listOf("/anime", "/manga", "/tv", "/tv series", "/movie", "/music", "/continue", "/anime continue")) {
            typedCommand.value = TextFieldValue(command, selection = TextRange(command.length))
            compose.waitForIdle()
            compose.onNodeWithTag("slash_suggestions").assertIsDisplayed()
            compose.onNodeWithTag("slash_command_$command").assertIsDisplayed()
        }
        typedCommand.value = TextFieldValue("/")
        compose.waitForIdle()
        for (root in listOf("/anime", "/manga", "/movie", "/tv", "/music", "/continue")) {
            compose.onNodeWithText(root, substring = false).assertExists()
        }
    }

    @Test fun selectingSlashSuggestionPlacesCaretAfterInsertedCommand() {
        val typedCommand = mutableStateOf(TextFieldValue("/"))
        compose.setContent {
            Composer(
                value = typedCommand.value,
                onValueChange = { typedCommand.value = it },
                onSuggestionSelected = { command ->
                    val selected = "$command "
                    typedCommand.value = TextFieldValue(selected, selection = TextRange(selected.length))
                },
                onSend = {},
                onMenu = {},
            )
        }
        compose.onNodeWithTag("slash_command_/anime").performClick()
        compose.onNodeWithTag("composer_input").performTextInput("search")
        compose.onNodeWithTag("composer_input").assertTextEquals("/anime search")
    }

    @Test fun animeDetailsOfferBeginningPlaybackAndSeasonListActions() {
        val actions = mutableListOf<String>()
        val anime = CatalogItem(
            id = 10, mediaType = "ANIME", title = "Blue Abroad Days", image = "", year = 2024,
            status = "RELEASING", episodes = 37, chapters = null, genres = listOf("Travel", "Drama"),
            summary = "A young woman sets off on a solo journey."
        )
        compose.setContent { SeriesCardMessage(anime) { actions += it } }
        compose.onNodeWithTag("anime_details_card").assertIsDisplayed()
        compose.onNodeWithText("Blue Abroad Days").assertIsDisplayed()
        compose.onNodeWithText("Travel").assertIsDisplayed()
        compose.onNodeWithText("Drama").assertIsDisplayed()
        compose.onNodeWithText(anime.summary).assertIsDisplayed()
        compose.onNodeWithText("Last watched: Not started").assertIsDisplayed()
        compose.onNodeWithTag("anime_action_play").performClick()
        compose.onNodeWithTag("anime_action_seasons").performClick()
        assertEquals(listOf("play", "seasons"), actions)
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
        compose.onNodeWithText("Local chapter · Not started").assertIsDisplayed()
        compose.onNodeWithTag("manga_action_Continue reading").performClick()
        compose.onNodeWithTag("manga_action_Chapters").performClick()
        assertEquals(listOf("reader", "chapters"), actions)
    }

    @Test fun selfMessagesHaveNoRedundantIdentityAndLongPressCopiesText() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            ChatBubble(
                entry = ChatEntry(101, true, "Copied from Annie"),
                onCatalogClick = {}, onActionClick = { _, _ -> }, onOpenSource = {},
                onSeriesAction = { _, _, _ -> },
            )
        }
        compose.onNodeWithText("You", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Y", substring = false).assertDoesNotExist()
        saveEmulatorScreenshot("annie-self-message-no-redundant-identity")
        compose.onNodeWithTag("text_message_bubble").performTouchInput { longClick() }
        compose.waitForIdle()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals("Copied from Annie", clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
    }

    @Test fun localMangaReaderPagesAndRestoresSavedPosition() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val item = manga.copy(id = System.nanoTime().toInt())
        val archive = java.io.File(context.cacheDir, "manga-reader-${item.id}.cbz")
        val pixel = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/Bf0AAAAASUVORK5CYII=",
            android.util.Base64.DEFAULT,
        )
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(archive)).use { zip ->
            listOf("page1.png", "page2.png").forEach { name ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(pixel)
                zip.closeEntry()
            }
        }
        try {
            compose.setContent { AnnieMangaReaderDialog(item, archive) {} }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("1 / 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Next page").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("2 / 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("2 / 2").assertIsDisplayed()
            compose.onNodeWithContentDescription("Previous page").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("1 / 2").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, AnnieMangaProgress.page(context, item.id))
        } finally {
            archive.delete()
        }
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
