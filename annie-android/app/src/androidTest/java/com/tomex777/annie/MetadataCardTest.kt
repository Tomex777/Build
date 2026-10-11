package com.tomex777.annie

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MetadataCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movieDetailsKeepCatalogProvenanceWithoutFakePlaybackActions() {
        var opened = ""
        val item = CatalogItem(
            id = 1,
            mediaType = "MOVIE",
            title = "Inception",
            image = "",
            year = 2010,
            status = "METADATA",
            episodes = null,
            chapters = null,
            summary = "A dream within a dream.",
            runtimeMinutes = 148,
            sourceLabel = "Wikidata",
            sourceUrl = "https://www.wikidata.org/wiki/Q25188"
        )

        compose.setContent { MediaMetadataMessage(item, "Movie") { opened = it } }

        compose.onNodeWithText("Inception").assertExists()
        compose.onNodeWithText("Movie metadata").assertDoesNotExist()
        compose.onNodeWithText("Metadata only", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithText("Download").assertDoesNotExist()
        assertEquals(0, compose.onAllNodesWithText("Open Wikidata record", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("View on Wikidata", substring = false).performClick()
        assertEquals("https://www.wikidata.org/wiki/Q25188", opened)
    }
}
