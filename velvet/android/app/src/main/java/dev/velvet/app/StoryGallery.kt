package dev.velvet.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Gallery-first prototype of Our Story. Files and metadata are stored only on THIS device.
 * These media are deliberately separate from chat attachments. Supabase and partner sync
 * will be introduced later; do not present local content as shared across both phones.
 */
private object StoryColors {
    val bg get() = VelvetTheme.current.bg
    val panel get() = VelvetTheme.current.paper
    val raised get() = VelvetTheme.current.raised
    val stroke get() = VelvetTheme.current.border
    val rose get() = VelvetTheme.current.rose
    val cream get() = VelvetTheme.current.text
    val muted get() = VelvetTheme.current.muted
    val gold get() = VelvetTheme.current.gold
}
private data class StoryAsset(
    val id: String,
    val storedFileName: String,
    val type: String,
    val caption: String,
    val uploadedBy: String,
    val originalSender: String?,
    val addedAt: Long,
    val albumId: String?
)
private data class StoryCollection(val id: String, val name: String)
private data class StoryWish(val id: String, val title: String, val completed: Boolean)

private class StoryLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("velvet_story_gallery_v1", Context.MODE_PRIVATE)
    val media = mutableStateListOf<StoryAsset>()
    val albums = mutableStateListOf<StoryCollection>()
    val wishes = mutableStateListOf<StoryWish>()

    init {
        runCatching {
            val j = JSONArray(prefs.getString("media", "[]"))
            for (i in 0 until j.length()) {
                val x = j.getJSONObject(i)
                media.add(StoryAsset(
                    x.getString("id"), x.getString("file"), x.optString("type", "image/jpeg"),
                    x.optString("caption", ""), x.optString("uploadedBy", "You"),
                    x.optString("originalSender").takeIf { it.isNotBlank() && it != "null" },
                    x.optLong("addedAt", 0L), x.optString("albumId").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
            val a = JSONArray(prefs.getString("albums", "[]"))
            for(i in 0 until a.length()) { val x=a.getJSONObject(i); albums.add(StoryCollection(x.getString("id"),x.getString("name"))) }
            val w = JSONArray(prefs.getString("wishes", "[]"))
            for(i in 0 until w.length()) { val x=w.getJSONObject(i); wishes.add(StoryWish(x.getString("id"),x.getString("title"),x.optBoolean("done"))) }
        }
    }

    fun fileFor(m: StoryAsset) = File(File(context.filesDir, "our-story"), m.storedFileName)
    private fun save() {
        val mj = JSONArray()
        media.forEach { m -> mj.put(JSONObject().put("id",m.id).put("file",m.storedFileName)
            .put("type",m.type).put("caption",m.caption).put("uploadedBy",m.uploadedBy)
            .put("originalSender",m.originalSender).put("addedAt",m.addedAt).put("albumId",m.albumId)) }
        val aj = JSONArray(); albums.forEach { a->aj.put(JSONObject().put("id",a.id).put("name",a.name)) }
        val wj = JSONArray(); wishes.forEach { w->wj.put(JSONObject().put("id",w.id).put("title",w.title).put("done",w.completed)) }
        prefs.edit().putString("media",mj.toString()).putString("albums",aj.toString()).putString("wishes",wj.toString()).apply()
    }
    suspend fun importPicked(uris: List<Uri>, ownerName: String): Int = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir,"our-story").apply { mkdirs() }
        val copied = mutableListOf<StoryAsset>()
        for (uri in uris) {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                if (!mime.startsWith("image/") && !mime.startsWith("video/")) return@runCatching
                val ext = if (mime.startsWith("video/")) "mp4" else "jpg"
                val fileName = "${UUID.randomUUID()}.$ext"
                val destination=File(folder,fileName)
                context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output->input.copyTo(output) } }
                    ?: error("Cannot open selected media")
                copied.add(StoryAsset(UUID.randomUUID().toString(),fileName,mime,"",ownerName,null,System.currentTimeMillis(),null))
            }
        }
        // State lists are mutated on the main thread.
        withContext(Dispatchers.Main) { media.addAll(0,copied);save() }
        copied.size
    }
    fun update(asset: StoryAsset) { val i=media.indexOfFirst { it.id==asset.id }; if(i!=-1) { media[i]=asset; save() } }
    fun remove(asset: StoryAsset) { if(media.removeAll { it.id==asset.id }) { fileFor(asset).delete();save() } }
    fun createAlbum(title: String) { if(title.isNotBlank()) { albums.add(StoryCollection(UUID.randomUUID().toString(),title.trim()));save() } }
    fun addWish(title: String) { if(title.isNotBlank()) { wishes.add(0,StoryWish(UUID.randomUUID().toString(),title.trim(),false));save() } }
    fun toggleWish(wish: StoryWish) { val i=wishes.indexOfFirst { it.id==wish.id }; if(i>=0) { wishes[i]=wish.copy(completed=!wish.completed);save() } }
}

private enum class StoryFilter(val label: String) {
    ALL("All media"), PHOTOS("Photos"), VIDEOS("Videos"), ALBUMS("Albums"), SOMEDAY("Someday"), TIMELINE("Timeline")
}

@Composable
internal fun GalleryFirstScreen(ownerName: String) {
    val context = LocalContext.current
    val scope= rememberCoroutineScope()
    val store=remember { StoryLibrary(context.applicationContext) }
    var filter by remember { mutableStateOf(StoryFilter.ALL) }
    var openedAlbum by remember { mutableStateOf<String?>(null) }
    var openedAsset by remember { mutableStateOf<String?>(null) }
    var newAlbum by remember { mutableStateOf(false) }
    var newWish by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if(uris.isNotEmpty()) scope.launch {
            busy=true
            try { val n=store.importPicked(uris, ownerName); message="$n ${if(n==1) "item" else "items"} added to Our Story" }
            catch (e: Exception) { message="Unable to add media: ${e.localizedMessage ?: "try again"}" }
            finally {busy=false}
        }
    }
    val pickMedia = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    val selected=store.media.find { it.id==openedAsset }

    Column(Modifier.fillMaxSize().background(StoryColors.bg)) {
        Column(Modifier.padding(top=24.dp,start=16.dp,end=16.dp)) {
            Text("THE DAYS THAT MAKE US",fontSize=10.sp,letterSpacing=2.5.sp,color=StoryColors.gold)
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Our Story.",fontSize=39.sp,fontFamily=FontFamily.Serif,color=StoryColors.cream)
                // One gallery Add control remains in the content area; avoid duplicate header actions.
            }
            Text("Every picture. Every little chapter.",fontSize=13.sp,color=StoryColors.muted)
            Spacer(Modifier.height(18.dp))
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=StoryColors.rose)
        Row(Modifier.fillMaxWidth().padding(bottom=13.dp).horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            StoryFilter.entries.forEach { tab ->
                val active=filter==tab
                Box(Modifier.clip(CircleShape).background(if(active) StoryColors.rose else StoryColors.panel)
                    .border(1.dp,if(active) StoryColors.rose else StoryColors.stroke,CircleShape)
                    .clickable { filter=tab;openedAlbum=null }.padding(horizontal=14.dp,vertical=10.dp)) {
                    Text(tab.label,fontSize=12.sp,color=if(active) StoryColors.bg else StoryColors.cream)
                }
            }
        }
        if(message!=null) {
            Text(message!!,Modifier.fillMaxWidth().padding(start=17.dp,end=17.dp,bottom=10.dp),color=StoryColors.rose,fontSize=12.sp)
        }
        when(filter) {
            StoryFilter.ALL,StoryFilter.PHOTOS,StoryFilter.VIDEOS -> {
                val shown=store.media.filter { m -> when(filter) {
                    StoryFilter.PHOTOS -> m.type.startsWith("image/")
                    StoryFilter.VIDEOS -> m.type.startsWith("video/")
                    else -> true
                }}.sortedByDescending { it.addedAt }
                StoryMediaGrid(shown,store,emptyTitle=when(filter) { StoryFilter.PHOTOS -> "No photos yet";StoryFilter.VIDEOS -> "No videos yet";else -> "Your gallery starts here" },onOpen={ openedAsset=it },onAdd=pickMedia)
            }
            StoryFilter.ALBUMS -> {
                val opened=store.albums.find { it.id==openedAlbum }
                if(opened==null) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Our albums",color=StoryColors.cream,fontFamily=FontFamily.Serif,fontSize=27.sp)
                            TextButton(onClick={newAlbum=true}) { Icon(Icons.Outlined.Add,null,Modifier.size(18.dp));Text("New album",fontSize=12.sp) }
                        }
                        Spacer(Modifier.height(9.dp))
                        if(store.albums.isEmpty()) StoryBlank("A home for your favorite collections", "Create an album, then add photos and videos from your gallery.", Icons.Outlined.Collections, {newAlbum=true},"Create album")
                        else store.albums.forEach { album ->
                            val contents=store.media.filter { it.albumId==album.id }
                            Row(Modifier.fillMaxWidth().padding(bottom=11.dp).clip(RoundedCornerShape(18.dp)).background(StoryColors.panel).clickable { openedAlbum=album.id }.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                Box(Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(StoryColors.raised),contentAlignment=Alignment.Center) {
                                    if(contents.isNotEmpty()) StoryPreview(store.fileFor(contents.first()),contents.first().type,Modifier.fillMaxSize())
                                    else Icon(Icons.Outlined.PhotoLibrary,null,tint=StoryColors.muted,modifier=Modifier.size(32.dp))
                                }
                                Spacer(Modifier.width(15.dp))
                                Column(Modifier.weight(1f)) { Text(album.name,color=StoryColors.cream,fontSize=19.sp,fontFamily=FontFamily.Serif);Text("${contents.size} memories",color=StoryColors.muted,fontSize=12.sp) }
                                Icon(Icons.Outlined.ChevronRight,null,tint=StoryColors.rose)
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick={openedAlbum=null},modifier=Modifier.padding(start=11.dp)) { Icon(Icons.Outlined.ChevronLeft,null);Text("All albums") }
                        Text(opened.name,Modifier.padding(start=17.dp,bottom=14.dp),fontSize=27.sp,color=StoryColors.cream,fontFamily=FontFamily.Serif)
                        StoryMediaGrid(store.media.filter{it.albumId==opened.id}.sortedByDescending{it.addedAt},store,"Nothing here yet",onOpen={openedAsset=it},onAdd={filter=StoryFilter.ALL;openedAlbum=null})
                    }
                }
            }
            StoryFilter.SOMEDAY -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("Our someday list.",color=StoryColors.cream,fontSize=28.sp,fontFamily=FontFamily.Serif)
                        IconButton(onClick={newWish=true}) { Icon(Icons.Outlined.Add,"Add a dream",tint=StoryColors.rose) }
                    }
                    Text("Plans, places, and little dreams.",color=StoryColors.muted,fontSize=13.sp)
                    Spacer(Modifier.height(20.dp))
                    if(store.wishes.isEmpty()) StoryBlank("Where shall we go someday?","Make a list of adventures you want to share.",Icons.Outlined.AutoAwesome,{newWish=true},"Add our first dream")
                    else store.wishes.forEach { w ->
                        Row(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(15.dp)).background(StoryColors.panel).clickable { store.toggleWish(w) }.padding(17.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(if(w.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,tint=StoryColors.rose)
                            Spacer(Modifier.width(12.dp));Text(w.title,color=StoryColors.cream,modifier=Modifier.weight(1f));if(w.completed)Text("Done ♡",color=StoryColors.gold,fontSize=11.sp)
                        }
                    }
                }
            }
            StoryFilter.TIMELINE -> {
                val items=store.media.sortedByDescending{it.addedAt}
                LazyColumn(Modifier.fillMaxSize().padding(horizontal=17.dp)) {
                    item { Text("Little chapters.",fontFamily=FontFamily.Serif,fontSize=28.sp,color=StoryColors.cream);Spacer(Modifier.height(18.dp)) }
                    if(items.isEmpty()) item { StoryBlank("The story begins with a memory", "Your photos and videos will appear here in the order they were added.",Icons.Outlined.CalendarMonth,pickMedia,"Add a memory") }
                    items(items,key={it.id}) { m ->
                        Row(Modifier.fillMaxWidth().padding(bottom=17.dp).clickable { openedAsset=m.id },verticalAlignment=Alignment.CenterVertically) {
                            StoryPreview(store.fileFor(m),m.type,Modifier.size(85.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.width(15.dp))
                            Column(Modifier.weight(1f)) {
                                Text(SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(Date(m.addedAt)),color=StoryColors.gold,fontSize=11.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(if(m.caption.isBlank()) "A moment worth keeping" else m.caption,color=StoryColors.cream,fontFamily=FontFamily.Serif,fontSize=19.sp,maxLines=2)
                                Text("Added by ${m.uploadedBy}",color=StoryColors.muted,fontSize=12.sp)
                            }
                            Icon(Icons.Outlined.ChevronRight,null,tint=StoryColors.rose)
                        }
                        HorizontalDivider(color=StoryColors.stroke.copy(alpha=.5f))
                    }
                }
            }
        }
    }

    if(selected!=null) StoryViewer(selected,store,onClose={openedAsset=null})
    if(newAlbum) StoryTextDialog("New album","Album name",onDismiss={newAlbum=false},onSave={store.createAlbum(it);newAlbum=false})
    if(newWish) StoryTextDialog("Our next dream","What would you like to do together?",onDismiss={newWish=false},onSave={store.addWish(it);newWish=false})
}

@Composable
private fun StoryMediaGrid(media:List<StoryAsset>,store:StoryLibrary,emptyTitle:String,onOpen:(String)->Unit,onAdd:()->Unit) {
    if(media.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(18.dp),contentAlignment=Alignment.TopCenter) {
            StoryBlank(emptyTitle,"Choose photos or videos from your phone. Captions are completely optional.",Icons.Outlined.AddPhotoAlternate,onAdd,"Add media")
        }
    } else {
        val format=remember { SimpleDateFormat("MMMM yyyy",Locale.getDefault()) }
        val grouped=media.groupBy { format.format(Date(it.addedAt)) }
        LazyVerticalGrid(GridCells.Fixed(3),modifier=Modifier.fillMaxSize().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp),contentPadding=PaddingValues(bottom=20.dp)) {
            grouped.forEach { (month,group) ->
                item(span={GridItemSpan(maxLineSpan)}) {
                    Text(month+" · Together",color=StoryColors.cream,fontSize=13.sp,modifier=Modifier.padding(top=12.dp,bottom=8.dp,start=4.dp))
                }
                items(group,key={it.id}) { asset ->
                    Box(Modifier.fillMaxWidth().aspectRatio(.84f).clip(RoundedCornerShape(5.dp)).background(StoryColors.panel).clickable {onOpen(asset.id)}) {
                        StoryPreview(store.fileFor(asset),asset.type,Modifier.fillMaxSize())
                        if(asset.type.startsWith("video/")) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(23.dp).background(StoryColors.bg.copy(alpha=.7f),CircleShape),contentAlignment=Alignment.Center) {
                            Icon(Icons.Outlined.PlayArrow,"Video",Modifier.size(17.dp),tint=StoryColors.cream)
                        }
                        Text(asset.uploadedBy.take(1).uppercase(),modifier=Modifier.align(Alignment.TopEnd).padding(5.dp).background(StoryColors.bg.copy(alpha=.6f),CircleShape).padding(horizontal=7.dp,vertical=3.dp),fontSize=9.sp,color=StoryColors.cream)
                        if(asset.caption.isNotBlank()) Text(asset.caption,modifier=Modifier.align(Alignment.BottomStart).fillMaxWidth().background(StoryColors.bg.copy(alpha=.5f)).padding(5.dp),fontSize=9.sp,color=StoryColors.cream,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryBlank(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onAction:()->Unit,action:String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(StoryColors.panel).border(1.dp,StoryColors.stroke,RoundedCornerShape(21.dp)).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(icon,null,Modifier.size(46.dp),tint=StoryColors.rose)
        Spacer(Modifier.height(14.dp));Text(title,color=StoryColors.cream,fontSize=24.sp,fontFamily=FontFamily.Serif,textAlign=TextAlign.Center)
        Spacer(Modifier.height(9.dp));Text(subtitle,color=StoryColors.muted,fontSize=13.sp,textAlign=TextAlign.Center)
        Spacer(Modifier.height(20.dp));Button(onClick=onAction,colors=ButtonDefaults.buttonColors(containerColor=StoryColors.rose,contentColor=StoryColors.bg)) {Text(action)}
    }
}

@Composable
private fun StoryPreview(file: File,type:String,modifier:Modifier=Modifier,maxDimension:Int=600) {
    val bitmap by produceState<ImageBitmap?>(initialValue=null,file.path,type,maxDimension) {
        value=withContext(Dispatchers.IO) { loadStoryBitmap(file,type,maxDimension)?.asImageBitmap() }
    }
    Box(modifier.background(StoryColors.raised),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap!!,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(if(type.startsWith("video/")) Icons.Outlined.Videocam else Icons.Outlined.Image,null,tint=StoryColors.muted)
    }
}

private fun loadStoryBitmap(file:File,mime:String,target:Int):Bitmap? = runCatching {
    if(mime.startsWith("video/")) {
        val r=MediaMetadataRetriever()
        try { r.setDataSource(file.absolutePath);r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }
        finally {r.release()}
    } else {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file.absolutePath,bounds)
        var sample=1
        while(bounds.outWidth/sample>target*2 || bounds.outHeight/sample>target*2)sample*=2
        BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample})
    }
}.getOrNull()

@Composable
private fun StoryViewer(asset:StoryAsset,store:StoryLibrary,onClose:()->Unit) {
    var changeCaption by remember(asset.id) { mutableStateOf(false) }
    var albumPicker by remember(asset.id) { mutableStateOf(false) }
    var confirmDelete by remember(asset.id) { mutableStateOf(false) }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(StoryColors.bg).padding(top=20.dp,bottom=22.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onClose) {Icon(Icons.Outlined.Close,"Close media",tint=StoryColors.cream)}
                Spacer(Modifier.width(7.dp));Text("Our Story",color=StoryColors.cream,fontFamily=FontFamily.Serif,fontSize=24.sp,modifier=Modifier.weight(1f))
                IconButton(onClick={changeCaption=true}) {Icon(Icons.Outlined.Edit,"Edit caption",tint=StoryColors.rose)}
                IconButton(onClick={confirmDelete=true}) {Icon(Icons.Outlined.DeleteOutline,"Delete memory",tint=StoryColors.muted)}
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black),contentAlignment=Alignment.Center) {
                if(asset.type.startsWith("video/")) {
                    AndroidView(factory={context -> VideoView(context).apply {setVideoPath(store.fileFor(asset).absolutePath);setMediaController(MediaController(context));setOnPreparedListener {it.isLooping=false} }},modifier=Modifier.fillMaxWidth().heightIn(max=500.dp))
                } else {
                    StoryPreview(store.fileFor(asset),asset.type,Modifier.fillMaxSize(),maxDimension=1800)
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal=19.dp)) {
                Text(if(asset.caption.isBlank()) "A little moment ♡" else asset.caption,color=StoryColors.cream,fontFamily=FontFamily.Serif,fontSize=22.sp,maxLines=2)
                Spacer(Modifier.height(6.dp));Text("Added by ${asset.uploadedBy} · ${SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(Date(asset.addedAt))}",color=StoryColors.muted,fontSize=12.sp)
                if(!asset.originalSender.isNullOrBlank()) Text("Originally shared by ${asset.originalSender}",color=StoryColors.muted,fontSize=12.sp)
                Spacer(Modifier.height(13.dp))
                OutlinedButton(onClick={albumPicker=true},modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Collections,null,Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text("${store.albums.find{it.id==asset.albumId}?.name ?: "Add to an album"}")
                }
            }
        }
    }
    if(changeCaption) {
        var draft by remember(asset.id) { mutableStateOf(asset.caption) }
        AlertDialog(onDismissRequest={changeCaption=false},title={Text("Memory caption")},text={OutlinedTextField(value=draft,onValueChange={draft=it},label={Text("Caption (optional)")},maxLines=3)},confirmButton={TextButton(onClick={store.update(asset.copy(caption=draft));changeCaption=false}){Text("Save")}},dismissButton={TextButton(onClick={changeCaption=false}){Text("Cancel")}},containerColor=StoryColors.panel)
    }
    if(albumPicker) AlertDialog(onDismissRequest={albumPicker=false},title={Text("Choose an album")},text={
        Column(Modifier.heightIn(max=310.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick={store.update(asset.copy(albumId=null));albumPicker=false}) {Text("No album")}
            if(store.albums.isEmpty()) Text("Create an album from Our Story → Albums first.",color=StoryColors.muted,fontSize=13.sp)
            else store.albums.forEach { a->TextButton(onClick={store.update(asset.copy(albumId=a.id));albumPicker=false}){Text(a.name)} }
        }
    },confirmButton={TextButton(onClick={albumPicker=false}){Text("Done")}},containerColor=StoryColors.panel)
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this memory?")},text={Text("This removes the local copy from Our Story on this phone.")},confirmButton={TextButton(onClick={store.remove(asset);confirmDelete=false;onClose()}){Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}},containerColor=StoryColors.panel)
}

@Composable
private fun StoryTextDialog(title:String,hint:String,onDismiss:()->Unit,onSave:(String)->Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(draft,{draft=it},label={Text(hint)},singleLine=true)},confirmButton={TextButton(onClick={onSave(draft)},enabled=draft.isNotBlank()){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}},containerColor=StoryColors.panel)
}
