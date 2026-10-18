package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.ActorKind

class StarterAssetCatalogTest {
    @Test
    fun starterCatalogHasDistinctLicensedCharactersAndProps() {
        val starters = PrototypeScene.starterAssets()
        val characters = starters.filter { it.kind == ActorKind.CHARACTER }
        val props = starters.filter { it.kind == ActorKind.PROP }

        assertTrue(characters.mapNotNull { it.asset?.assetId }.distinct().size >= 2)
        assertTrue(props.mapNotNull { it.asset?.assetId }.distinct().size >= 2)
        assertNotNull(starters.first { it.name == "Rigged Figure" }.asset?.attribution)
        assertTrue(starters.all { !it.asset?.source.isNullOrBlank() && !it.asset?.license.isNullOrBlank() })
        assertEquals(14, starters.mapNotNull { it.asset?.assetId }.distinct().size)
        assertTrue(starters.any { it.asset?.assetId == PrototypeScene.ANIME_ASSET_ID && it.kind == ActorKind.CHARACTER })
        assertEquals(setOf("Bicycle", "Car", "Sedan · Real Scale"), starters.filter {
            it.kind == ActorKind.VEHICLE
        }.map { it.name }.toSet())
        assertEquals(setOf("Tree", "Oak Tree · Detailed Leaves",
            "Two-storey House · Detailed", "Bungalow · Detailed"),
            starters.filter { it.kind == ActorKind.ENVIRONMENT }.map { it.name }.toSet())
    }
}
