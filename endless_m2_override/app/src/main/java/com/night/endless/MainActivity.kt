package com.night.endless

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.night.endless.engine.render.BodyLabelSnapshot
import com.night.endless.engine.render.EndlessGLView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
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
        setContent { EndlessApp() }
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
    val radius: String,
    val semiMajor: String,
    val orbitalPeriod: String,
    val rotation: String,
    val description: String
)

private val bodyInfo = mapOf(
    "sun" to BodyInfo("Sun", "696,340 km", "—", "—", "25.4 d equator", "The star at the centre of the Solar System. Display size is exaggerated so the inner system remains readable."),
    "mercury" to BodyInfo("Mercury", "2,439.7 km", "0.3871 AU", "87.97 d", "58.65 d", "The smallest planet and the closest planet to the Sun."),
    "venus" to BodyInfo("Venus", "6,051.8 km", "0.7233 AU", "224.70 d", "243.0 d retrograde", "A hot terrestrial world hidden beneath a dense atmosphere."),
    "earth" to BodyInfo("Earth", "6,371 km", "1.0000 AU", "365.26 d", "23 h 56 m", "Our home world. The native renderer keeps Earth moving on the same universe clock as the rest of the system."),
    "moon" to BodyInfo("Moon", "1,737.4 km", "384,400 km from Earth", "27.32 d", "27.32 d", "Earth's natural satellite. Surface exploration and high-resolution terrain are planned for the planetary layer."),
    "mars" to BodyInfo("Mars", "3,389.5 km", "1.5237 AU", "686.98 d", "24 h 37 m", "The fourth planet from the Sun, marked by iron-rich reddish terrain."),
    "jupiter" to BodyInfo("Jupiter", "69,911 km", "5.2029 AU", "11.86 y", "9 h 55 m", "The largest planet, a gas giant with banded clouds and enormous storms."),
    "saturn" to BodyInfo("Saturn", "58,232 km", "9.5371 AU", "29.45 y", "10 h 42 m", "A gas giant surrounded by its bright, complex ring system."),
    "uranus" to BodyInfo("Uranus", "25,362 km", "19.191 AU", "84.0 y", "17 h 14 m retrograde", "An ice giant rotating on its side with a faint ring system."),
    "neptune" to BodyInfo("Neptune", "24,622 km", "30.07 AU", "164.8 y", "16 h 6 m", "A distant blue ice giant with some of the fastest winds in the Solar System.")
)

@Composable
private fun EndlessApp() {
    var selected by remember { mutableStateOf<String?>("earth") }
    var infoVisible by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }
    var orbitsOn by remember { mutableStateOf(true) }
    var labelsOn by remember { mutableStateOf(true) }
    var speedLabel by remember { mutableStateOf("10×") }
    var dateText by remember { mutableStateOf("—") }
    var timeText by remember { mutableStateOf("—") }
    var snapshots by remember { mutableStateOf<List<BodyLabelSnapshot>>(emptyList()) }
    var glView by remember { mutableStateOf<EndlessGLView?>(null) }

    LaunchedEffect(glView) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val timeFormat = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        while (true) {
            glView?.endlessRenderer?.let { renderer ->
                val now = Date(renderer.currentTimeMillis())
                dateText = dateFormat.format(now)
                timeText = timeFormat.format(now)
                snapshots = renderer.labelSnapshots()
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
                            infoVisible = true
                        }
                    }.also { glView = it }
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
                Text("INTERACTIVE 3D ORRERY", color = Muted, fontSize = 9.sp, letterSpacing = 1.3.sp)
            }

            Row(
                Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                StatusBadge("JPL HORIZONS · FALLBACK", warning = true)
                StatusBadge("NATIVE · COLLISION ON")
            }

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 58.dp),
                shape = CircleShape,
                color = Panel,
                border = BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dateText, color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(7.dp))
                    Text(timeText, color = Accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            if (labelsOn) {
                snapshots.filter { it.visible && it.id != "sun" }.forEach { label ->
                    val xDp = with(density) { label.xPx.toDp() }
                    val yDp = with(density) { label.yPx.toDp() }
                    Surface(
                        modifier = Modifier.offset(x = xDp + 6.dp, y = yDp - 14.dp)
                            .clickable { glView?.endlessRenderer?.focus(label.id) },
                        shape = CircleShape,
                        color = Color(0xC7080B17),
                        border = BorderStroke(1.dp, Border)
                    ) {
                        Text(label.name, color = if (label.id == selected) Accent else Text, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
            }

            if (infoVisible && selected != null) {
                bodyInfo[selected]?.let { info ->
                    InfoPanel(
                        info = info,
                        selectedId = selected!!,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
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
                }
            }

            Surface(
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
private fun InfoPanel(info: BodyInfo, selectedId: String, modifier: Modifier = Modifier, onClose: () -> Unit) {
    Surface(
        modifier = modifier.width(292.dp).heightIn(max = 226.dp),
        shape = RoundedCornerShape(14.dp),
        color = PanelStrong,
        border = BorderStroke(1.dp, Border),
        shadowElevation = 18.dp
    ) {
        Column(Modifier.padding(15.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name, color = Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(
                    modifier = Modifier.size(32.dp).clickable(onClick = onClose),
                    shape = CircleShape,
                    color = Color(0x0AFFFFFF),
                    border = BorderStroke(1.dp, Border)
                ) { Box(contentAlignment = Alignment.Center) { Text("×", color = Muted, fontSize = 18.sp) } }
            }
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell("RADIUS", info.radius, Modifier.weight(1f))
                InfoCell(if (selectedId == "moon") "DISTANCE" else "SEMI-MAJOR AXIS", info.semiMajor, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell("ORBITAL PERIOD", info.orbitalPeriod, Modifier.weight(1f))
                InfoCell("ROTATION", info.rotation, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(10.dp))
            Text(info.description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
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
private fun ControlButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    TextButton(
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
