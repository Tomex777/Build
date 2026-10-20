package com.night.tachymns

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read actual offline JSON assets on an Android device. No network or audio
 * permission is involved. A numbered first line is NOT a complete hymn.
 */
@RunWith(AndroidJUnit4::class)
class HymnTextInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun redemptionAndAkokunAreIndependentlyNumberedAndSearchableOffline() {
        val index = IndexRepository.load(context)
        val redemption = index.filter { it.collection == "main" }
        val akokun = index.filter { it.collection == "akokun" }

        assertEquals(800, redemption.size)
        assertEquals((1..800).toList(), redemption.map { it.number })
        assertEquals(299, akokun.size)
        assertEquals(305, akokun.last().number)
        assertNotEquals(redemption.first().key, akokun.first().key)
        assertTrue(redemption.first().matches("Lord of all being"))
        assertTrue(akokun.first().matches("1"))
    }

    @Test
    fun yorubaRedemptionDraftAndEnglishTranslationDisplayAsRealVerses() {
        val hymns = HymnRepository.load(context)
        val fourth = hymns.single { it.collection == "main" && it.number == 4 }

        assertTrue(fourth.hasYoruba)
        assertTrue(fourth.hasEnglish)
        assertEquals("Níwájú ìtẹ́ àánú", fourth.versesYo.first().first())
        assertTrue(fourth.matches("aanu"))
        assertFalse(fourth.versesYo.first().first().contains("\\n"))
    }

    @Test
    fun fullEnglishIndexNeverPassesForVerifiedBilingualLyrics() {
        val hymns = HymnRepository.load(context)
        val englishOnly = hymns.single { it.collection == "main" && it.number == 190 }
        val indexedOnly = IndexRepository.load(context).single {
            it.collection == "main" && it.number == 800
        }

        assertEquals(25, hymns.size)
        assertEquals(18, hymns.count { it.hasYoruba })
        assertEquals(7, hymns.count { !it.hasYoruba && it.hasEnglish })
        assertFalse(englishOnly.hasYoruba)
        assertTrue(englishOnly.hasEnglish)
        assertTrue(englishOnly.textStatus.contains("needs_edition_review"))
        assertNotNull(indexedOnly.titleEn)
        assertFalse(hymns.any { it.collection == "main" && it.number == 800 })
    }
    @Test
    fun userSuppliedEightHundredHymnPackCanBeReadEntirelyOffline() {
        val entries = org.json.JSONArray()
        repeat(800) { offset ->
            val number = offset + 1
            entries.put(
                org.json.JSONObject()
                    .put("number", number)
                    .put("title_yo", "Àpẹẹrẹ Yorùbá $number")
                    .put(
                        "verses_yo",
                        org.json.JSONArray().put(
                            org.json.JSONArray().put("Àpẹẹrẹ ìlà orin $number")
                        )
                    )
            )
        }
        val json = org.json.JSONObject()
            .put("schema_version", 1)
            .put("collection", "main")
            .put("hymns", entries)
            .toString()
        val pack = YorubaHymnPack.parse(json)
        assertEquals(800, pack.size)
        val index = IndexRepository.load(context)
        val combined = YorubaHymnPack.combine(HymnRepository.load(context), index, pack)
        val redemption = combined.filter { it.collection == "main" }
        assertEquals(800, redemption.size)
        assertEquals((1..800).toList(), redemption.map { it.number })
        assertEquals(800, redemption.count { it.hasYoruba })
        assertEquals("Àpẹẹrẹ ìlà orin 800", redemption.last().versesYo[0][0])
        assertEquals(299, YorubaHymnPack.overlayIndex(index, pack).count { it.collection == "akokun" })
    }

    @Test
    fun importedYorubaPackRejectsInvalidNumbersAndBlankLyrics() {
        val sample = """
            {
              "schema_version": 1, "collection": "main",
              "hymns": [
                {"number": 25, "title_yo": "Ògo", "verses_yo": [["Ìyìn"]]}
              ]
            }
        """.trimIndent()
        assertEquals(1, YorubaHymnPack.parse(sample).size)
        val duplicate = sample.replace(
            "\"number\": 25, \"title_yo\"",
            "\"number\": 25, \"title_yo\""
        ).replace(
            "\"verses_yo\": [[\"Ìyìn\"]]}",
            "\"verses_yo\": [[\"Ìyìn\"]]}, {\"number\": 25, \"title_yo\": \"Ògo\", \"verses_yo\": [[\"Ìyìn\"]]}"
        )
        assertThrowsInvalid { YorubaHymnPack.parse(duplicate) }
        assertThrowsInvalid {
            YorubaHymnPack.parse(sample.replace("\"number\": 25", "\"number\": 801"))
        }
        assertThrowsInvalid {
            YorubaHymnPack.parse(sample.replace("[[\"Ìyìn\"]]", "[]"))
        }
        assertThrowsInvalid {
            YorubaHymnPack.parse(sample.replace("\"collection\": \"main\"", "\"collection\": \"akokun\""))
        }
    }

    private fun assertThrowsInvalid(action: () -> Unit) {
        try {
            action()
            fail("Bad hymn import should have been rejected")
        } catch (expected: IllegalArgumentException) {
            // IllegalArgumentException is thrown by input-validation require(...).
        }
    }

}
