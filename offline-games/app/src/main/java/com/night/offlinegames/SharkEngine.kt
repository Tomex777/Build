package com.night.offlinegames

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Entirely local, original shark-survival rules. Coordinates are world units (400 x 660). */
enum class SeaKind { PREY, JELLYFISH, PREDATOR }
data class SeaCreature(
    val id: Int, val kind: SeaKind, val x: Float, val y: Float,
    val velocity: Float, val radius: Float, val value: Int
)
data class SharkState(
    val x: Float = 180f, val y: Float = 330f, val health: Float = 100f,
    val points: Int = 0, val eaten: Int = 0, val sizeTier: Int = 1,
    val seconds: Float = 0f, val invulnerable: Float = 0f,
    val creatures: List<SeaCreature> = emptyList(), val nextId: Int = 0,
    val finished: Boolean = false
)

object SharkEngine {
    const val worldWidth = 400f
    const val worldHeight = 660f

    fun newGame(seed: Int = 42): SharkState {
        val random = Random(seed)
        val things = (0 until 25).map { id -> spawn(id, random, initial = true) }
        return SharkState(creatures = things, nextId = 25)
    }

    private fun spawn(id: Int, rng: Random, initial: Boolean = false): SeaCreature {
        val kind = when {
            id % 13 == 0 -> SeaKind.PREDATOR
            id % 7 == 0 -> SeaKind.JELLYFISH
            else -> SeaKind.PREY
        }
        val radius = when (kind) {
            SeaKind.PREY -> rng.nextInt(7, 13).toFloat()
            SeaKind.JELLYFISH -> 16f
            SeaKind.PREDATOR -> 28f
        }
        return SeaCreature(
            id, kind,
            if (initial) rng.nextInt(15, 390).toFloat() else if (rng.nextBoolean()) -30f else 430f,
            rng.nextInt(90, 620).toFloat(),
            if (rng.nextBoolean()) rng.nextInt(18, 47).toFloat() else -rng.nextInt(18, 47).toFloat(),
            radius, if (kind == SeaKind.PREY) rng.nextInt(8, 17) else 0
        )
    }

    /** Called with a clamped frame delta, not with wall-clock elapsed time. */
    fun step(state: SharkState, targetX: Float?, targetY: Float?, seconds: Float): SharkState {
        if (state.finished) return state
        val dt = seconds.coerceIn(0f, .05f)
        if (dt <= 0f) return state
        val dx = if (targetX != null) targetX - state.x else 0f
        val dy = if (targetY != null) targetY - state.y else 0f
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val speed = 188f + state.sizeTier * 19f
        val travel = if (length > 6f) min(length - 6f, speed * dt) else 0f
        val radius = 18f + state.sizeTier * 3f
        val x = (state.x + if (length > 0f) dx / length * travel else 0f).coerceIn(radius, worldWidth - radius)
        val y = (state.y + if (length > 0f) dy / length * travel else 0f).coerceIn(57f, worldHeight - 45f)
        var hp = (state.health - (1.95f + state.sizeTier * .35f) * dt).coerceAtLeast(0f)
        var points = state.points
        var eaten = state.eaten
        var cooldown = max(0f, state.invulnerable - dt)
        val updated = ArrayList<SeaCreature>(state.creatures.size)
        for (fish in state.creatures) {
            var fx = fish.x + fish.velocity * dt
            if (fx < -45f) fx = worldWidth + 40f
            if (fx > worldWidth + 45f) fx = -40f
            val shifted = fish.copy(x = fx)
            val d = hypot((fx - x).toDouble(), (shifted.y - y).toDouble()).toFloat()
            if (d < radius + fish.radius * .83f) {
                if (fish.kind == SeaKind.PREY) {
                    eaten++
                    points += fish.value * state.sizeTier
                    hp = min(100f, hp + 5.5f)
                    continue
                }
                if (cooldown <= 0f) {
                    hp = max(0f, hp - if (fish.kind == SeaKind.PREDATOR) 33f else 19f)
                    cooldown = 1.1f
                }
            }
            updated.add(shifted)
        }
        // Persistent seafood population, deterministically generated from frame state.
        var next = state.nextId
        if (updated.size < 25) {
            val rng = Random((next * 9973) xor state.points xor 10707)
            while (updated.size < 25) updated.add(spawn(next++, rng))
        }
        val tier = when {
            points >= 430 -> 3
            points >= 130 -> 2
            else -> 1
        }
        return SharkState(
            x = x, y = y, health = hp, points = points, eaten = eaten, sizeTier = tier,
            seconds = state.seconds + dt, invulnerable = cooldown,
            creatures = updated, nextId = next, finished = hp <= 0f
        )
    }
}
