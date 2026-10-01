package app.mira.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiraExtensionAbiTest {
    @Test fun separatelyInstalledMiraApkLoadsAndNamiApkIsExcluded() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sources = MiraNativeExtensionRegistry(context).installedSources()
        val fixture = sources.single { it.metadata.id == "app.mira.extension.fixture:movies" }
        assertEquals("app.mira.extension.fixture", fixture.metadata.extensionPackage)
        assertEquals(1, fixture.metadata.extensionApiVersion)
        assertEquals("Mira fixture movie", fixture.search("movie").items.single().title)
        assertTrue(sources.none { it.metadata.extensionPackage?.startsWith("app.nami") == true })
        val store = MiraSourceEnablementStore(context)
        store.setEnabled(fixture.metadata.id, false)
        assertFalse(MiraSourceEnablementStore(context).isEnabled(fixture.metadata.id))
        store.setEnabled(fixture.metadata.id, true)
    }
}
