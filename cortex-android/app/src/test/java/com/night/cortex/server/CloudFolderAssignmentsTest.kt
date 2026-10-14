package com.night.cortex.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFolderAssignmentsTest {
    private val mounted = listOf(
        CloudMount("videoZZZ", "Anime & TV"),
        CloudMount("pictureX", "Art & Stickers"),
        CloudMount("videoAAA", "Movies"),
        CloudMount("videoBBB", "Series"),
        CloudMount("otherXXX", "Other unrelated share"),
    )
    private val selected = setOf("videoZZZ", "videoAAA", "videoBBB", "pictureX")

    @Test fun explicitlySelectedImagesAndThreeVideos() {
        val rows = planCloudFolders(mounted, selected, "pictureX")
        assertEquals(4, rows.size)
        assertEquals(3, rows.count { it.mediaTypes == listOf("videos") })
        assertEquals(1, rows.count { it.mediaTypes == listOf("images", "stickers") })
        assertEquals("pictureX", rows.single { it.id == "images" }.nodeId)
        assertEquals("Art & Stickers", rows.single { it.id == "images" }.label)
        assertTrue(rows.none { it.nodeId == "otherXXX" })
    }

    @Test fun rejectsWrongCountOrUnknownImageTarget() {
        assertFails { planCloudFolders(mounted, setOf("videoAAA", "videoBBB", "pictureX"), "pictureX") }
        assertFails { planCloudFolders(mounted, selected, "otherXXX") }
        assertFails { planCloudFolders(mounted, selected, null) }
    }

    @Test fun doesNotTrustUnknownOrDuplicateFolderIds() {
        assertFails {
            planCloudFolders(mounted, setOf("videoZZZ", "videoAAA", "pictureX", "bogusXXX"), "pictureX")
        }
        assertFails {
            planCloudFolders(mounted + CloudMount("videoAAA", "Duplicate"), selected, "pictureX")
        }
    }

    private fun assertFails(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected invalid folder layout to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.isNotBlank() == true)
        }
    }
}
