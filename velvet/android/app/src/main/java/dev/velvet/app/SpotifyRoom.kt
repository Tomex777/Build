package dev.velvet.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

internal data class VelvetSong(val title:String,val artist:String,val spotifyUrl:String,val note:String)
internal class VelvetSoundtrack(private val ctx:Context) {
    private val prefs=ctx.getSharedPreferences("velvet_soundtrack_v1",Context.MODE_PRIVATE)
    val songs=mutableStateListOf<VelvetSong>()
    var jamUrl by mutableStateOf(prefs.getString("jam","") ?: "")
    init {
        runCatching {
            val json=JSONArray(prefs.getString("songs","[]"))
            for(i in 0 until json.length()) {
                val obj=json.getJSONObject(i)
                songs.add(VelvetSong(obj.optString("title"),obj.optString("artist"),
                    obj.optString("url"),obj.optString("note")))
            }
        }
    }
    fun save() {
        val array=JSONArray()
        songs.forEach { array.put(JSONObject().put("title",it.title).put("artist",it.artist)
            .put("url",it.spotifyUrl).put("note",it.note)) }
        prefs.edit().putString("songs",array.toString()).putString("jam",jamUrl).apply()
    }
    fun add(song:VelvetSong){songs.add(0,song);save()}
    fun delete(index:Int){if(index in songs.indices){songs.removeAt(index);save()}}
}
internal fun isSpotifyUrl(text:String):Boolean {
    val url=runCatching{Uri.parse(text.trim())}.getOrNull() ?: return false
    if(url.scheme?.lowercase()!="https")return false
    return url.host?.lowercase() in setOf("open.spotify.com","spotify.link","www.spotify.com","spotify.com")
}
internal fun openSpotify(context:Context,url:String) {
    if(!isSpotifyUrl(url))return
    val intent=Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching{context.startActivity(intent)}
}
internal fun shareSpotify(context:Context,url:String,title:String) {
    if(!isSpotifyUrl(url))return
    val intent=Intent(Intent.ACTION_SEND).apply {
        type="text/plain"
        putExtra(Intent.EXTRA_TEXT, "${title.trim()} ♡\n$url")
    }
    context.startActivity(Intent.createChooser(intent,"Share with your love"))
}
@Composable
internal fun SpotifyListeningRoom(onBack:()->Unit) {
    val ctx=LocalContext.current
    val p=VelvetTheme.current
    val soundtrack=remember {VelvetSoundtrack(ctx)}
    var showAdd by remember { mutableStateOf(false) }
    var showJam by remember { mutableStateOf(false) }
    var title by remember {mutableStateOf("")}
    var artist by remember {mutableStateOf("")}
    var url by remember {mutableStateOf("")}
    var note by remember {mutableStateOf("")}
    var pendingJam by remember {mutableStateOf(soundtrack.jamUrl)}
    val last=soundtrack.songs.firstOrNull()
    Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState())
        .padding(horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.fillMaxWidth().padding(top=7.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,modifier=Modifier.size(44.dp).clip(CircleShape).background(p.raised)) {
                Icon(Icons.Outlined.ArrowBack,"Back",tint=p.text)
            }
            Spacer(Modifier.width(12.dp))
            Text("Listen together",color=p.text,fontFamily=FontFamily.Serif,fontSize=29.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Our soundtrack ♡",color=p.rose,fontSize=13.sp)
        Spacer(Modifier.height(19.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF503649),Color(0xFF302631),p.paper)))
            .padding(23.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Box(Modifier.fillMaxWidth().height(194.dp).clip(RoundedCornerShape(23.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF986D82),Color(0xFF513B65),Color(0xFF223F49)))),
                contentAlignment=Alignment.Center){
                Icon(Icons.Outlined.MusicNote,null,Modifier.size(87.dp),tint=Color.White.copy(alpha=.8f))
                Text("YOU  ♡  YOUR LOVE",modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=13.dp),
                    color=Color.White.copy(alpha=.9f),fontSize=10.sp,letterSpacing=1.5.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text(last?.title ?: "A soundtrack for our story",color=p.text,
                fontFamily=FontFamily.Serif,fontSize=25.sp,textAlign=TextAlign.Center)
            Text(last?.artist ?: "Add the first song that feels like us",color=p.muted,fontSize=13.sp,
                textAlign=TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            if(last!=null)Button(onClick={openSpotify(ctx,last.spotifyUrl)},modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Open song in Spotify")
            } else Button(onClick={showAdd=true},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Add our first song")
            }
            Spacer(Modifier.height(9.dp))
            Text("Spotify plays the music. Velvet stores your dedications, not the audio.",
                color=p.muted,fontSize=11.sp,textAlign=TextAlign.Center,lineHeight=16.sp)
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick={showJam=true},modifier=Modifier.weight(1f)){
                Icon(Icons.Outlined.Group,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Spotify Jam")
            }
            OutlinedButton(onClick={showAdd=true},modifier=Modifier.weight(1f)){
                Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Dedicate")
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.paper)
            .padding(14.dp)) {
            Text("How our listening room works",color=p.text,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
            Spacer(Modifier.height(6.dp))
            Text("Start a Jam in Spotify → invite your partner → paste the Jam link here. "+
                "Remote Spotify Jam requires Premium for both listeners. "+
                "Velvet does not control Spotify playback or send notifications yet.",
                color=p.muted,fontSize=12.sp,lineHeight=19.sp)
            if(isSpotifyUrl(soundtrack.jamUrl)){
                Spacer(Modifier.height(10.dp))
                TextButton(onClick={openSpotify(ctx,soundtrack.jamUrl)}){
                    Icon(Icons.Outlined.OpenInNew,null,Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp));Text("Open saved Jam invitation")
                }
                TextButton(onClick={shareSpotify(ctx,soundtrack.jamUrl,"Come listen with me")}){
                    Icon(Icons.Outlined.Share,null,Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp));Text("Share Jam invitation")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Songs we keep",color=p.text,fontSize=27.sp,fontFamily=FontFamily.Serif,
                modifier=Modifier.weight(1f))
            Text("${soundtrack.songs.size} saved",color=p.muted,fontSize=11.sp)
        }
        Spacer(Modifier.height(11.dp))
        if(soundtrack.songs.isEmpty()){
            Text("Songs you dedicate will appear here and stay on this phone until pairing is ready.",
                color=p.muted,fontSize=13.sp)
        }
        soundtrack.songs.toList().forEachIndexed {index,song ->
            Row(Modifier.fillMaxWidth().padding(bottom=9.dp).clip(RoundedCornerShape(17.dp))
                .background(p.paper).clickable{openSpotify(ctx,song.spotifyUrl)}
                .padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(51.dp).clip(RoundedCornerShape(12.dp))
                    .background(p.raised),contentAlignment=Alignment.Center) {
                    Icon(Icons.Outlined.MusicNote,null,tint=p.rose)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)){
                    Text(song.title,color=p.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text(song.artist,color=p.muted,fontSize=11.sp)
                    if(song.note.isNotBlank())Text("♡ ${song.note}",color=p.rose,fontSize=11.sp,maxLines=2)
                }
                IconButton(onClick={shareSpotify(ctx,song.spotifyUrl,song.title)}) {
                    Icon(Icons.Outlined.Share,"Share song",tint=p.rose,modifier=Modifier.size(18.dp))
                }
                IconButton(onClick={soundtrack.delete(index)},modifier=Modifier.size(32.dp)){
                    Icon(Icons.Outlined.Close,"Remove song",tint=p.muted,modifier=Modifier.size(17.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if(showJam) AlertDialog(onDismissRequest={showJam=false},
        title={Text("Spotify Jam ♡")},
        text={Column{
            Text("Start a Jam in Spotify, tap Invite → Share link, then paste that link here.",
                color=p.muted,fontSize=12.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value=pendingJam,onValueChange={pendingJam=it.trim()},
                label={Text("Jam invite link")},modifier=Modifier.fillMaxWidth(),maxLines=3)
            if(pendingJam.isNotBlank()&&!isSpotifyUrl(pendingJam))
                Text("Enter a genuine Spotify link.",color=p.rose,fontSize=11.sp)
        }},
        confirmButton={TextButton(onClick={
            soundtrack.jamUrl=pendingJam;soundtrack.save();showJam=false
        },enabled=pendingJam.isBlank()||isSpotifyUrl(pendingJam)){Text("Save invitation")}},
        dismissButton={TextButton(onClick={showJam=false}){Text("Cancel")}},
        containerColor=p.paper)
    if(showAdd) AlertDialog(onDismissRequest={showAdd=false},
        title={Text("Dedicate a song ♡")},
        text={Column {
            Text("Add a Spotify song link and a note. This stays on your device for now.",
                color=p.muted,fontSize=12.sp)
            OutlinedTextField(value=title,onValueChange={title=it.take(90)},label={Text("Song title")},
                modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(value=artist,onValueChange={artist=it.take(90)},label={Text("Artist")},
                modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(value=url,onValueChange={url=it.trim()},label={Text("Spotify song link")},
                modifier=Modifier.fillMaxWidth(),maxLines=2)
            OutlinedTextField(value=note,onValueChange={note=it.take(160)},
                label={Text("Why it reminds you of them (optional)")},modifier=Modifier.fillMaxWidth(),maxLines=3)
            if(url.isNotEmpty()&&!isSpotifyUrl(url))
                Text("Please use a Spotify link.",color=p.rose,fontSize=11.sp)
        }},
        confirmButton={TextButton(onClick={
            soundtrack.add(VelvetSong(title.trim(),artist.trim(),url.trim(),note.trim()))
            title="";artist="";url="";note="";showAdd=false
        },enabled=title.isNotBlank()&&artist.isNotBlank()&&isSpotifyUrl(url)){Text("Keep this song")}},
        dismissButton={TextButton(onClick={showAdd=false}){Text("Cancel")}},
        containerColor=p.paper)
}
