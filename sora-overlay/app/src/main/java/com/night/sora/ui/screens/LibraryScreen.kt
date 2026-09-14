package com.night.sora.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.LibraryEntry
import com.night.sora.ui.components.DenseRow
import com.night.sora.ui.theme.SoraMuted

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    entries: List<LibraryEntry>,
    onOpenMedia: (ExtensionMediaSelection) -> Unit,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(20.dp, 24.dp, 20.dp, 14.dp)) {
                Text("Library", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Private material and saved media Sora can work with.", color = SoraMuted, fontSize = 13.sp)
            }
        }
        items(entries, key = { it.id }) { item ->
            val selection = item.toMediaSelection()
            val icon = when {
                selection != null -> Icons.Rounded.PlayCircle
                item.kind == "Reference" -> Icons.Rounded.Description
                item.kind == "Collection" -> Icons.Rounded.Collections
                else -> Icons.Rounded.Folder
            }
            DenseRow(
                item.label,
                "${item.kind} · ${item.detail}",
                icon,
                onClick = selection?.let { { onOpenMedia(it) } },
            )
        }
    }
}
