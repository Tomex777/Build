package app.nami.source.kayoanime

import app.nami.source.NamiAnimeSource
import app.nami.source.NamiExtensionHost
import app.nami.source.NamiExtensionProvider

class KayoAnimeExtensionProvider : NamiExtensionProvider {
    override val extensionId: String = "app.nami.source.kayoanime"
    override val displayName: String = "KayoAnime"

    override fun sources(host: NamiExtensionHost): List<NamiAnimeSource> =
        listOf(KayoAnimeSource())
}
