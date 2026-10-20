package com.night.browser

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadDuplicatePolicyTest {
    @Test fun sameFileAndOriginWarnEvenWithFreshSignedToken() {
        assertTrue(DownloadDuplicatePolicy.matches(
            "https://example.org/media/song.mp3?token=NEW#play",
            "song.mp3",
            "https://example.org/media/song.mp3?token=OLD",
            "song.mp3"
        ))
    }

    @Test fun sameSourceWithNewDownloadNameWarns() {
        assertTrue(DownloadDuplicatePolicy.matches(
            "https://EXAMPLE.org/files/get?token=new", "renamed.pdf",
            "https://example.org/files/get?token=old", "original.pdf"
        ))
    }

    @Test fun matchingFileNamesAtDifferentWebsitesDoNotCollide() {
        assertFalse(DownloadDuplicatePolicy.matches(
            "https://other.org/files/file.png", "file.png",
            "https://example.org/files/file.png", "file.png"
        ))
    }

    @Test fun sameOriginAndNameWarnEvenFromDifferentPaths() {
        assertTrue(DownloadDuplicatePolicy.matches(
            "https://example.org/download?id=2", "poster.jpg",
            "https://example.org/gallery?id=1", "poster.jpg"
        ))
    }

    @Test fun differentPathsAndNamesOnSameOriginDoNotWarn() {
        assertFalse(DownloadDuplicatePolicy.matches(
            "https://example.org/new.pdf", "new.pdf",
            "https://example.org/old.pdf", "old.pdf"
        ))
    }

    @Test fun localOrUnsafeLinksNeverMatch() {
        assertFalse(DownloadDuplicatePolicy.matches(
            "file:///sdcard/item.png", "item.png",
            "file:///sdcard/item.png", "item.png"
        ))
        assertFalse(DownloadDuplicatePolicy.matches(
            "javascript:alert(1)", "item.png",
            "https://example.org/item.png", "item.png"
        ))
    }

    @Test fun portAndProtocolBoundariesAreNotConflated() {
        assertFalse(DownloadDuplicatePolicy.matches(
            "http://example.org/a", "a",
            "https://example.org/a", "a"
        ))
        assertFalse(DownloadDuplicatePolicy.matches(
            "https://example.org:8001/a", "a",
            "https://example.org:8002/a", "a"
        ))
    }
    @Test fun onlyCompletedAndPresentFilesTriggerThePrompt() {
        val previous = LibraryDownload(
            id = 1L, url = "https://example.org/video.mp4",
            fileName = "video.mp4", mimeType = "video/mp4",
            createdAt = 0L, status = DownloadManager.STATUS_SUCCESSFUL
        )
        val pending = previous.copy(id = 2L, status = DownloadManager.STATUS_RUNNING)
        val failed = previous.copy(id = 3L, status = DownloadManager.STATUS_FAILED)
        val requested = "https://example.org/video.mp4?token=new"
        assertEquals(previous, DownloadDuplicatePolicy.findCompleted(
            requested, "video.mp4", listOf(pending, failed, previous)) { true })
        assertNull(DownloadDuplicatePolicy.findCompleted(
            requested, "video.mp4", listOf(previous)) { false })
        assertNull(DownloadDuplicatePolicy.findCompleted(
            requested, "video.mp4", listOf(pending, failed)) { true })
    }

}
