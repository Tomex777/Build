package com.tomex777.annie

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MetadataCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movieDetailsIdentifyMetadataAndOpenItsCatalogRecordWithoutFakePlaybackActions() {
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
        compose.onNodeWithText("Metadata only · Wikidata. Playback and downloads need a connected media source.").assertExists()
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Open Wikidata record  ›").performClick()
        assertEquals("https://www.wikidata.org/wiki/Q25188", opened)
    }
}
