package com.night.endless.engine.render

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Standalone Kotlin/JVM checks for this agent's pure-code helpers. No
 * Android/GLES dependencies, no synthetic screenshots and no claims about
 * device frame rate. Run: bash endless_ci/run_agent2_geology_smoke.sh
 */
fun main() {
    val outputs = mutableMapOf<String, ByteArray>()
    val started = System.nanoTime()
    for (world in PlanetaryGrainTreatment.worlds) {
        val rgba = SurfaceMicroMaterial.pixels()
        PlanetaryGrainTreatment.apply(world, rgba)
        val twice = SurfaceMicroMaterial.pixels().also {
            PlanetaryGrainTreatment.apply(world, it)
        }
        require(rgba.contentEquals(twice)) { "$world material is not deterministic" }
        outputs[world] = rgba
        fun sample(x: Int, z: Int, c: Int) =
            rgba[(z * 256 + x) * 4 + c].toInt() and 255

        for (channel in 0..3) {
            val xSeam = (0 until 256).map {
                abs(sample(0, it, channel) - sample(255, it, channel))
            }.average()
            val zSeam = (0 until 256).map {
                abs(sample(it, 0, channel) - sample(it, 255, channel))
            }.average()
            require(xSeam < 30.0 && zSeam < 30.0) {
                "$world channel=$channel seam: horizontal=$xSeam vertical=$zSeam"
            }
            val low = (0 until 256 * 256).minOf { rgba[it * 4 + channel].toInt() and 255 }
            val high = (0 until 256 * 256).maxOf { rgba[it * 4 + channel].toInt() and 255 }
            require(high - low > 18) { "$world channel=$channel is uniformly flat" }
        }
        println("PASS $world 256x256 RGBA atlas, hash=${rgba.contentHashCode()}")
    }
    require(outputs.values.map { it.contentHashCode() }.toSet().size == 6) {
        "Materials on different worlds must be genuinely distinct"
    }
    val elapsed = (System.nanoTime() - started) / 1_000_000
    println("Six CPU atlases plus checks: ${elapsed}ms on this JVM (NOT Android FPS)")

    val center = WorldGeomorphology.impactCrater(0.0, 0.0, 0.0, 0.0, 1.0, .1)
    require(center < -.09) { "Impact crater floor must be depressed" }
    val rim = (0..11).map { i ->
        val theta = i * 2 * Math.PI / 12
        WorldGeomorphology.impactCrater(cos(theta), sin(theta), 0.0, 0.0, 1.0, .1)
    }
    require(rim.maxOrNull()!! - rim.minOrNull()!! > .001) {
        "Rim should not be a perfect circular ring"
    }
    for (z in -30..30 step 3) for (x in -30..30 step 3) {
        val xx = x / 3.0
        val zz = z / 3.0
        val elevation = listOf(
            WorldGeomorphology.mercuryScarp(xx, zz),
            WorldGeomorphology.ceresSlump(xx, zz),
            WorldGeomorphology.europaDoubleRidges(xx, zz, .1, -.2),
            WorldGeomorphology.titanDunes(xx, zz, -.1, .2)
        )
        require(elevation.all { it.isFinite() && abs(it) < .10 }) {
            "Non-finite or implausibly abrupt terrain relief at ($xx,$zz)"
        }
    }
    println("PASS crater variation, terrain bounds and finite values")
    var distinct = 0
    for (n in -120..120) {
        val x = n * .00076
        val z = n * .00093
        val moon = FineRegolithRelief.moon(x, z)
        val mars = FineRegolithRelief.mars(x, z)
        require(moon.isFinite() && mars.isFinite())
        require(abs(moon) < .000055 && abs(mars) < .000055) {
            "Meter-scale geology is too steep/chunky"
        }
        require(abs(moon - FineRegolithRelief.moon(x + .0000001, z)) < .000003)
        require(abs(mars - FineRegolithRelief.mars(x + .0000001, z)) < .000003)
        if (abs(moon - mars) > .0000001) distinct++
    }
    require(distinct > 110) { "Near-field Mars/Moon regolith too similar" }
    println("PASS Mars/Moon 1-mesh geological relief: smooth, finite and body-specific")
}
