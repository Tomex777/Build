package com.example.whatsapp.presentation.chatscreen

import com.example.whatsapp.data.browser.NightBrowserSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightRichMessageCodecTest {
    @Test
    fun roundTripsAllPersistedLegacyRichTypes() {
        val messages = listOf<RichResultMessage>(
            ButtonResultMessage(
                id = "buttons",
                title = "Choose",
                body = "Pick one",
                actions = listOf(
                    MessageAction("a", "A"),
                    MessageAction("b", "B"),
                ),
                time = "10:00",
            ),
            FileResultMessage(
                id = "file",
                name = "Night.pdf",
                detail = "PDF • 1 MB",
                time = "10:01",
            ),
            AnimeResultMessage(
                id = "anime",
                title = "Solo Leveling",
                episode = "Episode 8",
                quality = "1080p",
                size = "420 MB",
                time = "10:02",
                coverPath = "/tmp/cover.jpg",
                mediaType = "TV",
                status = "Ongoing",
                description = "Description",
                primaryActionLabel = "Play",
            ),
            LinkPreviewMessage(
                id = "link",
                title = "Docs",
                description = "Reference",
                domain = "example.com",
                thumbnailPath = "/tmp/thumb.jpg",
                mine = false,
                time = "10:03",
            ),
            GeneratedImageResultMessage(
                id = "generated",
                title = "Generated",
                detail = "1024x1024",
                localPath = "/tmp/generated.png",
                time = "10:04",
            ),
            CreationResultMessage(
                id = "creation",
                kind = CreationKind.Pdf,
                state = CreationState.Working,
                title = "Creating PDF",
                detail = "Rendering",
                progress = 0.6f,
                localPath = "/tmp/draft.pdf",
                fileName = "draft.pdf",
                time = "10:05",
            ),
            ImageSearchResultMessage(
                id = "images",
                source = "Pinterest",
                resultCount = 12,
                time = "10:06",
            ),
            DownloadResultMessage(
                id = "download",
                title = "Episode 8",
                detail = "184 MB of 428 MB",
                progress = 0.43f,
                time = "10:07",
            ),
            ToolResultMessage(
                id = "tool",
                toolName = "GitHub",
                title = "PR ready",
                subtitle = "Checks passed",
                time = "10:08",
                iconText = "GH",
                actions = listOf(
                    MessageAction("open", "Open"),
                ),
            ),
            BrowserResultMessage(
                id = "browser",
                spec = NightBrowserSpec(
                    sessionId = "test.browser",
                    initialUrl = "https://example.com/start",
                    allowedHosts = listOf("example.com"),
                    title = "Example",
                    verifyActionId = "verify",
                ),
                time = "10:09",
                sourceLabel = "Test",
            ),
        )

        messages.forEach { original ->
            val type = NightRichMessageCodec.typeOf(original)
            assertNotNull(type)
            requireNotNull(type)

            val decoded = NightRichMessageCodec.decode(
                type = type,
                id = original.id,
                text = NightRichMessageCodec.previewText(original),
                payloadJson = NightRichMessageCodec.encodePayload(original),
                time = "11:11",
                mine = false,
            )

            assertNotNull(decoded)
            requireNotNull(decoded)
            assertEquals(original::class, decoded::class)
            assertEquals(original.id, decoded.id)
        }
    }

    @Test
    fun buttonActionsRoundTrip() {
        val original = ButtonResultMessage(
            id = "buttons",
            title = "Mood",
            body = "Choose",
            actions = listOf(
                MessageAction("anime", "Anime"),
                MessageAction("movie", "Movie"),
            ),
            time = "10:00",
        )

        val decoded = NightRichMessageCodec.decode(
            type = NightRichMessageCodec.TYPE_BUTTONS,
            id = original.id,
            text = original.title,
            payloadJson = NightRichMessageCodec.encodePayload(original),
            time = original.time,
            mine = false,
        ) as ButtonResultMessage

        assertEquals(original.actions, decoded.actions)
        assertEquals("Mood", decoded.title)
        assertEquals("Choose", decoded.body)
    }

    @Test
    fun progressValuesAreClampedWhenDecoded() {
        val decoded = NightRichMessageCodec.decode(
            type = NightRichMessageCodec.TYPE_DOWNLOAD,
            id = "download",
            text = "Download",
            payloadJson = """{"title":"Download","detail":"","progress":4.5}""",
            time = "10:00",
            mine = false,
        ) as DownloadResultMessage

        assertEquals(1f, decoded.progress, 0f)
    }

    @Test
    fun unsupportedTypesReturnNull() {
        assertTrue(
            NightRichMessageCodec.decode(
                type = "future_type",
                id = "future",
                text = "Future",
                payloadJson = "{}",
                time = "10:00",
                mine = false,
            ) == null
        )
    }
}
