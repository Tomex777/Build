package dev.velvet.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private data class ChatVideoItem(
    val id:String,
    val path:String,
    val senderId:String,
    val at:String,
    val caption:String,
    val durationMs:Int
)

private fun parseVideoItem(json:JSONObject):ChatVideoItem? {
    val path=json.optString("storage_path")
    val ms=json.optInt("duration_ms")
    if(path.isBlank() || !path.endsWith(".mp4") || ms !in 100..120000)return null
    return ChatVideoItem(
        id=json.getString("id"),
        path=path,
        senderId=json.optString("sender_id"),
        at=json.getString("created_at"),
        caption=json.optString("caption").takeUnless{it=="null"} ?: "",
        durationMs=ms
    )
}

private fun videoDate(iso:String)=runCatching {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a"))
}.getOrDefault("")

@Composable
internal fun VelvetChatVideoGallery(
    client:VelvetPairClient,
    myId:String,
    partnerName:String
) {
    val rose=Color(0xFFE4A1B6)
    val cream=Color(0xFFFFEAF0)
    val muted=Color(0xFFD1B7C1)
    val card=Color(0xFF30202D)
    val scope=rememberCoroutineScope()
    val videos=remember {mutableStateListOf<ChatVideoItem>()}
    var loading by remember {mutableStateOf(false)}
    var fetched by remember {mutableStateOf(false)}
    var more by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf("")}
    var cursorAt by remember {mutableStateOf<String?>(null)}
    var cursorId by remember {mutableStateOf<String?>(null)}

    fun loadMore() {
        if(loading || !more)return
        loading=true
        error=""
        val fromAt=cursorAt
        val fromId=cursorId
        scope.launch {
            try {
                val page=client.chatVideos(fromAt,fromId,12)
                if(page.length()>0) {
                    val last=page.getJSONObject(page.length()-1)
                    cursorAt=last.getString("created_at")
                    cursorId=last.getString("id")
                }
                val parsed=(0 until page.length()).mapNotNull {
                    parseVideoItem(page.getJSONObject(it))
                }
                val known=videos.map{it.id}.toSet()
                videos.addAll(parsed.filter{it.id !in known})
                more=page.length()>=12
            } catch(e:Exception) {
                error=e.message ?: "Couldn't load your shared chat videos."
            } finally {
                fetched=true
                loading=false
            }
        }
    }
    LaunchedEffect(Unit) {loadMore()}
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Our shared videos",color=cream,
                    fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text("Private Chat clips · Nothing auto-saves to Our Story.",
                    color=muted,fontSize=11.sp)
            }
            TextButton(onClick={
                if(!loading) {
                    videos.clear()
                    cursorAt=null
                    cursorId=null
                    more=true
                    fetched=false
                    loadMore()
                }
            },enabled=!loading) {Text("Refresh",color=rose)}
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier=Modifier.fillMaxWidth().weight(1f),
            contentPadding=PaddingValues(bottom=26.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)
        ) {
            items(videos.size,key={i->videos[i].id}) {index->
                val clip=videos[index]
                Column(
                    Modifier.fillMaxWidth()
                        .background(card,RoundedCornerShape(18.dp))
                        .padding(13.dp)
                ) {
                    Text((if(clip.senderId==myId)"You" else partnerName)+
                        " · "+videoDate(clip.at),color=rose,fontSize=12.sp)
                    Spacer(Modifier.height(8.dp))
                    VelvetPrivateVideo(client,clip.path,clip.durationMs)
                    if(clip.caption.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(clip.caption,color=cream,fontSize=13.sp)
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical=12.dp),
                    horizontalAlignment=Alignment.CenterHorizontally
                ) {
                    if(loading) CircularProgressIndicator(color=rose)
                    else {
                        if(fetched && videos.isEmpty() && error.isBlank())
                            Text("No videos shared yet ♡",color=cream)
                        if(error.isNotBlank())
                            Text(error,color=rose,fontSize=12.sp)
                        if(more) {
                            TextButton(onClick={loadMore()}) {
                                Text(
                                    if(error.isBlank())"Load older videos" else "Retry",
                                    color=rose
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
