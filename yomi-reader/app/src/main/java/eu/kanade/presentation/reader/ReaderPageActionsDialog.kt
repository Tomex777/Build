package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Mihon v0.19.9 ReaderPageActionsDialog presentation adapter.
 * Its four original actions and set-cover confirmation are retained; Yomi's
 * standalone Material3 sheet replaces Mihon's app-wide AdaptiveSheet and
 * application-specific resource/action-button dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSetAsCover: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReaderPageAction(
                title = "Set as cover",
                icon = Icons.Outlined.Photo,
                modifier = Modifier.weight(1f),
                onClick = { showSetCoverDialog = true },
            )
            ReaderPageAction(
                title = "Copy",
                icon = Icons.Outlined.ContentCopy,
                modifier = Modifier.weight(1f),
                onClick = { onCopy(); onDismissRequest() },
            )
            ReaderPageAction(
                title = "Share",
                icon = Icons.Outlined.Share,
                modifier = Modifier.weight(1f),
                onClick = { onShare(); onDismissRequest() },
            )
            ReaderPageAction(
                title = "Save",
                icon = Icons.Outlined.Save,
                modifier = Modifier.weight(1f),
                onClick = { onSave(); onDismissRequest() },
            )
        }
    }

    if (showSetCoverDialog) {
        AlertDialog(
            onDismissRequest = { showSetCoverDialog = false },
            text = { Text("Use this page as the book cover?") },
            confirmButton = {
                TextButton(onClick = {
                    showSetCoverDialog = false
                    onSetAsCover()
                    onDismissRequest()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showSetCoverDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ReaderPageAction(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(modifier = modifier, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}
