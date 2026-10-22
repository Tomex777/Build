package com.nice.jigsaw

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
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
    private lateinit var image: Bitmap
    private var refresh: () -> Unit = {}
    private var board: PuzzleBoardView? = null

    private val photoPicker=registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri!=null) runCatching {
            val newImage=store.importPhoto(uri)
            image=newImage
            game=PuzzleGame.fresh(game.cols,game.rows,"custom")
            store.save(game)
            refresh()
        }.onFailure {
            Toast.makeText(this,it.message ?: "Couldn't open this photo",Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,true)
        WindowInsetsControllerCompat(window,window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())
        store=PuzzleStore(this)
        game=store.load() ?: PuzzleGame.fresh(6,4)
        val restoredPhoto = if (game.imageKey=="custom") store.loadPhoto() else null
        if (game.imageKey=="custom" && restoredPhoto==null) game=PuzzleGame.fresh(6,4)
        image=restoredPhoto ?: SceneImage.create()
        store.save(game)
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=forest,background=bg,
                surface=bg,onSurface=Color(0xFF242B26))) {
                JigsawScreen()
            }
        }
    }

    override fun onPause() { super.onPause(); if (::store.isInitialized) store.save(game) }

    @Composable
    private fun JigsawScreen() {
        var revision by remember { mutableIntStateOf(0) }
        var difficultyPicker by remember { mutableStateOf(false) }
        refresh={ revision++ }
        val progress=revision.let { game.completed }
        val solved=progress==game.count
        Surface(color=bg,modifier=Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=10.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Column {
                        Text("JIGSAW",fontSize=22.sp,fontWeight=FontWeight.Black,
                            letterSpacing=1.6.sp,color=forest)
                        Text(if (game.imageKey=="custom") "Your photograph"
                            else "Mountain • starter illustration",
                            fontSize=12.sp,color=muted,maxLines=1,
                            overflow=TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick={photoPicker.launch("image/*")}) {
                        Text("Choose photo")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(if(solved) "Completed!" else
                        progress.toString()+" / "+game.count+" pieces",
                        fontWeight=FontWeight.SemiBold,fontSize=16.sp)
                    TextButton(onClick={difficultyPicker=true}) {
                        Text("New puzzle")
                    }
                }
                LinearProgressIndicator(progress={progress.toFloat()/game.count},
                    modifier=Modifier.fillMaxWidth().height(4.dp),
                    color=forest,trackColor=Color(0xFFDBDDD3))
                Spacer(Modifier.height(12.dp))
                Box(modifier=Modifier.weight(1f).fillMaxWidth()
                    .border(1.dp,Color(0xFFD5D2C9),RoundedCornerShape(14.dp)),
                    contentAlignment=Alignment.Center) {
                    AndroidView(
                        factory={ context ->
                            PuzzleBoardView(context).also {
                                board=it
                                it.bind(game,image) { store.save(game);refresh() }
                            }
                        },
                        update={ view ->
                            board=view
                            view.bind(game,image) { store.save(game);refresh() }
                        },
                        modifier=Modifier.fillMaxSize()
                    )
                    if (solved) {
                        Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFFF7F5F0)) {
                            Column(Modifier.padding(22.dp),
                                horizontalAlignment=Alignment.CenterHorizontally) {
                                Text("Beautifully done.",fontSize=22.sp,fontWeight=FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("Every piece found its place.",color=muted)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick={difficultyPicker=true}) {Text("Play again")}
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(
                        onClick={game.preview=!game.preview;store.save(game);revision++},
                        modifier=Modifier.weight(1f)) {
                        Text(if(game.preview) "Hide guide" else "Guide",fontSize=13.sp,maxLines=1)
                    }
                    Button(onClick={
                            if (game.hint()) {store.save(game);revision++;board?.invalidate()}
                        },
                        enabled=!solved && game.hintsRemaining>0,
                        modifier=Modifier.weight(1f),
                        colors=ButtonDefaults.buttonColors(containerColor=forest)) {
                        Text("Hint · "+game.hintsRemaining,fontSize=13.sp,maxLines=1)
                    }
                    OutlinedButton(onClick={board?.centerBoard()},
                        modifier=Modifier.weight(1f)) {
                        Text("Center",fontSize=13.sp,maxLines=1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Drag pieces to the board · Pinch to zoom · Swipe empty space to pan",
                    color=muted,fontSize=11.sp,lineHeight=14.sp,
                    modifier=Modifier.fillMaxWidth())
            }
        }

        if(difficultyPicker) AlertDialog(
            onDismissRequest={difficultyPicker=false},
            title={Text("New puzzle")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("The current arrangement will be replaced.",color=muted)
                    listOf(Triple(6,4,"24 · Relaxed"), Triple(8,6,"48 · Focus"),
                        Triple(12,12,"144 · Challenge")).forEach { (cols,rows,label) ->
                        OutlinedButton(onClick={
                            game=PuzzleGame.fresh(cols,rows,game.imageKey)
                            store.save(game);revision++;difficultyPicker=false
                        },modifier=Modifier.fillMaxWidth()) {Text(label)}
                    }
                }
            },
            confirmButton={TextButton(onClick={difficultyPicker=false}){Text("Cancel")}}
        )
    }
}
