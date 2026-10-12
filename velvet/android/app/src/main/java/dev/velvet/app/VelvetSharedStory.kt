package dev.velvet.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class SharedMoment(val id:String,val path:String,val mime:String,val caption:String,
    val sender:String,val saver:String,val at:String,val duration:Int) {
    companion object {
        fun parse(j:JSONObject)=SharedMoment(j.getString("id"),j.getString("storage_path"),
            j.getString("mime"),j.optString("caption"),j.getString("original_sender"),
            j.getString("saved_by"),j.getString("saved_at"),j.optInt("duration_ms"))
    }
}

@Composable
internal fun VelvetSharedStory(client:VelvetPairClient,onBack:()->Unit,onPair:()->Unit) {
    val scope=rememberCoroutineScope()
    val moments=remember {mutableStateListOf<SharedMoment>()}
    var me by remember {mutableStateOf("")}
    var partner by remember {mutableStateOf("Your love")}
    var ready by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(false)}
    var more by remember {mutableStateOf(true)}
    var beforeAt by remember {mutableStateOf<String?>(null)}
    var beforeId by remember {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf("")}
    var remove by remember {mutableStateOf<SharedMoment?>(null)}
    var checks by remember {mutableIntStateOf(0)}
    val rose=Color(0xFFE4A1B6);val cream=Color(0xFFFFEAF0);val bg=Color(0xFF171019)
    fun load(reset:Boolean=false) {
        if(loading)return
        loading=true;error=""
        val cursorAt=if(reset)null else beforeAt
        val cursorId=if(reset)null else beforeId
        scope.launch {
            try {
                val page=client.sharedStory(cursorAt,cursorId)
                val next=(0 until page.length()).map{SharedMoment.parse(page.getJSONObject(it))}
                if(reset)moments.clear()
                val known=moments.map{it.id}.toSet()
                moments.addAll(next.filter{it.id !in known})
                next.lastOrNull()?.let{beforeAt=it.at;beforeId=it.id}
                if(reset && next.isEmpty()){beforeAt=null;beforeId=null}
                more=page.length()>=20
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(e:Exception) {error=e.message ?: "Couldn't load your shared Story."}
            finally {loading=false}
        }
    }
    LaunchedEffect(client,checks) {
        ready=false;error=""
        if(!client.configured) {error="Connect the backend before sharing Our Story.";return@LaunchedEffect}
        try {
            check(client.hasSession()) {"Sign in and pair your accounts first."}
            val pair=client.myPair()
            check(pair?.optInt("member_count")==2) {"Pair both accounts first."}
            me=pair!!.getString("my_id")
            partner=pair.optString("partner_name").takeUnless{it.isBlank()||it=="null"} ?: "Your love"
            ready=true;load(true)
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(e:Exception) {error=e.message ?: "Couldn't connect."}
    }
    fun name(id:String)=if(id==me)"You" else partner
    Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
        Row {
            TextButton(onClick=onBack){Text("← Back",color=rose)}
            Text("Our shared Story",color=cream,fontSize=24.sp,modifier=Modifier.padding(top=8.dp))
        }
        Text("Only moments you choose to save from connected Chat appear here.",color=rose,fontSize=12.sp)
        if(error.isNotBlank())Text(error,color=cream,modifier=Modifier.padding(vertical=12.dp))
        if(!ready) {
            TextButton(onClick=onPair){Text("Connect our phones",color=rose)}
            TextButton(onClick={checks++}){Text("Retry",color=rose)}
        } else {
            TextButton(onClick={load(true)},enabled=!loading){Text("Refresh shared moments",color=rose)}
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp),
                contentPadding=PaddingValues(bottom=24.dp)) {
                items(moments,key={it.id}) {m->
                    Column(Modifier.fillMaxWidth().background(Color(0xFF30202D),RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Text("Sent by ${name(m.sender)} · Saved by ${name(m.saver)}",color=rose,fontSize=12.sp)
                        Spacer(Modifier.height(10.dp))
                        if(m.mime.startsWith("video/"))VelvetPrivateVideo(client,m.path,m.duration)
                        else VelvetPrivateImage(client,m.path)
                        if(m.caption.isNotBlank())Text(m.caption,color=cream,modifier=Modifier.padding(top=10.dp))
                        if(m.saver==me)TextButton(onClick={remove=m},enabled=!loading){Text("Remove from Our Story",color=rose)}
                    }
                }
                item {
                    if(loading)CircularProgressIndicator(color=rose)
                    else if(moments.isEmpty() && error.isBlank())Text("Long-press a photo or video in connected Chat and choose Save to Our Story ♡",color=cream)
                    if(!loading && (more || error.isNotBlank()))TextButton(onClick={load()}){Text(if(error.isBlank())"Earlier moments" else "Retry loading",color=rose)}
                }
            }
            Text("Saved moments stay here after Chat deletion. Removing one here leaves its Chat message unchanged.",color=rose,fontSize=11.sp)
        }
    }
    remove?.let {entry->
        AlertDialog(onDismissRequest={remove=null},title={Text("Remove this saved moment?")},
            text={Text("It will leave both partners' Our Story. Its original Chat message stays unchanged.")},
            confirmButton={TextButton(onClick={
                remove=null;loading=true
                scope.launch {
                    try {
                        val result=client.removeSharedStory(entry.id)
                        if(result.optBoolean("ok"))moments.removeAll{it.id==entry.id}
                        else error=result.optString("error","Couldn't remove this moment.")
                    } catch(cancelled:CancellationException) {throw cancelled}
                    catch(e:Exception) {error=e.message ?: "Couldn't remove this moment."}
                    finally {loading=false}
                }
            }){Text("Remove")}},dismissButton={TextButton(onClick={remove=null}){Text("Keep")}})
    }
}
