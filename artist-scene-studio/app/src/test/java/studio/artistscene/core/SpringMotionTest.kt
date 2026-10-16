package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpringMotionTest {
    private fun integrate(
        spring: SpringMotion, gravity: Vec3, steps: Int = 120,
        wind: Float = 0f, pivot: (Int) -> Vec3 = { Vec3() },
    ): Vec3 {
        var last = Vec3()
        for (i in 0 until steps) {
            last = spring.advance(1f / 60f, gravity, pivot(i), gravityPower = 1f, wind = wind)
        }
        return last
    }

    @Test fun gravityPullsSidewaysStrandsDownWithoutUnboundedRotation() {
        val spring = SpringMotion(Vec3(1f, 0f, 0f), stiffness = .45f, drag = .4f)
        val end = integrate(spring, Vec3(0f, -1f, 0f))
        assertTrue("Gravity must deflect an actual horizontal hair root", abs(end.z) > 8f)
        assertTrue("Hair must not spin through the scalp", abs(end.z) <= 28f)
    }

    @Test fun naturallyHangingHairRestsUntilWindOrBodyMovement() {
        val still = SpringMotion(Vec3(0f, -1f, 0f))
        assertEquals(Vec3(), integrate(still, Vec3(0f, -1f, 0f)))
        val breezy = SpringMotion(Vec3(0f, -1f, 0f))
        val withWind = integrate(breezy, Vec3(0f, -1f, 0f), wind = 1f)
        assertTrue(abs(withWind.x) + abs(withWind.z) > .05f)
    }

    @Test fun stopWindCausesDampedRecoveryToRest() {
        val spring = SpringMotion(Vec3(0f, -1f, 0f))
        integrate(spring, Vec3(0f, -1f, 0f), wind = 1.5f)
        val rest = integrate(spring, Vec3(0f, -1f, 0f), steps = 300)
        assertTrue(abs(rest.x) < .5f && abs(rest.z) < .5f)
    }

    @Test fun gravityIsZeroWhenArtistTurnsOffStrength() {
        val spring = SpringMotion(Vec3(1f, 0f, 0f))
        val result = (0 until 180).fold(Vec3()) { _, _ ->
            spring.advance(1f / 60f, Vec3(0f, -1f, 0f), Vec3(), gravityPower = 0f)
        }
        assertEquals(Vec3(), result)
    }

    @Test fun discontinuousFrameAndResetDoNotExplode() {
        val spring = SpringMotion(Vec3(.1f, -.2f, 0f))
        integrate(spring, Vec3(0f, -1f, 0f))
        val held = spring.advance(Float.NaN, Vec3(0f, -1f, 0f), Vec3())
        assertTrue(held.x.isFinite() && held.z.isFinite())
        spring.reset()
        assertEquals(Vec3(), spring.advance(0f, Vec3(), Vec3()))
    }

    @Test fun poseAppearanceSettingsRoundTripAndKeepHairStylesOnEdit() {
        val actor = Actor(
            id = "anime", name = "Avatar", kind = ActorKind.CHARACTER,
            asset = AssetReference("starter.mise.anime-figure", "models/mise_anime.glb"),
        )
        val start = SceneEditorState(SceneProject(id = "hair-tests", name = "Hair", actors = listOf(actor)))
        val edited = start.setAnimeHairTint("#b4c7df").setAnimeHairPhysics(
            enabled = false, gravity = 1.75f, wind = .5f, fabricEnabled = false,
        )
        assertEquals("#B4C7DF", edited.selectedActor!!.appearance.animeHairTintHex)
        assertEquals(1.75f, edited.selectedActor!!.appearance.hairGravityStrength)
        assertEquals(.5f, edited.selectedActor!!.appearance.hairWindStrength)
        assertFalse(edited.selectedActor!!.appearance.hairPhysicsEnabled)
        val saved = SceneProjectCodec.decode(SceneProjectCodec.encode(edited.project))
        assertEquals(edited.project, saved)
        assertTrue(edited.undo().canUndo)
        assertEquals(start, start.setAnimeHairTint("not-a-color"))
        assertEquals(start, start.setAnimeHairPhysics(gravity = Float.POSITIVE_INFINITY))
        val noneSelected = start.selectActor(null)
        assertEquals(noneSelected, noneSelected.setAnimeHairPhysics(enabled = false))
    }
}
