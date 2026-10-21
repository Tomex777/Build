package com.night.offlinegames

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun GameRow(entry: GameEntry, favourite: Boolean, onOpen: () -> Unit, onFavourite: () -> Unit) {
    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE3E8E1)),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).clickable(onClick = onOpen)
                    .semantics { contentDescription = "Open ${entry.title}" }
                    .padding(start = 14.dp, top = 13.dp, bottom = 13.dp)
            ) {
                Surface(color = Color(0xFFECF3ED), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(53.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (entry.id) {
                            "2048" -> Icons.Outlined.ViewModule
                            "colorsort" -> Icons.Outlined.WaterDrop
                            "shark" -> Icons.Outlined.Waves
                            "mines" -> Icons.Outlined.BugReport
                            "blocks" -> Icons.Outlined.Apps
                            "bird" -> Icons.Outlined.Flight
                            "maze" -> Icons.Outlined.Extension
                            "sudoku" -> Icons.Outlined.ViewModule
                            "connect" -> Icons.Outlined.FilterNone
                            else -> Icons.Outlined.SportsEsports
                        }
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(27.dp))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(entry.title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(entry.detail, color = muted, fontSize = 12.sp, maxLines = 2)
                }
            }
            IconButton(onClick = onFavourite, modifier = Modifier.padding(end = 7.dp)) {
                Icon(
                    if (favourite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favourite) "Remove ${entry.title} from favourites" else "Favourite ${entry.title}",
                    tint = if (favourite) accent else muted
                )
            }
        }
    }
}

