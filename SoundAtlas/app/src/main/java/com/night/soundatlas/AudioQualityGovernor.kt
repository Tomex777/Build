package com.night.soundatlas

/**
 * Keeps binaural FIR convolution within the real-time Android audio budget.
 * Rendering audio too slowly is worse than falling back to simple ITD/ILD.
 */
class AudioQualityGovernor(
    private val targetFrames:Int=768,
    private val sampleRate:Int=44100
) {
    var measuredVoices=4
        private set
    private var healthy=0
    val budgetNanos:Long=targetFrames*1_000_000_000L/sampleRate

    fun record(renderNanos:Long) {
        when {
            renderNanos > (budgetNanos*0.74).toLong() -> {
                measuredVoices=(measuredVoices-1).coerceAtLeast(0)
                healthy=0
            }
            renderNanos < (budgetNanos*.28).toLong() -> {
                healthy++
                if(healthy>200 && measuredVoices<4) {
                    measuredVoices++
                    healthy=0
                }
            }
            else -> healthy=0
        }
    }
}
