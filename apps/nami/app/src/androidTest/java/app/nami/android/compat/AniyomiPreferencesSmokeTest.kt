package app.nami.android.compat

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.nami.android.AniyomiSourcePreferencesActivity
import app.nami.android.NamiApplication
import app.nami.compat.aniyomi.AniyomiConfigurableSourceHandle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.lifecycle.Lifecycle
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

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

            var topInset = 0
            scenario.onActivity { activity ->
                topInset = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())
                    ?.top
                    ?: 0
            }
            val firstPreference = device.wait(
                Until.findObject(By.text("Preferred Domain")),
                10_000,
            )
            assertNotNull("AnimeSogo did not render its first source preference", firstPreference)
            assertTrue(
                "Source preferences still overlap the status bar: rowTop=" +
                    firstPreference.visibleBounds.top + " inset=" + topInset,
                firstPreference.visibleBounds.top >= topInset,
            )

            var primaryTextColor = Color.TRANSPARENT
            var secondaryTextColor = Color.TRANSPARENT
            scenario.onActivity { activity ->
                activity.obtainStyledAttributes(
                    intArrayOf(
                        android.R.attr.textColorPrimary,
                        android.R.attr.textColorSecondary,
                    ),
                ).use { attributes ->
                    primaryTextColor = attributes.getColor(0, Color.TRANSPARENT)
                    secondaryTextColor = attributes.getColor(1, Color.TRANSPARENT)
                }
            }
            assertTrue(
                "Source preference primary text is too dark for Nami's dark surface: " +
                    Integer.toHexString(primaryTextColor),
                relativeLuminance(primaryTextColor) >= 0.55,
            )
            assertTrue(
                "Source preference secondary text is too dark for Nami's dark surface: " +
                    Integer.toHexString(secondaryTextColor),
                relativeLuminance(secondaryTextColor) >= 0.30,
            )

            val topScreenshot = File(application.filesDir, "nami-source-preferences-top.png")
            assertTrue(
                "Could not capture source preference top-inset acceptance screenshot",
                device.takeScreenshot(topScreenshot),
            )
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

            var markFillerRow = findPreferenceRow(device, "Mark Filler Episodes")
            assertNotNull(
                "AnimeSogo preference screen did not render Mark Filler Episodes",
                markFillerRow,
            )
            var markFillerSwitch = nearestSwitch(device, markFillerRow!!)
            assertNotNull(
                "Mark Filler Episodes did not render a native checkable switch",
                markFillerSwitch,
            )

            if (!markFillerSwitch!!.isChecked) {
                markFillerRow.click()
                device.waitForIdle()
                SystemClock.sleep(250)
                markFillerSwitch = nearestSwitch(device, markFillerRow)
                assertTrue(
                    "Mark Filler Episodes did not visually switch ON after tapping it",
                    markFillerSwitch?.isChecked == true,
                )
            }

            val beforeTurningOff = preferences.all.toMap()
            markFillerRow.click()
            device.waitForIdle()
            SystemClock.sleep(300)
            markFillerSwitch = nearestSwitch(device, markFillerRow)
            assertFalse(
                "Mark Filler Episodes still reports checked after switching it OFF",
                markFillerSwitch?.isChecked ?: true,
            )
            val afterTurningOff = preferences.all.toMap()
            assertTrue(
                "Switching Mark Filler Episodes OFF did not persist a boolean source preference",
                afterTurningOff.any { (name, value) ->
                    value is Boolean &&
                        value == false &&
                        beforeTurningOff[name] != value
                },
            )

            val screenshot = File(application.filesDir, "nami-source-preferences.png")
            assertTrue(
                "Could not capture source preference acceptance screenshot",
                device.takeScreenshot(screenshot),
            )

            scenario.close()
            scenario = ActivityScenario.launch(intent)
            markFillerRow = findPreferenceRow(device, "Mark Filler Episodes")
            assertNotNull(
                "Mark Filler Episodes disappeared after reopening source settings",
                markFillerRow,
            )
            val reopenedSwitch = nearestSwitch(device, markFillerRow!!)
            assertNotNull(
                "Reopened Mark Filler Episodes did not expose its switch",
                reopenedSwitch,
            )
            assertFalse(
                "Mark Filler Episodes OFF state did not survive reopening source settings",
                reopenedSwitch!!.isChecked,
            )
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

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private fun findPreferenceRow(
        device: UiDevice,
        title: String,
    ): UiObject2? {
        device.findObject(By.text(title))?.let { return it }
        runCatching {
            UiScrollable(UiSelector().scrollable(true))
                .setAsVerticalList()
                .scrollTextIntoView(title)
        }
        return device.wait(Until.findObject(By.text(title)), 10_000)
    }

    private fun nearestSwitch(
        device: UiDevice,
        title: UiObject2,
    ): UiObject2? {
        val centerY = title.visibleBounds.centerY()
        return device.findObjects(By.checkable(true))
            .filter { candidate ->
                candidate.className?.contains("Switch", ignoreCase = true) == true
            }
            .minByOrNull { candidate ->
                abs(candidate.visibleBounds.centerY() - centerY)
            }
            ?.takeIf { candidate ->
                abs(candidate.visibleBounds.centerY() - centerY) <= title.visibleBounds.height() * 2
            }
    }
}
