package dev.velvet.app

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Private video attachments download ONLY after the viewer taps Play.
 * Downloads require the couple-scoped Storage SELECT RLS on every request;
 * no public URL or OS gallery permissions are used.
 */
@Composable
internal fun VelvetPrivateVideo(
    client:VelvetPairClient,
    storagePath:String,
    durationMs:Int,
) {
    var expanded by remember(storagePath){mutableStateOf(false)}
    val rose=Color(0xFFE4A1B6)
    val cream=Color(0xFFFFEAF0)
    Column(
        Modifier.fillMaxWidth().heightIn(min=128.dp)
            .background(Color(0xFF241925),RoundedCornerShape(13.dp))
            .clickable{expanded=true}
            .padding(14.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center
    ) {
        Icon(Icons.Outlined.PlayCircleOutline,"Play private video",tint=rose,
            modifier=Modifier.size(50.dp))
        Spacer(Modifier.height(8.dp))
        Text("Private video · ${velvetVoiceTime(durationMs)}",
            color=cream,fontSize=12.sp)
        Text("Tap to play full screen",color=rose,fontSize=10.sp)
    }
    if(expanded) {
        VelvetVideoFullscreen(client,storagePath,onDismiss={expanded=false})
    }
}

@Composable
private fun VelvetVideoFullscreen(
    client:VelvetPairClient,
    storagePath:String,
    onDismiss:()->Unit
) {
    val context=LocalContext.current
    val lifecycle=(context as LifecycleOwner).lifecycle
    val cream=Color(0xFFFFEAF0)
    val rose=Color(0xFFE4A1B6)
    var videoFile by remember(storagePath){mutableStateOf<File?>(null)}
    var loading by remember(storagePath){mutableStateOf(true)}
    var problem by remember(storagePath){mutableStateOf("")}
    var retry by remember(storagePath){mutableIntStateOf(0)}
    var activeVideo by remember(storagePath){mutableStateOf<VideoView?>(null)}

    LaunchedEffect(storagePath,retry) {
        loading=true
        problem=""
        try {
            videoFile=client.downloadVideo(storagePath)
        } catch(cancelled:CancellationException) {
            throw cancelled
        } catch(e:Exception) {
            problem=e.message ?: "Couldn't load this private video."
        } finally {
            loading=false
        }
    }
    DisposableEffect(storagePath,lifecycle) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_STOP) activeVideo?.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            activeVideo?.stopPlayback()
            videoFile?.delete()
        }
    }

    Dialog(
        onDismissRequest=onDismiss,
        properties=DialogProperties(
            usePlatformDefaultWidth=false,
            decorFitsSystemWindows=false
        )
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                Modifier.fillMaxWidth().padding(top=26.dp,start=8.dp,end=12.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                IconButton(onClick=onDismiss) {
                    Icon(Icons.Outlined.ArrowBack,"Close video",tint=cream)
                }
                Text("Private Chat Video",color=cream,
                    modifier=Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().weight(1f),
                contentAlignment=Alignment.Center) {
                val file=videoFile
                if(file!=null) {
                    AndroidView(
                        factory={viewContext->
                            VideoView(viewContext).apply {
                                val controls=MediaController(viewContext)
                                controls.setAnchorView(this)
                                setMediaController(controls)
                                setVideoPath(file.absolutePath)
                                setOnPreparedListener {start()}
                                activeVideo=this
                            }
                        },
                        modifier=Modifier.fillMaxSize(),
                        onRelease={view->
                            view.stopPlayback()
                            if(activeVideo===view)activeVideo=null
                        }
                    )
                } else if(loading) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color=rose)
                        Spacer(Modifier.height(14.dp))
                        Text("Downloading privately…",color=cream)
                    }
                } else {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment=Alignment.CenterHorizontally
                    ) {
                        Text(problem.ifBlank{"Video unavailable."},color=cream)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick={retry++}) {Text("Retry")}
                    }
                }
            }
            Text(
                "Chat video only · Not end-to-end encrypted",
                color=rose,fontSize=11.sp,
                modifier=Modifier.padding(16.dp)
            )
        }
    }
}
