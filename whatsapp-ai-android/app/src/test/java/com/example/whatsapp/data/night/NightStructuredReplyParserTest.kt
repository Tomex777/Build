package com.example.whatsapp.data.night

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightStructuredReplyParserTest {
    @Test
    fun parsesMultiSelectAndSuppressesRepeatedCardText() {
        val parsed = NightStructuredReplyParser.parse(
            """**What type of anime do you enjoy most?**
                |1️⃣ Action/Adventure
                |2️⃣ Comedy
                |3️⃣ Fantasy
                |4️⃣ Sci-Fi
                |Let me know which one feels right for you!
                |NIGHT_OPTIONS:{"title":"What type of anime do you enjoy most?","options":["Action/Adventure","Comedy","Fantasy","Sci-Fi"],"multiple":true}""".trimMargin()
        )

        assertEquals("What type of anime do you enjoy most?", parsed.choice?.title)
        assertEquals(listOf("Action/Adventure", "Comedy", "Fantasy", "Sci-Fi"), parsed.choice?.options)
        assertTrue(parsed.choice?.multiple == true)
        assertEquals("", parsed.text)
    }

    @Test
    fun preservesUsefulExplanationAlongsideChoiceCard() {
        val parsed = NightStructuredReplyParser.parse(
            "Fantasy has the most adventure.\n" +
                "Question\nA\nB\nNIGHT_OPTIONS:{\"title\":\"Question\",\"options\":[\"A\",\"B\"]}"
        )

        assertEquals("Fantasy has the most adventure.", parsed.text)
    }

    @Test
    fun malformedChoiceMarkerRemainsVisibleInsteadOfSilentlyLosingText() {
        val parsed = NightStructuredReplyParser.parse("NIGHT_OPTIONS:{broken}")

        assertNull(parsed.choice)
        assertEquals("NIGHT_OPTIONS:{broken}", parsed.text)
    }
}
