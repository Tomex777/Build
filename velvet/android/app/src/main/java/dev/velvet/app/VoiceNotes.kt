package dev.velvet.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

internal data class VoiceClip(val path:String,val bars:List<Float>,val durationMs:Long)

private val ink get() = VelvetTheme.current.text
private val blush get() = VelvetTheme.current.rose
private val faded get() = VelvetTheme.current.muted
private val panel get() = VelvetTheme.current.paper

private class NativeRecorder(private val context: Context) {
    private var recorder:MediaRecorder?=null
    private var startAt=0L
    private var target:File?=null
    private val levels=mutableListOf<Float>()
    var started=false
        private set
    private var pauseAt=0L
    private var pausedFor=0L

    @Suppress("DEPRECATION")
    fun start():Boolean = try {
        val destination=File(context.filesDir,"velvet-audio/${UUID.randomUUID()}.m4a")
        destination.parentFile?.mkdirs()
        val r= if(Build.VERSION.SDK_INT >= 31)MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96000)
        r.setAudioSamplingRate(44100)
        r.setOutputFile(destination.absolutePath)
        r.prepare();r.start()
        recorder=r; target=destination;startAt=SystemClock.elapsedRealtime();pausedFor=0L;pauseAt=0L;levels.clear();started=true
        true
    }catch(_:Exception){cancel();false}

    fun pause():Boolean = try {
        if(started && pauseAt==0L){recorder?.pause();pauseAt=SystemClock.elapsedRealtime()}
        true
    }catch(_:Exception){false}
    fun resume():Boolean = try {
        if(started && pauseAt!=0L){recorder?.resume();pausedFor+=SystemClock.elapsedRealtime()-pauseAt;pauseAt=0L}
        true
    }catch(_:Exception){false}
    fun sample():Float {
        val amplitude=runCatching {recorder?.maxAmplitude ?: 0}.getOrDefault(0)
        // These peaks are recorded from the microphone, not random decorative bars.
        val value=kotlin.math.sqrt(amplitude/32767f).coerceIn(.09f,1f)
        levels.add(value)
        return value
    }
    fun finish():VoiceClip? {
        if(!started)return null
        if(pauseAt!=0L)resume()
        val duration=SystemClock.elapsedRealtime()-startAt-pausedFor-(if(pauseAt!=0L)SystemClock.elapsedRealtime()-pauseAt else 0L)
        var valid=true
        try {recorder?.stop()} catch(_:Exception){valid=false}
        recorder?.release();recorder=null;started=false
        if(!valid || duration<450L){target?.delete();return null}
        val bars=if(levels.isEmpty())listOf(.05f) else levels.chunked(max(1,levels.size/36)).map {it.maxOrNull() ?: .05f}.take(48)
        return VoiceClip(target?.absolutePath ?: return null,bars,duration)
    }
    fun cancel() {
        if(started){runCatching{recorder?.stop()}}
        runCatching{recorder?.reset()};runCatching{recorder?.release()}
        recorder=null; started=false;target?.delete();target=null;pauseAt=0L;pausedFor=0L
    }
}

@Composable
internal fun VoiceRecorderDialog(onDismiss:()->Unit,onRecorded:(VoiceClip)->Unit) {
    val context=LocalContext.current
    val recorder=remember{NativeRecorder(context)}
    var recording by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val samples=remember{mutableStateListOf<Float>()}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(granted){recording=recorder.start();if(!recording)error="Microphone couldn't start."}
        else error="Microphone permission is needed to record a voice note."
    }
    DisposableEffect(Unit){onDispose{recorder.cancel()}}
    LaunchedEffect(recording){
        while(recording){samples.add(recorder.sample());if(samples.size>70)samples.removeAt(0);delay(90)}
    }
    AlertDialog(onDismissRequest={recorder.cancel();onDismiss()},title={Text("Voice note",color=ink)},
        text={ Column {
            Text(if(recording)"Recording your voice…" else "Record a voice note to preview in Chat.",color=faded,fontSize=13.sp)
            Spacer(Modifier.height(16.dp))
            Waveform(samples.takeLast(38),0f,Modifier.fillMaxWidth().height(52.dp))
            error?.let{Spacer(Modifier.height(9.dp));Text(it,color=blush,fontSize=12.sp)}
        } },
        confirmButton={
            if(recording)TextButton(onClick={val clip=recorder.finish();recording=false;if(clip!=null)onRecorded(clip)else error="Recording too short. Try again."}){Text("Send recording",color=blush)}
            else TextButton(onClick={
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){recording=recorder.start();if(!recording)error="Microphone couldn't start."}
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }){Text("Record",color=blush)}
        }, dismissButton={TextButton(onClick={recorder.cancel();recording=false;onDismiss()}){Text("Cancel",color=faded)}},
        containerColor=panel)
}

@Composable
internal fun VoiceBubble(path:String?,bars:List<Float>,durationMs:Long) {
    var player by remember(path){mutableStateOf<MediaPlayer?>(null)}
    var playing by remember(path){mutableStateOf(false)}
    var progress by remember(path){mutableFloatStateOf(0f)}
    var speed by remember(path){mutableFloatStateOf(1f)}
    val context=LocalContext.current
    DisposableEffect(path){onDispose {runCatching{player?.release()};player=null}}
    LaunchedEffect(playing){while(playing){
        val p=player
        if(p?.isPlaying==true){progress= if(p.duration>0)p.currentPosition.toFloat()/p.duration else 0f}
        else {playing=false;progress=0f}
        delay(100)
    }}
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.widthIn(min=218.dp,max=270.dp)){
        IconButton(onClick={
            if(path==null || !File(path).exists())return@IconButton
            try {
                val previous=player
                if(previous==null){
                    val new=MediaPlayer()
                    new.setDataSource(path);new.prepare();new.setOnCompletionListener{playing=false;progress=0f}
                    player=new;runCatching{new.playbackParams=new.playbackParams.setSpeed(speed)};new.start();playing=true
                } else if(previous.isPlaying){previous.pause();playing=false}
                else {previous.start();playing=true}
            }catch(_:Exception){playing=false}
        }){Icon(if(playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,"Play voice note",tint=blush)}
        Column(Modifier.weight(1f)){
            Waveform(bars,progress,Modifier.fillMaxWidth().height(32.dp).pointerInput(path) {
                detectTapGestures { offset ->
                    val fraction=(offset.x/size.width.toFloat()).coerceIn(0f,1f)
                    player?.let { if(it.duration>0){it.seekTo((it.duration*fraction).toInt());progress=fraction} }
                }
            })
            Text("${((progress*durationMs).toLong())/60000}:${((((progress*durationMs).toLong())/1000)%60).toString().padStart(2,'0')}  /  ${durationMs/60000}:${((durationMs/1000)%60).toString().padStart(2,'0')}",
                color=faded,fontSize=10.sp)
        }
        Text("${if(speed==1f) "1×" else if(speed==1.5f) "1.5×" else "2×"}",
            Modifier.padding(start=8.dp).clip(RoundedCornerShape(8.dp)).background(panel).clickable {
                speed=if(speed==1f)1.5f else if(speed==1.5f)2f else 1f
                runCatching {player?.let{it.playbackParams=it.playbackParams.setSpeed(speed)}}
            }.padding(horizontal=7.dp,vertical=6.dp),fontSize=11.sp,color=blush,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun Waveform(bars:List<Float>,progress:Float,modifier:Modifier=Modifier) {
    Canvas(modifier){
        val data=if(bars.isEmpty())List(20){.03f} else bars
        val step=size.width/data.size
        data.forEachIndexed {index,value->
            val bar=max(3f, min(size.height*.96f, value*size.height*.93f))
            val x=(index+.5f)*step
            drawLine(if(index.toFloat()/data.size<=progress)blush else blush.copy(alpha=.52f),
                Offset(x,(size.height-bar)/2),Offset(x,(size.height+bar)/2),strokeWidth=step.coerceIn(2f,5f))
        }
    }
}


/** Gesture and microphone state survives Compose recompositions and layout changes. */
internal class VoiceCaptureState(context:Context) {
    private val recorder=NativeRecorder(context)
    var recording by mutableStateOf(false); private set
    var locked by mutableStateOf(false); private set
    var paused by mutableStateOf(false); private set
    var elapsed by mutableLongStateOf(0L); private set
    var error by mutableStateOf<String?>(null)
    val bars=mutableStateListOf<Float>()
    var granted by mutableStateOf(
        ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
    )
    private var startAt=0L
    private var pausedAt=0L
    private var pausedTotal=0L

    fun begin():Boolean {
        if(recording)return true
        if(!granted){error="Microphone permission needed";return false}
        val ok=recorder.start()
        if(!ok){error="Couldn't start recording";return false}
        bars.clear();elapsed=0L;locked=false;paused=false;pausedTotal=0L;pausedAt=0L
        startAt=SystemClock.elapsedRealtime()
        recording=true;error=null
        return true
    }
    fun sample() {
        if(!recording || paused)return
        elapsed=(SystemClock.elapsedRealtime()-startAt-pausedTotal).coerceAtLeast(0L)
        bars.add(recorder.sample())
        if(bars.size>48)bars.removeAt(0)
    }
    fun lock(){if(recording)locked=true}
    fun togglePause(){
        if(!recording || !locked)return
        if(paused) {
            if(recorder.resume()) {
                pausedTotal += SystemClock.elapsedRealtime()-pausedAt
                paused=false
            }
        } else if(recorder.pause()) {
            pausedAt=SystemClock.elapsedRealtime();paused=true
        }
    }
    fun finish():VoiceClip? {
        if(!recording)return null
        val clip=recorder.finish()
        reset()
        if(clip==null)error="Hold a little longer to record"
        return clip
    }
    fun cancel(){recorder.cancel();reset()}
    private fun reset(){
        recording=false;locked=false;paused=false;elapsed=0L
        bars.clear();pausedAt=0L;pausedTotal=0L
    }
}

@Composable
internal fun rememberVoiceCaptureState():VoiceCaptureState {
    val context=LocalContext.current
    val state=remember { VoiceCaptureState(context) }
    DisposableEffect(state){onDispose{state.cancel()}}
    LaunchedEffect(state.recording){
        while(state.recording) {
            state.sample()
            delay(80)
        }
    }
    return state
}

/** Fixed-size touch target: it never expands or slides beneath the user's finger. */
@Composable
internal fun VoiceHoldControl(state:VoiceCaptureState,onRecorded:(VoiceClip)->Unit) {
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state.granted=it
        state.error=if(it)null else "Microphone permission denied"
    }
    val active=state.recording
    val color=VelvetTheme.current
    val gesture=Modifier.pointerInput(state.granted, state.locked) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            if(!state.granted){
                permission.launch(Manifest.permission.RECORD_AUDIO)
                var held:Boolean
                do{val e=awaitPointerEvent();held=e.changes.any{it.pressed}}while(held)
            } else if(!state.locked && state.begin()) {
                var done=false
                while(!done) {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull{it.id==down.id}
                    if(change==null || !change.pressed) {
                        val clip=state.finish()
                        if(clip!=null)onRecorded(clip)
                        done=true
                    } else {
                        val dx=change.position.x-down.position.x
                        val dy=change.position.y-down.position.y
                        when {
                            dx < -76.dp.toPx() -> {state.cancel();done=true}
                            dy < -72.dp.toPx() -> {state.lock();done=true}
                            else -> change.consume()
                        }
                    }
                }
            } else {
                var held:Boolean
                do{val e=awaitPointerEvent();held=e.changes.any{it.pressed}}while(held)
            }
        }
    }
    Box(Modifier.size(48.dp).clip(CircleShape).background(color.rose).then(gesture),
        contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Mic,"Hold microphone to record; slide left to cancel, up to lock",
            modifier=Modifier.size(25.dp),tint=color.bg)
    }
}

/** Composer replacement on the left of the stationary microphone button. */
@Composable
internal fun VoiceRecordingBar(state:VoiceCaptureState,modifier:Modifier=Modifier,onRecorded:(VoiceClip)->Unit){
    val p=VelvetTheme.current
    Row(modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
        .background(p.raised).padding(horizontal=7.dp),
        verticalAlignment=Alignment.CenterVertically){
        if(state.locked) {
            IconButton(onClick=state::cancel,modifier=Modifier.size(36.dp)){
                Icon(Icons.Outlined.DeleteOutline,"Discard recording",tint=p.rose,modifier=Modifier.size(22.dp))
            }
        } else {
            Text("●",fontSize=14.sp,color=p.rose,modifier=Modifier.padding(horizontal=5.dp))
        }
        Text("${state.elapsed/60000}:${(state.elapsed/1000%60).toString().padStart(2,'0')}",
            fontSize=12.sp,color=p.text,modifier=Modifier.padding(horizontal=5.dp))
        Waveform(state.bars.toList(),0f,Modifier.weight(1f).height(31.dp))
        if(state.locked){
            IconButton(onClick=state::togglePause,modifier=Modifier.size(36.dp)) {
                Icon(if(state.paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    if(state.paused)"Resume" else "Pause",tint=p.text,modifier=Modifier.size(21.dp))
            }
            IconButton(onClick={state.finish()?.let(onRecorded)},modifier=Modifier.size(39.dp)
                .clip(CircleShape).background(p.rose)){
                Icon(Icons.Outlined.Send,"Send recording",tint=p.bg,modifier=Modifier.size(20.dp))
            }
        } else {
            Text("← cancel   ↑ lock",fontSize=10.sp,color=p.muted,
                modifier=Modifier.padding(horizontal=4.dp))
        }
    }
}
