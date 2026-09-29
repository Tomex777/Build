package com.tomex777.cubic

import android.os.Bundle
import androidx.activity.ComponentActivity
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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private enum class Mode { PLAY, LEARN }

private data class LearnStep(
    val title: String,
    val body: String,
    val axis: Axis
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                CubicApp()
            }
        }
    }
}

@Composable
private fun CubicApp() {
    val puzzle = remember { PuzzleState(3, 3, 3) }
    var revision by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(Mode.PLAY) }
    var lastMove by remember { mutableStateOf("Ready") }
    var learnIndex by remember { mutableIntStateOf(0) }

    val learnSteps = remember {
        listOf(
            LearnStep(
                "Read the puzzle",
                "Drag the open space to orbit the puzzle. Pinch to move the camera. The highlighted layer is the one we are discussing.",
                Axis.Y
            ),
            LearnStep(
                "Learn a face turn",
                "A face turn moves one complete layer. On a cuboid, Cubic uses a half-turn when a quarter-turn would not preserve the shape.",
                Axis.X
            ),
            LearnStep(
                "Build a repeatable sequence",
                "Practice R, U and F. Cubic records every legal move, so Undo and the guided solver always have a valid route back.",
                Axis.Z
            )
        )
    }
    val learnStep = learnSteps[learnIndex]
    val guideMove = puzzle.nextSolutionMove()

    fun changed(message: String) {
        lastMove = message
        revision++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { context -> CubeSurfaceView(context) },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                revision
                view.setPuzzle(puzzle.snapshot())
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            color = Color(0xCC101621),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cubic", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${puzzle.width} × ${puzzle.height} × ${puzzle.depth}  •  ${if (puzzle.isSolved()) "Solved" else "${puzzle.moveCount()} moves"}  •  $lastMove",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB7C0CE)
                    )
                }
                ModeButton("Play", mode == Mode.PLAY) { mode = Mode.PLAY }
                Spacer(Modifier.width(6.dp))
                ModeButton("Learn", mode == Mode.LEARN) { mode = Mode.LEARN }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            color = Color(0xF2101621),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (mode == Mode.LEARN) {
                    if (guideMove != null) {
                        Text("Next move: ${guideMove.label}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            puzzle.describe(guideMove) + " The highlighted pieces are the layer that will move.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC1CAD8)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val applied = puzzle.solveNextStep()
                                    changed(
                                        when {
                                            applied == null -> "Already solved"
                                            puzzle.isSolved() -> "Solved"
                                            else -> "Learned ${applied.label}"
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Do this move") }
                            TextButton(
                                onClick = {
                                    val undone = puzzle.undo()
                                    changed(if (undone == null) "Nothing to undo" else "Undo ${undone.label}")
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { learnIndex = (learnIndex - 1 + learnSteps.size) % learnSteps.size }
                            ) { Text("Previous") }
                            TextButton(
                                onClick = { learnIndex = (learnIndex + 1) % learnSteps.size }
                            ) { Text("Next") }
                            Button(
                                onClick = {
                                    val move = puzzle.turnOuter(learnStep.axis)
                                    changed("Practice ${move.label}")
                                }
                            ) { Text("Practice") }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DimensionStepper("W", puzzle.width) {
                            puzzle.resize(it, puzzle.height, puzzle.depth)
                            changed("Resized")
                        }
                        DimensionStepper("H", puzzle.height) {
                            puzzle.resize(puzzle.width, it, puzzle.depth)
                            changed("Resized")
                        }
                        DimensionStepper("D", puzzle.depth) {
                            puzzle.resize(puzzle.width, puzzle.height, it)
                            changed("Resized")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                puzzle.scramble()
                                changed("Scrambled")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Scramble") }
                        Button(
                            onClick = {
                                val undone = puzzle.undo()
                                changed(if (undone == null) "Nothing to undo" else "Undo ${undone.label}")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Undo") }
                        Button(
                            onClick = {
                                puzzle.reset()
                                changed("Solved")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reset") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Axis.X to "R", Axis.Y to "U", Axis.Z to "F").forEach { (axis, label) ->
                            Button(
                                onClick = {
                                    val move = puzzle.turnOuter(axis)
                                    changed(move.label)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) { Text(label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Axis.X to "R'", Axis.Y to "U'", Axis.Z to "F'").forEach { (axis, label) ->
                            Button(
                                onClick = {
                                    val move = puzzle.turnOuter(axis, positive = false)
                                    changed(move.label)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) { Text(label) }
                        }
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
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    ) { Text(label) }
}

@Composable
private fun DimensionStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            TextButton(onClick = { onChange((value - 1).coerceAtLeast(2)) }) { Text("−") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF98A5B8))
                Text(value.toString(), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { onChange((value + 1).coerceAtMost(9)) }) { Text("+") }
        }
    }
}
