#!/usr/bin/env python3
"""Pixel-tested audio UX patch: generous sound field, precision, reliable live gain."""
from pathlib import Path
p=Path("EchoSpace/app/src/main/java/com/nexa/echospace/MainActivity.kt")
s=p.read_text()

def change(a,b,label):
    global s
    n=s.count(a)
    if n!=1: raise RuntimeError(f"audio UX {label}: found {n}, expected 1")
    s=s.replace(a,b,1)

change("import kotlin.math.hypot\n",
       "import kotlin.math.hypot\nimport kotlin.math.atan2\nimport kotlin.math.PI\nimport kotlin.math.cos\nimport kotlin.math.sin\n",
       "angle math imports")
change("import androidx.compose.material.icons.filled.AcUnit\n",
       "import androidx.compose.material.icons.filled.AcUnit\n"
       "import androidx.compose.material.icons.filled.LocalCafe\n"
       "import androidx.compose.material.icons.filled.Train\n",
       "sound icons imports")
change('        "thunder" in name -> Icons.Default.FlashOn',
       '''        "café" in name || "cafe" in name -> Icons.Default.LocalCafe
        "train" in name -> Icons.Default.Train
        "fan" in name -> Icons.Default.Air
        "white noise" in name -> Icons.Default.Cloud
        "pink noise" in name -> Icons.Default.MusicNote
        "brown noise" in name -> Icons.Default.GraphicEq
        "thunder" in name -> Icons.Default.FlashOn''',
       "sound icon mapping")

change(
 "BoxWithConstraints(Modifier.fillMaxWidth().height(if (expanded) 332.dp else 236.dp)",
 "BoxWithConstraints(Modifier.fillMaxWidth().height(if (expanded) 425.dp else 236.dp)",
 "large sound field")
change(
 "val radius = minOf(diameter * 0.365f, fieldHeight * 0.38f)",
 "val radius = minOf(diameter * (if (expanded) 0.44f else 0.365f),\n"
 "            fieldHeight * (if (expanded) 0.43f else 0.38f))",
 "more usable radial movement")
change("val nodeSize = if (expanded) 64.dp else 54.dp",
       "val nodeSize = if (expanded) 70.dp else 54.dp",
       "large, reachable source markers")

change(
'''    val focusedLayerRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedId, sources.size) {
        if (sources.any { it.id == selectedId }) {
            delay(350)
            focusedLayerRequester.bringIntoView()
        }
    }
''',
"",
"remove forced scroll that hid Save and Close")
change(
'''    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp)
            .navigationBarsPadding()
    ) {''',
'''    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.96f)
            .padding(horizontal = 14.dp)
            .navigationBarsPadding()
    ) {''',
"pinned modal header")
change(
'''        Spacer(Modifier.height(16.dp))
        SoundMap(''',
'''        Spacer(Modifier.height(9.dp))
        // Header stays visible. Only mixer content scrolls.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SoundMap(''',
"scroll mixer body, not header")
change(
'''        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun BrandMark''',
'''        Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun BrandMark''',
"close nested scroll")

# The card used a parent click modifier that competed with the gain slider
# and triggered unexpected re-selection under finger drags.
change(
'''                    .clickable { onSelect(sound.id) }
                    .then(if (focused) Modifier.bringIntoViewRequester(focusedLayerRequester)
                        else Modifier)
                    .padding(horizontal = 12.dp, vertical = 11.dp)''',
'''                    .padding(horizontal = 12.dp, vertical = 11.dp)''',
"remove interfering card click")
change(
'''                    Column(Modifier.weight(1f)) {
                        Text(sound.kind.title, color = white''',
'''                    Column(Modifier.weight(1f).clickable { onSelect(sound.id) }) {
                        Text(sound.kind.title, color = white''',
"select sound by tapping name")

change(
'''                ThickGainControl(
                    sound.gain, tint, { value -> onGain(sound.id, value) },
                    modifier = Modifier.fillMaxWidth(), height = 62.dp,
                    label = sound.kind.title + " level"
                )''',
'''                Slider(
                    value = sound.gain.coerceIn(0f, 1f),
                    onValueChange = { level -> onGain(sound.id, level) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .semantics { contentDescription = sound.kind.title + " level" },
                    colors = SliderDefaults.colors(
                        thumbColor = tint,
                        activeTrackColor = tint,
                        inactiveTrackColor = Color(0xFF3E5161)
                    )
                )''',
"replace broken canvas gesture with real per-layer slider")

change(
'''            onSelect = onSelect, onMove = onMove, expanded = true
        )
        Spacer(Modifier.height(12.dp))''',
'''            onSelect = onSelect, onMove = onMove, expanded = true
        )
        // Drag for broad positioning; these distinct 5-degree and distance nudges
        // give reliable fine positioning even when the phone is held one-handed.
        val targetSound = sources.firstOrNull { it.id == selectedId }
        if (targetSound != null) {
            fun nudge(angleDelta: Double = 0.0, distanceDelta: Float = 0f) {
                val r = hypot(targetSound.x, targetSound.y).coerceIn(0.12f, 0.98f)
                val theta = atan2(targetSound.x.toDouble(), -targetSound.y.toDouble()) +
                    angleDelta * PI / 180.0
                val newR = (r + distanceDelta).coerceIn(0.12f, 0.98f)
                val dx = (sin(theta) * newR).toFloat() - targetSound.x
                val dy = (-cos(theta) * newR).toFloat() - targetSound.y
                onMove(targetSound.id, dx, dy)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(targetSound.kind.title, color = white, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val angle = (atan2(targetSound.x.toDouble(),
                    -targetSound.y.toDouble()) * 180.0 / PI).roundToInt()
                val distance = (hypot(targetSound.x, targetSound.y) * 100).roundToInt()
                Text(angle.toString() + "°  ·  " + distance.toString() + "%",
                    color = tint, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = { nudge(angleDelta = -5.0) },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        .semantics { contentDescription = "Move selected sound five degrees left" }) {
                    Text("↶ 5°", color = white, fontSize = 13.sp)
                }
                TextButton(onClick = { nudge(angleDelta = 5.0) },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        .semantics { contentDescription = "Move selected sound five degrees right" }) {
                    Text("5° ↷", color = white, fontSize = 13.sp)
                }
                TextButton(onClick = { nudge(distanceDelta = -0.06f) },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        .semantics { contentDescription = "Move selected sound closer" }) {
                    Text("Near", color = white, fontSize = 13.sp)
                }
                TextButton(onClick = { nudge(distanceDelta = 0.06f) },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        .semantics { contentDescription = "Move selected sound farther" }) {
                    Text("Far", color = white, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))''',
"exact small-step positioning for selected source")
p.write_text(s)
print("ECHOSPACE_MIXER_PRECISION_APPLIED",len(s),flush=True)
