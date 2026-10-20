package studio.artistscene.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.LightSettings
import studio.artistscene.core.LightType

class SceneLightingPolicyTest {
    private fun light(
        type: LightType = LightType.DIRECTIONAL,
        visible: Boolean = true,
        castsShadow: Boolean = true,
    ) = Actor(
        id = "light-$type-$visible-$castsShadow",
        name = "Artist light",
        kind = ActorKind.LIGHT,
        visible = visible,
        light = LightSettings(type = type, castsShadow = castsShadow),
    )

    @Test fun sceneWithNoConfiguredLightingRetainsLegacySun() {
        assertTrue(sceneUsesFallbackSun(emptyList()))
        assertTrue(sceneNeedsShadowMaps(emptyList()))
        assertTrue(sceneUsesFallbackSun(listOf(Actor("prop-1", "Cube", ActorKind.PROP))))
    }

    @Test fun hidingTheOnlyDirectionalLightDoesNotSpawnReplacementSun() {
        val hiddenSun = listOf(light(visible = false))
        assertFalse(sceneUsesFallbackSun(hiddenSun))
        assertFalse(sceneNeedsShadowMaps(hiddenSun))
    }

    @Test fun pointAndSpotOnlyScenesNeverGetAnUnwantedSun() {
        val lights = listOf(light(LightType.POINT), light(LightType.SPOT))
        assertFalse(sceneUsesFallbackSun(lights))
        assertTrue(sceneNeedsShadowMaps(lights))
    }

    @Test fun visibleShadowCasterControlsShadowMaps() {
        assertFalse(sceneNeedsShadowMaps(listOf(light(castsShadow = false))))
        assertFalse(sceneNeedsShadowMaps(listOf(light(LightType.SPOT, visible = false))))
        assertTrue(sceneNeedsShadowMaps(listOf(
            light(LightType.POINT, castsShadow = false),
            light(LightType.SPOT, castsShadow = true),
        )))
    }
}
