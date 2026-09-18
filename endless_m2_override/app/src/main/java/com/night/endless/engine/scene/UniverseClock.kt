package com.night.endless.engine.scene

class UniverseClock {
    private var simulationSeconds = 0.0
    private var anchorMillis = System.currentTimeMillis()

    private val speeds = doubleArrayOf(0.1, 1.0, 10.0, 100.0, 1_000.0, 10_000.0, 100_000.0)
    private var speedIndex = 2 // HTML prototype default: 10×

    @Volatile var speed: Double = speeds[speedIndex]
        private set
    @Volatile var paused: Boolean = false

    @Synchronized
    fun advance(realSeconds: Double): Double {
        if (!paused) simulationSeconds += realSeconds.coerceIn(0.0, 0.1) * speed
        return simulationSeconds
    }

    @Synchronized
    fun reset() {
        simulationSeconds = 0.0
        anchorMillis = System.currentTimeMillis()
    }

    @Synchronized
    fun seconds(): Double = simulationSeconds

    @Synchronized
    fun currentTimeMillis(): Long = anchorMillis + (simulationSeconds * 1000.0).toLong()

    @Synchronized
    fun slower(): Double {
        speedIndex = (speedIndex - 1).coerceAtLeast(0)
        speed = speeds[speedIndex]
        return speed
    }

    @Synchronized
    fun faster(): Double {
        speedIndex = (speedIndex + 1).coerceAtMost(speeds.lastIndex)
        speed = speeds[speedIndex]
        return speed
    }

    @Synchronized
    fun speedLabel(): String = when {
        speed < 1.0 -> "0.1×"
        speed < 1000.0 -> "${speed.toInt()}×"
        speed < 1_000_000.0 -> "${(speed / 1000.0).toInt()}K×"
        else -> "${(speed / 1_000_000.0).toInt()}M×"
    }
}
