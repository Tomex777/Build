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
        assertEquals(5, starters.mapNotNull { it.asset?.assetId }.distinct().size)
    }
}
