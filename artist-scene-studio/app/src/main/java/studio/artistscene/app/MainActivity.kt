package studio.artistscene.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.artistscene.core.SceneProject

class MainActivity : ComponentActivity() {
    private lateinit var store: studio.artistscene.core.SceneProjectStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = studio.artistscene.core.SceneProjectStore(this)
        val existing = runCatching { store.load(PrototypeScene.PROJECT_ID) }.getOrNull()
        setContent {
            MaterialTheme {
                StudioScreen(
                    initialProject = existing ?: PrototypeScene.create(),
                    initiallyRestored = existing != null,
                    onSave = store::save,
                    onRestore = { runCatching { store.load(PrototypeScene.PROJECT_ID) }.getOrNull() },
                )
            }
        }
    }
}

@Composable
private fun StudioScreen(
    initialProject: SceneProject,
    initiallyRestored: Boolean,
    onSave: (SceneProject) -> Unit,
    onRestore: () -> SceneProject?,
) {
    var project by remember { mutableStateOf(initialProject) }
    var assetStatus by remember { mutableStateOf("Loading bundled GLB…") }
    var rendererStatus by remember { mutableStateOf("Waiting for renderer surface") }
    var saveStatus by remember { mutableStateOf(if (initiallyRestored) "Restored saved scene" else "New scene") }
    val selected = project.actors.firstOrNull { it.kind.name == "PROP" }
    val x = selected?.transform?.position?.x ?: 0f

    Surface(Modifier.fillMaxSize(), color = Color(0xFF171A20)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(
                    Modifier.fillMaxSize().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SceneViewport(
                        project = project,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onAssetLoaded = { assetStatus = "Loaded GLB · " + it },
                        onAssetFailed = { assetStatus = "GLB load failed · " + it },
                        onRendererFrame = { rendererStatus = "Renderer loop active" },
                    )
                    EditorPanel(
                        project = project,
                        x = x,
                        assetStatus = assetStatus,
                        rendererStatus = rendererStatus,
                        saveStatus = saveStatus,
                        modifier = Modifier.weight(0.42f).fillMaxSize(),
                        onMove = { project = project.movePropX(it) },
                        onSave = { onSave(project); saveStatus = "Saved scene" },
                        onRestore = {
                            val restored = onRestore()
                            if (restored != null) {
                                project = restored
                                saveStatus = "Restored saved scene"
                            } else saveStatus = "No saved scene"
                        },
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
                        Text("Artist Scene Studio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("Scene-first feasibility build", color = Color(0xFFAAB4C2), fontSize = 13.sp)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SceneViewport(
                            project = project,
                            modifier = Modifier.fillMaxSize().background(Color(0xFF202630)),
                            onAssetLoaded = { assetStatus = "Loaded GLB · " + it },
                            onAssetFailed = { assetStatus = "GLB load failed · " + it },
                            onRendererFrame = { rendererStatus = "Renderer loop active" },
                        )
                        Text(
                            "LIVE FILAMENT VIEWPORT",
                            Modifier.align(Alignment.TopStart).padding(12.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    EditorPanel(
                        project = project,
                        x = x,
                        assetStatus = assetStatus,
                        rendererStatus = rendererStatus,
                        saveStatus = saveStatus,
                        modifier = Modifier.fillMaxWidth().height(210.dp),
                        onMove = { project = project.movePropX(it) },
                        onSave = { onSave(project); saveStatus = "Saved scene" },
                        onRestore = {
                            val restored = onRestore()
                            if (restored != null) {
                                project = restored
                                saveStatus = "Restored saved scene"
                            } else saveStatus = "No saved scene"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPanel(
    project: SceneProject,
    x: Float,
    assetStatus: String,
    rendererStatus: String,
    saveStatus: String,
    modifier: Modifier,
    onMove: (Float) -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier.background(Color(0xFF222832), RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(project.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(project.actors.size.toString() + " scene actors", color = Color(0xFFD0D7E1), fontSize = 12.sp)
        Text(assetStatus, color = Color(0xFFD0D7E1), fontSize = 12.sp, modifier = Modifier.testTag("asset-status"))
        Text(rendererStatus, color = Color(0xFFD0D7E1), fontSize = 12.sp, modifier = Modifier.testTag("renderer-status"))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onMove(-0.25f) }, modifier = Modifier.testTag("move-left")) { Text("X −") }
            Button(onClick = { onMove(0.25f) }, modifier = Modifier.testTag("move-right")) { Text("X +") }
            Text("X " + "%.2f".format(java.util.Locale.US, x), color = Color.White, modifier = Modifier.testTag("actor-x"), fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSave, modifier = Modifier.testTag("save-project")) { Text("Save") }
            Button(onClick = onRestore, modifier = Modifier.testTag("restore-project")) { Text("Restore") }
        }
        Text(saveStatus, color = Color(0xFFAAB4C2), fontSize = 11.sp, modifier = Modifier.testTag("save-status"))
    }
}

private fun SceneProject.movePropX(delta: Float): SceneProject = copy(
    actors = actors.map { actor ->
        if (actor.kind.name != "PROP") actor else actor.copy(
            transform = actor.transform.copy(
                position = actor.transform.position.copy(x = actor.transform.position.x + delta),
            ),
        )
    },
)
