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

    @Test fun breezeDeflectsHangingHairEvenWhenGravityIsDisabled() {
        val spring = SpringMotion(Vec3(0f, -1f, 0f), stiffness = .4f, drag = .4f)
        var maxTilt = 0f
        repeat(180) {
            val sample = spring.advance(
                dtSeconds = 1f / 30f,
                gravityLocal = Vec3(0f, -1f, 0f),
                pivotWorld = Vec3(),
                gravityPower = 0f,
                wind = 2f,
            )
            maxTilt = maxOf(maxTilt, abs(sample.x) + abs(sample.z))
        }
        assertTrue(
            "Disabling Gravity must not also disable the independent Breeze slider",
            maxTilt > .5f,
        )
        // With both sliders off and a stationary head, no phantom force.
        val settled = SpringMotion(Vec3(0f, -1f, 0f))
        assertEquals(Vec3(), integrate(settled, Vec3(0f, -1f, 0f), wind = 0f))
    }

    @Test fun realFemaleVrmSpringTipsDeformAtBudgetPhoneFrameRates() {
        // Authored directions from the BUNDLED HairSample_Female spring manifest,
        // rather than a synthetic sideways test strand. These exact joints bind
        // to weighted Hair001 vertices in the packaged real glTF skin.
        val tips = listOf(
            Vec3(-.07681452f, -.12791419f, .02509075f),
            Vec3(.04479633f, .05421329f, .00103015f),
            Vec3(-.00891432f, -.06463218f, -.00434126f),
            Vec3(.00434746f, -.04984069f, .00411876f),
            Vec3(-.00298315f, -.05570221f, .00789168f),
            Vec3(.0799448f, -.12631178f, .02506085f),
            Vec3(-.03974458f, .04959571f, .00119012f),
        )
        tips.forEachIndexed { index, tip ->
            val motion = SpringMotion(tip, stiffness = .4f, drag = .4f)
            var maxTilt = 0f
            // Actual Android 8 frames can be ~33ms; a 150ms scene hiccup
            // must not permanently pin a real VRM HairJoint in the bind pose.
            repeat(180) { frame ->
                val sample = motion.advance(
                    dtSeconds = if (frame == 30) .15f else 1f / 30f,
                    gravityLocal = Vec3(0f, -1f, 0f),
                    pivotWorld = Vec3(),
                    gravityPower = 2f,
                    wind = 2f,
                )
                maxTilt = maxOf(maxTilt, abs(sample.x) + abs(sample.z))
                assertTrue("VRM hair root $index yielded a non-finite angle",
                    sample.x.isFinite() && sample.z.isFinite())
            }
            assertTrue(
                "Real VRM hair root $index stayed numerically frozen under wind/gravity",
                maxTilt > .25f,
            )
        }
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
