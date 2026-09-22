package com.example.whatsapp.data.night

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightAppearanceControllerInstrumentedTest {
    @Test
    fun customAccentPersistsAndUnavailableFontsAreRejected() = runBlocking {
        val repository = NightRepository.get(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val original = repository.ensureAppearance()
        val controller = NightAppearanceController(repository)
        try {
            val changed = controller.handleNaturalRequest("Make Night purple: #8B5CF6")
            assertNotNull(changed)
            assertEquals(0xFF8B5CF6L, repository.ensureAppearance().accentColor)

            controller.applyToolAction("accent_color", "#8B5CF6")
            assertEquals(0xFF8B5CF6L, repository.ensureAppearance().accentColor)
            try {
                controller.applyToolAction("accent_color", "#GG12ZZ")
                fail("Invalid custom color was accepted")
            } catch (_: IllegalStateException) {
                assertEquals(0xFF8B5CF6L, repository.ensureAppearance().accentColor)
            }

            val unsupported = controller.handleNaturalRequest("Use the unsupported font family")
            assertNull(unsupported)
            assertEquals(original.fontFamilyKey, repository.ensureAppearance().fontFamilyKey)
        } finally {
            repository.setAppearance(original)
        }
    }
}
