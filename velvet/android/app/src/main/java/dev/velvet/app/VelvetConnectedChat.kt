package dev.velvet.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private data class ConnectedText(
    val id:String,
    val body:String,
    val senderId:String,
    val at:String,
    val pending:Boolean=false,
    val replyTo:String?=null
)

/**
 * Dedicated scoped outbox: prevents retrying messages from one account/couple
 * under a different account after sign-out or phone re-pairing.
 * Stored only in app-private preferences (device backup is disabled).
 */
private class ConnectedTextOutbox(context:Context) {
    private val prefs=context.getSharedPreferences("velvet_connected_outbox",Context.MODE_PRIVATE)
    fun get(scope:String):List<ConnectedText> = runCatching {
        val arr=JSONArray(prefs.getString(scope,"[]"))
        (0 until arr.length()).map {i->
            val o=arr.getJSONObject(i)
            ConnectedText(o.getString("id"),o.getString("body"),"",o.optString("at"),
                true,o.optString("reply_to").takeIf{it.isNotBlank()})
        }
    }.getOrDefault(emptyList())
    fun add(scope:String, entry:ConnectedText) = save(scope,get(scope)+entry)
    fun drop(scope:String,id:String) = save(scope,get(scope).filterNot{it.id==id})
    private fun save(scope:String,items:List<ConnectedText>) {
        val arr=JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id",it.id).put("body",it.body)
                .put("at",it.at).put("reply_to",it.replyTo ?: ""))
        }
        // commit() before showing success avoids losing a queued message on fast exits.
        check(prefs.edit().putString(scope,arr.toString()).commit()) {
            "Could not save your outgoing message on this phone."
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VelvetConnectedChat(
    onBack:()->Unit,onOpenLocalPreview:(()->Unit)?=null,sharedClient:VelvetPairClient?=null
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val rememberedClient=remember {VelvetPairClient(context.applicationContext)}
    val client=sharedClient ?: rememberedClient
    val scope=rememberCoroutineScope()
    val outbox=remember {ConnectedTextOutbox(context.applicationContext)}
    val messages=remember {mutableStateListOf<ConnectedText>()}
    val queued=remember {mutableStateListOf<ConnectedText>()}
    val listState=rememberLazyListState()
    var ownId by remember {mutableStateOf("")}
    var partnerName by remember {mutableStateOf("Your love")}
    var outboxScope by remember {mutableStateOf("")}
    var status by remember {mutableStateOf("Connecting…")}
    var text by remember {mutableStateOf("")}
    var ready by remember {mutableStateOf(false)}
    var replyingTo by remember {mutableStateOf<ConnectedText?>(null)}
    var historyLoading by remember {mutableStateOf(false)}
    var historyError by remember {mutableStateOf("")}
    var hasOlder by remember {mutableStateOf(false)}
    var oldestAt by remember {mutableStateOf<String?>(null)}
    var oldestId by remember {mutableStateOf<String?>(null)}
    val bg=Color(0xFF171019)
    val card=Color(0xFF30202D)
    val rose=Color(0xFFE4A1B6)
    val cream=Color(0xFFFFEAF0)
    val muted=Color(0xFFD1B7C1)

    // Foreground-only pull, about every 3s. This is NOT push/Realtime WebSockets.
    // The UUID cursor and persisted outbox make retries idempotent and loss-resistant.
    LaunchedEffect(Unit) {
        if(!client.configured) {
            status="Online chat is not configured. Your local preview is still available."
            return@LaunchedEffect
        }
        if(!client.hasSession()) {
            status="Please sign in through Connect our phones first."
            return@LaunchedEffect
        }
        try {
            val pair=client.myPair()
            if(pair?.optInt("member_count")!=2) {
                status="Pair both accounts before opening connected chat."
                return@LaunchedEffect
            }
            val me=pair.optString("my_id")
            val couple=pair.optString("couple_id")
            if(me.isBlank() || couple.isBlank()) {
                status="Update the chat database migration before opening connected chat."
                return@LaunchedEffect
            }
            ownId=me
            outboxScope="outbox_${me}_${couple}"
            partnerName=pair.optString("partner_name").takeIf{
                it.isNotBlank() && it!="null"
            } ?: "Your love"
            queued.clear()
            queued.addAll(outbox.get(outboxScope))
            ready=true
        } catch(e:Exception) {
            status=e.message ?: "Could not read the couple's account."
            return@LaunchedEffect
        }

        var lastAt:String?=null
        var lastId:String?=null
        while(isActive) {
            try {
                var sendError:String?=null
                for(entry in queued.toList()) {
                    val result=client.sendText(UUID.fromString(entry.id),entry.body,entry.replyTo)
                    if(result.optBoolean("ok")) {
                        outbox.drop(outboxScope,entry.id)
                        queued.removeAll {it.id==entry.id}
                    } else {
                        sendError=when(result.optString("error")) {
                            "rate_limited"->"Sending limited for a minute. Your message is queued."
                            "not_paired"->"This couple is no longer paired."
                            else->"One message is queued for retry."
                        }
                        // Don't try more sends while the server is rate limiting.
                        break
                    }
                }
                val fresh=client.listText(lastAt,lastId)
                if(lastAt==null && fresh.length()>0) {
                    hasOlder=fresh.length()>=80
                    oldestAt=fresh.getJSONObject(0).getString("created_at")
                    oldestId=fresh.getJSONObject(0).getString("id")
                }
                for(i in 0 until fresh.length()) {
                    val item=fresh.getJSONObject(i)
                    val id=item.getString("id")
                    if(messages.none{it.id==id}) {
                        messages.add(ConnectedText(id,item.optString("body"),
                            item.optString("sender_id"),item.optString("created_at"),
                            replyTo=item.optString("reply_to").takeIf { it.isNotBlank() && it!="null" }))
                    }
                    lastAt=item.getString("created_at")
                    lastId=id
                }
                status=sendError ?: "Connected · updates while this screen is open"
            } catch(e:Exception) {
                status="Offline · outgoing messages stay queued. ${e.message ?: "Retrying…"}"
            }
            delay(3000)
        }
    }

    val combined=messages.toList()+queued.toList()
    LaunchedEffect(combined.size) {
        if(combined.isNotEmpty()) {
            val lastVisible=listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if(lastVisible<0 || lastVisible>=combined.lastIndex-3) {
                listState.animateScrollToItem(combined.lastIndex)
            }
        }
    }
    Column(Modifier.fillMaxSize().background(bg).imePadding()) {
        Row(Modifier.fillMaxWidth().background(card).padding(12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Outlined.ArrowBack,"Back",tint=cream) }
            Column(Modifier.weight(1f)) {
                Text(partnerName,color=cream,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                Text("Our connected text chat ♡",color=rose,fontSize=11.sp)
            }
            if(onOpenLocalPreview!=null) {
                TextButton(onClick=onOpenLocalPreview) {
                    Text("Local preview",color=rose,fontSize=11.sp)
                }
            }
        }
        Text(status,Modifier.fillMaxWidth().padding(horizontal=17.dp,vertical=8.dp),
            color=muted,fontSize=11.sp,lineHeight=15.sp)
        if(!ready) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(25.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center) {
                Text("Our messages, once we're connected.",color=cream,fontSize=20.sp)
                Spacer(Modifier.height(12.dp))
                Text("Connect both accounts to begin exchanging text across phones.",
                    color=muted,fontSize=13.sp)
            }
        } else {
            if(hasOlder || historyLoading) {
                TextButton(
                    onClick={
                        if(!historyLoading && oldestAt!=null && oldestId!=null) {
                            historyLoading=true
                            historyError=""
                            scope.launch {
                                try {
                                    val older=client.pageText(oldestAt,oldestId)
                                    val fetched=(0 until older.length()).map { i ->
                                        val row=older.getJSONObject(i)
                                        ConnectedText(
                                            id=row.getString("id"),
                                            body=row.optString("body"),
                                            senderId=row.optString("sender_id"),
                                            at=row.getString("created_at"),
                                            replyTo=row.optString("reply_to")
                                                .takeIf {it.isNotBlank() && it!="null"}
                                        )
                                    }
                                    val before=messages.map {it.id}.toSet()
                                    val unseen=fetched.filter {it.id !in before}
                                    if(unseen.isNotEmpty()) messages.addAll(0,unseen)
                                    if(fetched.isNotEmpty()) {
                                        oldestAt=fetched.first().at
                                        oldestId=fetched.first().id
                                    }
                                    hasOlder=fetched.size>=80
                                } catch(e:Exception) {
                                    historyError=e.message ?: "Could not load older messages."
                                } finally {historyLoading=false}
                            }
                        }
                    },
                    modifier=Modifier.fillMaxWidth(),
                    enabled=!historyLoading
                ) { Text(if(historyLoading) "Loading older messages…" else "↑ Earlier messages",color=rose) }
            }
            if(historyError.isNotEmpty()) {
                Text(historyError,modifier=Modifier.padding(horizontal=18.dp),
                    color=muted,fontSize=12.sp)
            }
            LazyColumn(
                modifier=Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp),
                state=listState,
                contentPadding=PaddingValues(vertical=14.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                items(combined,key={it.id}) { msg->
                    val mine=msg.pending || msg.senderId==ownId
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=if(mine) Arrangement.End else Arrangement.Start) {
                        Column(
                            Modifier.widthIn(max=290.dp)
                                .background(if(mine) Color(0xFF60354C) else card,
                                    RoundedCornerShape(18.dp))
                                .combinedClickable(onClick={replyingTo=msg},onLongClick={replyingTo=msg})
                                .padding(horizontal=14.dp,vertical=10.dp)
                        ) {
                            if(!msg.replyTo.isNullOrBlank()) {
                                val source=combined.firstOrNull {it.id==msg.replyTo}
                                Text("↳ "+(source?.body ?: "Earlier message"),
                                    color=rose,fontSize=12.sp,maxLines=2,
                                    modifier=Modifier.fillMaxWidth()
                                        .background(Color(0xFF4A2C3E),RoundedCornerShape(9.dp))
                                        .padding(8.dp))
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(msg.body,color=cream,fontSize=15.sp,lineHeight=21.sp)
                            Spacer(Modifier.height(4.dp))
                            val time=runCatching {
                                OffsetDateTime.parse(msg.at).format(DateTimeFormatter.ofPattern("HH:mm"))
                            }.getOrDefault("")
                            Text(if(msg.pending) "Queued · ${time}" else time,
                                color=muted,fontSize=10.sp)
                            if(msg.pending) {
                                TextButton(onClick={
                                    try {
                                        outbox.drop(outboxScope,msg.id)
                                        queued.removeAll {it.id==msg.id}
                                        if(replyingTo?.id==msg.id) replyingTo=null
                                        status="Local retry discarded. This does not delete messages received by your partner."
                                    } catch(e:Exception) {
                                        status=e.message ?: "Couldn't discard this queued message."
                                    }
                                }) {
                                    Text("Discard retry",fontSize=10.sp,color=rose)
                                }
                            }
                        }
                    }
                }
            }
        }
        if(ready) {
            replyingTo?.let { target ->
                Row(Modifier.fillMaxWidth().background(Color(0xFF40283A))
                    .padding(start=18.dp,end=10.dp,top=7.dp,bottom=7.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Replying to",color=rose,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                        Text(target.body,color=cream,fontSize=12.sp,maxLines=2)
                    }
                    IconButton(onClick={replyingTo=null}) {
                        Icon(Icons.Outlined.Close,"Cancel reply",tint=cream)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(card)
                    .padding(horizontal=12.dp,vertical=10.dp),
                verticalAlignment=Alignment.Bottom) {
                OutlinedTextField(
                    value=text,
                    onValueChange={ if(it.length<=4000) text=it },
                    placeholder={Text("Message your person…",color=muted)},
                    maxLines=5,
                    modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(21.dp)
                )
                Spacer(Modifier.width(7.dp))
                IconButton(onClick={
                    val body=text.trim()
                    if(body.isNotEmpty() && outboxScope.isNotEmpty()) {
                        val entry=ConnectedText(UUID.randomUUID().toString(),body,ownId,
                            OffsetDateTime.now().toString(),pending=true,
                            replyTo=replyingTo?.id)
                        try {
                            outbox.add(outboxScope,entry)
                            queued.add(entry)
                            text=""
                            replyingTo=null
                            status="Message saved locally, sending…"
                        } catch(e:Exception) {
                            status=e.message ?: "Couldn't queue this message."
                        }
                    }
                }, enabled=text.isNotBlank()) {
                    Icon(Icons.Outlined.Send,"Send",tint=rose)
                }
            }
        }
        Text("Tap a message to reply · Text only · No end-to-end encryption · Media and games aren't synced",
            color=muted,fontSize=10.sp,modifier=Modifier.fillMaxWidth()
                .background(card).padding(start=16.dp,end=16.dp,bottom=8.dp))
    }
}
