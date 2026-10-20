package com.night.echospace.audio

/**
 * Adaptive mobile CPU budget for expensive measured binaural convolution.
 * The system drops HRTF voices aggressively on overload and recovers slowly
 * after 200 consecutive cheap buffers. Spatial fallback stays active.
 */
class BinauralQualityBudget(private val frames:Int=512,private val sampleRate:Int=44100) {
    var measuredVoiceLimit=3
        private set
    private var healthy=0
    val bufferBudgetNanos:Long = frames*1_000_000_000L/sampleRate

    fun record(renderNanos:Long) {
        if(renderNanos>bufferBudgetNanos*0.72) {
            measuredVoiceLimit=(measuredVoiceLimit-1).coerceAtLeast(0)
            healthy=0
        } else if(renderNanos<bufferBudgetNanos*0.25) {
            if(++healthy>=200) {
                measuredVoiceLimit=(measuredVoiceLimit+1).coerceAtMost(4)
                healthy=0
            }
        } else healthy=0
    }
}
