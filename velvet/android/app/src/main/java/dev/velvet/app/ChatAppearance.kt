package dev.velvet.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Personal styles for Chat only; shared application palette remains Velvet Noir. */
internal object VelvetChatStyle {
    val outgoing = listOf(Color(0xFF49303F),Color(0xFF305451),Color(0xFF354B65),Color(0xFF593B55),Color(0xFF5F483B))
    val incoming = listOf(Color(0xFF29232B),Color(0xFF30383A),Color(0xFF292F39),Color(0xFF37303E))
    var outgoingIndex by mutableIntStateOf(0)
    var incomingIndex by mutableIntStateOf(0)
    var shapeIndex by mutableIntStateOf(0)
    var fontIndex by mutableIntStateOf(0)
    var wallpaperIndex by mutableIntStateOf(0)
    var wallpaperPath by mutableStateOf("")
    var wallpaperDim by mutableFloatStateOf(.48f)
    var textSize by mutableFloatStateOf(14f)
    var partnerNickname by mutableStateOf("")
    val outgoingColor get() = outgoing[outgoingIndex.coerceIn(outgoing.indices)]
    val incomingColor get() = incoming[incomingIndex.coerceIn(incoming.indices)]
    val font get() = when(fontIndex){1->FontFamily.Serif;2->FontFamily.Monospace;else->FontFamily.Default}
    fun bubbleShape(mine:Boolean):RoundedCornerShape = when(shapeIndex) {
        1->RoundedCornerShape(22.dp)
        2->RoundedCornerShape(10.dp)
        else -> RoundedCornerShape(topStart=17.dp,topEnd=17.dp,
            bottomEnd=if(mine)4.dp else 17.dp,bottomStart=if(mine)17.dp else 4.dp)
    }
    fun load(context:Context){
        val p=context.getSharedPreferences("velvet_chat_appearance",Context.MODE_PRIVATE)
        outgoingIndex=p.getInt("outgoing",0).coerceIn(outgoing.indices)
        incomingIndex=p.getInt("incoming",0).coerceIn(incoming.indices)
        shapeIndex=p.getInt("shape",0).coerceIn(0,2)
        fontIndex=p.getInt("font",0).coerceIn(0,2)
        wallpaperIndex=p.getInt("wallpaper",0).coerceIn(0,3)
        wallpaperPath=p.getString("wallpaper_path","") ?: ""
        wallpaperDim=p.getFloat("wallpaper_dim",.48f).coerceIn(.15f,.8f)
        textSize=p.getFloat("message_text_size",14f).coerceIn(12f,19f)
        partnerNickname=p.getString("partner_nickname","") ?: ""
    }
    fun save(context:Context){
        context.getSharedPreferences("velvet_chat_appearance",Context.MODE_PRIVATE).edit()
            .putInt("outgoing",outgoingIndex).putInt("incoming",incomingIndex)
            .putInt("shape",shapeIndex).putInt("font",fontIndex).putInt("wallpaper",wallpaperIndex)
            .putString("wallpaper_path",wallpaperPath)
            .putFloat("wallpaper_dim",wallpaperDim).putFloat("message_text_size",textSize)
            .putString("partner_nickname",partnerNickname).apply()
    }
}

@Composable
internal fun ChatWallpaper(modifier:Modifier=Modifier) {
    val selected=VelvetChatStyle.wallpaperIndex
    val path=VelvetChatStyle.wallpaperPath
    Box(modifier.background(Color(0xFF171318))) {
        val bitmap=remember(selected,path){
            if(selected!=3 || path.isBlank()) null
            else try {
                val bounds=BitmapFactory.Options().also{it.inJustDecodeBounds=true}
                BitmapFactory.decodeFile(path,bounds)
                var sample=1
                while(bounds.outWidth/sample>1080 || bounds.outHeight/sample>1920)sample*=2
                BitmapFactory.decodeFile(path,BitmapFactory.Options().also{it.inSampleSize=sample})
            }catch(_:Exception){null}
        }
        if(selected==3 && bitmap!=null){
            Image(bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=VelvetChatStyle.wallpaperDim)))
        }
        if(selected==1){
            Canvas(Modifier.fillMaxSize()){
                val step=33.dp.toPx()
                var y=step/2f
                while(y<size.height){
                    var x=step/2f
                    while(x<size.width){
                        drawCircle(Color(0xFFF0B4C3).copy(alpha=.085f),radius=1.7.dp.toPx(),center=Offset(x,y))
                        x+=step
                    }
                    y+=step
                }
            }
        }
        if(selected==2){
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0xFF271C2B),Color(0xFF16151C),Color(0xFF261920)))))
        }
    }
}

@Composable
internal fun ChatAppearanceEditor() {
    val ctx=LocalContext.current
    val p=VelvetTheme.current
    var savedNotice by remember { mutableStateOf(false) }
    val mediaPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri:Uri? ->
        if(uri!=null){
            runCatching {
                val dest=File(ctx.filesDir,"velvet-chat-wallpaper.jpg")
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output->input.copyTo(output) }
                } ?: throw IllegalStateException("Could not open photo")
                VelvetChatStyle.wallpaperPath=dest.absolutePath
                VelvetChatStyle.wallpaperIndex=3
                VelvetChatStyle.save(ctx)
                savedNotice=true
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
        .background(p.paper).padding(17.dp)) {
        Text("Chat appearance",fontFamily=FontFamily.Serif,fontSize=25.sp,color=p.text)
        Text("Personal styles on this phone. Shared chat styles will require both partners to approve.",
            color=p.muted,fontSize=12.sp,lineHeight=17.sp)
        Spacer(Modifier.height(15.dp))
        Text("WALLPAPER",fontSize=11.sp,letterSpacing=1.5.sp,color=p.gold)
        Spacer(Modifier.height(9.dp))
        val wallpapers=listOf("Classic","Petals","Twilight","My photo")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            wallpapers.forEachIndexed { index,label ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .border(if(VelvetChatStyle.wallpaperIndex==index)2.dp else 1.dp,
                        if(VelvetChatStyle.wallpaperIndex==index)p.rose else p.border,RoundedCornerShape(12.dp))
                    .clickable{
                        if(index==3)mediaPicker.launch("image/*") else {
                            VelvetChatStyle.wallpaperIndex=index;VelvetChatStyle.save(ctx)
                        }
                    }.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp))
                        .background(when(index){1->Color(0xFF413141);2->Color(0xFF28374B);3->Color(0xFF523C49);else->Color(0xFF171318)}),
                        contentAlignment=Alignment.Center){
                        if(index==3)Icon(Icons.Outlined.AddPhotoAlternate,null,tint=p.rose,modifier=Modifier.size(20.dp))
                        if(index==1)Text("✧  ♡",color=p.rose,fontSize=14.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(label,color=p.text,fontSize=10.sp,maxLines=1)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("MY MESSAGE COLOR",fontSize=11.sp,letterSpacing=1.4.sp,color=p.gold)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(13.dp)){
            VelvetChatStyle.outgoing.forEachIndexed {i,c->
                Box(Modifier.size(34.dp).clip(CircleShape).background(c)
                    .border(if(i==VelvetChatStyle.outgoingIndex)2.dp else 1.dp,
                        if(i==VelvetChatStyle.outgoingIndex)p.rose else p.border,CircleShape)
                    .clickable{VelvetChatStyle.outgoingIndex=i;VelvetChatStyle.save(ctx)},
                    contentAlignment=Alignment.Center) {
                    if(i==VelvetChatStyle.outgoingIndex)Icon(Icons.Outlined.Check,null,Modifier.size(18.dp),tint=p.text)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("THEIR MESSAGE COLOR",fontSize=11.sp,letterSpacing=1.4.sp,color=p.gold)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(13.dp)){
            VelvetChatStyle.incoming.forEachIndexed {i,c->
                Box(Modifier.size(34.dp).clip(CircleShape).background(c)
                    .border(if(i==VelvetChatStyle.incomingIndex)2.dp else 1.dp,
                        if(i==VelvetChatStyle.incomingIndex)p.rose else p.border,CircleShape)
                    .clickable{VelvetChatStyle.incomingIndex=i;VelvetChatStyle.save(ctx)},
                    contentAlignment=Alignment.Center) {
                    if(i==VelvetChatStyle.incomingIndex)Icon(Icons.Outlined.Check,null,Modifier.size(18.dp),tint=p.text)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("BUBBLE SHAPE",fontSize=11.sp,letterSpacing=1.4.sp,color=p.gold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
            listOf("Soft tails","Rounded","Compact").forEachIndexed {i,label->
                val selected=VelvetChatStyle.shapeIndex==i
                OutlinedButton(onClick={VelvetChatStyle.shapeIndex=i;VelvetChatStyle.save(ctx)},
                    contentPadding=PaddingValues(horizontal=10.dp),
                    modifier=Modifier.weight(1f),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=if(selected)p.rose else p.text),
                    border=androidx.compose.foundation.BorderStroke(if(selected)2.dp else 1.dp,
                        if(selected)p.rose else p.border)) {
                    Text(label,fontSize=11.sp,maxLines=1)
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        Text("MESSAGE FONT",fontSize=11.sp,letterSpacing=1.4.sp,color=p.gold)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("Modern","Serif","Mono").forEachIndexed{i,label->
                val selected=VelvetChatStyle.fontIndex==i
                OutlinedButton(onClick={VelvetChatStyle.fontIndex=i;VelvetChatStyle.save(ctx)},
                    modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp),
                    border=androidx.compose.foundation.BorderStroke(if(selected)2.dp else 1.dp,
                        if(selected)p.rose else p.border)) {
                    Text(label,fontFamily=when(i){1->FontFamily.Serif;2->FontFamily.Monospace;else->FontFamily.Default},
                        fontSize=12.sp,color=if(selected)p.rose else p.text)
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        Text("MESSAGE TEXT SIZE · ${VelvetChatStyle.textSize.toInt()}sp",fontSize=11.sp,
            letterSpacing=1.1.sp,color=p.gold)
        Slider(value=VelvetChatStyle.textSize,onValueChange={
                VelvetChatStyle.textSize=it
            },onValueChangeFinished={VelvetChatStyle.save(ctx)},
            valueRange=12f..19f,steps=6,modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text("WALLPAPER DIMMING · ${(VelvetChatStyle.wallpaperDim*100).toInt()}%",fontSize=11.sp,
            letterSpacing=1.1.sp,color=p.gold)
        Slider(value=VelvetChatStyle.wallpaperDim,onValueChange={
                VelvetChatStyle.wallpaperDim=it
            },onValueChangeFinished={VelvetChatStyle.save(ctx)},
            valueRange=.15f.. .8f,modifier=Modifier.fillMaxWidth(),
            enabled=VelvetChatStyle.wallpaperIndex==3)
        Text("Adjusts contrast when using your own photo.",fontSize=11.sp,color=p.muted)
        Spacer(Modifier.height(13.dp))
        OutlinedTextField(value=VelvetChatStyle.partnerNickname,onValueChange={
                if(it.length<=32){VelvetChatStyle.partnerNickname=it;VelvetChatStyle.save(ctx)}
            },label={Text("Your private nickname for your love")},
            placeholder={Text("e.g. Sunshine ♡")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("A nickname on this phone only; it won't edit your partner's profile.",
            color=p.muted,fontSize=11.sp)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(15.dp))){
            ChatWallpaper(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.SpaceEvenly){
                Box(Modifier.align(Alignment.Start).clip(VelvetChatStyle.bubbleShape(false))
                    .background(VelvetChatStyle.incomingColor).padding(horizontal=12.dp,vertical=7.dp)){
                    Text("You make my days brighter ♡",color=p.text,fontFamily=VelvetChatStyle.font,fontSize=(VelvetChatStyle.textSize-2f).sp)
                }
                Box(Modifier.align(Alignment.End).clip(VelvetChatStyle.bubbleShape(true))
                    .background(VelvetChatStyle.outgoingColor).padding(horizontal=12.dp,vertical=7.dp)){
                    Text("Right back at you ♡",color=p.text,fontFamily=VelvetChatStyle.font,fontSize=(VelvetChatStyle.textSize-2f).sp)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text("Preview · changes appear in Chat immediately.",fontSize=11.sp,color=p.muted)
        TextButton(onClick={
            VelvetChatStyle.outgoingIndex=0;VelvetChatStyle.incomingIndex=0
            VelvetChatStyle.shapeIndex=0;VelvetChatStyle.fontIndex=0
            VelvetChatStyle.wallpaperIndex=0;VelvetChatStyle.wallpaperPath=""
            VelvetChatStyle.wallpaperDim=.48f;VelvetChatStyle.textSize=14f
            VelvetChatStyle.partnerNickname=""
            VelvetChatStyle.save(ctx)
        }) {Text("Restore classic chat appearance",color=p.rose)}
        if(savedNotice)Text("Wallpaper saved on this phone ♡",color=p.rose,fontSize=11.sp)
    }
}
