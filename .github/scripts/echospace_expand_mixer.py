#!/usr/bin/env python3
"""Apply EchoSpace's expanded sound-mixer UI after extracting its source archive.

The repository tracks the small Android project as EchoSpace/source.tar.xz.
This readable patch is applied identically to both emulator QA and ARM64
release builds, keeping the UI source of each produced APK reproducible.
"""
from pathlib import Path

root = Path("EchoSpace")
main = root / "app/src/main/java/com/nexa/echospace/MainActivity.kt"
gradle = root / "app/build.gradle.kts"
source = main.read_text()

def replace_one(before, after):
    global source
    count = source.count(before)
    if count != 1:
        raise RuntimeError("Expected exactly one mixer anchor, found %s: %s" % (count, before[:120]))
    source = source.replace(before, after, 1)

replace_one(
    "    var directionOpen by remember { mutableStateOf(false) }\n",
    "    var directionOpen by remember { mutableStateOf(false) }\n"
    "    var mixerOpen by remember { mutableStateOf(false) }\n"
    "    // Solo is a listening preview; only the unmuted original sources are persisted.\n"
    "    var soloId by remember { mutableIntStateOf(-1) }\n",
)
replace_one(
    "    val selected = sources.firstOrNull { it.id == selectedId }\n",
    "    val selected = sources.firstOrNull { it.id == selectedId }\n"
    "    val audibleSources = if (soloId < 0) sources else sources.map {\n"
    "        if (it.id == soloId) it else it.copy(muted = true)\n"
    "    }\n",
)
replace_one(
    "    LaunchedEffect(title, sources, heading, living, service) {\n",
    "    LaunchedEffect(title, sources, heading, living, service, soloId) {\n",
)
old_update = "service?.updateAudio(title, sources, volume, heading, living)"
assert source.count(old_update) == 2, "Unexpected audio-update callsites"
source = source.replace(old_update, "service?.updateAudio(title, audibleSources, volume, heading, living)")

replace_one(
    "    fun setWorld(scene: SoundScene) {\n",
    """    fun moveSource(id: Int, dx: Float, dy: Float) {
        editSource(id) { sound ->
            val x = (sound.x + dx).coerceIn(-1f, 1f)
            val y = (sound.y + dy).coerceIn(-1f, 1f)
            val len = hypot(x, y)
            if (len > 0.98f) sound.copy(x = x * 0.98f / len, y = y * 0.98f / len)
            else sound.copy(x = x, y = y)
        }
    }
    fun setWorld(scene: SoundScene) {
        soloId = -1
""",
)
replace_one(
    """                        onMove = { id, dx, dy ->
                            editSource(id) { sound ->
                                val x = (sound.x + dx).coerceIn(-1f, 1f)
                                val y = (sound.y + dy).coerceIn(-1f, 1f)
                                val len = hypot(x, y)
                                if (len > 0.98f) sound.copy(x = x * 0.98f / len, y = y * 0.98f / len)
                                else sound.copy(x = x, y = y)
                            }
                        })
""",
    """                        onMove = { id, dx, dy -> moveSource(id, dx, dy) })
""",
)
replace_one(
    """                    Row(Modifier.fillMaxWidth().height(30.dp),
""",
    """                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp),
""",
)
replace_one(
    """                        TextButton(onClick = { directionOpen = !directionOpen },
""",
    """                        TextButton(onClick = { mixerOpen = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.semantics { contentDescription = "Open full sound mixer" }) {
                            Text("Expand", color = glow, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { directionOpen = !directionOpen },
""",
)
replace_one(
    "    if (addOpen) {\n",
    """    if (mixerOpen) {
        ModalBottomSheet(
            onDismissRequest = { mixerOpen = false; soloId = -1 },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0D1722),
            contentColor = white,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF718998)) }
        ) {
            ExpandedSoundMixer(
                sources = sources,
                selectedId = selectedId,
                soloId = soloId,
                heading = heading,
                tint = glow,
                playing = isPlaying,
                breathe = breathe,
                onSelect = { selectedId = it },
                onMove = { id, dx, dy -> moveSource(id, dx, dy) },
                onGain = { id, gain -> editSource(id) { it.copy(gain = gain) } },
                onMute = { id -> editSource(id) { it.copy(muted = !it.muted) } },
                onSolo = { id -> soloId = if (soloId == id) -1 else id },
                onDelete = { id ->
                    sources = sources.filterNot { it.id == id }
                    if (selectedId == id) selectedId = sources.firstOrNull()?.id ?: -1
                    if (soloId == id) soloId = -1
                },
                onClose = { mixerOpen = false; soloId = -1 }
            )
        }
    }

    if (addOpen) {
""",
)

# Expand the spatial pad only when the fullscreen mixer is open.
replace_one(
    """onSelect: (Int) -> Unit, onMove: (Int, Float, Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(236.dp)
""",
    """onSelect: (Int) -> Unit, onMove: (Int, Float, Float) -> Unit,
    expanded: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(if (expanded) 332.dp else 236.dp)
""",
)
replace_one(
    """        Box(Modifier.offset {
            IntOffset((midX + sound.x * radius - 27.dp.toPx()).roundToInt(),
                (midY + sound.y * radius - 27.dp.toPx()).roundToInt())
        }.size(54.dp).clip(CircleShape)
""",
    """        val nodeSize = if (expanded) 64.dp else 54.dp
        Box(Modifier.offset {
            IntOffset((midX + sound.x * radius - nodeSize.toPx() / 2f).roundToInt(),
                (midY + sound.y * radius - nodeSize.toPx() / 2f).roundToInt())
        }.size(nodeSize).clip(CircleShape)
""",
)
replace_one(
    """private fun ThickGainControl(value: Float, tint: Color, onChange: (Float) -> Unit,
    modifier: Modifier = Modifier) {
    // A chunky, touch-friendly layer mixer — not a second device-volume control.
    Canvas(modifier.height(48.dp)
""",
    """private fun ThickGainControl(value: Float, tint: Color, onChange: (Float) -> Unit,
    modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 48.dp,
    label: String = "Layer level") {
    // Keep gesture handlers on the current sound, even when the selected source changes.
    val latestOnChange by rememberUpdatedState(onChange)
    Canvas(modifier.height(height)
""",
)
replace_one(
    """            contentDescription = "Layer level"
            progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            setProgress { onChange(it.coerceIn(0f, 1f)); true }
""",
    """            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            setProgress { latestOnChange(it.coerceIn(0f, 1f)); true }
""",
)
replace_one(
    """        .pointerInput(Unit) {
            detectTapGestures { pos -> onChange((pos.x / size.width).coerceIn(0f, 1f)) }
        }
        .pointerInput(Unit) {
            detectDragGestures { change, _ ->
                change.consume()
                onChange((change.position.x / size.width).coerceIn(0f, 1f))
            }
        }) {
""",
    """        .pointerInput(Unit) {
            detectTapGestures { pos -> latestOnChange((pos.x / size.width).coerceIn(0f, 1f)) }
        }
        .pointerInput(Unit) {
            detectDragGestures { change, _ ->
                change.consume()
                latestOnChange((change.position.x / size.width).coerceIn(0f, 1f))
            }
        }) {
""",
)

# The expansion is a single dedicated mixer surface, with one gain control
# per layer and its own large sound-positioning field.
mixer = """@Composable
private fun ExpandedSoundMixer(
    sources: List<SoundSource>,
    selectedId: Int,
    soloId: Int,
    heading: Float,
    tint: Color,
    playing: Boolean,
    breathe: Float,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onGain: (Int, Float) -> Unit,
    onMute: (Int) -> Unit,
    onSolo: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp)
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sound mixer", color = white, fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
                Text(sources.size.toString() + if (sources.size == 1) " layer" else " layers",
                    color = secondary, fontSize = 13.sp)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Close, "Close mixer", tint = white)
            }
        }
        Spacer(Modifier.height(16.dp))
        SoundMap(
            sounds = sources, selectedId = selectedId, heading = heading,
            tint = tint, playing = playing, breathe = breathe,
            onSelect = onSelect, onMove = onMove, expanded = true
        )
        Spacer(Modifier.height(9.dp))
        Text("Drag sounds around the listener to change their direction and distance",
            color = secondary, fontSize = 12.sp)
        Spacer(Modifier.height(23.dp))
        Text("Layers", color = white, fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        sources.sortedByDescending { it.id == selectedId }.forEach { sound ->
            val focused = sound.id == selectedId
            val solo = soloId == sound.id
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (focused) tint.copy(alpha = 0.09f)
                        else Color(0xFF16232F))
                    .border(
                        if (focused) 1.5.dp else 1.dp,
                        if (focused) tint.copy(alpha = 0.65f) else hairline,
                        RoundedCornerShape(22.dp)
                    )
                    .clickable { onSelect(sound.id) }
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                        .background(tint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center) {
                        Text(sound.kind.emoji, fontSize = 23.sp)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sound.kind.title, color = white, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(if (sound.muted) "Muted" else
                            (sound.gain * 100).roundToInt().toString() + "%",
                            color = if (focused) tint else secondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onSolo(sound.id) },
                        modifier = Modifier.width(53.dp).heightIn(min = 48.dp)
                            .semantics { contentDescription =
                                if (solo) "Disable solo for " + sound.kind.title
                                else "Solo " + sound.kind.title },
                        contentPadding = PaddingValues(0.dp)) {
                        Text("Solo", color = if (solo) tint else secondary,
                            fontSize = 12.sp, fontWeight = if (solo) FontWeight.Bold else FontWeight.Normal)
                    }
                    IconButton(onClick = { onMute(sound.id) }, modifier = Modifier.size(46.dp)) {
                        Icon(if (sound.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            if (sound.muted) "Unmute " + sound.kind.title else "Mute " + sound.kind.title,
                            tint = if (sound.muted) secondary else tint,
                            modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { onDelete(sound.id) }, modifier = Modifier.size(46.dp)) {
                        Icon(Icons.Default.DeleteOutline, "Remove " + sound.kind.title,
                            tint = Color(0xFFE4A7A9), modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                ThickGainControl(
                    sound.gain, tint, { value -> onGain(sound.id, value) },
                    modifier = Modifier.fillMaxWidth(), height = 62.dp,
                    label = sound.kind.title + " level"
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

"""
replace_one("@Composable\nprivate fun BrandMark(tint: Color) {", mixer + "@Composable\nprivate fun BrandMark(tint: Color) {")

main.write_text(source)
cfg=gradle.read_text()
assert 'versionCode = 7' in cfg and 'versionName = "0.5.0"' in cfg
gradle.write_text(cfg.replace('versionCode = 7','versionCode = 8').replace('versionName = "0.5.0"','versionName = "0.6.0"'))
print("ECHOSPACE_MIXER_PATCH_APPLIED", main.stat().st_size, gradle.stat().st_size)
