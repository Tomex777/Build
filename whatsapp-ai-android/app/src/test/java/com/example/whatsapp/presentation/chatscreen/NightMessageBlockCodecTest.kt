package com.example.whatsapp.presentation.chatscreen

import com.example.whatsapp.extensions.messages.ExtensionCardTemplate
import com.example.whatsapp.extensions.messages.ExtensionMessageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightMessageBlockCodecTest {
    @Test
    fun roundTripsEveryStructuredBlockFamily() {
        val blocks = listOf<NightMessageBlock>(
            NightTextBlock("text", "Body", "Title"),
            NightCodeBlock("code", "println(1)", "kotlin", "Fix"),
            NightCopyBlock("copy", "adb devices", "Copy", "Command"),
            NightTableBlock(
                "table",
                columns = listOf("A", "B"),
                rows = listOf(listOf("1", "2")),
                title = "Table",
            ),
            NightProgressBlock(
                "progress",
                title = "Download",
                detail = "50%",
                progress = 0.5f,
                state = NightProgressState.Paused,
                primaryActionId = "resume",
                primaryActionLabel = "Resume",
            ),
            NightLevelBlock(
                "level",
                title = "Researcher",
                level = 12,
                currentXp = 760,
                nextLevelXp = 1000,
                rank = "Gold",
                detail = "240 XP until Level 13",
                badgeText = "12",
                action = NightBlockAction("details", "Details"),
            ),
            NightToolBlock(
                "tool",
                toolName = "GitHub",
                title = "PR ready",
                actions = listOf(
                    NightBlockAction(
                        "open",
                        "Open",
                        NightBlockActionStyle.Primary,
                    )
                ),
            ),
            NightErrorBlock("error", "Failed", "Try again", "retry"),
            NightSourcesBlock(
                "sources",
                sources = listOf(
                    NightSourceItem("1", "Docs", "Official", "https://example.com")
                ),
            ),
            NightConfirmationBlock(
                "confirm",
                title = "Continue?",
                confirmAction = NightBlockAction("yes", "Yes"),
                cancelAction = NightBlockAction("no", "No"),
                destructive = true,
            ),
            NightPermissionBlock(
                "permission",
                title = "Allow?",
                options = listOf(
                    NightPermissionOption("once", "Once", selected = true)
                ),
                destructiveConfirmationRequired = true,
            ),
            NightQuestionBlock(
                "question",
                title = "Pick",
                options = listOf(
                    NightQuestionOption("a", "A", selected = true)
                ),
                multiple = true,
            ),
            NightDiffBlock("diff", before = "-old", after = "+new"),
            NightConnectionBlock(
                "connection",
                service = "Notion",
                title = "Connected",
                connected = true,
                action = NightBlockAction("manage", "Manage"),
            ),
            NightExtensionBlock(
                "extension",
                snapshot = ExtensionMessageSnapshot(
                    extensionId = "notion",
                    messageType = "notion.page",
                    template = ExtensionCardTemplate.Content,
                    extensionName = "Notion",
                    title = "Project Night",
                ),
            ),
        )

        val decoded = NightMessageBlockCodec.decode(
            NightMessageBlockCodec.encode(blocks)
        )

        assertEquals(blocks.size, decoded.size)
        assertTrue(decoded[0] is NightTextBlock)
        assertTrue(decoded[1] is NightCodeBlock)
        assertTrue(decoded[2] is NightCopyBlock)
        assertTrue(decoded[3] is NightTableBlock)
        assertTrue(decoded[4] is NightProgressBlock)
        assertTrue(decoded[5] is NightLevelBlock)
        assertTrue(decoded[6] is NightToolBlock)
        assertTrue(decoded[7] is NightErrorBlock)
        assertTrue(decoded[8] is NightSourcesBlock)
        assertTrue(decoded[9] is NightConfirmationBlock)
        assertTrue(decoded[10] is NightPermissionBlock)
        assertTrue(decoded[11] is NightQuestionBlock)
        assertTrue(decoded[12] is NightDiffBlock)
        assertTrue(decoded[13] is NightConnectionBlock)
        assertTrue(decoded[14] is NightExtensionBlock)

        val progress = decoded[4] as NightProgressBlock
        assertEquals(NightProgressState.Paused, progress.state)
        assertEquals(0.5f, progress.progress ?: -1f, 0f)

        val level = decoded[5] as NightLevelBlock
        assertEquals(12, level.level)
        assertEquals(760L, level.currentXp)
        assertEquals(1000L, level.nextLevelXp)
        assertEquals(0.76f, level.progress, 0f)

        val extension = decoded[14] as NightExtensionBlock
        assertEquals("notion.page", extension.snapshot.messageType)
    }

    @Test
    fun unknownBlockTypesAreIgnoredWithoutDroppingValidBlocks() {
        val raw = """
            {
              "schemaVersion": 1,
              "blocks": [
                {"type":"future_block","blockId":"future"},
                {"type":"text","blockId":"text","text":"Still visible","title":""}
              ]
            }
        """.trimIndent()

        val decoded = NightMessageBlockCodec.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals("Still visible", (decoded.single() as NightTextBlock).text)
    }

    @Test
    fun previewTextUsesMeaningfulFirstBlockTitle() {
        assertEquals(
            "Download episode 8",
            NightMessageBlockCodec.previewText(
                listOf(
                    NightProgressBlock(
                        blockId = "progress",
                        title = "Download episode 8",
                    )
                )
            )
        )
    }
}
