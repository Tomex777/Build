package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightLiveVoiceClient

@Composable
fun NightLiveVoiceScreen(
    chatTitle: String,
    state: NightLiveVoiceClient.State,
    error: String?,
    onEndCall: () -> Unit,
) {
    val status = when (state) {
        NightLiveVoiceClient.State.CONNECTING -> "Connecting…"
        NightLiveVoiceClient.State.CONNECTED -> "Connected"
        NightLiveVoiceClient.State.LISTENING -> "Listening"
        NightLiveVoiceClient.State.SPEAKING -> "Night is speaking"
        NightLiveVoiceClient.State.ENDED -> "Call ended"
    }

    val stateIcon = when (state) {
        NightLiveVoiceClient.State.SPEAKING -> Icons.Default.VolumeUp
        NightLiveVoiceClient.State.LISTENING -> Icons.Default.Mic
        else -> Icons.Default.GraphicEq
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090A))
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                color = Color(0xFF171C1F),
                shape = CircleShape,
                modifier = Modifier.size(118.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = stateIcon,
                        contentDescription = null,
                        tint = Color(0xFF21C063),
                        modifier = Modifier.size(54.dp),
                    )
                }
            }

            Text(
                text = chatTitle,
                color = Color(0xFFF0F2F3),
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp),
            )

            Text(
                text = status,
                color = Color(0xFFA6AFB3),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 7.dp),
            )

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = Color(0xFFFF7A86),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Spacer(modifier = Modifier.size(76.dp))

            Surface(
                color = Color(0xFFE24955),
                shape = CircleShape,
                modifier = Modifier.size(68.dp),
                onClick = onEndCall,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Text(
                text = "End",
                color = Color(0xFFA6AFB3),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
