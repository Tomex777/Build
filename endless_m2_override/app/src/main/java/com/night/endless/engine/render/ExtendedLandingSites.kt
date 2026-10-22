package com.night.endless.engine.render

/**
 * Curated *illustrative* first-person landing compositions for extended worlds.
 * Mercury/Ceres are not georeferenced surveyed sites; these positions only
 * select convincing rugged terrain inside the already-existing simulated
 * heightfield. Players remain free to walk or turn in every direction.
 */
internal object ExtendedLandingSites {
    data class Site(val x: Double, val z: Double, val yaw: Double, val pitch: Double)

    fun forBody(id: String): Site = when (id) {
        // Initial release spawned both airless worlds on nearly identical
        // black-sky grey flats. Start *inside* their simulated crater bowls,
        // facing sunlit rims 2-3km ahead for readable geological relief.
        "mercury" -> Site(-1.8, 1.8, 1.57, -.15)
        "ceres" -> Site(-1.45, 2.3, 1.57, -.15)
        // Preserve Jupiter's large disk in Europa's real black sky.
        "europa" -> Site(0.0, 0.0, 1.24, -.08)
        // Look slightly below the thick haze toward existing Cassini-style
        // modeled dune fields; the atmosphere remains brown, never blue.
        "titan" -> Site(0.0, 0.0, .85, -.17)
        else -> throw IllegalArgumentException("Unsupported simulated landing: $id")
    }
}
