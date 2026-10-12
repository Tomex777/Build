package dev.velvet.app

import android.media.MediaPlayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Authenticated playback, never a public URL; temporary media stays app-private. */
@Composable
internal fun VelvetVoicePlayer(
    client:VelvetPairClient,
    storagePath:String,
    durationMs:Int,
    waveform:List<Int>,
    onPlaybackStart:()->Unit={},
    playingAllowed:Boolean=true
) {
    val context=LocalContext.current
    val lifecycle=(context as LifecycleOwner).lifecycle
    val scope=rememberCoroutineScope()
    var player by remember(storagePath){mutableStateOf<MediaPlayer?>(null)}
    var localFile by remember(storagePath){mutableStateOf<File?>(null)}
    var loading by remember(storagePath){mutableStateOf(false)}
    var playing by remember(storagePath){mutableStateOf(false)}
    var progress by remember(storagePath){mutableIntStateOf(0)}
    var error by remember(storagePath){mutableStateOf("")}
    val rose=Color(0xFFE4A1B6)
    val cream=Color(0xFFFFEAF0)

    DisposableEffect(storagePath,lifecycle) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_STOP) {
                runCatching {player?.pause()}
                playing=false
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching {player?.release()}
            player=null
            runCatching {localFile?.delete()}
            localFile=null
        }
    }
    LaunchedEffect(playingAllowed) {
        if(!playingAllowed) {
            runCatching {player?.pause()}
            playing=false
        }
    }
    LaunchedEffect(playing) {
        while(playing) {
            delay(140)
            progress=runCatching{player?.currentPosition ?: 0}.getOrDefault(0)
            if(progress>=durationMs)playing=false
        }
    }

    fun toggle() {
        val active=player
        if(active!=null) {
            if(playing) {
                runCatching {active.pause()}
                playing=false
            } else {
                runCatching {
                    if(active.currentPosition>=active.duration-200)active.seekTo(0)
                    onPlaybackStart()
                    active.start()
                    playing=true
                }.onFailure{error="Playback could not resume."}
            }
        } else if(!loading) {
            loading=true
            error=""
            scope.launch {
                try {
                    val nextFile=File.createTempFile(
                        "velvet_play_",".m4a",context.cacheDir)
                    try {
                        val bytes=client.downloadVoice(storagePath)
                        withContext(Dispatchers.IO) {nextFile.writeBytes(bytes)}
                        val next=MediaPlayer()
                        try {
                            next.setDataSource(nextFile.absolutePath)
                            withContext(Dispatchers.IO) {next.prepare()}
                            next.setOnCompletionListener {
                                playing=false
                                progress=0
                                it.seekTo(0)
                            }
                            currentCoroutineContext().ensureActive()
                            player=next
                            localFile=nextFile
                            // Loading can finish after the user leaves or selects another clip.
                            if(playingAllowed && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                onPlaybackStart()
                                next.start()
                                playing=true
                            }
                        } catch(e:Exception) {
                            runCatching {next.release()}
                            throw e
                        }
                    } catch(e:Exception) {
                        nextFile.delete()
                        throw e
                    }
                } catch(cancelled:CancellationException) {
                    throw cancelled
                } catch(e:Exception) {
                    error=e.message ?: "Couldn't download this private voice note."
                } finally {loading=false}
            }
        }
    }

    Column(Modifier.widthIn(min=165.dp,max=255.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(enabled=!loading,onClick={toggle()}) {
                if(loading) {
                    CircularProgressIndicator(
                        Modifier.size(19.dp),color=rose,strokeWidth=2.dp)
                } else {
                    Icon(if(playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if(playing) "Pause voice note" else "Play voice note",tint=rose)
                }
            }
            Column {
                VelvetWaveform(waveform,rose)
                Text(
                    "${velvetVoiceTime(if(playing)progress else durationMs)} · Voice note",
                    color=cream,fontSize=11.sp
                )
            }
        }
        if(player!=null && durationMs>0) {
            Slider(
                value=(progress.toFloat()/durationMs).coerceIn(0f,1f),
                onValueChange={fraction->
                    val next=(fraction*durationMs).toInt()
                    runCatching {player?.seekTo(next)}
                    progress=next
                },modifier=Modifier.fillMaxWidth().height(24.dp)
            )
        }
        if(error.isNotBlank()) {
            Text(error,color=rose,fontSize=10.sp,maxLines=2)
            TextButton(onClick={toggle()}) {Text("Retry",color=rose)}
        }
    }
}
