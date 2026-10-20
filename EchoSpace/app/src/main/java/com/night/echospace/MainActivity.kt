@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.echospace

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Train
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.echospace.audio.SpatialAudioService
import com.night.echospace.model.SceneStore
import com.night.echospace.model.Scenes
import com.night.echospace.model.SoundKind
import com.night.echospace.model.SoundScene
import com.night.echospace.model.SoundSource
import com.night.echospace.model.SoundPlacement
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.roundToInt
import kotlin.math.sin

/** ModalBottomSheet owns a Dialog window, so hiding only Activity system bars
 * leaves status/navigation bars visible on the spatial sound editor.
 * Keep both dialog windows immersive with the same swipe-to-reveal policy.
 */
@Composable
private fun ImmersiveModalBars() {
    val view = LocalView.current
    SideEffect {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            // The sheet lives in its own Window. Extend its Canvas to the
            // immersive edges rather than reserving black system-bar strips.
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            val controller = WindowCompat.getInsetsController(
                dialogWindow, dialogWindow.decorView
            )
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private val ink = Color(0xFF050911)
private val white = Color(0xFFF3F7F7)
private val secondary = Color(0xFFA6B8C1)
private val hairline = Color.White.copy(alpha = 0.12f)
private val frosted = Color(0xFF121B27).copy(alpha = 0.96f)

private fun sceneArtwork(title: String): Int = when (title) {
    "Deep Forest", "Wild Falls" -> R.drawable.scene_forest
    "Night Shore", "Moonlit Marsh" -> R.drawable.scene_shore
    "Fireside" -> R.drawable.scene_fire
    "Dream Drift", "My Space" -> R.drawable.scene_dream
    else -> R.drawable.scene_rain
}

private data class Atmosphere(val glow: Color, val light: Color, val deep: Color)
private fun atmosphere(title: String): Atmosphere = when (title) {
    "Deep Forest", "Wild Falls" -> Atmosphere(Color(0xFF5FD6AC), Color(0xFF2F8D8C), Color(0xFF0C2729))
    "Night Shore", "Moonlit Marsh" -> Atmosphere(Color(0xFF73CBED), Color(0xFF4165B9), Color(0xFF102547))
    "Fireside" -> Atmosphere(Color(0xFFFFBD7D), Color(0xFFC76B61), Color(0xFF35202E))
    "Dream Drift" -> Atmosphere(Color(0xFFC4B1FF), Color(0xFF8C74D8), Color(0xFF28264D))
    "My Space" -> Atmosphere(Color(0xFFB8C4FC), Color(0xFF7889CA), Color(0xFF232444))
    else -> Atmosphere(Color(0xFF87DECE), Color(0xFF4D87B4), Color(0xFF192C4A))
}

class MainActivity : ComponentActivity() {
    private var service by mutableStateOf<SpatialAudioService?>(null)
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? SpatialAudioService.LocalBinder)?.service()
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 9, 17)
        window.navigationBarColor = android.graphics.Color.rgb(5, 9, 17)
        applyImmersiveBars()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF97E8D1), onPrimary = ink,
                background = ink, surface = Color(0xFF15232F),
                onSurface = white, onBackground = white
            )) {
                EchoSpaceScreen(service) { action ->
                    if (action == SpatialAudioService.ACTION_PLAY && Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 51)
                    val command = Intent(this, SpatialAudioService::class.java).setAction(action)
                    if (action == SpatialAudioService.ACTION_PLAY) startForegroundService(command)
                    else startService(command)
                }
            }
        }
    }
    private fun applyImmersiveBars() {
        // Hide status/navigation bars: swipe temporarily reveals native system UI.
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveBars()
    }
    override fun onStart() {
        super.onStart()
        bound = bindService(Intent(this, SpatialAudioService::class.java), connection, Context.BIND_AUTO_CREATE)
        // Tell the user when hardware output failed while the app was backgrounded.
        val diagnostics=getSharedPreferences("echospace_audio",MODE_PRIVATE)
        diagnostics.getString("last_error",null)?.let { message ->
            diagnostics.edit().remove("last_error").apply()
            android.widget.Toast.makeText(this,"Playback interrupted: "+message,
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun onStop() {
        if (bound) { unbindService(connection); bound = false }
        service = null
        super.onStop()
    }
}

@Composable
private fun EchoSpaceScreen(service: SpatialAudioService?, dispatch: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { SceneStore(context.applicationContext) }
    val restored = remember { store.restore() }
    var title by remember { mutableStateOf(restored?.first ?: Scenes.rainChamber.title) }
    var sources by remember { mutableStateOf(restored?.second ?: Scenes.rainChamber.sources) }
    var selectedId by remember { mutableIntStateOf(sources.firstOrNull()?.id ?: -1) }
    var savedMix by remember { mutableStateOf(store.restoreSavedMix()) }
    // System volume keys control the final loudness; only per-layer mixing lives in the UI.
    val volume = 1f
    var heading by remember { mutableFloatStateOf(store.heading()) }
    var living by remember { mutableStateOf(store.living()) }
    var isPlaying by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableLongStateOf(0L) }
    var timerOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var mixerOpen by remember { mutableStateOf(false) }
    // Solo is a listening preview; only the unmuted original sources are persisted.
    var soloId by remember { mutableIntStateOf(-1) }
    val sceneListState = rememberLazyListState()
    val audibleSources = if (soloId < 0) sources else sources.map {
        it.copy(muted = it.id != soloId)
    }
    val palette = atmosphere(title)
    val environments = remember(savedMix) {
        if (savedMix == null) Scenes.presets else Scenes.presets + SoundScene(
            "My Space", "✦", "Your saved environment", savedMix!!
        )
    }
    val glow by animateColorAsState(palette.glow, animationSpec = tween(850), label = "scene glow")
    val light by animateColorAsState(palette.light, animationSpec = tween(850), label = "scene light")
    val deep by animateColorAsState(palette.deep, animationSpec = tween(850), label = "scene background")
    val motion = rememberInfiniteTransition(label = "ambient movement")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(18000), RepeatMode.Reverse), label = "aurora drift")
    val breathe by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(3800), RepeatMode.Reverse), label = "listener breath")

    LaunchedEffect(title, sources, heading, living, service, soloId) {
        service?.updateAudio(title, audibleSources, volume, heading, living)
        store.setLiving(living)
        delay(350)
        store.save(title, sources, volume, heading)
    }
    LaunchedEffect(service) {
        while (true) {
            isPlaying = service?.isPlaying() == true
            timerSeconds = service?.remainingSeconds() ?: 0L
            delay(350)
        }
    }
    LaunchedEffect(title, environments.size) {
        val index = environments.indexOfFirst { it.title == title }
        if (index >= 0) sceneListState.animateScrollToItem(index)
    }
    fun editSource(id: Int, change: (SoundSource) -> SoundSource) {
        sources = sources.map { if (it.id == id) change(it) else it }
    }
    fun moveSource(id: Int, dx: Float, dy: Float) {
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
        title = scene.title
        sources = scene.sources.map { it.copy() }
        selectedId = sources.firstOrNull()?.id ?: -1
        heading = 0f
    }

    Scaffold(containerColor = ink, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            PlaybackDock(isPlaying = isPlaying, enabled = service != null,
                title = title, glow = glow,
                onPlay = {
                    service?.updateAudio(title, audibleSources, volume, heading, living)
                    dispatch(if (isPlaying) SpatialAudioService.ACTION_PAUSE else SpatialAudioService.ACTION_PLAY)
                    isPlaying = !isPlaying
                })
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().background(ink).padding(pad).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 17.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BrandMark(glow)
                Spacer(Modifier.width(10.dp))
                Text("EchoSpace", color = white, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp,
                    modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { timerOpen = true },
                        modifier = Modifier.size(46.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.055f))) {
                        Icon(painterResource(R.drawable.es_timer), "Sleep timer", tint = if (timerSeconds > 0) glow else secondary)
                    }
                    DropdownMenu(expanded = timerOpen, onDismissRequest = { timerOpen = false },
                        containerColor = Color(0xFF1A2832)) {
                        listOf(0, 15, 30, 60).forEach { minutes ->
                            DropdownMenuItem(text = { Text(if (minutes == 0) "Timer off" else "$minutes minutes") },
                                onClick = { service?.setSleepTimer(minutes); timerOpen = false })
                        }
                    }
                }
            }

            // Full-bleed landscape artwork is bundled in the APK for offline immersion.
            Box(Modifier.fillMaxWidth().height(413.dp)
                .clip(RoundedCornerShape(30.dp))) {
                Image(painter = painterResource(sceneArtwork(title)), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to Color(0xFF04080F).copy(alpha = .52f),
                    .30f to Color(0xFF071320).copy(alpha = .23f),
                    .76f to Color(0xFF07111D).copy(alpha = .65f),
                    1f to ink.copy(alpha = .95f)
                )))
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.20f + drift * 0.03f), Color.Transparent),
                        Offset(size.width * .69f, size.height * .52f), size.width * 0.9f),
                        center = Offset(size.width * .69f, size.height * .52f),
                        radius = size.width * .9f)
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 17.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = white, fontSize = 31.sp,
                                letterSpacing = (-1.1).sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(Scenes.presets.find { it.title == title }?.caption ?: "Your soundscape",
                                color = Color(0xFFE2E9EC).copy(alpha = .88f), fontSize = 13.sp)
                        }
                        IconButton(onClick = { addOpen = true }, enabled = sources.size < 12,
                            modifier = Modifier.size(47.dp).clip(CircleShape)
                                .background(Color(0xFF101C29).copy(alpha = .74f))
                                .border(1.dp, Color.White.copy(alpha = .28f), CircleShape)) {
                            Icon(painterResource(R.drawable.es_add), "Add a sound", tint = white)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SoundMap(sources, selectedId, heading, glow, isPlaying, breathe,
                        onSelect = { selectedId = it },
                        onMove = { id, dx, dy -> moveSource(id, dx, dy) })
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
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
                                Icon(painterResource(R.drawable.es_mixer), null, tint = glow, modifier = Modifier.size(21.dp))
                                Spacer(Modifier.width(9.dp))
                                Text("Mixer", color = white, fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.16f),
                    RoundedCornerShape(30.dp)))
            }
            Spacer(Modifier.height(20.dp))
            Text("Explore", color = white, fontSize = 19.sp,
                letterSpacing = (-0.35).sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            LazyRow(state = sceneListState, modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                itemsIndexed(environments, key = { _, scene -> scene.title }) { _, scene ->
                    SceneTile(scene, chosen = scene.title == title, onClick = { setWorld(scene) })
                }
            }
            Spacer(Modifier.height(17.dp))
            service?.error()?.let { Text("Playback error: $it", color = Color(0xFFFFA8A7), fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(17.dp))
        }
    }

    if (mixerOpen) {
        ModalBottomSheet(
            onDismissRequest = { mixerOpen = false; soloId = -1 },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0D1722),
            contentColor = white,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF718998)) }
        ) {
            ImmersiveModalBars()
            ExpandedSoundMixer(
                sources = sources,
                selectedId = selectedId,
                soloId = soloId,
                heading = heading,
                living = living,
                tint = glow,
                playing = isPlaying,
                breathe = breathe,
                onSelect = { selectedId = it },
                onMove = { id, dx, dy -> moveSource(id, dx, dy) },
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
        ModalBottomSheet(onDismissRequest = { addOpen = false }, containerColor = Color(0xFF111D29),
            contentColor = white, dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF667D8C)) }) {
            ImmersiveModalBars()
            Row(Modifier.fillMaxWidth().padding(start = 23.dp, end = 13.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Add a sound", color = white, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { addOpen = false }) { Icon(painterResource(R.drawable.es_close), "Close", tint = secondary) }
            }
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                SoundKind.entries.forEach { kind ->
                    Row(Modifier.fillMaxWidth().clickable {
                        val id = (sources.maxOfOrNull { it.id } ?: 0) + 1
                        val (x, y) = SoundPlacement.next(sources)
                        sources = sources + SoundSource(id, kind, x, y)
                        selectedId = id
                        addOpen = false
                    }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                            .background(glow.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(soundGlyph(kind)), null, tint = glow, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(kind.title, fontSize = 15.sp, color = white, fontWeight = FontWeight.Medium)
                            Text(kind.subtitle, fontSize = 12.sp, color = secondary)
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

private fun soundGlyph(kind: SoundKind): Int = when (kind) {
        SoundKind.BIRDS -> R.drawable.es_birds
        SoundKind.BROWN_NOISE -> R.drawable.es_brown_noise
        SoundKind.CAFE -> R.drawable.es_cafe
        SoundKind.CHIMES -> R.drawable.es_chimes
        SoundKind.DRIZZLE -> R.drawable.es_drizzle
        SoundKind.FAN -> R.drawable.es_fan
        SoundKind.FIRE -> R.drawable.es_fire
        SoundKind.FROGS -> R.drawable.es_frogs
        SoundKind.LEAVES -> R.drawable.es_leaves
        SoundKind.NIGHT -> R.drawable.es_night
        SoundKind.OCEAN -> R.drawable.es_ocean
        SoundKind.OWL -> R.drawable.es_owl
        SoundKind.PINK_NOISE -> R.drawable.es_pink_noise
        SoundKind.RAIN -> R.drawable.es_rain
        SoundKind.SLEET -> R.drawable.es_sleet
        SoundKind.STREAM -> R.drawable.es_stream
        SoundKind.THUNDER -> R.drawable.es_thunder
        SoundKind.TRAFFIC -> R.drawable.es_traffic
        SoundKind.TRAIN -> R.drawable.es_train
        SoundKind.WATERFALL -> R.drawable.es_waterfall
        SoundKind.WHITE_NOISE -> R.drawable.es_white_noise
        SoundKind.WIND -> R.drawable.es_wind
    }

@Composable
private fun ExpandedSoundMixer(
    sources: List<SoundSource>,
    selectedId: Int,
    soloId: Int,
    heading: Float,
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
    onMute: (Int) -> Unit,
    onSolo: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.96f)
            .padding(horizontal = 14.dp)
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sound mixer", color = white, fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
                Text(sources.size.toString() + if (sources.size == 1) " layer" else " layers",
                    color = secondary, fontSize = 13.sp)
            }
            IconButton(onClick = onSave, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.es_save), "Save current soundscape",
                    tint = tint, modifier = Modifier.size(25.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.es_close), "Close mixer", tint = white)
            }
        }
        Spacer(Modifier.height(9.dp))
        // Header stays visible. Only mixer content scrolls.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SoundMap(
            sounds = sources, selectedId = selectedId, heading = heading,
            tint = tint, playing = playing, breathe = breathe,
            onSelect = onSelect, onMove = onMove, expanded = true
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
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Facing " + heading.roundToInt() + "°",
                color = secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onHeading((heading - 30f).coerceAtLeast(-180f)) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(painterResource(R.drawable.es_rotate_left), "Rotate 30 degrees left", tint = white)
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
                Icon(painterResource(R.drawable.es_rotate_right), "Rotate 30 degrees right", tint = white)
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
                Icon(painterResource(R.drawable.es_waveform), null,
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
                Icon(painterResource(R.drawable.es_add), "Add a sound", tint = tint)
            }
        }
        Spacer(Modifier.height(9.dp))
        if (sources.isEmpty()) {
            Text("No sounds added", color = secondary, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 18.dp))
        }
        // Preserve row order when the active sound changes: no cards jumping under fingers.
        sources.forEach { sound ->
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
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                        .background(tint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(soundGlyph(sound.kind)), null, tint = tint, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f).clickable { onSelect(sound.id) }) {
                        Text(sound.kind.title, color = white, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(if (sound.muted) "Muted" else
                            (sound.gain * 100).roundToInt().toString() + "%",
                            color = if (focused) tint else secondary, fontSize = 12.sp)
                    }
                    Surface(
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
                    IconButton(onClick = { onMute(sound.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(if (sound.muted) painterResource(R.drawable.es_mute) else painterResource(R.drawable.es_speaker),
                            if (sound.muted) "Unmute " + sound.kind.title else "Mute " + sound.kind.title,
                            tint = if (sound.muted) secondary else tint,
                            modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { onDelete(sound.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.es_remove), "Remove " + sound.kind.title,
                            tint = Color(0xFFE4A7A9), modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                EchoLayerGain(
                    value = sound.gain,
                    onValueChange = { level -> onGain(sound.id, level) },
                    tint = tint,
                    label = sound.kind.title + " level",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun EchoLayerGain(
    value: Float,
    onValueChange: (Float) -> Unit,
    tint: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    val currentUpdate by rememberUpdatedState(onValueChange)
    val amount = value.coerceIn(0f, 1f)
    Canvas(
        modifier.height(70.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(amount, 0f..1f)
                setProgress {
                    currentUpdate(it.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    // Whole 70dp control is interactive; parent scroll won't steal drags.
                    val minX = 24.dp.toPx()
                    val range = (size.width.toFloat() - 2f * minX).coerceAtLeast(1f)
                    fun updateAt(x: Float) {
                        currentUpdate(((x - minX) / range).coerceIn(0f, 1f))
                    }
                    updateAt(first.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val point = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!point.pressed) break
                        updateAt(point.position.x)
                        point.consume()
                    }
                }
            }
    ) {
        val inset = 24.dp.toPx()
        val left = inset
        val width = (size.width - 2f * inset).coerceAtLeast(1f)
        val midY = size.height * 0.43f
        val height = 11.dp.toPx()
        val filled = width * amount
        drawRoundRect(
            color = Color(0xFF243947),
            topLeft = Offset(left, midY - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2f)
        )
        if (filled > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(tint.copy(alpha = 0.65f), tint)
                ),
                topLeft = Offset(left, midY - height / 2f),
                size = Size(filled, height),
                cornerRadius = CornerRadius(height / 2f)
            )
        }
        // Fine engraved dB-style guide ticks, rather than a system control.
        for (index in 0..20) {
            val position = left + width * index / 20f
            val large = index % 5 == 0
            drawRoundRect(
                color = if (index / 20f <= amount)
                    tint.copy(alpha = 0.58f) else Color(0xFF667C8A),
                topLeft = Offset(position - 0.75.dp.toPx(), midY + 15.dp.toPx()),
                size = Size(1.5.dp.toPx(), if (large) 9.dp.toPx() else 5.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        val thumb = Offset(left + filled, midY)
        drawCircle(tint.copy(alpha = 0.14f), 24.dp.toPx(), thumb)
        drawRoundRect(
            color = Color(0xFFF5FAFC),
            topLeft = Offset(thumb.x - 8.dp.toPx(), thumb.y - 17.dp.toPx()),
            size = Size(16.dp.toPx(), 34.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(thumb.x - 1.5.dp.toPx(), thumb.y - 7.dp.toPx()),
            size = Size(3.dp.toPx(), 14.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx())
        )
    }
}

@Composable
private fun BrandMark(tint: Color) {
    Canvas(Modifier.size(27.dp)) {
        drawCircle(tint.copy(alpha = 0.2f), radius = size.minDimension * 0.48f)
        drawCircle(tint.copy(alpha = 0.75f), radius = size.minDimension * 0.34f, style = Stroke(1.5.dp.toPx()))
        drawCircle(tint, radius = size.minDimension * 0.12f)
    }
}

@Composable
private fun SceneTile(scene: SoundScene, chosen: Boolean, onClick: () -> Unit) {
    val tint = atmosphere(scene.title).glow
    val shape = RoundedCornerShape(21.dp)
    Box(Modifier.width(if (chosen) 154.dp else 139.dp).height(119.dp)
        .clip(shape).clickable(onClick = onClick)
        .border(if (chosen) 2.dp else 1.dp,
            if (chosen) tint.copy(alpha = 0.9f) else hairline, shape)) {
        Image(painter = painterResource(sceneArtwork(scene.title)), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color(0xFF08101A).copy(alpha = .08f),
                Color(0xFF04080F).copy(alpha = .27f), Color(0xFF04080F).copy(alpha = .90f)))))
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            if (chosen) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
            } else Spacer(Modifier.height(8.dp))
            Text(scene.title, color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
private fun PlaybackDock(isPlaying: Boolean, enabled: Boolean, title: String,
                         glow: Color, onPlay: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
        ink.copy(alpha = 0.8f), ink, ink))).navigationBarsPadding()
        .padding(start = 18.dp, end = 18.dp, top = 7.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
            .background(Color(0xFF182632))
            .border(1.dp, hairline, RoundedCornerShape(25.dp))
            .padding(start = 10.dp, end = 15.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlay, enabled = enabled,
                modifier = Modifier.size(62.dp).clip(CircleShape).background(glow)) {
                Icon(if (isPlaying) painterResource(R.drawable.es_pause) else painterResource(R.drawable.es_play),
                    if (isPlaying) "Pause" else "Play", tint = ink, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (isPlaying) "Listening" else "Tap to listen",
                    color = secondary, fontSize = 11.sp)
            }
            Icon(painterResource(R.drawable.es_waveform), null, tint = glow.copy(alpha = .9f),
                modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun SoundMap(sounds: List<SoundSource>, selectedId: Int, heading: Float,
                     tint: Color, playing: Boolean, breathe: Float,
                     onSelect: (Int) -> Unit, onMove: (Int, Float, Float) -> Unit,
    expanded: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(if (expanded) 425.dp else 236.dp)
        .clip(RoundedCornerShape(28.dp))
        .background(Color(0xFF08111D).copy(alpha = 0.43f))
        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))) {
        val diameter = with(LocalDensity.current) { maxWidth.toPx() }
        val fieldHeight = with(LocalDensity.current) { maxHeight.toPx() }
        val midX = diameter / 2f
        val midY = fieldHeight / 2f
        val radius = minOf(diameter * (if (expanded) 0.44f else 0.365f),
            fieldHeight * (if (expanded) 0.43f else 0.38f))
        Canvas(Modifier.fillMaxSize()) {
            val centerPoint = Offset(size.width / 2f, size.height / 2f)
            // Quiet, luminous range guides instead of a heavy map grid.
            listOf(0.30f, 0.66f, 1f).forEachIndexed { index, factor ->
                drawCircle(Color.White.copy(alpha = if (index == 2) 0.26f else 0.115f),
                    radius * factor, center = centerPoint,
                    style = Stroke(if (index == 2) 1.4.dp.toPx() else 1.dp.toPx()))
            }
            drawLine(Color.White.copy(alpha = 0.095f), Offset(centerPoint.x, centerPoint.y - radius),
                Offset(centerPoint.x, centerPoint.y + radius), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.07f), Offset(centerPoint.x - radius, centerPoint.y),
                Offset(centerPoint.x + radius, centerPoint.y), 1.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = if (playing) 0.19f + breathe * 0.1f else 0.15f),
                Color.Transparent), center = centerPoint, radius = 52.dp.toPx()),
                radius = 52.dp.toPx(), center = centerPoint)
        }
        Text("FRONT", Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            fontSize = 9.sp, letterSpacing = 1.6.sp, color = Color.White.copy(alpha = 0.65f))
        Text("BACK", Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
            fontSize = 9.sp, letterSpacing = 1.6.sp, color = Color.White.copy(alpha = 0.57f))
        Box(Modifier.offset {
                IntOffset((midX - 22.dp.toPx()).roundToInt(),
                    (midY - 22.dp.toPx()).roundToInt())
            }.size(44.dp).clip(CircleShape)
            .background(Color(0xFF183038).copy(alpha = 0.95f))
            .border(1.5.dp, tint, CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(23.dp).rotate(heading)) {
                val triangle = Path().apply {
                    moveTo(size.width * .5f, size.height * .13f)
                    lineTo(size.width * .84f, size.height * .85f)
                    lineTo(size.width * .5f, size.height * .70f)
                    lineTo(size.width * .16f, size.height * .85f)
                    close()
                }
                drawPath(triangle, tint)
            }
        }
        sounds.forEach { sound ->
            val chosen = sound.id == selectedId
            val nodeSize = if (expanded) 70.dp else 54.dp
            Box(Modifier.offset {
                IntOffset((midX + sound.x * radius - nodeSize.toPx() / 2f).roundToInt(),
                    (midY + sound.y * radius - nodeSize.toPx() / 2f).roundToInt())
            }.size(nodeSize).clip(CircleShape)
                .background(if (sound.muted) Color(0xFF26333D) else
                    if (chosen) tint.copy(alpha = 0.30f) else Color(0xFF243748).copy(alpha = 0.94f))
                .border(if (chosen) 2.dp else 1.dp,
                    if (chosen) tint else Color.White.copy(alpha = 0.33f), CircleShape)
                .pointerInput(sound.id, radius) {
                    detectDragGestures(onDragStart = { onSelect(sound.id) }) { event, change ->
                        event.consume()
                        onMove(sound.id, change.x / radius, change.y / radius)
                    }
                }
                .semantics { contentDescription = "${sound.kind.title} sound position. Drag to move." }
                .clickable { onSelect(sound.id) }, contentAlignment = Alignment.Center) {
                Icon(painterResource(soundGlyph(sound.kind)), null,
                    tint = if (sound.muted) secondary else white,
                    modifier = Modifier.size(if (expanded) 29.dp else 26.dp))
            }
        }
    }
}
