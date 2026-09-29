package com.tomex777.cubic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

private enum class Mode { PLAY, LEARN }

private data class LearnStep(
    val title: String,
    val body: String,
    val axis: Axis
)

class MainActivity : ComponentActivity() {
    private var cubeSurfaceView: CubeSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF070A10),
                    surface = Color(0xFF101621),
                    surfaceVariant = Color(0xFF171F2C),
                    primary = Color(0xFF8EA8FF),
                    onPrimary = Color(0xFF0A1020),
                    onBackground = Color(0xFFF2F5FA),
                    onSurface = Color(0xFFF2F5FA)
                )
            ) {
                CubicApp(
                    onSurfaceReady = { cubeSurfaceView = it }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cubeSurfaceView?.onResume()
    }

    override fun onPause() {
        cubeSurfaceView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        cubeSurfaceView = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CubicApp(
    onSurfaceReady: (CubeSurfaceView) -> Unit
) {
    val puzzle = remember { PuzzleState(3, 3, 3) }
    var revision by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(Mode.PLAY) }
    var learnIndex by remember { mutableIntStateOf(0) }
    var selectedFace by remember { mutableStateOf(Face.R) }
    var selectedLayerDepth by remember { mutableIntStateOf(1) }
    var controlsOpen by remember { mutableStateOf(false) }
    var renderedRevision by remember { mutableIntStateOf(-1) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val learnSteps = remember {
        listOf(
            LearnStep(
                "Read the puzzle",
                "Drag to orbit. Pinch to zoom. In Learn mode, Cubic highlights the layer for each move.",
                Axis.Y
            ),
            LearnStep(
                "Face turns",
                "Turn clockwise or counterclockwise as viewed straight at the selected face. Rectangular cuboid sections turn 180° when a quarter-turn would not preserve the shape.",
                Axis.X
            ),
            LearnStep(
                "Practice a sequence",
                "Make a move in Play, then return to Learn. Cubic will highlight the exact layer and walk that move back step by step.",
                Axis.Z
            )
        )
    }

    val learnStep = learnSteps[learnIndex]
    val guideMove = remember(revision) { puzzle.nextSolutionMove() }
    val moveCount = puzzle.moveCount()
    val statusText = when {
        puzzle.isSolved() -> "Solved"
        moveCount == 1 -> "1 move"
        else -> "$moveCount moves"
    }

    fun changed() {
        revision++
    }

    fun closeControls() {
        scope.launch {
            sheetState.hide()
            controlsOpen = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { context ->
                CubeSurfaceView(
                    context = context,
                    onFrameRendered = { rendered ->
                        if (rendered > renderedRevision) {
                            renderedRevision = rendered
                        }
                    }
                ).also(onSurfaceReady)
            },
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = if (renderedRevision >= revision) {
                        "3D puzzle ready"
                    } else {
                        "3D puzzle updating"
                    }
                },
            update = { view ->
                view.setPuzzle(puzzle.snapshot(), revision)
                if (mode == Mode.LEARN) {
                    val move = puzzle.nextSolutionMove()
                    if (move != null) {
                        view.setHighlight(move.axis, move.layer)
                    } else {
                        val layer = when (learnStep.axis) {
                            Axis.X -> puzzle.width - 1
                            Axis.Y -> puzzle.height - 1
                            Axis.Z -> puzzle.depth - 1
                        }
                        view.setHighlight(learnStep.axis, layer)
                    }
                } else {
                    view.setHighlight(null, null)
                }
            }
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            color = Color(0xCC101621),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cubic", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${puzzle.width} × ${puzzle.height} × ${puzzle.depth}  •  $statusText",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB7C0CE)
                    )
                }
                Text(
                    if (mode == Mode.PLAY) "Play" else "Learn",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFB7C0CE)
                )
            }
        }

        if (!controlsOpen) {
            Button(
                onClick = { controlsOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
                    .semantics { contentDescription = "Open controls" },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Controls")
            }
        }
    }

    if (controlsOpen) {
        ModalBottomSheet(
            onDismissRequest = { controlsOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .semantics {
                        contentDescription = "Puzzle ${puzzle.width} × ${puzzle.height} × ${puzzle.depth}, status $statusText"
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Controls",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { closeControls() },
                        modifier = Modifier.semantics {
                            contentDescription = "Close controls"
                        }
                    ) {
                        Text("Close")
                    }
                }

                Text(
                    "${puzzle.width} × ${puzzle.height} × ${puzzle.depth}  •  $statusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7C0CE)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFB7C0CE),
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton("Play", mode == Mode.PLAY) { mode = Mode.PLAY }
                    Spacer(Modifier.width(4.dp))
                    ModeButton("Learn", mode == Mode.LEARN) { mode = Mode.LEARN }
                }

                if (mode == Mode.LEARN) {
                    if (guideMove != null) {
                        Text(
                            "Next move: ${guideMove.label}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            puzzle.describe(guideMove) + " Follow the highlighted layer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC1CAD8)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    puzzle.solveNextStep()
                                    changed()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Do this move") }
                            TextButton(
                                onClick = {
                                    puzzle.undo()
                                    changed()
                                }
                            ) { Text("Undo") }
                        }
                    } else {
                        Text(learnStep.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            learnStep.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC1CAD8)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    learnIndex = (learnIndex - 1 + learnSteps.size) % learnSteps.size
                                }
                            ) { Text("Previous") }
                            TextButton(
                                onClick = {
                                    learnIndex = (learnIndex + 1) % learnSteps.size
                                }
                            ) { Text("Next") }
                            Button(
                                onClick = {
                                    puzzle.turnOuter(learnStep.axis)
                                    changed()
                                }
                            ) { Text("Practice") }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DimensionStepper(
                            label = "Width",
                            value = puzzle.width,
                            modifier = Modifier.weight(1f)
                        ) {
                            puzzle.resize(it, puzzle.height, puzzle.depth)
                            selectedLayerDepth = 1
                            changed()
                        }
                        DimensionStepper(
                            label = "Height",
                            value = puzzle.height,
                            modifier = Modifier.weight(1f)
                        ) {
                            puzzle.resize(puzzle.width, it, puzzle.depth)
                            selectedLayerDepth = 1
                            changed()
                        }
                        DimensionStepper(
                            label = "Depth",
                            value = puzzle.depth,
                            modifier = Modifier.weight(1f)
                        ) {
                            puzzle.resize(puzzle.width, puzzle.height, it)
                            selectedLayerDepth = 1
                            changed()
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = {
                                puzzle.scramble()
                                changed()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Scramble") }
                        Button(
                            onClick = {
                                puzzle.undo()
                                changed()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Undo") }
                        Button(
                            onClick = {
                                puzzle.reset()
                                selectedLayerDepth = 1
                                changed()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reset") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Face.entries.forEach { face ->
                            TextButton(
                                onClick = {
                                    selectedFace = face
                                    selectedLayerDepth = 1
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = "Face ${face.label}" },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (selectedFace == face) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (selectedFace == face) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            ) { Text(face.label) }
                        }
                    }

                    val faceLayerCount = puzzle.layersFor(selectedFace)
                    val activeLayerDepth = selectedLayerDepth.coerceIn(1, faceLayerCount)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                selectedLayerDepth = (activeLayerDepth - 1).coerceAtLeast(1)
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Previous layer"
                            }
                        ) { Text("−") }
                        Text(
                            "Layer $activeLayerDepth of $faceLayerCount",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = {
                                selectedLayerDepth = (activeLayerDepth + 1).coerceAtMost(faceLayerCount)
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Next layer"
                            }
                        ) { Text("+") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = {
                                puzzle.turnFaceLayer(
                                    selectedFace,
                                    activeLayerDepth,
                                    clockwise = true
                                )
                                changed()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Turn clockwise" }
                        ) { Text("Clockwise") }
                        Button(
                            onClick = {
                                puzzle.turnFaceLayer(
                                    selectedFace,
                                    activeLayerDepth,
                                    clockwise = false
                                )
                                changed()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    contentDescription = "Turn counterclockwise"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("Counterclockwise") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) { Text(label) }
}

@Composable
private fun DimensionStepper(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
        ) {
            TextButton(
                onClick = { onChange((value - 1).coerceAtLeast(2)) },
                modifier = Modifier.semantics {
                    contentDescription = "Decrease $label"
                }
            ) { Text("−") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF98A5B8)
                )
                Text(value.toString(), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = { onChange((value + 1).coerceAtMost(9)) },
                modifier = Modifier.semantics {
                    contentDescription = "Increase $label"
                }
            ) { Text("+") }
        }
    }
}
