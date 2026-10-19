package eu.kanade.tachiyomi.data.updater

import eu.kanade.tachiyomi.BuildConfig
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Fork safety: Torri must never offer the official Mihon APK as its own update.
 */
class TorriUpdateIdentityTest {

    @Test
    fun `stable update checks use Torri releases`() {
        GITHUB_REPO shouldBe "Tomex777/Torri"
        RELEASE_URL.startsWith("https://github.com/Tomex777/Torri/releases/tag/") shouldBe true
    }

    @Test
    fun `development builds retain the separate Torri package identity`() {
        BuildConfig.APPLICATION_ID shouldBe "app.torri.dev"
    }
}
