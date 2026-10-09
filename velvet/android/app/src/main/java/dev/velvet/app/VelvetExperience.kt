package dev.velvet.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class VelvetPalette(
    val name:String, val bg:Color, val paper:Color, val raised:Color,
    val border:Color, val rose:Color, val roseDark:Color, val text:Color,
    val muted:Color, val gold:Color, val mood:String
)
internal object VelvetTheme {
    val presets = listOf(
        VelvetPalette("Midnight Rose",Color(0xFF170F15),Color(0xFF291920),Color(0xFF39232E),Color(0xFF62424D),Color(0xFFF0B4C3),Color(0xFFB97B90),Color(0xFFFFEAE7),Color(0xFFC8A9B0),Color(0xFFE6C6A1),"Warm · Romantic"),
        VelvetPalette("Ocean Hearts",Color(0xFF0D1922),Color(0xFF172A34),Color(0xFF24414D),Color(0xFF3E626E),Color(0xFF8BCFD2),Color(0xFF51949A),Color(0xFFEAF6F4),Color(0xFFADC8CB),Color(0xFFE4C8A9),"Calm · Coastal"),
        VelvetPalette("Lavender Dreams",Color(0xFF1A1525),Color(0xFF292238),Color(0xFF382F4B),Color(0xFF605277),Color(0xFFD7B9F6),Color(0xFF9F82C5),Color(0xFFF8EFFC),Color(0xFFC9B9D7),Color(0xFFE4C4BE),"Soft · Dreamy"),
        VelvetPalette("Obsidian Love",Color(0xFF101114),Color(0xFF202125),Color(0xFF2D2D33),Color(0xFF52525B),Color(0xFFE5A3AB),Color(0xFFAB646E),Color(0xFFF4F2F1),Color(0xFFB6B3B8),Color(0xFFCFBCAF),"Elegant · Minimal"),
        VelvetPalette("Peach Blossom",Color(0xFF231A1B),Color(0xFF352528),Color(0xFF473039),Color(0xFF765159),Color(0xFFFFC1AD),Color(0xFFD58C80),Color(0xFFFFF0E5),Color(0xFFE0BEB8),Color(0xFFEAC99E),"Cozy · Playful"),
        VelvetPalette("Evergreen",Color(0xFF121C18),Color(0xFF21312A),Color(0xFF2F493A),Color(0xFF4E705B),Color(0xFFAED8B8),Color(0xFF77A98A),Color(0xFFEAF4E8),Color(0xFFB9CDBC),Color(0xFFE0C9A4),"Peaceful · Natural")
    )
    var selected by mutableStateOf("Midnight Rose")
    val current:VelvetPalette get() = presets.find { it.name == selected } ?: presets.first()
}

@Composable
internal fun VelvetStudioScreen(note:String,onNoteChange:(String)->Unit,onThemeChange:(String)->Unit,onBack:()->Unit) {
    var draft by remember(note) { mutableStateOf(note) }
    val palette=VelvetTheme.current
    val strokes=remember { mutableStateListOf<List<Offset>>() }
    Column(Modifier.fillMaxSize().background(palette.bg).verticalScroll(rememberScrollState()).padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=9.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Outlined.ArrowBack,"Back",tint=palette.text) }
            Spacer(Modifier.width(7.dp))
            Text("Our Studio",fontFamily=FontFamily.Serif,fontSize=27.sp,color=palette.text)
        }
        Text("Make this space feel like the two of you.",color=palette.muted,fontSize=13.sp)
        Spacer(Modifier.height(20.dp))
        Text("MY THEME",color=palette.gold,fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.SemiBold)
        Text("Your choice changes the colors on this phone only.",color=palette.muted,fontSize=12.sp)
        Spacer(Modifier.height(12.dp))
        VelvetTheme.presets.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    val selected=VelvetTheme.selected==preset.name
                    Column(Modifier.weight(1f).padding(bottom=10.dp).clip(RoundedCornerShape(18.dp))
                        .background(preset.paper).border(if(selected)2.dp else 1.dp,if(selected)preset.rose else preset.border,RoundedCornerShape(18.dp))
                        .clickable {onThemeChange(preset.name)}.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(preset.bg),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                            listOf(preset.rose,preset.raised,preset.gold).forEach { c ->
                                Box(Modifier.size(21.dp).clip(CircleShape).background(c))
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(preset.name,color=preset.text,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                        Text(preset.mood,color=preset.muted,fontSize=10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("OUR LITTLE NOTE",color=palette.gold,fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.paper).padding(16.dp)) {
            Text("A line for your shared Home",color=palette.text,fontFamily=FontFamily.Serif,fontSize=22.sp)
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(value=draft,onValueChange={if(it.length<=110)draft=it},
                label={Text("Write something sweet")},minLines=2,maxLines=3,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Button(onClick={onNoteChange(draft.trim())},enabled=draft!=note,modifier=Modifier.align(Alignment.End)){
                Text("Save to Home")
            }
            Text("Currently saved on your phone; shared designs will sync once your partner is connected.",
                fontSize=11.sp,color=palette.muted)
        }
        Spacer(Modifier.height(20.dp))
        Text("DRAW SOMETHING",color=palette.gold,fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.SemiBold)
        Text("A little finger-drawing pad · local preview",color=palette.muted,fontSize=12.sp)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(224.dp).clip(RoundedCornerShape(22.dp)).background(palette.paper)
            .border(1.dp,palette.border,RoundedCornerShape(22.dp))) {
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(onDragStart={ start -> strokes.add(listOf(start)) },
                    onDrag={ change,_ ->
                        change.consume()
                        if(strokes.isNotEmpty()) strokes[strokes.lastIndex]=strokes.last()+change.position
                    })
            }) {
                strokes.forEach { points ->
                    if(points.size==1)drawCircle(palette.rose,4f,points[0])
                    else for(i in 1 until points.size)drawLine(palette.rose,points[i-1],points[i],strokeWidth=8f)
                }
            }
            if(strokes.isEmpty()) Text("Draw a little something ♡",color=palette.muted,
                modifier=Modifier.align(Alignment.Center),fontSize=13.sp)
        }
        TextButton(onClick={strokes.clear()},modifier=Modifier.align(Alignment.End)){Text("Clear drawing")}
        Text("Drawing export and delivery will be enabled with the Azure-backed shared studio.",
            fontSize=11.sp,color=palette.muted)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun VelvetCallPreview(partner:String,video:Boolean,onBack:()->Unit) {
    val p=VelvetTheme.current
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(video) }
    var front by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.raised,p.bg,p.paper)))
        .padding(horizontal=20.dp,vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Back",tint=p.text)}
            Spacer(Modifier.weight(1f))
            Text(if(video)"VIDEO CALL" else "VOICE CALL",color=p.gold,fontSize=11.sp,letterSpacing=2.sp)
            Spacer(Modifier.weight(1f));Spacer(Modifier.width(42.dp))
        }
        Spacer(Modifier.weight(.45f))
        Box(Modifier.size(146.dp).clip(CircleShape).background(p.paper).border(2.dp,p.rose.copy(alpha=.7f),CircleShape),
            contentAlignment=Alignment.Center) {
            Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(65.dp),tint=p.rose)
        }
        Spacer(Modifier.height(25.dp))
        Text(partner,color=p.text,fontSize=36.sp,fontFamily=FontFamily.Serif,textAlign=TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("A little closer, even far away.",color=p.muted,fontSize=14.sp)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.paper).padding(16.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Info,null,tint=p.gold)
            Spacer(Modifier.height(5.dp))
            Text("Call screen preview",color=p.text,fontWeight=FontWeight.SemiBold)
            Text("This screen doesn't place calls yet. Live audio/video needs pairing and a WebRTC call service.",
                color=p.muted,fontSize=12.sp,lineHeight=18.sp,textAlign=TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            @Composable fun CallControl(label:String,selected:Boolean,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    IconButton(onClick=click,modifier=Modifier.size(60.dp).clip(CircleShape).background(if(selected)p.rose else p.raised)) {
                        Icon(icon,label,tint=if(selected)p.bg else p.text)
                    }
                    Text(label,color=p.muted,fontSize=11.sp)
                }
            }
            CallControl(if(muted)"Unmute" else "Mute",muted,if(muted)Icons.Outlined.MicOff else Icons.Outlined.Mic) {muted=!muted}
            CallControl("Speaker",speaker,Icons.Outlined.VolumeUp) {speaker=!speaker}
            if(video){
                CallControl(if(camera)"Camera on" else "Camera off",camera,if(camera)Icons.Outlined.Videocam else Icons.Outlined.VideocamOff){camera=!camera}
                CallControl(if(front)"Front" else "Rear",!front,Icons.Outlined.Cameraswitch){front=!front}
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick=onBack,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB94759)),
            modifier=Modifier.fillMaxWidth(.64f).height(54.dp)) {
            Icon(Icons.Outlined.CallEnd,null)
            Spacer(Modifier.width(10.dp))
            Text("Leave preview")
        }
        Spacer(Modifier.height(18.dp))
    }
}
