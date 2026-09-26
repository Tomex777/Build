package app.nami.android.compat

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import app.nami.android.AniyomiSourcePreferencesActivity
import app.nami.android.NamiApplication
import app.nami.compat.aniyomi.AniyomiConfigurableSourceHandle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.lifecycle.Lifecycle
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AniyomiPreferencesSmokeTest {

    @Test
    fun realAnimeSogoPreferenceDialogOpensAndPersistsToSourceStore() {
        val application = ApplicationProvider.getApplicationContext<NamiApplication>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val source = runBlocking {
            application.installedSourceRegistry
                .installedSources()
                .filter { it.metadata.name == "AnimeSogo" && it.metadata.capabilities.configurable }
                .maxByOrNull { it.metadata.extensionApiVersion ?: 0 }
        } ?: throw AssertionError("A configurable real AnimeSogo source was not installed")

        val handle = source as? AniyomiConfigurableSourceHandle
            ?: throw AssertionError("AnimeSogo did not expose Nami's configurable-source handle")

        val preferences = application.getSharedPreferences(
            handle.preferenceName(),
            Context.MODE_PRIVATE,
        )
        val key = "preferred_quality"
        val originalValues = preferences.all.toMap()

        var scenario: ActivityScenario<AniyomiSourcePreferencesActivity>? = null
        try {
            val intent = Intent(application, AniyomiSourcePreferencesActivity::class.java)
                .putExtra(AniyomiSourcePreferencesActivity.EXTRA_SOURCE_ID, source.metadata.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            scenario = ActivityScenario.launch(intent)

            val qualityRow = device.wait(
                Until.findObject(By.text("Preferred Quality")),
                30_000,
            )
            assertNotNull("AnimeSogo preference screen did not render Preferred Quality", qualityRow)
            qualityRow.click()

            val quality720 = device.wait(
                Until.findObject(By.text("720p")),
                15_000,
            )
            assertNotNull("Tapping Preferred Quality did not open its ListPreference dialog", quality720)
            quality720.click()

            device.waitForIdle()
            SystemClock.sleep(500)

            assertTrue(
                "Nami's source-settings activity died after selecting a preference",
                device.hasObject(By.text("Preferred Quality")),
            )
            assertEquals(
                "AnimeSogo did not receive the preference value selected in Nami",
                "720",
                preferences.getString(key, null),
            )

            val reportedPreferenceTitles = listOf(
                "Preferred Domain",
                "Preferred Title Language",
                "Preferred Server",
                "Preferred Type",
                "Score Display Position",
                "Exclude Servers",
                "Exclude Types",
            )
            reportedPreferenceTitles.forEach { title ->
                var row = device.wait(
                    Until.findObject(By.text(title)),
                    2_000,
                )
                if (row == null) {
                    runCatching {
                        UiScrollable(UiSelector().scrollable(true))
                            .setAsVerticalList()
                            .scrollTextIntoView(title)
                    }
                    row = device.wait(
                        Until.findObject(By.text(title)),
                        10_000,
                    )
                }
                assertNotNull(
                    "Real AnimeSogo preference '$title' was not rendered",
                    row,
                )
                row.click()
                device.waitForIdle()
                SystemClock.sleep(350)

                scenario.onActivity { activity ->
                    assertTrue(
                        "Nami's source-settings activity died after tapping '$title'",
                        !activity.isFinishing && !activity.isDestroyed,
                    )
                }
                assertTrue(
                    "Nami left the source preference activity after tapping '$title'",
                    scenario.state != Lifecycle.State.DESTROYED,
                )

                val dialogVisible =
                    device.hasObject(By.res("android", "button1")) ||
                        device.hasObject(By.res("android", "button2")) ||
                        device.hasObject(By.clazz("android.widget.ListView"))
                if (dialogVisible) {
                    device.pressBack()
                    device.waitForIdle()
                    SystemClock.sleep(200)
                }

                assertTrue(
                    "AnimeSogo preference screen did not survive '$title'",
                    device.hasObject(By.text(title)),
                )
            }
        } finally {
            val editor = preferences.edit().clear()
            originalValues.forEach { (name, value) ->
                when (value) {
                    is String -> editor.putString(name, value)
                    is Boolean -> editor.putBoolean(name, value)
                    is Int -> editor.putInt(name, value)
                    is Long -> editor.putLong(name, value)
                    is Float -> editor.putFloat(name, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(name, value.filterIsInstance<String>().toSet())
                    }
                }
            }
            editor.commit()
            scenario?.close()
        }
    }
}
