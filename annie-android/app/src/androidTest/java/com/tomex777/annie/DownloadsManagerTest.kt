package com.tomex777.annie

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class DownloadsManagerTest {
    @get:Rule val compose = createComposeRule()

    private val items = listOf(
        DownloadItem("m1", "manga:1", "src-a", "Source A", DownloadMediaKind.MANGA, "The Greatest Estate Developer",
            unitTitle = "Chapter 1", unitNumber = "1", state = DownloadState.COMPLETE, batchTotal = 20, catalogTotal = 247),
        DownloadItem("m2", "manga:1", "src-a", "Source A", DownloadMediaKind.MANGA, "The Greatest Estate Developer",
            unitTitle = "Chapter 2", unitNumber = "2", state = DownloadState.COMPLETE, batchTotal = 20, catalogTotal = 247),
        DownloadItem("a1", "anime:2", "src-b", "Source B", DownloadMediaKind.ANIME, "Example Anime",
            unitTitle = "Episode 2", unitNumber = "2", state = DownloadState.COMPLETE, catalogTotal = 12),
        DownloadItem("t1", "tv:3", "src-c", "Source C", DownloadMediaKind.TV, "Example Series",
            unitTitle = "Episode 1", unitNumber = "1", state = DownloadState.QUEUED, catalogTotal = 8),
    )

    @Test fun groupsShowPartialAvailabilityAndFilterByMedia() {
        compose.setContent { DownloadsManagerContent(items, onRemove = {}) }
        compose.onNodeWithText("2 of 247 chapters available offline").assertExists()
        compose.onNodeWithText("1 of 12 episodes available offline").assertExists()
        compose.onNodeWithTag("download_filter_Manga").performClick()
        compose.onNodeWithText("The Greatest Estate Developer").assertExists()
        compose.onNodeWithText("Example Anime").assertDoesNotExist()
    }

    @Test fun partialUnitsAreNotReportedAsWholeTitleDownloaded() {
        compose.setContent { DownloadsManagerContent(items, onRemove = {}) }
        compose.onNodeWithText("2 of 247 chapters available offline").assertExists()
        compose.onNodeWithText("Downloaded", substring = true).assertDoesNotExist()
    }
}
