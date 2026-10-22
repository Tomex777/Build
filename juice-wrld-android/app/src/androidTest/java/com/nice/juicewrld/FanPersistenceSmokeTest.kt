package com.nice.juicewrld

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real SharedPreferences round trip through two separate ViewModel instances. */
@RunWith(AndroidJUnit4::class)
class FanPersistenceSmokeTest {
    @Test fun favoritesAndPrivateMemoriesSurvivePlayerRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val prefs = app.getSharedPreferences("juice-fan", Context.MODE_PRIVATE)
        val oldFavorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
        val oldMemory = prefs.getString("memory:lucid-dreams", null)
        val chosenSong = "lucid-dreams"
        try {
            // Deliberately isolate this test from the user's / other tests' data.
            prefs.edit().putStringSet("favorites", emptySet())
                .remove("memory:" + chosenSong).commit()
            lateinit var first: FanPlayer
            lateinit var recreated: FanPlayer
            instrumentation.runOnMainSync {
                first = FanPlayer(app)
                first.toggleFavorite(chosenSong)
                first.saveMemory(chosenSong, "This song reminds me of a summer drive.")
                recreated = FanPlayer(app)
            }
            assertTrue("Favorite was lost on ViewModel recreation",
                chosenSong in recreated.state.value.favorites)
            assertEquals("This song reminds me of a summer drive.",
                recreated.state.value.memories[chosenSong])
        } finally {
            val edit = prefs.edit().putStringSet("favorites", oldFavorites)
            if (oldMemory == null) edit.remove("memory:" + chosenSong)
            else edit.putString("memory:" + chosenSong, oldMemory)
            edit.commit()
        }
    }
}
