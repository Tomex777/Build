package app.nami.android.compat

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.android.NamiApplication
import app.nami.source.SourceOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the signer-matched, minified production APK in CI.
 *
 * Installed extensions execute against classes from the host app classloader, so this test
 * deliberately exercises constructor/default-argument and Kotlin-runtime ABI that R8 could
 * otherwise remove while leaving the launcher UI apparently healthy.
 */
@RunWith(AndroidJUnit4::class)
class NamiReleaseExtensionAbiTest {

    @Test
    fun installedNativeExtensionRunsAgainstMinifiedHost() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<NamiApplication>()
        app.installedSourceRegistry.invalidate()

        val source = app.installedSourceRegistry.installedSources().firstOrNull {
            it.metadata.extensionPackage == "app.nami.fixture.nativeextension"
        }
        assertNotNull("Installed Nami extension was not loadable in the production APK", source)
        source!!

        assertEquals(SourceOrigin.NATIVE_NAMI, source.metadata.origin)
        val search = source.search("Nami", page = 1)
        assertTrue("Installed Nami extension returned no search result", search.items.isNotEmpty())

        val item = search.items.first()
        val details = source.details(item.ref, item.sourceState)
        assertTrue("Installed Nami extension returned an empty title", details.title.isNotBlank())

        val episodes = source.episodes(details.ref, details.sourceState)
        assertTrue("Installed Nami extension returned no episodes", episodes.isNotEmpty())

        val media = source.resolve(episodes.first().ref, episodes.first().sourceState)
        assertTrue("Installed Nami extension returned no playable media", media.isNotEmpty())
        assertTrue(
            "Installed Nami extension returned an invalid media URL",
            media.first().url.isNotBlank(),
        )
    }
}
