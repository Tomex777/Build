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
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.rememberEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioShell() }
    }
}

@Composable
private fun StudioShell() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF171A20)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Viewport(Modifier.weight(1f).fillMaxSize())
                        StatusPanel(Modifier.weight(0.38f).fillMaxSize())
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Header()
                        Viewport(Modifier.fillMaxWidth().weight(1f))
                        StatusPanel(Modifier.fillMaxWidth().height(124.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Artist Scene Studio", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Scene-first feasibility build", color = Color(0xFFAAB4C2), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Viewport(modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    Box(modifier.background(Color(0xFF202630))) {
        SceneView(engine = engine, modifier = Modifier.fillMaxSize()) {
            // Engineering fixture only; this is not a production model or actor library.
            CubeNode(size = Size(0.8f))
        }
        Text(
            "LIVE RENDERER · ENGINEERING FIXTURE",
            Modifier.align(Alignment.TopStart).padding(12.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun StatusPanel(modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color(0xFF222832)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Foundation status", color = Color.White, fontWeight = FontWeight.SemiBold)
        Text("Scene data is renderer-independent.", color = Color(0xFFD0D7E1), style = MaterialTheme.typography.bodyMedium)
        Text("Licensed GLB + humanoid proof remains open.", color = Color(0xFFAAB4C2), style = MaterialTheme.typography.bodySmall)
    }
}
