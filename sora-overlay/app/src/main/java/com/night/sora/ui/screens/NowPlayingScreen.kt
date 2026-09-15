package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.ui.theme.*

@Composable
fun NowPlayingScreen(track: ExtensionMediaSelection, onBack: () -> Unit) {
    var playing by remember { mutableStateOf(true) }
    var liked by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(.53f) }

    Column(Modifier.fillMaxSize().background(SoraBg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowDown, "Collapse") }
            Text("PLAYING FROM SORA", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f), color = SoraMuted)
            IconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, "Track options") }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(SoraSurfaceHigh), contentAlignment = Alignment.Center) {
            if (!track.artworkUrl.isNullOrBlank()) AsyncImage(track.artworkUrl, track.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(88.dp), tint = SoraMuted)
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
                Text(track.subtitle, color = SoraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = { liked = !liked }) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (liked) SoraAccent else SoraText) }
        }
        Spacer(Modifier.height(20.dp))
        Slider(value = progress, onValueChange = { progress = it }, colors = SliderDefaults.colors(thumbColor = SoraText, activeTrackColor = SoraText, inactiveTrackColor = SoraSurfaceRaised))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("2:18", color = SoraMuted, fontSize = 9.sp); Text("4:19", color = SoraMuted, fontSize = 9.sp) }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = {}) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = SoraMuted) }
            IconButton(onClick = {}) { Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(34.dp)) }
            FilledIconButton(onClick = { playing = !playing }, modifier = Modifier.size(66.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoraText, contentColor = Color.Black)) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", modifier = Modifier.size(34.dp)) }
            IconButton(onClick = {}) { Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(34.dp)) }
            IconButton(onClick = {}) { Icon(Icons.Rounded.Repeat, "Repeat", tint = SoraMuted) }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PlayerSecondary(Icons.Rounded.Lyrics, "Lyrics")
            PlayerSecondary(Icons.Rounded.QueueMusic, "Queue")
            PlayerSecondary(Icons.Rounded.PlaylistAdd, "Playlist")
        }
        Spacer(Modifier.height(20.dp))
        Surface(color = Color(0xFF26241D), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("LYRICS", color = SoraMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Lyrics appear here when the active music source provides them.", fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Text("Open lyrics", color = SoraAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clickable { }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speaker, null, tint = SoraMuted)
            Text("This phone", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Text("Change", color = SoraMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlayerSecondary(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = SoraMuted)
        Text(label, color = SoraMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
