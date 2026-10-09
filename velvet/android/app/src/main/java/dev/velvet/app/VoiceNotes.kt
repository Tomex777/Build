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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

private val ink=Color(0xFFF8E8E4)
private val blush=Color(0xFFF2BDCC)
private val faded=Color(0xFFBCA4B4)
private val panel=Color(0xFF281C2A)

private class NativeRecorder(private val context: Context) {
    private var recorder:MediaRecorder?=null
    private var startAt=0L
    private var target:File?=null
    private val levels=mutableListOf<Float>()
    var started=false
        private set

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
        recorder=r; target=destination;startAt=SystemClock.elapsedRealtime();levels.clear();started=true
        true
    }catch(_:Exception){cancel();false}

    fun sample():Float {
        val amplitude=runCatching {recorder?.maxAmplitude ?: 0}.getOrDefault(0)
        // These peaks are recorded from the microphone, not random decorative bars.
        val value=(amplitude/32767f).coerceIn(.025f,1f)
        levels.add(value)
        return value
    }
    fun finish():VoiceClip? {
        if(!started)return null
        val duration=SystemClock.elapsedRealtime()-startAt
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
        recorder=null; started=false;target?.delete();target=null
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
                    player=new;new.start();playing=true
                } else if(previous.isPlaying){previous.pause();playing=false}
                else {previous.start();playing=true}
            }catch(_:Exception){playing=false}
        }){Icon(if(playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,"Play voice note",tint=blush)}
        Column(Modifier.weight(1f)){
            Waveform(bars,progress,Modifier.fillMaxWidth().height(30.dp).clickable {
                // Seek to midpoint on tapping the audio waveform; progress remains real playback time.
                player?.let { if(it.duration>0){it.seekTo((it.duration*.5f).toInt());progress=.5f} }
            })
            Text("${durationMs/60000}:${((durationMs/1000)%60).toString().padStart(2,'0')}",color=faded,fontSize=10.sp)
        }
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
