package dev.velvet.app

import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import java.io.File
import java.util.UUID

internal data class PickedVelvetMedia(val path:String,val mime:String,val displayName:String)

private fun importPickedMedia(ctx:android.content.Context,uri:Uri):PickedVelvetMedia? = runCatching {
    val mime=ctx.contentResolver.getType(uri) ?: "application/octet-stream"
    val extension=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
    val dest=File(File(ctx.filesDir,"velvet-chat-attachments"),"${UUID.randomUUID()}.$extension")
    dest.parentFile?.mkdirs()
    ctx.contentResolver.openInputStream(uri)?.use{input->dest.outputStream().use{input.copyTo(it)}} ?: return null
    PickedVelvetMedia(dest.absolutePath,mime,if(mime.startsWith("image/"))"Photo"
        else if(mime.startsWith("video/"))"Video"
        else if(mime.startsWith("audio/"))"Audio file" else "Document")
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VelvetAttachmentSheet(onDismiss:()->Unit,onSend:(List<PickedVelvetMedia>,String)->Unit){
    val ctx=LocalContext.current
    val p=VelvetTheme.current
    val media=remember{mutableStateListOf<PickedVelvetMedia>()}
    var caption by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)){uris->
        media.clear()
        uris.forEach{u->importPickedMedia(ctx,u)?.let(media::add)}
        if(uris.isNotEmpty() && media.isEmpty())error="Unable to read selected photos."
    }
    val audio=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null){
            media.clear()
            importPickedMedia(ctx,uri)?.let(media::add)
            if(media.isEmpty())error="Unable to read that file."
        }
    }
    val documents=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null){
            media.clear()
            importPickedMedia(ctx,uri)?.let(media::add)
            if(media.isEmpty())error="Unable to read that document."
        }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=p.paper,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(bottom=24.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(if(media.isEmpty())"Share a little moment" else "Ready to send ♡",
                    color=p.text,fontSize=23.sp,fontWeight=FontWeight.SemiBold,
                    modifier=Modifier.weight(1f))
                IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Close",tint=p.muted)}
            }
            Spacer(Modifier.height(13.dp))
            if(media.isEmpty()){
                listOf(
                    Triple("Photos & videos","Choose up to 10 at a time",Icons.Outlined.PhotoLibrary),
                    Triple("Audio","Choose a sound or song file",Icons.Outlined.AudioFile),
                    Triple("Document","Choose a document securely",Icons.Outlined.Description)
                ).forEachIndexed{index,(name,description,icon)->
                    Row(Modifier.fillMaxWidth().padding(bottom=8.dp)
                        .clip(RoundedCornerShape(17.dp)).background(p.raised)
                        .clickable{
                            when(index){
                                0->gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                1->audio.launch("audio/*")
                                else->documents.launch("*/*")
                            }
                        }.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(icon,null,Modifier.size(27.dp),tint=p.rose)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)){
                            Text(name,color=p.text,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                            Text(description,color=p.muted,fontSize=11.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight,null,tint=p.rose)
                    }
                }
                Text("Android controls which files you grant access to. Selected files are copied into Velvet's private local chat storage.",
                    color=p.muted,fontSize=11.sp,lineHeight=17.sp,
                    modifier=Modifier.padding(vertical=12.dp))
            }else{
                Text("${media.size} attachment${if(media.size==1)"" else "s"} selected",
                    color=p.muted,fontSize=12.sp)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    items(media.toList()){item->
                        Column(Modifier.width(130.dp).clip(RoundedCornerShape(14.dp))
                            .background(p.raised).padding(5.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            VelvetMediaThumb(item.path,item.mime,Modifier.fillMaxWidth().height(110.dp))
                            Text(item.displayName,color=p.text,fontSize=11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                OutlinedTextField(value=caption,onValueChange={caption=it.take(320)},
                    placeholder={Text("Add a little caption…")},maxLines=3,
                    modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OutlinedButton(onClick={media.clear();caption=""},modifier=Modifier.weight(1f)){
                        Text("Choose again")
                    }
                    Button(onClick={onSend(media.toList(),caption.trim());onDismiss()},
                        modifier=Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Send,null,Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp));Text("Send here")
                    }
                }
                Text("These attachments are local-only until pairing and Azure uploads are connected.",
                    fontSize=11.sp,color=p.muted,modifier=Modifier.padding(top=10.dp))
            }
            if(error.isNotBlank())Text(error,color=p.rose,fontSize=12.sp)
        }
    }
}

@Composable
internal fun VelvetMediaThumb(path:String?,mime:String?,modifier:Modifier=Modifier){
    val bitmap=remember(path){
        if(path==null || mime?.startsWith("image/")!=true) null
        else runCatching{
            val bounds=BitmapFactory.Options().also{it.inJustDecodeBounds=true}
            BitmapFactory.decodeFile(path,bounds)
            var sample=1
            while(bounds.outWidth/sample>960 || bounds.outHeight/sample>960)sample*=2
            BitmapFactory.decodeFile(path,BitmapFactory.Options().also{it.inSampleSize=sample})?.asImageBitmap()
        }.getOrNull()
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF5C4658)),
        contentAlignment=Alignment.Center){
        if(bitmap!=null)Image(bitmap,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(if(mime?.startsWith("video/")==true)Icons.Outlined.PlayCircle
            else if(mime?.startsWith("audio/")==true)Icons.Outlined.MusicNote
            else Icons.Outlined.InsertDriveFile,null,Modifier.size(42.dp),tint=Color.White)
    }
}
@Composable
internal fun VelvetMediaBubble(path:String?,mime:String?){
    var open by remember{mutableStateOf(false)}
    val p=VelvetTheme.current
    Column {
        VelvetMediaThumb(path,mime,Modifier.widthIn(min=205.dp,max=260.dp)
            .height(215.dp).clickable{open=true})
        Text(if(mime?.startsWith("video/")==true)"Tap to play video"
            else if(mime?.startsWith("image/")==true)"Tap to view"
            else "File saved on this phone",color=p.muted,fontSize=11.sp,
            modifier=Modifier.padding(top=5.dp))
    }
    if(open)Dialog(onDismissRequest={open=false},
        properties=DialogProperties(usePlatformDefaultWidth=false)){
        Column(Modifier.fillMaxSize().background(p.bg).padding(12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(if(mime?.startsWith("video/")==true)"Video" else "Media",
                    color=p.text,fontSize=19.sp,modifier=Modifier.weight(1f))
                IconButton(onClick={open=false}){Icon(Icons.Outlined.Close,"Close",tint=p.text)}
            }
            if(path!=null && mime?.startsWith("video/")==true){
                AndroidView(factory={ctx->
                    VideoView(ctx).apply{
                        setVideoPath(path)
                        setMediaController(MediaController(ctx))
                        setOnPreparedListener{it.isLooping=false;start()}
                    }
                },modifier=Modifier.fillMaxSize())
            } else VelvetMediaThumb(path,mime,Modifier.fillMaxSize())
        }
    }
}
