package com.night.offlinegames

import kotlin.random.Random

/** Small deterministic Kotlin-only snake simulation; never depends on rendering. */
data class SnakeCell(val x: Int, val y: Int)
enum class SnakeHeading(val dx: Int, val dy: Int) {
    UP(0, -1), RIGHT(1, 0), DOWN(0, 1), LEFT(-1, 0)
}

data class NativeSnakeState(
    val snake: List<SnakeCell>,
    val heading: SnakeHeading,
    val pending: SnakeHeading = heading,
    val fruit: SnakeCell,
    val score: Int = 0,
    val ticks: Int = 0,
    val over: Boolean = false,
    val victory: Boolean = false
)

object NativeSnakeEngine {
    const val columns = 18
    const val rows = 25
    private const val savedVersion = "1"

    fun newGame(random: Random = Random.Default): NativeSnakeState {
        val snake = listOf(SnakeCell(8, 12), SnakeCell(7, 12), SnakeCell(6, 12))
        return NativeSnakeState(snake, SnakeHeading.RIGHT, fruit = findFruit(snake, random)!!)
    }

    fun requestTurn(state: NativeSnakeState, turn: SnakeHeading): NativeSnakeState {
        if (state.over || state.victory) return state
        if (turn.dx == -state.heading.dx && turn.dy == -state.heading.dy) return state
        return state.copy(pending = turn)
    }

    fun tick(state: NativeSnakeState, random: Random = Random.Default): NativeSnakeState {
        if (state.over || state.victory) return state
        val h = state.pending
        val first = state.snake.first()
        val next = SnakeCell(first.x + h.dx, first.y + h.dy)
        if (next.x !in 0 until columns || next.y !in 0 until rows)
            return state.copy(heading = h, over = true, ticks = state.ticks + 1)
        val grows = next == state.fruit
        // Stepping into the old tail is legal when that tail leaves on this tick.
        val collisionBody = if (grows) state.snake else state.snake.dropLast(1)
        if (next in collisionBody) return state.copy(heading = h, over = true, ticks = state.ticks + 1)
        val body = listOf(next) + if (grows) state.snake else state.snake.dropLast(1)
        val nextFruit = if (grows) findFruit(body, random) else state.fruit
        return state.copy(
            snake = body, heading = h, pending = h, fruit = nextFruit ?: next,
            score = state.score + if (grows) 10 else 0, ticks = state.ticks + 1,
            victory = grows && nextFruit == null
        )
    }

    private fun findFruit(body: List<SnakeCell>, random: Random): SnakeCell? {
        val occupied = body.toSet()
        val vacancies = (0 until rows).flatMap { y ->
            (0 until columns).mapNotNull { x ->
                SnakeCell(x, y).takeUnless { it in occupied }
            }
        }
        if (vacancies.isEmpty()) return null
        return vacancies[random.nextInt(vacancies.size)]
    }

    fun tickMillis(score: Int): Long = (200 - (score / 40).coerceAtMost(10) * 10).toLong()

    fun encode(s: NativeSnakeState): String = listOf(
        savedVersion, s.heading.ordinal, s.pending.ordinal, s.fruit.x, s.fruit.y,
        s.score, s.ticks, s.over, s.victory,
        s.snake.joinToString("|") { "${it.x},${it.y}" }
    ).joinToString(";")

    fun decode(raw: String?): NativeSnakeState? = try {
        if (raw == null) null else {
            val t = raw.split(";", limit = 10)
            if (t.size != 10 || t[0] != savedVersion) null else {
                val body = t[9].split("|").map {
                    val xy = it.split(",")
                    if (xy.size != 2) throw IllegalArgumentException("invalid cell")
                    SnakeCell(xy[0].toInt(), xy[1].toInt())
                }
                val s = NativeSnakeState(
                    snake = body,
                    heading = SnakeHeading.entries[t[1].toInt()],
                    pending = SnakeHeading.entries[t[2].toInt()],
                    fruit = SnakeCell(t[3].toInt(), t[4].toInt()),
                    score = t[5].toInt(), ticks = t[6].toInt(),
                    over = t[7].toBooleanStrict(), victory = t[8].toBooleanStrict()
                )
                if (s.snake.isEmpty() || s.snake.size > columns * rows ||
                    s.snake.toSet().size != s.snake.size ||
                    s.snake.any { it.x !in 0 until columns || it.y !in 0 until rows } ||
                    s.fruit.x !in 0 until columns || s.fruit.y !in 0 until rows ||
                    (s.fruit in s.snake && !s.victory) || s.score < 0 || s.ticks < 0) null else s
            }
        }
    } catch (_: RuntimeException) { null }
}
