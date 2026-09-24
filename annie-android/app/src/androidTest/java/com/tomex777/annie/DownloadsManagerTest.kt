package com.tomex777.annie

import androidx.compose.runtime.mutableStateListOf
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
        compose.setContent { DownloadsManagerContent(items, onRemove = {}, onStateChange = { _, _ -> }) }
        compose.onNodeWithText("2 of 247 chapters available offline").assertExists()
        compose.onNodeWithText("1 of 12 episodes available offline").assertExists()
        compose.onNodeWithTag("download_filter_Manga").performClick()
        compose.onNodeWithText("The Greatest Estate Developer").assertExists()
        compose.onNodeWithText("Example Anime").assertDoesNotExist()
    }

    @Test fun partialUnitsAreNotReportedAsWholeTitleDownloaded() {
        compose.setContent { DownloadsManagerContent(items, onRemove = {}, onStateChange = { _, _ -> }) }
        compose.onNodeWithText("2 of 247 chapters available offline").assertExists()
        compose.onNodeWithText("Downloaded", substring = true).assertDoesNotExist()
    }
    @Test fun downloadingRowCanPauseAndResume() {
        val currentItems = mutableStateListOf(
            DownloadItem("active", "manga:4", "src-a", "Source A", DownloadMediaKind.MANGA, "Queued Manga",
                unitTitle = "Chapter 3", unitNumber = "3", state = DownloadState.DOWNLOADING, progress = .45f, batchTotal = 20)
        )
        compose.setContent {
            DownloadsManagerContent(
                items = currentItems,
                onRemove = { item -> currentItems.removeAll { it.id == item.id } },
                onStateChange = { item, state ->
                    val index = currentItems.indexOfFirst { it.id == item.id }
                    if (index >= 0) currentItems[index] = currentItems[index].copy(state = state)
                },
            )
        }
        compose.onNodeWithText("Queued Manga").performClick()
        compose.onNodeWithTag("download_action_pause").performClick()
        compose.onNodeWithText("Paused").assertExists()
        compose.onNodeWithTag("download_action_resume").assertExists()
    }

    @Test fun statusFilterShowsPartialCatalogCount() {
        compose.setContent { DownloadsManagerContent(items, onRemove = {}, onStateChange = { _, _ -> }) }
        compose.onNodeWithTag("download_status_Downloaded").performClick()
        compose.onNodeWithText("2 of 247 chapters available offline").assertExists()
        compose.onNodeWithText("Example Series").assertDoesNotExist()
    }

}
