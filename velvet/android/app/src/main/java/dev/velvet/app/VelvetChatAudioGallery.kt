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

private data class ChatAudioItem(
    val id:String,
    val path:String,
    val senderId:String,
    val at:String,
    val durationMs:Int,
    val samples:List<Int>
)

private fun parseChatAudio(json:JSONObject):ChatAudioItem? {
    val path=json.optString("storage_path")
    if(!path.endsWith(".m4a") || path.isBlank() || path=="null") return null
    val duration=json.optInt("duration_ms")
    if(duration !in 500..120000) return null
    val levels=json.optJSONArray("waveform")
    val samples=if(levels==null) emptyList() else
        (0 until levels.length()).take(42).map {index->
            levels.optInt(index).coerceIn(0,100)
        }
    return ChatAudioItem(
        id=json.getString("id"),path=path,
        senderId=json.optString("sender_id"),
        at=json.getString("created_at"),
        durationMs=duration,samples=samples
    )
}

private fun audioDate(value:String):String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a"))
}.getOrDefault("")

/**
 * Chat Info > Audio is only server-synced, couple-private voice notes.
 * No overlap with local prototypes or handpicked Our Story assets.
 */
@Composable
internal fun VelvetChatAudioGallery(
    client:VelvetPairClient,
    myId:String,
    partnerName:String
) {
    val rose=Color(0xFFE4A1B6)
    val cream=Color(0xFFFFEAF0)
    val muted=Color(0xFFD1B7C1)
    val card=Color(0xFF30202D)
    val scope=rememberCoroutineScope()
    val recordings=remember {mutableStateListOf<ChatAudioItem>()}
    var loading by remember {mutableStateOf(false)}
    var fetched by remember {mutableStateOf(false)}
    var hasMore by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf("")}
    // The cursor represents the raw server page, even if a malformed item
    // was filtered from the UI, so history paging cannot repeat that item.
    var cursorAt by remember {mutableStateOf<String?>(null)}
    var cursorId by remember {mutableStateOf<String?>(null)}

    fun loadNext() {
        if(loading || !hasMore) return
        loading=true
        error=""
        val beforeAt=cursorAt
        val beforeId=cursorId
        scope.launch {
            try {
                val rows=client.chatAudio(beforeAt,beforeId,16)
                if(rows.length()>0) {
                    val tail=rows.getJSONObject(rows.length()-1)
                    cursorAt=tail.getString("created_at")
                    cursorId=tail.getString("id")
                }
                val parsed=(0 until rows.length()).mapNotNull {index->
                    parseChatAudio(rows.getJSONObject(index))
                }
                val existing=recordings.map{it.id}.toSet()
                recordings.addAll(parsed.filter{it.id !in existing})
                hasMore=rows.length()>=16
            } catch(e:Exception) {
                error=e.message ?: "Couldn't load the shared voice notes."
            } finally {
                loading=false
                fetched=true
            }
        }
    }

    // The tab's screen is created only when selected; switching away stops
    // and disposes all voice players and their app-private audio temp files.
    LaunchedEffect(Unit) {loadNext()}

    Column(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=10.dp)) {
        Row(Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Our voice notes",
                    color=cream,fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                Text("Chat recordings, saved only in our private conversation.",
                    color=muted,fontSize=11.sp)
            }
            TextButton(onClick={
                if(!loading) {
                    recordings.clear()
                    cursorAt=null
                    cursorId=null
                    hasMore=true
                    fetched=false
                    loadNext()
                }
            },enabled=!loading) {Text("Refresh",color=rose)}
        }
        Spacer(Modifier.height(9.dp))
        LazyColumn(
            modifier=Modifier.fillMaxWidth().weight(1f),
            verticalArrangement=Arrangement.spacedBy(10.dp),
            contentPadding=PaddingValues(bottom=23.dp)
        ) {
            items(recordings.size,key={index->recordings[index].id}) {index->
                val note=recordings[index]
                Column(
                    Modifier.fillMaxWidth()
                        .background(card,RoundedCornerShape(17.dp))
                        .padding(horizontal=13.dp,vertical=11.dp)
                ) {
                    Text(
                        (if(note.senderId==myId) "You" else partnerName)+
                            " · "+audioDate(note.at),
                        color=rose,fontSize=12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    VelvetVoicePlayer(
                        client=client,
                        storagePath=note.path,
                        durationMs=note.durationMs,
                        waveform=note.samples
                    )
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical=12.dp),
                    horizontalAlignment=Alignment.CenterHorizontally
                ) {
                    if(loading) {
                        CircularProgressIndicator(color=rose)
                    } else {
                        if(fetched && recordings.isEmpty() && error.isBlank()) {
                            Text("No voice notes shared yet ♡",
                                color=cream,fontSize=14.sp)
                        }
                        if(error.isNotBlank()) {
                            Text(error,color=rose,fontSize=12.sp)
                        }
                        if(hasMore) {
                            TextButton(onClick={loadNext()}) {
                                Text(if(error.isBlank()) "Load older voice notes"
                                     else "Try again",color=rose)
                            }
                        }
                    }
                }
            }
        }
    }
}
