package eu.kanade.presentation.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Extraction of the AppBar, AppBarTitle and AppBarActions UI used by Mihon's
 * ReaderTopBar. Only its standalone reader-facing API is included here.
 * Mihon source reference: app/.../presentation/components/AppBar.kt
 * commit: 7a917968e3bf71c4a665e6655a550877d81ead1d
 */
object AppBar {
    sealed interface AppBarAction
    data class Action(
        val title: String,
        val icon: ImageVector,
        val onClick: () -> Unit,
    ) : AppBarAction
    data class OverflowAction(
        val title: String,
        val onClick: () -> Unit,
    ) : AppBarAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    title: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    subtitle: String? = null,
    navigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            navigateUp?.let {
                IconButton(onClick = it) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
        },
        title = {
            Column {
                title?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(repeatDelayMillis = 2_000),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
    )
}

@Composable
fun AppBarActions(actions: List<AppBar.AppBarAction>) {
    var showMenu by remember { mutableStateOf(false) }
    actions.filterIsInstance<AppBar.Action>().forEach { action ->
        IconButton(onClick = action.onClick) {
            Icon(action.icon, contentDescription = action.title)
        }
    }
    val overflow = actions.filterIsInstance<AppBar.OverflowAction>()
    if (overflow.isNotEmpty()) {
        androidx.compose.foundation.layout.Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                overflow.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.title) },
                        onClick = { showMenu = false; action.onClick() },
                    )
                }
            }
        }
    }
}
