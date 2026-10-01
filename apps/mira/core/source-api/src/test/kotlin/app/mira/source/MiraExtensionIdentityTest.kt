package app.mira.source

import kotlin.test.*

class MiraExtensionIdentityTest {
    @Test fun rejectsForeignPackagesAndUnscopedSources() {
        assertFalse(MiraExtensionIdentity.acceptsPackage("app.nami.extension.fixture"))
        assertFalse(MiraExtensionIdentity.acceptsPackage("app.mira.extension"))
        assertFalse(MiraExtensionIdentity.acceptsSource("app.mira.extension.fixture", "tvmaze"))
        assertFalse(MiraExtensionIdentity.acceptsSource("app.mira.extension.fixture", "app.nami.extension.fixture:movies"))
        assertTrue(MiraExtensionIdentity.acceptsSource("app.mira.extension.fixture", "app.mira.extension.fixture:movies"))
        assertEquals("app.mira.extension", MiraExtensionManifest.FEATURE)
    }
}
