package dev.velvet.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

internal data class VelvetVoiceDraft(
    val file:File,val durationMs:Int,val waveform:List<Int>
)

internal fun velvetVoiceTime(ms:Int):String {
    val seconds=(ms/1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds/60,seconds%60)
}

@Composable
internal fun VelvetWaveform(samples:List<Int>,highlight:Color,
                            modifier:Modifier=Modifier) {
    Row(modifier.height(34.dp),horizontalArrangement=Arrangement.spacedBy(2.dp),
        verticalAlignment=Alignment.CenterVertically) {
        val bins=if(samples.isEmpty()) List(15){17} else samples.takeLast(30)
        bins.forEach { level->
            Box(Modifier.width(3.dp)
                .height((4+26*level.coerceIn(0,100)/100).dp)
                .background(highlight,RoundedCornerShape(2.dp)))
        }
    }
}

/**
 * Explicit tap-to-record, tap-stop, review and send. The microphone permission
 * is requested at runtime. No upload begins until the sender presses Send.
 * M4A files are app-private temp files; Android backup is disabled.
 */
@Composable
internal fun VelvetVoiceCapture(
    enabled:Boolean,
    onSend:suspend (VelvetVoiceDraft)->Boolean
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var recorder by remember {mutableStateOf<MediaRecorder?>(null)}
    var captureFile by remember {mutableStateOf<File?>(null)}
    var staged by remember {mutableStateOf<VelvetVoiceDraft?>(null)}
    var startMs by remember {mutableLongStateOf(0L)}
    var elapsedMs by remember {mutableIntStateOf(0)}
    var recording by remember {mutableStateOf(false)}
    var sending by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    val amplitudes=remember {mutableStateListOf<Int>()}
    val rose=Color(0xFFE4A1B6)
    val cream=Color(0xFFFFEAF0)

    fun stopCapture(save:Boolean) {
        val active=recorder
        val file=captureFile
        val duration=(SystemClock.elapsedRealtime()-startMs).toInt()
        recording=false
        recorder=null
        captureFile=null
        val finished=runCatching {active?.stop();true}.getOrDefault(false)
        runCatching {active?.release()}
        if(save && finished && file!=null && duration>=500 && file.length()>0
           && file.length()<=5242880) {
            staged?.file?.delete()
            staged=VelvetVoiceDraft(file,duration.coerceAtMost(120000),
                amplitudes.toList().takeLast(42))
        } else {
            file?.delete()
            if(save)error="Recording was too short or couldn't be saved."
        }
    }

    fun startCapture() {
        if(!enabled || recording || sending || staged!=null)return
        error=""
        val file=File.createTempFile("velvet_note_",".m4a",context.cacheDir)
        val next=MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(48000)
            next.setAudioSamplingRate(24000)
            next.setMaxDuration(120000)
            next.setMaxFileSize(5242880L)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
            recorder=next
            captureFile=file
            amplitudes.clear()
            elapsedMs=0
            startMs=SystemClock.elapsedRealtime()
            recording=true
        } catch(e:Exception) {
            runCatching {next.release()}
            file.delete()
            error=e.message ?: "Microphone unavailable."
        }
    }

    val askPermission=rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if(granted) startCapture() else error="Allow microphone access to record."
    }

    LaunchedEffect(recording) {
        while(recording) {
            delay(180)
            elapsedMs=(SystemClock.elapsedRealtime()-startMs).toInt()
            val maxAmp=runCatching {recorder?.maxAmplitude ?: 0}.getOrDefault(0)
            val value=(sqrt(maxAmp.toDouble()/32767.0)*100.0).toInt()
                .coerceIn(0,100)
            if(amplitudes.size>=42) amplitudes.removeAt(0)
            amplitudes.add(value)
            if(elapsedMs>=119800)stopCapture(true)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {recorder?.stop()}
            runCatching {recorder?.release()}
            recorder=null
            captureFile?.delete()
            staged?.file?.delete()
        }
    }

    when {
        recording -> {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(velvetVoiceTime(elapsedMs),color=rose,fontSize=12.sp)
                Spacer(Modifier.width(6.dp))
                VelvetWaveform(amplitudes,rose,Modifier.widthIn(max=110.dp))
                IconButton(onClick={stopCapture(true)}) {
                    Icon(Icons.Outlined.Stop,"Stop recording",tint=rose)
                }
                IconButton(onClick={stopCapture(false)}) {
                    Icon(Icons.Outlined.Close,"Discard recording",tint=cream)
                }
            }
        }
        staged!=null -> {
            val draft=staged!!
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("🎙 "+velvetVoiceTime(draft.durationMs),color=rose,fontSize=12.sp)
                Spacer(Modifier.width(5.dp))
                IconButton(enabled=!sending,onClick={
                    draft.file.delete()
                    staged=null
                    error=""
                }) {Icon(Icons.Outlined.Close,"Discard voice note",tint=cream)}
                IconButton(enabled=!sending && enabled,onClick={
                    sending=true
                    scope.launch {
                        try {
                            if(onSend(draft)) {
                                draft.file.delete()
                                staged=null
                            } else error="Voice wasn't sent. Try again."
                        } catch(e:Exception) {
                            error=e.message ?: "Voice upload failed."
                        } finally {sending=false}
                    }
                }) {
                    if(sending) CircularProgressIndicator(
                        Modifier.size(20.dp),color=rose,strokeWidth=2.dp)
                    else Icon(Icons.Outlined.Send,"Send voice note",tint=rose)
                }
            }
        }
        else -> {
            IconButton(enabled=enabled,onClick={
                if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==
                    PackageManager.PERMISSION_GRANTED) startCapture()
                else askPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) {Icon(Icons.Outlined.Mic,"Record a voice note",tint=rose)}
        }
    }
    if(error.isNotBlank()) {
        Text(error,color=rose,fontSize=10.sp,maxLines=2)
    }
}
