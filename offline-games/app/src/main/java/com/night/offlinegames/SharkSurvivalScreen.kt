package com.night.offlinegames

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

private val oceanInk = Color(0xFF0D2935)
private val foam = Color(0xFFE8F6EE)

@Composable
fun SharkSurvivalScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_shark_survival", Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(SharkEngine.decode(prefs.getString("run", null)) ?: SharkEngine.newGame()) }
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var paused by remember { mutableStateOf(false) }
    var desired by remember { mutableStateOf<Offset?>(null) }
    val latestDesired by rememberUpdatedState(desired)

    // Never keep the action running behind the user or after home/back.
    val owner = context as? LifecycleOwner
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { paused = true; desired = null }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(paused, state.finished) {
        if (paused || state.finished) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val delta = ((now - last) / 1_000_000_000f).coerceIn(0f, .05f)
                    val point = latestDesired
                    state = SharkEngine.step(state, point?.x, point?.y, delta)
                }
                last = now
            }
        }
    }
    // Persist once per second rather than writing preferences on every frame.
    LaunchedEffect(state.seconds.toInt(), state.finished) {
        if (state.points > best) best = state.points
        prefs.edit().putString("run", SharkEngine.encode(state)).putInt("best", best).apply()
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().background(Color(0xFFF1F6F4))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            IconButton(onClick = {
                prefs.edit().putString("run", SharkEngine.encode(state)).apply()
                onExit()
            }, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to games", tint = oceanInk)
            }
            Text("Shark Survival", color = oceanInk, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(
                onClick = { paused = !paused; desired = null },
                colors = ButtonDefaults.buttonColors(containerColor = oceanInk),
                enabled = !state.finished
            ) { Text(if (paused) "Resume" else "Pause") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SCORE ${state.points}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = oceanInk)
            Text("BEST ${maxOf(best, state.points)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = oceanInk)
            Text("SIZE ${state.sizeTier}  •  EATEN ${state.eaten}", fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = oceanInk)
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    desired = Offset(
                        down.position.x / size.width * SharkEngine.worldWidth,
                        down.position.y / size.height * SharkEngine.worldHeight
                    )
                    do {
                        val event = awaitPointerEvent()
                        val held = event.changes.firstOrNull { it.pressed }
                        if (held != null) {
                            desired = Offset(
                                held.position.x / size.width * SharkEngine.worldWidth,
                                held.position.y / size.height * SharkEngine.worldHeight
                            )
                            held.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    desired = null
                }
            }) {
                drawOcean(state, desired)
            }
            if (paused || state.finished) {
                Surface(
                    Modifier.align(Alignment.Center).padding(horizontal = 25.dp),
                    color = Color(0xEAF6FAF7),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.finished) "Dive over" else "Paused",
                            color = oceanInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(9.dp))
                        Text(
                            if (state.finished) "You ate ${state.eaten} creatures and earned ${state.points} points."
                            else "Your dive is waiting.",
                            color = Color(0xFF47645D), fontSize = 13.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                if (state.finished) {
                                    state = SharkEngine.newGame()
                                    prefs.edit().putString("run", SharkEngine.encode(state)).apply()
                                }
                                paused = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = oceanInk)
                        ) { Text(if (state.finished) "New dive" else "Resume") }
                    }
                }
            }
        }
        Text(
            "Drag anywhere to swim. Eat smaller fish, avoid stingers and predators.",
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 13.dp),
            fontSize = 12.sp, color = Color(0xFF5C756E)
        )
    }
}

/** Procedural original art drawn in Android Canvas. No borrowed sprites or web assets. */
private fun DrawScope.drawOcean(state: SharkState, steering: Offset?) {
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF73C9D9), Color(0xFF247A94), Color(0xFF103F66), Color(0xFF092C4A))),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
    )
    val sx = size.width / SharkEngine.worldWidth
    val sy = size.height / SharkEngine.worldHeight
    scale(sx, sy, pivot = Offset.Zero) {
        val seconds = state.seconds
        // Far parallax rays and moving bubbles.
        for (i in 0..5) {
            val x = 45f + i * 82f + sin(seconds * .2f + i).toFloat() * 12f
            drawLine(Color.White.copy(alpha = .08f),
                Offset(x, 0f), Offset(x - 74f, 580f), strokeWidth = 16f)
        }
        for (i in 0..19) {
            val x = ((i * 71 + 17) % 380).toFloat()
            val y = 620f - ((seconds * (10 + i % 4) + i * 47) % 650f)
            drawCircle(Color(0xFFDEF8F0).copy(alpha = .23f), radius = 2f + i % 3,
                center = Offset(x, y))
        }
        drawPath(Path().apply {
            moveTo(0f, 635f)
            quadraticTo(70f, 600f, 150f, 629f)
            quadraticTo(265f, 658f, 400f, 618f)
            lineTo(400f, 660f); lineTo(0f, 660f); close()
        }, Color(0xFF153F49))
        for (i in 0..9) {
            val x = 10f + i * 42f
            val height = 13f + (i % 4) * 10f
            drawLine(if (i % 3 == 0) Color(0xFF60B9A0) else Color(0xFF346F65),
                Offset(x, 647f), Offset(x + sin(seconds*.6f + i).toFloat()*7f, 647f-height),
                strokeWidth = 4f)
        }
        for (creature in state.creatures) {
            when (creature.kind) {
                SeaKind.PREY -> drawFish(creature, Color(0xFFD7E2A0), Color(0xFF9CD5BF))
                SeaKind.PREDATOR -> drawFish(creature, Color(0xFFAF6D72), Color(0xFF743F57))
                SeaKind.JELLYFISH -> {
                    val x = creature.x;val y=creature.y
                    drawArc(Color(0xFFEDA8D5),180f,180f,false,Offset(x-15f,y-16f),Size(30f,26f))
                    for (t in -2..2) {
                        val sx1=x+t*6f
                        drawLine(Color(0xFFDDB0E0).copy(alpha = .76f),
                            Offset(sx1,y-2f),Offset(sx1+sin(seconds*3+t).toFloat()*4f,y+19f),
                            strokeWidth=2f)
                    }
                }
            }
        }
        val direction = if (steering != null && steering.x < state.x - 7f) -1f else 1f
        val len = 25f + state.sizeTier*8f
        val px = state.x;val py=state.y
        val bodyColour = when (state.sizeTier) {
            1 -> Color(0xFF6C8793)
            2 -> Color(0xFF4E7585)
            else -> Color(0xFF3D627A)
        }
        if (state.invulnerable <= 0f || (seconds * 7).toInt() % 2 == 0) {
            drawPath(Path().apply {
                moveTo(px + direction * len, py)
                cubicTo(px + direction * (len*.2f),py-len*.45f,
                    px-direction*(len*.75f),py-len*.35f,px-direction*len,py)
                cubicTo(px-direction*(len*.5f),py+len*.38f,
                    px+direction*(len*.15f),py+len*.38f,px+direction*len,py)
                close()
            },bodyColour)
            drawPath(Path().apply {
                moveTo(px - direction*len*.8f,py)
                lineTo(px-direction*len*1.4f,py-len*.55f)
                lineTo(px-direction*len*1.4f,py+len*.55f)
                close()
            },bodyColour)
            drawPath(Path().apply {
                moveTo(px-direction*len*.1f,py-len*.25f)
                lineTo(px-direction*len*.2f,py-len*.83f)
                lineTo(px+direction*len*.24f,py-len*.28f)
                close()
            },Color(0xFF254F63))
            drawOval(Color(0xFFE4F0E9).copy(alpha=.78f),
                Offset(px-direction*len*.55f,py+len*.05f),
                Size(len*1.1f,len*.26f))
            drawCircle(Color.White,3.8f,Offset(px+direction*len*.55f,py-5f))
            drawCircle(Color(0xFF112B38),2.0f,Offset(px+direction*len*.63f,py-5f))
            drawLine(Color(0xFF284858), Offset(px+direction*len*.75f,py+len*.11f),
                Offset(px+direction*len*.93f,py+len*.13f), 1.5f)
        }
        // Health is a genuine gameplay resource and remains readable over every biome.
        drawRoundRect(Color(0x990A2D3A),Offset(14f,17f),Size(372f,28f),
            androidx.compose.ui.geometry.CornerRadius(10f))
        drawRoundRect(Color(0xFF27495B),Offset(24f,27f),Size(350f,8f),
            androidx.compose.ui.geometry.CornerRadius(4f))
        drawRoundRect(
            if (state.health > 35f) Color(0xFF6CE0AE) else Color(0xFFF29A75),
            Offset(24f,27f),Size(350f * state.health.coerceIn(0f,100f) / 100f,8f),
            androidx.compose.ui.geometry.CornerRadius(4f)
        )
        if (steering != null) {
            drawCircle(Color.White.copy(alpha=.22f),16f,steering,
                style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.5f))
        }
    }
}

private fun DrawScope.drawFish(f: SeaCreature, front: Color, back: Color) {
    val sign = if (f.velocity >= 0f) 1f else -1f
    val x=f.x;val y=f.y;val r=f.radius
    drawPath(Path().apply {
        moveTo(x-sign*r,y)
        lineTo(x-sign*r*1.7f,y-r*.75f)
        lineTo(x-sign*r*1.7f,y+r*.75f)
        close()
    },back)
    drawOval(front,Offset(x-r,y-r*.62f),Size(r*2,r*1.24f))
    drawPath(Path().apply {
        moveTo(x-r*.25f,y-r*.4f);lineTo(x+sign*r*.1f,y-r*1.0f)
        lineTo(x+sign*r*.53f,y-r*.35f);close()
    },back)
    drawCircle(Color.White,r*.19f,Offset(x+sign*r*.54f,y-r*.13f))
    drawCircle(oceanInk,r*.08f,Offset(x+sign*r*.6f,y-r*.13f))
}
