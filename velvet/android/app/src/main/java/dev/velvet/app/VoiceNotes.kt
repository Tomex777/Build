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


/**
 * The actual microphone recorder used by the inline chat composer.
 * Hold to record, release to send, slide left to discard, slide up to lock.
 * The first use may require a microphone permission prompt; hold again afterwards.
 */
@Composable
internal fun VoiceHoldControl(onRecorded:(VoiceClip)->Unit,onActiveChange:(Boolean)->Unit) {
    val context=LocalContext.current
    val recorder=remember {NativeRecorder(context)}
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
    }
    var recording by remember {mutableStateOf(false)}
    var locked by remember {mutableStateOf(false)}
    var paused by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var elapsed by remember {mutableLongStateOf(0L)}
    var coordinates by remember {mutableStateOf<LayoutCoordinates?>(null)}
    val samples=remember {mutableStateListOf<Float>()}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed ->
        granted=allowed
        error=if(allowed)"Hold the mic to record" else "Microphone permission needed"
    }
    DisposableEffect(Unit){onDispose {recorder.cancel()}}
    LaunchedEffect(recording) {
        val begin=SystemClock.elapsedRealtime()
        while(recording) {
            if(!paused){
                elapsed=SystemClock.elapsedRealtime()-begin
                samples.add(recorder.sample())
                if(samples.size>38)samples.removeAt(0)
            }
            delay(90)
        }
    }
    fun reset() { recording=false;locked=false;paused=false;onActiveChange(false);samples.clear();elapsed=0 }
    val gesture=if(locked) Modifier else Modifier.pointerInput(granted) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val startRoot=coordinates?.localToRoot(down.position) ?: down.position
            if(!granted) {
                permission.launch(Manifest.permission.RECORD_AUDIO)
                var stillDown:Boolean
                do {val event=awaitPointerEvent();stillDown=event.changes.any{it.pressed}} while(stillDown)
            } else if(!recording) {
                if(recorder.start()) {
                    recording=true;onActiveChange(true);error=null
                    var ended=false
                    while(!ended) {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull{it.id==down.id}
                        if(change==null || !change.pressed) {
                            val clip=recorder.finish()
                            reset()
                            if(clip!=null)onRecorded(clip)
                            ended=true
                        } else {
                            // Use root-space positions: the recording bar expands while pressed.
                            val atRoot=coordinates?.localToRoot(change.position) ?: change.position
                            val x=atRoot.x-startRoot.x
                            val y=atRoot.y-startRoot.y
                            if(x < -100.dp.toPx()) {
                                recorder.cancel();reset();ended=true
                            } else if(y < -85.dp.toPx()) {
                                locked=true;ended=true
                            } else change.consume()
                        }
                    }
                } else error="Microphone could not start"
            } else {
                var stillDown:Boolean
                do {val event=awaitPointerEvent();stillDown=event.changes.any{it.pressed}} while(stillDown)
            }
        }
    }
    Column {
        Row(Modifier.width(if(recording)310.dp else 45.dp).height(50.dp)
            .then(gesture).onGloballyPositioned {coordinates=it}
            .clip(RoundedCornerShape(24.dp))
            .background(if(recording)panel else Color.Transparent),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            if(recording) {
                if(locked) {
                    IconButton(onClick={recorder.cancel();reset()},modifier=Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.DeleteOutline,"Discard voice note",tint=faded)
                    }
                } else {
                    Text("●",color=blush,modifier=Modifier.padding(start=12.dp),fontSize=17.sp)
                }
                Text("${elapsed/60000}:${((elapsed/1000)%60).toString().padStart(2,'0')}",
                    color=ink,fontSize=12.sp,modifier=Modifier.padding(horizontal=7.dp))
                Waveform(samples.takeLast(24),0f,Modifier.weight(1f).height(29.dp))
                if(locked) {
                    IconButton(onClick={
                        if(paused) {if(recorder.resume())paused=false}
                        else {if(recorder.pause())paused=true}
                    },modifier=Modifier.size(38.dp)) {
                        Icon(if(paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            if(paused)"Resume recording" else "Pause recording",tint=blush)
                    }
                    IconButton(onClick={
                        val clip=recorder.finish();reset();if(clip!=null)onRecorded(clip)
                    },modifier=Modifier.size(44.dp)) {
                        Icon(Icons.Outlined.Send,"Send voice note",tint=blush)
                    }
                } else {
                    Text("↑ Lock\n← Cancel",color=faded,fontSize=10.sp,lineHeight=13.sp,
                        modifier=Modifier.padding(horizontal=9.dp))
                }
            } else {
                Icon(Icons.Outlined.Mic,"Hold to record",Modifier.size(27.dp),tint=blush)
            }
        }
        error?.let {Text(it,color=blush,fontSize=10.sp)}
    }
}
