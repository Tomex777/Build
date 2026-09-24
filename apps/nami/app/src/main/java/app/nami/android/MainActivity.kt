package app.nami.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NamiShell() }
    }
}

@androidx.compose.runtime.Composable
private fun NamiShell() {
    var selected by remember { mutableIntStateOf(0) }
    val areas = listOf("Library", "Browse", "More")
    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    areas.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Text(label.take(1)) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(areas[selected], style = MaterialTheme.typography.headlineMedium)
                if (selected == 0) {
                    Text("Your anime library is empty.", modifier = Modifier.padding(top = 12.dp))
                } else if (selected == 1) {
                    Text("Anime sources will appear here when installed.", modifier = Modifier.padding(top = 12.dp))
                } else {
                    Text("Nami tools and settings.", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
