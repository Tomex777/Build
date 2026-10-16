#!/usr/bin/env python3
"""ABR pass for EchoSpace: one uncluttered main sound surface, one full mixer."""
from pathlib import Path

p = Path("EchoSpace/app/src/main/java/com/nexa/echospace/MainActivity.kt")
gradle = Path("EchoSpace/app/build.gradle.kts")
s = p.read_text()

def one(before, after, label="replacement"):
    global s
    n = s.count(before)
    if n != 1:
        raise RuntimeError(f"ABR {label}: expected 1 anchor, got {n}: {before[:120]!r}")
    s = s.replace(before, after, 1)

def between(start, stop, replacement, label):
    global s
    a = s.find(start)
    b = s.find(stop, a + len(start))
    if a < 0 or b < 0:
        raise RuntimeError(f"ABR {label}: missing source section")
    s = s[:a] + replacement + s[b:]

# Vector icons instead of emoji and unicode navigation symbols.
imports = """import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.ui.graphics.vector.ImageVector
"""
one("import androidx.compose.material3.*\n", imports + "import androidx.compose.material3.*\n", "vector imports")

# The old compact gain/mute/remove surface duplicated the mixer.
one("    val selected = sources.firstOrNull { it.id == selectedId }\n", "", "duplicate selection")
between("            if (selected != null) {\n",
        "            service?.error()?.let {",
        "", "duplicate selected sound card")
between("@Composable\nprivate fun SelectedSound(",
        "@Composable\nprivate fun ThickGainControl(",
        "", "unused SelectedSound")
between("@Composable\nprivate fun GlassPanel(",
        "@Composable\nprivate fun SceneTile(",
        "", "unused glass bar")
one("    var directionOpen by remember { mutableStateOf(false) }\n", "", "redundant rotation popup")
one("        directionOpen = false\n", "", "rotation popup state reset")

# The immersive main screen keeps the field itself and one obvious entry to edit.
between(
    "                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp),\n",
    "                }\n                Box(Modifier.matchParentSize().border(",
    """                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { mixerOpen = true },
                            shape = RoundedCornerShape(17.dp),
                            color = Color(0xFF132A34).copy(alpha = 0.94f),
                            modifier = Modifier.heightIn(min = 50.dp)
                                .semantics { contentDescription = "Open full sound mixer" }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, null, tint = glow, modifier = Modifier.size(21.dp))
                                Spacer(Modifier.width(9.dp))
                                Text("Mixer", color = white, fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
""",
    "main immersive map controls",
)
between(
    "            if (directionOpen) {\n",
    "            Spacer(Modifier.height(20.dp))\n",
    "",
    "duplicate orientation popup",
)

# Explore is for selecting environments, not for configuring/saving sounds.
between(
    "            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n"
    "                Text(\"Explore\",",
    "            Spacer(Modifier.height(7.dp))",
    """            Text("Explore", color = white, fontSize = 19.sp,
                letterSpacing = (-0.35).sp, fontWeight = FontWeight.SemiBold)
""",
    "Explore unrelated sound actions",
)

# Use preview audio only when soloing, without persisting altered mute flags.
one(
    """    val audibleSources = if (soloId < 0) sources else sources.map {
        if (it.id == soloId) it else it.copy(muted = true)
    }
""",
    """    val audibleSources = if (soloId < 0) sources else sources.map {
        it.copy(muted = it.id != soloId)
    }
""",
    "solo audible preview",
)

# Add/save/living/orientation controls belong inside the one expanded mixer.
one(
    """                heading = heading,
                tint = glow,
""",
    """                heading = heading,
                living = living,
                tint = glow,
""",
    "mixer living argument",
)
one(
    """                onMove = { id, dx, dy -> moveSource(id, dx, dy) },
                onGain = { id, gain -> editSource(id) { it.copy(gain = gain) } },
""",
    """                onMove = { id, dx, dy -> moveSource(id, dx, dy) },
                onHeading = { heading = it },
                onLiving = { living = !living },
                onAdd = { mixerOpen = false; soloId = -1; addOpen = true },
                onSave = {
                    savedMix = sources.map { it.copy() }
                    store.saveMyMix(sources)
                    title = "My Space"
                    soloId = -1
                    mixerOpen = false
                },
                onGain = { id, gain -> editSource(id) { it.copy(gain = gain) } },
""",
    "new mixer callbacks",
)

one(
    """    heading: Float,
    tint: Color,
    playing: Boolean,
    breathe: Float,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onGain: (Int, Float) -> Unit,
""",
    """    heading: Float,
    living: Boolean,
    tint: Color,
    playing: Boolean,
    breathe: Float,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onHeading: (Float) -> Unit,
    onLiving: () -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onGain: (Int, Float) -> Unit,
""",
    "mixer signature",
)

# Save and close are distinct: save persists a named soundscape; close just returns.
one(
    """            IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Close, "Close mixer", tint = white)
            }
""",
    """            IconButton(onClick = onSave, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.BookmarkAdd, "Save current soundscape",
                    tint = tint, modifier = Modifier.size(25.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Close, "Close mixer", tint = white)
            }
""",
    "mixer save action",
)
one(
    """        Spacer(Modifier.height(9.dp))
        Text("Drag sounds around the listener to change their direction and distance",
            color = secondary, fontSize = 12.sp)
        Spacer(Modifier.height(23.dp))
        Text("Layers", color = white, fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        sources.sortedByDescending { it.id == selectedId }.forEach { sound ->
""",
    """        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Facing " + heading.roundToInt() + "°",
                color = secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onHeading((heading - 30f).coerceAtLeast(-180f)) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.RotateLeft, "Rotate 30 degrees left", tint = white)
            }
            TextButton(
                onClick = { onHeading(0f) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Front", color = tint, fontSize = 13.sp)
            }
            IconButton(
                onClick = { onHeading((heading + 30f).coerceAtMost(180f)) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.RotateRight, "Rotate 30 degrees right", tint = white)
            }
        }
        Spacer(Modifier.height(9.dp))
        Surface(
            onClick = onLiving,
            shape = RoundedCornerShape(14.dp),
            color = if (living) tint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .semantics { contentDescription =
                    if (living) "Living sounds on" else "Living sounds off" }
        ) {
            Row(
                Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GraphicEq, null,
                    tint = if (living) tint else secondary,
                    modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(12.dp))
                Text(if (living) "Living" else "Steady",
                    color = white, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if (living) "On" else "Off",
                    color = if (living) tint else secondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Layers", color = white, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd, enabled = sources.size < 12,
                modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Add, "Add a sound", tint = tint)
            }
        }
        Spacer(Modifier.height(9.dp))
        if (sources.isEmpty()) {
            Text("No sounds added", color = secondary, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 18.dp))
        }
        // Preserve row order when the active sound changes: no cards jumping under fingers.
        sources.forEach { sound ->
""",
    "expanded professional mixer",
)

one(
    """                    TextButton(onClick = { onSolo(sound.id) },
                        modifier = Modifier.width(53.dp).heightIn(min = 48.dp)
                            .semantics { contentDescription =
                                if (solo) "Disable solo for " + sound.kind.title
                                else "Solo " + sound.kind.title },
                        contentPadding = PaddingValues(0.dp)) {
                        Text("Solo", color = if (solo) tint else secondary,
                            fontSize = 12.sp, fontWeight = if (solo) FontWeight.Bold else FontWeight.Normal)
                    }
""",
    """                    Surface(
                        onClick = { onSolo(sound.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (solo) tint.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.width(55.dp).height(48.dp)
                            .semantics { contentDescription =
                                if (solo) "Disable solo for " + sound.kind.title
                                else "Solo " + sound.kind.title }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Solo", color = if (solo) tint else secondary,
                                fontSize = 12.sp,
                                fontWeight = if (solo) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
""",
    "active solo visual state",
)

# Large, accessible targets for individual commands.
s=s.replace('modifier = Modifier.size(46.dp)) {\n                        Icon(if (sound.muted)', 'modifier = Modifier.size(48.dp)) {\n                        Icon(if (sound.muted)', 1)
s=s.replace('modifier = Modifier.size(46.dp)) {\n                        Icon(Icons.Default.DeleteOutline', 'modifier = Modifier.size(48.dp)) {\n                        Icon(Icons.Default.DeleteOutline', 1)

# Compose Material icons are ImageVectors, retaining crisp shapes at every density.
glyph = """private fun soundGlyph(kind: SoundKind): ImageVector {
    val name = kind.title.lowercase()
    return when {
        "thunder" in name -> Icons.Default.FlashOn
        "rain" in name || "drizzle" in name -> Icons.Default.WaterDrop
        "ocean" in name || "stream" in name || "water" in name || "shore" in name -> Icons.Default.Waves
        "wind" in name -> Icons.Default.Air
        "fire" in name -> Icons.Default.LocalFireDepartment
        "chime" in name -> Icons.Default.MusicNote
        "bird" in name || "leaf" in name || "leaves" in name || "frog" in name -> Icons.Default.Park
        "night" in name || "owl" in name -> Icons.Default.NightsStay
        "traffic" in name || "car" in name -> Icons.Default.DirectionsCar
        "sleet" in name || "snow" in name -> Icons.Default.AcUnit
        "cloud" in name -> Icons.Default.Cloud
        else -> Icons.Default.GraphicEq
    }
}

"""
one("@Composable\nprivate fun ExpandedSoundMixer(", glyph + "@Composable\nprivate fun ExpandedSoundMixer(", "vector glyph function")
one("                        Text(sound.kind.emoji, fontSize = 23.sp)\n",
    "                        Icon(soundGlyph(sound.kind), null, tint = tint, modifier = Modifier.size(24.dp))\n",
    "mixer emoji")
one("                        Text(kind.emoji, fontSize = 23.sp)\n",
    "                        Icon(soundGlyph(kind), null, tint = glow, modifier = Modifier.size(26.dp))\n",
    "add sheet emoji")
one("                Text(sound.kind.emoji, fontSize = 23.sp)\n",
    "                Icon(soundGlyph(sound.kind), null,\n"
    "                    tint = if (sound.muted) secondary else white,\n"
    "                    modifier = Modifier.size(if (expanded) 29.dp else 26.dp))\n",
    "spatial source emoji")

# Listener bearing is a vector path rather than a platform font triangle.
one(
    """            Text("▲", color = tint, fontSize = 19.sp, modifier = Modifier.rotate(heading))
""",
    """            Canvas(Modifier.size(23.dp).rotate(heading)) {
                val triangle = Path().apply {
                    moveTo(size.width * .5f, size.height * .13f)
                    lineTo(size.width * .84f, size.height * .85f)
                    lineTo(size.width * .5f, size.height * .70f)
                    lineTo(size.width * .16f, size.height * .85f)
                    close()
                }
                drawPath(triangle, tint)
            }
""",
    "listener vector bearing",
)

# Avoid unused composables and misleading non-interactive sound-level cards.
one("    // A chunky, touch-friendly layer mixer — not a second device-volume control.\n",
    "    // One gain slider per sound, and no duplicate master-volume slider.\n",
    "one volume control")
assert "private fun SelectedSound" not in s
assert "private fun GlassPanel" not in s
assert "directionOpen" not in s
assert ".emoji" not in s, "All sound icons should be vectors"
p.write_text(s)

g = gradle.read_text()
assert 'versionCode = 8' in g and 'versionName = "0.6.0"' in g
g=g.replace("versionCode = 8", "versionCode = 9")
g=g.replace('versionName = "0.6.0"', 'versionName = "0.7.0"')
gradle.write_text(g)
print("ECHOSPACE_ABR_APPLIED",len(s),"android minSdk26", "v0.7.0")
