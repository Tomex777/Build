package com.night.jigsaw

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val forest=Color(0xFF254C39)
private val bg=Color(0xFFF7F5F0)
private val muted=Color(0xFF6F756E)

private fun clockText(seconds: Int): String {
    val safe=seconds.coerceAtLeast(0)
    val hours=safe/3600
    val mins=(safe%3600)/60
    val secs=safe%60
    return if(hours>0)"%d:%02d:%02d".format(hours,mins,secs)
           else "%02d:%02d".format(mins,secs)
}

class MainActivity : ComponentActivity() {
    private lateinit var store: PuzzleStore
    private lateinit var game: PuzzleGame
    private lateinit var picture: Bitmap
    private var refresh: () -> Unit = {}
    private var openLibrary: () -> Unit = {}
    private var board: PuzzleBoardView? = null
    private var isAppForeground by mutableStateOf(true)

    private val difficulties=listOf(
        Triple(3,2,"6 · Beginner"),
        Triple(4,3,"12 · Gentle"),
        Triple(6,4,"24 · Relaxed"),
        Triple(8,6,"48 · Focus"),
        Triple(9,8,"72 · Steady"),
        Triple(12,8,"96 · Advanced"),
        Triple(12,12,"144 · Challenge"),
        Triple(20,12,"240 · Expert"),
        Triple(24,16,"384 · Master"),
        Triple(30,20,"600 · Grandmaster")
    )

    private val photoPicker=registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri != null) {
            Thread {
                val result=runCatching { store.importPhoto(uri) }
                runOnUiThread {
                    result.onSuccess { chosen ->
                        picture=chosen.bitmap
                        game=PuzzleGame.fresh(game.cols,game.rows,chosen.photo.key,
                            landscape=game.landscapeMode)
                        store.save(game)
                        refresh()
                    }.onFailure {
                        Toast.makeText(this,it.message ?: "Couldn't import this picture",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        store=PuzzleStore(this)
        game=store.load() ?: PuzzleGame.fresh(6,4)
        val restoredPhoto=if(game.imageKey!="starter")store.loadPhoto(game.imageKey) else null
        if (game.imageKey!="starter" && restoredPhoto==null) game=PuzzleGame.fresh(6,4)
        picture=restoredPhoto ?: SceneImage.create()
        store.save(game)
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=forest,background=bg,
                surface=bg,onSurface=Color(0xFF242B26))) {
                JigsawScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAppForeground=true
    }

    override fun onPause() {
        isAppForeground=false
        if (::store.isInitialized) store.save(game)
        super.onPause()
    }

    @Composable
    private fun PuzzleBoard(modifier: Modifier, solved: Boolean,
                            paused: Boolean, onResume: () -> Unit,
                            onNewPuzzle: () -> Unit) {
        Box(modifier=modifier.border(1.dp,Color(0xFFD5D2C9),
            RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
            AndroidView(
                factory={ context ->
                    PuzzleBoardView(context).also {
                        board=it
                        it.bind(game,picture) { store.save(game);refresh() }
                    }
                },
                update={ view ->
                    board=view
                    view.bind(game,picture) { store.save(game);refresh() }
                },
                modifier=Modifier.fillMaxSize()
            )
            if(solved) {
                Surface(shape=RoundedCornerShape(14.dp),
                    color=Color(0xFFF7F5F0)) {
                    Column(Modifier.padding(18.dp),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("Beautifully done.",fontSize=21.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("${game.count} pieces · ${clockText(game.elapsedSeconds)} · ${game.moves} moves",
                            color=muted,fontSize=13.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick=onNewPuzzle) { Text("Play again") }
                    }
                }
            }
            if(paused && !solved) {
                // Full overlay prevents the native puzzle from accepting
                // concealed drag gestures while the game is paused.
                Box(Modifier.fillMaxSize()
                    .background(Color(0xDD232F2A)).clickable(onClick={}),
                    contentAlignment=Alignment.Center) {
                    Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFFF7F5F0)) {
                        Column(Modifier.padding(24.dp),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("Paused",fontWeight=FontWeight.Bold,fontSize=22.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(clockText(game.elapsedSeconds),color=muted)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick=onResume) {Text("Resume")}
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GameActions(revision: Int, wide: Boolean,
                            update: () -> Unit, chooseZone: () -> Unit,
                            onGuide: () -> Unit, onPause: () -> Unit) {
        val solved=revision.let { game.solved }
        if(wide) {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick=onPause,modifier=Modifier.fillMaxWidth()) {
                    Text("Pause · ${clockText(game.elapsedSeconds)}",fontSize=12.sp)
                }
                OutlinedButton(onClick=openLibrary,
                    modifier=Modifier.fillMaxWidth()) { Text("Pictures",fontSize=12.sp) }
                OutlinedButton(onClick={update()},
                    modifier=Modifier.fillMaxWidth()) { Text("New puzzle",fontSize=12.sp) }
                if(game.zoneCount>1) OutlinedButton(onClick=chooseZone,
                    modifier=Modifier.fillMaxWidth()) {
                    Text("Zone ${game.activeZone+1}/${game.zoneCount}",fontSize=12.sp)
                }
                OutlinedButton(
                    onClick=onGuide,
                    enabled=!solved && (game.mode!=PuzzleMode.MEMORY ||
                        game.previewsRemaining>0 || game.preview),
                    modifier=Modifier.fillMaxWidth()) {
                    Text(if(game.mode==PuzzleMode.MEMORY)
                        "Peek · ${game.previewsRemaining}" else
                        if(game.preview)"Hide guide" else "Guide",fontSize=12.sp)
                }
                if(game.count>=72) {
                    OutlinedButton(onClick={
                        game.edgesOnly=!game.edgesOnly
                        store.save(game);refresh()
                    },modifier=Modifier.fillMaxWidth()) {
                        Text(if(game.edgesOnly)"Show all pieces" else "Edges only",
                            fontSize=12.sp)
                    }
                }
                Button(
                    onClick={if(game.hint()){store.save(game);refresh();board?.invalidate()}},
                    enabled=!solved && game.hintsRemaining>0,
                    colors=ButtonDefaults.buttonColors(containerColor=forest),
                    modifier=Modifier.fillMaxWidth()) {
                    Text("Hint · ${game.hintsRemaining}",fontSize=12.sp)
                }
                if(game.zoneCount>1) {
                    OutlinedButton(onClick={board?.jumpToPieces()},
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Find pieces",fontSize=12.sp)
                    }
                }
                OutlinedButton(
                    onClick={if(game.zoneCount>1)board?.focusZone() else board?.centerBoard()},
                    modifier=Modifier.fillMaxWidth()) {
                    Text(if(game.zoneCount>1)"Board section" else "Center board",
                        fontSize=12.sp)
                }
            }
        } else {
            Column {
            if(game.zoneCount>1) {
                OutlinedButton(onClick=chooseZone,modifier=Modifier.fillMaxWidth()) {
                    Text("Zone ${game.activeZone+1}/${game.zoneCount}  ·  ${game.zoneName(game.activeZone)}  ·  ${game.zoneRemaining(game.activeZone)} pieces remaining",
                        fontSize=12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick=onGuide,
                    enabled=!solved && (game.mode!=PuzzleMode.MEMORY ||
                        game.previewsRemaining>0 || game.preview),
                    modifier=Modifier.weight(1f)) {
                    Text(if(game.mode==PuzzleMode.MEMORY)
                        "Peek · ${game.previewsRemaining}" else
                        if(game.preview)"Hide guide" else "Guide",
                        fontSize=12.sp,maxLines=1)
                }
                Button(
                    onClick={if(game.hint()){store.save(game);refresh();board?.invalidate()}},
                    enabled=!solved && game.hintsRemaining>0,
                    colors=ButtonDefaults.buttonColors(containerColor=forest),
                    modifier=Modifier.weight(1f)) {
                    Text("Hint · ${game.hintsRemaining}",fontSize=12.sp,maxLines=1)
                }
                if(game.zoneCount==1) {
                    OutlinedButton(onClick={board?.centerBoard()},
                        modifier=Modifier.weight(1f)) {
                        Text("Center",fontSize=12.sp,maxLines=1)
                    }
                }
            }
            if(game.zoneCount>1) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick={board?.jumpToPieces()},
                        modifier=Modifier.weight(1f)) {
                        Text("Find pieces",fontSize=12.sp)
                    }
                    OutlinedButton(onClick={board?.focusZone()},
                        modifier=Modifier.weight(1f)) {
                        Text("Board section",fontSize=12.sp)
                    }
                    OutlinedButton(onClick={
                        game.edgesOnly=!game.edgesOnly
                        store.save(game);refresh()
                    },modifier=Modifier.weight(1f)) {
                        Text(if(game.edgesOnly)"Show all" else "Edges",
                            fontSize=12.sp)
                    }
                }
            } else if(game.count>=72) {
                OutlinedButton(onClick={
                    game.edgesOnly=!game.edgesOnly
                    store.save(game);refresh()
                },modifier=Modifier.fillMaxWidth()) {
                    Text(if(game.edgesOnly)"Show all pieces" else "Show edge pieces")
                }
            }
            }
        }
    }

    @Composable
    private fun Progress(revision: Int) {
        val completed=revision.let{game.completed}
        Text(if(game.solved)"Completed!" else "${completed} / ${game.count} pieces",
            fontSize=14.sp,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress={completed.toFloat()/game.count},
            modifier=Modifier.fillMaxWidth().height(4.dp),
            color=forest,trackColor=Color(0xFFDBDDD3))
    }

    @Composable
    private fun JigsawScreen() {
        var revision by remember { mutableIntStateOf(0) }
        var difficultyPicker by remember { mutableStateOf(false) }
        var zonePicker by remember { mutableStateOf(false) }
        var libraryPicker by remember { mutableStateOf(false) }
        var libraryRevision by remember { mutableIntStateOf(0) }
        var photoToRemove by remember { mutableStateOf<SavedPuzzlePhoto?>(null) }
        var onlinePicker by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf<String?>(null) }
        var paused by remember { mutableStateOf(false) }
        var peekNonce by remember { mutableIntStateOf(0) }
        var newPuzzleMode by remember { mutableStateOf(PuzzleMode.RELAXED) }
        openLibrary={libraryPicker=true}
        refresh={ revision++ }
        val landscape=LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if(game.setLandscape(landscape)) {
            store.save(game)
            board?.centerBoard()
        }
        val solved=revision.let { game.solved }
        val photoTitle=remember(game.imageKey,libraryRevision) {
            if(game.imageKey=="starter") "Mountain · illustration"
            else store.listPhotos().firstOrNull {it.key==game.imageKey}?.title
                ?: "My picture"
        }
        LaunchedEffect(game, paused, isAppForeground, solved) {
            while(isActive && !paused && isAppForeground && !solved) {
                delay(1000)
                if(!paused && isAppForeground && !game.solved) {
                    game.elapsedSeconds++
                    revision++
                    if(game.elapsedSeconds%5==0)store.save(game)
                }
            }
        }
        LaunchedEffect(game, peekNonce) {
            if(game.mode==PuzzleMode.MEMORY && game.preview) {
                delay(game.mode.peekMillis)
                game.preview=false
                store.save(game)
                revision++
            }
        }
        val onGuide: () -> Unit = {
            if(game.mode==PuzzleMode.MEMORY) {
                if(!game.preview && game.usePreview()) {
                    game.preview=true
                    peekNonce++
                }
            } else game.preview=!game.preview
            store.save(game)
            revision++
        }

        Surface(color=bg,modifier=Modifier.fillMaxSize()) {
            if(landscape) {
                Row(Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    PuzzleBoard(Modifier.weight(1f).fillMaxHeight(),solved,paused,
                        {paused=false}) {
                        newPuzzleMode=game.mode;difficultyPicker=true
                    }
                    Column(Modifier.width(166.dp).fillMaxHeight().verticalScroll(
                        rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Text("JIGSAW",fontSize=19.sp,fontWeight=FontWeight.Black,
                            color=forest,letterSpacing=1.2.sp)
                        Text(photoTitle,fontSize=11.sp,
                            color=muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Progress(revision)
                        GameActions(revision,true,
                            {newPuzzleMode=game.mode;difficultyPicker=true},
                            {zonePicker=true},onGuide,{paused=true})
                        Text("Drag · Zoom · Pan",fontSize=11.sp,color=muted)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=8.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Column {
                            Text("JIGSAW",fontSize=21.sp,fontWeight=FontWeight.Black,
                                color=forest,letterSpacing=1.6.sp)
                            Text(photoTitle,fontSize=11.sp,color=muted,maxLines=1,
                                overflow=TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick=openLibrary) {
                            Text("Pictures",fontSize=12.sp)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(if(solved)"Completed!" else "${game.completed} / ${game.count} pieces",
                            fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                        TextButton(onClick={
                            newPuzzleMode=game.mode;difficultyPicker=true
                        }) {Text("New puzzle")}
                    }
                    LinearProgressIndicator(progress={game.completed.toFloat()/game.count},
                        modifier=Modifier.fillMaxWidth().height(4.dp),
                        color=forest,trackColor=Color(0xFFDBDDD3))
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("${game.mode.label} · ${clockText(game.elapsedSeconds)} · ${game.moves} moves",
                            color=muted,fontSize=11.sp)
                        TextButton(onClick={paused=!paused}) {
                            Text(if(paused)"Resume" else "Pause",fontSize=12.sp)
                        }
                    }
                    PuzzleBoard(Modifier.weight(1f).fillMaxWidth(),solved,paused,
                        {paused=false}) {
                        newPuzzleMode=game.mode;difficultyPicker=true
                    }
                    Spacer(Modifier.height(8.dp))
                    GameActions(revision,false,
                        {newPuzzleMode=game.mode;difficultyPicker=true},
                        {zonePicker=true},onGuide,{paused=true})
                    Spacer(Modifier.height(4.dp))
                    Text(if(game.zoneCount>1)
                        "Drag · Zoom · Sort sections" else "Drag · Zoom · Pan",
                        color=muted,fontSize=11.sp,
                        modifier=Modifier.fillMaxWidth())
                }
            }
        }

        if(libraryPicker) AlertDialog(
            onDismissRequest={libraryPicker=false},
            title={Text("Your puzzle pictures")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Choose an existing picture or import a new one. Your previous photographs stay saved.",
                        color=muted,fontSize=12.sp)
                    Button(onClick={
                        libraryPicker=false
                        photoPicker.launch("image/*")
                    },modifier=Modifier.fillMaxWidth()) {
                        Text("Add a picture from your phone")
                    }
                    OutlinedButton(onClick={
                        libraryPicker=false
                        onlinePicker=true
                    },modifier=Modifier.fillMaxWidth()) {
                        Text("Browse downloadable puzzles")
                    }
                    val savedPhotos=remember(libraryPicker,libraryRevision){store.listPhotos()}
                    Text("${savedPhotos.size} saved · ${store.savedPhotoBytes()/1_048_576} MB",
                        color=muted,fontSize=11.sp)
                    LazyColumn(Modifier.heightIn(max=320.dp),
                        verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        item {
                            OutlinedButton(onClick={
                                picture=SceneImage.create()
                                game=PuzzleGame.fresh(game.cols,game.rows,"starter",
                                    landscape=landscape)
                                store.save(game);revision++;libraryPicker=false
                            },modifier=Modifier.fillMaxWidth()) {
                                Text("Mountain · starter illustration")
                            }
                        }
                        items(savedPhotos,key={it.key}) { entry ->
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment=Alignment.CenterVertically) {
                            OutlinedButton(onClick={
                                val selected=store.loadPhoto(entry.key)
                                if(selected!=null) {
                                    picture=selected
                                    game=PuzzleGame.fresh(game.cols,game.rows,entry.key,
                                        landscape=landscape)
                                    store.save(game);revision++;libraryPicker=false
                                }
                            },modifier=Modifier.weight(1f)) {
                                val thumbnail=remember(entry.key) {
                                    store.loadThumbnail(entry.key)?.asImageBitmap()
                                }
                                if(thumbnail!=null) {
                                    Image(bitmap=thumbnail,contentDescription="",
                                        contentScale=ContentScale.Crop,
                                        modifier=Modifier.size(54.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column {
                                    Text(entry.title,maxLines=1,
                                        overflow=TextOverflow.Ellipsis)
                                    Text(entry.source,fontSize=11.sp,color=muted)
                                }
                            }
                            if(entry.key!=game.imageKey) {
                                TextButton(onClick={photoToRemove=entry}) {
                                    Text("Remove",fontSize=11.sp)
                                }
                            }
                            }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={libraryPicker=false}){Text("Close")}}
        )

        if(photoToRemove!=null) AlertDialog(
            onDismissRequest={photoToRemove=null},
            title={Text("Remove saved picture?")},
            text={Text("Remove this picture from NIGHT Jigsaw? This won't affect the original photo on your device.")},
            confirmButton={TextButton(onClick={
                val item=photoToRemove
                if(item!=null && store.deletePhoto(item.key,game.imageKey)) {
                    libraryRevision++
                }
                photoToRemove=null
            }) {Text("Remove")}},
            dismissButton={TextButton(onClick={photoToRemove=null}) {Text("Cancel")}}
        )

        if(onlinePicker) AlertDialog(
            onDismissRequest={if(downloading==null)onlinePicker=false},
            title={Text("Downloadable photographs")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("14 handpicked puzzles · online download once, then play offline. Downloads use data.",
                        fontSize=12.sp,color=muted)
                    LazyColumn(Modifier.heightIn(max=360.dp),
                        verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        items(CuratedPhotoCatalog.photos,key={it.id}) { candidate ->
                            OutlinedButton(
                                enabled=downloading==null,
                                onClick={
                                    downloading=candidate.id
                                    Thread {
                                        val outcome=runCatching {
                                            CuratedPhotoDownloader(store,cacheDir)
                                                .download(candidate)
                                        }
                                        runOnUiThread {
                                            downloading=null
                                            outcome.onSuccess { chosen ->
                                                picture=chosen.bitmap
                                                game=PuzzleGame.fresh(game.cols,game.rows,
                                                    chosen.photo.key,landscape=landscape)
                                                store.save(game)
                                                onlinePicker=false
                                                revision++
                                            }.onFailure { error ->
                                                Toast.makeText(this@MainActivity,
                                                    error.message ?: "Download failed",
                                                    Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }.start()
                                },
                                modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.title,maxLines=1,
                                        overflow=TextOverflow.Ellipsis)
                                    Text(candidate.category+" · Unsplash",
                                        fontSize=11.sp,color=muted)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if(downloading==candidate.id) "Saving…" else "Get")
                            }
                        }
                    }
                    Text("Images: Unsplash License. Provider attribution is shown on each picture.",
                        fontSize=11.sp,color=muted)
                }
            },
            confirmButton={TextButton(
                enabled=downloading==null,
                onClick={onlinePicker=false}){Text("Close")}}
        )

        if(zonePicker && game.zoneCount>1) AlertDialog(
            onDismissRequest={zonePicker=false},
            title={Text("Work on a section")},
            text={
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Organize large puzzles into sections. All completed pieces remain on the board.",
                        color=muted,fontSize=13.sp)
                    (0 until game.zoneCount).forEach { zone ->
                        OutlinedButton(onClick={
                            game.selectZone(zone)
                            store.save(game)
                            revision++
                            zonePicker=false
                            board?.focusZone()
                        },modifier=Modifier.fillMaxWidth()) {
                            Text("${zone+1}. ${game.zoneName(zone)} · ${game.zoneRemaining(zone)} left")
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={zonePicker=false}) {
                Text("Close")
            }}
        )

        if(difficultyPicker) AlertDialog(
            onDismissRequest={difficultyPicker=false},
            title={Text("Choose a puzzle size")},
            text={
                Column {
                    Text("Choose how to play, then choose a size.",
                        fontSize=13.sp,color=muted)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        PuzzleMode.entries.forEach { option ->
                            val selected=newPuzzleMode==option
                            OutlinedButton(onClick={newPuzzleMode=option},
                                modifier=Modifier.weight(1f),
                                colors=ButtonDefaults.outlinedButtonColors(
                                    containerColor=if(selected)Color(0xFFD5E5D9) else bg
                                )) {
                                Text(option.label,fontSize=11.sp,maxLines=1)
                            }
                        }
                    }
                    Text(when(newPuzzleMode) {
                        PuzzleMode.RELAXED -> "3 hints · reference guide"
                        PuzzleMode.CHALLENGE -> "1 hint · reference guide"
                        PuzzleMode.MEMORY -> "No hints · one 5-second peek"
                    },fontSize=11.sp,color=muted)
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.heightIn(max=255.dp)
                        .verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        difficulties.forEach { (cols,rows,label) ->
                            OutlinedButton(onClick={
                                game=PuzzleGame.fresh(cols,rows,game.imageKey,
                                    landscape=landscape,mode=newPuzzleMode)
                                paused=false
                                store.save(game);revision++;difficultyPicker=false
                            },modifier=Modifier.fillMaxWidth()) {
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={difficultyPicker=false}) {
                Text("Cancel")
            }}
        )
    }
}
