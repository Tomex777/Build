package com.night.jigsaw

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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

class MainActivity : ComponentActivity() {
    private lateinit var store: PuzzleStore
    private lateinit var game: PuzzleGame
    private lateinit var picture: Bitmap
    private var refresh: () -> Unit = {}
    private var board: PuzzleBoardView? = null

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
        if (uri!=null) runCatching {
            val chosen=store.importPhoto(uri)
            picture=chosen
            game=PuzzleGame.fresh(game.cols,game.rows,"custom",
                landscape=game.landscapeMode)
            store.save(game)
            refresh()
        }.onFailure {
            Toast.makeText(this,it.message ?: "Couldn't open this photo",Toast.LENGTH_LONG).show()
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
        val restoredPhoto=if(game.imageKey=="custom") store.loadPhoto() else null
        if (game.imageKey=="custom" && restoredPhoto==null) game=PuzzleGame.fresh(6,4)
        picture=restoredPhoto ?: SceneImage.create()
        store.save(game)
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=forest,background=bg,
                surface=bg,onSurface=Color(0xFF242B26))) {
                JigsawScreen()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::store.isInitialized) store.save(game)
    }

    @Composable
    private fun PuzzleBoard(modifier: Modifier, solved: Boolean, onNewPuzzle: () -> Unit) {
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
                        Text("Every piece found its place.",color=muted,fontSize=13.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick=onNewPuzzle) { Text("Play again") }
                    }
                }
            }
        }
    }

    @Composable
    private fun GameActions(revision: Int, wide: Boolean,
                            update: () -> Unit, chooseZone: () -> Unit) {
        val solved=revision.let { game.solved }
        if(wide) {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick={photoPicker.launch("image/*")},
                    modifier=Modifier.fillMaxWidth()) { Text("Choose photo",fontSize=12.sp) }
                OutlinedButton(onClick={update()},
                    modifier=Modifier.fillMaxWidth()) { Text("New puzzle",fontSize=12.sp) }
                if(game.zoneCount>1) OutlinedButton(onClick=chooseZone,
                    modifier=Modifier.fillMaxWidth()) {
                    Text("Zone ${game.activeZone+1}/${game.zoneCount}",fontSize=12.sp)
                }
                OutlinedButton(
                    onClick={game.preview=!game.preview;store.save(game);refresh()},
                    modifier=Modifier.fillMaxWidth()) {
                    Text(if(game.preview)"Hide guide" else "Guide",fontSize=12.sp)
                }
                Button(
                    onClick={if(game.hint()){store.save(game);refresh();board?.invalidate()}},
                    enabled=!solved && game.hintsRemaining>0,
                    colors=ButtonDefaults.buttonColors(containerColor=forest),
                    modifier=Modifier.fillMaxWidth()) {
                    Text("Hint · ${game.hintsRemaining}",fontSize=12.sp)
                }
                OutlinedButton(onClick={board?.centerBoard()},
                    modifier=Modifier.fillMaxWidth()) { Text("Center board",fontSize=12.sp) }
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
                    onClick={game.preview=!game.preview;store.save(game);refresh()},
                    modifier=Modifier.weight(1f)) {
                    Text(if(game.preview)"Hide guide" else "Guide",fontSize=12.sp,maxLines=1)
                }
                Button(
                    onClick={if(game.hint()){store.save(game);refresh();board?.invalidate()}},
                    enabled=!solved && game.hintsRemaining>0,
                    colors=ButtonDefaults.buttonColors(containerColor=forest),
                    modifier=Modifier.weight(1f)) {
                    Text("Hint · ${game.hintsRemaining}",fontSize=12.sp,maxLines=1)
                }
                OutlinedButton(onClick={board?.centerBoard()},
                    modifier=Modifier.weight(1f)) {
                    Text("Center",fontSize=12.sp,maxLines=1)
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
        refresh={ revision++ }
        val landscape=LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if(game.setLandscape(landscape)) {
            store.save(game)
            board?.centerBoard()
        }
        val solved=revision.let { game.solved }

        Surface(color=bg,modifier=Modifier.fillMaxSize()) {
            if(landscape) {
                Row(Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    PuzzleBoard(Modifier.weight(1f).fillMaxHeight(),solved) {
                        difficultyPicker=true
                    }
                    Column(Modifier.width(166.dp).fillMaxHeight().verticalScroll(
                        rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Text("JIGSAW",fontSize=19.sp,fontWeight=FontWeight.Black,
                            color=forest,letterSpacing=1.2.sp)
                        Text(if(game.imageKey=="custom")"Your photograph"
                            else "Mountain · illustration",fontSize=11.sp,
                            color=muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Progress(revision)
                        GameActions(revision,true,{difficultyPicker=true},{zonePicker=true})
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
                            Text(if(game.imageKey=="custom")"Your photograph"
                                else "Mountain · illustration",fontSize=11.sp,color=muted)
                        }
                        OutlinedButton(onClick={photoPicker.launch("image/*")}) {
                            Text("Choose photo",fontSize=12.sp)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(if(solved)"Completed!" else "${game.completed} / ${game.count} pieces",
                            fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                        TextButton(onClick={difficultyPicker=true}) {
                            Text("New puzzle")
                        }
                    }
                    LinearProgressIndicator(progress={game.completed.toFloat()/game.count},
                        modifier=Modifier.fillMaxWidth().height(4.dp),
                        color=forest,trackColor=Color(0xFFDBDDD3))
                    Spacer(Modifier.height(8.dp))
                    PuzzleBoard(Modifier.weight(1f).fillMaxWidth(),solved) {
                        difficultyPicker=true
                    }
                    Spacer(Modifier.height(8.dp))
                    GameActions(revision,false,{difficultyPicker=true},{zonePicker=true})
                    Spacer(Modifier.height(4.dp))
                    Text("Drag pieces · Pinch to zoom · Pan empty space",
                        color=muted,fontSize=11.sp,
                        modifier=Modifier.fillMaxWidth())
                }
            }
        }

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
                    Text("Start a new puzzle using your selected picture.",
                        fontSize=13.sp,color=muted)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max=300.dp)
                        .verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        difficulties.forEach { (cols,rows,label) ->
                            OutlinedButton(onClick={
                                game=PuzzleGame.fresh(cols,rows,game.imageKey,
                                    landscape=landscape)
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
