package app.mira.extension.fixture

import app.mira.source.*
import app.mira.domain.*

class SampleMiraExtensionProvider : MiraExtensionProvider {
    override val extensionId = "app.mira.extension.fixture"
    override val displayName = "Mira Sample Extension"
    override fun sources(host: MiraExtensionHost): List<MiraSource> = listOf(object : MiraSource {
        override val metadata = SourceMetadata(
            id = "$extensionId:movies",
            name = "Mira Fixture Movies",
            capabilities = SourceCapabilities(movies = true),
        )
        private val ref = ContentRef(metadata.id, "movie", ContentKind.MOVIE)
        override suspend fun search(query: String, page: Int) = SourcePage(
            listOf(ContentSearchResult(ref, "Mira fixture movie")), false,
        )
        override suspend fun details(content: ContentRef, sourceState: String?) =
            ContentDetails(content, "Mira fixture movie")
    })
}
