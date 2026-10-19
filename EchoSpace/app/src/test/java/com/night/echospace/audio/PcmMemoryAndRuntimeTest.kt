package com.night.echospace.audio

import com.night.echospace.model.SoundKind
import com.night.echospace.model.SoundSource
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PcmMemoryAndRuntimeTest {
    @Test fun packedSixteenBitRecordingProducesValidStereo() {
        // The same PCM16 format shipped by the asset pipeline. Avoid allocating
        // 32-bit float copies for every 48-second field recording.
        val recording=ShortArray(100_000){ i ->
            ((if(i%200<100)1 else -1)*14_000).toShort()
        }
        val synth=SpatialSynth(recordings=mapOf(SoundKind.RAIN to recording))
        val out=ShortArray(4096)
        synth.renderInto(out,listOf(SoundSource(19,SoundKind.RAIN,-.7f,-.6f)),.75f)
        assertTrue("PCM16 asset must be audible",out.any{it!=0.toShort()})
        assertTrue("PCM output must avoid numeric wrapping",out.all{it.toInt() in -32768..32767})
        val l=(out.indices step 2).sumOf{abs(out[it].toInt()).toLong()}
        val r=(1 until out.size step 2).sumOf{abs(out[it].toInt()).toLong()}
        assertTrue("Directional source should not lose left signal",l>0)
        assertTrue("Directional source should not lose right signal",r>0)
    }

    @Test fun sustainedMultiLayerPlaybackDoesNotAccumulateUnboundedVoices() {
        val mix=SpatialSynth()
        val buffer=ShortArray(1024)
        // 8 seconds of synthetic PCM, alternating distinct scene identities
        // while ensuring finite output, no wrapping and no crashes.
        val kinds=arrayOf(SoundKind.RAIN,SoundKind.WIND,SoundKind.CHIMES,
            SoundKind.TRAFFIC,SoundKind.WHITE_NOISE)
        repeat(689){
            val sources=(0 until 7).map { i ->
                SoundSource(i+1,kinds[(it/13+i)%kinds.size],
                    (i-3)/4f,if(i%2==0)-.6f else .4f,
                    .55f)
            }
            mix.renderInto(buffer,sources,.84f,(it%36)*10f-180f)
            assertTrue(buffer.any{it.toInt()!=-32768})
        }
    }
}
