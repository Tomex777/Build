package com.night.endless

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.night.endless.engine.render.ApproachSnapshot
import com.night.endless.engine.render.BodyLabelSnapshot
import com.night.endless.engine.render.EndlessGLView
import com.night.endless.engine.render.EndlessRenderer
import com.night.endless.engine.scene.UniverseClock
import com.night.endless.engine.scene.DeepTimeHistory
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private var activeGlView: EndlessGLView? = null
    private var historyAgeGa: Double = 0.0
    private var historyDomain: String = "System"
    private var historyOpen: Boolean = false
    private var historyPlaying: Boolean = false
    private var historySpeedIndex: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        val restoredState = savedInstanceState?.readExplorationState()
        setContent {
            EndlessApp(
                initialState = restoredState,
                initialHistoryAgeGa = savedInstanceState?.getDouble("endless.history.age", 0.0) ?: 0.0,
                initialHistoryDomain = savedInstanceState?.getString("endless.history.domain") ?: "System",
                initialHistoryOpen = savedInstanceState?.getBoolean("endless.history.open") ?: false,
                initialHistoryPlaying = savedInstanceState?.getBoolean("endless.history.playing") ?: false,
                initialHistorySpeedIndex = savedInstanceState?.getInt("endless.history.speed", 1) ?: 1,
                onHistoryStateChanged = { age, domain, open, playing, speedIndex ->
                    historyAgeGa = age
                    historyDomain = domain
                    historyOpen = open
                    historyPlaying = playing
                    historySpeedIndex = speedIndex
                },
                onGlViewReady = { activeGlView = it }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        activeGlView?.onResume()
    }

    override fun onPause() {
        activeGlView?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        activeGlView?.endlessRenderer?.snapshotState()?.let(outState::writeExplorationState)
        outState.putDouble("endless.history.age", historyAgeGa)
        outState.putString("endless.history.domain", historyDomain)
        outState.putBoolean("endless.history.open", historyOpen)
        outState.putBoolean("endless.history.playing", historyPlaying)
        outState.putInt("endless.history.speed", historySpeedIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        activeGlView = null
        super.onDestroy()
    }
}

private val Bg = Color(0xFF02030A)
private val Panel = Color(0xD9080B17)
private val PanelStrong = Color(0xF20F1426)
private val Text = Color(0xFFEEF2FF)
private val Muted = Color(0xFF9BA7C7)
private val Accent = Color(0xFF73BFFF)
private val Border = Color(0x18FFFFFF)
private val AccentBg = Color(0x2173BFFF)

private data class BodyInfo(
    val name: String,
    val type: String,
    val radius: String,
    val semiMajor: String,
    val orbitalPeriod: String,
    val rotation: String,
    val description: String
)

private data class InfoSection(val title: String, val body: String)

private val sunInformation = listOf(
    InfoSection("LAYERS", "The core is where hydrogen fuses into helium. Energy moves outward through the radiative zone, then by rising and sinking plasma in the convective zone. Above the visible photosphere are the chromosphere and the much hotter corona."),
    InfoSection("ACTIVITY", "Sunspots trace concentrated magnetic fields. Magnetic restructuring can drive flares and prominences. The corona releases the solar wind, which inflates the heliosphere around the planets."),
    InfoSection("COMPOSITION", "The Sun is mostly hydrogen and helium by mass, with a small fraction of heavier elements. Its core reaches about 15.7 million K; the photosphere is about 5,500 °C."),
    InfoSection("MEASUREMENTS", "Mass: 1.989 × 10³⁰ kg. Mean radius: about 695,700 km. Surface gravity: about 274 m/s². Rotation is differential: the equator turns faster than the polar regions."),
    InfoSection("FUTURE", "Solar models project gradual brightening during the main sequence, followed in roughly five billion years by expansion into a red giant and later envelope loss, leaving a white dwarf. These are future models, not observations.")
)

private val bodyInfo = mapOf(
    "sun" to BodyInfo("Sun", "Star", "696,340 km", "—", "—", "25.4 d equator", "The star at the centre of the Solar System. Display size is exaggerated so the inner system remains readable."),
    "mercury" to BodyInfo("Mercury", "Terrestrial planet", "2,439.7 km", "0.3871 AU", "87.97 d", "58.65 d", "The smallest planet and the closest planet to the Sun."),
    "venus" to BodyInfo("Venus", "Terrestrial planet", "6,051.8 km", "0.7233 AU", "224.70 d", "243.0 d retrograde", "A hot terrestrial world hidden beneath a dense atmosphere."),
    "earth" to BodyInfo("Earth", "Terrestrial planet", "6,371 km", "1.0000 AU", "365.26 d", "23 h 56 m", "Our home world. The native renderer keeps Earth moving on the same universe clock as the rest of the system."),
    "moon" to BodyInfo("Moon", "Natural satellite", "1,737.4 km", "384,400 km from Earth", "27.32 d", "27.32 d", "Earth's natural satellite. Endless supports close lunar orbit, a cratered airless landing patch, surface walking and takeoff."),
    "mars" to BodyInfo("Mars", "Terrestrial planet", "3,389.5 km", "1.5237 AU", "686.98 d", "24 h 37 m", "The fourth planet from the Sun, marked by iron-rich reddish terrain."),
    "jupiter" to BodyInfo("Jupiter", "Gas giant", "69,911 km", "5.2029 AU", "11.86 y", "9 h 55 m", "The largest planet, a gas giant with banded clouds and enormous storms."),
    "saturn" to BodyInfo("Saturn", "Gas giant", "58,232 km", "9.5371 AU", "29.45 y", "10 h 42 m", "A gas giant surrounded by its bright, complex ring system."),
    "uranus" to BodyInfo("Uranus", "Ice giant", "25,362 km", "19.191 AU", "84.0 y", "17 h 14 m retrograde", "An ice giant rotating on its side with a faint ring system."),
    "neptune" to BodyInfo("Neptune", "Ice giant", "24,622 km", "30.07 AU", "164.8 y", "16 h 6 m", "A distant blue ice giant with some of the fastest winds in the Solar System.")
)

@Composable
private fun EndlessApp(
    initialState: EndlessRenderer.ExplorationState?,
    initialHistoryAgeGa: Double,
    initialHistoryDomain: String,
    initialHistoryOpen: Boolean,
    initialHistoryPlaying: Boolean,
    initialHistorySpeedIndex: Int,
    onHistoryStateChanged: (Double, String, Boolean, Boolean, Int) -> Unit,
    onGlViewReady: (EndlessGLView) -> Unit
) {
    var selected by remember {
        mutableStateOf(
            if (initialState?.overview == true) null
            else initialState?.selectedId ?: "earth"
        )
    }
    var infoVisible by remember {
        mutableStateOf(initialState?.marsSurfaceMode != true && initialState?.moonSurfaceMode != true)
    }
    var paused by remember { mutableStateOf(initialState?.clockState?.paused ?: false) }
    var overview by remember { mutableStateOf(initialState?.overview ?: false) }
    var orbitsOn by remember { mutableStateOf(initialState?.showOrbits ?: true) }
    var labelsOn by remember { mutableStateOf(true) }
    var speedLabel by remember { mutableStateOf("10×") }
    var dateText by remember { mutableStateOf("—") }
    var timeText by remember { mutableStateOf("—") }
    var snapshots by remember { mutableStateOf<List<BodyLabelSnapshot>>(emptyList()) }
    var approach by remember { mutableStateOf(ApproachSnapshot(null, Double.POSITIVE_INFINITY, "SPACE", false)) }
    var landedBody by remember {
        mutableStateOf(
            when {
                initialState?.moonSurfaceMode == true -> "moon"
                initialState?.marsSurfaceMode == true -> "mars"
                else -> null
            }
        )
    }
    var glView by remember { mutableStateOf<EndlessGLView?>(null) }
    var historyOpen by remember { mutableStateOf(initialHistoryOpen) }
    var historyAgeGa by remember { mutableFloatStateOf(initialHistoryAgeGa.toFloat().coerceIn(-7f, 4.6f)) }
    var historyDomain by remember { mutableStateOf(initialHistoryDomain.takeIf { it in DeepTimeHistory.domains } ?: "System") }
    var historyPlaying by remember { mutableStateOf(initialHistoryPlaying) }
    var historySpeedIndex by remember { mutableIntStateOf(initialHistorySpeedIndex.coerceIn(0, 3)) }

    LaunchedEffect(historyAgeGa, glView) {
        glView?.endlessRenderer?.setDeepTimeAgeGa(historyAgeGa.toDouble())
        onHistoryStateChanged(historyAgeGa.toDouble(), historyDomain, historyOpen, historyPlaying, historySpeedIndex)
    }
    LaunchedEffect(historyDomain, historyOpen, historyPlaying, historySpeedIndex) {
        onHistoryStateChanged(historyAgeGa.toDouble(), historyDomain, historyOpen, historyPlaying, historySpeedIndex)
    }
    LaunchedEffect(historyPlaying, historySpeedIndex, historyDomain) {
        val steps = floatArrayOf(.0006f, .0012f, .006f, .024f)
        while (historyPlaying) {
            delay(50)
            historyAgeGa = (historyAgeGa - steps[historySpeedIndex]).coerceAtLeast(-7f)
            if (historyAgeGa <= -7f) historyPlaying = false
        }
    }

    LaunchedEffect(glView) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val timeFormat = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        while (true) {
            glView?.endlessRenderer?.let { renderer ->
                val now = Date(renderer.currentTimeMillis())
                dateText = dateFormat.format(now)
                timeText = timeFormat.format(now)
                snapshots = renderer.labelSnapshots()
                approach = renderer.approachSnapshot()
                landedBody = renderer.surfaceBodyId()
                speedLabel = renderer.speedLabel()
            }
            delay(33)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = PanelStrong)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
            val density = LocalDensity.current

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    EndlessGLView(context) { id ->
                        selected = id
                        if (id != null) {
                            overview = false
                            if (landedBody == null) infoVisible = true
                        }
                    }.also { view ->
                        initialState?.let(view.endlessRenderer::restoreState)
                        glView = view
                        onGlViewReady(view)
                    }
                }
            )

            Box(
                Modifier.fillMaxWidth().height(96.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xEB02030A), Color.Transparent)))
            )

            Column(
                Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("ENDLESS", color = Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.7.sp)
                Text(
                    when (landedBody) {
                        "mars" -> "MARS SURFACE · PROCEDURAL TERRAIN"
                        "moon" -> "LUNAR SURFACE · CRATER TERRAIN"
                        else -> "INTERACTIVE 3D ORRERY"
                    },
                    color = Muted, fontSize = 9.sp, letterSpacing = 1.3.sp
                )
            }

            if (landedBody == null) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    StatusBadge("JPL HORIZONS · FALLBACK", warning = true)
                    StatusBadge("NATIVE · COLLISION ON")
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 58.dp),
                shape = CircleShape,
                color = Panel,
                border = BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ORBITAL", color = Color(0xFF7D89AA), fontSize = 8.sp, letterSpacing = .6.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(dateText, color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(7.dp))
                    Text(timeText, color = Accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            if (labelsOn) {
                snapshots.filter { it.visible && it.id != "sun" }.forEach { label ->
                    val xDp = with(density) { label.xPx.toDp() }
                    val yDp = with(density) { label.yPx.toDp() }
                    val labelX = if (label.id == "moon") xDp - 18.dp else xDp + 6.dp
                    val labelY = if (label.id == "moon") yDp + 10.dp else yDp - 14.dp
                    Box(
                        modifier = Modifier.offset(x = labelX - 9.dp, y = labelY - 7.dp)
                            .sizeIn(minWidth = 44.dp, minHeight = 36.dp)
                            .semantics { contentDescription = "Focus ${label.name}" }
                            .clickable {
                                glView?.endlessRenderer?.focus(label.id)
                                selected = label.id
                                overview = false
                                infoVisible = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xC7080B17),
                            border = BorderStroke(1.dp, Border)
                        ) {
                            Text(
                                label.name,
                                color = if (label.id == selected) Accent else Text,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (infoVisible && selected != null && landedBody == null) {
                bodyInfo[selected]?.let { info ->
                    InfoPanel(
                        info = info,
                        selectedId = selected!!,
                        detailSections = if (selected == "sun") sunInformation else emptyList(),
                        primaryActionLabel = when (selected) {
                            "moon" -> "Explore Moon"
                            "mars" -> "Explore Mars"
                            else -> null
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                        onPrimaryAction = {
                            infoVisible = false
                            glView?.endlessRenderer?.approachSelected()
                        },
                        onClose = { infoVisible = false }
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    if (selected == null) "OVERVIEW · SUN-CENTERED" else "FOCUSED · ${bodyInfo[selected]?.name?.uppercase() ?: selected!!.uppercase()}",
                    color = Color(0x99BEC6DC), fontSize = 8.sp, letterSpacing = 1.1.sp
                )
                if (overview) {
                    Text(
                        "Tap a planet or its label to fly there",
                        color = Color(0x667D89AA), fontSize = 8.sp
                    )
                } else if (landedBody != null) {
                    Text(
                        "WALKABLE ${landedBody!!.uppercase()} PATCH · COLLISION ON",
                        color = Accent,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if ((selected == "mars" || selected == "moon") && approach.altitudeKm.isFinite()) {
                    val altitude = when {
                        approach.altitudeKm >= 1000.0 -> String.format(Locale.US, "%.0f km", approach.altitudeKm)
                        approach.altitudeKm >= 10.0 -> String.format(Locale.US, "%.1f km", approach.altitudeKm)
                        else -> String.format(Locale.US, "%.2f km", approach.altitudeKm)
                    }
                    Text(
                        "${approach.stage} · ALT ${altitude}",
                        color = if (
                            approach.stage == "ATMOSPHERE" ||
                            approach.stage == "LOW ORBIT" ||
                            approach.stage == "SURFACE SKIM"
                        ) Accent else Color(0x887D89AA),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (landedBody != null || ((selected == "mars" || selected == "moon") && !overview)) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = if (landedBody != null) 18.dp else 68.dp),
                    shape = CircleShape,
                    color = PanelStrong,
                    border = BorderStroke(1.dp, if (landedBody != null) Accent.copy(alpha = .38f) else Border),
                    shadowElevation = 14.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (landedBody != null) {
                            Text(
                                landedBody!!.uppercase(),
                                color = Accent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp)
                            )
                            DividerPill()
                            ControlButton("↑") { glView?.endlessRenderer?.walkSurface(1f, 0f) }
                            ControlButton("←") { glView?.endlessRenderer?.walkSurface(0f, -1f) }
                            ControlButton("↓") { glView?.endlessRenderer?.walkSurface(-1f, 0f) }
                            ControlButton("→") { glView?.endlessRenderer?.walkSurface(0f, 1f) }
                            DividerPill()
                            ControlButton("↗  Take off", active = true) {
                                if (glView?.endlessRenderer?.takeOffSurface() == true) {
                                    landedBody = null
                                }
                            }
                        } else {
                            Text(
                                approach.stage,
                                color = Accent,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 7.dp)
                            )
                            DividerPill()

                            if (selected == "mars") {
                                when (approach.stage) {
                                    "ORBIT", "CLOSE APPROACH" -> {
                                        ControlButton("↓  Approach Mars", active = approach.stage == "CLOSE APPROACH") {
                                            infoVisible = false
                                            glView?.endlessRenderer?.approachSelected()
                                        }
                                    }
                                    "ATMOSPHERE" -> {
                                        ControlButton("↓  Descend to surface", active = true) {
                                            glView?.endlessRenderer?.descendSelected()
                                        }
                                        ControlButton("↑  Pull back") {
                                            glView?.endlessRenderer?.pullBackSelected()
                                        }
                                    }
                                    "SURFACE SKIM" -> {
                                        ControlButton("◆  Land on Mars", active = true) {
                                            if (glView?.endlessRenderer?.landOnMars() == true) {
                                                landedBody = "mars"
                                                infoVisible = false
                                            }
                                        }
                                        ControlButton("↑  Pull back") {
                                            glView?.endlessRenderer?.pullBackSelected()
                                        }
                                    }
                                    else -> {
                                        ControlButton("↑  Pull back") {
                                            glView?.endlessRenderer?.pullBackSelected()
                                        }
                                    }
                                }
                            } else if (selected == "moon") {
                                when (approach.stage) {
                                    "ORBIT" -> {
                                        ControlButton("↓  Approach Moon", active = true) {
                                            infoVisible = false
                                            glView?.endlessRenderer?.approachSelected()
                                        }
                                    }
                                    "CLOSE APPROACH" -> {
                                        ControlButton("↓  Enter low orbit", active = true) {
                                            glView?.endlessRenderer?.descendSelected()
                                        }
                                        ControlButton("↑  Pull back") {
                                            glView?.endlessRenderer?.pullBackSelected()
                                        }
                                    }
                                    "LOW ORBIT" -> {
                                        ControlButton("↓  Surface skim", active = true) {
                                            glView?.endlessRenderer?.descendSelected()
                                        }
                                        ControlButton("↑  Pull back") {
                                            glView?.endlessRenderer?.pullBackSelected()
                                        }
                                    }
                                    "SURFACE SKIM" -> {
                                        ControlButton("◆  Land on Moon", active = true) {
                                            if (glView?.endlessRenderer?.landOnMoon() == true) {
                                                landedBody = "moon"
                                                infoVisible = false
                                            }
                                        }
                                        ControlButton("↑  Pull back") {
                                            glView?.endlessRenderer?.pullBackSelected()
                                        }
                                    }
                                    else -> {
                                        ControlButton("↑  Pull back") {
                                            glView?.endlessRenderer?.pullBackSelected()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (landedBody == null) {
                if (historyOpen) {
                    DeepTimePanel(
                        ageGa = historyAgeGa,
                        domain = historyDomain,
                        playing = historyPlaying,
                        speedIndex = historySpeedIndex,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        onDomain = { historyDomain = it },
                        onAge = { historyAgeGa = it.coerceIn(-7f, 4.6f) },
                        onPlay = { historyPlaying = !historyPlaying },
                        onSpeed = { historySpeedIndex = (historySpeedIndex + 1) % 4 },
                        onEvent = { historyAgeGa = it.toFloat() },
                        onAdjacentEvent = { direction ->
                            DeepTimeHistory.adjacentEvent(historyDomain, historyAgeGa.toDouble(), direction)?.let {
                                historyAgeGa = it.ageGa.toFloat()
                            }
                        },
                        onPresent = { historyAgeGa = 0f; historyPlaying = false },
                        onClose = { historyOpen = false; historyPlaying = false }
                    )
                } else Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                        .widthIn(max = maxWidth - 150.dp),
                shape = CircleShape,
                color = Panel,
                border = BorderStroke(1.dp, Border),
                shadowElevation = 12.dp
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(if (overview) "◉  Return" else "◉  Overview", active = overview) {
                        overview = glView?.endlessRenderer?.toggleOverview() ?: overview
                    }
                    DividerPill()
                    ControlButton(if (paused) "▶  Play" else "Ⅱ  Pause", active = paused) {
                        paused = glView?.endlessRenderer?.togglePause() ?: paused
                    }
                    ControlButton("▣  Now") { glView?.endlessRenderer?.resetTime() }
                    DividerPill()
                    ControlButton("◀◀") { glView?.endlessRenderer?.slower(); speedLabel = glView?.endlessRenderer?.speedLabel() ?: speedLabel }
                    Text(speedLabel, color = Accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp))
                    ControlButton("▶▶") { glView?.endlessRenderer?.faster(); speedLabel = glView?.endlessRenderer?.speedLabel() ?: speedLabel }
                    DividerPill()
                    ControlButton("◎  Orbits", active = orbitsOn) {
                        orbitsOn = glView?.endlessRenderer?.toggleOrbits() ?: orbitsOn
                    }
                    ControlButton("◆  Labels", active = labelsOn) { labelsOn = !labelsOn }
                    DividerPill()
                    ControlButton("◷  History", active = historyOpen) {
                        historyOpen = true
                        historyDomain = "System"
                        historyPlaying = false
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, warning: Boolean = false) {
    Surface(shape = CircleShape, color = Panel, border = BorderStroke(1.dp, Border)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (warning) Color(0xFFFFBE63) else Color(0xFF69D58B)))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Muted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun DeepTimePanel(
    ageGa: Float,
    domain: String,
    playing: Boolean,
    speedIndex: Int,
    modifier: Modifier = Modifier,
    onDomain: (String) -> Unit,
    onAge: (Float) -> Unit,
    onPlay: () -> Unit,
    onSpeed: () -> Unit,
    onEvent: (Double) -> Unit,
    onAdjacentEvent: (Int) -> Unit,
    onPresent: () -> Unit,
    onClose: () -> Unit
) {
    val events = DeepTimeHistory.events(domain)
    val nearest = DeepTimeHistory.nearestEvent(domain, ageGa.toDouble())
    val eventScroll = remember(domain) { ScrollState(0) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = PanelStrong,
        border = BorderStroke(1.dp, Border),
        shadowElevation = 16.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DEEP TIME", color = Accent, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        DeepTimeHistory.formatAge(ageGa.toDouble()),
                        modifier = Modifier.semantics { contentDescription = "Deep time ${DeepTimeHistory.formatAge(ageGa.toDouble())}" },
                        color = Text, fontSize = 15.sp, fontFamily = FontFamily.Monospace
                    )
                }
                ControlButton("‹ Event", onClick = { onAdjacentEvent(-1) })
                ControlButton("Event ›", onClick = { onAdjacentEvent(1) })
                ControlButton("Speed ${listOf("0.5×", "1×", "5×", "20×")[speedIndex.coerceIn(0, 3)]}", onClick = onSpeed)
                ControlButton(if (playing) "Ⅱ  Pause" else "▶  Play", active = playing, onClick = onPlay)
                ControlButton("◎  Present", onClick = onPresent)
                ControlButton("×", onClick = onClose)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                DeepTimeHistory.domains.forEach { item ->
                    ControlButton(
                        item,
                        active = item == domain,
                        modifier = Modifier.semantics { contentDescription = "History track $item" },
                        onClick = { onDomain(item) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("4.6 Ga", color = Muted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Slider(
                    value = DeepTimeHistory.sliderPosition(ageGa.toDouble()),
                    onValueChange = { onAge(DeepTimeHistory.ageFromSlider(it).toFloat()) },
                    valueRange = 0f..11.6f,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Deep time timeline scrubber" }
                )
                Text("future", color = Muted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(eventScroll),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                events.forEach { event ->
                    Surface(
                        modifier = Modifier.semantics { contentDescription = "Jump to ${event.title}" },
                        shape = CircleShape,
                        color = if (event.id == nearest?.id) AccentBg else Color(0x12FFFFFF),
                        border = BorderStroke(1.dp, if (event.id == nearest?.id) Accent.copy(alpha = .48f) else Border)
                    ) {
                        Text(
                            event.title,
                            modifier = Modifier.clickable { onEvent(event.ageGa) }.padding(horizontal = 9.dp, vertical = 6.dp),
                            color = if (event.id == nearest?.id) Accent else Muted,
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            nearest?.let { event ->
                Spacer(Modifier.height(5.dp))
                Text("${event.confidence.uppercase(Locale.US)}  ·  ${event.summary}", color = Muted, fontSize = 9.sp, lineHeight = 13.sp, maxLines = 2)
            }
            Text("Shared universe epoch · historical surfaces are curated scientific reconstructions", color = Color(0xFF7D89AA), fontSize = 8.sp)
        }
    }
}

@Composable
private fun InfoPanel(
    info: BodyInfo,
    selectedId: String,
    detailSections: List<InfoSection>,
    primaryActionLabel: String?,
    modifier: Modifier = Modifier,
    onPrimaryAction: () -> Unit,
    onClose: () -> Unit
) {
    var showDeepInfo by remember(selectedId) { mutableStateOf(false) }
    Surface(
        modifier = modifier.width(292.dp).heightIn(max = 268.dp),
        shape = RoundedCornerShape(14.dp),
        color = PanelStrong,
        border = BorderStroke(1.dp, Border),
        shadowElevation = 18.dp
    ) {
        Column(Modifier.padding(15.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(info.name, color = Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(info.type.uppercase(Locale.US), color = Accent, fontSize = 8.sp, letterSpacing = 1.0.sp)
                }
                Surface(
                    modifier = Modifier.size(32.dp).clickable(onClick = onClose),
                    shape = CircleShape,
                    color = Color(0x0AFFFFFF),
                    border = BorderStroke(1.dp, Border)
                ) { Box(contentAlignment = Alignment.Center) { Text("×", color = Muted, fontSize = 18.sp) } }
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell("RADIUS", info.radius, Modifier.weight(1f))
                InfoCell(if (selectedId == "moon") "DISTANCE" else "SEMI-MAJOR AXIS", info.semiMajor, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell("ORBITAL PERIOD", info.orbitalPeriod, Modifier.weight(1f))
                InfoCell("ROTATION", info.rotation, Modifier.weight(1f))
            }
            if (primaryActionLabel != null) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ControlButton("↗  $primaryActionLabel", active = true, onClick = onPrimaryAction)
                }
            }
            Spacer(Modifier.height(7.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(8.dp))
            Text(info.description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3)
            if (detailSections.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                ControlButton(if (showDeepInfo) "Hide Sun science" else "Explore Sun science", active = showDeepInfo) {
                    showDeepInfo = !showDeepInfo
                }
                if (showDeepInfo) {
                    detailSections.forEach { section ->
                        Spacer(Modifier.height(8.dp))
                        Text(section.title, color = Accent, fontSize = 8.sp, letterSpacing = 1.0.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(section.body, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Orbit source · built-in fallback  •  Collision · continuous", color = Color(0xFF7D89AA), fontSize = 8.sp)
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color(0xFF7D89AA), fontSize = 8.sp, letterSpacing = .8.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Text, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ControlButton(label: String, active: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (active) Accent else Muted,
            containerColor = if (active) AccentBg else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp)
    ) { Text(label, fontSize = 9.sp, maxLines = 1) }
}

@Composable
private fun DividerPill() {
    Box(Modifier.width(1.dp).height(22.dp).background(Border))
}


private const val STATE_VERSION_KEY = "endless.state.version"
private const val STATE_VERSION = 2

private fun Bundle.writeExplorationState(state: EndlessRenderer.ExplorationState) {
    putInt(STATE_VERSION_KEY, STATE_VERSION)
    putString("endless.state.selected", state.selectedId)
    putBoolean("endless.state.overview", state.overview)
    putBoolean("endless.state.orbits", state.showOrbits)
    putDouble("endless.state.yaw", state.yaw)
    putDouble("endless.state.pitch", state.pitch)
    putDouble("endless.state.distance", state.distance)
    putDouble("endless.state.targetDistance", state.targetDistance)
    putBoolean("endless.state.surface", state.marsSurfaceMode)
    putDouble("endless.state.surfaceX", state.marsSurfaceX)
    putDouble("endless.state.surfaceZ", state.marsSurfaceZ)
    putBoolean("endless.state.moonSurface", state.moonSurfaceMode)
    putDouble("endless.state.moonSurfaceX", state.moonSurfaceX)
    putDouble("endless.state.moonSurfaceZ", state.moonSurfaceZ)

    state.savedFocus?.let { focus ->
        putBoolean("endless.state.savedFocus.present", true)
        putString("endless.state.savedFocus.selected", focus.selectedId)
        putDouble("endless.state.savedFocus.yaw", focus.yaw)
        putDouble("endless.state.savedFocus.pitch", focus.pitch)
        putDouble("endless.state.savedFocus.distance", focus.distance)
        putDouble("endless.state.savedFocus.targetDistance", focus.targetDistance)
    }

    putDouble("endless.state.clock.seconds", state.clockState.simulationSeconds)
    putLong("endless.state.clock.anchorMillis", state.clockState.anchorMillis)
    putInt("endless.state.clock.speedIndex", state.clockState.speedIndex)
    putBoolean("endless.state.clock.paused", state.clockState.paused)
}

private fun Bundle.readExplorationState(): EndlessRenderer.ExplorationState? {
    val stateVersion = getInt(STATE_VERSION_KEY, 0)
    if (stateVersion !in 1..STATE_VERSION) return null

    val savedFocus = if (getBoolean("endless.state.savedFocus.present", false)) {
        EndlessRenderer.CameraState(
            selectedId = getString("endless.state.savedFocus.selected"),
            yaw = getDouble("endless.state.savedFocus.yaw"),
            pitch = getDouble("endless.state.savedFocus.pitch"),
            distance = getDouble("endless.state.savedFocus.distance"),
            targetDistance = getDouble("endless.state.savedFocus.targetDistance")
        )
    } else {
        null
    }

    return EndlessRenderer.ExplorationState(
        selectedId = getString("endless.state.selected"),
        overview = getBoolean("endless.state.overview"),
        showOrbits = getBoolean("endless.state.orbits", true),
        yaw = getDouble("endless.state.yaw"),
        pitch = getDouble("endless.state.pitch"),
        distance = getDouble("endless.state.distance"),
        targetDistance = getDouble("endless.state.targetDistance"),
        savedFocus = savedFocus,
        marsSurfaceMode = getBoolean("endless.state.surface"),
        marsSurfaceX = getDouble("endless.state.surfaceX"),
        marsSurfaceZ = getDouble("endless.state.surfaceZ"),
        moonSurfaceMode = stateVersion >= 2 && getBoolean("endless.state.moonSurface"),
        moonSurfaceX = if (stateVersion >= 2) getDouble("endless.state.moonSurfaceX") else 0.0,
        moonSurfaceZ = if (stateVersion >= 2) getDouble("endless.state.moonSurfaceZ") else 0.0,
        clockState = UniverseClock.State(
            simulationSeconds = getDouble("endless.state.clock.seconds"),
            anchorMillis = getLong("endless.state.clock.anchorMillis"),
            speedIndex = getInt("endless.state.clock.speedIndex", 2),
            paused = getBoolean("endless.state.clock.paused")
        )
    )
}
