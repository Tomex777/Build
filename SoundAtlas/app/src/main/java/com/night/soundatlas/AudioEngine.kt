package com.night.soundatlas

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.*

/**
 * Offline real-time PCM synthesizer. All mixing happens off the UI thread.
 * Source types are procedural: sounds never repeat a fixed 20- or 30-second recording.
 * Material attenuation, ITD/ILD and a feedback-tail model are approximations.
 */
class AudioEngine(private val assets: AssetManager) {
    companion object { const val SAMPLE_RATE = 44100; const val FRAMES = 768 }
    private val scene = AtomicReference<World?>(null)
    private val running = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val voices = mutableMapOf<Int, Voice>()
    private var fieldRecordings: Map<SoundKind, ShortArray> = emptyMap()
    private var recordingsReady=false
    private var measuredHrir: MeasuredHrir? = null
    private val governor=AudioQualityGovernor()
    private val reverb = DiffuseReverb()
    private var cachedGeometryWorld: World? = null
    private val staticReflections = mutableMapOf<Int,List<ReflectionTap>>()
    private var worker: Thread? = null
    private var frameClock = 0L
    @Volatile var error: String? = null
        private set

    val isPlaying: Boolean get() = running.get()
    fun update(world: World) { scene.set(world.copyForAudio()) }
    @Synchronized fun start(world: World) {
        update(world)
        if (!running.compareAndSet(false,true)) return
        error = null
        val session = generation.incrementAndGet()
        val predecessor=worker
        worker = thread(name="SoundAtlasAudio",isDaemon=true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var track: AudioTrack? = null
            try {
                // Don't create a second AudioTrack while a previous blocking
                // write is still releasing the hardware. Never wait on UI.
                if(predecessor?.isAlive==true) {
                    predecessor.join(3000L)
                    check(!predecessor.isAlive) { "Previous audio output has not released" }
                }
                if(!running.get() || generation.get()!=session)return@thread
                val requested = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT)
                require(requested>0) { "Stereo PCM output unavailable" }
                if(!recordingsReady){
                    fieldRecordings=readOfflineFieldRecordings()
                    recordingsReady=true
                }
                if(measuredHrir==null) {
                    measuredHrir=try {
                        MeasuredHrir.read(assets.open("kemar_44100.hrir"))
                    } catch(_:Exception) { null }
                }
                if(!running.get() || generation.get()!=session)return@thread
                track = AudioTrack(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build(),
                    max(requested, FRAMES*2*2*3), AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                check(track.state==AudioTrack.STATE_INITIALIZED) { "AudioTrack init failed" }
                track.play()
                val buffer=ShortArray(FRAMES*2)
                while(running.get() && generation.get()==session && !Thread.currentThread().isInterrupted) {
                    val world=scene.get()
                    val renderStart=System.nanoTime()
                    if(world!=null) render(world,buffer)
                    else buffer.fill(0)
                    governor.record(System.nanoTime()-renderStart)
                    val n=track.write(buffer,0,buffer.size,AudioTrack.WRITE_BLOCKING)
                    if(n<0) error("AudioTrack write failed: $n")
                }
            } catch (e: Exception) {
                if(running.get() && generation.get()==session)
                    error=e.message ?: e.javaClass.simpleName
            } finally {
                try { track?.pause(); track?.flush(); track?.release() } catch (_: Exception) {}
                if(generation.get()==session) running.set(false)
            }
        }
    }
    @Synchronized fun stop() {
        running.set(false)
        generation.incrementAndGet()
        worker?.interrupt()
        // Retain the old thread so a rapid restart can join it off the UI thread.
    }
    fun close() = stop()

    /** PCM16 mono / 22.05kHz field recordings; provenance generated with the asset pack. */
    private fun readOfflineFieldRecordings(): Map<SoundKind, ShortArray> {
        val files=mapOf(
            SoundKind.RAIN to "rain", SoundKind.THUNDER to "thunder",
            SoundKind.WIND to "wind", SoundKind.WATER to "stream",
            SoundKind.BIRDS to "birds", SoundKind.LEAVES to "leaves",
            SoundKind.CRICKETS to "night", SoundKind.BELL to "chimes",
            SoundKind.TRAFFIC to "traffic", SoundKind.CROWD to "cafe"
        )
        val result=mutableMapOf<SoundKind,ShortArray>()
        files.forEach { (type,filename) ->
            try {
                val raw=assets.open("audio/"+filename+".pcm").use{it.readBytes()}
                if(raw.size<44100 || raw.size>12_000_000 || raw.size%2!=0)return@forEach
                val pcm=ShortArray(raw.size/2)
                for(i in pcm.indices) {
                    val lo=raw[i*2].toInt() and 255
                    val hi=raw[i*2+1].toInt()
                    pcm[i]=((hi shl 8) or lo).toShort()
                }
                result[type]=PcmLeveler.normalize(pcm)
            } catch(_:Exception) {
                // A source may be absent in test-only builds; procedural fallback is available.
            }
        }
        return result
    }

    private fun render(world: World, out: ShortArray) {
        if(cachedGeometryWorld !== world){
            cachedGeometryWorld=world
            staticReflections.clear()
        }
        // Prioritize perceptually loud, close emitters under bounded mobile CPU load.
        val active = world.sounds.asSequence()
            .filter { it.enabled && it.gain>0f }
            .sortedByDescending { it.gain/(1f+it.at.distance(world.listener)*.12f) }
            .take(WorldLimits.MAX_SOURCES).toList()
        val valid = active.map { it.id }.toSet()
        voices.keys.retainAll(valid)
        val layers = active.mapIndexed { sourceIndex, emitter ->
            val voice = voices.getOrPut(emitter.id) {
                Voice(emitter.seed,fieldRecordings[emitter.kind])
            }
            val t=frameClock.toDouble()/SAMPLE_RATE
            val position = if (emitter.moving) {
                emitter.at.moved(
                    (3.0*sin(t*.29+emitter.seed*.01)).toFloat(),
                    (2.2*cos(t*.21+emitter.seed*.021)).toFloat())
            } else emitter.at
            val acoustic = AcousticSolver.evaluate(world,emitter,position)
            voice.targetAlpha=(1.0-exp(-2.0*PI*acoustic.lowPassHz/SAMPLE_RATE))
                .toFloat().coerceIn(.001f,1f)
            voice.targetPan=acoustic.pan
            voice.targetGain=(emitter.gain*acoustic.directGain).coerceAtMost(1.2f)
            // Only dominant emitters need geometrical early reflections.
            // Static source geometry is cached between audio blocks; the editor
            // atomically replaces its scene snapshot when a wall/listener moves.
            val computeTaps = {
                EarlyReflections.solve(world,position).mapIndexed { index, reflection ->
                    val alpha=(1.0-exp(-2.0*PI*reflection.cutoffHz/SAMPLE_RATE)).toFloat()
                        .coerceIn(.001f,1f)
                    val pan=reflection.pan
                    ReflectionTap(reflection.delayFrames,
                        sqrt(((1f-pan)*.5f).coerceAtLeast(0f))*reflection.gain*emitter.gain,
                        sqrt(((1f+pan)*.5f).coerceAtLeast(0f))*reflection.gain*emitter.gain,
                        alpha,index)
                }
            }
            val reflections=if(sourceIndex>=4) emptyList() else
                if(emitter.moving) computeTaps() else
                    staticReflections.getOrPut(emitter.id,computeTaps)
            Layer(emitter,voice,reflections,position)
        }
        // Use measured binaural filters for the four most audible nearby layers.
        // Less audible sources use lightweight ITD/ILD; don't overload Android CPUs.
        val hrir=measuredHrir
        if(hrir!=null) {
            layers.sortedByDescending{it.voice.targetGain}
                .take(governor.measuredVoices).forEach { layer ->
                layer.measured=true
                val p=layer.position
                val bearing=atan2(p.x-world.listener.x,
                    -(p.y-world.listener.y))-world.facing
                hrir.kernels(bearing,layer.voice.desiredHrirLeft,layer.voice.desiredHrirRight)
                // Smooth filter-coefficient changes across buffers, preventing clicks.
                for(tap in 0 until 96){
                    layer.voice.hrirLeft[tap]+=(layer.voice.desiredHrirLeft[tap]-
                        layer.voice.hrirLeft[tap])*.32f
                    layer.voice.hrirRight[tap]+=(layer.voice.desiredHrirRight[tap]-
                        layer.voice.hrirRight[tap])*.32f
                }
            }
        }
        val room=world.zones.lastOrNull { it.contains(world.listener) }
        val decay=room?.decay ?: .28f
        val damp=room?.damping ?: .72f
        reverb.configure(decay,damp)
        val timeStep = 1.0 / SAMPLE_RATE
        var t=frameClock.toDouble()*timeStep
        for(i in 0 until FRAMES) {
            var left=0f;var right=0f
            for(layer in layers) {
                val voice=layer.voice
                val sound=voice.next(layer.emitter.kind,world.wind,world.rain,world.storm,t)
                // 50 ms ramps prevent zipper clicks when positioning sounds or crossing doors.
                voice.gain += (voice.targetGain-voice.gain)*.00045f
                voice.pan += (voice.targetPan-voice.pan)*.00045f
                voice.filterAlpha += (voice.targetAlpha-voice.filterAlpha)*.00045f
                voice.lowpass += (sound-voice.lowpass)*voice.filterAlpha
                val filtered=voice.lowpass*voice.gain
                voice.hrirMix += ((if(layer.measured)1f else 0f)-
                    voice.hrirMix)*.00065f
                // Direct sound follows ear-shadow + fractional horizon direction.
                val side=voice.pan
                val lGain=sqrt(((1f-side)*.5f).coerceAtLeast(0f))
                val rGain=sqrt(((1f+side)*.5f).coerceAtLeast(0f))
                voice.delay[voice.delayIndex]=filtered
                val itd=(abs(side)*27f).toInt().coerceIn(0,27)
                val old=(voice.delayIndex-itd+voice.delay.size)%voice.delay.size
                val shadow=voice.delay[old]
                var directLeft=(if(side>0f) shadow else filtered)*lGain
                var directRight=(if(side<0f) shadow else filtered)*rGain
                val history=voice.hrirHistory
                history[voice.hrirIndex]=filtered
                if(voice.hrirMix>.0005f) {
                    var convolvedL=0f;var convolvedR=0f
                    var cursor=voice.hrirIndex
                    for(tap in 0 until 96) {
                        val sample=history[cursor]
                        convolvedL+=sample*voice.hrirLeft[tap]
                        convolvedR+=sample*voice.hrirRight[tap]
                        cursor=if(cursor==0)127 else cursor-1
                    }
                    val mix=voice.hrirMix
                    directLeft=directLeft*(1f-mix)+convolvedL*(1.6f*mix)
                    directRight=directRight*(1f-mix)+convolvedR*(1.6f*mix)
                }
                voice.hrirIndex=(voice.hrirIndex+1) and 127
                left+=directLeft
                right+=directRight
                voice.delayIndex=(voice.delayIndex+1)%voice.delay.size

                // Specular early reflections travel to actual wall hit points.
                // This separate delay line preserves reflected sound behind a
                // direct-path obstruction when a clear reflected path exists.
                val buffer=voice.echoLine
                buffer[voice.echoIndex]=voice.lowpass
                for(tap in layer.reflections) {
                    val read=(voice.echoIndex-tap.delay+buffer.size)%buffer.size
                    val sampleReflected=buffer[read]
                    val filteredRef=voice.reflectionFilters[tap.slot]+
                        (sampleReflected-voice.reflectionFilters[tap.slot])*tap.alpha
                    voice.reflectionFilters[tap.slot]=filteredRef
                    left+=filteredRef*tap.left
                    right+=filteredRef*tap.right
                }
                voice.echoIndex=(voice.echoIndex+1)%buffer.size
            }
            // Bounded wet sends; shorter surfaces absorb more reverberant energy.
            reverb.process(left,right)
            val dryAttenuation=.72f
            val l=softClip(left*dryAttenuation+reverb.wetLeft)
            val r=softClip(right*dryAttenuation+reverb.wetRight)
            out[i*2]=(l*32767f).toInt().coerceIn(-32768,32767).toShort()
            out[i*2+1]=(r*32767f).toInt().coerceIn(-32768,32767).toShort()
            t+=timeStep
        }
        frameClock+=FRAMES
    }
    private fun softClip(x: Float):Float = (x / (1.0f+0.38f*abs(x))).coerceIn(-1f,1f)

    private data class ReflectionTap(val delay:Int,val left:Float,val right:Float,
        val alpha:Float,val slot:Int)
    private data class Layer(val emitter:Emitter,val voice:Voice,
        val reflections:List<ReflectionTap>,val position:V2,var measured:Boolean=false)

    private class Voice(seed:Int, private val recording:ShortArray?) {
        private var rng=if(seed==0) 1234567 else seed
        private var low=0.0
        private var mid=0.0
        private var phase=0.0
        private var eventPhase=0.0
        private var envelope=0.0
        private var eventFreq=700.0
        private var eventLength=.3
        private var nextEvent=.3+random01()*2
        private var eventAge=0.0
        private var pulse=0.0
        private var clipPhase = if(recording==null) 0.0 else
            (abs(seed%89)/97.0)*(recording.size-1)
        private var clipNextStart = 0
        private var clipRate = .490 + (abs(seed%31)/31.0)*.020
        private fun fieldSample():Float {
            val clip=recording?:return 0f
            if(clip.size<44100)return 0f
            val crossfade=(clip.size/10).coerceIn(4400,17500)
            val start=clip.size-crossfade
            val index=clipPhase.toInt().coerceIn(0,clip.size-2)
            val fraction=clipPhase-index
            var sample=clip[index]*(1.0-fraction)+clip[index+1]*fraction
            if(clipPhase>=start) {
                val mix=((clipPhase-start)/crossfade).coerceIn(0.0,1.0)
                val next=(clipNextStart+clipPhase-start).coerceIn(0.0,(clip.size-2).toDouble())
                val j=next.toInt();val f=next-j
                val entering=clip[j]*(1.0-f)+clip[j+1]*f
                sample=sample*cos(mix*PI*.5)+entering*sin(mix*PI*.5)
            }
            clipPhase+=clipRate
            if(clipPhase>=clip.size) {
                clipPhase=clipNextStart+crossfade+(clipPhase-clip.size)
                clipNextStart=(random01()*(clip.size-crossfade-2)*.36).toInt()
                clipRate=.491+random01()*.018
            }
            return (sample/32768.0).toFloat()
        }
        var filterAlpha=.7f
        var targetAlpha=.7f
        var lowpass=0f
        var gain=0f
        var targetGain=0f
        var pan=0f
        var targetPan=0f
        val delay=FloatArray(48)
        var delayIndex=0
        val echoLine=FloatArray(16384)
        var echoIndex=0
        val reflectionFilters=FloatArray(4)
        val hrirHistory=FloatArray(128)
        var hrirIndex=0
        var hrirMix=0f
        val hrirLeft=FloatArray(96)
        val hrirRight=FloatArray(96)
        val desiredHrirLeft=FloatArray(128)
        val desiredHrirRight=FloatArray(128)
        private fun random01():Double {
            rng = rng * 1664525 + 1013904223
            return ((rng ushr 8) and 0xFFFFFF).toDouble()/0x1000000
        }
        private fun noise():Double = random01()*2-1
        private fun osc(hz:Double):Double {
            phase+=2*PI*hz/SAMPLE_RATE
            if(phase>=2*PI)phase-=2*PI
            return sin(phase)
        }
        fun next(kind:SoundKind, wind:Float, rain:Float, storm:Float, t:Double):Float {
            val white=noise()
            low += (white-low)*.003
            mid += (white-mid)*.06
            // Per-voice event clock: arrivals vary but remain reproducible from seed.
            nextEvent-=1.0/SAMPLE_RATE
            if(nextEvent<=0.0) {
                eventAge=0.0
                eventLength=when(kind) {
                    SoundKind.THUNDER -> 2.7+random01()*4.0
                    SoundKind.METAL -> .55+random01()*.7
                    SoundKind.BELL -> 1.5+random01()*1.6
                    SoundKind.BIRDS -> .12+random01()*.9
                    else -> .08+random01()*.5
                }
                eventFreq=200+random01()*1800
                if(kind==SoundKind.THUNDER || kind==SoundKind.BELL){
                    clipPhase=0.0
                    clipNextStart=0
                }
                nextEvent=when(kind) {
                    SoundKind.THUNDER -> (8.0+random01()*27.0)*(1.4-storm.coerceIn(0f,1f)*.9)
                    SoundKind.DRIP -> .3+random01()*2.3
                    SoundKind.BIRDS -> .4+random01()*3.8
                    SoundKind.BELL -> 5.0+random01()*17.0
                    SoundKind.METAL -> 3.0+random01()*11.0
                    SoundKind.STEPS -> .4+random01()*.42
                    SoundKind.TRAFFIC -> 3.0+random01()*7.0
                    SoundKind.CRICKETS -> .24+random01()*.85
                    SoundKind.CROWD -> .5+random01()*1.4
                    else -> 1.0+random01()*4.0
                }
            }
            eventAge+=1.0/SAMPLE_RATE
            val progress=(eventAge/eventLength).coerceIn(0.0,1.0)
            val active=if(eventAge<eventLength) (1.0-progress).pow(
                if(kind==SoundKind.THUNDER) 1.5 else 2.6) else 0.0
            val result=when(kind) {
                SoundKind.RAIN -> {
                    // Multiple spectral bands + slow intensity drift, not an audio loop.
                    val intensity=(.65+rain*.5)
                    (white*.23+mid*.41+low*.3)*intensity*(.93+.07*sin(t*.39))
                }
                SoundKind.THUNDER -> {
                    // Subsonic-like crack + lengthy rolling envelope; no downloaded clip.
                    (low*3.1+mid*.5+white*.12)*active*(.45+storm*.65+rain*.22)
                }
                SoundKind.WIND -> (low*2.0+mid*.19)*(0.5+wind*.7)*
                    (.68+.32*sin(t*.2+eventFreq))
                SoundKind.DRIP -> {
                    val frequency=530+eventFreq*.35
                    (osc(frequency)*active*.48+white*active*.03)
                }
                SoundKind.WATER -> (mid*.43+low*.6+white*.1)*
                    (.76+.24*sin(t*.33))
                SoundKind.BIRDS -> {
                    val chirp=eventFreq*(.9+progress*.75+sin(progress*PI*7)*.16)
                    osc(chirp)*active*.26
                }
                SoundKind.LEAVES -> (white*.16+mid*.55+low*.8)*
                    (.5+wind*.45)*(.8+.2*sin(t*.73))
                SoundKind.CRICKETS -> {
                    val chirp=if(sin(t*73.0+eventFreq)>0) 1.0 else .1
                    osc(2200+eventFreq*.7)*active*chirp*.12
                }
                SoundKind.BELL -> {
                    (osc(eventFreq)+.42*sin(phase*2.76)+.18*sin(phase*4.04))*
                        active*.32
                }
                SoundKind.HUM -> {
                    (osc(58.0)*.19+osc(117.0)*.07+low*.32+mid*.05)*
                        (.93+.07*sin(t*.32))
                }
                SoundKind.METAL -> {
                    (osc(eventFreq*.37)+.33*sin(phase*2.71)+white*.12)*
                        active*.19
                }
                SoundKind.STEPS -> (mid+white*.14+low*.35)*active*.63
                SoundKind.CROWD -> {
                    // Filtered non-speech formant-like motion; no intelligible voices.
                    val chatter=abs(sin(t*5.8+eventFreq))
                    (mid*.19+osc(160+eventFreq*.08)*.09+low*.28)*
                        (.3+chatter*.7)
                }
                SoundKind.TRAFFIC -> {
                    // Receding pass-bys with slow spectral and amplitude changes.
                    val pass=sin(t*.23+eventFreq*.04).let{(.5+.5*it).pow(2.0)}
                    (low*1.35+mid*.27+osc(75.0)*.08)*(.25+pass*.7)
                }
            }
            // Variable-length recordings + irregular synthesized events sound richer
            // while the procedural layer covers every sound without a bundled asset.
            // One-shot recordings must not run continuously under an idle event
            // generator. Thunder and bells restart at actual, irregular event onset.
            val real=when(kind) {
                SoundKind.THUNDER, SoundKind.BELL ->
                    if(active>0.001) fieldSample()*active.toFloat() else 0f
                else -> fieldSample()
            }
            return if(recording==null) result.toFloat().coerceIn(-1f,1f)
            else (result.toFloat()*.49f+real*.68f).coerceIn(-1f,1f)
        }
    }

    /** Small Schroeder-like tail, not costly geometric ray tracing. */
    private class DiffuseReverb {
        private class Comb(val size:Int) {
            val l=FloatArray(size)
            val r=FloatArray(size)
            var i=0
            var dL=0f
            var dR=0f
            var lastL=0f
            var lastR=0f
            fun process(x:Float,y:Float,feedback:Float,damp:Float) {
                lastL=l[i];lastR=r[i]
                dL=lastL*(1f-damp)+dL*damp
                dR=lastR*(1f-damp)+dR*damp
                l[i]=(x*.23f+dL*feedback).coerceIn(-4f,4f)
                r[i]=(y*.23f+dR*feedback).coerceIn(-4f,4f)
                i++;if(i>=size)i=0
            }
        }
        private val combs=arrayOf(Comb(1327),Comb(1613),Comb(1789),Comb(2099))
        private var feedbacks=FloatArray(combs.size)
        private var damp=.4f
        private var wet=.15f
        var wetLeft=0f
            private set
        var wetRight=0f
            private set
        fun configure(decay:Float,absorption:Float) {
            val seconds=decay.coerceIn(.12f,12f)
            for(i in combs.indices)
                feedbacks[i]=10.0.pow(-3.0*combs[i].size/(SAMPLE_RATE*seconds))
                    .toFloat().coerceIn(0f,.98f)
            damp=(absorption*.58f).coerceIn(.02f,.7f)
            wet=(.07f+decay*.06f).coerceIn(.06f,.34f)
        }
        fun process(left:Float,right:Float) {
            var outL=0f;var outR=0f
            for(i in combs.indices) {
                val comb=combs[i]
                comb.process(left,right,feedbacks[i],damp)
                outL+=comb.lastL;outR+=comb.lastR
            }
            wetLeft=outL*wet*.38f
            wetRight=outR*wet*.38f
        }
    }
}
