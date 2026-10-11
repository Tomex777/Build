package dev.velvet.app

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
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

private data class VelvetGalleryPhoto(
    val id:String,val path:String,val senderId:String,val caption:String,
    val createdAt:String
)
private data class VelvetSearchHit(
    val id:String,val senderId:String,val kind:String,val body:String,
    val createdAt:String
)
private data class VelvetChatLink(
    val id:String,val url:String,val senderId:String,val createdAt:String
)

private fun velvetDate(value:String):String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
}.getOrDefault("")

private fun readGalleryPhoto(json:JSONObject):VelvetGalleryPhoto? {
    val path=json.optString("storage_path")
    if(path.isBlank() || path=="null") return null
    return VelvetGalleryPhoto(
        json.getString("id"),path,json.optString("sender_id"),
        json.optString("caption").takeUnless{it=="null"} ?: "",
        json.getString("created_at")
    )
}

/**
 * Connected Chat Info is a full-screen destination, not a bottom sheet.
 * Private chat images are shown here, NEVER copied into Our Story.
 * Only the authenticated couple can obtain this data through scoped RPCs.
 */
@Composable
internal fun VelvetConnectedChatInfo(
    client:VelvetPairClient,
    partnerName:String,
    myId:String,
    onBack:()->Unit
) {
    val rose=Color(0xFFE4A1B6)
    val cream=Color(0xFFFFEAF0)
    val muted=Color(0xFFD1B7C1)
    val bg=Color(0xFF171019)
    val card=Color(0xFF30202D)
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    val urlRegex=remember {Regex("""https?://[^\s<>"']+""",RegexOption.IGNORE_CASE)}
    val links=remember {mutableStateListOf<VelvetChatLink>()}
    var linksLoading by remember {mutableStateOf(false)}
    var linksFetched by remember {mutableStateOf(false)}
    var linksError by remember {mutableStateOf("")}
    var moreLinks by remember {mutableStateOf(true)}
    var linksCursorAt by remember {mutableStateOf<String?>(null)}
    var linksCursorId by remember {mutableStateOf<String?>(null)}
    var urlToOpen by remember {mutableStateOf<String?>(null)}
    var tab by remember { mutableIntStateOf(0) }
    val photos=remember {mutableStateListOf<VelvetGalleryPhoto>()}
    var loadingPhotos by remember {mutableStateOf(false)}
    var photosFetched by remember {mutableStateOf(false)}
    var photosError by remember {mutableStateOf("")}
    var morePhotos by remember {mutableStateOf(true)}

    val hits=remember {mutableStateListOf<VelvetSearchHit>()}
    var query by remember {mutableStateOf("")}
    var lastSearched by remember {mutableStateOf("")}
    var loadingSearch by remember {mutableStateOf(false)}
    var searchError by remember {mutableStateOf("")}
    var moreHits by remember {mutableStateOf(false)}
    var searched by remember {mutableStateOf(false)}

    fun loadMorePhotos() {
        if(loadingPhotos || !morePhotos) return
        loadingPhotos=true
        photosError=""
        val last=photos.lastOrNull()
        scope.launch {
            try {
                // 12 at a time avoids decoding dozens of large photos on entry.
                val rows=client.chatPhotos(last?.createdAt,last?.id,12)
                val fetched=(0 until rows.length()).mapNotNull { index ->
                    readGalleryPhoto(rows.getJSONObject(index))
                }
                val ids=photos.map{it.id}.toSet()
                photos.addAll(fetched.filter{it.id !in ids})
                morePhotos=rows.length()>=12
            } catch(e:Exception) {
                photosError=e.message ?: "Could not load the shared media."
            } finally {
                photosFetched=true
                loadingPhotos=false
            }
        }
    }

    fun searchMessages(fresh:Boolean) {
        val term=if(fresh) query.trim() else lastSearched
        if(term.length !in 2..64 || loadingSearch) return
        loadingSearch=true
        searchError=""
        if(fresh) {
            hits.clear()
            moreHits=true
            searched=true
            lastSearched=term
        }
        val last=if(fresh) null else hits.lastOrNull()
        scope.launch {
            try {
                val rows=client.searchChat(term,last?.createdAt,last?.id,40)
                val fetched=(0 until rows.length()).map { index ->
                    val obj=rows.getJSONObject(index)
                    VelvetSearchHit(
                        obj.getString("id"),obj.optString("sender_id"),
                        obj.optString("kind","text"),obj.optString("body"),
                        obj.getString("created_at")
                    )
                }
                val ids=hits.map{it.id}.toSet()
                hits.addAll(fetched.filter{it.id !in ids})
                moreHits=rows.length()>=40
            } catch(e:Exception) {
                searchError=e.message ?: "Could not search your conversation."
            } finally {loadingSearch=false}
        }
    }

    fun loadMoreLinks() {
        if(linksLoading || !moreLinks) return
        linksLoading=true
        linksError=""
        val beforeAt=linksCursorAt
        val beforeId=linksCursorId
        scope.launch {
            try {
                // This uses the same couple-scoped literal-text search RPC.
                val rows=client.searchChat("http",beforeAt,beforeId,40)
                val fetched=mutableListOf<VelvetChatLink>()
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i)
                    val msgId=row.getString("id")
                    val body=row.optString("body")
                    urlRegex.findAll(body).forEachIndexed {index,match->
                        val clean=match.value.trimEnd('.',',',';',':','!','?')
                        fetched.add(VelvetChatLink(
                            "$msgId-$index",clean,row.optString("sender_id"),
                            row.getString("created_at")
                        ))
                    }
                }
                if(rows.length()>0) {
                    linksCursorAt=rows.getJSONObject(rows.length()-1).getString("created_at")
                    linksCursorId=rows.getJSONObject(rows.length()-1).getString("id")
                }
                val ids=links.map{it.id}.toSet()
                links.addAll(fetched.filter{it.id !in ids})
                moreLinks=rows.length()>=40
            } catch(e:Exception) {
                linksError=e.message ?: "Could not load chat links."
            } finally {linksFetched=true;linksLoading=false}
        }
    }

    LaunchedEffect(Unit) { loadMorePhotos() }
    LaunchedEffect(tab) {
        if(tab==3 && !linksFetched) loadMoreLinks()
    }

    Column(Modifier.fillMaxSize().background(bg)) {
        Row(Modifier.fillMaxWidth().background(card)
            .padding(horizontal=8.dp,vertical=7.dp),
            verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {
                Icon(Icons.Outlined.ArrowBack,"Back to chat",tint=cream)
            }
            Column(Modifier.weight(1f)) {
                Text("Chat info",color=cream,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                Text("Private space for two",color=muted,fontSize=11.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(58.dp)
                .background(Color(0xFF60354C),RoundedCornerShape(18.dp)),
                contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.FavoriteBorder,"Your partner",tint=rose,
                    modifier=Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(partnerName,color=cream,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                Text("Only you and your partner can read shared chat media.",
                    color=muted,fontSize=11.sp)
            }
        }

        ScrollableTabRow(
            selectedTabIndex=tab,
            containerColor=card,contentColor=rose,
            edgePadding=8.dp
        ) {
            Tab(selected=tab==0,onClick={tab=0},
                text={Text("Photos",color=if(tab==0)cream else muted)})
            Tab(selected=tab==1,onClick={tab=1},
                text={Text("Videos",color=if(tab==1)cream else muted)})
            Tab(selected=tab==2,onClick={tab=2},
                text={Text("Audio",color=if(tab==2)cream else muted)})
            Tab(selected=tab==3,onClick={tab=3},
                text={Text("Links",color=if(tab==3)cream else muted)})
            Tab(selected=tab==4,onClick={tab=4},
                text={Text("Search",color=if(tab==4)cream else muted)})
        }

        if(tab==0) {
            Row(Modifier.fillMaxWidth().padding(start=18.dp,end=12.dp,top=7.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text("SHARED CHAT PHOTOS",
                    color=rose,fontSize=10.sp,letterSpacing=1.3.sp,
                    modifier=Modifier.weight(1f))
                TextButton(
                    enabled=!loadingPhotos,
                    onClick={
                        photos.clear()
                        morePhotos=true
                        photosFetched=false
                        loadMorePhotos()
                    }
                ) { Text("Refresh",color=rose,fontSize=11.sp) }
            }
            Text("Photos sent in Chat only. Nothing is automatically added to Our Story.",
                color=muted,fontSize=11.sp,
                modifier=Modifier.padding(start=18.dp,end=18.dp,bottom=14.dp))
            LazyVerticalGrid(
                columns=GridCells.Fixed(3),
                contentPadding=PaddingValues(start=10.dp,end=10.dp,bottom=28.dp),
                horizontalArrangement=Arrangement.spacedBy(7.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp),
                modifier=Modifier.weight(1f)
            ) {
                items(photos.size,key={ index->photos[index].id }) {index->
                    val photo=photos[index]
                    Column(Modifier.fillMaxWidth()) {
                        VelvetPrivateImage(client,photo.path,
                            modifier=Modifier.fillMaxWidth().aspectRatio(1f),
                            thumbMaxDimension=640)
                        if(photo.caption.isNotBlank()) {
                            Text(photo.caption,color=cream,fontSize=10.sp,
                                maxLines=1,modifier=Modifier.padding(top=4.dp))
                        }
                        Text(
                            (if(photo.senderId==myId) "You" else partnerName)+
                                " · "+velvetDate(photo.createdAt),
                            color=muted,fontSize=9.sp,maxLines=1,
                            modifier=Modifier.padding(top=2.dp)
                        )
                    }
                }
                item(span={GridItemSpan(maxLineSpan)}) {
                    Column(Modifier.fillMaxWidth().padding(vertical=14.dp),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        if(loadingPhotos) {
                            CircularProgressIndicator(color=rose)
                        } else {
                            if(photosFetched && photos.isEmpty() && photosError.isBlank()) {
                                Text("No chat photos yet ♡",color=cream,fontSize=15.sp)
                            }
                            if(photosError.isNotBlank()) {
                                Text(photosError,color=rose,fontSize=12.sp)
                            }
                            if(morePhotos) {
                                TextButton(onClick={loadMorePhotos()}) {
                                    Text(if(photosError.isBlank()) "Load older photos" else "Retry",
                                        color=rose)
                                }
                            }
                        }
                    }
                }
            }
        } else if(tab==1) {
            VelvetChatVideoGallery(
                client=client,myId=myId,partnerName=partnerName
            )
        } else if(tab==2) {
            VelvetChatAudioGallery(
                client=client,myId=myId,partnerName=partnerName
            )
        } else if(tab==3) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Links shared in Chat",
                        color=cream,fontSize=18.sp,fontWeight=FontWeight.SemiBold,
                        modifier=Modifier.weight(1f))
                    TextButton(onClick={
                        if(!linksLoading) {
                            links.clear()
                            linksCursorAt=null
                            linksCursorId=null
                            moreLinks=true
                            linksFetched=false
                            loadMoreLinks()
                        }
                    },enabled=!linksLoading) {Text("Refresh",color=rose)}
                }
                Text("Only links found in your private messages or photo captions.",
                    color=muted,fontSize=11.sp)
                Spacer(Modifier.height(14.dp))
                LazyColumn(Modifier.weight(1f),
                    verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    items(links.size,key={index->links[index].id}) {index->
                        val link=links[index]
                        Column(
                            Modifier.fillMaxWidth()
                                .background(card,RoundedCornerShape(14.dp))
                                .clickable{urlToOpen=link.url}
                                .padding(15.dp)
                        ) {
                            Text(Uri.parse(link.url).host ?: "Link",
                                color=rose,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(link.url,color=cream,maxLines=2,fontSize=12.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                (if(link.senderId==myId) "You" else partnerName)+
                                    " · "+velvetDate(link.createdAt),
                                color=muted,fontSize=10.sp
                            )
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth(),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            if(linksLoading) CircularProgressIndicator(color=rose)
                            if(linksError.isNotBlank()) {
                                Text(linksError,color=rose,fontSize=12.sp)
                            }
                            if(linksFetched && links.isEmpty() &&
                                linksError.isBlank() && !linksLoading) {
                                Text("No shared links found ♡",color=muted)
                            }
                            if(moreLinks && !linksLoading) {
                                TextButton(onClick={loadMoreLinks()}) {
                                    Text(if(linksError.isBlank()) "Load older links" else "Retry",
                                        color=rose)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                OutlinedTextField(
                    value=query,
                    onValueChange={if(it.length<=64)query=it},
                    label={Text("Find text or photo captions")},
                    leadingIcon={Icon(Icons.Outlined.Search,"Search conversation")},
                    singleLine=true,
                    modifier=Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick={searchMessages(true)},
                    enabled=!loadingSearch && query.trim().length in 2..64,
                    modifier=Modifier.fillMaxWidth()
                ) {
                    Text(if(loadingSearch) "Searching…" else "Search our conversation")
                }
                Spacer(Modifier.height(12.dp))
                if(searchError.isNotBlank()) {
                    Text(searchError,color=rose,fontSize=12.sp)
                    TextButton(onClick={searchMessages(false)}) {
                        Text("Retry search",color=rose)
                    }
                }
                if(!searched) {
                    Text("Searches only this private chat. Matches include messages and photo captions.",
                        color=muted,fontSize=12.sp)
                } else if(!loadingSearch && hits.isEmpty() && searchError.isBlank()) {
                    Text("No messages found for “$lastSearched”.",
                        color=muted,fontSize=13.sp)
                }
                LazyColumn(Modifier.weight(1f),
                    verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    items(hits.size,key={index->hits[index].id}) {index->
                        val hit=hits[index]
                        Column(
                            Modifier.fillMaxWidth()
                                .background(card,RoundedCornerShape(15.dp))
                                .padding(13.dp)
                        ) {
                            Text(
                                (if(hit.senderId==myId) "You" else partnerName)+
                                    "  ·  "+velvetDate(hit.createdAt)+
                                    (if(hit.kind=="image") "  ·  Photo caption" else ""),
                                color=rose,fontSize=11.sp
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(hit.body,color=cream,fontSize=14.sp,lineHeight=19.sp)
                        }
                    }
                    if(moreHits && hits.isNotEmpty()) {
                        item {
                            TextButton(
                                onClick={searchMessages(false)},enabled=!loadingSearch,
                                modifier=Modifier.fillMaxWidth()
                            ) {
                                Text(if(loadingSearch)"Loading…" else "More results",
                                    color=rose)
                            }
                        }
                    }
                }
                Text("Documents and pinned-message lists are not synced yet.",
                    color=muted,fontSize=10.sp,
                    modifier=Modifier.fillMaxWidth().padding(top=8.dp,bottom=5.dp))
            }
        }
    }
    urlToOpen?.let { destination ->
        AlertDialog(
            onDismissRequest={urlToOpen=null},
            title={Text("Open external link?",color=cream)},
            text={Text(
                "This will open a website outside Velvet.\n\n"+
                    destination.take(200),
                color=muted
            )},
            confirmButton={
                TextButton(onClick={
                    val url=destination
                    urlToOpen=null
                    runCatching {
                        val intent=Intent(Intent.ACTION_VIEW,Uri.parse(url))
                        context.startActivity(intent)
                    }.onFailure { linksError="There is no browser available for this link." }
                }) {Text("Open",color=rose)}
            },
            dismissButton={
                TextButton(onClick={urlToOpen=null}) {Text("Cancel",color=muted)}
            },
            containerColor=card
        )
    }
}
