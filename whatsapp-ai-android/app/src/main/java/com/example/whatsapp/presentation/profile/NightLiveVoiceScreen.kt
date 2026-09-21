package com.example.whatsapp.presentation.profile

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.R
import com.example.whatsapp.data.night.NightLiveVoiceClient
import java.util.Locale
import kotlinx.coroutines.delay

private val CallBackground = Color(0xFF07141B)
private val CallTray = Color(0xF2151B1E)
private val CallButton = Color(0xFF20282C)
private val CallPrimary = Color(0xFFF3F5F6)
private val CallSecondary = Color(0xFFADB7BB)
private val CallGreen = Color(0xFF21C063)
private val CallRed = Color(0xFFFF003D)

@Composable
fun NightLiveVoiceScreen(
    chatTitle: String,
    state: NightLiveVoiceClient.State,
    error: String?,
    microphoneMuted: Boolean = false,
    speakerEnabled: Boolean = false,
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    val callActive =
        state == NightLiveVoiceClient.State.CONNECTED ||
            state == NightLiveVoiceClient.State.LISTENING ||
            state == NightLiveVoiceClient.State.SPEAKING

    LaunchedEffect(state) {
        if (callActive && startedAt == 0L) {
            startedAt = SystemClock.elapsedRealtime()
        }
        while (callActive) {
            elapsedSeconds = if (startedAt == 0L) {
                0L
            } else {
                ((SystemClock.elapsedRealtime() - startedAt) / 1000L).coerceAtLeast(0L)
            }
            delay(1_000L)
        }
    }

    val status = when (state) {
        NightLiveVoiceClient.State.CONNECTING -> "Connecting…"
        NightLiveVoiceClient.State.CONNECTED -> "Connected"
        NightLiveVoiceClient.State.LISTENING ->
            if (microphoneMuted) "Microphone muted" else "Listening"
        NightLiveVoiceClient.State.SPEAKING -> "Night is speaking"
        NightLiveVoiceClient.State.ENDED -> "Call ended"
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        LiveVoiceWallpaper()

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 22.dp, top = 14.dp),
        ) {
            Text(
                text = "Night",
                color = CallPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Live voice",
                color = CallSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp, vertical = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_night),
                contentDescription = "Night avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (state == NightLiveVoiceClient.State.SPEAKING) {
                            CallGreen.copy(alpha = 0.78f)
                        } else {
                            Color.White.copy(alpha = 0.10f)
                        },
                        shape = CircleShape,
                    )
                    .padding(2.dp),
            )

            Text(
                text = chatTitle,
                color = CallPrimary,
                fontSize = 27.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp),
            )

            Text(
                text = status,
                color = if (state == NightLiveVoiceClient.State.SPEAKING) {
                    CallGreen
                } else {
                    CallSecondary
                },
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (startedAt != 0L) {
                Text(
                    text = formatCallDuration(elapsedSeconds),
                    color = CallSecondary.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            if (!error.isNullOrBlank()) {
                Surface(
                    color = Color(0xCC28171B),
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFFF8791),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
                .fillMaxWidth()
                .widthIn(max = 540.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (moreExpanded) {
                Surface(
                    color = Color(0xE6192125),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "Live voice",
                            color = CallPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = status + " • " + formatCallDuration(elapsedSeconds),
                            color = CallSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = (if (microphoneMuted) "Mic off" else "Mic on") +
                                " • " +
                                (if (speakerEnabled) "Speaker" else "Phone audio"),
                            color = CallSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            Surface(
                color = CallTray,
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 22.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveVoiceControl(
                        label = "Speaker",
                        icon = Icons.Default.VolumeUp,
                        selected = speakerEnabled,
                        onClick = onToggleSpeaker,
                    )
                    LiveVoiceControl(
                        label = "Mute",
                        icon = if (microphoneMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        selected = microphoneMuted,
                        onClick = onToggleMute,
                    )
                    LiveVoiceControl(
                        label = "More",
                        icon = Icons.Default.MoreHoriz,
                        selected = moreExpanded,
                        onClick = { moreExpanded = !moreExpanded },
                    )
                    LiveVoiceControl(
                        label = "End",
                        icon = Icons.Default.CallEnd,
                        danger = true,
                        onClick = onEndCall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceControl(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = when {
                danger -> CallRed
                selected -> CallPrimary
                else -> CallButton
            },
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = when {
                        danger -> Color.White
                        selected -> Color(0xFF111719)
                        else -> CallPrimary
                    },
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Text(
            text = label,
            color = CallPrimary.copy(alpha = 0.92f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LiveVoiceWallpaper() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(CallBackground)

        val ink = Color(0xFF8DA0A8).copy(alpha = 0.085f)
        val stroke = Stroke(width = 1.2.dp.toPx())
        val cell = 78.dp.toPx()
        val iconSize = 27.dp.toPx()

        var row = 0
        var y = 34.dp.toPx()
        while (y < size.height + cell) {
            var col = 0
            var x = -20.dp.toPx() + if (row % 2 == 0) 0f else cell * 0.45f
            while (x < size.width + cell) {
                when ((row + col) % 4) {
                    0 -> {
                        drawCircle(
                            color = ink,
                            radius = 13.dp.toPx(),
                            center = Offset(x + iconSize / 2f, y + iconSize / 2f),
                            style = stroke,
                        )
                        drawLine(
                            color = ink,
                            start = Offset(x + 7.dp.toPx(), y + 14.dp.toPx()),
                            end = Offset(x + 21.dp.toPx(), y + 14.dp.toPx()),
                            strokeWidth = 1.2.dp.toPx(),
                        )
                    }
                    1 -> {
                        drawRoundRect(
                            color = ink,
                            topLeft = Offset(x, y),
                            size = Size(iconSize, iconSize * 0.74f),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            style = stroke,
                        )
                        drawLine(
                            color = ink,
                            start = Offset(x + 5.dp.toPx(), y + 8.dp.toPx()),
                            end = Offset(x + 21.dp.toPx(), y + 8.dp.toPx()),
                            strokeWidth = 1.2.dp.toPx(),
                        )
                    }
                    2 -> {
                        drawLine(
                            color = ink,
                            start = Offset(x + 2.dp.toPx(), y + 2.dp.toPx()),
                            end = Offset(x + 24.dp.toPx(), y + 24.dp.toPx()),
                            strokeWidth = 1.2.dp.toPx(),
                        )
                        drawLine(
                            color = ink,
                            start = Offset(x + 24.dp.toPx(), y + 2.dp.toPx()),
                            end = Offset(x + 2.dp.toPx(), y + 24.dp.toPx()),
                            strokeWidth = 1.2.dp.toPx(),
                        )
                        drawCircle(
                            color = ink,
                            radius = 4.dp.toPx(),
                            center = Offset(x + 13.dp.toPx(), y + 13.dp.toPx()),
                            style = stroke,
                        )
                    }
                    else -> {
                        drawRoundRect(
                            color = ink,
                            topLeft = Offset(x + 2.dp.toPx(), y + 2.dp.toPx()),
                            size = Size(iconSize * 0.82f, iconSize * 0.82f),
                            cornerRadius = CornerRadius(9.dp.toPx()),
                            style = stroke,
                        )
                        drawCircle(
                            color = ink,
                            radius = 3.dp.toPx(),
                            center = Offset(x + 13.dp.toPx(), y + 13.dp.toPx()),
                            style = stroke,
                        )
                    }
                }
                col += 1
                x += cell
            }
            row += 1
            y += cell
        }

        drawRect(Color.Black.copy(alpha = 0.08f))
    }
}

private fun formatCallDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
