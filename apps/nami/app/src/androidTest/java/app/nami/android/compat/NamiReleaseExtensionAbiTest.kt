package app.nami.android.compat

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.android.NamiApplication
import app.nami.source.SourceOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

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
    fun installedNativeExtensionRunsAgainstMinifiedHost() = runSuspendTest {
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

    /**
     * Avoids depending on kotlinx.coroutines from the signer-matched test APK. Android Gradle
     * deduplicates dependencies that also exist in the target app, while R8 is free to remove
     * production-unused helpers such as runBlocking. This keeps the production proof focused on
     * Nami's actual suspend ABI instead of forcing test-only coroutine entry points into release.
     */
    private fun runSuspendTest(block: suspend () -> Unit) {
        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        block.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    failure = result.exceptionOrNull()
                    completed.countDown()
                }
            },
        )
        assertTrue(
            "Timed out while exercising the installed extension suspend ABI",
            completed.await(30, TimeUnit.SECONDS),
        )
        failure?.let { throw it }
    }
}
