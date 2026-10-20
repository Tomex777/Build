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
}
